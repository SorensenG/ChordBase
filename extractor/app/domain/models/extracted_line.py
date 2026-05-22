from __future__ import annotations

from pydantic import BaseModel, Field

from app.domain.models.extracted_token import ExtractedToken


class ExtractedLine(BaseModel):
    page_number: int = Field(default=1, ge=1)
    y: float
    text: str
    tokens: list[ExtractedToken]
