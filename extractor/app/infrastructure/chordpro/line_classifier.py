import re

from app.domain.models.extracted_line import ExtractedLine
from app.infrastructure.chordpro.chord_detector import is_chord
from app.infrastructure.chordpro.token_normalizer import (
    collapse_overprinted_tokens,
    tokens_to_text,
)

TABLATURE_REGEX = re.compile(
    r"^("
    r"[eEABGDC]|"
    r"HH|H|S|SD|B|BD|K|C|CC|RC|R|T|T1|T2|T3|F"
    r")\|"
)


def is_section_line(line: ExtractedLine) -> bool:
    text = tokens_to_text(collapse_overprinted_tokens(line.tokens)).strip()

    return text.startswith("[") and text.endswith("]") and len(text) <= 80


def is_tablature_line(line: ExtractedLine) -> bool:
    text = line.text.strip()

    return bool(TABLATURE_REGEX.match(text))


def is_metadata_line(line: ExtractedLine) -> bool:
    text = tokens_to_text(collapse_overprinted_tokens(line.tokens)).strip().lower()

    return text.startswith(("tom:", "afinação:", "afinacao:", "capo:", "capotraste:"))


def is_chord_line(line: ExtractedLine) -> bool:
    if is_tablature_line(line) or is_metadata_line(line):
        return False

    tokens = collapse_overprinted_tokens(line.tokens)

    if not tokens:
        return False

    chord_count = sum(1 for token in tokens if is_chord(token.text))
    ratio = chord_count / len(tokens)

    return ratio >= 0.7 and chord_count >= 1
