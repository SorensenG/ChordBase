package com.chordbase.presentation.Dtos.Chord;

import java.util.UUID;

public record ChrodVizualizationResponse(UUID uuid, String chordName, String artist, String chordPro, String addBy) {
}
