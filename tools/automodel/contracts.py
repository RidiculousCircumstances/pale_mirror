#!/usr/bin/env python3
"""Typed contracts for the offline Pale Mirror Automodel laboratory.

These contracts deliberately describe evidence and proposals rather than an
"accepted model".  They contain no Blender, PMMesh, Minecraft or runtime API.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import hashlib
import json
from pathlib import Path, PurePosixPath
from typing import Any, Iterable


SCHEMA_PREFIX = "pale_mirror.automodel"
BUILD_ROOT = PurePosixPath("build/automodel")


class AutomodelContractError(ValueError):
    """Raised when an offline research artifact violates the evidence boundary."""


class EvidenceTier(str, Enum):
    PRIMARY_TRACE = "PRIMARY_TRACE"
    REVIEWED_SECONDARY = "REVIEWED_SECONDARY"
    MODEL_DERIVED = "MODEL_DERIVED"
    UNTRUSTED = "UNTRUSTED"


TIER_RANK = {
    EvidenceTier.UNTRUSTED: 0,
    EvidenceTier.MODEL_DERIVED: 1,
    EvidenceTier.REVIEWED_SECONDARY: 2,
    EvidenceTier.PRIMARY_TRACE: 3,
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_object(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise AutomodelContractError(f"cannot load JSON object {path}: {exc}") from exc
    if not isinstance(value, dict):
        raise AutomodelContractError(f"{path} must contain one JSON object")
    return value


def write_object(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def require_identifier(value: Any, field: str) -> str:
    if not isinstance(value, str) or not value or any(character not in "abcdefghijklmnopqrstuvwxyz0123456789_-/" for character in value):
        raise AutomodelContractError(f"{field} must be a lowercase Pale Mirror identifier")
    return value


def require_sha256(value: Any, field: str) -> str:
    if not isinstance(value, str) or len(value) != 64 or any(character not in "0123456789abcdef" for character in value):
        raise AutomodelContractError(f"{field} must be a lowercase SHA-256")
    return value


def require_revision(value: Any, field: str) -> str:
    """Accept a pinned Git SHA-1/SHA-256 revision without confusing it with a file hash."""

    if not isinstance(value, str) or len(value) not in {40, 64} or any(character not in "0123456789abcdef" for character in value):
        raise AutomodelContractError(f"{field} must be a lowercase 40- or 64-character source revision")
    return value


def require_relative_build_path(value: Any, field: str) -> str:
    if not isinstance(value, str):
        raise AutomodelContractError(f"{field} must be a repository-relative path")
    path = PurePosixPath(value)
    if path.is_absolute() or ".." in path.parts or not path.is_relative_to(BUILD_ROOT):
        raise AutomodelContractError(f"{field} must stay below {BUILD_ROOT}")
    return path.as_posix()


def parse_tier(value: Any, field: str) -> EvidenceTier:
    try:
        return EvidenceTier(value)
    except (TypeError, ValueError) as exc:
        raise AutomodelContractError(f"{field} has an unknown evidence tier") from exc


def validate_reference_bundle(value: dict[str, Any]) -> None:
    if value.get("schema") != f"{SCHEMA_PREFIX}.reference_bundle.v1":
        raise AutomodelContractError("reference bundle has an unsupported schema")
    require_identifier(value.get("asset_id"), "asset_id")
    if value.get("scope") != "research_only_noncanonical":
        raise AutomodelContractError("reference bundle must be research_only_noncanonical")
    primary = value.get("primary")
    trace = value.get("primary_trace")
    anchors = value.get("anchors")
    model_inputs = value.get("model_inputs", [])
    if not isinstance(primary, dict) or not isinstance(trace, dict) or not isinstance(anchors, list) or not isinstance(model_inputs, list):
        raise AutomodelContractError("reference bundle requires primary, primary_trace, anchors and model_inputs")
    if parse_tier(primary.get("tier"), "primary.tier") is not EvidenceTier.PRIMARY_TRACE:
        raise AutomodelContractError("literal primary must have PRIMARY_TRACE authority")
    if primary.get("role") != "sole_likeness_anchor":
        raise AutomodelContractError("literal primary must declare sole_likeness_anchor")
    require_sha256(primary.get("sha256"), "primary.sha256")
    require_sha256(trace.get("sha256"), "primary_trace.sha256")
    if not isinstance(primary.get("file"), str) or not isinstance(trace.get("file"), str):
        raise AutomodelContractError("primary and trace require pinned files")
    seen_ids: set[str] = set()
    for index, anchor in enumerate(anchors):
        if not isinstance(anchor, dict):
            raise AutomodelContractError(f"anchors[{index}] must be an object")
        anchor_id = require_identifier(anchor.get("id"), f"anchors[{index}].id")
        if anchor_id in seen_ids:
            raise AutomodelContractError("anchor identifiers must be unique")
        seen_ids.add(anchor_id)
        tier = parse_tier(anchor.get("tier"), f"anchors[{index}].tier")
        if tier is EvidenceTier.PRIMARY_TRACE:
            raise AutomodelContractError("only primary may have PRIMARY_TRACE authority")
        require_sha256(anchor.get("sha256"), f"anchors[{index}].sha256")
        if not isinstance(anchor.get("file"), str):
            raise AutomodelContractError(f"anchors[{index}] requires a pinned file")
    seen_model_ids: set[str] = set()
    for index, model_input in enumerate(model_inputs):
        if not isinstance(model_input, dict):
            raise AutomodelContractError(f"model_inputs[{index}] must be an object")
        input_id = require_identifier(model_input.get("id"), f"model_inputs[{index}].id")
        if input_id in seen_model_ids:
            raise AutomodelContractError("model-input identifiers must be unique")
        seen_model_ids.add(input_id)
        if model_input.get("role") != "conditioning_only":
            raise AutomodelContractError("model inputs must be conditioning_only")
        if not isinstance(model_input.get("file"), str):
            raise AutomodelContractError(f"model_inputs[{index}] requires a pinned file")
        require_sha256(model_input.get("sha256"), f"model_inputs[{index}].sha256")
        derives_from = model_input.get("derives_from")
        if not isinstance(derives_from, list) or sorted(derives_from) != ["primary", "primary_trace"]:
            raise AutomodelContractError("model inputs must declare derivation from primary and primary_trace")


def validate_view_set(value: dict[str, Any]) -> None:
    if value.get("schema") != f"{SCHEMA_PREFIX}.view_set.v1":
        raise AutomodelContractError("view set has an unsupported schema")
    require_identifier(value.get("asset_id"), "asset_id")
    frames = value.get("frames")
    if not isinstance(frames, list) or not frames:
        raise AutomodelContractError("view set requires at least one frame")
    primary_count = 0
    seen_ids: set[str] = set()
    for index, frame in enumerate(frames):
        if not isinstance(frame, dict):
            raise AutomodelContractError(f"frames[{index}] must be an object")
        frame_id = require_identifier(frame.get("id"), f"frames[{index}].id")
        if frame_id in seen_ids:
            raise AutomodelContractError("view-set frame identifiers must be unique")
        seen_ids.add(frame_id)
        tier = parse_tier(frame.get("tier"), f"frames[{index}].tier")
        if tier is EvidenceTier.PRIMARY_TRACE:
            primary_count += 1
        require_relative_build_path(frame.get("file"), f"frames[{index}].file")
        require_sha256(frame.get("sha256"), f"frames[{index}].sha256")
        review = frame.get("review")
        if tier is EvidenceTier.REVIEWED_SECONDARY:
            if not isinstance(review, dict) or review.get("status") != "accepted_by_human":
                raise AutomodelContractError("reviewed secondary frames require an explicit human receipt")
        elif isinstance(review, dict) and review.get("status") == "accepted_by_human":
            raise AutomodelContractError("human-accepted frame must be REVIEWED_SECONDARY")
    if primary_count != 1:
        raise AutomodelContractError("view set must contain exactly one primary frame")


def validate_run_manifest(value: dict[str, Any]) -> None:
    if value.get("schema") != f"{SCHEMA_PREFIX}.run_manifest.v1":
        raise AutomodelContractError("run manifest has an unsupported schema")
    require_identifier(value.get("asset_id"), "asset_id")
    require_identifier(value.get("adapter_id"), "adapter_id")
    if value.get("scope") != "research_only_noncanonical":
        raise AutomodelContractError("automodel runs must stay research_only_noncanonical")
    if value.get("promotion_prohibited") is not True:
        raise AutomodelContractError("automodel runs must prohibit automatic promotion")
    require_relative_build_path(value.get("run_directory"), "run_directory")
    provenance = value.get("provenance")
    if not isinstance(provenance, dict):
        raise AutomodelContractError("run manifest requires provenance")
    require_revision(provenance.get("adapter_revision"), "provenance.adapter_revision")
    for field in ("checkpoint_sha256", "environment_lock_sha256"):
        require_sha256(provenance.get(field), f"provenance.{field}")
    if not isinstance(provenance.get("license"), str) or not provenance["license"]:
        raise AutomodelContractError("run provenance must include a licence")
    if not isinstance(value.get("inputs"), list) or not value["inputs"]:
        raise AutomodelContractError("run manifest requires hash-pinned inputs")
    for index, input_value in enumerate(value["inputs"]):
        if not isinstance(input_value, dict):
            raise AutomodelContractError(f"inputs[{index}] must be an object")
        require_sha256(input_value.get("sha256"), f"inputs[{index}].sha256")
        require_relative_build_path(input_value.get("file"), f"inputs[{index}].file")
    forbidden = (".blend", ".pmmesh", "src/main/resources", "pale-mirror-neoforge", "server")
    serialized = json.dumps(value, sort_keys=True).lower()
    if any(token in serialized for token in forbidden):
        raise AutomodelContractError("automodel manifest may not target canonical or runtime artifacts")


def validate_geometry_evidence(value: dict[str, Any]) -> None:
    if value.get("schema") != f"{SCHEMA_PREFIX}.geometry_evidence.v1":
        raise AutomodelContractError("geometry evidence has an unsupported schema")
    if value.get("scope") != "research_only_noncanonical":
        raise AutomodelContractError("geometry evidence must remain noncanonical")
    observations = value.get("observations")
    if not isinstance(observations, list) or not observations:
        raise AutomodelContractError("geometry evidence requires observations")
    for index, observation in enumerate(observations):
        if not isinstance(observation, dict):
            raise AutomodelContractError(f"observations[{index}] must be an object")
        tier = parse_tier(observation.get("tier"), f"observations[{index}].tier")
        if tier is not EvidenceTier.MODEL_DERIVED:
            raise AutomodelContractError("geometry predictions must stay MODEL_DERIVED")
        require_identifier(observation.get("adapter_id"), f"observations[{index}].adapter_id")
        require_relative_build_path(observation.get("file"), f"observations[{index}].file")
        require_sha256(observation.get("sha256"), f"observations[{index}].sha256")


def validate_shape_proposal(value: dict[str, Any]) -> None:
    """Validate a review-only mesh/point-cloud proposal.

    A shape proposal deliberately has no acceptance flag.  It is a named
    model hypothesis whose only legal destination is an Automodel review
    package, never an editable Blender source or a runtime export.
    """

    if value.get("schema") != f"{SCHEMA_PREFIX}.shape_proposal.v1":
        raise AutomodelContractError("shape proposal has an unsupported schema")
    require_identifier(value.get("asset_id"), "asset_id")
    if value.get("scope") != "research_only_noncanonical":
        raise AutomodelContractError("shape proposal must remain noncanonical")
    run_manifest = value.get("run_manifest")
    if not isinstance(run_manifest, dict):
        raise AutomodelContractError("shape proposal requires its run manifest")
    require_relative_build_path(run_manifest.get("file"), "run_manifest.file")
    require_sha256(run_manifest.get("sha256"), "run_manifest.sha256")
    proposals = value.get("proposals")
    if not isinstance(proposals, list) or not proposals:
        raise AutomodelContractError("shape proposal requires at least one model hypothesis")
    for index, proposal in enumerate(proposals):
        if not isinstance(proposal, dict):
            raise AutomodelContractError(f"proposals[{index}] must be an object")
        require_identifier(proposal.get("adapter_id"), f"proposals[{index}].adapter_id")
        if parse_tier(proposal.get("tier"), f"proposals[{index}].tier") is not EvidenceTier.MODEL_DERIVED:
            raise AutomodelContractError("shape proposals must stay MODEL_DERIVED")
        require_relative_build_path(proposal.get("file"), f"proposals[{index}].file")
        require_sha256(proposal.get("sha256"), f"proposals[{index}].sha256")
        if proposal.get("role") != "review_only_shape_hypothesis":
            raise AutomodelContractError("shape proposal must declare review_only_shape_hypothesis")


def validate_execution_receipt(value: dict[str, Any]) -> None:
    """Validate the immutable receipt left after a local adapter run."""

    if value.get("schema") != f"{SCHEMA_PREFIX}.execution_receipt.v1":
        raise AutomodelContractError("execution receipt has an unsupported schema")
    require_identifier(value.get("asset_id"), "asset_id")
    require_identifier(value.get("adapter_id"), "adapter_id")
    if value.get("scope") != "research_only_noncanonical":
        raise AutomodelContractError("execution receipt must remain noncanonical")
    manifest = value.get("run_manifest")
    if not isinstance(manifest, dict):
        raise AutomodelContractError("execution receipt requires the run manifest")
    require_relative_build_path(manifest.get("file"), "run_manifest.file")
    require_sha256(manifest.get("sha256"), "run_manifest.sha256")
    outputs = value.get("outputs")
    if not isinstance(outputs, list) or not outputs:
        raise AutomodelContractError("execution receipt requires hash-pinned outputs")
    for index, output in enumerate(outputs):
        if not isinstance(output, dict):
            raise AutomodelContractError(f"outputs[{index}] must be an object")
        require_relative_build_path(output.get("file"), f"outputs[{index}].file")
        require_sha256(output.get("sha256"), f"outputs[{index}].sha256")
    if value.get("promotion_prohibited") is not True:
        raise AutomodelContractError("execution receipt must prohibit promotion")


def validate_official_checkpoint_receipt(value: dict[str, Any]) -> None:
    """Validate provenance for one private, official local checkpoint.

    A receipt deliberately says nothing about artistic quality or acceptance.
    It exists so a registered local runner cannot silently substitute a
    third-party/quarantined checkpoint with the same broad model family.
    """

    if value.get("schema") != f"{SCHEMA_PREFIX}.official_checkpoint_receipt.v1":
        raise AutomodelContractError("official checkpoint receipt has an unsupported schema")
    require_identifier(value.get("model_id"), "model_id")
    if value.get("scope") != "research_only_noncanonical" or value.get("promotion_prohibited") is not True:
        raise AutomodelContractError("official checkpoint receipt must remain noncanonical and promotion-prohibited")
    origin = value.get("official_origin")
    checkpoint = value.get("checkpoint")
    if not isinstance(origin, dict) or not isinstance(checkpoint, dict):
        raise AutomodelContractError("official checkpoint receipt requires origin and checkpoint records")
    for field in ("repository", "filename", "license"):
        if not isinstance(origin.get(field), str) or not origin[field]:
            raise AutomodelContractError(f"official checkpoint origin requires {field}")
    require_sha256(checkpoint.get("sha256"), "checkpoint.sha256")
    if not isinstance(checkpoint.get("byte_size"), int) or checkpoint["byte_size"] <= 0:
        raise AutomodelContractError("official checkpoint receipt requires a positive byte_size")
    if checkpoint.get("storage") != "private_local_host":
        raise AutomodelContractError("official checkpoint receipt may reference only private_local_host storage")
    contract = value.get("contract")
    if not isinstance(contract, dict) or contract.get("weights_not_copied_to_repository") is not True or contract.get("canonical_promotion_forbidden") is not True:
        raise AutomodelContractError("official checkpoint receipt must prohibit repository copy and canonical promotion")


def validate_adapter_qualification(value: dict[str, Any]) -> None:
    """Validate an adapter benchmark receipt, not an artistic quality score."""

    if value.get("schema") != f"{SCHEMA_PREFIX}.adapter_qualification.v1":
        raise AutomodelContractError("adapter qualification has an unsupported schema")
    require_identifier(value.get("adapter_id"), "adapter_id")
    if value.get("scope") != "research_only_noncanonical":
        raise AutomodelContractError("adapter qualification must remain noncanonical")
    result = value.get("benchmark_result")
    if not isinstance(result, dict) or result.get("schema") != f"{SCHEMA_PREFIX}.benchmark_result.v1":
        raise AutomodelContractError("adapter qualification requires a benchmark result")
    if not isinstance(result.get("qualified_for_model_derived_geometry"), bool):
        raise AutomodelContractError("adapter qualification requires an explicit verdict")
    corpus = value.get("corpus")
    if not isinstance(corpus, dict):
        raise AutomodelContractError("adapter qualification requires a pinned corpus")
    require_relative_build_path(corpus.get("file"), "corpus.file")
    require_sha256(corpus.get("sha256"), "corpus.sha256")
    if value.get("authority") != EvidenceTier.MODEL_DERIVED.value:
        raise AutomodelContractError("qualification may grant MODEL_DERIVED evidence only")


def manifest_inputs(files: Iterable[Path], repository_root: Path) -> list[dict[str, str]]:
    records: list[dict[str, str]] = []
    for path in files:
        resolved = path.resolve()
        try:
            relative = resolved.relative_to(repository_root.resolve()).as_posix()
        except ValueError as exc:
            raise AutomodelContractError(f"input is outside repository: {path}") from exc
        require_relative_build_path(relative, "input.file")
        records.append({"file": relative, "sha256": sha256(resolved)})
    return records
