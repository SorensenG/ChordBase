from __future__ import annotations

import logging
import math
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
            original_width, original_height = image.size
            source_pixels = original_width * original_height
            if (
                image.format not in {"JPEG", "MPO"}
                and source_pixels > settings.ocr_max_source_pixels
            ):
                raise ExtractionException(
                    message=(
                        "A imagem é grande demais para processamento seguro. "
                        "Envie uma foto menor ou um PDF."
                    ),
                    code="IMAGE_DIMENSIONS_TOO_LARGE",
                    details={
                        "width": original_width,
                        "height": original_height,
                        "maxSourcePixels": settings.ocr_max_source_pixels,
                    },
                )

            target_width, target_height = _target_size(original_width, original_height)
            was_resized = (target_width, target_height) != (
                original_width,
                original_height,
            )

            if was_resized and image.format in {"JPEG", "MPO"}:
                image.draft("RGB", (target_width, target_height))

            if was_resized:
                image.thumbnail(
                    (target_width, target_height),
                    Image.Resampling.LANCZOS,
                )

            converted = image.convert("RGB")
            processed_width, processed_height = converted.size
            logger.info(
                "Image prepared for OCR",
                extra={
                    "original_width": original_width,
                    "original_height": original_height,
                    "processed_width": processed_width,
                    "processed_height": processed_height,
                    "resized": was_resized,
                },
            )
            return np.asarray(converted)
    except ExtractionException:
        raise
    except (FileNotFoundError, UnidentifiedImageError, Image.DecompressionBombError) as exception:
        raise ExtractionException(
            message="Não foi possível ler a imagem enviada.",
            code="INVALID_IMAGE",
        ) from exception


def _target_size(width: int, height: int) -> tuple[int, int]:
    if width <= 0 or height <= 0:
        raise ExtractionException(
            message="Não foi possível ler a imagem enviada.",
            code="INVALID_IMAGE",
        )

    scale = min(
        1.0,
        settings.ocr_max_edge / max(width, height),
        math.sqrt(settings.ocr_max_pixels / (width * height)),
    )
    return max(1, math.floor(width * scale)), max(1, math.floor(height * scale))


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
