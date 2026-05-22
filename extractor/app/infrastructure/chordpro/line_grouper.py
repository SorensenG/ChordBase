from __future__ import annotations

from collections import defaultdict
from typing import Optional

from app.domain.models.extracted_line import ExtractedLine
from app.domain.models.extracted_token import ExtractedToken


def group_tokens_by_lines(
    tokens: list[ExtractedToken],
    y_tolerance: float = 8,
) -> list[ExtractedLine]:
    grouped_by_page: dict[int, list[dict]] = defaultdict(list)

    sorted_tokens = sorted(tokens, key=lambda token: (token.page_number, token.y, token.x))

    for token in sorted_tokens:
        page_lines = grouped_by_page[token.page_number]
        matched_line = _find_matching_line(page_lines, token, y_tolerance)

        if matched_line:
            matched_line["tokens"].append(token)
            matched_line["y"] = sum(t.y for t in matched_line["tokens"]) / len(
                matched_line["tokens"]
            )
        else:
            page_lines.append({"y": token.y, "tokens": [token]})

    result: list[ExtractedLine] = []

    for page_number in sorted(grouped_by_page):
        for line in sorted(grouped_by_page[page_number], key=lambda item: item["y"]):
            line_tokens = sorted(line["tokens"], key=lambda token: token.x)
            line_text = " ".join(token.text for token in line_tokens)

            result.append(
                ExtractedLine(
                    page_number=page_number,
                    y=line["y"],
                    text=line_text,
                    tokens=line_tokens,
                )
            )

    return result


def _find_matching_line(
    grouped_lines: list[dict],
    token: ExtractedToken,
    y_tolerance: float,
) -> Optional[dict]:
    dynamic_tolerance = max(y_tolerance, token.height * 0.55)

    for line in grouped_lines:
        if abs(line["y"] - token.y) <= dynamic_tolerance:
            return line

    return None
