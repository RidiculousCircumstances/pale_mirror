#!/usr/bin/env python3
"""Fetch only the two hash-pinned trial dependencies; never install into a live pack."""
import hashlib
import json
from pathlib import Path
import urllib.request

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
