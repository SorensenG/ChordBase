package com.chordbase.domain.repository;

import com.chordbase.domain.entities.Chord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ChordRepository extends JpaRepository<Chord, UUID> {
}
