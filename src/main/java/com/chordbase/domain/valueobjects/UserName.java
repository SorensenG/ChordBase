package com.chordbase.domain.valueobjects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Locale;
import java.util.regex.Pattern;

@Embeddable
public class UserName {
    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 30;
    private static final Pattern USER_NAME_PATTERN = Pattern.compile("^[a-z0-9_.]+$");

    @Column(name = "user_name", nullable = false, unique = true)
    private String value;

    protected UserName() {
    }

    private UserName(String value) {
        this.value = value;
    }

    public static UserName of(String value) {
        if (value == null) {
            throw new IllegalArgumentException("UserName must not be empty");
        }

        String normalizedValue = value.trim().toLowerCase(Locale.ROOT);

        if (normalizedValue.length() < MIN_LENGTH || normalizedValue.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("UserName must have between 3 and 30 characters");
        }

        if (!USER_NAME_PATTERN.matcher(normalizedValue).matches()) {
            throw new IllegalArgumentException("UserName can contain only letters, numbers, underscore and dot");
        }

        return new UserName(normalizedValue);
    }

    public String value() {
        return value;
    }
}
