"""文本清洗：修复 PDF 提取时汉字被空格拆开的问题。"""
from __future__ import annotations

import re


def normalize_document_text(text: str) -> str:
    if not text:
        return ""
    # 汉字之间的多余空格：灾 害 -> 灾害
    text = re.sub(r"(?<=[\u4e00-\u9fff])\s+(?=[\u4e00-\u9fff])", "", text)
    # 数字之间的多余空格：2  000 -> 2000
    text = re.sub(r"(?<=\d)\s+(?=\d)", "", text)
    # 合并连续空白
    text = re.sub(r"[ \t\u3000]{2,}", " ", text)
    return text.strip()
