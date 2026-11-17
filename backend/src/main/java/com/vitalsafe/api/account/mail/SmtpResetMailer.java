package com.vitalsafe.api.account.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public class SmtpResetMailer implements ResetMailer {

    private static final Logger log = LoggerFactory.getLogger(SmtpResetMailer.class);

    private final JavaMailSender sender;
    private final String from;

    public SmtpResetMailer(JavaMailSender sender, String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void sendResetLink(String toEmail, String link, int minutesValid) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject("Reset your VitalSafe password");
        message.setText("Someone asked to reset the password of your VitalSafe account.\n\n"
                + "To choose a new password, open this link:\n" + link + "\n\n"
                + "The link works once and expires in " + minutesValid + " minutes.\n\n"
                + "If you did not ask for this, ignore this email. Your password has not been changed.");
        try {
            sender.send(message);
        } catch (MailException e) {
            // Never log the address or the link. The type of the error is enough to investigate.
            log.error("Could not send a password reset email: {}", e.getClass().getSimpleName());
        }
    }
}