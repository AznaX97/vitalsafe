package com.vitalsafe.api.account;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {

    @Test
    void aLongEnoughPasswordIsAccepted() {
        assertThat(PasswordPolicy.problem("a-long-enough-pw")).isNull();
        assertThat(PasswordPolicy.problem("x".repeat(12))).isNull();
        assertThat(PasswordPolicy.problem("x".repeat(72))).isNull();
    }

    @Test
    void aShortOrMissingPasswordIsRefused() {
        assertThat(PasswordPolicy.problem("x".repeat(11))).contains("at least 12");
        assertThat(PasswordPolicy.problem("")).isNotNull();
        assertThat(PasswordPolicy.problem(null)).isNotNull();
    }

    @Test
    void moreThanSeventyTwoBytesIsRefusedEvenWhenTheCharacterCountIsLow() {
        assertThat(PasswordPolicy.problem("x".repeat(73))).contains("72 bytes");
        // 40 characters but 80 bytes
        assertThat(PasswordPolicy.problem("\u00e9".repeat(40))).contains("72 bytes");
        // 36 characters and exactly 72 bytes
        assertThat(PasswordPolicy.problem("\u00e9".repeat(36))).isNull();
    }
}