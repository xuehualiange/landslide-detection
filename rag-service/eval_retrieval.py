"""Offline retrieval eval: hybrid_retrieve only, no LLM calls."""
from __future__ import annotations

import json
import os
import sys
from collections import defaultdict
from pathlib import Path

from langchain_community.embeddings import HuggingFaceEmbeddings
from langchain_community.vectorstores import FAISS

from retrieval import DEFAULT_K, hybrid_retrieve

BASE_DIR = Path(__file__).resolve().parent
QUESTIONS_FILE = BASE_DIR / "eval_questions.json"
FAISS_DIR = Path(os.getenv("FAISS_INDEX_DIR", str(BASE_DIR / "faiss_index")))
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "sentence-transformers/all-MiniLM-L6-v2")


def load_questions() -> list[dict]:
    data = json.loads(QUESTIONS_FILE.read_text(encoding="utf-8"))
    if not isinstance(data, list):
        raise ValueError("eval_questions.json must be a JSON array")
    return data


def load_vectordb() -> FAISS:
    index_file = FAISS_DIR / "index.faiss"
    if not index_file.exists():
        print(f"FAIL: FAISS index missing: {FAISS_DIR}", file=sys.stderr)
        sys.exit(1)
    embeddings = HuggingFaceEmbeddings(model_name=EMBEDDING_MODEL)
    return FAISS.load_local(
        str(FAISS_DIR),
        embeddings,
        allow_dangerous_deserialization=True,
    )


def doc_source_name(doc) -> str:
    meta = doc.metadata or {}
    return str(meta.get("source", meta.get("file", "unknown")))


def retrieve_top_sources(vectordb: FAISS, question: str, k: int = DEFAULT_K) -> list[str]:
    """Default retrieval path: k=8, no definition/enumeration boosts."""
    docs = hybrid_retrieve(
        vectordb,
        question,
        k=k,
        definition_mode=False,
        enumeration_mode=False,
    )
    return [doc_source_name(doc) for doc in docs]


def expect_hit(sources: list[str], expect: str) -> bool:
    fragment = (expect or "").strip()
    if not fragment:
        return False
    return any(fragment in name for name in sources)


def main() -> None:
    questions = load_questions()
    vectordb = load_vectordb()

    print("=== RAG retrieval eval (no LLM) ===")
    print(f"Questions: {len(questions)} | k={DEFAULT_K} | mode=default\n")

    type_total: dict[str, int] = defaultdict(int)
    type_hits: dict[str, int] = defaultdict(int)
    total_hits = 0

    for item in questions:
        qid = item.get("id", "?")
        qtype = str(item.get("type", "未知"))
        question = str(item.get("q", "")).strip()
        expect = str(item.get("expect", "")).strip()

        sources = retrieve_top_sources(vectordb, question, k=DEFAULT_K)
        hit = expect_hit(sources, expect)
        status = "PASS" if hit else "FAIL"
        if hit:
            total_hits += 1
            type_hits[qtype] += 1
        type_total[qtype] += 1

        matched = [name for name in sources if expect in name]
        print(f"[{status}] #{qid} ({qtype}) {question}")
        print(f"       expect: {expect}")
        print(f"       top-{DEFAULT_K}: {', '.join(sources) if sources else '(none)'}")
        if matched:
            print(f"       matched: {', '.join(matched)}")
        print()

    total = len(questions)
    overall_rate = (total_hits / total * 100) if total else 0.0
    print("--- Summary ---")
    print(f"Overall: {total_hits}/{total} = {overall_rate:.1f}%")
    print("By type:")
    for qtype in sorted(type_total.keys()):
        hits = type_hits[qtype]
        count = type_total[qtype]
        rate = (hits / count * 100) if count else 0.0
        print(f"  {qtype}: {hits}/{count} = {rate:.1f}%")


if __name__ == "__main__":
    main()
