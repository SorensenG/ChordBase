package com.chordbase.presentation.controller;

import com.chordbase.application.services.ChordService;
import com.chordbase.domain.entities.User;
import com.chordbase.infra.security.UserDetailsImp;
import com.chordbase.presentation.Dtos.Chord.CreateChordResponse;
import com.chordbase.presentation.Dtos.Chord.CreateChrodRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/chord")
public class ChordController {
    private final ChordService chordService;

    public ChordController(ChordService chordService) {
        this.chordService = chordService;
    }


    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CreateChordResponse> CreateChord(@AuthenticationPrincipal UserDetailsImp userImp, @RequestParam("file") MultipartFile file) {

        User user = userImp.getUser();

        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        var userName = user.getUserName();


        var chordUuid = chordService.saveChrod(new CreateChrodRequest(file, userName));

        return ResponseEntity.status(HttpStatus.CREATED).body(new CreateChordResponse(chordUuid));
    }


}
