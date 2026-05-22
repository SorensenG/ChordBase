import logging
from tempfile import TemporaryDirectory

from pdf2image import convert_from_path
from pdf2image.exceptions import PDFPopplerTimeoutError

from app.config import settings
from app.domain.exceptions.extraction_exceptions import ExtractionException
from app.domain.models.extracted_token import ExtractedToken
from app.infrastructure.extractors.image_ocr_extractor import extract_tokens_from_image

logger = logging.getLogger(__name__)


def extract_tokens_from_scanned_pdf(file_path: str) -> list[ExtractedToken]:
    all_tokens: list[ExtractedToken] = []

    with TemporaryDirectory() as temp_dir:
        try:
            pages = convert_from_path(
                file_path,
                dpi=settings.ocr_dpi,
                first_page=1,
                last_page=settings.max_pdf_pages,
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

        for page_index, image_path in enumerate(pages, start=1):
            all_tokens.extend(
                extract_tokens_from_image(
                    image_path=image_path,
                    page_number=page_index,
                )
            )

    logger.info("PDF OCR extraction finished", extra={"token_count": len(all_tokens)})
    return all_tokens
