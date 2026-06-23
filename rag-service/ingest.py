"""Build FAISS vector index from knowledge/ (.txt / .pdf / .docx)."""
from __future__ import annotations

import os
from pathlib import Path

from langchain.text_splitter import RecursiveCharacterTextSplitter
from langchain_community.document_loaders import Docx2txtLoader, PyPDFLoader, TextLoader
from langchain_community.embeddings import HuggingFaceEmbeddings
from langchain_community.vectorstores import FAISS

from text_utils import normalize_document_text

BASE_DIR = Path(__file__).resolve().parent
KNOWLEDGE_DIR = Path(os.getenv("KNOWLEDGE_DIR", str(BASE_DIR / "knowledge")))
FAISS_DIR = Path(os.getenv("FAISS_INDEX_DIR", str(BASE_DIR / "faiss_index")))
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "sentence-transformers/all-MiniLM-L6-v2")
CHUNK_SIZE = int(os.getenv("CHUNK_SIZE", "1000"))
CHUNK_OVERLAP = int(os.getenv("CHUNK_OVERLAP", "200"))

SUPPORTED_SUFFIXES = {".txt", ".pdf", ".docx"}


def load_file(path: Path) -> list:
    suffix = path.suffix.lower()
    if suffix == ".txt":
        loader = TextLoader(str(path), encoding="utf-8-sig")
    elif suffix == ".pdf":
        loader = PyPDFLoader(str(path))
    elif suffix == ".docx":
        loader = Docx2txtLoader(str(path))
    else:
        return []
    docs = loader.load()
    for doc in docs:
        doc.page_content = normalize_document_text(doc.page_content or "")
    return docs


def main() -> None:
    if not KNOWLEDGE_DIR.exists():
        KNOWLEDGE_DIR.mkdir(parents=True, exist_ok=True)
        print(f"Created {KNOWLEDGE_DIR}, add documents and run again")
        return

    docs = []
    skipped = []
    for file in sorted(KNOWLEDGE_DIR.iterdir()):
        if not file.is_file():
            continue
        suffix = file.suffix.lower()
        if suffix == ".doc":
            skipped.append(f"{file.name} (请另存为 .docx)")
            continue
        if suffix not in SUPPORTED_SUFFIXES:
            skipped.append(f"{file.name} (不支持 {suffix})")
            continue
        try:
            loaded = load_file(file)
            if not loaded:
                skipped.append(f"{file.name} (无内容)")
                continue
            docs.extend(loaded)
            print(f"loaded: {file.name} ({len(loaded)} parts)")
        except Exception as exc:
            skipped.append(f"{file.name} ({exc})")

    if skipped:
        print("skipped:")
        for item in skipped:
            print(f"  - {item}")

    if not docs:
        print(f"No readable files in {KNOWLEDGE_DIR}")
        print(f"Supported: {', '.join(sorted(SUPPORTED_SUFFIXES))}")
        return

    splitter = RecursiveCharacterTextSplitter(
        chunk_size=CHUNK_SIZE,
        chunk_overlap=CHUNK_OVERLAP,
        separators=["\n\n", "\n3.", "\n4.", "\n5.", "\n6.", "\n7.", "\n8.", "\n9.", "\n", "。", " "],
    )
    chunks = splitter.split_documents(docs)
    print(f"chunks: {len(chunks)}")

    embeddings = HuggingFaceEmbeddings(model_name=EMBEDDING_MODEL)
    vectordb = FAISS.from_documents(chunks, embeddings)
    FAISS_DIR.mkdir(parents=True, exist_ok=True)
    vectordb.save_local(str(FAISS_DIR))
    print(f"FAISS index saved: {FAISS_DIR}")


if __name__ == "__main__":
    main()
