package com.chordbase.presentation.controller;

import com.chordbase.application.hellpers.AuthenticatedUserResolver;
import com.chordbase.application.services.ChordService;
import com.chordbase.domain.entities.User;
import com.chordbase.infra.security.UserDetailsImp;
import com.chordbase.presentation.Dtos.Chord.SimpleChordVizualizationResponse;
import com.chordbase.presentation.Dtos.Chord.ChordPreviewResponse;
import com.chordbase.presentation.Dtos.Chord.FullChrodVizualizationResponse;
import com.chordbase.presentation.Dtos.Chord.ConfirmChordRequest;
import com.chordbase.presentation.Dtos.Chord.CreateChordResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/chord")
public class ChordController {
    private final ChordService chordService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    public ChordController(ChordService chordService, AuthenticatedUserResolver authenticatedUserResolver) {
        this.chordService = chordService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }


    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ChordPreviewResponse> previewChord(@AuthenticationPrincipal UserDetailsImp userImp, @RequestParam("file") MultipartFile file) {

        User user = authenticatedUserResolver.resolve(userImp);

        var preview = chordService.previewChord(file, user);

        return ResponseEntity.status(HttpStatus.OK).body(preview);
    }


    @PutMapping(value = "/confirm/{chordUuid}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CreateChordResponse> confirmChord(@AuthenticationPrincipal UserDetailsImp userImp, @PathVariable UUID chordUuid, @Valid @RequestBody ConfirmChordRequest request) {

        User user = authenticatedUserResolver.resolve(userImp);

        var chord = chordService.confirmChord(chordUuid, request, user);

        return ResponseEntity.status(HttpStatus.OK).body(chord);
    }


    @GetMapping("/me")
    public ResponseEntity<List<SimpleChordVizualizationResponse>> listMyChords(@AuthenticationPrincipal UserDetailsImp userImp) {

        User user = authenticatedUserResolver.resolve(userImp);

        var chords = chordService.listMyChords(user);

        return ResponseEntity.status(HttpStatus.OK).body(chords);
    }


    @GetMapping("/search")
    public ResponseEntity<List<SimpleChordVizualizationResponse>> searchChord(@AuthenticationPrincipal UserDetailsImp userImp, @RequestParam String chordName) {

        User user = authenticatedUserResolver.resolve(userImp);

        var chord = chordService.findChord(chordName, user);

        return ResponseEntity.status(HttpStatus.OK).body(chord);
    }


    @PutMapping(value = "/{chordUuid}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FullChrodVizualizationResponse> updateChord(@AuthenticationPrincipal UserDetailsImp userImp, @PathVariable UUID chordUuid, @Valid @RequestBody ConfirmChordRequest request) {

        User user = authenticatedUserResolver.resolve(userImp);

        var chord = chordService.updateChord(chordUuid, request, user);

        return ResponseEntity.status(HttpStatus.OK).body(chord);
    }


    @DeleteMapping("/{chordUuid}")
    public ResponseEntity<Void> deleteChord(@AuthenticationPrincipal UserDetailsImp userImp, @PathVariable UUID chordUuid) {

        User user = authenticatedUserResolver.resolve(userImp);

        chordService.deleteChord(chordUuid, user);

        return ResponseEntity.noContent().build();
    }


    @GetMapping("/{chordUuid}")
    public ResponseEntity<FullChrodVizualizationResponse> getChord(@AuthenticationPrincipal UserDetailsImp userImp, @PathVariable UUID chordUuid) {

        User user = authenticatedUserResolver.resolve(userImp);

        var chord = chordService.getChord(chordUuid, user);

        return ResponseEntity.status(HttpStatus.OK).body(chord);

    }
}
