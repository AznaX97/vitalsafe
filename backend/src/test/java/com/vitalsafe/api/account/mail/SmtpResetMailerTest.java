package com.vitalsafe.api.account.mail;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SmtpResetMailerTest {

    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final SmtpResetMailer mailer = new SmtpResetMailer(sender, "no-reply@vitalsafe.test");

    @Test
    void theEmailHoldsTheLinkTheExpiryAndAWarningForPeopleWhoDidNotAsk() {
        mailer.sendResetLink("me@example.com", "https://app.test/reset-password#token=abc", 30);

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(sent.capture());
        SimpleMailMessage message = sent.getValue();
        assertThat(message.getTo()).containsExactly("me@example.com");
        assertThat(message.getFrom()).isEqualTo("no-reply@vitalsafe.test");
        assertThat(message.getText()).contains("https://app.test/reset-password#token=abc")
                .contains("30 minutes")
                .contains("If you did not ask for this");
    }

    @Test
    void aMailFailureDoesNotEscape() {
        doThrow(new MailSendException("boom")).when(sender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> mailer.sendResetLink("me@example.com", "https://app.test/x", 30)).doesNotThrowAnyException();
    }

    @Test
    void theNoMailVersionSendsNothingAndNeverFails() {
        assertThatCode(() -> new NoMailResetMailer().sendResetLink("me@example.com", "https://app.test/x#token=secret", 30))
                .doesNotThrowAnyException();
    }
}