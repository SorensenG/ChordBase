package com.chordbase.application.services;

import com.chordbase.application.hellpers.ChordMetadataResolver;
import com.chordbase.domain.entities.Chord;
import com.chordbase.domain.entities.Setlist;
import com.chordbase.domain.entities.SetlistCollaborator;
import com.chordbase.domain.entities.User;
import com.chordbase.domain.repository.ChordRepository;
import com.chordbase.domain.repository.SetlistCollaboratorRepository;
import com.chordbase.domain.repository.SetlistRepository;
import com.chordbase.domain.repository.UserRepository;
import com.chordbase.domain.valueobjects.ChordStatus;
import com.chordbase.domain.valueobjects.SetlistCollaboratorStatus;
import com.chordbase.domain.valueobjects.SetlistVisibility;
import com.chordbase.domain.valueobjects.UserName;
import com.chordbase.presentation.Dtos.Setlist.CreateSetlistInviteRequest;
import com.chordbase.presentation.Dtos.Setlist.UpdateSetlistRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SetlistServiceTest {
    private final Map<UUID, Setlist> setlists = new HashMap<>();
    private final Map<UUID, SetlistCollaborator> invites = new HashMap<>();
    private final Map<UUID, Chord> chords = new HashMap<>();
    private final Map<UUID, User> users = new HashMap<>();
    private final AtomicInteger setlistSaveCount = new AtomicInteger();

    private SetlistService setlistService;

    @BeforeEach
    void setUp() {
        setlists.clear();
        invites.clear();
        chords.clear();
        users.clear();
        setlistSaveCount.set(0);

        SetlistRepository setlistRepository = repositoryProxy(SetlistRepository.class, (proxy, method, args) -> switch (method.getName()) {
            case "findById" -> Optional.ofNullable(setlists.get((UUID) args[0]));
            case "save" -> {
                Setlist setlist = (Setlist) args[0];
                setlists.put(setlist.getUuid(), setlist);
                setlistSaveCount.incrementAndGet();
                yield setlist;
            }
            case "delete" -> {
                Setlist setlist = (Setlist) args[0];
                setlists.remove(setlist.getUuid());
                yield null;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });

        SetlistCollaboratorRepository setlistCollaboratorRepository = repositoryProxy(SetlistCollaboratorRepository.class, (proxy, method, args) -> switch (method.getName()) {
            case "findById" -> Optional.ofNullable(invites.get((UUID) args[0]));
            case "save" -> {
                SetlistCollaborator invite = (SetlistCollaborator) args[0];
                invites.put(invite.getUuid(), invite);
                yield invite;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });

        ChordRepository chordRepository = repositoryProxy(ChordRepository.class, (proxy, method, args) -> switch (method.getName()) {
            case "findById" -> Optional.ofNullable(chords.get((UUID) args[0]));
            default -> throw new UnsupportedOperationException(method.getName());
        });

        UserRepository userRepository = repositoryProxy(UserRepository.class, (proxy, method, args) -> switch (method.getName()) {
            case "findById" -> Optional.ofNullable(users.get((UUID) args[0]));
            default -> throw new UnsupportedOperationException(method.getName());
        });

        setlistService = new SetlistService(
                setlistRepository,
                setlistCollaboratorRepository,
                chordRepository,
                userRepository,
                new ChordMetadataResolver()
        );
    }

    @Test
    void addChordAddsPublishedChordToOwnerSetlist() {
        User owner = user("gabriel@test.com");
        Chord chord = publishedChord("Samurai");
        Setlist setlist = setlist(owner, SetlistVisibility.PRIVATE);

        users.put(owner.getUuid(), owner);
        setlists.put(setlist.getUuid(), setlist);
        chords.put(chord.getUuid(), chord);

        var response = setlistService.addChord(setlist.getUuid(), chord.getUuid(), owner);

        assertEquals(1, setlist.getChords().size());
        assertEquals(chord.getUuid(), response.chords().getFirst().uuid());
        assertEquals(1, response.chords().getFirst().position());
        assertEquals(1, setlistSaveCount.get());
    }

    @Test
    void addChordDoesNotDuplicateChordAlreadyInSetlist() {
        User owner = user("gabriel@test.com");
        Chord chord = publishedChord("Samurai");
        Setlist setlist = setlist(owner, SetlistVisibility.PRIVATE);

        users.put(owner.getUuid(), owner);
        setlists.put(setlist.getUuid(), setlist);
        chords.put(chord.getUuid(), chord);

        setlistService.addChord(setlist.getUuid(), chord.getUuid(), owner);
        setlistService.addChord(setlist.getUuid(), chord.getUuid(), owner);

        assertEquals(1, setlist.getChords().size());
    }

    @Test
    void addChordRejectsDraftChord() {
        User owner = user("gabriel@test.com");
        Chord chord = draftChord("Samurai");
        Setlist setlist = setlist(owner, SetlistVisibility.PRIVATE);

        users.put(owner.getUuid(), owner);
        setlists.put(setlist.getUuid(), setlist);
        chords.put(chord.getUuid(), chord);

        assertThrows(
                IllegalArgumentException.class,
                () -> setlistService.addChord(setlist.getUuid(), chord.getUuid(), owner)
        );

        assertEquals(0, setlistSaveCount.get());
    }

    @Test
    void addChordRejectsUserWithoutAccess() {
        User owner = user("owner@test.com");
        User otherUser = user("other@test.com");
        Chord chord = publishedChord("Samurai");
        Setlist setlist = setlist(owner, SetlistVisibility.PRIVATE);

        users.put(otherUser.getUuid(), otherUser);
        setlists.put(setlist.getUuid(), setlist);
        chords.put(chord.getUuid(), chord);

        assertThrows(
                AccessDeniedException.class,
                () -> setlistService.addChord(setlist.getUuid(), chord.getUuid(), otherUser)
        );

        assertEquals(0, setlistSaveCount.get());
    }

    @Test
    void inviteCollaboratorCreatesPendingInvite() {
        User owner = user("owner@test.com");
        User collaborator = user("collaborator@test.com");
        Setlist setlist = setlist(owner, SetlistVisibility.COLLABORATIVE);

        users.put(owner.getUuid(), owner);
        users.put(collaborator.getUuid(), collaborator);
        setlists.put(setlist.getUuid(), setlist);

        var response = setlistService.inviteCollaborator(
                setlist.getUuid(),
                new CreateSetlistInviteRequest(collaborator.getUuid()),
                owner
        );

        assertEquals(1, setlist.getCollaborators().size());
        assertEquals(SetlistCollaboratorStatus.PENDING, setlist.getCollaborators().getFirst().getStatus());
        assertEquals(SetlistCollaboratorStatus.PENDING, response.collaborators().getFirst().status());
    }

    @Test
    void acceptedInviteCanEditSetlist() {
        User owner = user("owner@test.com");
        User collaborator = user("collaborator@test.com");
        Chord chord = publishedChord("Samurai");
        Setlist setlist = setlist(owner, SetlistVisibility.COLLABORATIVE);
        SetlistCollaborator invite = SetlistCollaborator.builder()
                .uuid(UUID.randomUUID())
                .setlist(setlist)
                .user(collaborator)
                .status(SetlistCollaboratorStatus.PENDING)
                .invitedBy(owner)
                .build();

        setlist.getCollaborators().add(invite);
        users.put(collaborator.getUuid(), collaborator);
        setlists.put(setlist.getUuid(), setlist);
        invites.put(invite.getUuid(), invite);
        chords.put(chord.getUuid(), chord);

        setlistService.acceptInvite(invite.getUuid(), collaborator);
        setlistService.addChord(setlist.getUuid(), chord.getUuid(), collaborator);

        assertEquals(SetlistCollaboratorStatus.ACCEPTED, invite.getStatus());
        assertEquals(1, setlist.getChords().size());
    }

    @Test
    void updateSetlistClearsCollaboratorsWhenVisibilityStopsBeingCollaborative() {
        User owner = user("owner@test.com");
        User collaborator = user("collaborator@test.com");
        Setlist setlist = setlist(owner, SetlistVisibility.COLLABORATIVE);
        SetlistCollaborator invite = SetlistCollaborator.builder()
                .uuid(UUID.randomUUID())
                .setlist(setlist)
                .user(collaborator)
                .status(SetlistCollaboratorStatus.ACCEPTED)
                .invitedBy(owner)
                .build();

        setlist.getCollaborators().add(invite);
        users.put(owner.getUuid(), owner);
        setlists.put(setlist.getUuid(), setlist);

        var response = setlistService.updateSetlist(
                setlist.getUuid(),
                new UpdateSetlistRequest("Novo show", "Descricao", SetlistVisibility.PRIVATE),
                owner
        );

        assertEquals("Novo show", response.name());
        assertEquals(SetlistVisibility.PRIVATE, response.visibility());
        assertTrue(setlist.getCollaborators().isEmpty());
    }

    @Test
    void deleteSetlistRejectsNonOwner() {
        User owner = user("owner@test.com");
        User otherUser = user("other@test.com");
        Setlist setlist = setlist(owner, SetlistVisibility.PRIVATE);

        users.put(otherUser.getUuid(), otherUser);
        setlists.put(setlist.getUuid(), setlist);

        assertThrows(
                AccessDeniedException.class,
                () -> setlistService.deleteSetlist(setlist.getUuid(), otherUser)
        );

        assertTrue(setlists.containsKey(setlist.getUuid()));
    }

    @Test
    void deleteSetlistRemovesOwnerSetlist() {
        User owner = user("owner@test.com");
        Setlist setlist = setlist(owner, SetlistVisibility.PRIVATE);

        users.put(owner.getUuid(), owner);
        setlists.put(setlist.getUuid(), setlist);

        setlistService.deleteSetlist(setlist.getUuid(), owner);

        assertFalse(setlists.containsKey(setlist.getUuid()));
    }

    @SuppressWarnings("unchecked")
    private <T> T repositoryProxy(Class<T> repositoryType, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(
                repositoryType.getClassLoader(),
                new Class<?>[]{repositoryType},
                handler
        );
    }

    private User user(String email) {
        return User.builder()
                .uuid(UUID.randomUUID())
                .userName(UserName.of(email.substring(0, email.indexOf("@"))))
                .email(email)
                .passwordHash("hash")
                .build();
    }

    private Chord publishedChord(String name) {
        return chord(name, ChordStatus.PUBLISHED);
    }

    private Chord draftChord(String name) {
        return chord(name, ChordStatus.DRAFT);
    }

    private Chord chord(String name, ChordStatus status) {
        return Chord.builder()
                .uuid(UUID.randomUUID())
                .name(name)
                .artist("Djavan")
                .addByUser("gabriel")
                .status(status.name())
                .build();
    }

    private Setlist setlist(User owner, SetlistVisibility visibility) {
        return Setlist.builder()
                .uuid(UUID.randomUUID())
                .name("Show")
                .visibility(visibility)
                .owner(owner)
                .build();
    }
}
