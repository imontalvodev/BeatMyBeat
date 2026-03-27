import os
import importlib
import threading
import time
import json
import unittest
from unittest.mock import patch
import asyncio


# Forzar el límite para los tests
os.environ["MAX_CONCURRENT_DOWNLOADS"] = "1"


class DownloadQueueTests(unittest.TestCase):
    def setUp(self) -> None:
        # Re-cargar el módulo para que el semáforo/estado global use MAX_CONCURRENT_DOWNLOADS
        import main as backend_main  # backend/main.py (no es paquete)

        self.backend = importlib.reload(backend_main)

    def test_download_queue_max_concurrent_oneslot(self) -> None:
        """
        Con MAX_CONCURRENT_DOWNLOADS=1:
        - 1ª descarga entra y mantiene el slot
        - 2ª descarga debe responder 202 con 'Queued'
        - cuando se libera el slot, el job pasa a 'ready' y el stream funciona
        """

        first_started = threading.Event()
        release_first = threading.Event()

        def fake_download_with_yt_dlp(_video_url: str):
            first_started.set()
            # Mantener ocupada la petición hasta que el test libere el slot
            released = release_first.wait(timeout=5)
            if not released:
                raise RuntimeError("Timeout waiting for first download release")

            def gen():
                yield b"FIRST"

            return "first.mp3", gen(), "audio/mpeg"

        def fake_download_youtube_audio_to_file(_video_url: str, job_dir: str, **_kwargs):
            # Crear un fichero dummy para que /download-job/stream pueda servirlo
            os.makedirs(job_dir, exist_ok=True)
            file_path = os.path.join(job_dir, "job.mp3")
            with open(file_path, "wb") as f:
                f.write(b"JOB")
            return file_path, "job.mp3", "audio/mpeg", "mp3"

        # Asegurar que el job de tipo download-auto no haga red si se llegara a usar
        def fake_download_auto_audio_to_file(_final_query: str, job_dir: str, **_kwargs):
            os.makedirs(job_dir, exist_ok=True)
            file_path = os.path.join(job_dir, "job_auto.mp3")
            with open(file_path, "wb") as f:
                f.write(b"JOB_AUTO")
            return file_path, "job_auto.mp3", "audio/mpeg", "mp3"

        def call_first():
            # Mantener viva la llamada hasta que el semáforo se libere.
            # En este test llamamos al endpoint como función, sin TestClient.
            self.first_response = self.backend.api_download(videoId="first-video")

        with patch.object(self.backend, "_download_with_yt_dlp", new=fake_download_with_yt_dlp), patch.object(
            self.backend, "_download_youtube_audio_to_file", new=fake_download_youtube_audio_to_file
        ), patch.object(
            self.backend, "_download_auto_audio_to_file", new=fake_download_auto_audio_to_file
        ):
            t = threading.Thread(target=call_first, daemon=True)
            t.start()

            # Esperar a que la primera petición esté bloqueando/ocupando el slot
            self.assertTrue(first_started.wait(timeout=5), "La primera descarga no empezó a tiempo")

            queued_resp = self.backend.api_download(videoId="second-video")
            self.assertEqual(queued_resp.status_code, 202)
            queued_data = json.loads(queued_resp.body.decode("utf-8"))
            self.assertEqual(queued_data.get("error"), "Queued")
            self.assertEqual(queued_data.get("queuePosition"), 2)

            job_id = queued_data.get("jobId")
            self.assertTrue(job_id, "Falta jobId en la respuesta queued")

            # Liberar el primer slot para que el worker procese el job
            release_first.set()
            t.join(timeout=10)

            # Esperar a que el job esté listo
            ready = False
            for _ in range(60):
                job_state = self.backend.api_download_job(jobId=job_id)
                # endpoint devuelve dict (no hace falta parseo JSON)
                if job_state.get("status") == "ready":
                    ready = True
                    break
                time.sleep(0.1)

            self.assertTrue(ready, "El job no pasó a ready a tiempo")

            # Probar el stream
            stream_resp = self.backend.api_download_job_stream(jobId=job_id)
            self.assertEqual(stream_resp.status_code, 200)
            async def _collect_body() -> bytes:
                chunks: list[bytes] = []
                async for chunk in stream_resp.body_iterator:
                    chunks.append(chunk)
                return b"".join(chunks)

            content = asyncio.run(_collect_body())
            self.assertEqual(content, b"JOB")


if __name__ == "__main__":
    unittest.main()

