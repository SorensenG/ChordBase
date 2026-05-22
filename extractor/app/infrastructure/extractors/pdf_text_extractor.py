import logging

import fitz

from app.config import settings
from app.domain.models.extracted_token import ExtractedToken

logger = logging.getLogger(__name__)


def extract_tokens_from_text_pdf(file_path: str) -> list[ExtractedToken]:
    tokens: list[ExtractedToken] = []

    with fitz.open(file_path) as document:
        page_limit = min(document.page_count, settings.max_pdf_pages)

        for page_index in range(page_limit):
            page = document.load_page(page_index)
            words = page.get_text("words")

            for word in words:
                x0, y0, x1, y1, text, *_ = word
                cleaned = text.strip()

                if not cleaned:
                    continue

                tokens.append(
                    ExtractedToken(
                        text=cleaned,
                        x=float(x0),
                        y=float(y0),
                        width=float(x1 - x0),
                        height=float(y1 - y0),
                        page_number=page_index + 1,
                        confidence=1.0,
                    )
                )

    logger.info("PDF text extraction finished", extra={"token_count": len(tokens)})
    return tokens


def has_enough_text(tokens: list[ExtractedToken]) -> bool:
    total_chars = sum(len(token.text.strip()) for token in tokens)
    total_tokens = len(tokens)

    return (
        total_chars >= settings.min_pdf_text_chars
        and total_tokens >= settings.min_pdf_text_tokens
    )
