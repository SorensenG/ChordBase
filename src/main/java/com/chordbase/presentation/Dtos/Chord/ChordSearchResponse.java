package com.chordbase.presentation.Dtos.Chord;

import java.util.UUID;

public record ChordSearchResponse(UUID uuid, String chordName, String artist, String addBy) {
}
