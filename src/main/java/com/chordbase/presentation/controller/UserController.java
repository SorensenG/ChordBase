package com.chordbase.presentation.controller;

import com.chordbase.application.services.UserService;
import com.chordbase.presentation.Dtos.User.RegisterUserDtoRequest;
import com.chordbase.presentation.Dtos.User.RegisterUserResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    public ResponseEntity<RegisterUserResponse> registerUser(@Valid @RequestBody RegisterUserDtoRequest request){

    }

}
