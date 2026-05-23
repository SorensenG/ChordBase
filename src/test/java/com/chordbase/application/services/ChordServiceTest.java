package com.chordbase.application.services;

import com.chordbase.application.hellpers.ChordMetadataResolver;
import com.chordbase.application.hellpers.ChordOwnershipPolicy;
import com.chordbase.domain.entities.Chord;
import com.chordbase.domain.entities.User;
import com.chordbase.domain.repository.ChordRepository;
import com.chordbase.domain.valueobjects.EmailAddress;
import com.chordbase.domain.valueobjects.UserName;
import com.chordbase.infra.external.ExternalExtrator;
import com.chordbase.presentation.Dtos.Chord.ChordExtractionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChordServiceTest {
    private final Map<UUID, Chord> chords = new HashMap<>();
    private ChordRepository chordRepository;

    @BeforeEach
    void setUp() {
        chords.clear();
        chordRepository = repositoryProxy(ChordRepository.class, (proxy, method, args) -> switch (method.getName()) {
            case "save" -> {
                Chord chord = (Chord) args[0];
                if (chord.getUuid() == null) {
                    chord.setUuid(UUID.randomUUID());
                }
                chords.put(chord.getUuid(), chord);
                yield chord;
            }
            case "findById" -> Optional.ofNullable(chords.get((UUID) args[0]));
            case "findByNameContainingIgnoreCaseAndStatusAndOwner_UuidNot" -> {
                String name = ((String) args[0]).toLowerCase();
                String status = (String) args[1];
                UUID ownerUuid = (UUID) args[2];
                yield chords.values().stream()
                        .filter(chord -> chord.getName().toLowerCase().contains(name))
                        .filter(chord -> status.equals(chord.getStatus()))
                        .filter(chord -> chord.getOwner() != null && !ownerUuid.equals(chord.getOwner().getUuid()))
                        .toList();
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    @Test
    void previewChordStoresImageOcrResult() {
        ChordService service = chordService(file -> extractionResponse(
                "NEEDS_REVIEW",
                "OCR_IMAGE",
                "Amazing grace\n[C]How sweet the sound",
                0.76,
                new String[]{"Imagem processada por OCR. Revise o resultado antes de salvar."}
        ));

        var response = service.previewChord(imageFile(), user());

        assertNotNull(response.uuid());
        assertEquals("Amazing grace", response.chordName());
        assertEquals("Amazing grace\n[C]How sweet the sound", response.chordPro());
        Chord saved = chords.get(response.uuid());
        assertEquals("OCR_IMAGE", saved.getSourceType());
        assertEquals(0.76, saved.getConfidence());
    }

    @Test
    void previewChordRejectsFailedImageExtractionWithoutSavingDraft() {
        ChordService service = chordService(file -> extractionResponse(
                "FAILED",
                "OCR_IMAGE",
                null,
                0.0,
                new String[]{"Não foi possível extrair texto do arquivo."}
        ));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.previewChord(imageFile(), user())
        );

        assertEquals("Não foi possível extrair texto do arquivo.", exception.getMessage());
        assertTrue(chords.isEmpty());
    }

    @Test
    void previewChordRejectsOcrImageWithoutChordSignalsWithoutSavingDraft() {
        ChordService service = chordService(file -> extractionResponse(
                "NEEDS_REVIEW",
                "OCR_IMAGE",
                "Lista de compras\nLeite e pao",
                0.72,
                new String[]{"Imagem processada por OCR. Revise o resultado antes de salvar."}
        ));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.previewChord(imageFile(), user())
        );

        assertEquals("Não consegui identificar uma cifra nessa imagem. Envie uma foto mais nítida, PDF ou TXT.", exception.getMessage());
        assertTrue(chords.isEmpty());
    }

    @Test
    void searchChordReturnsPublishedChordOwnedByAnotherUser() {
        User authenticatedUser = user("user@example.com", "gabriel");
        storeChord("Vento", "PUBLISHED", user("other@example.com", "outro"));

        List<?> result = chordService(file -> null).findChord("vento", authenticatedUser);

        assertEquals(1, result.size());
    }

    @Test
    void searchChordOmitsPublishedChordOwnedByAuthenticatedUser() {
        User authenticatedUser = user("user@example.com", "gabriel");
        storeChord("Vento", "PUBLISHED", authenticatedUser);

        List<?> result = chordService(file -> null).findChord("vento", authenticatedUser);

        assertTrue(result.isEmpty());
    }

    @Test
    void searchChordOmitsDraftAndDeletedChords() {
        User authenticatedUser = user("user@example.com", "gabriel");
        User otherUser = user("other@example.com", "outro");
        storeChord("Vento draft", "DRAFT", otherUser);
        storeChord("Vento deleted", "DELETED", otherUser);

        List<?> result = chordService(file -> null).findChord("vento", authenticatedUser);

        assertTrue(result.isEmpty());
    }

    private ChordService chordService(ExternalExtrator externalExtrator) {
        return new ChordService(
                externalExtrator,
                chordRepository,
                new ChordMetadataResolver(),
                new ChordOwnershipPolicy()
        );
    }

    private ChordExtractionResponse extractionResponse(
            String status,
            String sourceType,
            String chordPro,
            Double confidence,
            String[] warnings
    ) {
        return new ChordExtractionResponse(
                UUID.randomUUID().toString(),
                status,
                sourceType,
                chordPro,
                confidence,
                warnings,
                new ChordExtractionResponse.Metadata(
                        "amazing-grace.png",
                        null,
                        "image/png",
                        1024L,
                        1,
                        chordPro == null ? 0 : 3,
                        chordPro == null ? 0 : 1
                ),
                120L
        );
    }

    private MockMultipartFile imageFile() {
        return new MockMultipartFile(
                "file",
                "amazing-grace.png",
                "image/png",
                new byte[]{1, 2, 3}
        );
    }

    private User user() {
        return user("user@example.com", "gabriel");
    }

    private User user(String email, String name) {
        return User.builder()
                .uuid(UUID.randomUUID())
                .email(EmailAddress.of(email))
                .userName(UserName.of(name))
                .build();
    }

    private void storeChord(String name, String status, User owner) {
        Chord chord = Chord.builder()
                .uuid(UUID.randomUUID())
                .name(name)
                .artist("Artista")
                .addByUser(owner.getUserName())
                .owner(owner)
                .status(status)
                .build();
        chords.put(chord.getUuid(), chord);
    }

    @SuppressWarnings("unchecked")
    private <T> T repositoryProxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
}
