"""RAG engine: FAISS hybrid retrieval + DeepSeek generation."""
from __future__ import annotations

import logging
import os
import sys
from pathlib import Path
from typing import Any

from langchain.prompts import PromptTemplate
from langchain_community.embeddings import HuggingFaceEmbeddings
from langchain_community.vectorstores import FAISS
from langchain_openai import ChatOpenAI

from retrieval import (
    DEFAULT_K,
    ENUMERATION_K,
    build_comparison_context,
    build_context,
    extract_comparison_concepts,
    extract_definition_fallback,
    extract_enumeration_fallback,
    extract_query_term,
    extract_triggers_cache_fallback,
    hybrid_retrieve,
    is_comparison_question,
    is_definition_question,
    is_enumeration_question,
    is_judgment_comparison_question,
    is_trigger_factor_question,
    retrieve_for_concept,
)


def _configure_logging() -> None:
    root = logging.getLogger()
    if root.handlers:
        return
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(logging.Formatter("%(levelname)s:%(name)s:%(message)s"))
    if hasattr(handler.stream, "reconfigure"):
        try:
            handler.stream.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass
    logging.basicConfig(level=logging.INFO, handlers=[handler])


_configure_logging()
logger = logging.getLogger(__name__)

BASE_DIR = Path(__file__).resolve().parent
FAISS_DIR = Path(os.getenv("FAISS_INDEX_DIR", str(BASE_DIR / "faiss_index")))
KNOWLEDGE_DIR = Path(os.getenv("KNOWLEDGE_DIR", str(BASE_DIR / "knowledge")))
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "sentence-transformers/all-MiniLM-L6-v2")

PROMPT_TEMPLATE = """你是一个专业的滑坡灾害防治助手。请仅根据下列「参考文档」回答用户问题。
若参考文档中已有不够准确的信息，请据实说明；仅在完全无关时才回答「根据现有资料无法确定，需要补充」。
如果检索到多个来源的答案，请归纳总结，不要只依赖单一来源。
对于列举型问题，请尽量列出参考文档中的全部相关条目，分点作答。

【参考文档】
{context}

【用户问题】
{question}

【回答】"""

COMPARISON_PROMPT_TEMPLATE = """你是一个专业的滑坡灾害防治助手。用户正在对比多个概念，请仅根据下列「参考文档」作答。

参考文档按概念分组（【概念1：xxx】、【概念2：yyy】等），每个概念有独立的参考资料。

请按以下要求回答：
1. 优先使用 Markdown 对比表格，列至少包含：对比维度、各概念对应内容。
2. 若表格不便展示，可使用结构化的对比列表（分维度说明各概念异同）。
3. 若参考文档中没有直接的对比表述，请根据各概念的独立描述归纳对比结论，并说明依据。
4. 不要编造参考文档中不存在的事实；缺少信息时据实说明。

【参考文档】
{context}

【用户问题】
{question}

【回答】"""




JUDGMENT_COMPARISON_PROMPT_TEMPLATE = """你是一个专业的滑坡灾害防治助手。用户正在对比或判别多个概念，请仅根据下列「参考文档」作答。

参考文档按概念分组（【概念1：xxx】、【概念2：yyy】等），每个概念有独立的参考资料。

请按以下要求回答：
1. 优先使用 Markdown 对比表格，列至少包含：对比维度、各概念对应内容。
2. 若表格不便展示，可使用结构化的对比列表（分维度说明各概念异同）。
3. 若参考文档中没有直接的对比表述，请根据各概念的独立描述归纳对比结论，并说明依据。
4. 不要编造参考文档中不存在的事实；缺少信息时据实说明。

针对「怎么判断」「如何区分」「怎么辨别」类判别问题，请按以下结构作答：
1. 先列出判断的核心依据：明确从哪几个维度（如形态特征、物质组成、运动方式、触发条件等）进行区分。
2. 再用 Markdown 对比表格或分维度对比列表，呈现各维度下各概念的具体差异。
3. 可附简要判别步骤或思路，帮助用户现场辨识。
表格列建议至少包含：对比维度、各概念对应特征/判别要点。

【参考文档】
{context}

【用户问题】
{question}

【回答】"""

