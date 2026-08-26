package ca.iiroc.halt.email.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Everything the design doc keeps out of {@code HaltMessage} and out of a database: the From address and
 * the four listing marketplaces' To/Bcc/labels (§05 "Recipients &amp; from-address"). Bound from
 * {@code application.yml}'s {@code email-service} block via relaxed binding (e.g. {@code symbol-label}
 * in YAML binds to {@link Marketplace#symbolLabel()}).
 */
@ConfigurationProperties(prefix = "email-service")
public record EmailServiceProperties(Mail mail, Map<String, Marketplace> marketplaces) {

    public record Mail(String from) {
    }

    public record Marketplace(String to, String bcc, String city, String symbolLabel) {
    }
}
