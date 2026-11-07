#!/usr/bin/env python3
"""Fetch only the two hash-pinned trial dependencies; never install into a live pack."""
import hashlib
import json
from pathlib import Path
import urllib.request
import subprocess

trial = Path(__file__).resolve().parent
project = trial.parents[2]
destination = project / "build/handcrafted-kitchen/dependencies"
destination.mkdir(parents=True, exist_ok=True)
for dependency in json.loads((trial / "dependencies.json").read_text()):
    target = destination / dependency["filename"]
    if not target.is_file() or hashlib.sha512(target.read_bytes()).hexdigest() != dependency["sha512"]:
        data = urllib.request.urlopen(dependency["url"], timeout=60).read()
        if hashlib.sha512(data).hexdigest() != dependency["sha512"]:
            raise ValueError("Dependency checksum mismatch: " + dependency["name"])
        target.write_bytes(data)
    print(dependency["filename"])

# Keep the historical before/after authoring trial independent of its promoted template.
baseline = project / "build/handcrafted-kitchen/baseline/kitchen.nbt"
baseline.parent.mkdir(parents=True, exist_ok=True)
data = subprocess.check_output(["git", "-C", str(project), "show",
    "494356929:pale-mirror/pale-mirror-frontier/src/main/resources/io/farfrontier/palemirror/frontier/v3/buildings/medieval_spruce/kitchen.nbt"])
if hashlib.sha256(data).hexdigest() != "dddcd84305685843dbf7be4c724ef4c2aacde453e450cbf89f2b705c3983ffcb":
    raise ValueError("Historical kitchen baseline differs")
baseline.write_bytes(data)
print("Historical kitchen baseline: " + str(baseline))
