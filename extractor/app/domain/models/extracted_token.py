from __future__ import annotations

from typing import Optional

from pydantic import BaseModel, Field


class ExtractedToken(BaseModel):
    text: str
    x: float
    y: float
    width: float
    height: float
    page_number: int = Field(default=1, ge=1)
    confidence: Optional[float] = Field(default=None, ge=0.0, le=1.0)
