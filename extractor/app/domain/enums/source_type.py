from enum import Enum


class SourceType(str, Enum):
    TEXT_FILE = "TEXT_FILE"
    TEXT_PDF = "TEXT_PDF"
    OCR_PDF = "OCR_PDF"
    OCR_IMAGE = "OCR_IMAGE"
    UNKNOWN = "UNKNOWN"
