"""Hybrid retrieval: keyword prefilter + vector search with definition boosts."""
from __future__ import annotations

import logging
import re
from pathlib import Path

from langchain.schema import Document
from langchain_community.vectorstores import FAISS

from text_utils import normalize_document_text

logger = logging.getLogger(__name__)

DEFAULT_K = 8
ENUMERATION_K = 15
SIMILARITY_THRESHOLD = 0.6

DEFINITION_PATTERNS = (
    r"什么是",
    r"是什么",
    r"什么叫",
    r"何谓",
    r"含义",
    r"定义",
    r"是指什么",
    r"如何定义",
    r"怎么理解",
    r"指的是什么",
)

ENUMERATION_PATTERNS = (
    r"有哪些",
    r"包括什么",
    r"包括哪些",
    r"因素",
    r"原因",
    r"列举",
    r"分别是什么",
)

TRIGGER_FACTOR_PATTERNS = (
    r"诱发因素",
    r"诱发",
    r"触发因素",
)

COMPARISON_PATTERNS = (
    r"对比",
    r"比较",
    r"区别",
    r"差异",
    r"不同点",
    r"相同点",
    r"异同",
    r"相比",
    r"对照",
    r"有何不同",
    r"有什么不同",
    r"哪个更",
    r"怎么判断",
    r"如何判断",
    r"如何区分",
    r"怎么区分",
    r"如何辨别",
    r"怎么辨别",
    r"vs",
    r"VS",
    r"versus",
)

_JUDGMENT_PATTERNS = (
    r"怎么判断",
    r"如何判断",
    r"如何区分",
    r"怎么区分",
    r"如何辨别",
    r"怎么辨别",
)

_HAISHIB_JUDGMENT_PATTERN = re.compile(
    r"(?:请)?(?:怎么|如何)(?:判断|区分|辨别)?(?:一下)?"
    r"(?:这个|该|此|一个)?(?:滑坡|坡面|坡体|区域|现场|现象|灾害)?(?:是)?(.+?)还是(.+?)$",
)
_HAISHIB_BINARY_PATTERN = re.compile(r"(.+?)还是(.+?)$")
_HAISHI_FALSE_POSITIVE = re.compile(r"我还是|或者是|还不如|仍然是|还是要")

CONCEPT_K = 6

_COMPARISON_CLEAN = re.compile(
    r"(?:请)?(?:简要)?(?:介绍|说明|解释|描述|分析|对比|比较|区分)(?:一下)?|"
    r"(?:请)?(?:怎么|如何)(?:判断|区分|辨别)(?:一下)?|"
    r"(?:怎么|如何)(?:区分|辨别|判断)(?:一下)?|"
    r"(?:有什么|有何|有哪些)?(?:区别|差异|不同点|相同点|异同|不同|相同)(?:吗|呢)?|"
    r"(?:之间|二者的?|两者的?|分别|各自|各有什么|哪个更|更)(?:好|优|差|强|弱)?|"
    r"相比|对照|versus|vs\.?",
    re.IGNORECASE,
)

_CONCEPT_NOISE = re.compile(
    r"[\uff1f?\u3002\uff0e,\uff0c;\uff1b:\"\'\u201c\u201d\u2018\u2019\uff08\uff09()\[\]\u3010\u3011]+",
)
SECTION_KEYWORDS = (
    "术语和定义",
    "术语",
    "定义",
    "名词",
    "3.1",
    "3.2",
    "3.3",
    "3.4",
    "3.5",
    "滑坡",
    "崩塌",
    "泥石流",
    "不稳定斜坡",
    "监测",
    "预警",
    "危险区",
    "稳定性",
    "防治",
    "勘察",
)

ENUMERATION_KEYWORDS = (
    "诱发",
    "触发",
    "因素",
    "降雨",
    "地震",
    "水库",
    "开挖",
    "采矿",
    "冻融",
    "侵蚀",
    "加载",
    "爆破",
    "植被",
)

_GBT_TERM_LINE = re.compile(
    r"(?:^|\n)(\d+\.\d+)\s*([^\n\r]+)\r?\n((?:[^\n\r]+(?:\r?\n(?!\s*\d+\.\d\s)[^\n\r]+)*))",
    re.MULTILINE,
)

_TRIGGERS_FILE = "landslide_triggers.txt"


def is_definition_question(question: str) -> bool:
    q = (question or "").strip()
    if not q:
        return False
    return any(re.search(p, q) for p in DEFINITION_PATTERNS)


def is_enumeration_question(question: str) -> bool:
    q = (question or "").strip()
    if not q:
        return False
    return any(re.search(p, q) for p in ENUMERATION_PATTERNS)


