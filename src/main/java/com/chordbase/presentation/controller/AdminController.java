package com.chordbase.presentation.controller;

import com.chordbase.application.hellpers.AuthenticatedUserResolver;
import com.chordbase.application.services.AdminService;
import com.chordbase.domain.entities.User;
import com.chordbase.infra.security.UserDetailsImp;
import com.chordbase.presentation.Dtos.Admin.AdminUserResponse;
import com.chordbase.presentation.Dtos.Admin.UpdateUserRolesRequest;
import com.chordbase.presentation.Dtos.Admin.UpdateUserActiveRequest;
import com.chordbase.presentation.Dtos.Chord.SimpleChordVizualizationResponse;
import com.chordbase.presentation.Dtos.Setlist.SetlistResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
public class AdminController {
    private final AdminService adminService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    public AdminController(AdminService adminService, AuthenticatedUserResolver authenticatedUserResolver) {
        this.adminService = adminService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @GetMapping("/users")
    public ResponseEntity<List<AdminUserResponse>> listUsers(@AuthenticationPrincipal UserDetailsImp userImp) {
        User user = authenticatedUserResolver.resolve(userImp);
        var users = adminService.listUsers(user);

        return ResponseEntity.status(HttpStatus.OK).body(users);
    }

    @PutMapping("/users/{userUuid}/roles")
    public ResponseEntity<AdminUserResponse> updateUserRoles(
            @AuthenticationPrincipal UserDetailsImp userImp,
            @PathVariable UUID userUuid,
            @Valid @RequestBody UpdateUserRolesRequest request
    ) {
        User user = authenticatedUserResolver.resolve(userImp);
        var updatedUser = adminService.updateUserRoles(userUuid, request, user);

        return ResponseEntity.status(HttpStatus.OK).body(updatedUser);
    }

    @PutMapping("/users/{userUuid}/active")
    public ResponseEntity<AdminUserResponse> updateUserActive(
            @AuthenticationPrincipal UserDetailsImp userImp,
            @PathVariable UUID userUuid,
            @Valid @RequestBody UpdateUserActiveRequest request
    ) {
        User user = authenticatedUserResolver.resolve(userImp);
        var updatedUser = adminService.updateUserActive(userUuid, request.active(), user);

        return ResponseEntity.status(HttpStatus.OK).body(updatedUser);
    }

    @GetMapping("/users/{userUuid}/chords")
    public ResponseEntity<List<SimpleChordVizualizationResponse>> listUserChords(
            @AuthenticationPrincipal UserDetailsImp userImp,
            @PathVariable UUID userUuid
    ) {
        User user = authenticatedUserResolver.resolve(userImp);
        var chords = adminService.listUserChords(userUuid, user);

        return ResponseEntity.status(HttpStatus.OK).body(chords);
    }

    @GetMapping("/users/{userUuid}/setlists")
    public ResponseEntity<List<SetlistResponse>> listUserSetlists(
            @AuthenticationPrincipal UserDetailsImp userImp,
            @PathVariable UUID userUuid
    ) {
        User user = authenticatedUserResolver.resolve(userImp);
        var setlists = adminService.listUserSetlists(userUuid, user);

        return ResponseEntity.status(HttpStatus.OK).body(setlists);
    }
}
