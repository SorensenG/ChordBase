package com.chordbase.domain.valueobjects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Locale;
import java.util.regex.Pattern;

@Embeddable
public class EmailAddress {
    private static final int MAX_LENGTH = 254;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");

    @Column(name = "email", nullable = false, unique = true, length = 254)
    private String value;

    protected EmailAddress() {
    }

    private EmailAddress(String value) {
        this.value = value;
    }

    public static EmailAddress of(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Email must not be empty");
        }

        String normalizedValue = value.trim().toLowerCase(Locale.ROOT);

        if (normalizedValue.isEmpty()) {
            throw new IllegalArgumentException("Email must not be empty");
        }

        if (normalizedValue.length() > MAX_LENGTH || !EMAIL_PATTERN.matcher(normalizedValue).matches()) {
            throw new IllegalArgumentException("Email must be valid");
        }

        return new EmailAddress(normalizedValue);
    }

    public String value() {
        return value;
    }
}
