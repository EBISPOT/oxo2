#!/usr/bin/env python3
"""Cut an OxO2 software release from a push to the stable branch (ADR-0052).

Run by .github/workflows/release.yml as four idempotent commands, in order:

  prepare         choose version + notes, create (or recover) the draft GitHub release
  archive         tag the tested commit, publish the release, wait for the Zenodo DOI
  remember-image  record one component image's digest as a release asset (called per image)
  finish          upload the manifest + enriched source tarball, mark images complete

Every step reads and writes a JSON state comment hidden in the GitHub release body, so a re-run
of the original workflow run resumes where it stopped and never makes a second release for the
same commit. Zenodo is driven only by GitHub's native integration (publishing the release is the
trigger); this script reads Zenodo's public records API without a token to find the DOI.

Configuration lives in .github/release/config.json:
  firstVersion         the version of the very first release (no bump label applies to it)
  archiving            "github" (wait for the Zenodo DOI) or "none" (publish without a DOI)
  archiveWaitSeconds   how long to wait for Zenodo, archivePollSeconds the poll interval
"""
import argparse
import gzip
import io
import json
import os
import re
import subprocess
import tarfile
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlencode, urlparse
from urllib.request import HTTPRedirectHandler, Request, build_opener


class SafeRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        if urlparse(new_url).scheme != "https":
            raise ValueError("Refusing non-HTTPS redirect")
        redirected = super().redirect_request(request, response, code, message, headers, new_url)
        if redirected and urlparse(request.full_url).netloc != urlparse(new_url).netloc:
            redirected.remove_header("Authorization")
        return redirected


HTTP = build_opener(SafeRedirect())

SEMVER = re.compile(r"^v?(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$")
STATE_MARKER = "<!-- oxo2-release-state:"
BUMP_LABELS = ("release:patch", "release:minor", "release:major")
# One entry per image CI publishes; the GHCR repository is ghcr.io/ebispot/oxo2-<component>.
COMPONENTS = ("dataload", "nextflow", "backend", "frontend", "solr")
# Where the release manifest is bundled so the running services can report their own version
# without a network call: the backend serves it at /api/v2/release, the frontend footer reads it,
# and the dataload images copy it next to solr-data.tar.gz when they archive a data release.
BUNDLE_TARGETS = (
    Path("oxo2-backend/src/main/resources/oxo2-release.json"),
    Path("oxo2-frontend/public/oxo2-release.json"),
    Path("oxo2-dataload/oxo2-release.json"),
)
EMPTY_BUNDLE = {"current": None, "conceptDoi": None, "releases": []}


# --- Versions and notes --------------------------------------------------------------------------

def version_tuple(version):
    match = SEMVER.fullmatch(version)
    if not match:
        raise ValueError(f"Invalid release version: {version}")
    return tuple(map(int, match.groups()))


def chosen_bump(labels):
    bumps = {label.removeprefix("release:") for label in labels if label in BUMP_LABELS}
    if len(bumps) > 1:
        raise ValueError("Select only one release:patch/minor/major label")
    return next(iter(bumps), "patch")


def next_version(previous, labels):
    bump = chosen_bump(labels)
    major, minor, patch = version_tuple(previous)
    return {"major": f"{major + 1}.0.0", "minor": f"{major}.{minor + 1}.0",
            "patch": f"{major}.{minor}.{patch + 1}"}[bump]


def choose_version(config, previous_versions, labels):
    """The first release is config['firstVersion'] whatever the label; later ones bump the max."""
    chosen_bump(labels)  # Two bump labels are a mistake even on the first release.
    if not previous_versions:
        version_tuple(config["firstVersion"])
        return config["firstVersion"]
    return next_version(max(previous_versions, key=version_tuple), labels)


def reviewed_notes(body):
    """The '## Release notes' section of the merged PR, or '' if it was left as the template."""
    body = re.sub(r"<!--.*?-->", "", body or "", flags=re.S)
    match = re.search(r"^## Release notes\s*\n(.*?)(?=^## |\Z)", body, re.M | re.S)
    if not match:
        return ""
    notes = match.group(1).strip()
    content = re.sub(r"^#+ .*?$", "", notes, flags=re.M).strip()
    return notes if content else ""