def is_trigger_factor_question(question: str) -> bool:
    q = (question or "").strip()
    if not q:
        return False
    return any(re.search(p, q) for p in TRIGGER_FACTOR_PATTERNS)




def is_comparison_question(question: str) -> bool:
    q = (question or "").strip()
    if not q:
        return False
    if any(re.search(p, q) for p in COMPARISON_PATTERNS):
        return True
    return _has_binary_haishi_choice(q)


def is_judgment_comparison_question(question: str) -> bool:
    q = (question or "").strip()
    if not q:
        return False
    if any(re.search(p, q) for p in _JUDGMENT_PATTERNS):
        return True
    if _has_binary_haishi_choice(q) and re.match(r"^(?:这是|该|此)", q):
        return True
    return False


def _normalize_concept_part(part: str) -> str:
    part = (part or "").strip()
    if not part:
        return ""
    part = _CONCEPT_NOISE.sub(" ", part).strip()
    part = re.sub(r"\s+", " ", part).strip()
    part = re.sub(r"^(?:到底是|究竟是|到底|究竟)\s*", "", part).strip()
    part = re.sub(
        r"^(?:这个坡面|该坡面|此坡面|这个区域|该区域|此区域)(?:是)?",
        "",
        part,
    ).strip()
    part = re.sub(r"^(?:一个)?(?:滑坡|坡体)(?:是)?", "", part).strip()
    part = re.sub(r"^(?:这是|那个|这个|该|此|此种)?(?:是)?", "", part).strip()
    part = re.sub(r"(?:怎么|如何)(?:区分|辨别|判断).*$", "", part).strip()
    if part in ("推移式", "牵引式", "旋转式", "平移式"):
        part = part + "滑坡"
    if len(part) >= 2:
        return part
    return ""


def _has_binary_haishi_choice(q: str) -> bool:
    if "还是" not in q:
        return False
    if _HAISHI_FALSE_POSITIVE.search(q):
        return False
    m = _HAISHIB_BINARY_PATTERN.search(q)
    if not m:
        return False
    left = _normalize_concept_part(m.group(1))
    right = _normalize_concept_part(m.group(2))
    return len(left) >= 2 and len(right) >= 2


def _unique_concepts(concepts: list[str]) -> list[str]:
    seen: set[str] = set()
    unique: list[str] = []
    for c in concepts:
        if c and c not in seen:
            seen.add(c)
            unique.append(c)
    return unique if len(unique) >= 2 else []


def extract_comparison_concepts(question: str) -> list[str]:
    q = (question or "").strip()
    if not q:
        return []
    q = re.sub(r"[？?。．;；]+$", "", q)

    m = _HAISHIB_JUDGMENT_PATTERN.search(q)
    if m:
        concepts = [_normalize_concept_part(m.group(1)), _normalize_concept_part(m.group(2))]
        result = _unique_concepts([c for c in concepts if c])
        if result:
            return result

    if "还是" in q and is_comparison_question(q):
        m = _HAISHIB_BINARY_PATTERN.search(q)
        if m:
            concepts = [_normalize_concept_part(m.group(1)), _normalize_concept_part(m.group(2))]
            result = _unique_concepts([c for c in concepts if c])
            if result:
                return result

    q = _COMPARISON_CLEAN.sub(" ", q).strip()
    parts = re.split(
        r"与|和|及|跟|、|以及|还有|还是|vs\.?|VS|versus",
        q,
        flags=re.IGNORECASE,
    )
    concepts: list[str] = []
    for part in parts:
        normalized = _normalize_concept_part(part)
        if normalized:
            concepts.append(normalized)
    return _unique_concepts(concepts)

def extract_query_term(question: str) -> str:
    q = (question or "").strip()
    if not q:
        return ""
    q = re.sub(r"[？?。．,，;；:\"\'\u201c\u201d\u2018\u2019（）()\[\]【】]", " ", q)
    for pat in (
        r"^(?:请)?(?:简要)?(?:介绍|说明|解释|描述)?",
        r"(?:什么是|什么叫|何谓|如何定义|怎么理解|的含义|的定义|是指什么|指的是什么|是指)",
        r"(?:这个|该|此)?术语(?:的)?(?:含义|定义)?",
        r"(?:用)?中文(?:回答)?",
        r"(?:请)?回答",
    ):
        q = re.sub(pat, " ", q)
    q = re.sub(r"\s+", " ", q).strip()
    if len(q) > 40:
        q = q[:40].strip()
    return q


def _term_in_line(term: str, line: str) -> bool:
    if not term or not line:
        return False
    if term in line:
        return True
    compact = re.sub(r"\s+", "", line)
    compact_term = re.sub(r"\s+", "", term)
    return compact_term in compact


