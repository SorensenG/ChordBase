package com.chordbase.presentation.controller;

import com.chordbase.application.services.UserService;
import com.chordbase.infra.security.RefreshTokenCookieService;
import com.chordbase.presentation.Dtos.User.LoginRequest;
import com.chordbase.presentation.Dtos.User.LoginResponse;
import com.chordbase.presentation.Dtos.User.RefreshTokenResponse;
import com.chordbase.presentation.Dtos.User.RegisterUserDtoRequest;
import com.chordbase.presentation.Dtos.User.RegisterUserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService userService;
    private final RefreshTokenCookieService refreshTokenCookieService;

    public UserController(UserService userService, RefreshTokenCookieService refreshTokenCookieService) {
        this.userService = userService;
        this.refreshTokenCookieService = refreshTokenCookieService;
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
            @CookieValue(name = "${security.refresh-cookie.name:refreshToken}", required = false) String refreshToken) {
        var refreshResult = userService.refreshAccessToken(refreshToken);

        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieService.createCookie(refreshResult.refreshToken()).toString())
                .body(new RefreshTokenResponse(refreshResult.accessToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "${security.refresh-cookie.name:refreshToken}", required = false) String refreshToken) {
        userService.logout(refreshToken);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieService.clearCookie().toString())
                .build();
    }

}
