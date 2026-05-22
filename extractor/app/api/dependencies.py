from functools import lru_cache

from app.application.services.chordpro_extraction_service import (
    ChordproExtractionService,
)


@lru_cache
def get_chordpro_extraction_service() -> ChordproExtractionService:
    return ChordproExtractionService()
