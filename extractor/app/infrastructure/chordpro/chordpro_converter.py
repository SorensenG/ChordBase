from __future__ import annotations

import re
from bisect import bisect_right

from app.domain.models.extracted_line import ExtractedLine
from app.domain.models.extracted_token import ExtractedToken
from app.infrastructure.chordpro.chord_detector import is_chord
from app.infrastructure.chordpro.line_classifier import (
    is_chord_line,
    is_metadata_line,
    is_section_line,
    is_tablature_line,
)
from app.infrastructure.chordpro.token_normalizer import (
    collapse_overprinted_tokens,
    tokens_to_text,
)


def convert_lines_to_chordpro(lines: list[ExtractedLine]) -> str:
    output: list[str] = []
    index = 0

    while index < len(lines):
        current_line = lines[index]
        current_tokens = collapse_overprinted_tokens(current_line.tokens)
        current_text = tokens_to_text(current_tokens)

        if is_section_line(current_line):
            section_name = current_text.strip().strip("[]")
            output.append(f"{{section: {section_name}}}")
            index += 1
            continue

        if is_tablature_line(current_line):
            output.append(current_line.text)
            index += 1
            continue

        if is_phrase_cue_line(current_line):
            next_line = lines[index + 1] if index + 1 < len(lines) else None
            output.append(format_positioned_cue_line(current_line, next_line))
            index += 1
            continue

        if is_chord_line(current_line):
            next_line = lines[index + 1] if index + 1 < len(lines) else None

            if next_line and is_tablature_line(next_line):
                output.append(format_positioned_chord_line(current_line, next_line))
                index += 1
                continue

            if (
                next_line
                and not is_chord_line(next_line)
                and not is_tablature_line(next_line)
                and not is_section_line(next_line)
                and not is_metadata_line(next_line)
            ):
                output.append(
                    merge_chords_with_lyrics(
                        chord_tokens=current_tokens,
                        lyric_tokens=next_line.tokens,
                    )
                )
                index += 2
                continue

            output.extend(_format_chord_only_line(current_tokens))
            index += 1
            continue

        output.append(current_text or current_line.text)
        index += 1

    return "\n".join(output)


def is_phrase_cue_line(line: ExtractedLine) -> bool:
    text = tokens_to_text(collapse_overprinted_tokens(line.tokens)).strip()

    return bool(_PHRASE_CUE_REGEX.match(text))


def format_positioned_cue_line(
    cue_line: ExtractedLine,
    reference_line: ExtractedLine | None,
) -> str:
    cue_tokens = collapse_overprinted_tokens(cue_line.tokens)
    cue_text = tokens_to_text(cue_tokens) or cue_line.text
    cue_chord = next((token for token in cue_tokens if is_chord(token.text)), None)

    if cue_chord is None or reference_line is None:
        return cue_text

    reference_tokens = collapse_overprinted_tokens(reference_line.tokens)

    if not reference_tokens or is_tablature_line(reference_line):
        return cue_text

    reference_text = tokens_to_text(reference_tokens)
    char_positions = _estimate_character_positions(reference_tokens, reference_text)
    column = _find_insert_position(
        chord=cue_chord,
        lyric_tokens=reference_tokens,
        lyric_text=reference_text,
        char_positions=char_positions,
        is_first_chord=True,
    )

    return f"{' ' * column}{cue_text}"


def format_positioned_chord_line(
    chord_line: ExtractedLine,
    reference_line: ExtractedLine,
) -> str:
    chord_tokens = [
        token
        for token in collapse_overprinted_tokens(chord_line.tokens)
        if is_chord(token.text)
    ]

    if not chord_tokens:
        return tokens_to_text(collapse_overprinted_tokens(chord_line.tokens))

    reference_tokens = collapse_overprinted_tokens(reference_line.tokens)

    if not reference_tokens:
        return " ".join(f"[{token.text}]" for token in chord_tokens)

    reference_text = tokens_to_text(reference_tokens) or reference_line.text
    char_positions = _estimate_character_positions(reference_tokens, reference_text)
    chars = list(" " * max(len(reference_text), 1))

    for chord in sorted(chord_tokens, key=lambda token: token.x):
        column = _find_insert_position(
            chord=chord,
            lyric_tokens=reference_tokens,
            lyric_text=reference_text,
            char_positions=char_positions,
            is_first_chord=False,
        )
        chord_text = f"[{chord.text}]"
        while _has_text_collision(chars, column, chord_text):
            column += 1
        while len(chars) < column + len(chord_text):
            chars.append(" ")
        for index, character in enumerate(chord_text):
            chars[column + index] = character

    return "".join(chars).rstrip()


