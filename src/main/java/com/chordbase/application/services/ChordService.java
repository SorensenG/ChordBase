package com.chordbase.application.services;

import com.chordbase.domain.entities.Chord;
import com.chordbase.domain.entities.User;
import com.chordbase.domain.repository.ChordRepository;
import com.chordbase.application.hellpers.ChordMetadataResolver;
import com.chordbase.application.hellpers.ChordOwnershipPolicy;
import com.chordbase.domain.valueobjects.ChordStatus;
import com.chordbase.infra.external.ExternalExtrator;
import com.chordbase.presentation.Dtos.Chord.ChordPreviewResponse;
import com.chordbase.presentation.Dtos.Chord.ChordSearchResponse;
import com.chordbase.presentation.Dtos.Chord.ChrodVizualizationResponse;
import com.chordbase.presentation.Dtos.Chord.ConfirmChordRequest;
import com.chordbase.presentation.Dtos.Chord.CreateChordResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ChordService {
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

        var microserviceResponse = externalExtrator.extractChordPro(file);
        var chordPro = microserviceResponse.chordPro();
        var fallbackName = file.getOriginalFilename();

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

    @Transactional(readOnly = true)
    public ChrodVizualizationResponse getChord(UUID chordUuid, User user) {

        Chord chord = findChordByUuid(chordUuid);

        if (ChordStatus.DRAFT.name().equals(chord.getStatus())) {
            chordOwnershipPolicy.validateOwner(chord, user);
        }

        return new ChrodVizualizationResponse(
                chord.getUuid(),
                chord.getName(),
                chordMetadataResolver.fallbackArtist(chord.getArtist()),
                chord.getChordPro(),
                chord.getAddByUser()
        );

    }

    public List<ChordSearchResponse> findChord(String chordName) {
        List<Chord> chords = chordRepository.findByNameContainingIgnoreCaseAndStatus(chordName, ChordStatus.PUBLISHED.name());

        if (chords.isEmpty()) {
            throw new IllegalArgumentException("Chord not found with name: " + chordName);
        }

        List<ChordSearchResponse> chordResponse = new ArrayList<>();

        for (Chord c : chords) {
            chordResponse.add(new ChordSearchResponse(c.getUuid(), c.getName(), chordMetadataResolver.fallbackArtist(c.getArtist()), c.getAddByUser()));
        }

        return chordResponse;
    }

    private Chord findChordByUuid(UUID chordUuid) {
        Optional<Chord> chordOptional = chordRepository.findById(chordUuid);

        if (chordOptional.isEmpty()) {
            throw new IllegalArgumentException("Chord not found with uuid: " + chordUuid);
        }

        return chordOptional.get();
    }
}
