package com.chordbase.application.services;

import com.chordbase.domain.entities.Role;
import com.chordbase.domain.entities.User;
import com.chordbase.domain.repository.UserRepository;
import com.chordbase.infra.security.JwtTokenService;
import com.chordbase.infra.security.SecurityConfiguration;
import com.chordbase.infra.security.UserDetailsImp;
import com.chordbase.presentation.Dtos.User.LoginRequest;
import com.chordbase.presentation.Dtos.User.LoginResponse;
import com.chordbase.presentation.Dtos.User.RefreshResult;
import com.chordbase.presentation.Dtos.User.RegisterUserDtoRequest;
import com.chordbase.presentation.Dtos.User.RegisterUserResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {
    final private AuthenticationManager authenticationManager;

    final private JwtTokenService jwtTokenService;
    final private RefreshTokenService refreshTokenService;

    final private UserRepository userRepository;
    final private SecurityConfiguration securityConfiguration;


    public UserService(AuthenticationManager authenticationManager, JwtTokenService jwtTokenService, RefreshTokenService refreshTokenService, UserRepository userRepository, SecurityConfiguration securityConfiguration) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
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


    public LoginResponse userLogin(LoginRequest request) {

        if (userRepository.findByEmail(request.email()).isEmpty()) {
            throw new BadCredentialsException("Invalid email or password");
        }

        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(request.email(), request.password());
        Authentication authentication = authenticationManager.authenticate(authToken);

        UserDetailsImp userDetails = (UserDetailsImp) authentication.getPrincipal();
        if (userDetails == null) {
            throw new BadCredentialsException("Invalid email or password");
        }
        var accessToken = jwtTokenService.generateAccessToken(userDetails);
        var refreshToken = refreshTokenService.createRefreshToken(userDetails.getUser());

        return new LoginResponse(userDetails.getUsername(), userDetails.getUser().getUuid(), userDetails.getUser().getRoles(), accessToken, refreshToken);
    }

    public RefreshResult refreshAccessToken(String refreshToken) {
        var rotation = refreshTokenService.rotate(refreshToken);
        var userDetails = new UserDetailsImp(rotation.user());
        var accessToken = jwtTokenService.generateAccessToken(userDetails);

        return new RefreshResult(accessToken, rotation.refreshToken());
    }

    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }
}
