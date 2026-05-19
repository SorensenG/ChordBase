package com.chordbase.domain.repository;

import com.chordbase.domain.entities.Chord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChordRepository extends JpaRepository<Chord, UUID> {
    List<Chord> findByNameContainingIgnoreCaseAndStatus(String name, String status);

    List<Chord> findByOwner_UuidAndStatusNotOrderByNameAsc(UUID ownerUuid, String status);

    List<Chord> findByOwner_UuidOrderByNameAsc(UUID ownerUuid);

    @Query("select count(sc) from SetlistChord sc where sc.chord.uuid = :chordUuid")
    long countSetlistReferences(@Param("chordUuid") UUID chordUuid);
}
