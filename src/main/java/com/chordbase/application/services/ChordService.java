package com.chordbase.application.services;

import com.chordbase.domain.entities.Chord;
import com.chordbase.domain.entities.User;
import com.chordbase.domain.repository.ChordRepository;
import com.chordbase.application.hellpers.ChordMetadataResolver;
import com.chordbase.application.hellpers.ChordOwnershipPolicy;
import com.chordbase.domain.valueobjects.ChordStatus;
import com.chordbase.domain.valueobjects.UserRole;
import com.chordbase.infra.external.ExternalExtrator;
import com.chordbase.presentation.Dtos.Chord.ChordExtractionResponse;
import com.chordbase.presentation.Dtos.Chord.ChordPreviewResponse;
import com.chordbase.presentation.Dtos.Chord.SimpleChordVizualizationResponse;
import com.chordbase.presentation.Dtos.Chord.FullChrodVizualizationResponse;
import com.chordbase.presentation.Dtos.Chord.ConfirmChordRequest;
import com.chordbase.presentation.Dtos.Chord.CreateChordResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class ChordService {
    private static final String UNSUPPORTED_IMPORT_FORMAT_MESSAGE =
            "A importação aceita apenas arquivos PDF ou TXT. Imagens não são suportadas no momento.";

    private final ExternalExtrator externalExtrator;
    private final ChordRepository chordRepository;
    private final ChordMetadataResolver chordMetadataResolver;
    private final ChordOwnershipPolicy chordOwnershipPolicy;

    public ChordService(
            ExternalExtrator externalExtrator,
            ChordRepository chordRepository,
            ChordMetadataResolver chordMetadataResolver,
            ChordOwnershipPolicy chordOwnershipPolicy
    ) {
        this.externalExtrator = externalExtrator;
        this.chordRepository = chordRepository;
        this.chordMetadataResolver = chordMetadataResolver;
        this.chordOwnershipPolicy = chordOwnershipPolicy;
    }

    public ChordPreviewResponse previewChord(MultipartFile file, User user) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }

        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }

        validateImportFile(file);

        var microserviceResponse = externalExtrator.extractChordPro(file);
        var chordPro = microserviceResponse.chordPro();
        var fallbackName = file.getOriginalFilename();

        if ("FAILED".equalsIgnoreCase(microserviceResponse.status()) || chordPro == null || chordPro.isBlank()) {
            throw new IllegalArgumentException(resolveExtractionFailureMessage(microserviceResponse));
        }

        if (microserviceResponse.metadata() != null && microserviceResponse.metadata().filename() != null) {
            fallbackName = microserviceResponse.metadata().filename();
        }

        var suggestedChordName = chordMetadataResolver.resolveSuggestedChordName(chordPro, fallbackName);
        var suggestedArtist = chordMetadataResolver.resolveArtist(chordPro, suggestedChordName);

        Chord chord = Chord.builder()
                .name(suggestedChordName)
                .artist(chordMetadataResolver.fallbackArtist(suggestedArtist))
                .chordPro(chordPro)
                .addByUser(user.getUserName())
                .owner(user)
                .status(ChordStatus.DRAFT.name())
                .sourceType(microserviceResponse.sourceType())
                .confidence(microserviceResponse.confidence())
                .build();

        Chord savedChord = chordRepository.save(chord);

        return new ChordPreviewResponse(
                savedChord.getUuid(),
                savedChord.getName(),
                chordMetadataResolver.fallbackArtist(savedChord.getArtist()),
                savedChord.getChordPro(),
                savedChord.getStatus()
        );
    }

    @Transactional
    public CreateChordResponse confirmChord(UUID chordUuid, ConfirmChordRequest request, User user) {
        Chord chord = findChordByUuid(chordUuid);

        chordOwnershipPolicy.validateOwner(chord, user);

        chord.setName(request.chordName());
        chord.setArtist(chordMetadataResolver.fallbackArtist(request.artist()));
        chord.setChordPro(request.chordPro());
        chord.setStatus(ChordStatus.PUBLISHED.name());

        return new CreateChordResponse(chordRepository.save(chord).getUuid());
    }

    @Transactional
    public FullChrodVizualizationResponse updateChord(UUID chordUuid, ConfirmChordRequest request, User user) {
        Chord chord = findChordByUuid(chordUuid);

        if (!isAdmin(user)) {
            chordOwnershipPolicy.validateOwner(chord, user);
        }

        chord.setName(request.chordName());
        chord.setArtist(chordMetadataResolver.fallbackArtist(request.artist()));
        chord.setChordPro(request.chordPro());

        Chord saved = chordRepository.save(chord);

        return new FullChrodVizualizationResponse(
                saved.getUuid(),
                saved.getName(),
                chordMetadataResolver.fallbackArtist(saved.getArtist()),
                saved.getChordPro(),
                saved.getAddByUser(),
                saved.getStatus()
        );
    }

    @Transactional
    public void deleteChord(UUID chordUuid, User user) {
        Chord chord = findChordByUuid(chordUuid);

        if (ChordStatus.PUBLISHED.name().equals(chord.getStatus())) {
            if (!isAdmin(user)) {
                throw new AccessDeniedException("Only admins can delete public chords");
            }
        } else {
            chordOwnershipPolicy.validateOwner(chord, user);
        }

        chord.setStatus("DELETED");
        chordRepository.save(chord);
    }


    @Transactional(readOnly = true)
    public FullChrodVizualizationResponse getChord(UUID chordUuid, User user) {

        Chord chord = findChordByUuid(chordUuid);

        if ("DELETED".equals(chord.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chord not found");
        }

        if (ChordStatus.DRAFT.name().equals(chord.getStatus())) {
            chordOwnershipPolicy.validateOwner(chord, user);
        }

        return new FullChrodVizualizationResponse(
                chord.getUuid(),
                chord.getName(),
                chordMetadataResolver.fallbackArtist(chord.getArtist()),
                chord.getChordPro(),
                chord.getAddByUser(),
                chord.getStatus()
        );

    }

    @Transactional(readOnly = true)
    public List<SimpleChordVizualizationResponse> findChord(String chordName, User user) {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }

        List<Chord> chords = chordRepository.findByNameContainingIgnoreCaseAndStatusAndOwner_UuidNot(
                chordName,
                ChordStatus.PUBLISHED.name(),
                user.getUuid()
        );

        return toSimpleChordResponse(chords);
    }

    @Transactional(readOnly = true)
    public List<SimpleChordVizualizationResponse> listMyChords(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }

        return toSimpleChordResponse(chordRepository.findByOwner_UuidAndStatusNotOrderByNameAsc(user.getUuid(), "DELETED"));
    }

    private List<SimpleChordVizualizationResponse> toSimpleChordResponse(List<Chord> chords) {
        List<SimpleChordVizualizationResponse> chordResponse = new ArrayList<>();

        for (Chord c : chords) {
            chordResponse.add(new SimpleChordVizualizationResponse(c.getUuid(), c.getName(), chordMetadataResolver.fallbackArtist(c.getArtist()), c.getAddByUser(), c.getStatus()));
        }

        return chordResponse;
    }

    private boolean isAdmin(User user) {
        return user != null && user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> UserRole.ROLE_ADMIN.equals(role.getRole()));
    }

    private String resolveExtractionFailureMessage(ChordExtractionResponse response) {
        if (response.warnings() != null) {
            for (String warning : response.warnings()) {
                if (warning != null && !warning.isBlank()) {
                    return warning;
                }
            }
        }

        return "Não foi possível extrair texto do arquivo.";
    }

    private void validateImportFile(MultipartFile file) {
        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        String contentType = Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT);
        boolean unspecifiedContentType = contentType.isBlank() || "application/octet-stream".equals(contentType);
        boolean pdf = filename.endsWith(".pdf")
                && (unspecifiedContentType || "application/pdf".equals(contentType));
        boolean text = filename.endsWith(".txt")
                && (unspecifiedContentType || "text/plain".equals(contentType));

        if (!pdf && !text) {
            throw new IllegalArgumentException(UNSUPPORTED_IMPORT_FORMAT_MESSAGE);
        }
    }

    private Chord findChordByUuid(UUID chordUuid) {
        Optional<Chord> chordOptional = chordRepository.findById(chordUuid);

        if (chordOptional.isEmpty()) {
            throw new IllegalArgumentException("Chord not found with uuid: " + chordUuid);
        }

        return chordOptional.get();
    }
}
