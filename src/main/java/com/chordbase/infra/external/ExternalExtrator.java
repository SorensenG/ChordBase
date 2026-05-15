package com.chordbase.infra.external;

import com.chordbase.presentation.Dtos.Chord.ChordExtractionResponse;
import org.springframework.web.multipart.MultipartFile;

public interface ExternalExtrator {
    ChordExtractionResponse extractChordPro(MultipartFile file);

}
