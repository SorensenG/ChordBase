package com.chordbase.presentation.controller;

import com.chordbase.application.services.UserService;
import com.chordbase.application.hellpers.AuthenticatedUserResolver;
import com.chordbase.domain.entities.User;
import com.chordbase.infra.security.RefreshTokenCookieService;
import com.chordbase.infra.security.UserDetailsImp;
import com.chordbase.presentation.Dtos.User.CurrentUserResponse;
import com.chordbase.presentation.Dtos.User.LoginRequest;
import com.chordbase.presentation.Dtos.User.LoginResponse;
import com.chordbase.presentation.Dtos.User.RefreshTokenRequest;
import com.chordbase.presentation.Dtos.User.RefreshTokenResponse;
import com.chordbase.presentation.Dtos.User.RegisterUserDtoRequest;
import com.chordbase.presentation.Dtos.User.RegisterUserResponse;
import com.chordbase.presentation.Dtos.User.UpdateProfileImageRequest;
import com.chordbase.presentation.Dtos.User.UpdateProfileRequest;
import com.chordbase.presentation.Dtos.User.UserSearchResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService userService;
    private final RefreshTokenCookieService refreshTokenCookieService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    public UserController(UserService userService, RefreshTokenCookieService refreshTokenCookieService, AuthenticatedUserResolver authenticatedUserResolver) {
        this.userService = userService;
        this.refreshTokenCookieService = refreshTokenCookieService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterUserResponse> registerUser(@Valid @RequestBody RegisterUserDtoRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }

        var createdUser = userService.registerUser(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> userLogin(@Valid @RequestBody LoginRequest request){
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }

        var userLoged = userService.userLogin(request);

        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieService.createCookie(userLoged.refreshToken()).toString())
                .body(userLoged);

    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponse> refreshToken(
            @CookieValue(name = "${security.refresh-cookie.name:refreshToken}", required = false) String refreshTokenFromCookie,
            @RequestBody(required = false) RefreshTokenRequest request
    ) {
        String refreshToken = resolveRefreshToken(request, refreshTokenFromCookie);
        var refreshResult = userService.refreshAccessToken(refreshToken);

        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieService.createCookie(refreshResult.refreshToken()).toString())
                .body(new RefreshTokenResponse(refreshResult.accessToken(), refreshResult.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "${security.refresh-cookie.name:refreshToken}", required = false) String refreshTokenFromCookie,
            @RequestBody(required = false) RefreshTokenRequest request
    ) {
        String refreshToken = resolveRefreshToken(request, refreshTokenFromCookie);
        userService.logout(refreshToken);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieService.clearCookie().toString())
                .build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserSearchResponse>> searchUsers(@RequestParam String userName) {
        var users = userService.searchUsersByUserName(userName);

        return ResponseEntity.status(HttpStatus.OK).body(users);
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> getCurrentUser(@AuthenticationPrincipal UserDetailsImp userImp) {
        User user = authenticatedUserResolver.resolve(userImp);
        var currentUser = userService.getCurrentUser(user);

        return ResponseEntity.status(HttpStatus.OK).body(currentUser);
    }

    @PutMapping("/me/profile-image")
    public ResponseEntity<CurrentUserResponse> updateProfileImage(
            @AuthenticationPrincipal UserDetailsImp userImp,
            @RequestBody UpdateProfileImageRequest request
    ) {
        User user = authenticatedUserResolver.resolve(userImp);
        var currentUser = userService.updateProfileImage(user, request);

        return ResponseEntity.status(HttpStatus.OK).body(currentUser);
    }

    @PutMapping("/me/profile")
    public ResponseEntity<CurrentUserResponse> updateProfile(
            @AuthenticationPrincipal UserDetailsImp userImp,
            @RequestBody UpdateProfileRequest request
    ) {
        User user = authenticatedUserResolver.resolve(userImp);
        var currentUser = userService.updateProfile(user, request);

        return ResponseEntity.status(HttpStatus.OK).body(currentUser);
    }

    private String resolveRefreshToken(RefreshTokenRequest request, String refreshTokenFromCookie) {
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            return request.refreshToken();
        }

        return refreshTokenFromCookie;
    }

}
