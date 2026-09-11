#!/usr/bin/env python3
"""Create an anonymized, evidence-only Harvester pairwise review package.

The output's ``public/`` directory is the only directory a phase-A/B reviewer
may inspect.  It is deliberately scrubbed of source filenames, candidate IDs,
provenance, metrics and overlays.  ``operator_mapping.json`` is kept beside,
not inside, that directory so an operator can reveal the result only after the
blind verdicts are recorded.
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
import re
from typing import Iterable

from PIL import Image


VIEWS = ("primary", "front", "side", "opposite", "elevated_rear")
LABELS = ("amber", "cobalt")
SCHEMA = "pale_mirror_visuals.harvester_blind_pairwise_package.v1"


@dataclass(frozen=True)
class Candidate:
    """Operator-only candidate identity and the audited render directory."""

    identifier: str
    directory: Path


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def parse_candidate(value: str) -> Candidate:
    identifier, separator, raw_directory = value.partition("=")
    if not separator or not identifier or not raw_directory:
        raise ValueError("--candidate must use operator-only ID=render-directory syntax")
    if not re.fullmatch(r"[A-Za-z0-9_.-]+", identifier):
        raise ValueError("candidate ID may contain only letters, digits, dot, underscore and dash")
    directory = Path(raw_directory).expanduser().resolve()
    if not directory.is_dir():
        raise ValueError(f"candidate render directory does not exist: {directory}")
    return Candidate(identifier=identifier, directory=directory)


def required_views(candidate: Candidate) -> dict[str, Path]:
    result: dict[str, Path] = {}
    for view in VIEWS:
        image = candidate.directory / f"{view}.png"
        if not image.is_file():
            raise ValueError(f"{candidate.identifier} lacks required {view}.png")
        result[view] = image
    return result


def image_size(path: Path) -> tuple[int, int]:
    with Image.open(path) as image:
        return image.size


def normalized_png(source: Path, destination: Path) -> None:
    """Write only pixels, stripping source filename, EXIF and PNG text chunks."""

    with Image.open(source) as image:
        normalized = image.convert("RGBA")
        normalized.load()
    destination.parent.mkdir(parents=True, exist_ok=True)
    normalized.save(destination, format="PNG", optimize=False)


def candidate_fingerprint(candidate: Candidate, views: dict[str, Path]) -> str:
    digest = hashlib.sha256()
    for view in VIEWS:
        digest.update(view.encode("ascii"))
        digest.update(b"\0")
        digest.update(sha256_file(views[view]).encode("ascii"))
        digest.update(b"\n")
    return digest.hexdigest()


def public_prompt() -> str:
    return """# Blind visual review

You have no candidate history. Inspect **only this directory** and do not open
its parent, any repository files, metadata, overlay, score, manifest or source
artifact.

1. Compare `reference.png`, `amber/primary.png` and `cobalt/primary.png`.
   Choose `amber`, `cobalt` or `indistinguishable` for literal likeness to the
   reference. Record confidence (`low`, `medium` or `high`) and three visible
   reasons. Do not assign a number.
2. Inspect the four diagnostic images for each label. Confirm, reverse or mark
   `indistinguishable`; record visible volume/anatomy defects only.
3. Return the template exactly enough for an operator to record the result.

