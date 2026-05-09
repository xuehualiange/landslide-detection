"""Hybrid demo: keyword FAQ (Chinese) + fallback. Keeps last 10 rounds in memory."""

from __future__ import annotations

_MAX_ROUNDS = 10
_turns: list[tuple[str, str]] = []

_FAQ_LANDSLIDE = (
    "滑坡是指斜坡上的岩土体在重力、降雨、地震或人类工程活动等作用下，沿软弱面整体或分散地向下滑动的地质灾害。"
    "常见诱因包括持续降雨、坡脚开挖、植被破坏等。本系统侧重利用遥感/航拍影像结合深度学习模型，"
    "对疑似滑坡区域进行检测与辅助研判。"
)

_FAQ_SYSTEM = (
    "本滑坡识别系统主要包括：图像上传与识别任务、灾情等级评估、历史记录查询、灾情动态与预警事件、"
    "用户与角色管理等模块；推理侧示例使用 YOLO 类模型（ONNX）。具体流程可在「识别任务」页上传影像查看检测结果。"
)


def _match_reply(user_input: str) -> str | None:
    t = user_input.strip()
    low = t.lower()

    if any(k in t for k in ("滑坡", "山体滑动", "塌方", "泥石流")):
        return _FAQ_LANDSLIDE

    if any(k in t for k in ("系统", "识别任务", "YOLO", "功能", "模块")):
        return _FAQ_SYSTEM

    if any(k in t for k in ("预警", "灾情", "动态")):
        return (
            "灾情动态页可查看实时预警事件列表，必要时可对事件进行确认处理；"
            "预警触发与模型置信度、灾情等级判定策略有关，可在后端配置中调整阈值策略。"
        )

    if any(k in low for k in ("hello", "hi", "你好")):
        return "你好，我是本系统的内置问答助手（离线演示）。您可以问我「什么是滑坡」或「系统有哪些功能」。"

    if len(t) <= 12 and any(k in t for k in ("告诉我", "继续", "说说", "讲一下")):
        if _turns:
            prev_q = _turns[-1][0]
            if any(k in prev_q for k in ("滑坡", "山体")):
                return _FAQ_LANDSLIDE
            if any(k in prev_q for k in ("系统", "功能")):
                return _FAQ_SYSTEM
        return "可以说明一下您想了解的具体方向吗？例如：滑坡基本概念、本系统使用步骤，或识别结果如何解读。"

    return None


def get_ai_response(user_input: str) -> str:
    text = (user_input or "").strip()
    if not text:
        return ""

    reply = _match_reply(text)
    if reply is None:
        reply = (
            "（离线演示）我暂时只能回答与滑坡常识、本系统功能相关的固定说明。"
            "如需连续对话与专业生成，请改用 langchain-chat-api 并配置 DeepSeek/OpenAI 兼容接口。\n"
            f"您刚才说：{text}"
        )

    _turns.append((text, reply))
    if len(_turns) > _MAX_ROUNDS:
        _turns[:] = _turns[-_MAX_ROUNDS:]
    return reply


def recent_turns() -> list[tuple[str, str]]:
    """Expose trimmed history if you later wire a model that needs context."""
    return list(_turns)