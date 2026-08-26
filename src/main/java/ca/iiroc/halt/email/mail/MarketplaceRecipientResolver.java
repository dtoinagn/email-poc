package ca.iiroc.halt.email.mail;

import ca.iiroc.halt.email.config.EmailServiceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MarketplaceRecipientResolver {

    private final EmailServiceProperties properties;

    public EmailServiceProperties.Marketplace resolve(String listingMarket) {
        EmailServiceProperties.Marketplace marketplace = properties.marketplaces().get(listingMarket);
        if (marketplace == null) {
            throw new UnknownMarketplaceException(listingMarket);
        }
        return marketplace;
    }
}
