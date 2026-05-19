package com.chordbase.domain.entities;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
        name = "setlist_chords",
        uniqueConstraints = @UniqueConstraint(
                name = "setlist_chords_setlist_chord_unique",
                columnNames = {"setlist_uuid", "chord_uuid"}
        )
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class SetlistChord {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "setlist_uuid", nullable = false)
    private Setlist setlist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chord_uuid", nullable = false)
    private Chord chord;

    @Column(name = "chord_position", nullable = false)
    private Integer position;
}
