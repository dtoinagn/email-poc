package ca.iiroc.halt.email.template;

import ca.ciro.halt.model.HaltMessage;
import ca.iiroc.halt.email.config.EmailServiceProperties;
import ca.iiroc.halt.email.format.HaltTimeFormatter;
import ca.iiroc.halt.email.format.ReasonTranslator;
import ca.iiroc.halt.email.format.YesNoFormatter;
import ca.iiroc.halt.email.mail.EmailContent;
import ca.iiroc.halt.email.mail.MarketplaceRecipientResolver;
import ca.iiroc.halt.email.routing.EmailTrigger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/** Field mapping per design doc §04: everything the wire model leaves raw or missing gets resolved here. */
@Component
@RequiredArgsConstructor
public class EmailContentBuilder {

    private final TemplateEngine templateEngine;
    private final EmailCopy emailCopy;
    private final MarketplaceRecipientResolver marketplaceResolver;
    private final ReasonTranslator reasonTranslator;
    private final EmailServiceProperties properties;

    public EmailContent build(EmailTrigger trigger, HaltMessage message) {
        EmailServiceProperties.Marketplace marketplace = marketplaceResolver.resolve(message.getListingMarket());

        Context context = new Context();
        context.setVariable("heading", emailCopy.subject(trigger));
        context.setVariable("city", marketplace.city());
        context.setVariable("symbolLabel", marketplace.symbolLabel());
        context.setVariable("currentDate", HaltTimeFormatter.currentDate());
        context.setVariable("issueName", message.getIssueName());
        context.setVariable("symbol", message.getSymbol());
        context.setVariable("allIssue", YesNoFormatter.bilingual(message.getAllIssue()));
        context.setVariable("haltTime", HaltTimeFormatter.format(message.getHaltTime()));
        context.setVariable("resumptionTime", HaltTimeFormatter.format(message.getResumptionTime()));
        context.setVariable("resumptionTimeLabel", emailCopy.get("resumptionTimeLabel"));
        context.setVariable("introRemainHalted", emailCopy.get("intro.remainHalted"));
        context.setVariable("reasonEn", reasonTranslator.english(message));
        context.setVariable("reasonFr", reasonTranslator.french(message));

        String html = templateEngine.process(trigger.key(), context);
        String subject = emailCopy.subject(trigger);

        return new EmailContent(properties.mail().from(), marketplace.to(), marketplace.bcc(), subject, html);
    }
}
