package com.chordbase.application.services;

import com.chordbase.domain.entities.Chord;
import com.chordbase.domain.repository.ChordRepository;
import com.chordbase.infra.external.ExternalExtrator;
import com.chordbase.presentation.Dtos.Chord.CreateChrodRequest;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ChordService {

    private final ExternalExtrator externalExtrator;
    private final ChordRepository chordRepository;

    public ChordService(ExternalExtrator externalExtrator, ChordRepository chordRepository) {
        this.externalExtrator = externalExtrator;
        this.chordRepository = chordRepository;
    }

    public UUID saveChrod(CreateChrodRequest request) {
        if (request.file() == null || request.file().isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }

        var microserviceRespponse = externalExtrator.extractChordPro(request.file());



        Chord chord = Chord.builder().name(microserviceRespponse.metadata().filename())
                .chordPro(microserviceRespponse.chordPro())
                .addByUser(request.userName())
                .status(microserviceRespponse.status())
                .sourceType(microserviceRespponse.sourceType())
                .confidence(microserviceRespponse.confidence())
                .build();

        return chordRepository.save(chord).getUuid();
    }
}
