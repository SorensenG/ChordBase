from __future__ import annotations

import logging
import mimetypes
import re
from pathlib import Path
from time import perf_counter
from typing import Optional

from app.config import settings
from app.domain.enums.extraction_status import ExtractionStatus
from app.domain.enums.source_type import SourceType
from app.domain.exceptions.extraction_exceptions import UnsupportedFileException
from app.domain.models.extracted_token import ExtractedToken
from app.domain.models.extraction_result import ExtractionMetadata, ExtractionResult
from app.infrastructure.chordpro.chordpro_converter import convert_lines_to_chordpro
from app.infrastructure.chordpro.diagram_filter import (
    DIAGRAM_REMOVED_WARNING,
    remove_final_chord_diagram_block,
)
from app.infrastructure.chordpro.line_grouper import group_tokens_by_lines
from app.infrastructure.extractors.image_ocr_extractor import extract_tokens_from_image
from app.infrastructure.extractors.pdf_ocr_extractor import extract_tokens_from_scanned_pdf
from app.infrastructure.extractors.pdf_text_extractor import (
    extract_tokens_from_text_pdf,
    has_enough_text,
)
from app.infrastructure.extractors.text_file_extractor import extract_tokens_from_text_file

logger = logging.getLogger(__name__)

NO_CHORD_IMAGE_MESSAGE = (
    "Não consegui identificar uma cifra nessa imagem. "
    "Envie uma foto mais nítida, PDF ou TXT."
)


