#!/usr/bin/env python3
"""Render a Claude Code --output-format stream-json transcript as readable text.

Reads JSONL on stdin, writes text on stdout, and passes malformed lines through
untouched so a crash in the CLI still shows up in the log rather than vanishing.

`claude -p --verbose` alone logs only the final message, which is no use when the
question is *why* a session went the way it did. The raw JSONL is kept alongside
this rendering for anything this drops.
"""
import json
import sys

MAX_RESULT = 2000  # tool results are frequently whole files; keep the head only


def text_of(content):
    if isinstance(content, str):
        return content
    return "".join(b.get("text", "") for b in content if b.get("type") == "text")


def emit(line):
    sys.stdout.write(line + "\n")
    sys.stdout.flush()


def main():
    for raw in sys.stdin:
        raw = raw.strip()
        if not raw:
            continue
        try:
            event = json.loads(raw)
        except json.JSONDecodeError:
            emit(raw)
            continue

        kind = event.get("type")

        if kind == "system" and event.get("subtype") == "init":
            emit(f"=== session {event.get('session_id', '?')} "
                 f"model={event.get('model', '?')} cwd={event.get('cwd', '?')}")
            continue

        if kind == "assistant":
            for block in event.get("message", {}).get("content", []):
                btype = block.get("type")
                if btype == "text" and block.get("text", "").strip():
                    emit("\n[assistant]\n" + block["text"].rstrip())
                elif btype == "thinking" and block.get("thinking", "").strip():
                    emit("\n[thinking]\n" + block["thinking"].rstrip())
                elif btype == "tool_use":
                    args = json.dumps(block.get("input", {}), ensure_ascii=False)
                    if len(args) > MAX_RESULT:
                        args = args[:MAX_RESULT] + f"... [{len(args)} chars]"
                    emit(f"\n[tool: {block.get('name')}] {args}")
            continue

        if kind == "user":
            for block in event.get("message", {}).get("content", []):
                if block.get("type") != "tool_result":
                    continue
                body = text_of(block.get("content", "")).rstrip()
                truncated = ""
                if len(body) > MAX_RESULT:
                    truncated = f"\n... [truncated, {len(body)} chars; see the .jsonl]"
                    body = body[:MAX_RESULT]
                tag = "tool error" if block.get("is_error") else "tool result"
                emit(f"[{tag}]\n{body}{truncated}")
            continue

        if kind == "result":
            emit("\n=== result: " + event.get("subtype", "?")
                 + f" turns={event.get('num_turns', '?')}"
                 + f" cost=${event.get('total_cost_usd', 0):.4f}"
                 + f" duration={event.get('duration_ms', 0) / 1000:.1f}s")
            if event.get("is_error"):
                emit("=== SESSION ENDED IN ERROR")
            if event.get("result"):
                emit("\n" + str(event["result"]).rstrip())


if __name__ == "__main__":
    main()