Do not infer a winner from rendering polish, polygon density, filenames or any
knowledge outside this directory. The reference image decides likeness.
"""


def response_template() -> dict[str, object]:
    return {
        "schema": "pale_mirror_visuals.harvester_blind_pairwise_response.v1",
        "reviewer": "anonymous",
        "phase_a_primary_preference": "amber | cobalt | indistinguishable",
        "phase_a_confidence": "low | medium | high",
        "phase_a_visible_reasons": ["", "", ""],
        "phase_b_diagnostic_preference": "amber | cobalt | indistinguishable",
        "phase_b_visible_defects": {"amber": [""], "cobalt": [""]},
        "final_preference": "amber | cobalt | indistinguishable",
        "notes": "No scores; no history; no technical overlay inspected."
    }


def _validate_inputs(reference: Path, candidates: tuple[Candidate, Candidate]) -> tuple[dict[str, Path], dict[str, Path]]:
    if not reference.is_file():
        raise ValueError(f"reference image does not exist: {reference}")
    if candidates[0].identifier == candidates[1].identifier:
        raise ValueError("candidate IDs must be distinct")
    first, second = (required_views(candidate) for candidate in candidates)
    reference_size = image_size(reference)
    if image_size(first["primary"]) != reference_size or image_size(second["primary"]) != reference_size:
        raise ValueError("each primary candidate view must match the reference dimensions")
    for view in VIEWS:
        if image_size(first[view]) != image_size(second[view]):
            raise ValueError(f"candidate {view} views must have identical dimensions")
    return first, second


def build_package(*, protocol: Path, review_id: str, reference: Path, candidates: tuple[Candidate, Candidate], output_root: Path) -> dict[str, Path]:
    """Build a new package, refusing to overwrite a previous blind review."""

    protocol = protocol.expanduser().resolve()
    reference = reference.expanduser().resolve()
    output_root = output_root.expanduser().resolve()
    if not protocol.is_file():
        raise ValueError(f"blind review protocol does not exist: {protocol}")
    protocol_data = json.loads(protocol.read_text(encoding="utf-8"))
    if protocol_data.get("schema") != "pale_mirror_visuals.harvester_blind_pairwise_review.v1":
        raise ValueError("unsupported blind review protocol schema")
    if not re.fullmatch(r"[a-z0-9][a-z0-9_-]{2,80}", review_id):
        raise ValueError("review ID must be 3-81 lowercase letters, digits, underscores or dashes")
    first_views, second_views = _validate_inputs(reference, candidates)

    package_root = output_root / review_id
    if package_root.exists():
        raise ValueError(f"refusing to overwrite existing review package: {package_root}")
    public_root = package_root / "public"
    candidate_views = ((candidates[0], first_views), (candidates[1], second_views))
    ordered = sorted(
        candidate_views,
        key=lambda item: sha256_bytes(f"{review_id}\0{candidate_fingerprint(*item)}".encode("ascii")),
    )
    label_to_candidate = dict(zip(LABELS, ordered, strict=True))

    normalized_png(reference, public_root / "reference.png")
    public_candidates: dict[str, dict[str, object]] = {}
    operator_candidates: dict[str, dict[str, object]] = {}
    for label, (candidate, views) in label_to_candidate.items():
        public_candidates[label] = {"views": {}}
        operator_candidates[label] = {
            "candidate_id": candidate.identifier,
            "render_directory": str(candidate.directory),
            "view_fingerprint": candidate_fingerprint(candidate, views),
            "views": {view: {"path": str(path), "sha256": sha256_file(path)} for view, path in views.items()},
        }
        for view, source in views.items():
            destination = public_root / label / f"{view}.png"
            normalized_png(source, destination)
            public_candidates[label]["views"][view] = {
                "sha256": sha256_file(destination),
                "size": list(image_size(destination)),
            }

    (public_root / "review_prompt.md").write_text(public_prompt(), encoding="utf-8")
    (public_root / "response_template.json").write_text(
        json.dumps(response_template(), indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    public_manifest = {
        "schema": SCHEMA,
        "review_id": review_id,
        "reviewer_scope": "Inspect public/ only. Candidate identities, metrics and overlays are intentionally absent.",
        "reference": {"sha256": sha256_file(public_root / "reference.png"), "size": list(image_size(public_root / "reference.png"))},
        "candidates": public_candidates,
        "views": list(VIEWS),
        "blind_phase": "A/B only; no metric, trace or overlay evidence is included.",
    }
    (public_root / "package_manifest.json").write_text(
        json.dumps(public_manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    operator_mapping = {
        "schema": "pale_mirror_visuals.harvester_blind_pairwise_operator_mapping.v1",
        "review_id": review_id,
        "protocol": {"path": str(protocol), "sha256": sha256_file(protocol)},
        "reference": {"path": str(reference), "sha256": sha256_file(reference)},
        "labels": operator_candidates,
        "disclosure": "Reveal only after all phase-A/B reviewer records are immutable.",
    }
    (package_root / "operator_mapping.json").write_text(
        json.dumps(operator_mapping, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    return {"root": package_root, "public": public_root, "mapping": package_root / "operator_mapping.json"}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--protocol", required=True, type=Path)
    parser.add_argument("--review-id", required=True)
    parser.add_argument("--reference", required=True, type=Path)
    parser.add_argument("--candidate", action="append", required=True, help="operator-only ID=render-directory; specify exactly twice")
    parser.add_argument("--output-root", required=True, type=Path)
    arguments = parser.parse_args()
    if len(arguments.candidate) != 2:
        parser.error("specify --candidate exactly twice")
    result = build_package(
        protocol=arguments.protocol,
        review_id=arguments.review_id,
        reference=arguments.reference,
        candidates=tuple(parse_candidate(value) for value in arguments.candidate),  # type: ignore[arg-type]
        output_root=arguments.output_root,
    )
    print(json.dumps({name: str(path) for name, path in result.items()}, sort_keys=True))


if __name__ == "__main__":
    main()
