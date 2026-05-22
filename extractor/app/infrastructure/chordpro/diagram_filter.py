from __future__ import annotations

import re
from dataclasses import dataclass

from app.domain.models.extracted_line import ExtractedLine
from app.infrastructure.chordpro.chord_detector import is_chord
from app.infrastructure.chordpro.line_classifier import is_tablature_line
from app.infrastructure.chordpro.token_normalizer import (
    collapse_overprinted_tokens,
    tokens_to_text,
)

DIAGRAM_REMOVED_WARNING = "Bloco final de diagramas de acordes removido do resultado."

FINGERING_TOKEN_REGEX = re.compile(r"^(\d+|[xXoO]|[xXoO]+|\d+ª)$")


@dataclass(frozen=True)
class DiagramFilterResult:
    lines: list[ExtractedLine]
    removed_diagram_block: bool


def remove_final_chord_diagram_block(lines: list[ExtractedLine]) -> DiagramFilterResult:
    start_index = _find_final_diagram_block_start(lines)

    if start_index is None:
        return DiagramFilterResult(lines=lines, removed_diagram_block=False)

    return DiagramFilterResult(
        lines=lines[:start_index],
        removed_diagram_block=True,
    )


def _find_final_diagram_block_start(lines: list[ExtractedLine]) -> int | None:
    search_start = max(0, len(lines) - 80)

    for index in range(search_start, len(lines)):
        if _looks_like_chord_diagram_header(lines[index]) and _has_fingering_lines_after(
            lines=lines,
            index=index,
        ):
            return index

    return None


def _looks_like_chord_diagram_header(line: ExtractedLine) -> bool:
    if is_tablature_line(line):
        return False

    tokens = collapse_overprinted_tokens(line.tokens)

    if len(tokens) < 3:
        return False

    chord_count = sum(1 for token in tokens if is_chord(token.text))
    ratio = chord_count / len(tokens)

    return chord_count >= 3 and ratio >= 0.75


def _has_fingering_lines_after(lines: list[ExtractedLine], index: int) -> bool:
    following = lines[index + 1 : min(len(lines), index + 9)]

    if not following:
        return False

    if not _looks_like_fingering_line(following[0]):
        return False

    fingering_count = sum(1 for line in following if _looks_like_fingering_line(line))
    chord_header_count = sum(1 for line in following if _looks_like_chord_diagram_header(line))

    return fingering_count >= 2 or (fingering_count >= 1 and chord_header_count >= 1)


def _looks_like_fingering_line(line: ExtractedLine) -> bool:
    if is_tablature_line(line):
        return False

    tokens = collapse_overprinted_tokens(line.tokens)

    if not tokens:
        return False

    text = tokens_to_text(tokens).strip()

    if not text:
        return False

    fingering_count = sum(1 for token in tokens if FINGERING_TOKEN_REGEX.match(token.text))
    chord_count = sum(1 for token in tokens if is_chord(token.text))
    ratio = fingering_count / len(tokens)

    return fingering_count >= 1 and chord_count == 0 and ratio >= 0.8
