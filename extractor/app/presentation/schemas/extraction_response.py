from __future__ import annotations

from typing import Optional

from pydantic import BaseModel, Field

from app.domain.models.extraction_result import ExtractionResult


class ExtractionMetadataResponse(BaseModel):
    filename: Optional[str]
    mime_type: str = Field(alias="mimeType")
    file_size_bytes: int = Field(alias="fileSizeBytes")
    pages_processed: Optional[int] = Field(alias="pagesProcessed")
    token_count: int = Field(alias="tokenCount")
    line_count: int = Field(alias="lineCount")


class ExtractionResponse(BaseModel):
    request_id: str = Field(alias="requestId")
    status: str
    source_type: str = Field(alias="sourceType")
    chordpro: Optional[str] = Field(alias="chordPro")
    confidence: float
    warnings: list[str]
    metadata: ExtractionMetadataResponse
    processing_time_ms: int = Field(alias="processingTimeMs")

    @classmethod
    def from_result(
        cls,
        result: ExtractionResult,
        request_id: str,
    ) -> ExtractionResponse:
        return cls(
            requestId=request_id,
            status=result.status.value,
            sourceType=result.source_type.value,
            chordPro=result.chordpro,
            confidence=result.confidence,
            warnings=result.warnings,
            metadata=ExtractionMetadataResponse(
                filename=result.metadata.filename,
                mimeType=result.metadata.mime_type,
                fileSizeBytes=result.metadata.file_size_bytes,
                pagesProcessed=result.metadata.pages_processed,
                tokenCount=result.metadata.token_count,
                lineCount=result.metadata.line_count,
            ),
            processingTimeMs=result.processing_time_ms,
        )
