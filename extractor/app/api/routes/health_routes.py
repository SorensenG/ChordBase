import importlib.util
import shutil

from fastapi import APIRouter

from app.config import settings
from app.presentation.schemas.health_response import HealthResponse

router = APIRouter()


@router.get("/health", response_model=HealthResponse)
def health_check():
    return HealthResponse(
        status="ok",
        service=settings.app_name,
        version=settings.app_version,
        checks={},
    )


@router.get("/health/ready", response_model=HealthResponse)
def readiness_check():
    checks = {
        "tesseract": shutil.which("tesseract") is not None,
        "pdftoppm": shutil.which("pdftoppm") is not None,
        "pymupdf": importlib.util.find_spec("fitz") is not None,
        "pytesseract": importlib.util.find_spec("pytesseract") is not None,
    }

    status = "ok" if all(checks.values()) else "degraded"

    return HealthResponse(
        status=status,
        service=settings.app_name,
        version=settings.app_version,
        checks=checks,
    )
