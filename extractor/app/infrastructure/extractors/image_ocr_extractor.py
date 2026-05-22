from __future__ import annotations

import logging
from typing import Optional, Union

import cv2
import numpy as np
import pytesseract
from PIL import Image, UnidentifiedImageError
from pytesseract import Output

try:
    from pillow_heif import register_heif_opener
except ImportError:  # pragma: no cover - dependency is installed in runtime images.
    register_heif_opener = None

from app.config import settings
from app.domain.exceptions.extraction_exceptions import ExtractionException
from app.domain.models.extracted_token import ExtractedToken

logger = logging.getLogger(__name__)
if register_heif_opener is not None:
    register_heif_opener()


def extract_tokens_from_image(
    image_path: str,
    page_number: int = 1,
) -> list[ExtractedToken]:
    image = _load_image(image_path)
    processed = _preprocess_for_ocr(image)

    try:
        data = pytesseract.image_to_data(
            processed,
            output_type=Output.DICT,
            config=f"--psm {settings.ocr_page_segmentation_mode}",
            lang=settings.ocr_languages,
            timeout=settings.ocr_timeout_seconds,
        )
    except RuntimeError as exception:
        raise ExtractionException(
            message="Tempo limite excedido ao executar OCR.",
            code="OCR_TIMEOUT",
        ) from exception

    tokens: list[ExtractedToken] = []

    for index, text in enumerate(data["text"]):
        cleaned = text.strip()

        if not cleaned:
            continue

        confidence = _normalize_confidence(data["conf"][index])

        if confidence is None or confidence < settings.ocr_min_confidence:
            continue

        tokens.append(
            ExtractedToken(
                text=cleaned,
                x=float(data["left"][index]),
                y=float(data["top"][index]),
                width=float(data["width"][index]),
                height=float(data["height"][index]),
                page_number=page_number,
                confidence=confidence,
            )
        )

    logger.info(
        "Image OCR extraction finished",
        extra={"page_number": page_number, "token_count": len(tokens)},
    )
    return tokens


def _load_image(image_path: str) -> np.ndarray:
    try:
        with Image.open(image_path) as image:
            return np.array(image.convert("RGB"))
    except (FileNotFoundError, UnidentifiedImageError) as exception:
        raise ExtractionException(
            message="Não foi possível ler a imagem enviada.",
            code="INVALID_IMAGE",
        ) from exception


def _preprocess_for_ocr(image: np.ndarray) -> np.ndarray:
    gray = cv2.cvtColor(image, cv2.COLOR_RGB2GRAY)
    denoised = cv2.fastNlMeansDenoising(gray, h=12)

    return cv2.threshold(
        denoised,
        0,
        255,
        cv2.THRESH_BINARY + cv2.THRESH_OTSU,
    )[1]


def _normalize_confidence(raw_confidence: Union[str, int, float]) -> Optional[float]:
    try:
        value = float(raw_confidence)
    except (TypeError, ValueError):
        return None

    if value < 0:
        return None

    return min(value / 100, 1.0)
