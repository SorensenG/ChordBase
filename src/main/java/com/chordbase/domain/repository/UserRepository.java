package com.chordbase.domain.repository;

import com.chordbase.domain.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    @Query("select count(u) > 0 from User u where lower(u.userName.value) = lower(:userName)")
    boolean existsByUserNameIgnoreCase(@Param("userName") String userName);

    @Query("""
            select u
            from User u
            where lower(u.userName.value) like lower(concat('%', :userName, '%'))
            order by u.userName.value asc
            """)
    List<User> searchByUserName(@Param("userName") String userName);
}
