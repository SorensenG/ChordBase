package com.chordbase.domain.entities;

import com.chordbase.domain.valueobjects.SetlistCollaboratorStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
        name = "setlist_collaborators",
        uniqueConstraints = @UniqueConstraint(
                name = "setlist_collaborators_setlist_user_unique",
                columnNames = {"setlist_uuid", "user_uuid"}
        )
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class SetlistCollaborator {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "setlist_uuid", nullable = false)
    private Setlist setlist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_uuid", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SetlistCollaboratorStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_user_uuid")
    private User invitedBy;
}
