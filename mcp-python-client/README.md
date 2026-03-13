# Tiny MCP Python Client

This is a small Python client that connects to the Spring AI MCP Server using the SSE transport.
It speaks JSON-RPC 2.0 over SSE + HTTP POST.

## What it does

- Connects to `http://localhost:8080/mcp` via SSE
- Performs MCP `initialize`
- Lists tools or calls a tool directly

## Prerequisites

- Python 3.9+
- MCP server running on `http://localhost:8080`

## Install

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

## Run

List available tools:

```bash
python client.py list-tools
```

Call a tool (example: `readFile`):

```bash
python client.py call-tool --name readFile --arguments '{"path":"README.md"}'
```

If your server uses a different SSE endpoint (or `/mcp` returns 403), try:

```bash
python client.py --sse-url http://localhost:8080/mcp/sse list-tools
```

If your server uses a different base URL:

```bash
python client.py --base-url http://localhost:8080/mcp list-tools
```

## Notes

- If your server sends a different SSE endpoint shape, update `client.py` to match.
- This client directly calls tools; it does not run an LLM or natural-language agent.
- The client will try `/mcp` first and auto-fall back to `/mcp/sse` if `/mcp` is not the SSE endpoint.
