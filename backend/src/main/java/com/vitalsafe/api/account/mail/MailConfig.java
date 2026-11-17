package com.vitalsafe.api.account.mail;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class MailConfig {

    // Spring only creates a JavaMailSender when spring.mail.host is set
    @Bean
    public ResetMailer resetMailer(ObjectProvider<JavaMailSender> senders,
                                   @Value("${app.mail.from:no-reply@vitalsafe.local}") String from) {
        JavaMailSender sender = senders.getIfAvailable();
        return sender != null ? new SmtpResetMailer(sender, from) : new NoMailResetMailer();
    }

    // Emails go out on a background thread, so a reset for a real address takes no longer than one for an unknown address.
    // A full queue drops the extra emails instead of piling up.
    @Bean(destroyMethod = "shutdown")
    public ExecutorService resetMailExecutor() {
        return new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(100),
                new ThreadPoolExecutor.DiscardPolicy());
    }
}