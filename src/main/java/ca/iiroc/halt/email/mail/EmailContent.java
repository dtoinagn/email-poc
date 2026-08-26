package ca.iiroc.halt.email.mail;

/** A fully rendered halt/resumption notice, ready to hand to {@link HaltEmailSender}. */
public record EmailContent(String from, String to, String bcc, String subject, String htmlBody) {
}
