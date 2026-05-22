from __future__ import annotations

from typing import Any, Optional


class ExtractionException(Exception):
    def __init__(
        self,
        message: str,
        code: str = "EXTRACTION_ERROR",
        details: Optional[dict[str, Any]] = None,
    ):
        self.message = message
        self.code = code
        self.details = details
        super().__init__(message)


class UploadValidationException(ExtractionException):
    def __init__(
        self,
        message: str,
        code: str = "UPLOAD_VALIDATION_ERROR",
        details: Optional[dict[str, Any]] = None,
    ):
        super().__init__(message=message, code=code, details=details)


class UnsupportedFileException(UploadValidationException):
    def __init__(self, message: str = "Formato de arquivo não suportado."):
        super().__init__(message=message, code="UNSUPPORTED_FILE")


class DependencyUnavailableException(ExtractionException):
    def __init__(self, message: str, dependency: str):
        super().__init__(
            message=message,
            code="DEPENDENCY_UNAVAILABLE",
            details={"dependency": dependency},
        )
