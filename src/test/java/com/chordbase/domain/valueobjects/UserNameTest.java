package com.chordbase.domain.valueobjects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserNameTest {
    @Test
    void normalizesUserNameToLowerCase() {
        UserName userName = UserName.of("Gabriel.Dev");

        assertEquals("gabriel.dev", userName.value());
    }

    @Test
    void rejectsUserNameWithSpaces() {
        assertThrows(IllegalArgumentException.class, () -> UserName.of("gabriel dev"));
    }

    @Test
    void rejectsUserNameWithInvalidCharacters() {
        assertThrows(IllegalArgumentException.class, () -> UserName.of("gabriel-dev"));
    }

    @Test
    void rejectsTooShortUserName() {
        assertThrows(IllegalArgumentException.class, () -> UserName.of("ga"));
    }
}
