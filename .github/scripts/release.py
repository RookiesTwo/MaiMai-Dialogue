"""Paired main/editor release automation. Python 3.11+, standard library only."""

import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tomllib
import urllib.error
import urllib.parse
import urllib.request
import zipfile


ROOT = Path(__file__).resolve().parents[2]
DIST = ROOT / "build/release"
PROPERTIES = ("gradle.properties", "editor/gradle.properties")
VERSION = re.compile(r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([0-9A-Za-z.-]+))?(?:\+([0-9A-Za-z.-]+))?")


def git(*args, required=True):
    result = subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True, encoding="utf-8")
    if required and result.returncode:
        raise ValueError(f"git {args[0]} failed: {result.stderr.strip()}")
    return result.stdout.strip() if result.returncode == 0 else None


def properties(text):
    values = {}
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith(("#", "!")) or "=" not in line:
            continue
        key, value = (part.strip() for part in line.split("=", 1))
        if key in values:
            raise ValueError(f"Duplicate property: {key}")
        values[key] = value
    return values


def version_key(value):
    match = VERSION.fullmatch(value)
    if not match:
        raise ValueError(f"Expected a semantic version, got {value!r}")
    major, minor, patch, prerelease, _ = match.groups()
    identifiers = []
    if prerelease is not None:
        for item in prerelease.split("."):
            if not item or item.isdigit() and len(item) > 1 and item.startswith("0"):
                raise ValueError(f"Invalid prerelease version: {value}")
            identifiers.append((0, int(item)) if item.isdigit() else (1, item))
    if match.group(5) and any(not item for item in match.group(5).split(".")):
        raise ValueError(f"Invalid build metadata: {value}")
    return int(major), int(minor), int(patch), prerelease is None, tuple(identifiers)


def configuration():
    main, editor = [properties((ROOT / path).read_text(encoding="utf-8")) for path in PROPERTIES]
    for values in (main, editor):
        version_key(values["mod_version"])
        if not re.fullmatch(r"[a-z][a-z0-9_]{1,63}", values["mod_id"]):
            raise ValueError("Invalid MOD ID")
    if not re.fullmatch(r"[0-9]+(?:\.[0-9]+)+", main["minecraft_version"]):
        raise ValueError("Invalid Minecraft version")
    return main, editor


def release_decision(event_name, ref, event, main_version, editor_version, read_before):
    if event_name != "push" or ref != "refs/heads/master" or event.get("deleted") or event.get("forced"):
        return False, "Build only: releases require a normal push to master."
    before = event.get("before", "")
    if not before or set(before) == {"0"}:
        return False, "Build only: no previous commit to compare."
    if not re.fullmatch(r"[0-9a-f]{40}", before):
        raise ValueError("Invalid push baseline SHA")
    old = [read_before(before, path) for path in PROPERTIES]
    if any(value is None for value in old):
        return False, "Build only: both modules must already exist at the push baseline."
    previous = [properties(value)["mod_version"] for value in old]
    current = [main_version, editor_version]
    if previous == current:
        return False, "Build only: neither MOD version changed."
    for old_version, new_version in zip(previous, current):
        if version_key(new_version) < version_key(old_version):
            raise ValueError(f"Refusing to publish a version downgrade: {old_version} -> {new_version}")
    return True, "Version change detected; publish both JARs after verification."


def read_event():
    path = os.environ.get("GITHUB_EVENT_PATH")
    return json.loads(Path(path).read_text(encoding="utf-8")) if path else {}


