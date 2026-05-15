package com.chordbase.presentation.Dtos.Chord;

import jakarta.validation.constraints.NotBlank;

public record ConfirmChordRequest(
        @NotBlank(message = "ChordName must not be empty") String chordName,
        String artist,
        @NotBlank(message = "ChordPro must not be empty") String chordPro
) {
}
