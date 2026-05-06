package com.chordbase.presentation.Dtos.User;

import com.chordbase.domain.entities.Role;
import jakarta.validation.constraints.NotBlank;

public record RegisterUserDtoRequest(@NotBlank(message = "UserName must not be empty") String userName,
                                     @NotBlank(message = "Email must not be empty ") String email,
                                     @NotBlank(message = "Password must not be empty") String password,
                                     @NotBlank(message = "Role must not be empty") Role role) {
}
