package com.chordbase.presentation.Dtos.Admin;

import jakarta.validation.constraints.NotNull;

public record UpdateUserActiveRequest(
        @NotNull(message = "Active must not be empty") Boolean active
) {
}
