from enum import Enum


class ExtractionStatus(str, Enum):
    DONE = "DONE"
    NEEDS_REVIEW = "NEEDS_REVIEW"
    FAILED = "FAILED"
