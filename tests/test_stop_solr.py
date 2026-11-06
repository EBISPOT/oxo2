"""Check the stop wrapper without stopping any real Solr processes.

Run with: python3 -m unittest discover -s tests -p 'test_stop_solr.py'
"""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().parents[1] / "stopSolr.sh"


class StopSolrTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="oxo2-stop-solr-")
        self.addCleanup(self.directory.cleanup)
        self.launcher_dir = Path(self.directory.name) / "solr bin"
        self.launcher_dir.mkdir()
        self.record = Path(self.directory.name) / "invocation.json"
        launcher = self.launcher_dir / "solr"
        launcher.write_text(
            "#!/usr/bin/env python3\n"
            "import json, os, sys\n"
            "from pathlib import Path\n"
            "Path(os.environ['STOP_TEST_RECORD']).write_text(json.dumps({\n"
            "    'arguments': sys.argv[1:], 'port': os.environ.get('SOLR_PORT'),\n"
            "    'pid_dir': os.environ.get('SOLR_PID_DIR')}))\n"
            "sys.exit(int(os.environ.get('STOP_TEST_EXIT', '0')))\n"
        )
        launcher.chmod(0o755)
        self.environment = {
            "PATH": os.environ["PATH"],
            "SOLR_SCRIPT": str(self.launcher_dir),
            "STOP_TEST_RECORD": str(self.record),
            "SOLR_PID_DIR": str(Path(self.directory.name) / "pid files"),
        }

    def run_script(self, variables=None, arguments=()):
        return subprocess.run(
            [str(SCRIPT), *arguments],
            env={**self.environment, **(variables or {})},
            text=True, capture_output=True, check=False,
        )

    def test_targets_only_selected_port_and_preserves_launcher_environment(self):
        for variables, expected in [
            ({"SOLR_PORT": "20183"}, "20183"),
            ({"SOLR_URL": "http://localhost:20283/solr"}, "20283"),
            ({"SOLR_PORT": "20183", "SOLR_URL": "http://localhost:20283/solr"}, "20183"),
            ({"SOLR_PORT": "", "SOLR_URL": ""}, "8983"),
        ]:
            with self.subTest(variables=variables):
                result = self.run_script(variables)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(json.loads(self.record.read_text()), {
                    "arguments": ["stop", "-p", expected], "port": expected,
                    "pid_dir": self.environment["SOLR_PID_DIR"],
                })

    def test_invalid_port_never_calls_launcher(self):
        for port in ["0", "65536", "-all", "abc", "20183; echo unexpected"]:
            with self.subTest(port=port):
                result = self.run_script({"SOLR_PORT": port})
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("SOLR_PORT must be", result.stderr)
                self.assertFalse(self.record.exists())

    def test_missing_launcher_fails_clearly(self):
        for launcher_path in ["", str(self.launcher_dir / "missing")]:
            with self.subTest(launcher_path=launcher_path):
                result = self.run_script({"SOLR_SCRIPT": launcher_path})
                self.assertNotEqual(result.returncode, 0)
                self.assertTrue(result.stderr)
                self.assertFalse(self.record.exists())

    def test_unexpected_arguments_do_not_stop_any_instance(self):
        result = self.run_script(arguments=["--all"])
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Usage:", result.stderr)
        self.assertFalse(self.record.exists())

    def test_propagates_launcher_failure(self):
        result = self.run_script({"STOP_TEST_EXIT": "7", "SOLR_PORT": "20183"})
        self.assertEqual(result.returncode, 7)


if __name__ == "__main__":
    unittest.main()
