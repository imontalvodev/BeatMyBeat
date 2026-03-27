import importlib
import json
import unittest
from unittest.mock import patch


class _FakeResp:
    def __init__(self, status_code: int, payload: dict | None = None):
        self.status_code = status_code
        self._payload = payload or {}

    def json(self):
        return self._payload


class LyricsProviderTests(unittest.TestCase):
    def setUp(self) -> None:
        import main as backend_main
        self.backend = importlib.reload(backend_main)

    def test_api_lyrics_uses_ovh_when_available(self) -> None:
        with patch.object(
            self.backend.requests,
            "get",
            return_value=_FakeResp(200, {"lyrics": "line1\nline2"}),
        ):
            with patch.object(self.backend, "_get_lyrics_from_letras") as letras_mock:
                resp = self.backend.api_lyrics(title="In The End", artist="Linkin Park")

        self.assertTrue(resp.get("success"))
        self.assertEqual(resp.get("source"), "lyrics.ovh")
        self.assertIn("line1", resp.get("lyrics", ""))
        letras_mock.assert_not_called()

    def test_api_lyrics_fallback_to_letras_when_ovh_fails(self) -> None:
        with patch.object(
            self.backend.requests,
            "get",
            return_value=_FakeResp(404, {"error": "No lyrics found"}),
        ):
            with patch.object(
                self.backend,
                "_get_lyrics_from_letras",
                return_value={
                    "success": True,
                    "source": "letras.com",
                    "sourceUrl": "https://www.letras.com/x/y/",
                    "lyrics": "fallback lyrics",
                    "pageTitle": "In The End",
                    "pageArtist": "Linkin Park",
                },
            ):
                resp = self.backend.api_lyrics(title="In The End", artist="Linkin Park")

        self.assertTrue(resp.get("success"))
        self.assertEqual(resp.get("source"), "letras.com")
        self.assertEqual(resp.get("lyrics"), "fallback lyrics")

    def test_api_lyrics_returns_404_when_both_fail(self) -> None:
        with patch.object(
            self.backend.requests,
            "get",
            return_value=_FakeResp(500, {"error": "provider down"}),
        ):
            with patch.object(
                self.backend,
                "_get_lyrics_from_letras",
                return_value={
                    "success": False,
                    "error": "LyricsNotFound",
                    "message": "No se encontraron letras en letras.com",
                },
            ):
                resp = self.backend.api_lyrics(title="Unknown Song", artist="Unknown Artist")

        self.assertEqual(resp.status_code, 404)
        data = json.loads(resp.body.decode("utf-8"))
        self.assertFalse(data.get("success"))
        self.assertEqual(data.get("error"), "LyricsNotFound")
        self.assertIn("lyrics.ovh", data.get("message", ""))


if __name__ == "__main__":
    unittest.main()
