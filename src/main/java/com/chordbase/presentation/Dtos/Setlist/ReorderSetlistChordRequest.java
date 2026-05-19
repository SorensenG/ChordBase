package com.chordbase.presentation.Dtos.Setlist;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReorderSetlistChordRequest(
        @NotNull(message = "Chord uuid must not be empty") UUID chordUuid,
        @NotNull(message = "Position must not be empty") Integer position
) {
}
