package com.chordbase.presentation.Dtos.Setlist;

import com.chordbase.domain.valueobjects.SetlistVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateSetlistRequest(
        @NotBlank(message = "Setlist name must not be empty") String name,
        String description,
        @NotNull(message = "Setlist visibility must not be empty") SetlistVisibility visibility
) {
}