class ChordproExtractionService:
    def extract(
        self,
        file_path: str,
        filename: Optional[str],
        mime_type: Optional[str],
        file_size_bytes: int,
        request_id: str,
    ) -> ExtractionResult:
        started_at = perf_counter()
        detected_mime_type = self._detect_mime_type(filename, mime_type)

        extraction = self._extract_tokens(
            file_path=file_path,
            mime_type=detected_mime_type,
            request_id=request_id,
        )

        tokens = extraction.tokens
        warnings = extraction.warnings

        if not tokens:
            message = (
                NO_CHORD_IMAGE_MESSAGE
                if extraction.source_type == SourceType.OCR_IMAGE
                else "Não foi possível extrair texto do arquivo."
            )
            return self._build_failed_result(
                filename=filename,
                mime_type=detected_mime_type,
                file_size_bytes=file_size_bytes,
                source_type=extraction.source_type,
                warnings=warnings + [message],
                started_at=started_at,
            )

        lines = group_tokens_by_lines(tokens)
        diagram_filter_result = remove_final_chord_diagram_block(lines)
        lines = diagram_filter_result.lines

        if diagram_filter_result.removed_diagram_block:
            warnings.append(DIAGRAM_REMOVED_WARNING)

        chordpro = convert_lines_to_chordpro(lines)

        if (
            extraction.source_type == SourceType.OCR_IMAGE
            and not _has_chord_signals(chordpro)
        ):
            return self._build_failed_result(
                filename=filename,
                mime_type=detected_mime_type,
                file_size_bytes=file_size_bytes,
                source_type=extraction.source_type,
                warnings=warnings + [NO_CHORD_IMAGE_MESSAGE],
                started_at=started_at,
                pages_processed=_count_pages(tokens),
                token_count=len(tokens),
                line_count=len(lines),
            )

        confidence = self._calculate_confidence(tokens, extraction.source_type, chordpro)

        status = (
            ExtractionStatus.DONE
            if confidence >= settings.review_confidence_threshold
            else ExtractionStatus.NEEDS_REVIEW
        )

        if status == ExtractionStatus.NEEDS_REVIEW:
            warnings.append("A cifra convertida deve ser revisada manualmente.")

        return ExtractionResult(
            status=status,
            source_type=extraction.source_type,
            chordpro=chordpro,
            confidence=confidence,
            warnings=warnings,
            metadata=ExtractionMetadata(
                filename=filename,
                mime_type=detected_mime_type,
                file_size_bytes=file_size_bytes,
                pages_processed=_count_pages(tokens),
                token_count=len(tokens),
                line_count=len(lines),
            ),
            processing_time_ms=_elapsed_ms(started_at),
        )

    def _extract_tokens(
        self,
        file_path: str,
        mime_type: str,
        request_id: str,
    ) -> _TokenExtraction:
        logger.info(
            "Starting token extraction",
            extra={"request_id": request_id, "mime_type": mime_type},
        )

        if mime_type == "application/pdf":
            text_tokens = extract_tokens_from_text_pdf(file_path)

            if has_enough_text(text_tokens):
                return _TokenExtraction(
                    source_type=SourceType.TEXT_PDF,
                    tokens=text_tokens,
                    warnings=[],
                )

            ocr_tokens = extract_tokens_from_scanned_pdf(file_path)

            return _TokenExtraction(
                source_type=SourceType.OCR_PDF,
                tokens=ocr_tokens,
                warnings=[
                    "PDF sem texto selecionável suficiente. OCR foi utilizado.",
                    f"Apenas as primeiras {settings.max_pdf_pages} páginas são processadas.",
                ],
            )

        if mime_type == "text/plain":
            return _TokenExtraction(
                source_type=SourceType.TEXT_FILE,
                tokens=extract_tokens_from_text_file(file_path),
                warnings=[
                    "Arquivo TXT processado como texto puro. "
                    "Preserve espaços para melhor alinhamento."
                ],
            )

        if mime_type.startswith("image/"):
            return _TokenExtraction(
                source_type=SourceType.OCR_IMAGE,
                tokens=extract_tokens_from_image(file_path),
                warnings=[
                    "Imagem processada por OCR. Revise o resultado antes de salvar."
                ],
            )

        raise UnsupportedFileException(
            "Formato não suportado. Envie PDF, PNG, JPG, JPEG, WEBP, HEIC, HEIF ou TXT."
        )

    def _detect_mime_type(self, filename: Optional[str], mime_type: Optional[str]) -> str:
        normalized_mime_type = (mime_type or "").lower()

        if normalized_mime_type in settings.allowed_mime_types:
            return normalized_mime_type

        guessed_mime_type = mimetypes.guess_type(filename or "")[0]

        if guessed_mime_type and guessed_mime_type in settings.allowed_mime_types:
            return guessed_mime_type

        suffix = Path(filename or "").suffix.lower()
        if suffix == ".pdf":
            return "application/pdf"
        if suffix in {".jpg", ".jpeg"}:
            return "image/jpeg"
        if suffix == ".png":
            return "image/png"
        if suffix == ".webp":
            return "image/webp"
        if suffix == ".heic":
            return "image/heic"
        if suffix == ".heif":
            return "image/heif"
        if suffix == ".txt":
            return "text/plain"

        raise UnsupportedFileException(
            "Não foi possível identificar o tipo do arquivo."
        )

    def _calculate_confidence(
        self,
        tokens: list[ExtractedToken],
        source_type: SourceType,
        chordpro: str,
    ) -> float:
        if not tokens or not chordpro.strip():
            return 0.0

        if source_type in {SourceType.TEXT_FILE, SourceType.TEXT_PDF}:
            return 0.90

        confidences = [
            token.confidence
            for token in tokens
            if token.confidence is not None
        ]

        if not confidences:
            return 0.50

        average_confidence = sum(confidences) / len(confidences)

        # OCR output should normally pass through review even when Tesseract is optimistic.
        return min(round(average_confidence, 2), 0.78)

    def _build_failed_result(
        self,
        filename: Optional[str],
        mime_type: str,
        file_size_bytes: int,
        source_type: SourceType,
        warnings: list[str],
        started_at: float,
        pages_processed: int | None = None,
        token_count: int = 0,
        line_count: int = 0,
    ) -> ExtractionResult:
        return ExtractionResult(
            status=ExtractionStatus.FAILED,
            source_type=source_type,
            chordpro=None,
            confidence=0.0,
            warnings=warnings,
            metadata=ExtractionMetadata(
                filename=filename,
                mime_type=mime_type,
                file_size_bytes=file_size_bytes,
                pages_processed=pages_processed,
                token_count=token_count,
                line_count=line_count,
            ),
            processing_time_ms=_elapsed_ms(started_at),
        )


class _TokenExtraction:
    def __init__(
        self,
        source_type: SourceType,
        tokens: list[ExtractedToken],
        warnings: list[str],
    ):
        self.source_type = source_type
        self.tokens = tokens
        self.warnings = warnings


def _count_pages(tokens: list[ExtractedToken]) -> int:
    return len({token.page_number for token in tokens})


def _elapsed_ms(started_at: float) -> int:
    return round((perf_counter() - started_at) * 1000)


def _has_chord_signals(chordpro: str) -> bool:
    if not chordpro.strip():
        return False

    return any(
        pattern.search(chordpro)
        for pattern in (
            _CHORD_MARKER_REGEX,
            _TONE_METADATA_REGEX,
            _TABLATURE_REGEX,
        )
    )


_CHORD_MARKER_REGEX = re.compile(
    r"\[[A-G](?:#|b)?(?:m|maj|min|dim|aug|sus|add|º|°|[0-9()/+#b-])*\]"
)
_TONE_METADATA_REGEX = re.compile(r"(?im)^(?:tom|key|capo|afina(?:ç|c)ão)\s*:")
_TABLATURE_REGEX = re.compile(r"(?im)^[EADGBE]\|")
