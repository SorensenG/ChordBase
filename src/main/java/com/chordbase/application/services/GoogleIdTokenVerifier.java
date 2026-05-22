package com.chordbase.application.services;

public interface GoogleIdTokenVerifier {
    GoogleAccount verify(String idToken);

    record GoogleAccount(String email, boolean emailVerified, String name, String pictureUrl) {
    }
}