def extract_definition_fallback(knowledge_dir: Path, term: str) -> list[Document]:
    """Scan knowledge/*.txt for GB/T-style numbered term blocks (e.g. 3.1)."""
    term = (term or "").strip()
    if not term or not knowledge_dir.is_dir():
        return []

    found: list[Document] = []
    for path in sorted(knowledge_dir.glob("*.txt")):
        try:
            raw = path.read_text(encoding="utf-8-sig")
        except OSError:
            continue
        text = normalize_document_text(raw)
        for sec_no, title_line, body in _GBT_TERM_LINE.findall(text):
            block = f"{sec_no} {title_line.strip()}\n{body.strip()}".strip()
            if not _term_in_line(term, title_line) and not _term_in_line(term, body[:200]):
                continue
            if len(body.strip()) < 4:
                continue
            found.append(
                Document(
                    page_content=block,
                    metadata={
                        "source": path.name,
                        "file": path.name,
                        "section": sec_no,
                        "retrieval": "definition_fallback",
                    },
                )
            )
            break
        if found:
            break

    if not found:
        for path in sorted(knowledge_dir.glob("*.txt")):
            try:
                raw = path.read_text(encoding="utf-8-sig")
            except OSError:
                continue
            text = normalize_document_text(raw)
            idx = text.find(term)
            if idx < 0:
                compact_text = re.sub(r"\s+", "", text)
                compact_term = re.sub(r"\s+", "", term)
                idx = compact_text.find(compact_term) if compact_term else -1
                if idx < 0:
                    continue
            start = max(0, idx - 120)
            end = min(len(text), idx + 480)
            snippet = text[start:end].strip()
            found.append(
                Document(
                    page_content=snippet,
                    metadata={
                        "source": path.name,
                        "file": path.name,
                        "retrieval": "definition_fallback_context",
                    },
                )
            )
            break

    return found[:3]


def extract_enumeration_fallback(knowledge_dir: Path, question: str) -> list[Document]:
    """If question mentions trigger factors, return landslide_triggers.txt content."""
    q = (question or "").strip()
    if not q or not knowledge_dir.is_dir():
        return []
    if not any(k in q for k in ("诱发", "因素", "原因")):
        return []
    path = knowledge_dir / "landslide_triggers.txt"
    if not path.is_file():
        return []
    try:
        raw = path.read_text(encoding="utf-8-sig")
    except OSError:
        return []
    content = normalize_document_text(raw).strip()
    if not content:
        return []
    return [
        Document(
            page_content=content,
            metadata={
                "source": path.name,
                "file": path.name,
                "retrieval": "enumeration_fallback",
            },
        )
    ]


def extract_triggers_cache_fallback(knowledge_dir: Path) -> list[Document]:
    """Load merged trigger-factor checklist from landslide_triggers.txt."""
    if not knowledge_dir.is_dir():
        return []
    path = knowledge_dir / _TRIGGERS_FILE
    if not path.is_file():
        return []
    try:
        raw = path.read_text(encoding="utf-8-sig")
    except OSError:
        return []
    text = normalize_document_text(raw).strip()
    if not text:
        return []
    return [
        Document(
            page_content=text,
            metadata={
                "source": path.name,
                "file": path.name,
                "retrieval": "triggers_cache_fallback",
                "score": 1.0,
            },
        )
    ]

def _keywords_for_query(
    query: str,
    definition_mode: bool,
    enumeration_mode: bool = False,
) -> list[str]:
    keywords: list[str] = []
    term = extract_query_term(query)
    if term:
        keywords.append(term)
        if len(term) >= 2:
            keywords.append(term[:2])
    q = query.strip()
    for kw in SECTION_KEYWORDS:
        if kw in q and kw not in keywords:
            keywords.append(kw)
    if definition_mode:
        for kw in ("术语", "定义", "3.1", "3.2", "是指", "指"):
            if kw not in keywords:
                keywords.append(kw)
    if enumeration_mode:
        for kw in ENUMERATION_KEYWORDS:
            if kw not in keywords:
                keywords.append(kw)
    return keywords


def keyword_prefilter(vectordb: FAISS, keywords: list[str]) -> set[str]:
    if not keywords:
        return set()
    matched: set[str] = set()
    store = getattr(vectordb, "docstore", None)
    if store is None:
        return matched
    doc_dict = getattr(store, "_dict", None) or {}
    for doc_id, doc in doc_dict.items():
        content = doc.page_content or ""
        meta = " ".join(str(v) for v in (doc.metadata or {}).values())
        haystack = f"{content}\n{meta}"
        if any(kw and kw in haystack for kw in keywords):
            matched.add(str(doc_id))
    return matched


