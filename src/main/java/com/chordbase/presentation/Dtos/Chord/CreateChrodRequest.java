package com.chordbase.presentation.Dtos.Chord;

import org.springframework.web.multipart.MultipartFile;

public record CreateChrodRequest(MultipartFile file, String userName) {
}
