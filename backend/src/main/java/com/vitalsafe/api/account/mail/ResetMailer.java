package com.vitalsafe.api.account.mail;

public interface ResetMailer {

    void sendResetLink(String toEmail, String link, int minutesValid);
}