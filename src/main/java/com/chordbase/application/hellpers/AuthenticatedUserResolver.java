package com.chordbase.application.hellpers;

import com.chordbase.domain.entities.User;
import com.chordbase.infra.security.UserDetailsImp;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUserResolver {
    public User resolve(UserDetailsImp userImp) {
        if (userImp == null || userImp.getUser() == null) {
            throw new IllegalArgumentException("User must not be null");
        }

        return userImp.getUser();
    }
}
