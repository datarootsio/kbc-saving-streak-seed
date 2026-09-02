#!/usr/bin/env python3
"""Render an agent session's JSON transcript as readable text.

Reads JSONL on stdin, writes text on stdout, and passes malformed lines through
untouched so a crash in the CLI still shows up in the log rather than vanishing.

Two dialects are understood, because implement-tickets.sh can run its sessions on
either agent and both logs should read the same way:

  claude --output-format stream-json   type: system / assistant / user / result
  pi --mode json                       type: session / message_end / agent_end / ...

The type names do not collide, so no flag is needed to tell them apart.

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


def emit_tool_result(content, is_error):
    body = text_of(content).rstrip()
    truncated = ""
    if len(body) > MAX_RESULT:
        truncated = f"\n... [truncated, {len(body)} chars; see the .jsonl]"
        body = body[:MAX_RESULT]
    emit(f"[{'tool error' if is_error else 'tool result'}]\n{body}{truncated}")


def emit_tool_use(name, args):
    rendered = json.dumps(args, ensure_ascii=False)
    if len(rendered) > MAX_RESULT:
        rendered = rendered[:MAX_RESULT] + f"... [{len(rendered)} chars]"
    emit(f"\n[tool: {name}] {rendered}")


def main():
    seen_model = ""   # pi names the model on every assistant message; say it once
    pi_cost = 0.0     # and reports cost per message rather than per session
    pi_turns = 0

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

        # --- claude -----------------------------------------------------------

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
                    emit_tool_use(block.get("name"), block.get("input", {}))
            continue

        if kind == "user":
            for block in event.get("message", {}).get("content", []):
                if block.get("type") != "tool_result":
                    continue
                emit_tool_result(block.get("content", ""), block.get("is_error"))
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
            continue

        # --- pi ---------------------------------------------------------------
        # The streaming events (message_start, message_update, tool_execution_*) are
        # dropped: every one of them is repeated in the message_end that closes the
        # block, so rendering both would double the log.

        if kind == "session":
            emit(f"=== session {event.get('id', '?')} cwd={event.get('cwd', '?')}")
            continue

        if kind == "message_end":
            message = event.get("message", {})
            role = message.get("role")

            if role == "toolResult":
                emit_tool_result(message.get("content", ""), message.get("isError"))
                continue

            if role != "assistant":
                continue  # the user message is the prompt this script was handed

            model = message.get("model", "")
            if model and model != seen_model:
                seen_model = model
                emit(f"=== model {message.get('provider', '?')}/{model}")
            pi_turns += 1
            pi_cost += (message.get("usage", {}).get("cost", {}).get("total") or 0)

            for block in message.get("content", []):
                btype = block.get("type")
                if btype == "text" and block.get("text", "").strip():
                    emit("\n[assistant]\n" + block["text"].rstrip())
                elif btype == "thinking" and block.get("thinking", "").strip():
                    emit("\n[thinking]\n" + block["thinking"].rstrip())
                elif btype == "toolCall":
                    emit_tool_use(block.get("name"), block.get("arguments", {}))
            continue

        if kind == "agent_end":
            messages = event.get("messages", [])
            last = messages[-1] if messages else {}
            emit(f"\n=== result: {last.get('stopReason', '?')}"
                 f" turns={pi_turns} cost=${pi_cost:.4f}")
            if event.get("willRetry"):
                emit("=== pi is retrying")
            final = text_of(last.get("content", []) if isinstance(last, dict) else [])
            if final.strip():
                emit("\n" + final.rstrip())
            continue

        if kind == "error":
            emit("\n=== SESSION ERROR: "
                 + str(event.get("message") or event.get("error") or raw))
            continue


if __name__ == "__main__":
    main()
