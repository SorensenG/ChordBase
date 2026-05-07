package com.chordbase.application.services;

import com.chordbase.domain.entities.Role;
import com.chordbase.domain.entities.User;
import com.chordbase.domain.repository.UserRepository;
import com.chordbase.infra.security.JwtTokenService;
import com.chordbase.infra.security.SecurityConfiguration;
import com.chordbase.presentation.Dtos.User.RegisterUserDtoRequest;
import com.chordbase.presentation.Dtos.User.RegisterUserResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    final private AuthenticationManager authenticationManager;

    final private JwtTokenService jwtTokenService;

    final private UserRepository userRepository;
    final private SecurityConfiguration securityConfiguration;


    public UserService(AuthenticationManager authenticationManager, JwtTokenService jwtTokenService, UserRepository userRepository, SecurityConfiguration securityConfiguration) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
        this.userRepository = userRepository;
        this.securityConfiguration = securityConfiguration;
    }

    public RegisterUserResponse registerUser(RegisterUserDtoRequest request) {

        userRepository.findByEmail(request.email()).ifPresent(u -> {
            throw new IllegalArgumentException("Email already in use");
        });

        User user = User.builder()
                .userName(request.userName())
                        .email(request.email())
                                .passwordHash(securityConfiguration.passwordEncoder().encode(request.password()))
                                        .roles(List.of(Role.builder().role(request.role()).build())).

                build();

    userRepository.save(user);

        return new RegisterUserResponse(user.getUuid(), user.getEmail(), user.getUserName(), user.getRoles().stream().map(Role::getRole).toList());

    }


}
