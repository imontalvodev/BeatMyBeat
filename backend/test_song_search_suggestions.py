import importlib
import json
import unittest
from unittest.mock import patch


class _FakeYDL:
    def __init__(self, _opts):
        pass

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def extract_info(self, _query, download=False):
        assert download is False
        return {
            "entries": [
                {"title": "Metallica - One", "uploader": "Metallica Topic"},
                {"title": "One", "artist": "Metallica", "uploader": "Metallica Official"},
                {"title": "Numb", "artist": "Linkin Park", "uploader": "Linkin Park"},
                {"title": "Numb", "artist": "Linkin Park", "uploader": "Duplicate"},
            ]
        }


class _FakeYDLException:
    def __init__(self, _opts):
        pass

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def extract_info(self, _query, download=False):
        raise RuntimeError("boom")


class SongSearchSuggestionsTests(unittest.TestCase):
    def setUp(self) -> None:
        import main as backend_main

        self.backend = importlib.reload(backend_main)

    def test_search_song_suggestions_success(self) -> None:
        with patch.object(self.backend.yt_dlp, "YoutubeDL", _FakeYDL):
            resp = self.backend.api_search_song_suggestions(query="metalica one", limit=10)

        self.assertTrue(resp.get("success"))
        results = resp.get("results", [])
        self.assertGreaterEqual(len(results), 2)
        # Debe devolver formato title/artist y eliminar duplicados exactos
        self.assertIn({"title": "One", "artist": "Metallica"}, results)
        self.assertIn({"title": "Numb", "artist": "Linkin Park"}, results)
        self.assertEqual(len([r for r in results if r == {"title": "Numb", "artist": "Linkin Park"}]), 1)

    def test_search_song_suggestions_missing_query(self) -> None:
        resp = self.backend.api_search_song_suggestions(query="   ", limit=5)
        self.assertEqual(resp.status_code, 400)
        data = json.loads(resp.body.decode("utf-8"))
        self.assertFalse(data.get("success"))
        self.assertEqual(data.get("error"), "MissingQuery")

    def test_search_song_suggestions_internal_error(self) -> None:
        with patch.object(self.backend.yt_dlp, "YoutubeDL", _FakeYDLException):
            resp = self.backend.api_search_song_suggestions(query="test", limit=5)
        self.assertEqual(resp.status_code, 500)
        data = json.loads(resp.body.decode("utf-8"))
        self.assertFalse(data.get("success"))
        self.assertEqual(data.get("error"), "SongSearchError")


if __name__ == "__main__":
    unittest.main()
