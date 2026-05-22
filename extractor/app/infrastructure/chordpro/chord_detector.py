import re

CHORD_REGEX = re.compile(
    r"^[A-G](#|b)?"
    r"(m|maj|min|dim|aug|sus|add)?"
    r"[0-9M°º+\-()#/b]*"
    r"(/[A-G](#|b)?)?$"
)


def is_chord(text: str) -> bool:
    value = text.strip()

    if not value:
        return False

    return bool(CHORD_REGEX.fullmatch(value))
