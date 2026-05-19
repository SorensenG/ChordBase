package com.chordbase.presentation.Dtos.Admin;

import com.chordbase.domain.valueobjects.UserRole;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UpdateUserRolesRequest(
        @NotEmpty(message = "Roles must not be empty") List<UserRole> roles
) {
}
