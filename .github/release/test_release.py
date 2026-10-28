"""Release lifecycle tests for release.py — fake GitHub/Zenodo, a real temporary git repository.

Run with:  python3 -m unittest discover -s .github/release -p 'test_*.py' -v
Nothing here touches the network or makes a real release.
"""
import io
import json
import os
import re
import subprocess
import tarfile
import tempfile
import unittest
from pathlib import Path
from urllib.parse import parse_qs, urlparse

import release


def run_git(*args, cwd):
    env = {**os.environ, "GIT_AUTHOR_NAME": "Test", "GIT_AUTHOR_EMAIL": "test@example.org",
           "GIT_COMMITTER_NAME": "Test", "GIT_COMMITTER_EMAIL": "test@example.org"}
    return subprocess.check_output(["git", *args], cwd=cwd, text=True, env=env).strip()


class FakeGitHub(release.GitHub):
    """In-memory GitHub: releases, assets, tag refs and the merged PRs of each commit."""

    def __init__(self, pulls_by_commit=None):
        super().__init__("EBISPOT/oxo2", "fake-token")
        self.releases = []
        self.assets = {}
        self.tag_refs = {}
        self.pulls_by_commit = pulls_by_commit or {}
        self.next_id = 100

    def request(self, path, method="GET", data=None, raw=None, content_type="application/json"):
        parsed = urlparse(path)
        route = parsed.path.removeprefix(self.root)
        query = parse_qs(parsed.query)
        if route == "/releases" and method == "GET":
            page = int(query.get("page", ["1"])[0])
            return self.releases[(page - 1) * 100: page * 100]
        if route == "/releases" and method == "POST":
            self.next_id += 1
            created = {"id": self.next_id, "tag_name": data["tag_name"], "name": data["name"],
                       "target_commitish": data["target_commitish"], "body": data["body"],
                       "draft": data["draft"], "prerelease": data["prerelease"], "published_at": None}
            self.releases.insert(0, created)
            return dict(created)
        match = re.fullmatch(r"/releases/(\d+)", route)
        if match:
            found = next(r for r in self.releases if r["id"] == int(match.group(1)))
            if method == "PATCH":
                if "body" in data:
                    found["body"] = data["body"]
                if data.get("draft") is False and found["draft"]:
                    found["draft"] = False
                    found["published_at"] = "2026-10-23T10:00:00Z"
            return dict(found)
        match = re.fullmatch(r"/releases/(\d+)/assets", route)
        if match and method == "GET":
            release_assets = self.assets.get(int(match.group(1)), {})
            return [{"name": name, "url": f"https://api.github.com/assets/{match.group(1)}/{name}"}
                    for name in release_assets]
        match = re.fullmatch(r"/commits/([0-9a-f]{40})/pulls", route)
        if match:
            return self.pulls_by_commit.get(match.group(1), [])
        match = re.fullmatch(r"/git/matching-refs/tags/(.+)", route)
        if match:
            tag = match.group(1)
            return [{"ref": "refs/tags/" + tag, "object": {"type": "commit", "sha": self.tag_refs[tag]}}] \
                if tag in self.tag_refs else []
        if route == "/git/refs" and method == "POST":
            self.tag_refs[data["ref"].removeprefix("refs/tags/")] = data["sha"]
            return data
        raise AssertionError(f"Unexpected GitHub call: {method} {path}")

    def asset(self, release_id, name):
        return self.assets.get(release_id, {}).get(name)

    def upload(self, release_id, name, data, content_type="application/json"):
        existing = self.asset(release_id, name)
        if existing is not None:
            if existing != data:
                raise ValueError(f"Existing release asset differs: {name}")
            return
        self.assets.setdefault(release_id, {})[name] = data

    # Test helpers
    def release_for(self, tag):
        return next(r for r in self.releases if r["tag_name"] == tag)

    def state_for(self, tag):
        return release.parse_state(self.release_for(tag))


