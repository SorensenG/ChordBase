package com.chordbase.presentation.Dtos.Setlist;

import com.chordbase.domain.valueobjects.SetlistVisibility;

import java.util.List;
import java.util.UUID;

public record SetlistResponse(
        UUID uuid,
        String name,
        String description,
        SetlistVisibility visibility,
        UUID ownerUuid,
        String ownerUserName,
        List<SetlistChordResponse> chords,
        List<SetlistCollaboratorResponse> collaborators
) {
}
