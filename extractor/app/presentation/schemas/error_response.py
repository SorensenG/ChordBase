from __future__ import annotations

from typing import Any, Optional, Union

from pydantic import BaseModel, Field


class ErrorResponse(BaseModel):
    request_id: str = Field(alias="requestId")
    code: str
    message: str
    details: Optional[Union[dict[str, Any], list[Any]]] = None
