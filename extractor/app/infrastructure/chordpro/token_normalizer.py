from __future__ import annotations

from app.domain.models.extracted_token import ExtractedToken


def collapse_overprinted_tokens(
    tokens: list[ExtractedToken],
    x_tolerance: float = 2.0,
    y_tolerance: float = 2.0,
) -> list[ExtractedToken]:
    collapsed: list[ExtractedToken] = []

    for token in sorted(tokens, key=lambda item: (item.page_number, item.y, item.x)):
        if collapsed and _is_overprinted_duplicate(
            previous=collapsed[-1],
            current=token,
            x_tolerance=x_tolerance,
            y_tolerance=y_tolerance,
        ):
            continue

        collapsed.append(token)

    return sorted(collapsed, key=lambda item: item.x)


def tokens_to_text(tokens: list[ExtractedToken]) -> str:
    return " ".join(token.text for token in tokens)


def _is_overprinted_duplicate(
    previous: ExtractedToken,
    current: ExtractedToken,
    x_tolerance: float,
    y_tolerance: float,
) -> bool:
    return (
        previous.text == current.text
        and previous.page_number == current.page_number
        and abs(previous.x - current.x) <= x_tolerance
        and abs(previous.y - current.y) <= y_tolerance
    )
