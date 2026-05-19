package com.chordbase.presentation.Dtos.Setlist;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateSetlistInviteRequest(
        @NotNull(message = "User uuid must not be empty") UUID userUuid
) {
}
