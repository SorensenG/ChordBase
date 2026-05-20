package com.chordbase.domain.valueobjects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailAddressTest {
    @Test
    void normalizesEmailToLowerCase() {
        EmailAddress email = EmailAddress.of("  Gabriel@Example.COM  ");

        assertEquals("gabriel@example.com", email.value());
    }

    @Test
    void rejectsEmptyEmail() {
        assertThrows(IllegalArgumentException.class, () -> EmailAddress.of(" "));
    }

    @Test
    void rejectsEmailWithoutAtSign() {
        assertThrows(IllegalArgumentException.class, () -> EmailAddress.of("gabriel.example.com"));
    }

    @Test
    void rejectsEmailWithSpaces() {
        assertThrows(IllegalArgumentException.class, () -> EmailAddress.of("gabriel dev@example.com"));
    }

    @Test
    void rejectsEmailWithoutDomainDot() {
        assertThrows(IllegalArgumentException.class, () -> EmailAddress.of("gabriel@example"));
    }

    @Test
    void rejectsEmailWithShortTopLevelDomain() {
        assertThrows(IllegalArgumentException.class, () -> EmailAddress.of("gabriel@example.c"));
    }
}
