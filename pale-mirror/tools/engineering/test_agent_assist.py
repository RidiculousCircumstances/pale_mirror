from __future__ import annotations

import json
import os
import stat
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

TOOL_ROOT = Path(__file__).parent / "agent_assist"
sys.path.insert(0, str(TOOL_ROOT))

import async_profile  # noqa: E402
import runtime_context  # noqa: E402
import verification_selector  # noqa: E402


class VerificationSelectorTest(unittest.TestCase):
    def test_selects_fast_feedback_and_defers_complete_gate(self) -> None:
        result = verification_selector.select([
            "pale-mirror-frontier/src/main/java/io/farfrontier/palemirror/frontier/v3/kernel/ScheduledAction.java"
        ])

        self.assertEqual("change-feedback", result["mode"])
        self.assertIn("frontier-unit", {step["id"] for step in result["steps"]})
        self.assertIn("architecture", {step["id"] for step in result["steps"]})
        self.assertNotIn("critical-gate", {step["id"] for step in result["steps"]})
        self.assertIn("critical-gate", {step["id"] for step in result["deferred"]})
        self.assertEqual("advisory_only", result["execution"])

    def test_milestone_includes_one_complete_gate(self) -> None:
        result = verification_selector.select(["architecture.yml"], milestone=True)
        ids = [step["id"] for step in result["steps"]]

        self.assertEqual(1, ids.count("critical-gate"))

    def test_direct_java_test_gets_a_class_filter(self) -> None:
        path = "pale-mirror-neoforge/src/test/java/io/farfrontier/palemirror/internal/frontier/v3/CustodyTest.java"
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / path
            source.parent.mkdir(parents=True)
            source.write_text("class CustodyTest {}", encoding="utf-8")
            result = verification_selector.select([path], root=root)
            self.assertTrue(any("--tests 'io.farfrontier.palemirror.internal.frontier.v3.CustodyTest'" in step["command"]
                                for step in result["steps"]))
            source.unlink()
            result = verification_selector.select([path], root=root)
            self.assertEqual({"neoforge-test-compile"}, {step["id"] for step in result["steps"]})
            self.assertFalse(any("--tests" in step["command"] for step in result["steps"]))
            self.assertFalse(any("GameTest" in step["command"] for step in result["steps"] + result["deferred"]))

    def test_rejects_paths_outside_repository(self) -> None:
        with self.assertRaises(verification_selector.SelectionError):
            verification_selector.select(["../secret"])

    def test_unmapped_path_requires_manual_selection_without_launching_a_complete_gate(self) -> None:
        result = verification_selector.select(["unknown/new-owner.file"])
        self.assertNotIn("critical-gate", {step["id"] for step in result["steps"]})
        self.assertIn("critical-gate", {step["id"] for step in result["deferred"]})
        self.assertTrue(result["manual_selection_required"])
        self.assertEqual(["unknown/new-owner.file"], result["unmapped_paths"])

    def test_docs_and_production_custody_changes_do_not_infer_native_campaigns(self) -> None:
        docs = verification_selector.select(["docs/scope.md"])
        self.assertEqual(["git diff --check"], [step["command"] for step in docs["steps"]])
        product = verification_selector.select([
            "pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/internal/frontier/v3/Custody.java"
        ])
        self.assertFalse(any("GameTest" in step["command"] for step in product["steps"]))
        self.assertIn("scene-gametest", {step["id"] for step in product["deferred"]})


class RuntimeContextTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        root = Path(self.temporary.name)
        self.implementation = root / "implementation"
        self.evidence = root / "evidence"
        self.state = root / "state"
        self.implementation.mkdir()
        self.evidence.mkdir()

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def context(self) -> runtime_context.RuntimeContext:
        return runtime_context.RuntimeContext(self.implementation, [self.evidence], self.state)

    def test_indexes_queries_redacts_and_detects_staleness(self) -> None:
        evidence = self.evidence / "incident.jsonl"
        evidence.write_text(
            json.dumps({
                "timestamp": "2026-09-15T00:00:00Z",
                "traceId": "trace-7",
                "subjectId": "resident-4",
                "actorId": "actor-4",
                "runId": "r85",
                "reasonCode": "WORKER_NOT_CURRENT",
                "category": "WAIT_OR_BLOCKED",
                "token": "token=super-secret",
            }) + "\n",
            encoding="utf-8",
        )
        context = self.context()
        self.assertEqual("unavailable", context.status()["state"])

        indexed = context.index()
        self.assertEqual("ready", indexed["state"])
        result = context.query("trace-7", field="trace")
        self.assertEqual(1, len(result["matches"]))
        self.assertEqual("resident-4", result["matches"][0]["subject_id"])
        self.assertNotIn("super-secret", result["matches"][0]["payload"])
        why = context.why("resident-4")
        self.assertEqual("WORKER_NOT_CURRENT", why["why"]["last_reason"])

        evidence.write_text(evidence.read_text(encoding="utf-8") + "plain follow-up\n", encoding="utf-8")
        self.assertEqual("stale", context.status()["state"])

    def test_mcp_lists_only_bounded_assist_tools(self) -> None:
        process = subprocess.Popen(
            [
                sys.executable,
                str(TOOL_ROOT / "runtime_context.py"),
                "--implementation",
                str(self.implementation),
                "--evidence-root",
                str(self.evidence),
                "--state-root",
                str(self.state),
                "mcp",
            ],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            text=True,
        )
        assert process.stdin and process.stdout
        process.stdin.write(json.dumps({"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {}}) + "\n")
        process.stdin.write(json.dumps({"jsonrpc": "2.0", "id": 2, "method": "tools/list", "params": {}}) + "\n")
        process.stdin.flush()
        initialized = json.loads(process.stdout.readline())
        listed = json.loads(process.stdout.readline())
        process.stdin.close()
        process.wait(timeout=5)
        process.stdout.close()

        self.assertEqual(runtime_context.SERVER_NAME, initialized["result"]["serverInfo"]["name"])
        self.assertEqual(
            {"pm_runtime_status", "pm_runtime_index", "pm_runtime_timeline", "pm_runtime_why", "pm_verify_change"},
            {tool["name"] for tool in listed["result"]["tools"]},
        )


class AsyncProfilerWrapperTest(unittest.TestCase):
    def test_validates_pin_and_builds_bounded_dry_run(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            home = root / "async"
            (home / "bin").mkdir(parents=True)
            launcher = home / "bin/asprof"
            launcher.write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
            launcher.chmod(launcher.stat().st_mode | stat.S_IXUSR)
            library = home / "lib/libasyncProfiler.so"
            library.parent.mkdir()
            library.write_bytes(b"test-library")
            (home / "pale-mirror-install.json").write_text(json.dumps({
                "version": async_profile.VERSION,
                "archive_sha256": async_profile.ARCHIVE_SHA256,
                "asset": f"async-profiler-{async_profile.VERSION}-linux-x64.tar.gz",
            }), encoding="utf-8")

            output = async_profile.safe_output(root / "profiles", "sample", "jfr")
            command = async_profile.command(launcher, 123, "cpu", 30, "jfr", output)

            self.assertEqual(str(launcher), command[0])
            self.assertEqual("123", command[-1])
            self.assertFalse(output.exists())

    def test_rejects_unattested_install(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            home = Path(temporary)
            (home / "bin").mkdir()
            launcher = home / "bin/asprof"
            launcher.write_text("bad", encoding="utf-8")
            launcher.chmod(launcher.stat().st_mode | stat.S_IXUSR)
            (home / "lib").mkdir()
            (home / "lib/libasyncProfiler.so").write_bytes(b"bad")
            (home / "pale-mirror-install.json").write_text(json.dumps({
                "version": async_profile.VERSION,
                "archive_sha256": async_profile.ARCHIVE_SHA256,
                "asset": f"async-profiler-{async_profile.VERSION}-linux-x64.tar.gz",
            }), encoding="utf-8")
            with self.assertRaises(async_profile.ProfileError):
                async_profile.installation(home)

    def test_rejects_unsafe_output_and_non_java_pid(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaises(async_profile.ProfileError):
                async_profile.safe_output(Path(temporary), "../escape", "jfr")
        with self.assertRaises(async_profile.ProfileError):
            async_profile.java_process(os.getpid())


if __name__ == "__main__":
    unittest.main()
