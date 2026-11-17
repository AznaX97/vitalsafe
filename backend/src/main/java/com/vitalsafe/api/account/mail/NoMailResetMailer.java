package com.vitalsafe.api.account.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Used when no mail server is configured. It sends nothing, and above all it never writes the link to the log. */
public class NoMailResetMailer implements ResetMailer {

    private static final Logger log = LoggerFactory.getLogger(NoMailResetMailer.class);

    @Override
    public void sendResetLink(String toEmail, String link, int minutesValid) {
        log.warn("A password reset was requested but no mail server is configured (spring.mail.host), so nothing was sent");
    }
}