#!/usr/bin/env python3
"""Focused negative tests for the Frontier v3 architecture debt ratchet."""

from __future__ import annotations

import copy
import unittest
from pathlib import Path

import yaml

from frontier_v3_architecture_debt import DebtError, collect, validate


ROOT = Path(__file__).resolve().parents[2]
POLICY = ROOT / "tools/engineering/frontier_v3_architecture_debt.yml"


class FrontierV3ArchitectureDebtTest(unittest.TestCase):
    def setUp(self) -> None:
        self.policy = yaml.safe_load(POLICY.read_text(encoding="utf-8"))
        self.actual = collect(ROOT)

    def test_current_debt_is_within_checked_in_ceiling(self) -> None:
        validate(ROOT, self.policy, self.actual)

    def test_rejects_new_scene_branch_file(self) -> None:
        broken = copy.deepcopy(self.actual)
        broken["scene_cause_type_branches"]["new/HiddenSceneBranch.java"] = 1
        with self.assertRaisesRegex(DebtError, "spread to unapproved files"):
            validate(ROOT, self.policy, broken)

    def test_rejects_a_direct_cause_specific_production_predicate(self) -> None:
        broken = copy.deepcopy(self.actual)
        generic = (
            "pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/"
            "internal/frontier/v3/FrontierV3SceneExecutor.java"
        )
        broken["forbidden_scene_cause_predicates"][generic] += 1
        with self.assertRaisesRegex(DebtError, "per-file ceiling exceeded"):
            validate(ROOT, self.policy, broken)

    def test_rejects_more_positional_enum_tags(self) -> None:
        broken = copy.deepcopy(self.actual)
        broken["persisted_enum_position_tags"]["model/HiddenPositionalCodec.java"] = 1
        with self.assertRaisesRegex(DebtError, "spread to unapproved files"):
            validate(ROOT, self.policy, broken)

    def test_rejects_positional_wire_tag_registry(self) -> None:
        broken = copy.deepcopy(self.actual)
        broken["wire_tag_position_derivations"]["model/FrontierWireTags.java"] = 1
        with self.assertRaisesRegex(DebtError, "spread to unapproved files"):
            validate(ROOT, self.policy, broken)

    def test_rejects_more_direct_world_state_construction(self) -> None:
        broken = copy.deepcopy(self.actual)
        path = next(iter(broken["direct_world_state_construction"]))
        broken["direct_world_state_construction"][path] += 1
        with self.assertRaisesRegex(DebtError, "per-file ceiling exceeded"):
            validate(ROOT, self.policy, broken)

    def test_rejects_longer_manual_executor_pipeline(self) -> None:
        broken = copy.deepcopy(self.actual)
        broken["manual_executor_ticks"] += 1
        with self.assertRaisesRegex(DebtError, "physical executor pipeline grew"):
            validate(ROOT, self.policy, broken)

    def test_rejects_another_production_fixture_profile(self) -> None:
        broken = copy.deepcopy(self.actual)
        broken["lifecycle_fixture_profile_branches"] += 1
        with self.assertRaisesRegex(DebtError, "fixture profile branches grew"):
            validate(ROOT, self.policy, broken)

    def test_rejects_another_process_inside_model(self) -> None:
        broken = copy.deepcopy(self.actual)
        broken["process_classes_in_model"] += 1
        with self.assertRaisesRegex(DebtError, "process ownership spread"):
            validate(ROOT, self.policy, broken)

    def test_rejects_a_new_historical_surface_authority_branch(self) -> None:
        broken = copy.deepcopy(self.actual)
        broken["historical_surface_active_references"]["new/HiddenSurfaceAuthority.java"] = 1
        with self.assertRaisesRegex(DebtError, "spread to unapproved files"):
            validate(ROOT, self.policy, broken)

    def test_rejects_a_v3_gametest_chunk_load(self) -> None:
        broken = copy.deepcopy(self.actual)
        broken["v3_gametest_forced_chunk_loads"]["new/FrontierV3HiddenGameTests.java"] = 1
        with self.assertRaisesRegex(DebtError, "spread to unapproved files"):
            validate(ROOT, self.policy, broken)

    def test_rejects_a_scene_executor_local_player_scan(self) -> None:
        broken = copy.deepcopy(self.actual)
        broken["scene_executor_raw_player_scans"]["new/FrontierV3HiddenSceneExecutor.java"] = 1
        with self.assertRaisesRegex(DebtError, "spread to unapproved files"):
            validate(ROOT, self.policy, broken)

    def test_rejects_a_scene_executor_global_first_candidate(self) -> None:
        broken = copy.deepcopy(self.actual)
        broken["scene_executor_global_first_candidates"][
            "new/FrontierV3HiddenSceneExecutor.java"
        ] = 1
        with self.assertRaisesRegex(DebtError, "spread to unapproved files"):
            validate(ROOT, self.policy, broken)

    def test_rejects_a_scene_lease_payload_without_sdk_admission_marker(self) -> None:
        broken = copy.deepcopy(self.actual)
        broken["unfenced_scene_lease_admission_payloads"][
            "model/HiddenSceneLeasePrepared.java"
        ] = 1
        with self.assertRaisesRegex(DebtError, "spread to unapproved files"):
            validate(ROOT, self.policy, broken)

    def test_rejects_a_new_command_planner_branch(self) -> None:
        broken = copy.deepcopy(self.actual)
        broken["command_planner_payload_type_tests"] += 1
        with self.assertRaisesRegex(DebtError, "command planner dispatcher grew"):
            validate(ROOT, self.policy, broken)

    def test_rejects_a_new_event_reducer_case(self) -> None:
        broken = copy.deepcopy(self.actual)
        broken["event_reducer_cases"] += 1
        with self.assertRaisesRegex(DebtError, "event reducer dispatcher grew"):
            validate(ROOT, self.policy, broken)

    def test_rejects_a_domain_emission_fallback(self) -> None:
        broken = copy.deepcopy(self.actual)
        broken["domain_emission_fallbacks"] += 1
        with self.assertRaisesRegex(DebtError, "emission contracts use a domain fallback"):
            validate(ROOT, self.policy, broken)

    def test_rejects_a_model_reverse_dependency(self) -> None:
        broken = copy.deepcopy(self.actual)
        broken["model_forbidden_package_dependencies"]["model/LeakedRuntimeImport.java"] = 1
        with self.assertRaisesRegex(DebtError, "spread to unapproved files"):
            validate(ROOT, self.policy, broken)

    def test_rejects_a_missing_scheduled_queue_capacity_guard(self) -> None:
        broken = copy.deepcopy(self.actual)
        broken["unbounded_scheduled_queue_paths"]["kernel/InMemoryFrontierEngine.java"] = 1
        with self.assertRaisesRegex(DebtError, "spread to unapproved files"):
            validate(ROOT, self.policy, broken)


if __name__ == "__main__":
    unittest.main()