def metadata():
    main, editor = configuration()
    commit = git("rev-parse", "HEAD")
    if os.environ.get("GITHUB_SHA", commit) != commit:
        raise ValueError("Checkout does not match the triggering commit")
    release, reason = release_decision(os.environ.get("GITHUB_EVENT_NAME", ""), os.environ.get("GITHUB_REF", ""),
                                       read_event(), main["mod_version"], editor["mod_version"],
                                       lambda sha, path: git("show", f"{sha}:{path}", required=False))
    main_version, editor_version = main["mod_version"], editor["mod_version"]
    mc = main["minecraft_version"]
    result = {
        "commit": commit, "release": release, "reason": reason,
        "main_version": main_version, "editor_version": editor_version,
        "minecraft": mc, "neoforge": main["neo_version"], "modernui": main["modernui_mc_version"],
        "tag": f"v{main_version}-editor-{editor_version}",
        "title": f"MaiMai Dialogue {main_version} · Editor {editor_version}",
        "prerelease": not version_key(main_version)[3] or not version_key(editor_version)[3],
        "main_jar": f'{main["mod_id"]}-neoforge-{mc}-{main_version}.jar',
        "editor_jar": f'{editor["mod_id"]}-neoforge-{mc}-{editor_version}-for-{main_version}.jar',
    }
    DIST.mkdir(parents=True, exist_ok=True)
    (DIST / "build-info.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with open(output, "a", encoding="utf-8") as stream:
            stream.write(f'release={str(release).lower()}\ntag={result["tag"]}\n')
    summary(f'### {result["title"]}\n\n{reason}\n\nCommit: `{commit}`\n')
    return result


def sha256(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def verify_jar(path, values, main, editor=False):
    with zipfile.ZipFile(path) as archive:
        if archive.testzip() is not None:
            raise ValueError(f"Corrupt JAR: {path.name}")
        names = set(archive.namelist())
        info = tomllib.loads(archive.read("META-INF/neoforge.mods.toml").decode("utf-8"))
        mods = info["mods"]
        if len(mods) != 1 or (mods[0]["modId"], mods[0]["version"]) != (values["mod_id"], values["mod_version"]):
            raise ValueError(f"MOD identity/version mismatch: {path.name}")
        deps = {item["modId"]: item for item in info["dependencies"][values["mod_id"]]}
        for dependency, expected in (("minecraft", main["minecraft_version_range"]),
                                     ("neoforge", main["neo_version_range"]),
                                     ("modernui", f'[{main["modernui_mc_version"]},)')):
            if deps[dependency]["versionRange"] != expected or deps[dependency]["type"] != "required":
                raise ValueError(f"Dependency mismatch for {dependency}: {path.name}")
        if editor and (deps[main["mod_id"]]["versionRange"] != f'[{main["mod_version"]}]'
                       or deps[main["mod_id"]]["type"] != "required" or deps[main["mod_id"]]["side"] != "CLIENT"):
            raise ValueError("Editor does not require this exact main MOD version")
        if editor and any(name.startswith("top/rookiestwo/maimai_dialogue/") for name in names):
            raise ValueError("Editor must not embed the main MOD")
        prefix = "editor/src/main/resources/" if editor else "src/main/resources/"
        for resource in git("ls-files", "--", prefix).splitlines():
            if resource[len(prefix):] not in names:
                raise ValueError(f"Missing packaged resource: {resource}")
        for name in names:
            if name.startswith("assets/") and "/lang/" in name and name.endswith(".json"):
                json.loads(archive.read(name))
        for entry in info.get("mixins", []):
            mixin = json.loads(archive.read(entry["config"]))
            for name in mixin.get("client", []) + mixin.get("mixins", []) + mixin.get("server", []):
                target = (mixin["package"] + "." + name).replace(".", "/") + ".class"
                if target not in names:
                    raise ValueError(f"Missing mixin class: {target}")


def package():
    info = json.loads((DIST / "build-info.json").read_text(encoding="utf-8"))
    main, editor = configuration()
    if (info["commit"], info["main_version"], info["editor_version"]) != (git("rev-parse", "HEAD"), main["mod_version"], editor["mod_version"]):
        raise ValueError("Stale build metadata")
    artifacts = ((ROOT / "build/libs", main, "main_jar", False), (ROOT / "editor/build/libs", editor, "editor_jar", True))
    files = {}
    for directory, values, field, is_editor in artifacts:
        source = directory / f'{values["mod_id"]}-{values["mod_version"]}.jar'
        verify_jar(source, values, main, is_editor)
        destination = DIST / info[field]
        shutil.copyfile(source, destination)
        files[destination.name] = sha256(destination)
    checksums = DIST / "SHA256SUMS.txt"
    checksums.write_text("".join(f"{digest}  {name}\n" for name, digest in files.items()), encoding="utf-8")
    files[checksums.name] = sha256(checksums)
    info["files"] = files
    (DIST / "build-info.json").write_text(json.dumps(info, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    summary("\nBoth JARs and resource/dependency metadata verified. Local ignored tests and in-game UI were not run in CI.\n")
    return info


class GitHub:
    def __init__(self, repository, token):
        if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository) or not token:
            raise ValueError("Missing GitHub repository/token")
        self.base = f"https://api.github.com/repos/{repository}"
        self.token = token

    def call(self, method, path, data=None, file=None, missing=False):
        url = path if path.startswith("https://uploads.github.com/") else self.base + path
        body = file.read_bytes() if file else json.dumps(data).encode("utf-8") if data is not None else None
        request = urllib.request.Request(url, data=body, method=method, headers={
            "Authorization": f"Bearer {self.token}", "Accept": "application/vnd.github+json",
            "Content-Type": "application/octet-stream" if file else "application/json",
            "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "MaiMai-Dialogue-release",
        })
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                content = response.read()
                return json.loads(content) if content else None
        except urllib.error.HTTPError as error:
            if missing and error.code == 404:
                return None
            raise RuntimeError(f"GitHub {method} failed ({error.code}): {error.read().decode('utf-8', errors='replace')}") from error


def publish_release(info, api):
    tag = info["tag"]
    encoded_tag = urllib.parse.quote(tag, safe="")
    ref = api.call("GET", f"/git/ref/tags/{encoded_tag}", missing=True)
    if ref:
        obj = ref["object"]
        while obj["type"] == "tag":
            obj = api.call("GET", f'/git/tags/{obj["sha"]}')["object"]
        if obj["type"] != "commit" or obj["sha"] != info["commit"]:
            raise ValueError(f"Tag {tag} already points to another commit; bump the version instead")
    release = api.call("GET", f"/releases/tags/{encoded_tag}", missing=True)
    if release is None:
        # The tag endpoint finds published releases; list releases also exposes our drafts.
        page = 1
        while True:
            releases = api.call("GET", f"/releases?per_page=100&page={page}")
            release = next((item for item in releases if item["tag_name"] == tag), None)
            if release is not None or len(releases) < 100:
                break
            page += 1
    marker = f'<!-- maimai-release:{info["commit"]} -->'
    if release:
        if not ref:
            raise ValueError("Existing release has no matching tag")
        if not release["draft"]:
            summary(f'\nAlready published; left unchanged: {release["html_url"]}\n')
            return
        if marker not in (release.get("body") or ""):
            raise ValueError("Refusing to modify a draft not created by this workflow")
    if not ref:
        api.call("POST", "/git/refs", {"ref": f"refs/tags/{tag}", "sha": info["commit"]})
    if not release:
        notes = api.call("POST", "/releases/generate-notes", {"tag_name": tag, "target_commitish": info["commit"]})
        body = (f'{marker}\n\n'
                f'| 组件 | 版本 |\n| --- | --- |\n'
                f'| MaiMai Dialogue | {info["main_version"]} |\n'
                f'| Editor | {info["editor_version"]}（适配主 MOD {info["main_version"]}） |\n'
                f'| Minecraft | {info["minecraft"]} |\n'
                f'| NeoForge 构建版本 | {info["neoforge"]} |\n'
                f'| ModernUI 最低版本 | {info["modernui"]} |\n\n'
                f'Editor 为客户端可选组件，需要同时安装本条 Release 的主 MOD。\n\n'
                f'构建提交：`{info["commit"]}`\n\n'
                + notes["body"])
        release = api.call("POST", "/releases", {"tag_name": tag, "target_commitish": info["commit"],
                           "name": info["title"], "body": body, "draft": True, "prerelease": info["prerelease"]})
    existing = {asset["name"]: asset for asset in api.call("GET", f'/releases/{release["id"]}/assets?per_page=100')}
    for name, digest in info["files"].items():
        asset = existing.get(name)
        if asset and asset.get("digest") == "sha256:" + digest and asset.get("state") == "uploaded":
            continue
        # Only our unpublished draft can have partial/mismatched uploads replaced on a retry.
        if asset:
            api.call("DELETE", f'/releases/assets/{asset["id"]}')
        upload_url = release["upload_url"].split("{", 1)[0] + "?" + urllib.parse.urlencode({"name": name})
        api.call("POST", upload_url, file=DIST / name)
    uploaded = {asset["name"]: asset for asset in api.call("GET", f'/releases/{release["id"]}/assets?per_page=100')}
    for name, digest in info["files"].items():
        asset = uploaded.get(name, {})
        if asset.get("state") != "uploaded" or asset.get("digest") != "sha256:" + digest:
            raise ValueError(f"Release asset upload not verified: {name}")
    # Let GitHub's version/date ordering decide Latest, rather than the completion order of concurrent builds.
    published = api.call("PATCH", f'/releases/{release["id"]}', {"draft": False,
                         "make_latest": "false" if info["prerelease"] else "legacy"})
    summary(f'\nPublished both JARs: {published["html_url"]}\n')


def publish():
    main, editor = configuration()
    info = json.loads((DIST / "build-info.json").read_text(encoding="utf-8"))
    event = read_event()
    allowed, _ = release_decision(os.environ.get("GITHUB_EVENT_NAME", ""), os.environ.get("GITHUB_REF", ""), event,
                                  main["mod_version"], editor["mod_version"],
                                  lambda sha, path: git("show", f"{sha}:{path}", required=False))
    if not allowed or not info["release"] or info["commit"] != os.environ.get("GITHUB_SHA") or info["commit"] != git("rev-parse", "HEAD"):
        raise ValueError("Publishing requires the exact verified version-changing master push")
    if (info["main_version"], info["editor_version"], info["tag"]) != (main["mod_version"], editor["mod_version"], f'v{main["mod_version"]}-editor-{editor["mod_version"]}'):
        raise ValueError("Release versions do not match checkout")
    expected = {info["main_jar"], info["editor_jar"], "SHA256SUMS.txt"}
    if set(info["files"]) != expected or any(Path(name).name != name for name in expected):
        raise ValueError("Unexpected release assets")
    for name, digest in info["files"].items():
        if sha256(DIST / name) != digest:
            raise ValueError(f"Artifact checksum mismatch: {name}")
    publish_release(info, GitHub(os.environ["GITHUB_REPOSITORY"], os.environ["GH_TOKEN"]))


def summary(text):
    print(text)
    if path := os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(path, "a", encoding="utf-8") as stream:
            stream.write(text + "\n")


if __name__ == "__main__":
    commands = {"metadata": metadata, "package": package, "publish": publish}
    if len(sys.argv) != 2 or sys.argv[1] not in commands:
        raise SystemExit("Usage: release.py metadata|package|publish")
    commands[sys.argv[1]]()
