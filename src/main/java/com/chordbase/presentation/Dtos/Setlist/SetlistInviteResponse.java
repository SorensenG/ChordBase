package com.chordbase.presentation.Dtos.Setlist;

import com.chordbase.domain.valueobjects.SetlistCollaboratorStatus;

import java.util.UUID;

public record SetlistInviteResponse(
        UUID inviteUuid,
        SetlistCollaboratorStatus status,
        UUID setlistUuid,
        String setlistName,
        UUID ownerUuid,
        String ownerUserName,
        UUID invitedByUuid,
        String invitedByUserName
) {
}