CONVENTIONAL = re.compile(r"^(?P<type>[a-z]+)(\((?P<scope>[^)]*)\))?(?P<breaking>!)?:\s*(?P<subject>.+)$")
SECTION_ORDER = ("Breaking changes", "Added", "Changed", "Fixed")
TYPE_SECTIONS = {"feat": "Added", "fix": "Fixed"}
DEPENDENCY_UPDATE = "dependency update"


def classify_commit(subject):
    """Map one commit subject to (section, line text); None for merges and empty subjects."""
    subject = subject.strip()
    if not subject or subject.startswith("Merge "):
        return None
    match = CONVENTIONAL.match(subject)
    if not match:
        return ("Changed", subject)
    scope = match["scope"] or ""
    if match["type"] == "chore" and scope.startswith("deps"):
        return (DEPENDENCY_UPDATE, None)
    section = "Breaking changes" if match["breaking"] else TYPE_SECTIONS.get(match["type"], "Changed")
    text = f"{scope}: {match['subject']}" if scope else match["subject"]
    return (section, text)


def generated_notes(repo, previous_tag, tag, commit, previous_commit=None):
    """Release notes from the conventional-commit subjects since the previous release.

    OxO2 changes mostly land as direct commits rather than pull requests, so GitHub's PR-based
    notes generator would be near-empty; the commit log is the record that exists. The log range
    starts at the previous release's commit SHA when known (it is in the release state), so this
    does not depend on the previous tag having been fetched into the checkout.
    """
    if not previous_tag:
        return (f"First numbered OxO2 software release.\n\n"
                f"**Full changelog**: https://github.com/{repo}/commits/{tag}")
    log = git("log", "--no-merges", "--format=%H%x09%s", f"{previous_commit or previous_tag}..{commit}")
    sections = {section: [] for section in SECTION_ORDER}
    dependency_updates = 0
    for line in log.splitlines():
        sha, _, subject = line.partition("\t")
        classified = classify_commit(subject)
        if classified is None:
            continue
        section, text = classified
        if section == DEPENDENCY_UPDATE:
            dependency_updates += 1
            continue
        sections[section].append(f"- {text} ({sha[:7]})")
    if dependency_updates:
        plural = "s" if dependency_updates != 1 else ""
        sections["Changed"].append(f"- {dependency_updates} dependency update{plural}")
    parts = ["## What's changed"]
    for section in SECTION_ORDER:
        if sections[section]:
            parts.append(f"### {section}\n" + "\n".join(sections[section]))
    if len(parts) == 1:
        parts.append("No code changes since the previous release.")
    parts.append(f"**Full changelog**: https://github.com/{repo}/compare/{previous_tag}...{tag}")
    return "\n\n".join(parts)


# --- Release state ------------------------------------------------------------------------------

def state_body(state):
    encoded = json.dumps(state, sort_keys=True).replace("<", "\\u003c").replace(">", "\\u003e")
    body = state["notes"]
    if state.get("doi"):
        body += f"\n\nCite this software release: https://doi.org/{state['doi']}"
    if "imagesComplete" in state and not state["imagesComplete"]:
        body += "\n\nContainer images are being prepared. Use them after the release workflow completes."
    return body + "\n\n" + STATE_MARKER + encoded + " -->"


def parse_state(release):
    body = release.get("body") or ""
    if STATE_MARKER not in body:
        return None
    return json.loads(body.rsplit(STATE_MARKER, 1)[1].split(" -->", 1)[0])


# --- Providers ----------------------------------------------------------------------------------

class ApiError(RuntimeError):
    def __init__(self, method, path, status):
        self.status = status
        super().__init__(f"API {method} {path} failed with HTTP {status}")


