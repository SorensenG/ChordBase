from __future__ import annotations

import asyncio
import logging
from pathlib import Path
from time import perf_counter
from typing import Optional

from fastapi import APIRouter, Depends, File, Request, UploadFile

from app.api.dependencies import get_chordpro_extraction_service
from app.application.services.chordpro_extraction_service import (
    ChordproExtractionService,
)
from app.config import settings
from app.domain.exceptions.extraction_exceptions import (
    ExtractionBusyException,
    UploadValidationException,
)
from app.domain.models.extraction_result import ExtractionResult
from app.infrastructure.files.temporary_file_handler import (
    remove_temp_file,
    save_upload_to_temp_file,
)
from app.presentation.schemas.extraction_response import ExtractionResponse

router = APIRouter()
logger = logging.getLogger(__name__)
extraction_semaphore = asyncio.Semaphore(settings.ocr_max_concurrent_jobs)


@router.post("/chordpro", response_model=ExtractionResponse)
async def extract_chordpro(
    request: Request,
    file: UploadFile = File(...),
    service: ChordproExtractionService = Depends(get_chordpro_extraction_service),  # noqa: B008
):
    request_id = request.state.request_id
    validate_upload_metadata(file)

    temp_path: Optional[str] = None

    try:
        temp_file = await save_upload_to_temp_file(
            file=file,
            max_size_bytes=settings.max_upload_size_bytes,
        )
        temp_path = temp_file.path

        result = await run_extraction_with_resource_limit(
            service=service,
            file_path=temp_file.path,
            filename=file.filename,
            mime_type=file.content_type,
            file_size_bytes=temp_file.size_bytes,
            request_id=request_id,
        )

        logger.info(
            "Extraction finished",
            extra={
                "request_id": request_id,
                "upload_filename": file.filename,
                "source_type": result.source_type.value,
                "status": result.status.value,
                "confidence": result.confidence,
                "processing_time_ms": result.processing_time_ms,
            },
        )

        return ExtractionResponse.from_result(result, request_id=request_id)
    finally:
        if temp_path:
            remove_temp_file(temp_path)


async def run_extraction_with_resource_limit(
    service: ChordproExtractionService,
    file_path: str,
    filename: Optional[str],
    mime_type: Optional[str],
    file_size_bytes: int,
    request_id: str,
) -> ExtractionResult:
    if (mime_type or "").lower() == "text/plain":
        return await asyncio.to_thread(
            service.extract,
            file_path,
            filename,
            mime_type,
            file_size_bytes,
            request_id,
        )

    waiting_started = perf_counter()
    try:
        await asyncio.wait_for(
            extraction_semaphore.acquire(),
            timeout=settings.ocr_queue_timeout_seconds,
        )
    except asyncio.TimeoutError as exception:
        logger.warning(
            "Extraction queue timed out",
            extra={"request_id": request_id, "mime_type": mime_type},
        )
        raise ExtractionBusyException() from exception

    wait_ms = round((perf_counter() - waiting_started) * 1000)
    logger.info(
        "Extraction slot acquired",
        extra={"request_id": request_id, "mime_type": mime_type, "queue_wait_ms": wait_ms},
    )
    try:
        return await asyncio.to_thread(
            service.extract,
            file_path,
            filename,
            mime_type,
            file_size_bytes,
            request_id,
        )
    finally:
        extraction_semaphore.release()


def validate_upload_metadata(file: UploadFile) -> None:
    suffix = Path(file.filename or "").suffix.lower()
    content_type = (file.content_type or "").lower()

    if not file.filename:
        raise UploadValidationException(
            message="Arquivo sem nome. Envie um PDF, imagem ou TXT válido.",
            code="MISSING_FILENAME",
        )

    if suffix not in settings.allowed_extensions:
        raise UploadValidationException(
            message=(
                "Extensão não suportada. "
                "Envie PDF, PNG, JPG, JPEG, WEBP, HEIC, HEIF ou TXT."
            ),
            code="UNSUPPORTED_EXTENSION",
            details={"extension": suffix},
        )

    if content_type and content_type != "application/octet-stream":
        if content_type not in settings.allowed_mime_types:
            raise UploadValidationException(
                message="Tipo de arquivo não suportado.",
                code="UNSUPPORTED_MIME_TYPE",
                details={"mimeType": content_type},
            )
