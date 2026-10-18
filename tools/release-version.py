#!/usr/bin/env python3
"""Choose the next Android release version, reusing a tag when a run is retried."""

import re
import subprocess


def parse_version(tag):
    match = re.fullmatch(r"v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)", tag)
    if match is None:
        return None
    major, minor, patch = map(int, match.groups())
    if minor > 99 or patch > 99:
        raise ValueError(f"Tag {tag} exceeds the Android version component limits")
    code = major * 10000 + minor * 100 + patch
    if code >= 2100000000:
        raise ValueError(f"Tag {tag} reaches the Android version code limit")
    return code


def choose_version(tags, head_tags):
    versions = [code for tag in tags if (code := parse_version(tag)) is not None]
    existing = [code for tag in head_tags if (code := parse_version(tag)) is not None]
    code = max(existing) if existing else max(versions) + 1 if versions else 100
    if code >= 2100000000:
        raise ValueError("No Android version codes remain")
    return f"{code // 10000}.{code // 100 % 100}.{code % 100}"


def git_tags(*args):
    return subprocess.check_output(["git", "tag", *args], text=True).splitlines()


if __name__ == "__main__":
    print(choose_version(git_tags("--list"), git_tags("--points-at", "HEAD")))
