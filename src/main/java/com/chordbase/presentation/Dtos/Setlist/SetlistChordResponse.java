package com.chordbase.presentation.Dtos.Setlist;

import java.util.UUID;

public record SetlistChordResponse(
        UUID uuid,
        String chordName,
        String artist,
        String addBy,
        Integer position
) {
}
