# halt-health

Checks the Spring Boot services and web server on the box it runs on, and gives the external
load balancer one endpoint to probe. If this returns 503, the load balancer should stop sending traffic
to the box.

Kafka and PostgreSQL are deliberately not checked. They are shared, so an outage would fail every box at
once and leave the load balancer nowhere to send traffic.

## Build and run

Requires Java 21 and Maven.

```
mvn verify
java -jar target/halt-health-1.0.0-SNAPSHOT.jar
```

## Load balancer endpoint

`GET /health` or `HEAD /health`. The load balancer only needs the HTTP status code.

| Overall status | HTTP | Meaning |
|---|---|---|
| `UP` | 200 | Every service is up |
| `DEGRADED` | 200 | Every critical service is up, but a non-critical one is not |
| `DOWN` | 503 | A critical service is down, not checked yet, or its result is stale |

```json
{
  "status": "DOWN",
  "evaluatedAt": "2026-11-06T15:04:05.120Z",
  "services": [
    { "name": "orders", "status": "DOWN", "critical": true, "detail": "HTTP 503", "checkedAt": "2026-11-06T15:04:03.871Z" },
    { "name": "web-server", "status": "UP", "critical": true, "checkedAt": "2026-11-06T15:04:03.864Z" }
  ]
}
```

The endpoint has no authentication and returns service names, so keep it on a network that only the
load balancers and operators can reach.

`GET /actuator/health` reports on the aggregator process alone and ignores the monitored services. Use it
to tell whether the aggregator itself is running.

## What can be monitored

| `type` | `url` format | Healthy when |
|---|---|---|
| `actuator` (default) | `http(s)://host:port/actuator/health/readiness` | 2xx response with `"status": "UP"` |
| `http` | `http(s)://host/path` | Exactly HTTP 200; redirects are not followed |

Notes:

- **Spring Boot applications:** use the readiness endpoint. It reports `OUT_OF_SERVICE` during startup and
  graceful shutdown. Each application must set `management.endpoint.health.probes.enabled=true`, or the
  path returns 404.
- **Web server:** check it directly on the box rather than through the load balancer. Otherwise, once the
  load balancer removes the box, the check can't reach it, `/health` keeps returning 503, and the box never
  comes back. The `url` must be the final address of the page, because a redirect counts as unhealthy.
- **HTTPS:** the JVM must trust the certificate, and the certificate must list the address in the `url`.
  To use an IP address, the certificate needs that IP as a subject alternative name; otherwise the check
  reports `SSLHandshakeException`.
- **Hostnames:** Java can't use a hostname containing an underscore, so the application won't start with one.

## Configuration

Spring Boot loads a `config/application.yml` next to the jar on top of the bundled defaults, so the
service list can be kept outside the jar. A list in that file replaces the bundled list. Changes take
effect after a restart.

```yaml
halt-health:
  poll-interval: 5s  # delay between polling rounds
  timeout: 2s        # limit for checking one service
  stale-after: 15s   # older results count as UNKNOWN; must exceed poll-interval + timeout
  services:
    - name: orders
      url: http://localhost:8081/actuator/health/readiness
    - name: web-server
      type: http
      url: https://web01.sample.ca/
      critical: true  # defaults to true; a non-critical service reports DEGRADED instead of failing
```

The application won't start if the service list is empty, a name is repeated, a `url` doesn't match its
`type`, or `stale-after` is too short.

## Extending

- **A new kind of check:** add a value to `HealthProperties.ServiceType` with its URL scheme, and add a
  `HealthProbe` bean for that type.
- **Different roll-up rules:** change `HealthAggregator.overallStatus`.
- **Reacting to status changes, such as alerts or metrics:** `HealthAggregator.record` already detects
  transitions, so hook in there.
