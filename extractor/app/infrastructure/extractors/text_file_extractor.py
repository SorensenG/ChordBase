from __future__ import annotations

import logging

from app.domain.exceptions.extraction_exceptions import ExtractionException
from app.domain.models.extracted_token import ExtractedToken

logger = logging.getLogger(__name__)

CHAR_WIDTH = 8.0
LINE_HEIGHT = 16.0
TOKEN_HEIGHT = 12.0


def extract_tokens_from_text_file(file_path: str) -> list[ExtractedToken]:
    text = _read_text_file(file_path)
    tokens: list[ExtractedToken] = []

    for line_index, line_text in enumerate(text.splitlines()):
        tokens.extend(_extract_line_tokens(line_text=line_text, line_index=line_index))

    logger.info("Text file extraction finished", extra={"token_count": len(tokens)})
    return tokens


def _read_text_file(file_path: str) -> str:
    for encoding in ("utf-8-sig", "utf-8", "latin-1"):
        try:
            with open(file_path, encoding=encoding) as text_file:
                return text_file.read()
        except UnicodeDecodeError:
            continue
        except OSError as exception:
            raise ExtractionException(
                message="Não foi possível ler o arquivo TXT enviado.",
                code="INVALID_TEXT_FILE",
            ) from exception

    raise ExtractionException(
        message="Não foi possível identificar a codificação do arquivo TXT.",
        code="INVALID_TEXT_ENCODING",
    )


def _extract_line_tokens(line_text: str, line_index: int) -> list[ExtractedToken]:
    tokens: list[ExtractedToken] = []
    cursor = 0

    for raw_token in line_text.split():
        start_index = line_text.find(raw_token, cursor)

        if start_index < 0:
            continue

        cursor = start_index + len(raw_token)

        tokens.append(
            ExtractedToken(
                text=raw_token,
                x=start_index * CHAR_WIDTH,
                y=line_index * LINE_HEIGHT,
                width=max(len(raw_token) * CHAR_WIDTH, CHAR_WIDTH),
                height=TOKEN_HEIGHT,
                page_number=1,
                confidence=1.0,
            )
        )

    return tokens
