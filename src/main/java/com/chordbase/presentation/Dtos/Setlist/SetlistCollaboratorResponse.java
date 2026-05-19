package com.chordbase.presentation.Dtos.Setlist;

import com.chordbase.domain.valueobjects.SetlistCollaboratorStatus;

import java.util.UUID;

public record SetlistCollaboratorResponse(
        UUID inviteUuid,
        UUID uuid,
        String userName,
        SetlistCollaboratorStatus status,
        UUID invitedByUuid,
        String invitedByUserName
) {
}
