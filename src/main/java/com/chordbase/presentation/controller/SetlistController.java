package com.chordbase.presentation.controller;

import com.chordbase.application.hellpers.AuthenticatedUserResolver;
import com.chordbase.application.services.SetlistService;
import com.chordbase.domain.entities.User;
import com.chordbase.infra.security.UserDetailsImp;
import com.chordbase.presentation.Dtos.Setlist.CreateSetlistInviteRequest;
import com.chordbase.presentation.Dtos.Setlist.CreateSetlistRequest;
import com.chordbase.presentation.Dtos.Setlist.ReorderSetlistChordsRequest;
import com.chordbase.presentation.Dtos.Setlist.SetlistInviteResponse;
import com.chordbase.presentation.Dtos.Setlist.SetlistResponse;
import com.chordbase.presentation.Dtos.Setlist.UpdateSetlistRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/setlists")
public class SetlistController {
    private final SetlistService setlistService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    public SetlistController(SetlistService setlistService, AuthenticatedUserResolver authenticatedUserResolver) {
        this.setlistService = setlistService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @PostMapping
    public ResponseEntity<SetlistResponse> createSetlist(
            @AuthenticationPrincipal UserDetailsImp userImp,
            @Valid @RequestBody CreateSetlistRequest request
    ) {
        User user = authenticatedUserResolver.resolve(userImp);
        var setlist = setlistService.createSetlist(request, user);

        return ResponseEntity.status(HttpStatus.CREATED).body(setlist);
    }

    @GetMapping("/me")
    public ResponseEntity<List<SetlistResponse>> listMySetlists(@AuthenticationPrincipal UserDetailsImp userImp) {
        User user = authenticatedUserResolver.resolve(userImp);
        var setlists = setlistService.listMySetlists(user);

        return ResponseEntity.status(HttpStatus.OK).body(setlists);
    }

    @GetMapping("/{setlistUuid}")
    public ResponseEntity<SetlistResponse> getSetlist(
            @AuthenticationPrincipal UserDetailsImp userImp,
            @PathVariable UUID setlistUuid
    ) {
        User user = authenticatedUserResolver.resolve(userImp);
        var setlist = setlistService.getSetlist(setlistUuid, user);

        return ResponseEntity.status(HttpStatus.OK).body(setlist);
    }

    @PutMapping("/{setlistUuid}")
    public ResponseEntity<SetlistResponse> updateSetlist(
            @AuthenticationPrincipal UserDetailsImp userImp,
            @PathVariable UUID setlistUuid,
            @Valid @RequestBody UpdateSetlistRequest request
    ) {
        User user = authenticatedUserResolver.resolve(userImp);
        var setlist = setlistService.updateSetlist(setlistUuid, request, user);

        return ResponseEntity.status(HttpStatus.OK).body(setlist);
    }

    @DeleteMapping("/{setlistUuid}")
    public ResponseEntity<Void> deleteSetlist(
            @AuthenticationPrincipal UserDetailsImp userImp,
            @PathVariable UUID setlistUuid
    ) {
        User user = authenticatedUserResolver.resolve(userImp);
        setlistService.deleteSetlist(setlistUuid, user);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{setlistUuid}/chords/{chordUuid}")
    public ResponseEntity<SetlistResponse> addChord(
            @AuthenticationPrincipal UserDetailsImp userImp,
            @PathVariable UUID setlistUuid,
            @PathVariable UUID chordUuid
    ) {
        User user = authenticatedUserResolver.resolve(userImp);
        var setlist = setlistService.addChord(setlistUuid, chordUuid, user);

        return ResponseEntity.status(HttpStatus.OK).body(setlist);
    }

    @DeleteMapping("/{setlistUuid}/chords/{chordUuid}")
    public ResponseEntity<SetlistResponse> removeChord(
            @AuthenticationPrincipal UserDetailsImp userImp,
            @PathVariable UUID setlistUuid,
            @PathVariable UUID chordUuid
    ) {
        User user = authenticatedUserResolver.resolve(userImp);
        var setlist = setlistService.removeChord(setlistUuid, chordUuid, user);

        return ResponseEntity.status(HttpStatus.OK).body(setlist);
    }

    @PutMapping("/{setlistUuid}/chords/reorder")
    public ResponseEntity<SetlistResponse> reorderChords(
            @AuthenticationPrincipal UserDetailsImp userImp,
            @PathVariable UUID setlistUuid,
            @Valid @RequestBody ReorderSetlistChordsRequest request
    ) {
        User user = authenticatedUserResolver.resolve(userImp);
        var setlist = setlistService.reorderChords(setlistUuid, request, user);

        return ResponseEntity.status(HttpStatus.OK).body(setlist);
    }

    @PostMapping("/{setlistUuid}/collaborator-invites")
    public ResponseEntity<SetlistResponse> inviteCollaborator(
            @AuthenticationPrincipal UserDetailsImp userImp,
            @PathVariable UUID setlistUuid,
            @Valid @RequestBody CreateSetlistInviteRequest request
    ) {
        User user = authenticatedUserResolver.resolve(userImp);
        var setlist = setlistService.inviteCollaborator(setlistUuid, request, user);

        return ResponseEntity.status(HttpStatus.OK).body(setlist);
    }

    @GetMapping("/collaborator-invites/me")
    public ResponseEntity<List<SetlistInviteResponse>> listMyPendingInvites(@AuthenticationPrincipal UserDetailsImp userImp) {
        User user = authenticatedUserResolver.resolve(userImp);
        var invites = setlistService.listMyPendingInvites(user);

        return ResponseEntity.status(HttpStatus.OK).body(invites);
    }

    @PostMapping("/collaborator-invites/{inviteUuid}/accept")
    public ResponseEntity<SetlistInviteResponse> acceptInvite(
            @AuthenticationPrincipal UserDetailsImp userImp,
            @PathVariable UUID inviteUuid
    ) {
        User user = authenticatedUserResolver.resolve(userImp);
        var invite = setlistService.acceptInvite(inviteUuid, user);

        return ResponseEntity.status(HttpStatus.OK).body(invite);
    }

    @PostMapping("/collaborator-invites/{inviteUuid}/decline")
    public ResponseEntity<SetlistInviteResponse> declineInvite(
            @AuthenticationPrincipal UserDetailsImp userImp,
            @PathVariable UUID inviteUuid
    ) {
        User user = authenticatedUserResolver.resolve(userImp);
        var invite = setlistService.declineInvite(inviteUuid, user);

        return ResponseEntity.status(HttpStatus.OK).body(invite);
    }

    @DeleteMapping("/{setlistUuid}/collaborators/{userUuid}")
    public ResponseEntity<SetlistResponse> removeCollaborator(
            @AuthenticationPrincipal UserDetailsImp userImp,
            @PathVariable UUID setlistUuid,
            @PathVariable UUID userUuid
    ) {
        User user = authenticatedUserResolver.resolve(userImp);
        var setlist = setlistService.removeCollaborator(setlistUuid, userUuid, user);

        return ResponseEntity.status(HttpStatus.OK).body(setlist);
    }
}
