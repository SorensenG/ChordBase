package com.chordbase.application.hellpers;

import org.springframework.stereotype.Component;

@Component
public class ChordMetadataResolver {
    private static final String UNKNOWN_ARTIST = "Não informado";
    private static final String UNTITLED_CHORD = "Sem título";

    public String resolveSuggestedChordName(String chordPro, String fallbackName) {
        if (chordPro == null || chordPro.isBlank()) {
            return fallbackChordName(fallbackName);
        }

        for (String line : chordPro.split("\\R")) {
            String normalizedLine = line.trim();
            String explicitTitle = extractValueAfterPrefix(normalizedLine, "title:", "titulo:", "título:", "{title:", "{t:");

            if (explicitTitle != null) {
                return explicitTitle;
            }

            if (isCandidateTextLine(normalizedLine)) {
                return normalizedLine;
            }
        }

        return fallbackChordName(fallbackName);
    }

    public String resolveArtist(String chordPro, String suggestedChordName) {
        if (chordPro == null || chordPro.isBlank()) {
            return UNKNOWN_ARTIST;
        }

        String[] lines = chordPro.split("\\R");

        for (String line : lines) {
            String explicitArtist = extractValueAfterPrefix(line.trim(), "artist:", "artista:", "intérprete:", "interprete:", "{artist:", "{subtitle:", "subtitle:");

            if (explicitArtist != null) {
                return explicitArtist;
            }
        }

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            if (!containsCompositionMarker(line)) {
                continue;
            }

            String previousLine = previousCandidateLine(lines, i, suggestedChordName);

            if (previousLine != null) {
                return previousLine;
            }

            String composer = extractValueAfterPrefix(line, "composição de:", "composicao de:");

            if (composer != null) {
                return composer;
            }
        }

        return UNKNOWN_ARTIST;
    }

    public String fallbackArtist(String artist) {
        if (artist == null || artist.isBlank()) {
            return UNKNOWN_ARTIST;
        }

        return artist;
    }

    private String fallbackChordName(String chordName) {
        if (chordName == null || chordName.isBlank()) {
            return UNTITLED_CHORD;
        }

        return chordName;
    }

    private String previousCandidateLine(String[] lines, int currentIndex, String suggestedChordName) {
        for (int i = currentIndex - 1; i >= 0; i--) {
            String line = lines[i].trim();

            if (isCandidateTextLine(line) && !line.equalsIgnoreCase(suggestedChordName)) {
                return line;
            }
        }

        return null;
    }

    private boolean containsCompositionMarker(String line) {
        String normalizedLine = normalize(line);
        return normalizedLine.contains("composicao de:");
    }

    private String extractValueAfterPrefix(String line, String... prefixes) {
        String normalizedLine = normalize(line);

        for (String prefix : prefixes) {
            String normalizedPrefix = normalize(prefix);

            if (!normalizedLine.startsWith(normalizedPrefix)) {
                continue;
            }

            String value = line.substring(prefix.length()).trim();

            if (value.endsWith("}")) {
                value = value.substring(0, value.length() - 1).trim();
            }

            if (!value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    private boolean isCandidateTextLine(String line) {
        return line != null
                && !line.isBlank()
                && !line.startsWith("[")
                && !line.startsWith("{")
                && !line.contains(":");
    }

    private String normalize(String text) {
        return text.toLowerCase()
                .replace("ç", "c")
                .replace("ã", "a")
                .replace("á", "a")
                .replace("à", "a")
                .replace("â", "a")
                .replace("é", "e")
                .replace("ê", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ô", "o")
                .replace("õ", "o")
                .replace("ú", "u");
    }
}
