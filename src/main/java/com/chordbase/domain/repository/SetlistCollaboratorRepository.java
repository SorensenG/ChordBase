package com.chordbase.domain.repository;

import com.chordbase.domain.entities.SetlistCollaborator;
import com.chordbase.domain.valueobjects.SetlistCollaboratorStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SetlistCollaboratorRepository extends JpaRepository<SetlistCollaborator, UUID> {
    List<SetlistCollaborator> findByUserUuidAndStatus(UUID userUuid, SetlistCollaboratorStatus status);
}
