package com.chordbase.domain.entities;

import com.chordbase.domain.valueobjects.SetlistVisibility;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "setlists")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class Setlist {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SetlistVisibility visibility;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_uuid", nullable = false)
    private User owner;

    @OneToMany(mappedBy = "setlist", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @Builder.Default
    private List<SetlistChord> chords = new ArrayList<>();

    @OneToMany(mappedBy = "setlist", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SetlistCollaborator> collaborators = new ArrayList<>();
}
