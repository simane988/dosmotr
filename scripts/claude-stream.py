#!/usr/bin/env python3
"""Render `claude -p --output-format stream-json` as readable live output.

grind.sh runs its sessions non-interactively so they end by themselves instead
of dropping into a prompt that waits for Ctrl-C. Print mode alone would show
nothing until the very end, so the sessions stream JSON and this filter turns it
into what an interactive session shows: thinking, tool calls, results, answers.

    claude -p --output-format stream-json --include-partial-messages --verbose \
        "..." | python3 scripts/claude-stream.py [--text-out FILE] [--limit-out FILE]

--text-out writes the final assistant text (nothing else) to FILE, which is how
the reviewer's VERDICT line is captured.

--limit-out writes "<epoch>\t<human time>" to FILE when the session died on a
usage limit and said when the limit lifts. grind.sh sleeps until exactly that
moment instead of guessing an interval.

Every discrete event — session start, tool call, tool result, the end of the
run — carries a wall-clock timestamp, because a backlog run lasts hours and
"which of these steps took forty minutes" is the question asked of the log
afterwards. Streamed prose does not: it arrives a character at a time.
"""
import argparse
import json
import re
import sys
import time
from datetime import datetime, timedelta

COLOR = sys.stdout.isatty()


def paint(text, code):
    return f"\033[{code}m{text}\033[0m" if COLOR else text


DIM = lambda s: paint(s, "2")          # noqa: E731
CYAN = lambda s: paint(s, "36")        # noqa: E731
YELLOW = lambda s: paint(s, "33")      # noqa: E731
RED = lambda s: paint(s, "31")         # noqa: E731
BOLD = lambda s: paint(s, "1")         # noqa: E731


def stamp():
    return DIM(time.strftime("[%H:%M:%S] "))


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


# --- usage limits -------------------------------------------------------------
# A session killed by a usage limit says so in its last message, and says when
# the limit lifts — either as an epoch ("...limit reached|1751284800") or in
# words ("resets 3pm", "will reset at 15:00 (UTC)"). Both forms are turned into
# an epoch here so the caller can wait for the real moment.
#
# The hint is required before anything is parsed: "resets at 3" appears in
# ordinary prose too, and a false positive costs a wait of up to a day.
LIMIT_HINT = re.compile(
    r"(usage|session|rate|weekly|hourly)[\s-]+limit"
    r"|limit\s+(reached|exceeded)"
    r"|limit\s+will\s+reset"
    r"|limit\s+resets",
    re.I,
)
LIMIT_EPOCH = re.compile(r"limit\s+reached\s*\|\s*(\d{9,13})", re.I)
RESET_CLOCK = re.compile(
    r"reset[a-z]*\s*(?:at|on|after)?\s*"
    r"(\d{1,2})(?::(\d{2}))?\s*([ap]\.?m\.?)?"
    r"\s*(?:\(([^)]{1,40})\)|\s([A-Z]{2,5}))?",
    re.I,
)


def zone_of(name):
    """The tzinfo a message named in parentheses, or None for local time."""
    if not name:
        return None
    name = name.strip()
    try:
        from zoneinfo import ZoneInfo
        return ZoneInfo("UTC" if name.upper() in ("UTC", "GMT", "Z") else name)
    except Exception:
        return None


def parse_reset(text):
    """(epoch, human) for a usage-limit message that names a reset time."""
    if not text or not LIMIT_HINT.search(text):
        return None
    epoch = None
    match = LIMIT_EPOCH.search(text)
    if match:
        epoch = int(match.group(1))
        if epoch > 10_000_000_000:      # milliseconds, not seconds
            epoch //= 1000
    else:
        match = RESET_CLOCK.search(text)
        if not match:
            return None
        hour, minute = int(match.group(1)), int(match.group(2) or 0)
        suffix = (match.group(3) or "").replace(".", "").lower()
        if suffix == "pm" and hour < 12:
            hour += 12
        if suffix == "am" and hour == 12:
            hour = 0
        if hour > 23 or minute > 59:
            return None
        now = datetime.now(zone_of(match.group(4) or match.group(5)))
        target = now.replace(hour=hour, minute=minute, second=0, microsecond=0)
        if target <= now:               # a bare clock time means the next one
            target += timedelta(days=1)
        epoch = int(target.timestamp())
    # A garbled number must not park the loop until next week.
    now = time.time()
    if not (now - 3600 < epoch < now + 7 * 86400):
        return None
    return epoch, time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(epoch))


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

    def event_line(self, text):
        self.line(stamp() + text)

    def handle(self, event):
        kind = event.get("type")
        if kind == "system":
            if event.get("subtype") == "init":
                self.event_line(DIM(f"· session {event.get('session_id', '?')} "
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
                    self.event_line(text)
            elif btype == "tool_use":
                self.event_line(CYAN("⏺ " + tool_line(block.get("name", "?"),
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
            self.event_line((RED("  ⎿ ") if block.get("is_error") else DIM("  ⎿ "))
                            + DIM(text))

    def result(self, event):
        if event.get("subtype") != "success":
            self.event_line(RED(f"· ended: {event.get('subtype')} "
                                f"{clip(event.get('result', ''), 200)}"))
            return
        cost = event.get("total_cost_usd")
        turns = event.get("num_turns")
        secs = (event.get("duration_ms") or 0) / 1000
        bits = [f"{turns} turns" if turns else "", f"{secs:.0f}s" if secs else "",
                f"${cost:.2f}" if isinstance(cost, (int, float)) else ""]
        self.event_line(DIM("· done — " + ", ".join(b for b in bits if b)))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--text-out", help="write the final assistant text here")
    ap.add_argument("--limit-out", help="write the usage-limit reset time here")
    args = ap.parse_args()

    printer = Printer()
    final = ""
    limit = None

    def check_limit(text):
        """First usage-limit message wins; later ones repeat the same reset."""
        nonlocal limit
        if limit is not None:
            return
        hit = parse_reset(text)
        if not hit:
            return
        limit = hit
        wait = max(0, int(hit[0] - time.time()))
        printer.event_line(YELLOW(f"· usage limit — resets {hit[1]} "
                                  f"(in {wait // 3600}h {wait % 3600 // 60:02d}m)"))

    for raw in sys.stdin:
        raw = raw.strip()
        if not raw:
            continue
        try:
            event = json.loads(raw)
        except json.JSONDecodeError:
            # Not our stream (a crash message, claude's stderr): show it as is,
            # and read it for a limit — that is one of the places it lands.
            printer.event_line(YELLOW(raw))
            check_limit(raw)
            continue
        if event.get("type") == "result":
            text = " ".join(str(event.get(k, "")) for k in ("result", "error"))
            if event.get("subtype") == "success":
                final = event.get("result") or final
            check_limit(text)
        elif event.get("type") == "assistant":
            for block in (event.get("message") or {}).get("content") or []:
                if block.get("type") == "text" and block.get("text", "").strip():
                    final = block["text"]
                    check_limit(final)
        printer.handle(event)
    printer.line()

    if args.text_out:
        with open(args.text_out, "w", encoding="utf-8") as fh:
            fh.write(final.rstrip() + "\n")
    if args.limit_out and limit:
        with open(args.limit_out, "w", encoding="utf-8") as fh:
            fh.write(f"{limit[0]}\t{limit[1]}\n")


if __name__ == "__main__":
    main()
