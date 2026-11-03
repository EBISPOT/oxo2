"""Run the real download stage against a local HTTP server; no external data or Solr.

Build oxo2-downloader first, put nextflow on PATH, then run this file with Python 3.
Uses the production retry delays (up to 70 seconds for the exhaustion test).
"""
import io
import json
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile
import threading
import unittest
from collections import Counter
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


DATALOAD = Path(__file__).resolve().parents[1]
PAYLOAD = b"subject_id\tobject_id\nEFO:1\tMONDO:1\n"


class DownloadRetriesTest(unittest.TestCase):
    def run_download(self, failures):
        requests = Counter()
        archive = io.BytesIO()
        with tarfile.open(fileobj=archive, mode="w:gz") as tar:
            entry = tarfile.TarInfo("ontology.tsv")
            entry.size = len(PAYLOAD)
            tar.addfile(entry, io.BytesIO(PAYLOAD))

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                requests[self.path] += 1
                failing = self.path == "/sssom.tgz" and requests[self.path] <= failures
                body = b"Service unavailable" if failing else archive.getvalue()
                self.send_response(503 if failing else 200)
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *_args):
                pass

        with tempfile.TemporaryDirectory(prefix="oxo-download-retry-") as tmp:
            root = Path(tmp)
            server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            try:
                base = f"http://127.0.0.1:{server.server_port}"
                config = root / "config.json"
                config.write_text(json.dumps({"mapping_registries": [
                    {"id": "ols", "url": base + "/sssom.tgz"},
                    {"id": "healthy", "url": base + "/healthy.tgz"}
                ]}))
                # Only lower resource requirements; retain the real failure policy and delays.
                extra = root / "resources.config"
                extra.write_text("process { withName: DOWNLOAD_REGISTRY { memory = '256 MB' } }\n")
                logs = root / "logs"
                logs.mkdir()
                env = dict(os.environ, OXO2_CONFIG=str(config), OXO2_DATA=str(root / "data"),
                           NXF_WORK=str(root / "work"), NXF_LOGS=str(logs),
                           NF_EXTRA_CONFIG=str(extra), NF_PROFILE="standard",
                           NXF_OFFLINE="true", NXF_DISABLE_CHECK_LATEST="true",
                           NXF_ANSI_LOG="false", JAVA_OPTS="-Xmx128m", NXF_OPTS="-Xmx512m")
                result = subprocess.run(["bash", str(DATALOAD / "downloadMappings.nextflow")],
                                        cwd=root, env=env, capture_output=True, text=True, timeout=180)
                output = result.stdout + result.stderr
                downloaded = root / "data/sssom/ols/ontology.tsv"
                return result.returncode, requests, downloaded.read_bytes() if downloaded.exists() else None, output
            finally:
                server.shutdown()
                server.server_close()
                thread.join()

    def test_transient_failure_recovers_without_repeating_successful_registry(self):
        code, requests, payload, output = self.run_download(failures=2)
        self.assertEqual(code, 0, output)
        self.assertEqual(requests["/sssom.tgz"], 3, output)
        self.assertEqual(requests["/healthy.tgz"], 1, output)
        self.assertEqual(payload, PAYLOAD)

    def test_exhausted_retries_fail_the_stage_despite_a_successful_registry(self):
        code, requests, payload, output = self.run_download(failures=99)
        self.assertNotEqual(code, 0, output)
        self.assertEqual(requests["/sssom.tgz"], 4, output)
        self.assertEqual(requests["/healthy.tgz"], 1, output)
        self.assertIsNone(payload)


if __name__ == "__main__":
    unittest.main(verbosity=2)