class Api:
    def __init__(self, base, token=None):
        self.base, self.token = base.rstrip("/"), token

    def request(self, path, method="GET", data=None, raw=None, content_type="application/json"):
        url = path if path.startswith("https://") else self.base + path
        # Never forward the token to a host other than the configured API host.
        if urlparse(url).netloc != urlparse(self.base).netloc:
            raise ValueError("Refusing cross-host authenticated request")
        headers = {"Accept": "application/json", "User-Agent": "OxO2-release-workflow"}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        if data is not None:
            raw = json.dumps(data).encode()
        if raw is not None:
            headers["Content-Type"] = content_type
        try:
            with HTTP.open(Request(url, data=raw, headers=headers, method=method), timeout=120) as res:
                body = res.read()
                if not body:
                    return None
                if "json" in res.headers.get("Content-Type", ""):
                    return json.loads(body)
                return body
        except HTTPError as exc:
            # Response bodies and headers can carry provider detail that does not belong in CI logs.
            raise ApiError(method, urlparse(url).path, exc.code) from None


class PublicZenodo(Api):
    """Public record reads only; GitHub's native Zenodo integration owns archiving."""

    def __init__(self):
        super().__init__("https://zenodo.org/api")

    def request(self, path, method="GET", **kwargs):
        if method != "GET" or not path.startswith("/records/"):
            raise ValueError("Zenodo integration only permits public record GET requests")
        return super().request(path, method, **kwargs)

    def find(self, state):
        tag_url = state["releaseUrl"].replace("/releases/tag/", "/tree/")
        query = (f"related.identifier:{json.dumps(tag_url)} OR "
                 f"related.identifier:{json.dumps(state['releaseUrl'])}")
        records, page = [], 1
        while True:
            result = self.request("/records/?" + urlencode(
                {"q": query, "all_versions": "true", "size": 25, "page": page}))
            rows = result["hits"]["hits"]
            records.extend(rows)
            if len(rows) < 25:
                break
            page += 1
            if page > 20:
                raise ValueError("Too many Zenodo records match the exact release URL")
        matches = []
        for record in records:
            metadata = record["metadata"]
            urls = {item["identifier"] for item in (metadata.get("related_identifiers") or [])}
            if not urls.intersection((tag_url, state["releaseUrl"])):
                continue
            if metadata.get("resource_type", {}).get("type") != "software":
                continue
            try:
                same_version = version_tuple(metadata.get("version") or "") == version_tuple(state["version"])
            except ValueError:
                same_version = False
            if same_version:
                matches.append(record)
        if len(matches) > 1:
            raise ValueError("Multiple software DOIs match this GitHub release; inspect Zenodo before continuing")
        if not matches:
            return None
        record = matches[0]
        for key in ("doi", "conceptdoi"):
            if not re.fullmatch(r"10\.5281/zenodo\.[0-9]+", record.get(key, "")):
                raise ValueError("Zenodo did not return a valid software DOI")
        if state.get("conceptDoi") and record["conceptdoi"] != state["conceptDoi"]:
            raise ValueError("Zenodo software DOI series changed between releases")
        if not record.get("files"):
            raise ValueError("Zenodo software record has no source archive")
        return record


class GitHub(Api):
    def __init__(self, repo, token):
        super().__init__("https://api.github.com", token)
        self.repo, self.root = repo, f"/repos/{repo}"

    def pages(self, path):
        result, page = [], 1
        while True:
            rows = self.request(f"{self.root}{path}?per_page=100&page={page}")
            result.extend(rows)
            if len(rows) < 100:
                return result
            page += 1

    def asset(self, release_id, name):
        assets = self.pages(f"/releases/{release_id}/assets")
        found = next((asset for asset in assets if asset["name"] == name), None)
        if found is None:
            return None
        if urlparse(found["url"]).netloc != "api.github.com":
            raise ValueError("Refusing cross-host authenticated asset request")
        req = Request(found["url"], headers={"Authorization": f"Bearer {self.token}",
                                             "Accept": "application/octet-stream",
                                             "User-Agent": "OxO2-release-workflow"})
        with HTTP.open(req, timeout=120) as res:
            return res.read()

    def upload(self, release_id, name, data, content_type="application/json"):
        existing = self.asset(release_id, name)
        if existing is not None:
            if existing != data:
                raise ValueError(f"Existing release asset differs: {name}")
            return
        uploader = Api("https://uploads.github.com", self.token)
        uploader.request(f"/repos/{self.repo}/releases/{release_id}/assets?name={quote(name)}",
                         "POST", raw=data, content_type=content_type)

    def save_state(self, release, state):
        return self.request(f"{self.root}/releases/{release['id']}", "PATCH",
                            {"body": state_body(state)})

    def image_reference(self, component, digest):
        return f"ghcr.io/{self.repo.lower()}-{component}@{digest}"