class FakeZenodo:
    """Returns the record after `available_after` lookups, mimicking delayed public indexing."""

    def __init__(self, available_after=0, doi="10.5281/zenodo.200", concept="10.5281/zenodo.100", record_id=200):
        self.available_after, self.calls = available_after, 0
        self.doi, self.concept, self.record_id = doi, concept, record_id

    def find(self, state):
        self.calls += 1
        if self.calls <= self.available_after:
            return None
        if state.get("conceptDoi") and state["conceptDoi"] != self.concept:
            raise ValueError("Zenodo software DOI series changed between releases")
        return {"doi": self.doi, "conceptdoi": self.concept, "id": self.record_id, "files": [{}]}


def pull_request(commit, labels=(), body=""):
    return {"merged_at": "2026-10-23T09:00:00Z", "merge_commit_sha": commit, "base": {"ref": "stable"},
            "labels": [{"name": label} for label in labels], "body": body}


class ReleaseTestCase(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.repo = Path(self.tmp.name) / "repo"
        self.repo.mkdir()
        run_git("init", "-q", "-b", "dev", cwd=self.repo)
        (self.repo / ".zenodo.json").write_text(json.dumps({"title": "OxO2", "creators": [{"name": "x"}]}))
        for target in release.BUNDLE_TARGETS:
            (self.repo / target).parent.mkdir(parents=True, exist_ok=True)
            (self.repo / target).write_bytes(release.json_bytes(release.EMPTY_BUNDLE))
        run_git("add", ".", cwd=self.repo)
        self.commit("feat(backend): first feature")
        self.previous_cwd = os.getcwd()
        os.chdir(self.repo)
        self.output = Path(self.tmp.name) / "out"
        self.config = {"firstVersion": "2.0.0", "archiving": "github",
                       "archiveWaitSeconds": 0.05, "archivePollSeconds": 0.001}
        self.config_none = {**self.config, "archiving": "none"}

    def tearDown(self):
        os.chdir(self.previous_cwd)
        self.tmp.cleanup()

    def commit(self, subject):
        marker = self.repo / "changes.txt"
        with marker.open("a") as handle:
            handle.write(subject + "\n")
        run_git("add", ".", cwd=self.repo)
        run_git("commit", "-q", "-m", subject, cwd=self.repo)
        return run_git("rev-parse", "HEAD", cwd=self.repo)

    def push_stable(self):
        """Mimic 'merge dev into stable': stable and origin/stable move to HEAD."""
        head = run_git("rev-parse", "HEAD", cwd=self.repo)
        run_git("update-ref", "refs/heads/stable", head, cwd=self.repo)
        run_git("update-ref", "refs/remotes/origin/stable", head, cwd=self.repo)
        return head

    def metadata_file(self, component, digest_byte):
        path = Path(self.tmp.name) / f"build-{component}.json"
        path.write_text(json.dumps({"containerimage.digest": "sha256:" + (digest_byte * 64)}))
        return path

    def full_release(self, gh, zenodo, config, commit):
        release.prepare(gh, config, commit, self.output)
        release.archive(gh, zenodo, config, self.output)
        for index, component in enumerate(release.COMPONENTS):
            release.remember_image(gh, self.output, component, self.metadata_file(component, str(index)))
        release.finish(gh, self.output)

    # --- version selection --------------------------------------------------------------------

    def test_first_release_uses_first_version_whatever_the_label(self):
        self.assertEqual(release.choose_version(self.config, [], ["release:minor"]), "2.0.0")
        self.assertEqual(release.choose_version(self.config, [], []), "2.0.0")

    def test_later_releases_bump_from_the_highest_tag(self):
        self.assertEqual(release.choose_version(self.config, ["2.0.0", "2.0.1"], []), "2.0.2")
        self.assertEqual(release.choose_version(self.config, ["2.0.1"], ["release:minor"]), "2.1.0")
        self.assertEqual(release.choose_version(self.config, ["2.1.3"], ["release:major"]), "3.0.0")

    def test_two_bump_labels_fail(self):
        with self.assertRaises(ValueError):
            release.choose_version(self.config, ["2.0.0"], ["release:minor", "release:major"])
        with self.assertRaises(ValueError):
            release.choose_version(self.config, [], ["release:minor", "release:patch"])

    # --- notes ---------------------------------------------------------------------------------

    def test_reviewed_notes_ignore_the_untouched_template(self):
        template = "<!-- hint -->\n## Release notes\n\n### Added\n\n### Changed\n\n### Fixed\n"
        self.assertEqual(release.reviewed_notes(template), "")
        filled = "## Release notes\n\n### Added\n- Deep health endpoint\n\n## Checklist\n- [x] done"
        self.assertEqual(release.reviewed_notes(filled), "### Added\n- Deep health endpoint")

    def test_generated_notes_group_conventional_commits(self):
        first = self.push_stable()
        run_git("tag", "v2.0.0", first, cwd=self.repo)
        self.commit("fix(frontend): show the chevron only on multi-mapping rows")
        self.commit("feat(api): add namespace to /api/v2/ontologies (ADR-0047)")
        self.commit("chore(deps): bump the maven-minor-patch group")
        self.commit("chore(deps-dev): bump js-yaml")
        self.commit("docs(adr): measure prefix-case drift")
        self.commit("Fixed date.")
        self.commit("feat!: drop the v1 datasource endpoint")
        head = self.push_stable()
        notes = release.generated_notes("EBISPOT/oxo2", "v2.0.0", "v2.1.0", head)
        self.assertLess(notes.index("### Breaking changes"), notes.index("### Added"))
        self.assertLess(notes.index("### Added"), notes.index("### Changed"))
        self.assertLess(notes.index("### Changed"), notes.index("### Fixed"))
        self.assertIn("- drop the v1 datasource endpoint", notes)
        self.assertIn("- api: add namespace to /api/v2/ontologies (ADR-0047)", notes)
        self.assertIn("- frontend: show the chevron only on multi-mapping rows", notes)
        self.assertIn("- adr: measure prefix-case drift", notes)
        self.assertIn("- Fixed date.", notes)
        self.assertIn("- 2 dependency updates", notes)
        self.assertNotIn("bump js-yaml", notes)
        self.assertTrue(notes.endswith("https://github.com/EBISPOT/oxo2/compare/v2.0.0...v2.1.0"))

    def test_generated_notes_skip_merge_commits_and_mention_empty_ranges(self):
        self.assertIsNone(release.classify_commit("Merge pull request #104 from EBISPOT/dependabot/x"))
        self.assertIsNone(release.classify_commit("Merge branch 'tooltips-stats' into dev"))
        first = self.push_stable()
        run_git("tag", "v2.0.0", first, cwd=self.repo)
        notes = release.generated_notes("EBISPOT/oxo2", "v2.0.0", "v2.0.1", first)
        self.assertIn("No code changes since the previous release.", notes)

    def test_first_release_notes_need_no_previous_tag(self):
        notes = release.generated_notes("EBISPOT/oxo2", None, "v2.0.0", "abc")
        self.assertIn("First numbered OxO2 software release.", notes)
        self.assertIn("/commits/v2.0.0", notes)

    # --- lifecycle -----------------------------------------------------------------------------

    def test_full_release_with_zenodo_archiving(self):
        commit = self.push_stable()
        gh, zenodo = FakeGitHub(), FakeZenodo(available_after=2)
        self.full_release(gh, zenodo, self.config, commit)

        state = gh.state_for("v2.0.0")
        self.assertEqual(state["version"], "2.0.0")
        self.assertEqual(state["doi"], "10.5281/zenodo.200")
        self.assertEqual(state["conceptDoi"], "10.5281/zenodo.100")
        self.assertTrue(state["imagesComplete"])
        self.assertEqual(gh.tag_refs["v2.0.0"], commit)
        self.assertFalse(gh.release_for("v2.0.0")["draft"])
        self.assertEqual(gh.release_for("v2.0.0")["name"], "OxO2 software 2.0.0")
        self.assertIn("Cite this software release: https://doi.org/10.5281/zenodo.200",
                      gh.release_for("v2.0.0")["body"])
        self.assertNotIn("Container images are being prepared", gh.release_for("v2.0.0")["body"])
        self.assertGreaterEqual(zenodo.calls, 3)

        manifest = json.loads(gh.assets[gh.release_for("v2.0.0")["id"]]["release-manifest.json"])
        self.assertEqual(set(manifest["images"]), set(release.COMPONENTS))
        self.assertTrue(manifest["images"]["nextflow"].startswith("ghcr.io/ebispot/oxo2-nextflow@sha256:1"))
        self.assertEqual(manifest["current"]["version"], "2.0.0")
        self.assertEqual(manifest["zenodoRecordId"], 200)
        for target in release.BUNDLE_TARGETS:
            bundle = json.loads((self.repo / target).read_text())
            self.assertEqual(bundle["current"]["version"], "2.0.0")
            self.assertEqual(bundle["conceptDoi"], "10.5281/zenodo.100")
            self.assertEqual([entry["version"] for entry in bundle["releases"]], ["2.0.0"])

        archive_name = "oxo2-v2.0.0-source.tar.gz"
        with tarfile.open(fileobj=io.BytesIO(gh.assets[gh.release_for("v2.0.0")["id"]][archive_name])) as tar:
            names = tar.getnames()
            self.assertIn("oxo2-v2.0.0/RELEASE_NOTES.md", names)
            self.assertIn("oxo2-v2.0.0/release-manifest.json", names)
            self.assertIn("oxo2-v2.0.0/oxo2-dataload/oxo2-release.json", names)
            zenodo_json = json.loads(tar.extractfile("oxo2-v2.0.0/.zenodo.json").read())
            self.assertEqual(zenodo_json["doi"], "10.5281/zenodo.200")
            self.assertEqual(zenodo_json["version"], "2.0.0")
            self.assertEqual(zenodo_json["publication_date"], "2026-10-23")

    def test_release_without_archiving_has_no_doi_and_never_asks_zenodo(self):
        commit = self.push_stable()
        gh, zenodo = FakeGitHub(), FakeZenodo()
        self.full_release(gh, zenodo, self.config_none, commit)
        state = gh.state_for("v2.0.0")
        self.assertEqual(zenodo.calls, 0)
        self.assertNotIn("doi", state)
        self.assertIsNone(state["conceptDoi"])
        self.assertTrue(state["imagesComplete"])
        self.assertNotIn("Cite this software release", gh.release_for("v2.0.0")["body"])
        manifest = json.loads(gh.assets[gh.release_for("v2.0.0")["id"]]["release-manifest.json"])
        self.assertEqual(manifest["archiving"], "none")
        self.assertNotIn("zenodoRecordId", manifest)

    def test_second_release_uses_pr_label_and_reviewed_notes(self):
        first = self.push_stable()
        gh = FakeGitHub()
        self.full_release(gh, FakeZenodo(), self.config, first)
        run_git("tag", "v2.0.0", first, cwd=self.repo)

        self.commit("feat(frontend): show the software version in the footer")
        second = self.push_stable()
        gh.pulls_by_commit[second] = [pull_request(
            second, labels=["release:minor"],
            body="## Release notes\n\n### Added\n- Version in the footer\n")]
        output_two = Path(self.tmp.name) / "out2"
        release.prepare(gh, self.config, second, output_two)
        release.archive(gh, FakeZenodo(doi="10.5281/zenodo.201", record_id=201), self.config, output_two)
        state = gh.state_for("v2.1.0")
        self.assertEqual(state["notes"], "### Added\n- Version in the footer")
        self.assertEqual(state["conceptDoi"], "10.5281/zenodo.100")
        bundle = json.loads((self.repo / release.BUNDLE_TARGETS[1]).read_text())
        self.assertEqual([entry["version"] for entry in bundle["releases"]], ["2.1.0", "2.0.0"])
        self.assertEqual(bundle["current"]["doi"], "10.5281/zenodo.201")

    def test_second_release_falls_back_to_generated_notes(self):
        first = self.push_stable()
        gh = FakeGitHub()
        self.full_release(gh, FakeZenodo(), self.config, first)
        run_git("tag", "v2.0.0", first, cwd=self.repo)
        self.commit("fix(dataload): give the HPC Solr a real heap")
        second = self.push_stable()
        release.prepare(gh, self.config, second, Path(self.tmp.name) / "out2")
        state = gh.state_for("v2.0.1")
        self.assertIn("### Fixed\n- dataload: give the HPC Solr a real heap", state["notes"])
        self.assertIn("compare/v2.0.0...v2.0.1", state["notes"])

    def test_concept_doi_must_match_the_previous_release(self):
        first = self.push_stable()
        gh = FakeGitHub()
        self.full_release(gh, FakeZenodo(), self.config, first)
        self.commit("fix: something")
        second = self.push_stable()
        output_two = Path(self.tmp.name) / "out2"
        release.prepare(gh, self.config, second, output_two)
        with self.assertRaises(ValueError):
            release.archive(gh, FakeZenodo(concept="10.5281/zenodo.999"), self.config, output_two)

    # --- guards --------------------------------------------------------------------------------

    def test_prepare_refuses_a_commit_that_is_not_on_stable(self):
        self.push_stable()
        head = self.commit("feat: not yet merged")
        with self.assertRaisesRegex(ValueError, "must be on stable"):
            release.prepare(FakeGitHub(), self.config, head, self.output)

    def test_prepare_refuses_a_mismatched_checkout(self):
        first = self.push_stable()
        self.commit("feat: moved on")
        self.push_stable()
        with self.assertRaisesRegex(ValueError, "Checkout must match"):
            release.prepare(FakeGitHub(), self.config, first, self.output)

    def test_unfinished_release_blocks_the_next_commit(self):
        first = self.push_stable()
        gh = FakeGitHub()
        release.prepare(gh, self.config, first, self.output)
        release.archive(gh, FakeZenodo(), self.config, self.output)  # images never built
        self.commit("fix: next")
        second = self.push_stable()
        with self.assertRaisesRegex(ValueError, "unfinished"):
            release.prepare(gh, self.config, second, Path(self.tmp.name) / "out2")

    def test_rerun_resumes_the_same_release_and_skips_built_images(self):
        commit = self.push_stable()
        gh = FakeGitHub()
        release.prepare(gh, self.config, commit, self.output)
        release.archive(gh, FakeZenodo(), self.config, self.output)
        release.remember_image(gh, self.output, "backend", self.metadata_file("backend", "a"))
        release.remember_image(gh, self.output, "solr", self.metadata_file("solr", "b"))
        release_count = len(gh.releases)

        # The runner is lost; a re-run starts from an empty output directory.
        output_two = Path(self.tmp.name) / "rerun"
        context = release.prepare(gh, self.config, commit, output_two)
        self.assertEqual(len(gh.releases), release_count)
        self.assertEqual(context["state"]["version"], "2.0.0")
        self.assertFalse(context["complete"])
        self.assertTrue((output_two / "image-backend.json").exists())
        self.assertTrue((output_two / "image-solr.json").exists())
        self.assertFalse((output_two / "image-frontend.json").exists())
        release.archive(gh, FakeZenodo(), self.config, output_two)  # idempotent: already published
        for component in ("dataload", "nextflow", "frontend"):
            release.remember_image(gh, output_two, component, self.metadata_file(component, "c"))
        release.finish(gh, output_two)
        self.assertTrue(gh.state_for("v2.0.0")["imagesComplete"])
        self.assertEqual(release.prepare(gh, self.config, commit, Path(self.tmp.name) / "again")["complete"], True)

    def test_images_build_in_separate_jobs_in_any_order(self):
        commit = self.push_stable()
        gh = FakeGitHub()
        release.prepare(gh, self.config, commit, self.output)
        # A build runner must never bake the development placeholder manifest into an image.
        with self.assertRaisesRegex(ValueError, "not been archived"):
            release.prepare_image(gh, self.config, commit, Path(self.tmp.name) / "early", "backend")
        release.archive(gh, FakeZenodo(), self.config, self.output)

        # Each component is built on its own runner from an empty output directory, in whatever
        # order the jobs happen to run; the release assets are the only shared state.
        for index, component in enumerate(reversed(release.COMPONENTS)):
            job_output = Path(self.tmp.name) / f"job-{component}"
            self.assertFalse(release.prepare_image(gh, self.config, commit, job_output, component))
            for target in release.BUNDLE_TARGETS:
                bundle = json.loads((self.repo / target).read_text())
                self.assertEqual(bundle["current"]["version"], "2.0.0")
            release.remember_image(gh, job_output, component, self.metadata_file(component, str(index)))
            # A re-run of this job finds its image recorded and builds nothing.
            retry_output = Path(self.tmp.name) / f"retry-{component}"
            self.assertTrue(release.prepare_image(gh, self.config, commit, retry_output, component))
            self.assertTrue((retry_output / f"image-{component}.json").exists())

        # The finish job recovers the five digests from the release, not from any build runner.
        finish_output = Path(self.tmp.name) / "finish"
        release.prepare(gh, self.config, commit, finish_output)
        release.finish(gh, finish_output)
        manifest = json.loads(gh.assets[gh.release_for("v2.0.0")["id"]]["release-manifest.json"])
        self.assertEqual(set(manifest["images"]), set(release.COMPONENTS))
        self.assertTrue(manifest["images"]["solr"].startswith("ghcr.io/ebispot/oxo2-solr@sha256:0"))
        self.assertTrue(manifest["images"]["dataload"].startswith("ghcr.io/ebispot/oxo2-dataload@sha256:4"))
        self.assertTrue(gh.state_for("v2.0.0")["imagesComplete"])

    def test_zenodo_timeout_tells_the_operator_to_rerun(self):
        commit = self.push_stable()
        gh = FakeGitHub()
        release.prepare(gh, self.config, commit, self.output)
        with self.assertRaisesRegex(TimeoutError, "rerun the original workflow"):
            release.archive(gh, FakeZenodo(available_after=10_000), self.config, self.output)
        # The release is already public; the re-run must find it published and keep waiting for
        # the DOI rather than creating another one.
        self.assertFalse(gh.release_for("v2.0.0")["draft"])
        release.archive(gh, FakeZenodo(), self.config, self.output)
        self.assertEqual(gh.state_for("v2.0.0")["doi"], "10.5281/zenodo.200")

    def test_release_order_follows_stable_history(self):
        self.commit("feat: second commit on dev")
        first = self.push_stable()
        gh = FakeGitHub()
        self.full_release(gh, FakeZenodo(), self.config, first)
        # A commit that is NOT a descendant of the released one (a sibling branch of stable).
        run_git("checkout", "-q", "-b", "sibling", f"{first}~1", cwd=self.repo)
        sibling = self.commit("fix: on a sibling line")
        run_git("update-ref", "refs/remotes/origin/stable", sibling, cwd=self.repo)
        with self.assertRaisesRegex(ValueError, "Release order differs"):
            release.prepare(gh, self.config, sibling, Path(self.tmp.name) / "out2")

    def test_existing_tag_without_release_is_refused(self):
        commit = self.push_stable()
        run_git("tag", "v2.0.0", commit, cwd=self.repo)
        with self.assertRaisesRegex(ValueError, "already exists"):
            release.prepare(FakeGitHub(), self.config, commit, self.output)

    def test_invalid_image_digest_is_refused(self):
        commit = self.push_stable()
        gh = FakeGitHub()
        release.prepare(gh, self.config, commit, self.output)
        bad = Path(self.tmp.name) / "bad.json"
        bad.write_text(json.dumps({"containerimage.digest": "sha256:short"}))
        with self.assertRaisesRegex(ValueError, "Invalid image digest"):
            release.remember_image(gh, self.output, "backend", bad)

    def test_config_rejects_unknown_archiving(self):
        bad = Path(self.tmp.name) / "config.json"
        bad.write_text(json.dumps({"firstVersion": "2.0.0", "archiving": "zenodo-api"}))
        with self.assertRaises(ValueError):
            release.load_config(bad)

    def test_checked_in_config_is_valid(self):
        here = Path(__file__).resolve().parent
        config = release.load_config(here / "config.json")
        release.version_tuple(config["firstVersion"])
        self.assertIn(config["archiving"], ("github", "none"))

    def test_checked_in_bundles_are_the_development_placeholder(self):
        root = Path(__file__).resolve().parents[2]
        for target in release.BUNDLE_TARGETS:
            with self.subTest(target=str(target)):
                self.assertEqual(json.loads((root / target).read_text()), release.EMPTY_BUNDLE)


if __name__ == "__main__":
    unittest.main()
