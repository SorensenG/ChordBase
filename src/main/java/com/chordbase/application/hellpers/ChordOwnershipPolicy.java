package com.chordbase.application.hellpers;

import com.chordbase.domain.entities.Chord;
import com.chordbase.domain.entities.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class ChordOwnershipPolicy {
    public void validateOwner(Chord chord, User user) {
        if (user == null || chord.getOwner() == null || !chord.getOwner().getUuid().equals(user.getUuid())) {
            throw new AccessDeniedException("You are not allowed to change this chord");
        }
    }
}