# --- Helpers ------------------------------------------------------------------------------------

def json_bytes(data):
    return (json.dumps(data, indent=2, sort_keys=True) + "\n").encode()


def git(*args):
    return subprocess.check_output(["git", *args], text=True).strip()


def is_ancestor(ancestor, descendant):
    return subprocess.run(["git", "merge-base", "--is-ancestor", ancestor, descendant],
                          capture_output=True).returncode == 0


def release_history(releases, current_state):
    history = []
    for release in releases:
        state = parse_state(release)
        if release["draft"] or release.get("prerelease") or not state:
            continue
        if state["commit"] == current_state["commit"]:
            continue
        # A newer commit must not be released before an older stable commit.
        if not is_ancestor(state["commit"], current_state["commit"]):
            raise ValueError("Release order differs from stable history; retry earlier releases first")
        history.append(public_entry(state))
    return sorted(history, key=lambda entry: version_tuple(entry["version"]), reverse=True)


def public_entry(state):
    return {key: state.get(key) for key in ("version", "commit", "releasedAt", "notes", "doi", "releaseUrl")}


def save_context(output, context):
    output.mkdir(parents=True, exist_ok=True)
    (output / "context.json").write_bytes(json_bytes(context))


def load_context(output):
    return json.loads((output / "context.json").read_text())


def restore_assets(gh, output, context):
    for component in COMPONENTS:
        saved = gh.asset(context["releaseId"], f"image-{component}.json")
        if saved:
            (output / f"image-{component}.json").write_bytes(saved)
    if context["complete"]:
        (output / "release-manifest.json").write_bytes(gh.asset(context["releaseId"], "release-manifest.json"))


def write_bundle(bundle):
    for target in BUNDLE_TARGETS:
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(json_bytes(bundle))


# --- Commands -----------------------------------------------------------------------------------

def prepare(gh, config, commit, output):
    if not re.fullmatch(r"[0-9a-f]{40}", commit or ""):
        raise ValueError("A full merged commit SHA is required")
    if git("rev-parse", "HEAD") != commit:
        raise ValueError("Checkout must match the release commit")
    if not is_ancestor(commit, "origin/stable"):
        raise ValueError("Release commit must be on stable")
    releases = gh.pages("/releases")
    managed = [(release, parse_state(release)) for release in releases]
    managed = [(release, state) for release, state in managed if state]
    match = next(((release, state) for release, state in managed if state["commit"] == commit), None)
    if match:
        release, state = match
    else:
        if any(release["draft"] or not state.get("imagesComplete") for release, state in managed):
            raise ValueError("An earlier release is unfinished; retry it before releasing another commit")
        release_history(releases, {"commit": commit})
        versions = [release["tag_name"].removeprefix("v") for release in releases
                    if SEMVER.fullmatch(release["tag_name"])]
        prs = gh.request(f"{gh.root}/commits/{commit}/pulls") or []
        prs = [pr for pr in prs if pr.get("merged_at") and pr["base"]["ref"] == "stable"
               and pr.get("merge_commit_sha") == commit]
        labels = [label["name"] for pr in prs for label in pr["labels"]]
        version = choose_version(config, versions, labels)
        tag = "v" + version
        if tag in git("tag", "--list").splitlines():
            raise ValueError(f"Tag {tag} already exists without a managed release")
        previous_state = max(managed, key=lambda pair: version_tuple(pair[1]["version"]))[1] if managed else {}
        notes = "\n\n".join(filter(None, (reviewed_notes(pr.get("body")) for pr in prs)))
        if not notes:
            previous_tag = "v" + max(versions, key=version_tuple) if versions else None
            notes = generated_notes(gh.repo, previous_tag, tag, commit, previous_state.get("commit"))
        state = {"archiving": config.get("archiving", "github"), "commit": commit, "version": version,
                 "notes": notes,
                 "releasedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
                 "releaseUrl": f"https://github.com/{gh.repo}/releases/tag/{tag}",
                 "conceptDoi": previous_state.get("conceptDoi"), "imagesComplete": False}
        release = gh.request(f"{gh.root}/releases", "POST", {
            "tag_name": tag, "target_commitish": commit, "name": f"OxO2 software {version}",
            "body": state_body(state), "draft": True, "prerelease": False})
    context = {"releaseId": release["id"], "state": state, "complete": state["imagesComplete"]}
    saved = gh.asset(release["id"], "release-history.json")
    if saved:
        context["bundle"] = json.loads(saved)
        write_bundle(context["bundle"])
    save_context(output, context)
    restore_assets(gh, output, context)
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as handle:
            handle.write(f"version={state['version']}\ntag=v{state['version']}\n")
            handle.write(f"complete={str(context['complete']).lower()}\n")
    return context


