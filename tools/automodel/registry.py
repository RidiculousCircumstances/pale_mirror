"""Read the versioned Automodel model registry without executing models."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

from contracts import AutomodelContractError, load_object, require_identifier


REGISTRY_PATH = Path(__file__).with_name("model_registry.v1.json")


@dataclass(frozen=True)
class ModelSpec:
    identifier: str
    kind: str
    status: str
    executor: str
    minimum_vram_gib: int
    authority: str
    licence: str
    notes: str

    @property
    def local_preflight(self) -> bool:
        return self.status == "local_preflight"


def load_registry(path: Path = REGISTRY_PATH) -> dict[str, ModelSpec]:
    data = load_object(path)
    if data.get("schema") != "pale_mirror.automodel.model_registry.v1":
        raise AutomodelContractError("automodel registry has an unsupported schema")
    policy = data.get("policy")
    if not isinstance(policy, dict) or policy.get("runtime_promotion") != "forbidden" or policy.get("remote_execution") != "disabled":
        raise AutomodelContractError("automodel registry must keep runtime and remote execution disabled")
    entries = data.get("models")
    if not isinstance(entries, list) or not entries:
        raise AutomodelContractError("automodel registry requires model entries")
    parsed: dict[str, ModelSpec] = {}
    for index, entry in enumerate(entries):
        if not isinstance(entry, dict):
            raise AutomodelContractError(f"models[{index}] must be an object")
        identifier = require_identifier(entry.get("id"), f"models[{index}].id")
        if identifier in parsed:
            raise AutomodelContractError(f"duplicate model id: {identifier}")
        vram = entry.get("minimum_vram_gib")
        if not isinstance(vram, int) or vram < 0:
            raise AutomodelContractError(f"models[{index}].minimum_vram_gib must be non-negative")
        fields: dict[str, Any] = {field: entry.get(field) for field in ("kind", "status", "executor", "authority", "licence", "notes")}
        if not all(isinstance(value, str) and value for value in fields.values()):
            raise AutomodelContractError(f"models[{index}] lacks required descriptive fields")
        parsed[identifier] = ModelSpec(identifier, fields["kind"], fields["status"], fields["executor"], vram, fields["authority"], fields["licence"], fields["notes"])
    return parsed


def require_local_preflight(registry: dict[str, ModelSpec], identifier: str, available_vram_gib: int) -> ModelSpec:
    spec = registry.get(identifier)
    if spec is None:
        raise AutomodelContractError(f"model is not registered: {identifier}")
    if not spec.local_preflight:
        raise AutomodelContractError(f"model {identifier} is not enabled for local preflight: {spec.status}")
    if available_vram_gib < spec.minimum_vram_gib:
        raise AutomodelContractError(f"model {identifier} requires {spec.minimum_vram_gib} GiB VRAM")
    return spec
