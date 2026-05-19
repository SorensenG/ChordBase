package com.chordbase.presentation.Dtos.Setlist;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReorderSetlistChordsRequest(
        @NotEmpty(message = "Chords must not be empty") List<@Valid ReorderSetlistChordRequest> chords
) {
}
