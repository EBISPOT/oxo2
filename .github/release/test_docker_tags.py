"""Exercise the actual Docker workflow's tag selection without publishing images."""
import os
from pathlib import Path
import subprocess
import tempfile
import textwrap
import unittest


WORKFLOW = Path(__file__).resolve().parents[1] / "workflows/docker.yml"


class DockerTagsTest(unittest.TestCase):
    def select_tag(self, event, ref, head_ref=""):
        # Run the workflow's shell verbatim, so a regression in its tag selection is caught.
        step = WORKFLOW.read_text().split("      - name: Prepare image tags\n", 1)[1]
        step = step.split("      - name:", 1)[0]
        script = textwrap.dedent(step.split("        run: |\n", 1)[1])
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "output"
            env = dict(os.environ, GITHUB_EVENT_NAME=event, GITHUB_REF=ref,
                       GITHUB_REF_NAME=ref.removeprefix("refs/heads/").removeprefix("refs/pull/"),
                       GITHUB_HEAD_REF=head_ref, GITHUB_SHA="a" * 40,
                       GITHUB_OUTPUT=str(output))
            result = subprocess.run(["bash", "-euo", "pipefail", "-c", script],
                                    env=env, capture_output=True, text=True)
            return result.returncode, output.read_text() if output.exists() else ""

    def test_merge_back_pr_cannot_overwrite_stable(self):
        self.assertEqual(self.select_tag("pull_request", "refs/pull/180/merge", "stable"),
                         (0, "ref_tag=pr-180\n"))

    def test_pr_tags_cannot_collide_with_release_or_cache_tags(self):
        for branch in ("dev", "v2.0.2", "release-buildcache", "feature/example"):
            with self.subTest(branch=branch):
                self.assertEqual(self.select_tag("pull_request", "refs/pull/181/merge", branch),
                                 (0, "ref_tag=pr-181\n"))

    def test_dev_push_keeps_the_dev_alias(self):
        self.assertEqual(self.select_tag("push", "refs/heads/dev"), (0, "ref_tag=dev\n"))

    def test_unexpected_events_and_refs_fail_without_publishing_a_tag(self):
        for event, ref in (("push", "refs/heads/stable"), ("push", "refs/tags/v2.0.2"),
                           ("workflow_dispatch", "refs/heads/dev"),
                           ("pull_request", "refs/heads/stable")):
            with self.subTest(event=event, ref=ref):
                code, output = self.select_tag(event, ref)
                self.assertNotEqual(code, 0)
                self.assertEqual(output, "")