def ensure_tag(gh, state):
    tag = "v" + state["version"]
    refs = gh.request(f"{gh.root}/git/matching-refs/tags/{tag}")
    exact = next((ref for ref in refs if ref["ref"] == "refs/tags/" + tag), None)
    if exact:
        if exact["object"]["type"] != "commit" or exact["object"]["sha"] != state["commit"]:
            raise ValueError("Release tag no longer matches the tested commit")
    else:
        gh.request(f"{gh.root}/git/refs", "POST", {"ref": "refs/tags/" + tag, "sha": state["commit"]})


def wait_for_doi(zenodo, config, state):
    deadline = time.monotonic() + config["archiveWaitSeconds"]
    while True:
        try:
            record = zenodo.find(state)
        except ApiError as exc:
            if exc.status not in (429, 500, 502, 503, 504):
                raise
            record = None
        except (URLError, TimeoutError):
            record = None
        if record:
            return record
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError("Zenodo has not archived this release yet. Check the repository's Zenodo "
                               "release errors, then rerun the original workflow; do not create another release.")
        time.sleep(min(config["archivePollSeconds"], remaining))


def archive(gh, zenodo, config, output):
    context = load_context(output)
    state, release_id = context["state"], context["releaseId"]
    if context["complete"]:
        return
    ensure_tag(gh, state)
    release = gh.request(f"{gh.root}/releases/{release_id}")
    if release["draft"]:
        # Publishing is the only archival trigger. Never call Zenodo's deposition API, and never
        # republish to retry DOI discovery.
        release = gh.request(f"{gh.root}/releases/{release_id}", "PATCH", {
            "draft": False, "make_latest": "true", "body": state_body(state)})
    state["releasedAt"] = release["published_at"]
    if state["archiving"] == "github" and "doi" not in state:
        record = wait_for_doi(zenodo, config, state)
        state.update({"doi": record["doi"], "conceptDoi": record["conceptdoi"],
                      "zenodoRecordId": record["id"]})
    gh.save_state(release, state)
    ensure_tag(gh, state)
    saved = gh.asset(release_id, "release-history.json")
    if saved:
        bundle = json.loads(saved)
    else:
        history = release_history(gh.pages("/releases"), state)
        bundle = {"current": public_entry(state), "conceptDoi": state.get("conceptDoi"),
                  "releases": [public_entry(state), *history]}
        gh.upload(release_id, "release-history.json", json_bytes(bundle))
    context["bundle"] = bundle
    save_context(output, context)
    write_bundle(bundle)
    restore_assets(gh, output, context)


