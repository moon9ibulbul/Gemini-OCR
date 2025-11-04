"""Pororo OCR integration helpers for AstralOCR."""

from __future__ import annotations

import math
from typing import Dict, Iterable, List, Optional

from PororoOcrWrapper import PororoOcr

_ocr_instance: Optional[PororoOcr] = None


def _ensure_instance(dilatation_factor: float = 2.0) -> PororoOcr:
    global _ocr_instance
    if _ocr_instance is None:
        _ocr_instance = PororoOcr(dilatation_factor=dilatation_factor)
    return _ocr_instance


def run_ocr(image_path: str, dilatation_factor: float = 2.0) -> str:
    """Execute OCR and return formatted text for the Android client."""

    ocr = _ensure_instance(dilatation_factor)
    ocr.run_ocr(image_path, dilatation_factor=dilatation_factor)
    result = ocr.get_ocr_result() or {}
    formatted = _format_result(result)
    return formatted.strip()


def _format_result(result: Dict) -> str:
    entries: List[Dict] = list(result.get("bounding_poly") or [])
    if not entries:
        description = result.get("description") or []
        if not description:
            return ""
        text = " ".join(part.strip() for part in description if part.strip())
        return f"() : {text}" if text else ""

    stats = _collect_stats(entries)
    lines: List[str] = []
    for entry in entries:
        line = _format_entry(entry, stats)
        if line:
            lines.append(line)
    return "\n".join(lines)


def _collect_stats(entries: Iterable[Dict]) -> Dict[str, float]:
    min_x = math.inf
    max_x = -math.inf
    min_y = math.inf
    max_y = -math.inf
    max_width = 0.0
    max_height = 0.0

    for entry in entries:
        vertices = entry.get("vertices") or []
        xs = [float(v.get("x", 0.0)) for v in vertices]
        ys = [float(v.get("y", 0.0)) for v in vertices]
        if not xs or not ys:
            continue
        width = max(xs) - min(xs)
        height = max(ys) - min(ys)

        min_x = min(min_x, min(xs))
        max_x = max(max_x, max(xs))
        min_y = min(min_y, min(ys))
        max_y = max(max_y, max(ys))
        max_width = max(max_width, width)
        max_height = max(max_height, height)

    width_span = max(max_x - min_x, 1.0)
    height_span = max(max_y - min_y, 1.0)
    max_width = max(max_width, 1.0)
    max_height = max(max_height, 1.0)

    return {
        "min_x": min_x if min_x != math.inf else 0.0,
        "max_x": max_x if max_x != -math.inf else 0.0,
        "min_y": min_y if min_y != math.inf else 0.0,
        "max_y": max_y if max_y != -math.inf else 0.0,
        "width_span": width_span,
        "height_span": height_span,
        "max_width": max_width,
        "max_height": max_height,
    }


def _format_entry(entry: Dict, stats: Dict[str, float]) -> Optional[str]:
    text = str(entry.get("description", "")).strip()
    if not text:
        return None

    vertices = entry.get("vertices") or []
    xs = [float(v.get("x", 0.0)) for v in vertices]
    ys = [float(v.get("y", 0.0)) for v in vertices]

    if not xs or not ys:
        return f"() : {text}"

    min_x, max_x = min(xs), max(xs)
    min_y, max_y = min(ys), max(ys)
    width = max_x - min_x
    height = max_y - min_y
    aspect = width / max(height, 1.0)
    area_ratio = (width * height) / (stats["width_span"] * stats["height_span"])
    rel_width = width / stats["max_width"]
    rel_height = height / stats["max_height"]
    center_x = (min_x + max_x) / 2.0
    edge_margin = 0.12 * stats["width_span"]

    word_count = len([w for w in text.replace("\n", " ").split(" ") if w])
    char_count = len(text)
    uppercase_ratio = (
        sum(1 for c in text if c.isalpha() and c.upper() == c) / max(char_count, 1)
    )

    prefix = "() :"

    if width <= 0.0 or height <= 0.0:
        prefix = "() :"
    elif aspect < 0.7 or char_count <= 4 or uppercase_ratio > 0.6:
        prefix = "// :"
    elif area_ratio > 0.4 and (center_x - stats["min_x"] < edge_margin or stats["max_x"] - center_x < edge_margin):
        prefix = "'' :"
    elif word_count >= 10 or char_count > 40 or rel_width > 0.75:
        prefix = "[] :"
    elif (min_y - stats["min_y"]) < 0.15 * stats["height_span"] and rel_width > 0.4:
        prefix = "[] :"
    elif rel_height > 0.6 and rel_width < 0.45:
        prefix = "'' :"

    return f"{prefix} {text}"


__all__ = ["run_ocr"]
