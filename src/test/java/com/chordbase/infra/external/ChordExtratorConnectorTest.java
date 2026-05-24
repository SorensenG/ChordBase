package com.chordbase.infra.external;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChordExtratorConnectorTest {
    @Test
    void forwardsMultipartWithoutMaterializingUploadAsByteArray() throws IOException {
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/extractions/chordpro", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            byte[] response = """
                    {
                      "requestId": "request-id",
                      "status": "NEEDS_REVIEW",
                      "sourceType": "OCR_PDF",
                      "chordPro": "[C]Teste",
                      "confidence": 0.7,
                      "warnings": [],
                      "metadata": {
                        "filename": "cifra.pdf",
                        "mimeType": "application/pdf",
                        "fileSizeBytes": 3,
                        "pagesProcessed": 1,
                        "tokenCount": 1,
                        "lineCount": 1
                      },
                      "processingTimeMs": 10
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            ChordExtratorConnector connector = new ChordExtratorConnector(
                    RestClient.builder(),
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "/api/v1/extractions/chordpro"
            );
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "cifra.pdf",
                    "application/pdf",
                    new byte[]{1, 2, 3}
            ) {
                @Override
                public byte[] getBytes() throws IOException {
                    throw new AssertionError("The connector must stream the multipart upload");
                }
            };

            var response = connector.extractChordPro(file);

            assertEquals("OCR_PDF", response.sourceType());
            assertTrue(contentType.get().startsWith("multipart/form-data;boundary="));
            assertTrue(requestBody.get().contains("filename=\"cifra.pdf\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void preservesExtractorBusyResponseForApiLayer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/extractions/chordpro", exchange -> {
            byte[] response = """
                    {
                      "code": "OCR_BUSY",
                      "message": "O processamento de documentos está ocupado. Tente novamente em instantes."
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("Retry-After", "2");
            exchange.sendResponseHeaders(503, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            ChordExtratorConnector connector = new ChordExtratorConnector(
                    RestClient.builder(),
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "/api/v1/extractions/chordpro"
            );
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "cifra.pdf",
                    "application/pdf",
                    new byte[]{1, 2, 3}
            );

            ExtractorBusyException exception = assertThrows(
                    ExtractorBusyException.class,
                    () -> connector.extractChordPro(file)
            );
            assertEquals("2", exception.getRetryAfter());
            assertTrue(exception.getMessage().contains("processamento de documentos"));
        } finally {
            server.stop(0);
        }
    }
}
