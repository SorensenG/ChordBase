import logging
import math
from tempfile import TemporaryDirectory

import fitz
from pdf2image import convert_from_path
from pdf2image.exceptions import PDFPopplerTimeoutError

from app.config import settings
from app.domain.exceptions.extraction_exceptions import ExtractionException
from app.domain.models.extracted_token import ExtractedToken
from app.infrastructure.extractors.image_ocr_extractor import extract_tokens_from_image

logger = logging.getLogger(__name__)


def extract_tokens_from_scanned_pdf(file_path: str) -> list[ExtractedToken]:
    all_tokens: list[ExtractedToken] = []
    page_count = _validate_render_budget(file_path)

    with TemporaryDirectory() as temp_dir:
        for page_index in range(1, page_count + 1):
            try:
                pages = convert_from_path(
                    file_path,
                    dpi=settings.ocr_dpi,
                    first_page=page_index,
                    last_page=page_index,
                    output_folder=temp_dir,
                    fmt="png",
                    paths_only=True,
                    timeout=settings.ocr_timeout_seconds,
                )
            except PDFPopplerTimeoutError as exception:
                raise ExtractionException(
                    message="Tempo limite excedido ao converter PDF para imagem.",
                    code="PDF_CONVERSION_TIMEOUT",
                ) from exception
            all_tokens.extend(
                extract_tokens_from_image(
                    image_path=pages[0],
                    page_number=page_index,
                )
            )

    logger.info("PDF OCR extraction finished", extra={"token_count": len(all_tokens)})
    return all_tokens


def _validate_render_budget(file_path: str) -> int:
    total_pixels = 0
    with fitz.open(file_path) as document:
        page_count = min(document.page_count, settings.max_pdf_pages)
        for page_index in range(page_count):
            page = document.load_page(page_index)
            width = math.ceil(page.rect.width * settings.ocr_dpi / 72)
            height = math.ceil(page.rect.height * settings.ocr_dpi / 72)
            page_pixels = width * height
            total_pixels += page_pixels
            if (
                page_pixels > settings.pdf_max_rendered_pixels_per_page
                or total_pixels > settings.pdf_max_rendered_pixels_document
            ):
                raise ExtractionException(
                    message="O PDF é grande demais para rasterização segura.",
                    code="PDF_RENDER_BUDGET_EXCEEDED",
                )
    return page_count
