#!/usr/bin/env python3
"""Render `claude -p --output-format stream-json` as readable live output.

grind.sh runs its sessions non-interactively so they end by themselves instead
of dropping into a prompt that waits for Ctrl-C. Print mode alone would show
nothing until the very end, so the sessions stream JSON and this filter turns it
into what an interactive session shows: thinking, tool calls, results, answers.

    claude -p --output-format stream-json --include-partial-messages --verbose \
        "..." | python3 scripts/claude-stream.py [--text-out FILE]

--text-out writes the final assistant text (nothing else) to FILE, which is how
the reviewer's VERDICT line is captured.
"""
import argparse
import json
import sys

COLOR = sys.stdout.isatty()


def paint(text, code):
    return f"\033[{code}m{text}\033[0m" if COLOR else text


DIM = lambda s: paint(s, "2")          # noqa: E731
CYAN = lambda s: paint(s, "36")        # noqa: E731
YELLOW = lambda s: paint(s, "33")      # noqa: E731
RED = lambda s: paint(s, "31")         # noqa: E731
BOLD = lambda s: paint(s, "1")         # noqa: E731


def clip(text, limit=200):
    text = " ".join(str(text).split())
    return text if len(text) <= limit else text[:limit] + "…"


def tool_line(name, args):
    """One line per tool call: the argument that says what it actually does."""
    if not isinstance(args, dict):
        return f"{name}"
    for key in ("command", "file_path", "pattern", "path", "url", "prompt", "query"):
        if key in args:
            return f"{name}({clip(args[key], 160)})"
    return f"{name}({clip(json.dumps(args, ensure_ascii=False), 120)})" if args else name


class Printer:
    def __init__(self):
        self.streamed = set()   # message ids whose text already arrived as deltas
        self.column = 0         # >0 when a line is left open by streaming

    def raw(self, text):
        if not text:
            return
        sys.stdout.write(text)
        sys.stdout.flush()
        self.column = 0 if text.endswith("\n") else self.column + len(text)

    def line(self, text=""):
        if self.column:
            self.raw("\n")
        self.raw(text + "\n")

    def handle(self, event):
        kind = event.get("type")
        if kind == "system":
            if event.get("subtype") == "init":
                self.line(DIM(f"· session {event.get('session_id', '?')} "
                              f"[{event.get('model', '?')}]"))
        elif kind == "stream_event":
            self.partial(event.get("event") or {})
        elif kind == "assistant":
            self.assistant(event.get("message") or {})
        elif kind == "user":
            self.tool_results(event.get("message") or {})
        elif kind == "result":
            self.result(event)

    # --- live deltas ----------------------------------------------------------
    def partial(self, ev):
        etype = ev.get("type")
        if etype == "message_start":
            mid = ((ev.get("message") or {}).get("id"))
            if mid:
                self.streamed.add(mid)
        elif etype == "content_block_delta":
            delta = ev.get("delta") or {}
            if delta.get("type") == "text_delta":
                self.raw(delta.get("text", ""))
            elif delta.get("type") == "thinking_delta":
                self.raw(DIM(delta.get("thinking", "")))
        elif etype == "message_stop":
            self.line()

    # --- whole messages -------------------------------------------------------
    def assistant(self, message):
        already = message.get("id") in self.streamed
        for block in message.get("content") or []:
            btype = block.get("type")
            if btype == "thinking" and not already:
                self.line(DIM(block.get("thinking", "").strip()))
            elif btype == "text" and not already:
                text = block.get("text", "").strip()
                if text:
                    self.line(text)
            elif btype == "tool_use":
                self.line(CYAN("⏺ " + tool_line(block.get("name", "?"),
                                                block.get("input"))))

    def tool_results(self, message):
        for block in message.get("content") or []:
            if block.get("type") != "tool_result":
                continue
            content = block.get("content")
            if isinstance(content, list):
                content = " ".join(c.get("text", "") for c in content
                                   if isinstance(c, dict))
            text = clip(content or "", 200)
            if not text:
                continue
            self.line((RED("  ⎿ ") if block.get("is_error") else DIM("  ⎿ ")) + DIM(text))

    def result(self, event):
        if event.get("subtype") != "success":
            self.line(RED(f"· ended: {event.get('subtype')} "
                          f"{clip(event.get('result', ''), 200)}"))
            return
        cost = event.get("total_cost_usd")
        turns = event.get("num_turns")
        secs = (event.get("duration_ms") or 0) / 1000
        bits = [f"{turns} turns" if turns else "", f"{secs:.0f}s" if secs else "",
                f"${cost:.2f}" if isinstance(cost, (int, float)) else ""]
        self.line(DIM("· done — " + ", ".join(b for b in bits if b)))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--text-out", help="write the final assistant text here")
    args = ap.parse_args()

    printer = Printer()
    final = ""
    for raw in sys.stdin:
        raw = raw.strip()
        if not raw:
            continue
        try:
            event = json.loads(raw)
        except json.JSONDecodeError:
            # Not our stream (a crash message, a stray log line): show it as is.
            printer.line(YELLOW(raw))
            continue
        if event.get("type") == "result" and event.get("subtype") == "success":
            final = event.get("result") or final
        elif event.get("type") == "assistant":
            for block in (event.get("message") or {}).get("content") or []:
                if block.get("type") == "text" and block.get("text", "").strip():
                    final = block["text"]
        printer.handle(event)
    printer.line()

    if args.text_out:
        with open(args.text_out, "w", encoding="utf-8") as fh:
            fh.write(final.rstrip() + "\n")


if __name__ == "__main__":
    main()
