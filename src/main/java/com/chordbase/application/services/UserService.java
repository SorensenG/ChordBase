package com.chordbase.application.services;

import com.chordbase.domain.entities.Role;
import com.chordbase.domain.entities.User;
import com.chordbase.domain.repository.UserRepository;
import com.chordbase.domain.valueobjects.EmailAddress;
import com.chordbase.domain.valueobjects.UserName;
import com.chordbase.domain.valueobjects.UserRole;
import com.chordbase.infra.security.JwtTokenService;
import com.chordbase.infra.security.SecurityConfiguration;
import com.chordbase.infra.security.UserDetailsImp;
import com.chordbase.presentation.Dtos.User.LoginRequest;
import com.chordbase.presentation.Dtos.User.LoginResponse;
import com.chordbase.presentation.Dtos.User.CurrentUserResponse;
import com.chordbase.presentation.Dtos.User.GoogleLoginRequest;
import com.chordbase.presentation.Dtos.User.RefreshResult;
import com.chordbase.presentation.Dtos.User.RegisterUserDtoRequest;
import com.chordbase.presentation.Dtos.User.RegisterUserResponse;
import com.chordbase.presentation.Dtos.User.UpdateProfileImageRequest;
import com.chordbase.presentation.Dtos.User.UpdateProfileRequest;
import com.chordbase.presentation.Dtos.User.UserSearchResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class UserService {
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MAX_PROFILE_IMAGE_URL_LENGTH = 2_000_000;
    private static final List<String> PROFILE_DATA_IMAGE_PREFIXES = List.of(
            "data:image/png;base64,",
            "data:image/jpeg;base64,",
            "data:image/webp;base64,"
    );
    private static final String GOOGLE_PROFILE_IMAGE_PREFIX = "https://lh3.googleusercontent.com/";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    final private AuthenticationManager authenticationManager;

    final private JwtTokenService jwtTokenService;
    final private RefreshTokenService refreshTokenService;

    final private UserRepository userRepository;
    final private SecurityConfiguration securityConfiguration;
    final private GoogleIdTokenVerifier googleIdTokenVerifier;


    public UserService(AuthenticationManager authenticationManager, JwtTokenService jwtTokenService, RefreshTokenService refreshTokenService, UserRepository userRepository, SecurityConfiguration securityConfiguration, GoogleIdTokenVerifier googleIdTokenVerifier) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
        this.securityConfiguration = securityConfiguration;
        this.googleIdTokenVerifier = googleIdTokenVerifier;
    }

    public RegisterUserResponse registerUser(RegisterUserDtoRequest request) {
        UserName userName = UserName.of(request.userName());
        EmailAddress email = EmailAddress.of(request.email());

        userRepository.findByEmail(email.value()).ifPresent(u -> {
            throw new IllegalArgumentException("Email already in use");
        });

        if (userRepository.existsByUserNameIgnoreCase(userName.value())) {
            throw new IllegalArgumentException("UserName already in use");
        }

        User user = User.builder()
                .userName(userName)
                .email(email)
                .passwordHash(securityConfiguration.passwordEncoder().encode(request.password()))
                .roles(List.of(Role.builder().role(UserRole.ROLE_USER).build()))
                .profileImageUrl(normalizeProfileImageUrl(request.profileImageUrl()))
                .description(normalizeDescription(request.description()))
                .active(true)
                .build();

        userRepository.save(user);

        return toRegisterUserResponse(user);

    }


    public LoginResponse userLogin(LoginRequest request) {
        EmailAddress email = EmailAddress.of(request.email());

        if (userRepository.findByEmail(email.value()).isEmpty()) {
            throw new BadCredentialsException("Invalid email or password");
        }

        User loginUser = userRepository.findByEmail(email.value()).orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!loginUser.isActive()) {
            throw new BadCredentialsException("User account is inactive");
        }

        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(email.value(), request.password());
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
                userDetails.getUser().getDescription(),
                userDetails.getUser().getActive(),
                userDetails.getUser().getRoles().stream().map(Role::getRole).toList(),
                accessToken,
                refreshToken
        );
    }

    @Transactional
    public LoginResponse googleLogin(GoogleLoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request must not be null");
        }

        var googleAccount = googleIdTokenVerifier.verify(request.idToken());
        if (!googleAccount.emailVerified()) {
            throw new BadCredentialsException("Google email não verificado.");
        }

        EmailAddress email = EmailAddress.of(googleAccount.email());
        User user = userRepository.findByGoogleSubject(googleAccount.subject())
                .orElseGet(() -> linkOrCreateGoogleUser(email, googleAccount));

        if (!user.isActive()) {
            throw new BadCredentialsException("User account is inactive");
        }
        if (!email.value().equals(user.getEmail())) {
            throw new BadCredentialsException("Google account does not match the linked user.");
        }

        return createLoginResponse(user);
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

    public CurrentUserResponse updateProfile(User authenticatedUser, UpdateProfileRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request must not be null");
        }

        User user = findUserById(authenticatedUser);

        user.setProfileImageUrl(normalizeProfileImageUrl(request.profileImageUrl()));
        user.setDescription(normalizeDescription(request.description()));

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
                user.getDescription(),
                user.getActive(),
                user.getRoles().stream().map(Role::getRole).toList()
        );
    }

    private User createGoogleUser(EmailAddress email, GoogleIdTokenVerifier.GoogleAccount googleAccount) {
        PasswordEncoder passwordEncoder = securityConfiguration.passwordEncoder();
        User user = User.builder()
                .userName(UserName.of(nextGoogleUserName(email.value(), googleAccount.name())))
                .email(email)
                .passwordHash(passwordEncoder.encode(randomInternalPassword()))
                .roles(List.of(Role.builder().role(com.chordbase.domain.valueobjects.UserRole.ROLE_USER).build()))
                .profileImageUrl(normalizeProfileImageUrl(googleAccount.pictureUrl()))
                .description(null)
                .googleSubject(googleAccount.subject())
                .active(true)
                .build();

        return userRepository.save(user);
    }

    private User linkOrCreateGoogleUser(EmailAddress email, GoogleIdTokenVerifier.GoogleAccount googleAccount) {
        return userRepository.findByEmail(email.value())
                .map(existingUser -> linkGoogleSubject(existingUser, googleAccount.subject()))
                .orElseGet(() -> createGoogleUser(email, googleAccount));
    }

    private User linkGoogleSubject(User user, String googleSubject) {
        if (user.getGoogleSubject() != null && !user.getGoogleSubject().equals(googleSubject)) {
            throw new BadCredentialsException("Google account does not match the linked user.");
        }
        if (user.getGoogleSubject() == null) {
            user.setGoogleSubject(googleSubject);
            return userRepository.save(user);
        }
        return user;
    }

    private LoginResponse createLoginResponse(User user) {
        var userDetails = new UserDetailsImp(user);
        var accessToken = jwtTokenService.generateAccessToken(userDetails);
        var refreshToken = refreshTokenService.createRefreshToken(user);

        return new LoginResponse(
                user.getUserName(),
                user.getUuid(),
                user.getProfileImageUrl(),
                user.getDescription(),
                user.getActive(),
                user.getRoles().stream().map(Role::getRole).toList(),
                accessToken,
                refreshToken
        );
    }

    private String nextGoogleUserName(String email, String displayName) {
        String base = normalizeGoogleUserNameBase(displayName);
        if (base == null) {
            base = normalizeGoogleUserNameBase(email.substring(0, email.indexOf('@')));
        }
        if (base == null) {
            base = "user";
        }

        String candidate = fitUserName(base, 0);
        int suffix = 1;
        while (userRepository.existsByUserNameIgnoreCase(candidate)) {
            candidate = fitUserName(base, suffix);
            suffix++;
        }

        return candidate;
    }

    private String normalizeGoogleUserNameBase(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.]+", ".")
                .replaceAll("[._]{2,}", ".")
                .replaceAll("^[._]+|[._]+$", "");

        if (normalized.isBlank()) {
            return null;
        }

        if (normalized.length() < 3) {
            normalized = (normalized + ".user").replaceAll("[._]{2,}", ".");
        }

        return normalized;
    }

    private String fitUserName(String base, int suffix) {
        String suffixText = suffix == 0 ? "" : "." + suffix;
        int maxBaseLength = 30 - suffixText.length();
        String fittedBase = base.length() > maxBaseLength ? base.substring(0, maxBaseLength) : base;
        fittedBase = fittedBase.replaceAll("[._]+$", "");
        if (fittedBase.length() < 3) {
            fittedBase = "user";
        }
        return fittedBase + suffixText;
    }

    private String randomInternalPassword() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return "google:" + HexFormat.of().formatHex(bytes);
    }

    private CurrentUserResponse toCurrentUserResponse(User user) {
        return new CurrentUserResponse(
                user.getUuid(),
                user.getUserName(),
                user.getEmail(),
                user.getProfileImageUrl(),
                user.getDescription(),
                user.getActive(),
                user.getRoles().stream().map(Role::getRole).toList()
        );
    }

    private String normalizeProfileImageUrl(String profileImageUrl) {
        if (profileImageUrl == null || profileImageUrl.isBlank()) {
            return null;
        }

        String normalized = profileImageUrl.trim();
        if (normalized.length() > MAX_PROFILE_IMAGE_URL_LENGTH) {
            throw new IllegalArgumentException("Profile image must not exceed 2000000 characters");
        }
        boolean supportedDataImage = PROFILE_DATA_IMAGE_PREFIXES.stream().anyMatch(normalized::startsWith);
        boolean trustedGoogleImage = normalized.startsWith(GOOGLE_PROFILE_IMAGE_PREFIX);
        if (!supportedDataImage && !trustedGoogleImage) {
            throw new IllegalArgumentException("Profile image must be an uploaded image or trusted Google avatar");
        }
        return normalized;
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }

        String normalizedDescription = description.trim();
        if (normalizedDescription.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("Description must have at most 500 characters");
        }

        return normalizedDescription;
    }
}
