package ca.iiroc.halt.email.mail;

/**
 * Thrown when a {@code HaltMessage.listingMarket} has no matching entry in
 * {@code email-service.marketplaces}. Deliberately fails fast rather than guessing a recipient for a
 * regulatory notice (design doc §05); the Kafka listener's error handler retries it and, once exhausted,
 * logs it to the dedicated error log file like any other send failure.
 */
public class UnknownMarketplaceException extends RuntimeException {

    public UnknownMarketplaceException(String listingMarket) {
        super("No marketplace configuration for listingMarket=" + listingMarket);
    }
}
