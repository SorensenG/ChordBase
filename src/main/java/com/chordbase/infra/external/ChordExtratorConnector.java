package com.chordbase.infra.external;

import com.chordbase.presentation.Dtos.Chord.ChordExtractionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Component
public class ChordExtratorConnector implements ExternalExtrator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChordExtratorConnector.class);
    private final RestClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String extractionPath;

    public ChordExtratorConnector(
            RestClient.Builder restClientBuilder,
            @Value("${chordpro.extractor.base-url:http://localhost:8000}") String baseUrl,
            @Value("${chordpro.extractor.path:/api/v1/extractions/chordpro}") String extractionPath
    ) {
        this.client = restClientBuilder
                .requestFactory(new SimpleClientHttpRequestFactory())
                .baseUrl(baseUrl)
                .build();
        this.extractionPath = extractionPath;
    }


    @Override
    public ChordExtractionResponse extractChordPro(MultipartFile file) {
        try {
            InputStreamResource fileResource = new InputStreamResource(file.getInputStream()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }

                @Override
                public long contentLength() {
                    return file.getSize();
                }
            };

            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(resolveContentType(file));

            HttpEntity<InputStreamResource> filePart = new HttpEntity<>(fileResource, fileHeaders);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", filePart);

            LOGGER.info("Forwarding chord upload to extractor: filename={}, contentType={}, bytes={}",
                    file.getOriginalFilename(), file.getContentType(), file.getSize());

            ChordExtractionResponse response = client.post()
                    .uri(extractionPath)
                    .header("X-Request-ID", UUID.randomUUID().toString())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(ChordExtractionResponse.class);

            LOGGER.info("Chord extractor completed: filename={}, sourceType={}, status={}",
                    file.getOriginalFilename(),
                    response == null ? null : response.sourceType(),
                    response == null ? null : response.status());

            return response;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Erro ao ler arquivo enviado.", exception);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 503) {
                throw new ExtractorBusyException(
                        resolveExtractorMessage(exception),
                        exception.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER),
                        exception
                );
            }
            throw new IllegalArgumentException(resolveExtractorMessage(exception), exception);
        }
    }

    private String resolveExtractorMessage(RestClientResponseException exception) {
        try {
            JsonNode body = objectMapper.readTree(exception.getResponseBodyAsString());
            JsonNode message = body.get("message");
            if (message != null && message.isTextual() && !message.asText().isBlank()) {
                return message.asText();
            }
        } catch (Exception ignored) {
            // Fall back to a stable message below.
        }

        return "Não foi possível processar o arquivo enviado.";
    }

    private MediaType resolveContentType(MultipartFile file) {
        if (file.getContentType() == null || file.getContentType().isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }

        return MediaType.parseMediaType(file.getContentType());
    }
}
