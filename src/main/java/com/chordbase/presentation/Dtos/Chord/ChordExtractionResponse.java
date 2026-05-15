package com.chordbase.presentation.Dtos.Chord;

public record ChordExtractionResponse(
        String requestId,
        String status,
        String sourceType,
        String chordPro,
        Double confidence,
        String[] warnings,
        Metadata metadata,
        Long processingTimeMs
) {
    public record Metadata(
            String filename,

            String addByUser,
            String mimeType,
            Long fileSizeBytes,
            Integer pagesProcessed,
            Integer tokenCount,
            Integer lineCount
    ) {
    }
}