def remember_image(gh, output, component, metadata):
    context = load_context(output)
    digest = json.loads(metadata.read_text())["containerimage.digest"]
    if not re.fullmatch(r"sha256:[0-9a-f]{64}", digest):
        raise ValueError("Invalid image digest")
    data = {"component": component, "digest": digest, "image": gh.image_reference(component, digest)}
    body = json_bytes(data)
    gh.upload(context["releaseId"], f"image-{component}.json", body)
    (output / f"image-{component}.json").write_bytes(body)


def source_archive(state, manifest):
    prefix = f"oxo2-v{state['version']}/"
    source = subprocess.check_output(["git", "archive", "--format=tar", f"--prefix={prefix}", state["commit"]])
    metadata = json.loads(git("show", state["commit"] + ":.zenodo.json"))
    metadata.update({"version": state["version"], "publication_date": state["releasedAt"][:10]})
    if state.get("doi"):
        metadata["doi"] = state["doi"]
    bundle = json_bytes({key: manifest[key] for key in ("current", "conceptDoi", "releases")})
    additions = {".zenodo.json": json_bytes(metadata), "RELEASE_NOTES.md": state["notes"].encode(),
                 "release-manifest.json": json_bytes(manifest)}
    for target in BUNDLE_TARGETS:
        additions[str(target)] = bundle
    result = io.BytesIO()
    # A fixed gzip timestamp makes a re-created archive byte-identical after a retry.
    with gzip.GzipFile(fileobj=result, mode="wb", mtime=0) as compressed:
        with tarfile.open(fileobj=compressed, mode="w") as target:
            with tarfile.open(fileobj=io.BytesIO(source), mode="r:") as original:
                for item in original:
                    if item.name.removeprefix(prefix) in additions:
                        continue
                    target.addfile(item, original.extractfile(item) if item.isfile() else None)
            released = datetime.fromisoformat(state["releasedAt"].replace("Z", "+00:00"))
            for name, data in additions.items():
                item = tarfile.TarInfo(prefix + name)
                item.size, item.mtime = len(data), int(released.timestamp())
                target.addfile(item, io.BytesIO(data))
    return result.getvalue()


def finish(gh, output):
    context = load_context(output)
    state, release_id = context["state"], context["releaseId"]
    if context["complete"]:
        return
    ensure_tag(gh, state)
    images = {component: json.loads((output / f"image-{component}.json").read_text())["image"]
              for component in COMPONENTS}
    manifest = {**context["bundle"], "images": images, "archiving": state["archiving"]}
    if state.get("zenodoRecordId"):
        manifest["zenodoRecordId"] = state["zenodoRecordId"]
    gh.upload(release_id, "release-manifest.json", json_bytes(manifest))
    gh.upload(release_id, f"oxo2-v{state['version']}-source.tar.gz", source_archive(state, manifest),
              "application/gzip")
    state["imagesComplete"] = True
    gh.save_state({"id": release_id}, state)
    context["complete"] = True
    save_context(output, context)
    (output / "release-manifest.json").write_bytes(json_bytes(manifest))


def load_config(path=Path(".github/release/config.json")):
    config = json.loads(path.read_text())
    if config.get("archiving", "github") not in ("github", "none"):
        raise ValueError("config archiving must be 'github' or 'none'")
    config.setdefault("archiving", "github")
    return config


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["prepare", "archive", "remember-image", "finish"])
    parser.add_argument("--commit", default=os.environ.get("GITHUB_SHA"))
    parser.add_argument("--output", type=Path, default=Path(".release-output"))
    parser.add_argument("--component", choices=COMPONENTS)
    parser.add_argument("--metadata", type=Path)
    args = parser.parse_args()
    gh = GitHub(os.environ["GITHUB_REPOSITORY"], os.environ["GH_TOKEN"])
    if args.command == "remember-image":
        remember_image(gh, args.output, args.component, args.metadata)
        return
    config = load_config()
    if args.command == "prepare":
        prepare(gh, config, args.commit, args.output)
    elif args.command == "archive":
        archive(gh, PublicZenodo(), config, args.output)
    else:
        finish(gh, args.output)


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        # No tracebacks: they can echo request details and environment into the CI log.
        print(f"Release failed: {type(exc).__name__}: {exc}")
        raise SystemExit(1)
