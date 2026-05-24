package com.chordbase.infra.external;

public class ExtractorBusyException extends RuntimeException {
    private final String retryAfter;

    public ExtractorBusyException(String message, String retryAfter, Throwable cause) {
        super(message, cause);
        this.retryAfter = retryAfter == null || retryAfter.isBlank() ? "2" : retryAfter;
    }

    public String getRetryAfter() {
        return retryAfter;
    }
}
