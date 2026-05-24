package com.chordbase.application.services;

import com.chordbase.domain.entities.Role;
import com.chordbase.domain.entities.User;
import com.chordbase.domain.repository.UserRepository;
import com.chordbase.domain.repository.ChordRepository;
import com.chordbase.domain.repository.SetlistRepository;
import com.chordbase.application.hellpers.ChordMetadataResolver;
import com.chordbase.presentation.Dtos.Chord.SimpleChordVizualizationResponse;
import com.chordbase.presentation.Dtos.Setlist.SetlistResponse;
import com.chordbase.domain.valueobjects.UserRole;
import com.chordbase.presentation.Dtos.Admin.AdminUserResponse;
import com.chordbase.presentation.Dtos.Admin.UpdateUserRolesRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AdminService {
    private final UserRepository userRepository;
    private final ChordRepository chordRepository;
    private final SetlistRepository setlistRepository;
    private final SetlistService setlistService;
    private final ChordMetadataResolver chordMetadataResolver;
    private final RefreshTokenService refreshTokenService;

    public AdminService(UserRepository userRepository, ChordRepository chordRepository, SetlistRepository setlistRepository, SetlistService setlistService, ChordMetadataResolver chordMetadataResolver, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.chordRepository = chordRepository;
        this.setlistRepository = setlistRepository;
        this.setlistService = setlistService;
        this.chordMetadataResolver = chordMetadataResolver;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers(User authenticatedUser) {
        validateAdmin(authenticatedUser);

        return userRepository.findAllByOrderByUserNameAsc().stream()
                .map(this::toAdminUserResponse)
                .toList();
    }

    @Transactional
    public AdminUserResponse updateUserRoles(UUID userUuid, UpdateUserRolesRequest request, User authenticatedUser) {
        validateAdmin(authenticatedUser);

        if (request == null || request.roles() == null || request.roles().isEmpty()) {
            throw new IllegalArgumentException("Roles must not be empty");
        }

        User target = userRepository.findById(userUuid)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userUuid));

        target.setRoles(request.roles().stream()
                .distinct()
                .map(role -> Role.builder().role(role).build())
                .toList());

        return toAdminUserResponse(userRepository.save(target));
    }

    @Transactional
    public AdminUserResponse updateUserActive(UUID userUuid, Boolean active, User authenticatedUser) {
        validateAdmin(authenticatedUser);

        User target = userRepository.findById(userUuid)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userUuid));
        target.setActive(!Boolean.FALSE.equals(active));
        if (!target.isActive()) {
            refreshTokenService.revokeAllForUser(target);
        }

        return toAdminUserResponse(userRepository.save(target));
    }

    @Transactional(readOnly = true)
    public List<SimpleChordVizualizationResponse> listUserChords(UUID userUuid, User authenticatedUser) {
        validateAdmin(authenticatedUser);

        return chordRepository.findByOwner_UuidOrderByNameAsc(userUuid).stream()
                .map(chord -> new SimpleChordVizualizationResponse(chord.getUuid(), chord.getName(), chordMetadataResolver.fallbackArtist(chord.getArtist()), chord.getAddByUser(), chord.getStatus()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SetlistResponse> listUserSetlists(UUID userUuid, User authenticatedUser) {
        validateAdmin(authenticatedUser);

        return setlistRepository.findByOwner_UuidOrderByNameAsc(userUuid).stream()
                .map(setlist -> setlistService.getSetlist(setlist.getUuid(), authenticatedUser))
                .toList();
    }

    private void validateAdmin(User user) {
        if (user == null || !user.isActive() || !isAdmin(user)) {
            throw new AccessDeniedException("Admin access required");
        }
    }

    private boolean isAdmin(User user) {
        return user != null && user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> UserRole.ROLE_ADMIN.equals(role.getRole()));
    }

    private AdminUserResponse toAdminUserResponse(User user) {
        return new AdminUserResponse(
                user.getUuid(),
                user.getUserName(),
                user.getEmail(),
                user.getProfileImageUrl(),
                user.getDescription(),
                user.getActive(),
                user.getRoles().stream().map(Role::getRole).toList()
        );
    }
}
