package com.chordbase.domain.repository;

import com.chordbase.domain.entities.Setlist;
import com.chordbase.domain.valueobjects.SetlistCollaboratorStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SetlistRepository extends JpaRepository<Setlist, UUID> {
    @Query("""
            select distinct s
            from Setlist s
            left join s.collaborators c
            where s.owner.uuid = :userUuid or (c.user.uuid = :userUuid and c.status = :acceptedStatus)
            """)
    List<Setlist> findVisibleInUserLibrary(
            @Param("userUuid") UUID userUuid,
            @Param("acceptedStatus") SetlistCollaboratorStatus acceptedStatus
    );
}
