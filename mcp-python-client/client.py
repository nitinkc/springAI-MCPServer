#!/usr/bin/env python3
"""Tiny MCP SSE client for Spring AI MCP Server.

This is a minimal JSON-RPC 2.0 client over SSE + HTTP POST.
"""

from __future__ import annotations

import argparse
import json
import queue
import threading
import time
from dataclasses import dataclass
from typing import Any, Dict, Iterable, Iterator, Optional

import requests


@dataclass
class SseEvent:
    event: str
    data: Any


def _parse_data_payload(raw: str) -> Any:
    raw = raw.strip()
    if not raw:
        return ""
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        return raw


def iter_sse_events(lines: Iterable[str]) -> Iterator[SseEvent]:
    event_type = "message"
    data_lines = []

    for line in lines:
        line = line.rstrip("\n")
        if not line:
            if data_lines:
                data = _parse_data_payload("\n".join(data_lines))
                yield SseEvent(event=event_type, data=data)
            event_type = "message"
            data_lines = []
            continue

        if line.startswith(":"):
            continue

        if line.startswith("event:"):
            event_type = line[len("event:") :].strip()
            continue

        if line.startswith("data:"):
            data_lines.append(line[len("data:") :].lstrip())
            continue

    if data_lines:
        data = _parse_data_payload("\n".join(data_lines))
        yield SseEvent(event=event_type, data=data)


