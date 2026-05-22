from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from tempfile import NamedTemporaryFile
from typing import Optional

from fastapi import UploadFile

from app.domain.exceptions.extraction_exceptions import UploadValidationException


@dataclass(frozen=True)
class TemporaryUpload:
    path: str
    size_bytes: int


async def save_upload_to_temp_file(
    file: UploadFile,
    max_size_bytes: int,
    chunk_size: int = 1024 * 1024,
) -> TemporaryUpload:
    suffix = Path(file.filename or "").suffix.lower()
    size_bytes = 0
    temp_path: Optional[str] = None

    try:
        with NamedTemporaryFile(delete=False, suffix=suffix) as temp:
            temp_path = temp.name

            while chunk := await file.read(chunk_size):
                size_bytes += len(chunk)
                if size_bytes > max_size_bytes:
                    raise UploadValidationException(
                        message="Arquivo excede o tamanho máximo permitido.",
                        code="UPLOAD_TOO_LARGE",
                        details={"maxSizeBytes": max_size_bytes},
                    )
                temp.write(chunk)

        if size_bytes == 0:
            raise UploadValidationException(
                message="Arquivo vazio.",
                code="EMPTY_UPLOAD",
            )

        return TemporaryUpload(path=temp_path, size_bytes=size_bytes)
    except Exception:
        if temp_path:
            remove_temp_file(temp_path)
        raise


def remove_temp_file(file_path: str) -> None:
    Path(file_path).unlink(missing_ok=True)
