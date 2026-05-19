package com.chordbase.application.services;

import com.chordbase.application.hellpers.ChordMetadataResolver;
import com.chordbase.domain.entities.Chord;
import com.chordbase.domain.entities.Setlist;
import com.chordbase.domain.entities.SetlistChord;
import com.chordbase.domain.entities.SetlistCollaborator;
import com.chordbase.domain.entities.User;
import com.chordbase.domain.repository.ChordRepository;
import com.chordbase.domain.repository.SetlistCollaboratorRepository;
import com.chordbase.domain.repository.SetlistRepository;
import com.chordbase.domain.repository.UserRepository;
import com.chordbase.domain.valueobjects.ChordStatus;
import com.chordbase.domain.valueobjects.SetlistCollaboratorStatus;
import com.chordbase.domain.valueobjects.SetlistVisibility;
import com.chordbase.presentation.Dtos.Setlist.CreateSetlistInviteRequest;
import com.chordbase.presentation.Dtos.Setlist.CreateSetlistRequest;
import com.chordbase.presentation.Dtos.Setlist.ReorderSetlistChordsRequest;
import com.chordbase.presentation.Dtos.Setlist.SetlistChordResponse;
import com.chordbase.presentation.Dtos.Setlist.SetlistCollaboratorResponse;
import com.chordbase.presentation.Dtos.Setlist.SetlistInviteResponse;
import com.chordbase.presentation.Dtos.Setlist.SetlistResponse;
import com.chordbase.presentation.Dtos.Setlist.UpdateSetlistRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SetlistService {
    private final SetlistRepository setlistRepository;
    private final SetlistCollaboratorRepository setlistCollaboratorRepository;
    private final ChordRepository chordRepository;
    private final UserRepository userRepository;
    private final ChordMetadataResolver chordMetadataResolver;

    public SetlistService(
            SetlistRepository setlistRepository,
            SetlistCollaboratorRepository setlistCollaboratorRepository,
            ChordRepository chordRepository,
            UserRepository userRepository,
            ChordMetadataResolver chordMetadataResolver
    ) {
        this.setlistRepository = setlistRepository;
        this.setlistCollaboratorRepository = setlistCollaboratorRepository;
        this.chordRepository = chordRepository;
        this.userRepository = userRepository;
        this.chordMetadataResolver = chordMetadataResolver;
    }

    @Transactional
    public SetlistResponse createSetlist(CreateSetlistRequest request, User authenticatedUser) {
        User owner = findAuthenticatedUser(authenticatedUser);

        Setlist setlist = Setlist.builder()
                .name(request.name())
                .description(request.description())
                .visibility(request.visibility())
                .owner(owner)
                .build();

        return toResponse(setlistRepository.save(setlist));
    }

    @Transactional(readOnly = true)
    public List<SetlistResponse> listMySetlists(User authenticatedUser) {
        User user = findAuthenticatedUser(authenticatedUser);

        return setlistRepository.findVisibleInUserLibrary(user.getUuid(), SetlistCollaboratorStatus.ACCEPTED).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SetlistResponse getSetlist(UUID setlistUuid, User authenticatedUser) {
        User user = findAuthenticatedUser(authenticatedUser);
        Setlist setlist = findSetlist(setlistUuid);

        validateCanView(setlist, user);

        return toResponse(setlist);
    }

    @Transactional
    public SetlistResponse updateSetlist(UUID setlistUuid, UpdateSetlistRequest request, User authenticatedUser) {
        User owner = findAuthenticatedUser(authenticatedUser);
        Setlist setlist = findSetlist(setlistUuid);

        validateOwner(setlist, owner);

        setlist.setName(request.name());
        setlist.setDescription(request.description());
        setlist.setVisibility(request.visibility());

        if (!SetlistVisibility.COLLABORATIVE.equals(request.visibility())) {
            setlist.getCollaborators().clear();
        }

        return toResponse(setlistRepository.save(setlist));
    }

    @Transactional
    public void deleteSetlist(UUID setlistUuid, User authenticatedUser) {
        User owner = findAuthenticatedUser(authenticatedUser);
        Setlist setlist = findSetlist(setlistUuid);

        validateOwner(setlist, owner);

        setlistRepository.delete(setlist);
    }

    @Transactional
    public SetlistResponse addChord(UUID setlistUuid, UUID chordUuid, User authenticatedUser) {
        User user = findAuthenticatedUser(authenticatedUser);
        Setlist setlist = findSetlist(setlistUuid);

        validateCanEditChords(setlist, user);

        Chord chord = chordRepository.findById(chordUuid)
                .orElseThrow(() -> new IllegalArgumentException("Chord not found with id: " + chordUuid));

        if (!ChordStatus.PUBLISHED.name().equals(chord.getStatus())) {
            throw new IllegalArgumentException("Only published chords can be added to a setlist");
        }

        boolean alreadyAdded = setlist.getChords().stream()
                .anyMatch(setlistChord -> setlistChord.getChord().getUuid().equals(chordUuid));

        if (!alreadyAdded) {
            int nextPosition = setlist.getChords().stream()
                    .map(SetlistChord::getPosition)
                    .max(Integer::compareTo)
                    .orElse(0) + 1;

            setlist.getChords().add(SetlistChord.builder()
                    .setlist(setlist)
                    .chord(chord)
                    .position(nextPosition)
                    .build());
        }

        return toResponse(setlistRepository.save(setlist));
    }

    @Transactional
    public SetlistResponse removeChord(UUID setlistUuid, UUID chordUuid, User authenticatedUser) {
        User user = findAuthenticatedUser(authenticatedUser);
        Setlist setlist = findSetlist(setlistUuid);

        validateCanEditChords(setlist, user);

        boolean removed = setlist.getChords().removeIf(setlistChord -> setlistChord.getChord().getUuid().equals(chordUuid));

        if (!removed) {
            throw new IllegalArgumentException("Chord not found in setlist with id: " + chordUuid);
        }

        normalizePositions(setlist);

        return toResponse(setlistRepository.save(setlist));
    }

    @Transactional
    public SetlistResponse reorderChords(UUID setlistUuid, ReorderSetlistChordsRequest request, User authenticatedUser) {
        User user = findAuthenticatedUser(authenticatedUser);
        Setlist setlist = findSetlist(setlistUuid);

        validateCanEditChords(setlist, user);
        validateReorderRequest(setlist, request);

        Map<UUID, Integer> positionsByChordUuid = request.chords().stream()
                .collect(Collectors.toMap(
                        item -> item.chordUuid(),
                        item -> item.position()
                ));

        for (SetlistChord setlistChord : setlist.getChords()) {
            setlistChord.setPosition(positionsByChordUuid.get(setlistChord.getChord().getUuid()));
        }

        return toResponse(setlistRepository.save(setlist));
    }

    @Transactional
    public SetlistResponse inviteCollaborator(UUID setlistUuid, CreateSetlistInviteRequest request, User authenticatedUser) {
        User owner = findAuthenticatedUser(authenticatedUser);
        Setlist setlist = findSetlist(setlistUuid);

        validateOwner(setlist, owner);

        if (!SetlistVisibility.COLLABORATIVE.equals(setlist.getVisibility())) {
            throw new IllegalArgumentException("Only collaborative setlists can have collaborators");
        }

        UUID userUuid = request.userUuid();

        if (setlist.getOwner().getUuid().equals(userUuid)) {
            throw new IllegalArgumentException("Setlist owner cannot be added as collaborator");
        }

        User collaborator = userRepository.findById(userUuid)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userUuid));

        SetlistCollaborator existingInvite = setlist.getCollaborators().stream()
                .filter(item -> item.getUser().getUuid().equals(userUuid))
                .findFirst()
                .orElse(null);

        if (existingInvite == null) {
            setlist.getCollaborators().add(SetlistCollaborator.builder()
                    .setlist(setlist)
                    .user(collaborator)
                    .status(SetlistCollaboratorStatus.PENDING)
                    .invitedBy(owner)
                    .build());
        } else if (SetlistCollaboratorStatus.DECLINED.equals(existingInvite.getStatus())) {
            existingInvite.setStatus(SetlistCollaboratorStatus.PENDING);
            existingInvite.setInvitedBy(owner);
        }

        return toResponse(setlistRepository.save(setlist));
    }

    @Transactional(readOnly = true)
    public List<SetlistInviteResponse> listMyPendingInvites(User authenticatedUser) {
        User user = findAuthenticatedUser(authenticatedUser);

        return setlistCollaboratorRepository.findByUserUuidAndStatus(user.getUuid(), SetlistCollaboratorStatus.PENDING).stream()
                .map(this::toInviteResponse)
                .toList();
    }

    @Transactional
    public SetlistInviteResponse acceptInvite(UUID inviteUuid, User authenticatedUser) {
        User user = findAuthenticatedUser(authenticatedUser);
        SetlistCollaborator invite = findInvite(inviteUuid);

        validateInviteTarget(invite, user);

        if (!SetlistCollaboratorStatus.PENDING.equals(invite.getStatus())) {
            throw new IllegalArgumentException("Only pending invites can be accepted");
        }

        invite.setStatus(SetlistCollaboratorStatus.ACCEPTED);

        return toInviteResponse(setlistCollaboratorRepository.save(invite));
    }

    @Transactional
    public SetlistInviteResponse declineInvite(UUID inviteUuid, User authenticatedUser) {
        User user = findAuthenticatedUser(authenticatedUser);
        SetlistCollaborator invite = findInvite(inviteUuid);

        validateInviteTarget(invite, user);

        if (!SetlistCollaboratorStatus.PENDING.equals(invite.getStatus())) {
            throw new IllegalArgumentException("Only pending invites can be declined");
        }

        invite.setStatus(SetlistCollaboratorStatus.DECLINED);

        return toInviteResponse(setlistCollaboratorRepository.save(invite));
    }

    @Transactional
    public SetlistResponse removeCollaborator(UUID setlistUuid, UUID userUuid, User authenticatedUser) {
        User owner = findAuthenticatedUser(authenticatedUser);
        Setlist setlist = findSetlist(setlistUuid);

        validateOwner(setlist, owner);

        boolean removed = setlist.getCollaborators().removeIf(item -> item.getUser().getUuid().equals(userUuid));

        if (!removed) {
            throw new IllegalArgumentException("Collaborator not found in setlist with id: " + userUuid);
        }

        return toResponse(setlistRepository.save(setlist));
    }

    private User findAuthenticatedUser(User authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.getUuid() == null) {
            throw new IllegalArgumentException("User must not be null");
        }

        return userRepository.findById(authenticatedUser.getUuid())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + authenticatedUser.getUuid()));
    }

    private Setlist findSetlist(UUID setlistUuid) {
        return setlistRepository.findById(setlistUuid)
                .orElseThrow(() -> new IllegalArgumentException("Setlist not found with id: " + setlistUuid));
    }

    private SetlistCollaborator findInvite(UUID inviteUuid) {
        return setlistCollaboratorRepository.findById(inviteUuid)
                .orElseThrow(() -> new IllegalArgumentException("Invite not found with id: " + inviteUuid));
    }

    private void validateCanView(Setlist setlist, User user) {
        if (SetlistVisibility.PUBLIC.equals(setlist.getVisibility()) || isOwner(setlist, user) || isCollaborator(setlist, user)) {
            return;
        }

        throw new AccessDeniedException("You are not allowed to view this setlist");
    }

    private void validateCanEditChords(Setlist setlist, User user) {
        if (isOwner(setlist, user) || isCollaborator(setlist, user)) {
            return;
        }

        throw new AccessDeniedException("You are not allowed to change this setlist");
    }

    private void validateOwner(Setlist setlist, User user) {
        if (!isOwner(setlist, user)) {
            throw new AccessDeniedException("Only the setlist owner can perform this action");
        }
    }

    private boolean isOwner(Setlist setlist, User user) {
        return setlist.getOwner().getUuid().equals(user.getUuid());
    }

    private boolean isCollaborator(Setlist setlist, User user) {
        return setlist.getCollaborators().stream()
                .anyMatch(collaborator -> collaborator.getUser().getUuid().equals(user.getUuid())
                        && SetlistCollaboratorStatus.ACCEPTED.equals(collaborator.getStatus()));
    }

    private void validateInviteTarget(SetlistCollaborator invite, User user) {
        if (!invite.getUser().getUuid().equals(user.getUuid())) {
            throw new AccessDeniedException("You are not allowed to answer this invite");
        }
    }

    private void validateReorderRequest(Setlist setlist, ReorderSetlistChordsRequest request) {
        if (request.chords().size() != setlist.getChords().size()) {
            throw new IllegalArgumentException("Reorder request must include all setlist chords");
        }

        Set<UUID> requestedChordUuids = request.chords().stream()
                .map(item -> item.chordUuid())
                .collect(Collectors.toSet());

        Set<UUID> existingChordUuids = setlist.getChords().stream()
                .map(item -> item.getChord().getUuid())
                .collect(Collectors.toSet());

        if (!requestedChordUuids.equals(existingChordUuids)) {
            throw new IllegalArgumentException("Reorder request contains invalid chords");
        }

        Set<Integer> positions = new HashSet<>();
        for (var item : request.chords()) {
            if (item.position() < 1) {
                throw new IllegalArgumentException("Position must be greater than zero");
            }

            if (!positions.add(item.position())) {
                throw new IllegalArgumentException("Reorder request contains duplicated positions");
            }
        }
    }

    private void normalizePositions(Setlist setlist) {
        List<SetlistChord> orderedChords = setlist.getChords().stream()
                .sorted(Comparator.comparing(SetlistChord::getPosition))
                .toList();

        for (int i = 0; i < orderedChords.size(); i++) {
            orderedChords.get(i).setPosition(i + 1);
        }
    }

    private SetlistResponse toResponse(Setlist setlist) {
        List<SetlistChordResponse> chords = setlist.getChords().stream()
                .sorted(Comparator.comparing(SetlistChord::getPosition))
                .map(item -> {
                    Chord chord = item.getChord();

                    return new SetlistChordResponse(
                            chord.getUuid(),
                            chord.getName(),
                            chordMetadataResolver.fallbackArtist(chord.getArtist()),
                            chord.getAddByUser(),
                            item.getPosition()
                    );
                })
                .toList();

        List<SetlistCollaboratorResponse> collaborators = setlist.getCollaborators().stream()
                .map(item -> new SetlistCollaboratorResponse(
                        item.getUuid(),
                        item.getUser().getUuid(),
                        item.getUser().getUserName(),
                        item.getStatus(),
                        item.getInvitedBy() == null ? null : item.getInvitedBy().getUuid(),
                        item.getInvitedBy() == null ? null : item.getInvitedBy().getUserName()
                ))
                .toList();

        return new SetlistResponse(
                setlist.getUuid(),
                setlist.getName(),
                setlist.getDescription(),
                setlist.getVisibility(),
                setlist.getOwner().getUuid(),
                setlist.getOwner().getUserName(),
                chords,
                collaborators
        );
    }

    private SetlistInviteResponse toInviteResponse(SetlistCollaborator invite) {
        return new SetlistInviteResponse(
                invite.getUuid(),
                invite.getStatus(),
                invite.getSetlist().getUuid(),
                invite.getSetlist().getName(),
                invite.getSetlist().getOwner().getUuid(),
                invite.getSetlist().getOwner().getUserName(),
                invite.getInvitedBy() == null ? null : invite.getInvitedBy().getUuid(),
                invite.getInvitedBy() == null ? null : invite.getInvitedBy().getUserName()
        );
    }
}
