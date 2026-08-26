# Halt Email Service

Spring Boot service that turns halt/resumption lifecycle events published to the Kafka topic
`halt.lifecycle` into the eight regulatory emails specified in *Publish Regulatory Halt Project — Halt
and Resumption Email Requirements* (v1.0, June 25 2026).

## Tech stack

- Java 21, Spring Boot 3.4.5 (BOM-imported, no `spring-boot-starter-parent`)
- Spring Kafka, consuming the shared `ca.ciro.halt.model.HaltMessage` model (mapped from the wire-format
  `RegHaltMessage` protobuf via `halt-model-shared`)
- Thymeleaf for the eight email templates
- Spring Mail (`JavaMailSender`) for SMTP delivery
- Caffeine for an in-memory idempotency guard — **no database**; failures land in a dedicated log file
  instead of a dead-letter topic (see design doc §07/§10)

## Project layout

```
src/main/java/ca/iiroc/halt/email
├─ HaltEventProcessor.java  # routing → idempotency → render → send; no Kafka type in its signature
├─ config      # EmailServiceProperties (marketplaces, from-address), HaltReasonProperties
├─ kafka       # HaltLifecycleListener (thin), KafkaConsumerConfig, HaltEmailFailureRecoverer
├─ routing     # EmailTrigger, TriggerResolver (command + subState → trigger)
├─ format      # YesNoFormatter, HaltTimeFormatter, ReasonTranslator
├─ template    # EmailContentBuilder, EmailCopy + resources/templates/*.html
├─ mail        # HaltEmailSender, MarketplaceRecipientResolver
└─ idempotency # RecentSendGuard (Caffeine)

src/main/resources
├─ application.yml          # marketplace config, mail.from, Kafka/SMTP settings
├─ email-copy.properties    # bilingual subjects/labels not tied to a specific HaltMessage
├─ logback-spring.xml       # dedicated HALT_EMAIL_ERRORS file appender
└─ templates/               # one Thymeleaf template per trigger + fragments/footer.html
```

## Build

```
mvn clean package
```

Produces an executable Spring Boot jar at `target/halt-svc-email-<version>.jar`
(`spring-boot-maven-plugin` repackage). The `rpm-maven-plugin` execution requires `rpmbuild` and the
`apps/`, `scripts/`, and `license/` assets referenced in `pom.xml`'s RPM mapping — those live on the
Linux build agent, not in this checkout, so RPM packaging can't be exercised locally on Windows.

## Run

```
mvn spring-boot:run
```

or

```
java -jar target/halt-svc-email-<version>.jar
```

Key environment variables (see `application.yml` for the full list):

| Variable | Default | Purpose |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka cluster carrying `halt.lifecycle` |
| `SMTP_HOST` / `SMTP_PORT` | `localhost` / `25` | Outbound mail relay |
| `HALT_EMAIL_FROM` | `halts@ciro.ca` | From address on every sent email |

Without a reachable Kafka broker the app still starts cleanly — the consumer logs connection retries in
the background rather than failing startup.

## Test

```
mvn test
```

Three layers, per design doc §11 "Testing strategy":

1. **`TriggerResolverTest`** — every row of the event-routing table, no infra.
2. **`HaltTimeFormatterTest`** — the `haltTime`/`resumptionTime` parsing contract, no infra.
3. **`HaltEventProcessorIntegrationTest`** — `HaltEventProcessor.process(...)` called directly (no Kafka)
   against an embedded fake SMTP server (GreenMail), asserting real subjects/recipients/body content per
   trigger, plus the idempotency guard.

A fourth layer — `@EmbeddedKafka`, exercising the protobuf deserializer and offset-commit ordering — is
described in the design doc but not yet implemented here.

## Logs

Failed sends (after retries are exhausted) are logged to `logs/halt-email-errors.log`, not a database or
dead-letter topic — see design doc §07. Recovery is manual: read the log, resend.

## Known open items

Tracked in the design doc's §10 "Open Items":

- The resumption create-vs-update tiebreaker (`createdTime == lastModifiedTime`) is an inference, not a
  confirmed contract of `HaltMessage` — worth checking against the halt-gateway-core producer.
- Six of the eight templates carry placeholder French copy (`email-copy.properties`) where the
  requirements doc itself says "French TBD" — swap in real text there once Communications/Surveillance
  finalizes it; no code change needed.
- The `Halt_Extended` subState question from an earlier design revision is resolved: it's confirmed
  ignored (see `TriggerResolver`).
