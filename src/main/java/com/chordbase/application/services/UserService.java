package com.chordbase.application.services;

import com.chordbase.domain.entities.Role;
import com.chordbase.domain.entities.User;
import com.chordbase.domain.repository.UserRepository;
import com.chordbase.domain.valueobjects.UserName;
import com.chordbase.infra.security.JwtTokenService;
import com.chordbase.infra.security.SecurityConfiguration;
import com.chordbase.infra.security.UserDetailsImp;
import com.chordbase.presentation.Dtos.User.LoginRequest;
import com.chordbase.presentation.Dtos.User.LoginResponse;
import com.chordbase.presentation.Dtos.User.CurrentUserResponse;
import com.chordbase.presentation.Dtos.User.RefreshResult;
import com.chordbase.presentation.Dtos.User.RegisterUserDtoRequest;
import com.chordbase.presentation.Dtos.User.RegisterUserResponse;
import com.chordbase.presentation.Dtos.User.UpdateProfileImageRequest;
import com.chordbase.presentation.Dtos.User.UserSearchResponse;
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
        UserName userName = UserName.of(request.userName());

        userRepository.findByEmail(request.email()).ifPresent(u -> {
            throw new IllegalArgumentException("Email already in use");
        });

        if (userRepository.existsByUserNameIgnoreCase(userName.value())) {
            throw new IllegalArgumentException("UserName already in use");
        }

        User user = User.builder()
                .userName(userName)
                .email(request.email())
                .passwordHash(securityConfiguration.passwordEncoder().encode(request.password()))
                .roles(List.of(Role.builder().role(request.role()).build()))
                .profileImageUrl(normalizeProfileImageUrl(request.profileImageUrl()))
                .build();

        userRepository.save(user);

        return toRegisterUserResponse(user);

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

        return new LoginResponse(
                userDetails.getUsername(),
                userDetails.getUser().getUuid(),
                userDetails.getUser().getProfileImageUrl(),
                userDetails.getUser().getRoles().stream().map(Role::getRole).toList(),
                accessToken,
                refreshToken
        );
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

    public List<UserSearchResponse> searchUsersByUserName(String userName) {
        UserName normalizedSearch = UserName.of(userName);

        return userRepository.searchByUserName(normalizedSearch.value()).stream()
                .map(user -> new UserSearchResponse(user.getUuid(), user.getUserName(), user.getProfileImageUrl()))
                .toList();
    }

    public CurrentUserResponse getCurrentUser(User authenticatedUser) {
        User user = findUserById(authenticatedUser);

        return toCurrentUserResponse(user);
    }

    public CurrentUserResponse updateProfileImage(User authenticatedUser, UpdateProfileImageRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request must not be null");
        }

        User user = findUserById(authenticatedUser);

        user.setProfileImageUrl(normalizeProfileImageUrl(request.profileImageUrl()));

        return toCurrentUserResponse(userRepository.save(user));
    }

    private User findUserById(User authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.getUuid() == null) {
            throw new IllegalArgumentException("User must not be null");
        }

        return userRepository.findById(authenticatedUser.getUuid())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + authenticatedUser.getUuid()));
    }

    private RegisterUserResponse toRegisterUserResponse(User user) {
        return new RegisterUserResponse(
                user.getUuid(),
                user.getEmail(),
                user.getUserName(),
                user.getProfileImageUrl(),
                user.getRoles().stream().map(Role::getRole).toList()
        );
    }

    private CurrentUserResponse toCurrentUserResponse(User user) {
        return new CurrentUserResponse(
                user.getUuid(),
                user.getUserName(),
                user.getEmail(),
                user.getProfileImageUrl(),
                user.getRoles().stream().map(Role::getRole).toList()
        );
    }

    private String normalizeProfileImageUrl(String profileImageUrl) {
        if (profileImageUrl == null || profileImageUrl.isBlank()) {
            return null;
        }

        return profileImageUrl.trim();
    }
}