class RagEngine:
    def __init__(self) -> None:
        self.vectordb: FAISS | None = None
        self.llm: ChatOpenAI | None = None
        self.knowledge_dir: Path = KNOWLEDGE_DIR
        self._prompt = PromptTemplate(template=PROMPT_TEMPLATE, input_variables=["context", "question"])
        self._comparison_prompt = PromptTemplate(
            template=COMPARISON_PROMPT_TEMPLATE,
            input_variables=["context", "question"],
        )
        self._judgment_comparison_prompt = PromptTemplate(
            template=JUDGMENT_COMPARISON_PROMPT_TEMPLATE,
            input_variables=["context", "question"],
        )
        self._ready = False
        self._error: str | None = None

    @property
    def ready(self) -> bool:
        return self._ready

    @property
    def error(self) -> str | None:
        return self._error

    def initialize(self) -> None:
        index_file = FAISS_DIR / "index.faiss"
        if not index_file.exists():
            self._error = f"FAISS index missing: {FAISS_DIR}. Run: python ingest.py"
            return

        api_key = os.getenv("DEEPSEEK_API_KEY", "").strip()
        if not api_key:
            self._error = "DEEPSEEK_API_KEY not set"
            return

        try:
            embeddings = HuggingFaceEmbeddings(model_name=EMBEDDING_MODEL)
            self.vectordb = FAISS.load_local(
                str(FAISS_DIR),
                embeddings,
                allow_dangerous_deserialization=True,
            )
            self.llm = ChatOpenAI(
                base_url=os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1"),
                api_key=api_key,
                model=os.getenv("DEEPSEEK_MODEL", "deepseek-chat"),
                temperature=0.2,
            )
            self.knowledge_dir = Path(os.getenv("KNOWLEDGE_DIR", str(BASE_DIR / "knowledge")))
            self._ready = True
            self._error = None
            logger.info("RAG engine initialized (knowledge_dir=%s)", self.knowledge_dir)
        except Exception as exc:
            self._error = str(exc)
            logger.exception("RAG initialize failed: %s", exc)

    def _build_sources(
        self,
        docs: list | None = None,
        *,
        concept_docs: dict[str, list] | None = None,
    ) -> list[dict[str, Any]]:
        sources: list[dict[str, Any]] = []
        if concept_docs:
            for concept_name, doc_list in concept_docs.items():
                for doc in doc_list:
                    sources.append(self._source_item(doc, concept=concept_name))
            return sources
        for doc in docs or []:
            sources.append(self._source_item(doc))
        return sources

    def _source_item(self, doc: Any, *, concept: str | None = None) -> dict[str, Any]:
        meta = dict(doc.metadata or {})
        source_name = meta.get("source", meta.get("file", "unknown"))
        snippet = (doc.page_content or "")[:300]
        item: dict[str, Any] = {"source": str(source_name), "snippet": snippet}
        if concept is not None:
            item["concept"] = concept
        if meta.get("score") is not None:
            item["score"] = meta["score"]
        if meta.get("similarity") is not None:
            item["similarity"] = meta["similarity"]
        return item

    def _merge_docs(self, primary: list, extra: list) -> list:
        seen: set[str] = set()
        merged: list = []
        for doc in primary + extra:
            key = (doc.page_content or "")[:200]
            if key in seen:
                continue
            seen.add(key)
            merged.append(doc)
        return merged

    def ask(self, question: str) -> dict[str, Any]:
        if not self._ready or self.vectordb is None or self.llm is None:
            raise RuntimeError(self._error or "RAG engine not ready")

        question = (question or "").strip()
        docs: list = []
        enumeration = is_enumeration_question(question)

        if is_comparison_question(question):
            concepts = extract_comparison_concepts(question)
            if len(concepts) >= 2:
                logger.info("Comparison question, concepts=%s", concepts)
                concept_docs: dict[str, list] = {}
                for concept in concepts:
                    concept_docs[concept] = retrieve_for_concept(
                        self.vectordb,
                        self.knowledge_dir,
                        concept,
                    )
                context = build_comparison_context(concept_docs)
                if is_judgment_comparison_question(question):
                    prompt = self._judgment_comparison_prompt
                else:
                    prompt = self._comparison_prompt
                prompt_text = prompt.format(context=context, question=question)
                response = self.llm.invoke(prompt_text)
                answer = getattr(response, "content", None) or str(response)
                return {
                    "answer": answer,
                    "sources": self._build_sources(concept_docs=concept_docs),
                }

        if is_trigger_factor_question(question):
            logger.info("Trigger-factor question")
            cache_docs = extract_triggers_cache_fallback(self.knowledge_dir)
            if not cache_docs:
                cache_docs = extract_enumeration_fallback(self.knowledge_dir, question)
            vector_docs = hybrid_retrieve(
                self.vectordb,
                question,
                k=ENUMERATION_K,
                definition_mode=False,
                enumeration_mode=True,
            )
            docs = self._merge_docs(cache_docs, vector_docs)
            logger.info("Merged cache=%d vector=%d total=%d", len(cache_docs), len(vector_docs), len(docs))
        elif enumeration:
            logger.info("Enumeration question")
            docs = hybrid_retrieve(
                self.vectordb,
                question,
                k=ENUMERATION_K,
                definition_mode=False,
                enumeration_mode=True,
            )
        elif is_definition_question(question):
            term = extract_query_term(question)
            logger.info("Definition question, term=%s", term)
            docs = extract_definition_fallback(self.knowledge_dir, term)
            if docs:
                logger.info("Using definition fallback (%d docs)", len(docs))
            else:
                docs = hybrid_retrieve(
                    self.vectordb,
                    question,
                    k=DEFAULT_K,
                    definition_mode=True,
                )
        else:
            docs = hybrid_retrieve(
                self.vectordb,
                question,
                k=DEFAULT_K,
                definition_mode=False,
            )

        max_chars = 18000 if enumeration or is_trigger_factor_question(question) else 12000
        context = build_context(docs, max_chars=max_chars)
        prompt_text = self._prompt.format(context=context, question=question)
        response = self.llm.invoke(prompt_text)
        answer = getattr(response, "content", None) or str(response)

        return {"answer": answer, "sources": self._build_sources(docs)}


engine = RagEngine()
