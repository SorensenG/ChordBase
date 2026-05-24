package com.chordbase.presentation.handlers;

import com.chordbase.infra.external.ExtractorBusyException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void handlesMaxUploadSizeExceededWithFriendlyPayload() {
        var handler = new GlobalExceptionHandler();

        var response = handler.handleMaxUploadSizeExceeded();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message())
                .isEqualTo("Arquivo excede o tamanho máximo permitido.");
    }

    @Test
    void preservesExtractorRetryAfterWhenCapacityIsExhausted() {
        var handler = new GlobalExceptionHandler();

        var response = handler.handleExtractorBusy(new ExtractorBusyException("busy", "3", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("3");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("busy");
    }

    @Test
    void multipartLimitUsesThirtyMegabyteDefault() throws IOException {
        var properties = loadApplicationProperties();

        assertThat(properties.getProperty("spring.servlet.multipart.max-file-size"))
                .isEqualTo("${MAX_UPLOAD_SIZE:30MB}");
        assertThat(properties.getProperty("spring.servlet.multipart.max-request-size"))
                .isEqualTo("${MAX_UPLOAD_SIZE:30MB}");
    }

    private Properties loadApplicationProperties() throws IOException {
        var properties = new Properties();
        try (var input = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
            properties.load(input);
        }
        return properties;
    }
}
