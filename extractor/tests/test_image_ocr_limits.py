import asyncio

import pytest
from PIL import Image

from app.api.routes import extraction_routes
from app.domain.exceptions.extraction_exceptions import (
    ExtractionBusyException,
    ExtractionException,
)
from app.infrastructure.extractors.image_ocr_extractor import _load_image


def test_resizes_large_jpeg_before_creating_numpy_array(tmp_path, monkeypatch):
    monkeypatch.setattr("app.infrastructure.extractors.image_ocr_extractor.settings.ocr_max_pixels", 1_000_000)
    monkeypatch.setattr("app.infrastructure.extractors.image_ocr_extractor.settings.ocr_max_edge", 1_200)
    image_path = tmp_path / "large.jpg"
    Image.new("RGB", (4000, 3000), color="white").save(image_path, format="JPEG")

    image = _load_image(str(image_path))

    assert image.shape[0] * image.shape[1] <= 1_000_000
    assert max(image.shape[0], image.shape[1]) <= 1_200


def test_rejects_unsafe_large_non_jpeg_image_before_processing(tmp_path, monkeypatch):
    monkeypatch.setattr(
        "app.infrastructure.extractors.image_ocr_extractor.settings.ocr_max_source_pixels",
        100,
    )
    image_path = tmp_path / "large.png"
    Image.new("RGB", (20, 20), color="white").save(image_path, format="PNG")

    with pytest.raises(ExtractionException) as error:
        _load_image(str(image_path))

    assert error.value.code == "IMAGE_DIMENSIONS_TOO_LARGE"


@pytest.mark.asyncio
async def test_extraction_queue_returns_controlled_busy_error(monkeypatch):
    semaphore = asyncio.Semaphore(1)
    await semaphore.acquire()
    monkeypatch.setattr(extraction_routes, "extraction_semaphore", semaphore)
    monkeypatch.setattr(extraction_routes.settings, "ocr_queue_timeout_seconds", 0.001)

    with pytest.raises(ExtractionBusyException) as error:
        await extraction_routes.run_extraction_with_resource_limit(
            service=object(),
            file_path="/tmp/file.jpg",
            filename="file.jpg",
            mime_type="image/jpeg",
            file_size_bytes=1,
            request_id="request-id",
        )

    assert error.value.code == "OCR_BUSY"
