package com.chordbase.presentation.Dtos.Chord;

import java.util.UUID;

public record SimpleChordVizualizationResponse(UUID uuid, String chordName, String artist, String addBy, String status) {
}