def merge_chords_with_lyrics(
    chord_tokens: list[ExtractedToken],
    lyric_tokens: list[ExtractedToken],
) -> str:
    if not lyric_tokens:
        normalized_chords = collapse_overprinted_tokens(chord_tokens)
        return " ".join(f"[{chord.text}]" for chord in normalized_chords)

    normalized_chords = collapse_overprinted_tokens(chord_tokens)
    normalized_lyrics = collapse_overprinted_tokens(lyric_tokens)
    lyric_text = tokens_to_text(normalized_lyrics)
    char_positions = _estimate_character_positions(normalized_lyrics, lyric_text)
    insertions: dict[int, list[str]] = {}

    sorted_chords = sorted(normalized_chords, key=lambda token: token.x)

    for chord_index, chord in enumerate(sorted_chords):
        position = _find_insert_position(
            chord=chord,
            lyric_tokens=normalized_lyrics,
            lyric_text=lyric_text,
            char_positions=char_positions,
            is_first_chord=chord_index == 0,
        )
        insertions.setdefault(position, []).append(chord.text)

    parts: list[str] = []

    for index, character in enumerate(lyric_text):
        if index in insertions:
            parts.extend(f"[{chord}]" for chord in insertions[index])
        parts.append(character)

    end_position = len(lyric_text)
    if end_position in insertions:
        if parts and not parts[-1].isspace():
            parts.append(" ")
        parts.extend(f"[{chord}]" for chord in insertions[end_position])

    return "".join(parts)


def _format_chord_only_line(tokens: list[ExtractedToken]) -> list[str]:
    section_tokens = [
        token for token in tokens if token.text.startswith("[") and token.text.endswith("]")
    ]
    chord_tokens = [token for token in tokens if is_chord(token.text)]
    output: list[str] = []

    for token in section_tokens:
        output.append(f"{{section: {token.text.strip('[]')}}}")

    if chord_tokens:
        output.append(" ".join(f"[{token.text}]" for token in chord_tokens))

    if not output:
        output.append(tokens_to_text(tokens))

    return output


def _estimate_character_positions(
    lyric_tokens: list[ExtractedToken],
    lyric_text: str,
) -> list[float]:
    first_x = min(token.x for token in lyric_tokens)
    last_x = max(token.x + token.width for token in lyric_tokens)
    usable_width = max(last_x - first_x, 1.0)

    if len(lyric_text) <= 1:
        return [first_x]

    average_char_width = usable_width / max(len(lyric_text), 1)
    return [first_x + index * average_char_width for index in range(len(lyric_text))]


def _find_insert_position(
    chord: ExtractedToken,
    lyric_tokens: list[ExtractedToken],
    lyric_text: str,
    char_positions: list[float],
    is_first_chord: bool = False,
) -> int:
    if not char_positions:
        return 0

    if is_first_chord and _should_snap_first_chord_to_start(chord, lyric_tokens):
        return 0

    if chord.x <= char_positions[0]:
        return 0

    if chord.x >= char_positions[-1]:
        return len(lyric_text)

    position = bisect_right(char_positions, chord.x) - 1

    while position > 0 and lyric_text[position].isspace():
        position -= 1

    return max(position, 0)


def _should_snap_first_chord_to_start(
    chord: ExtractedToken,
    lyric_tokens: list[ExtractedToken],
) -> bool:
    first = lyric_tokens[0]
    last = lyric_tokens[-1]
    lyric_width = max((last.x + last.width) - first.x, 1.0)

    return first.x < chord.x <= first.x + lyric_width * 0.25


def _has_text_collision(chars: list[str], start: int, text: str) -> bool:
    if start > 0 and start < len(chars) and chars[start - 1] != " ":
        return True

    for index, _character in enumerate(text):
        target = start + index
        if target >= len(chars):
            return False
        if chars[target] != " ":
            return True

    return False


_PHRASE_CUE_REGEX = re.compile(
    r"^(?:[A-G](?:#|b)?\S*\s+)?\([^)]*frase[^)]*\)$",
    re.IGNORECASE,
)
