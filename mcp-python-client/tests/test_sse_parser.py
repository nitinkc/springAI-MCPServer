import unittest

from client import iter_sse_events


class SseParserTests(unittest.TestCase):
    def test_parses_endpoint_event(self) -> None:
        lines = [
            "event: endpoint\n",
            "data: {\"messageUrl\":\"/mcp/messages?sessionId=abc\"}\n",
            "\n",
        ]
        events = list(iter_sse_events(lines))
        self.assertEqual(len(events), 1)
        self.assertEqual(events[0].event, "endpoint")
        self.assertEqual(events[0].data["messageUrl"], "/mcp/messages?sessionId=abc")

    def test_parses_message_event(self) -> None:
        lines = [
            "event: message\n",
            "data: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}\n",
            "\n",
        ]
        events = list(iter_sse_events(lines))
        self.assertEqual(len(events), 1)
        self.assertEqual(events[0].event, "message")
        self.assertEqual(events[0].data["id"], 1)

    def test_parses_multiline_data(self) -> None:
        lines = [
            "event: message\n",
            "data: {\"jsonrpc\":\"2.0\",\n",
            "data: \"id\":2}\n",
            "\n",
        ]
        events = list(iter_sse_events(lines))
        self.assertEqual(len(events), 1)
        self.assertEqual(events[0].event, "message")
        self.assertEqual(events[0].data["id"], 2)


if __name__ == "__main__":
    unittest.main()