def _compute_boost(
    doc: Document,
    doc_id: str,
    pref_ids: set[str],
    definition_mode: bool,
    term: str,
    enumeration_mode: bool = False,
) -> float:
    boost = 0.0
    content = doc.page_content or ""
    if doc_id and doc_id in pref_ids:
        boost += 0.12
    for kw in SECTION_KEYWORDS:
        if kw in content:
            boost += 0.02
            break
    if definition_mode:
        boost += 0.08
        if term and _term_in_line(term, content):
            boost += 0.18
        if re.search(r"\d+\.\d+", content) and re.search(r"(指|是指|定义为|定义为：)", content):
            boost += 0.1
    if enumeration_mode:
        for kw in ("诱发", "因素", "原因"):
            if kw in content:
                boost += 0.06
                break
    return boost

def hybrid_retrieve(
    vectordb: FAISS,
    query: str,
    k: int = DEFAULT_K,
    definition_mode: bool = False,
    enumeration_mode: bool = False,
) -> list[Document]:
    keywords = _keywords_for_query(query, definition_mode, enumeration_mode)
    pref_ids = keyword_prefilter(vectordb, keywords)
    term = extract_query_term(query)

    if enumeration_mode:
        k = ENUMERATION_K
    fetch_k = max(k * 4, 32) if not enumeration_mode else max(k * 4, 60)

    pairs = vectordb.similarity_search_with_score(query, k=fetch_k)

    ranked: list[tuple[float, Document, float]] = []
    for doc, distance in pairs:
        doc_id = ""
        store = getattr(vectordb, "docstore", None)
        if store is not None:
            for _idx, did in (getattr(vectordb, "index_to_docstore_id", None) or {}).items():
                try:
                    if store.search(str(did)) == doc:
                        doc_id = str(did)
                        break
                except Exception:
                    continue
        boost = _compute_boost(
            doc, doc_id, pref_ids, definition_mode, term, enumeration_mode
        )
        adjusted = float(distance) - boost
        similarity = 1.0 / (1.0 + float(distance))
        meta = dict(doc.metadata or {})
        meta["distance"] = float(distance)
        meta["boost"] = boost
        meta["score"] = similarity + boost
        meta["similarity"] = similarity
        out_doc = Document(page_content=doc.page_content, metadata=meta)
        source_name = meta.get("source", meta.get("file", "unknown"))
        logger.info(
            "retrieve file=%s distance=%.4f boost=%.4f similarity=%.4f",
            source_name,
            float(distance),
            boost,
            similarity,
        )
        ranked.append((adjusted, out_doc, similarity))

    ranked.sort(key=lambda x: x[0])

    if enumeration_mode:
        filtered = [item[1] for item in ranked if item[2] > SIMILARITY_THRESHOLD]
        logger.info(
            "enumeration_mode kept %d docs with similarity > %.2f",
            len(filtered),
            SIMILARITY_THRESHOLD,
        )
        return filtered

    return [item[1] for item in ranked[:k]]


def build_context(docs: list[Document], max_chars: int = 12000) -> str:
    parts: list[str] = []
    total = 0
    for i, doc in enumerate(docs, start=1):
        meta = doc.metadata or {}
        source = meta.get("source", meta.get("file", f"doc{i}"))
        block = f"[{i}] ({source})\n{doc.page_content or ''}".strip()
        if total + len(block) > max_chars:
            remaining = max_chars - total
            if remaining > 200:
                parts.append(block[:remaining])
            break
        parts.append(block)
        total += len(block) + 2
    return "\n\n".join(parts)

def retrieve_for_concept(
    vectordb: FAISS,
    knowledge_dir: Path,
    concept: str,
    k: int = CONCEPT_K,
) -> list[Document]:
    concept = (concept or "").strip()
    if not concept:
        return []
    fallback_docs = extract_definition_fallback(knowledge_dir, concept)
    query = f"什么是{concept}"
    vector_docs = hybrid_retrieve(
        vectordb,
        query,
        k=k,
        definition_mode=True,
    )
    seen: set[str] = set()
    merged: list[Document] = []
    for doc in fallback_docs + vector_docs:
        key = (doc.page_content or "")[:200]
        if key in seen:
            continue
        seen.add(key)
        merged.append(doc)
    return merged[:k]


def build_comparison_context(
    concept_docs: dict[str, list[Document]],
    max_chars: int = 12000,
) -> str:
    parts: list[str] = []
    total = 0
    for i, (concept, docs) in enumerate(concept_docs.items(), start=1):
        section_header = f"【概念{i}：{concept}】"
        section_body = build_context(docs, max_chars=max_chars)
        section = f"{section_header}\n{section_body}".strip()
        if total + len(section) > max_chars:
            remaining = max_chars - total
            if remaining > 200:
                parts.append(section[:remaining])
            break
        parts.append(section)
        total += len(section) + 2
    return "\n\n".join(parts)
