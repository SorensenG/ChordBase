package com.chordbase.presentation.Dtos.Chord;

import java.util.UUID;

public record ChordPreviewResponse(
        UUID uuid,
        String chordName,
        String artist,
        String chordPro,
        String status
) {
}
