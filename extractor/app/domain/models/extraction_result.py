from __future__ import annotations

from typing import Optional

from pydantic import BaseModel, Field

from app.domain.enums.extraction_status import ExtractionStatus
from app.domain.enums.source_type import SourceType


class ExtractionMetadata(BaseModel):
    filename: Optional[str] = None
    mime_type: str
    file_size_bytes: int
    pages_processed: Optional[int] = None
    token_count: int
    line_count: int


class ExtractionResult(BaseModel):
    status: ExtractionStatus
    source_type: SourceType
    chordpro: Optional[str]
    confidence: float = Field(ge=0.0, le=1.0)
    warnings: list[str]
    metadata: ExtractionMetadata
    processing_time_ms: int