class McpSseClient:
    def __init__(
        self,
        base_url: str,
        sse_url: Optional[str] = None,
        connect_timeout_seconds: float = 10.0,
        response_timeout_seconds: float = 30.0,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.sse_url = (sse_url or self.base_url).rstrip("/")
        self.connect_timeout_seconds = connect_timeout_seconds
        self.response_timeout_seconds = response_timeout_seconds
        self._session = requests.Session()
        self._response_queue: "queue.Queue[Dict[str, Any]]" = queue.Queue()
        self._message_url: Optional[str] = None
        self._session_id: Optional[str] = None
        self._sse_response: Optional[requests.Response] = None
        self._stop_event = threading.Event()
        self._listener_thread: Optional[threading.Thread] = None
        self._request_id = 0
        self._request_id_lock = threading.Lock()

    def connect(self) -> None:
        headers = {"Accept": "text/event-stream"}
        primary_url = self.sse_url
        response, error = self._try_connect(primary_url, headers)

        if response is None or not response.ok:
            fallback_url = None
            if self.sse_url == self.base_url:
                fallback_url = f"{self.base_url.rstrip('/')}/sse"

            if fallback_url and fallback_url != primary_url:
                if response is not None:
                    response.close()
                response, error = self._try_connect(fallback_url, headers)
                if response is not None and response.ok:
                    self.sse_url = fallback_url

        if response is None:
            raise RuntimeError(f"SSE connect failed: GET {primary_url} -> {error}")

        if not response.ok:
            body = response.text.strip()
            snippet = body[:500] if body else "(empty response)"
            raise RuntimeError(
                f"SSE connect failed: GET {response.url} -> {response.status_code} {response.reason}. "
                f"Response: {snippet}"
            )

        self._sse_response = response
        self._listener_thread = threading.Thread(target=self._listen, daemon=True)
        self._listener_thread.start()

    def _try_connect(
        self,
        url: str,
        headers: Dict[str, str],
    ) -> tuple[Optional[requests.Response], Optional[Exception]]:
        try:
            response = self._session.get(
                url,
                headers=headers,
                stream=True,
                timeout=(self.connect_timeout_seconds, None),
            )
            return response, None
        except requests.RequestException as exc:
            return None, exc

    def close(self) -> None:
        self._stop_event.set()
        if self._sse_response is not None:
            self._sse_response.close()

    def initialize(self) -> Dict[str, Any]:
        result = self.request(
            "initialize",
            {
                "clientInfo": {"name": "mcp-python-client", "version": "0.1"},
                "protocolVersion": "2024-11-05",
                "capabilities": {},
            },
        )
        self.notify("initialized", {})
        return result

    def list_tools(self) -> Dict[str, Any]:
        return self.request("tools/list", {})

    def call_tool(self, name: str, arguments: Dict[str, Any]) -> Dict[str, Any]:
        return self.request("tools/call", {"name": name, "arguments": arguments})

    def notify(self, method: str, params: Dict[str, Any]) -> None:
        message = {"jsonrpc": "2.0", "method": method, "params": params}
        self._post_message(message)

    def request(self, method: str, params: Dict[str, Any]) -> Dict[str, Any]:
        request_id = self._next_request_id()
        message = {
            "jsonrpc": "2.0",
            "id": request_id,
            "method": method,
            "params": params,
        }
        self._post_message(message)
        return self._wait_for_response(request_id)

    def _post_message(self, message: Dict[str, Any]) -> None:
        message_url = self._wait_for_message_url()
        response = self._session.post(message_url, json=message, timeout=10)
        response.raise_for_status()

    def _wait_for_message_url(self) -> str:
        deadline = time.time() + self.connect_timeout_seconds
        while time.time() < deadline:
            if self._message_url:
                return self._message_url
            time.sleep(0.05)
        return f"{self.base_url}/messages"

    def _wait_for_response(self, request_id: int) -> Dict[str, Any]:
        deadline = time.time() + self.response_timeout_seconds
        while time.time() < deadline:
            try:
                message = self._response_queue.get(timeout=0.1)
            except queue.Empty:
                continue
            if message.get("id") == request_id:
                return message
        raise TimeoutError(f"Timed out waiting for response to id={request_id}")

    def _next_request_id(self) -> int:
        with self._request_id_lock:
            self._request_id += 1
            return self._request_id

    def _listen(self) -> None:
        if self._sse_response is None:
            return

        lines = self._sse_response.iter_lines(decode_unicode=True)
        for event in iter_sse_events(lines):
            if self._stop_event.is_set():
                return

            if event.event == "endpoint":
                self._apply_endpoint_event(event.data)
                continue

            if event.event == "message":
                if isinstance(event.data, dict):
                    self._response_queue.put(event.data)
                continue

    def _apply_endpoint_event(self, payload: Any) -> None:
        if isinstance(payload, str):
            self._message_url = self._resolve_message_url(payload)
            return

        if isinstance(payload, dict):
            for key in ("messageUrl", "messageEndpoint", "messages", "endpoint", "url"):
                if key in payload:
                    self._message_url = self._resolve_message_url(str(payload[key]))
                    break
            if "sessionId" in payload:
                self._session_id = str(payload["sessionId"])
                if self._message_url and "sessionId=" not in self._message_url:
                    joiner = "&" if "?" in self._message_url else "?"
                    self._message_url = f"{self._message_url}{joiner}sessionId={self._session_id}"

    def _resolve_message_url(self, endpoint: str) -> str:
        if endpoint.startswith("http://") or endpoint.startswith("https://"):
            return endpoint
        if endpoint.startswith("/"):
            return f"{self._base_origin()}{endpoint}"
        return f"{self.base_url.rstrip('/')}/{endpoint}"

    def _base_origin(self) -> str:
        parts = self.base_url.split("//", 1)
        if len(parts) == 2:
            scheme, rest = parts
            origin = rest.split("/", 1)[0]
            return f"{scheme}//{origin}"
        return self.base_url


def _parse_arguments(raw: Optional[str]) -> Dict[str, Any]:
    if not raw:
        return {}
    try:
        return json.loads(raw)
    except json.JSONDecodeError as exc:
        raise SystemExit(f"Invalid JSON for --arguments: {exc}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Tiny MCP SSE client")
    parser.add_argument(
        "--base-url",
        default="http://localhost:8080/mcp",
        help="MCP base URL (default: http://localhost:8080/mcp)",
    )
    parser.add_argument(
        "--sse-url",
        default=None,
        help="Override the SSE connect URL (e.g. http://localhost:8080/mcp/sse)",
    )
    parser.add_argument("--no-init", action="store_true", help="Skip initialize call")

    subparsers = parser.add_subparsers(dest="command", required=True)

    subparsers.add_parser("list-tools", help="List MCP tools")

    call_parser = subparsers.add_parser("call-tool", help="Call a specific tool")
    call_parser.add_argument("--name", required=True, help="Tool name")
    call_parser.add_argument(
        "--arguments",
        help='JSON object for tool arguments, e.g. "{\\"path\\": \\"README.md\\"}"',
    )

    args = parser.parse_args()

    client = McpSseClient(base_url=args.base_url, sse_url=args.sse_url)
    try:
        client.connect()
        if not args.no_init:
            client.initialize()

        if args.command == "list-tools":
            response = client.list_tools()
        elif args.command == "call-tool":
            response = client.call_tool(args.name, _parse_arguments(args.arguments))
        else:
            raise SystemExit("Unknown command")

        print(json.dumps(response, indent=2))
    finally:
        client.close()


if __name__ == "__main__":
    main()

