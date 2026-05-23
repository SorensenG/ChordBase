from __future__ import annotations

from functools import lru_cache

from pydantic import Field, computed_field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "ChordPro Extractor API"
    app_version: str = "1.0.0"
    environment: str = "production"
    log_level: str = "INFO"

    max_upload_size_mb: int = 30
    max_pdf_pages: int = 10
    min_pdf_text_chars: int = 100
    min_pdf_text_tokens: int = 20

    ocr_dpi: int = 300
    ocr_languages: str = "por+eng"
    ocr_page_segmentation_mode: int = 6
    ocr_min_confidence: float = 0.30
    ocr_timeout_seconds: int = 45
    ocr_max_pixels: int = 4_000_000
    ocr_max_edge: int = 2_400
    ocr_max_source_pixels: int = 16_000_000
    ocr_max_concurrent_jobs: int = 1
    ocr_queue_timeout_seconds: int = 60

    review_confidence_threshold: float = 0.85
    allowed_mime_types_csv: str = Field(
        default="application/pdf,image/png,image/jpeg,image/webp,image/heic,image/heif,text/plain",
        description="Comma-separated list of accepted upload MIME types.",
    )
    allowed_extensions_csv: str = ".pdf,.png,.jpg,.jpeg,.webp,.heic,.heif,.txt"
    cors_allowed_origins_csv: str = "*"

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    @computed_field
    @property
    def max_upload_size_bytes(self) -> int:
        return self.max_upload_size_mb * 1024 * 1024

    @computed_field
    @property
    def allowed_mime_types(self) -> set[str]:
        return {
            value.strip().lower()
            for value in self.allowed_mime_types_csv.split(",")
            if value.strip()
        }

    @computed_field
    @property
    def allowed_extensions(self) -> set[str]:
        return {
            value.strip().lower()
            for value in self.allowed_extensions_csv.split(",")
            if value.strip()
        }

    @computed_field
    @property
    def cors_allowed_origins(self) -> list[str]:
        return [
            value.strip()
            for value in self.cors_allowed_origins_csv.split(",")
            if value.strip()
        ]


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
