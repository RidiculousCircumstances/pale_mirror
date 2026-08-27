#!/usr/bin/env python3
"""Focused negative tests for the architecture contract validator."""

from __future__ import annotations

import copy
import unittest
from pathlib import Path

import yaml

from architecture_contract import ContractError, validate


ROOT = Path(__file__).resolve().parents[2]


class ArchitectureContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.document = yaml.safe_load(
            (ROOT / "architecture.yml").read_text(encoding="utf-8")
        )

    def test_current_contract_is_valid(self) -> None:
        validate(self.document)

    def test_rejects_duplicate_flow_identity(self) -> None:
        broken = copy.deepcopy(self.document)
        broken["critical_flows"].append(copy.deepcopy(broken["critical_flows"][0]))
        with self.assertRaisesRegex(ContractError, "duplicate critical_flows id"):
            validate(broken)

    def test_rejects_removed_greenfield_dependency_boundary(self) -> None:
        broken = copy.deepcopy(self.document)
        frontier = next(
            component
            for component in broken["components"]
            if component["name"] == "frontier_v3"
        )
        frontier["must_not_depend_on"].remove("pale-mirror-domain")
        with self.assertRaisesRegex(ContractError, "missing forbidden dependencies"):
            validate(broken)

    def test_rejects_legacy_flow_without_freeze(self) -> None:
        broken = copy.deepcopy(self.document)
        legacy = next(
            flow
            for flow in broken["critical_flows"]
            if flow["id"] == "frontier-graybox-projection"
        )
        legacy.pop("lifecycle")
        with self.assertRaisesRegex(ContractError, "must remain explicitly frozen-active"):
            validate(broken)


if __name__ == "__main__":
    unittest.main()
