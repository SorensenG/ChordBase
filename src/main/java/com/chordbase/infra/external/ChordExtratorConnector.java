package com.chordbase.infra.external;

import com.chordbase.presentation.Dtos.Chord.ChordExtractionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Component
public class ChordExtratorConnector implements ExternalExtrator {
    private final RestClient client;
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
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(resolveContentType(file));

            HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(fileResource, fileHeaders);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", filePart);

            return client.post()
                    .uri(extractionPath)
                    .header("X-Request-ID", UUID.randomUUID().toString())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(ChordExtractionResponse.class);

        } catch (IOException exception) {
            throw new IllegalArgumentException("Erro ao ler arquivo enviado.", exception);
        }
    }

    private MediaType resolveContentType(MultipartFile file) {
        if (file.getContentType() == null || file.getContentType().isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }

        return MediaType.parseMediaType(file.getContentType());
    }
}
