package com.chordbase.application.services;

import com.chordbase.domain.repository.UserRepository;
import com.chordbase.infra.security.JwtTokenService;
import com.chordbase.infra.security.SecurityConfiguration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    final private AuthenticationManager authenticationManager;

    final private JwtTokenService jwtTokenService;

    final private UserRepository userRepository;
    final private SecurityConfiguration securityConfiguration;


    public UserService(AuthenticationManager authenticationManager, JwtTokenService jwtTokenService, UserRepository userRepository, SecurityConfiguration securityConfiguration) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
        this.userRepository = userRepository;
        this.securityConfiguration = securityConfiguration;
    }




}
