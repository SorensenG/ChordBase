package com.chordbase.domain.entities;

import com.chordbase.domain.valueobjects.UserRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "roles")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class Role{
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @Enumerated(EnumType.STRING)
    private UserRole role;



}
