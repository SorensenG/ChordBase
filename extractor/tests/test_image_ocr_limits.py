import asyncio
from types import SimpleNamespace

import fitz
import pytest
from PIL import Image

from app.api.routes import extraction_routes
from app.domain.exceptions.extraction_exceptions import (
    ExtractionBusyException,
    ExtractionException,
    UploadValidationException,
)
from app.infrastructure.extractors.image_ocr_extractor import _load_image
from app.infrastructure.extractors.pdf_ocr_extractor import _validate_render_budget
from app.infrastructure.extractors.text_file_extractor import extract_tokens_from_text_file


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


def test_upload_metadata_accepts_pdf_and_txt_documents():
    extraction_routes.validate_upload_metadata(
        SimpleNamespace(filename="song.pdf", content_type="application/pdf")
    )
    extraction_routes.validate_upload_metadata(
        SimpleNamespace(filename="song.txt", content_type="text/plain")
    )


def test_upload_metadata_rejects_direct_image_import():
    with pytest.raises(UploadValidationException) as error:
        extraction_routes.validate_upload_metadata(
            SimpleNamespace(filename="song.jpg", content_type="image/jpeg")
        )

    assert error.value.code == "UNSUPPORTED_EXTENSION"
    assert "PDF ou TXT" in error.value.message


@pytest.mark.asyncio
async def test_extraction_queue_returns_controlled_busy_error(monkeypatch):
    semaphore = asyncio.Semaphore(1)
    await semaphore.acquire()
    monkeypatch.setattr(extraction_routes, "extraction_semaphore", semaphore)
    monkeypatch.setattr(extraction_routes, "admission_semaphore", asyncio.Semaphore(2))
    monkeypatch.setattr(extraction_routes.settings, "ocr_queue_timeout_seconds", 0.001)

    with pytest.raises(ExtractionBusyException) as error:
        await extraction_routes.run_extraction_with_resource_limit(
            service=object(),
            file_path="/tmp/file.pdf",
            filename="file.pdf",
            mime_type="application/pdf",
            file_size_bytes=1,
            request_id="request-id",
        )

    assert error.value.code == "OCR_BUSY"


@pytest.mark.asyncio
async def test_extraction_admission_rejects_requests_when_pending_limit_is_full(monkeypatch):
    semaphore = asyncio.Semaphore(1)
    await semaphore.acquire()
    monkeypatch.setattr(extraction_routes, "admission_semaphore", semaphore)
    monkeypatch.setattr(extraction_routes.settings, "ocr_admission_timeout_seconds", 0.001)

    with pytest.raises(ExtractionBusyException):
        await extraction_routes.acquire_extraction_slot("request-id", "text/plain")


def test_text_extraction_rejects_token_amplification(tmp_path, monkeypatch):
    monkeypatch.setattr("app.infrastructure.extractors.text_file_extractor.settings.text_max_tokens", 2)
    text_file = tmp_path / "many.txt"
    text_file.write_text("C G Am F", encoding="utf-8")

    with pytest.raises(ExtractionException) as error:
        extract_tokens_from_text_file(str(text_file))

    assert error.value.code == "TEXT_TOKEN_LIMIT_EXCEEDED"


def test_pdf_render_budget_is_checked_before_rasterization(tmp_path, monkeypatch):
    pdf_file = tmp_path / "huge-page.pdf"
    document = fitz.open()
    document.new_page(width=1000, height=1000)
    document.save(pdf_file)
    document.close()
    monkeypatch.setattr(
        "app.infrastructure.extractors.pdf_ocr_extractor.settings.pdf_max_rendered_pixels_per_page",
        100,
    )

    with pytest.raises(ExtractionException) as error:
        _validate_render_budget(str(pdf_file))

    assert error.value.code == "PDF_RENDER_BUDGET_EXCEEDED"
