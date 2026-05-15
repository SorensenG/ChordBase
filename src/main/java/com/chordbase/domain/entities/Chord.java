package com.chordbase.domain.entities;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@AllArgsConstructor
@Builder
@Getter
@Setter
@NoArgsConstructor
@Table(name = "chords")
public class Chord {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    private String name;

    private String artist;

    private String addByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_uuid")
    private User owner;

    @Column(columnDefinition = "text")
    private String chordPro;

    private String status;

    private String sourceType;

    private Double confidence;




}
