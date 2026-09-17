import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

MODULE = Path(__file__).with_name("repository_context.py")
SPEC = importlib.util.spec_from_file_location("repository_context", MODULE)
context = importlib.util.module_from_spec(SPEC); SPEC.loader.exec_module(context)


class RepositoryContextContractTest(unittest.TestCase):
    def nested_fixture(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        repository = Path(temporary.name) / "repository"
        implementation = repository / "pale-mirror"
        implementation.mkdir(parents=True)
        (repository / "pack.txt").write_text("pack-original", encoding="utf-8")
        (implementation / "tracked.txt").write_text("original", encoding="utf-8")
        subprocess.run(["git", "init", "-q", str(repository)], check=True)
        subprocess.run(["git", "-C", str(repository), "config", "user.email", "context@example.invalid"], check=True)
        subprocess.run(["git", "-C", str(repository), "config", "user.name", "Context Fixture"], check=True)
        subprocess.run(["git", "-C", str(repository), "add", "."], check=True)
        subprocess.run(["git", "-C", str(repository), "commit", "-qm", "fixture"], check=True)
        return repository, implementation

    def test_vendor_pin_and_read_only_surface_are_explicit(self):
        self.assertEqual("v0.8.1", context.RELEASE)
        self.assertEqual(64, len(context.BINARY_SHA256))
        self.assertNotIn("delete_project", context.READ_ONLY_TOOLS)
        self.assertIn("pm_context_task", context.READ_ONLY_TOOLS)

    def test_declarative_index_covers_all_architecture_components_and_f06r3(self):
        value = context.task_context("farmer last-cell HOT COLD restart re-entry")
        index = context.cross_links()
        declared = {component["name"] for component in context.architecture()["components"]}
        base = {contract["owner"] for contract in index["contracts"] if contract["id"].startswith("component-")}
        self.assertEqual(declared, base)
        self.assertEqual(["frontier_v3"], next(anchor["owners"] for anchor in value["anchors"] if "FrontierResourceSiteHarvestSceneSupport" in anchor["path"]))
        self.assertIn("neoforge", {owner for anchor in value["anchors"] for owner in anchor["owners"]})
        self.assertIn("frontier_v3_test_harness", {owner for anchor in value["anchors"] for owner in anchor["owners"]})
        self.assertTrue(any(anchor["path"].endswith("FrontierV3ServerRuntime.java") for anchor in value["anchors"]))
        self.assertTrue(any(anchor["path"].endswith("FrontierV3ServerLifecycle.java") for anchor in value["anchors"]))
        self.assertTrue(any(anchor["path"].endswith("FrontierV3ScenePresentation.java") for anchor in value["anchors"]))
        self.assertEqual(9, len(value["all_components"]))
        self.assertEqual({"frontier-v3-hot-cold-scene", "frontier-v3-exact-actor-unload-return", "frontier-v3-diagnostic-causality", "frontier-v3-restart-recovery", "frontier-v3-test-pilot-causality"}, {flow["id"] for flow in value["critical_flows"]})

    def test_generic_path_and_change_context_are_governed_and_unmapped_is_explicit(self):
        graybox = context.context_for_path("pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/internal/frontier/v3/FrontierV3GrayboxExecutor.java")
        self.assertIn("neoforge", graybox["owners"])
        self.assertIn("f06r3-graybox-projection", graybox["contract_ids"])
        self.assertIn("frontier-v3-diagnostic-causality", graybox["critical_flows"])
        change = context.context_for_change([
            "pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/internal/frontier/v3/FrontierV3ProjectionWorkDiagnostic.java",
            "tools/frontier-v3-test-pilot/src/f06r3-recovered-duty-carrier.mjs",
        ])
        self.assertIn("neoforge", change["owners"])
        self.assertIn("frontier_v3_test_harness", change["owners"])
        self.assertIn("focused-node", change["verification_families"])
        self.assertEqual("unmapped", context.context_for_path("tools/engineering/architecture_contract.py")["state"])

    def test_external_and_parent_paths_fail_closed(self):
        with self.assertRaises(context.ContextError): context.checked_path("../stream_miner/tools/mcp/codebase_memory_bridge.py")
        with self.assertRaises(context.ContextError): context.checked_path("/etc/passwd")

    def test_mcp_tool_list_is_only_the_declared_read_only_allowlist(self):
        response = context.mcp_response({"jsonrpc": "2.0", "id": 1, "method": "tools/list", "params": {}})
        self.assertEqual(context.READ_ONLY_TOOLS, tuple(tool["name"] for tool in response["result"]["tools"]))

    def test_nested_git_root_fingerprint_tracks_tracked_untracked_delete_and_restore_bytes(self):
        repository, implementation = self.nested_fixture()
        tracked = implementation / "tracked.txt"
        untracked = implementation / "untracked.txt"
        repository_tracked = repository / "pack.txt"
        with patch.object(context, "IMPLEMENTATION", implementation):
            self.assertEqual(repository, context.git_root())
            self.assertEqual("pale-mirror", context.implementation_scope()["relative_to_git_root"])
            clean = context.worktree_fingerprint()
            tracked.write_text("changed", encoding="utf-8")
            changed = context.worktree_fingerprint()
            self.assertNotEqual(clean, changed)
            tracked.write_text("original", encoding="utf-8")
            self.assertEqual(clean, context.worktree_fingerprint())
            repository_tracked.write_text("pack-changed", encoding="utf-8")
            self.assertNotEqual(clean, context.worktree_fingerprint())
            repository_tracked.write_text("pack-original", encoding="utf-8")
            self.assertEqual(clean, context.worktree_fingerprint())
            untracked.write_text("first", encoding="utf-8")
            first_untracked = context.worktree_fingerprint()
            untracked.write_text("second", encoding="utf-8")
            self.assertNotEqual(first_untracked, context.worktree_fingerprint())
            untracked.unlink()
            self.assertEqual(clean, context.worktree_fingerprint())
            tracked.unlink()
            self.assertNotEqual(clean, context.worktree_fingerprint())
            tracked.write_text("original", encoding="utf-8")
            self.assertEqual(clean, context.worktree_fingerprint())

    def test_fingerprint_rejects_a_symbolic_link_from_the_declared_repository_identity(self):
        _repository, implementation = self.nested_fixture()
        with patch.object(context, "IMPLEMENTATION", implementation):
            (implementation / "linked.txt").symlink_to("tracked.txt")
            with self.assertRaisesRegex(context.ContextError, "symbolic links"):
                context.worktree_fingerprint()

    def test_frozen_nested_root_wip_invalidates_the_accepted_old_index(self):
        with tempfile.TemporaryDirectory() as temporary:
            cache = Path(temporary) / "cache"
            with patch.object(context, "cache_directory", return_value=cache), patch.object(context, "validate_installation"):
                context.private_directory(cache)
                context.index_state_path().write_text(json.dumps({"worktree_fingerprint": "ce2ab4ef1fb36767dfbe3a271c34970378e8aeb0931deeecc18230de8e34ba43"}), encoding="utf-8")
                self.assertNotEqual("ce2ab4ef1fb36767dfbe3a271c34970378e8aeb0931deeecc18230de8e34ba43", context.worktree_fingerprint())
                result = context.state()
                self.assertEqual("stale", result["state"])
                self.assertEqual("ce2ab4ef1fb36767dfbe3a271c34970378e8aeb0931deeecc18230de8e34ba43", result["indexed_fingerprint"])
                self.assertEqual("/home/rd/proj/pm-f06r3-human-ingress-repair", result["implementation"]["git_root"])

    def test_clean_cache_reopens_ready_and_becomes_stale_on_nested_scope_change(self):
        _repository, implementation = self.nested_fixture()
        tracked = implementation / "tracked.txt"
        with tempfile.TemporaryDirectory() as temporary:
            cache = Path(temporary) / "cache"
            with patch.object(context, "IMPLEMENTATION", implementation), patch.object(context, "cache_directory", return_value=cache), patch.object(context, "validate_installation"):
                context.private_directory(cache)
                fingerprint = context.worktree_fingerprint()
                context.index_state_path().write_text(json.dumps({"worktree_fingerprint": fingerprint}), encoding="utf-8")
                self.assertEqual("ready", context.state()["state"])
                self.assertEqual("ready", context.state()["state"])
                tracked.write_text("changed", encoding="utf-8")
                self.assertEqual("stale", context.state()["state"])
                tracked.write_text("original", encoding="utf-8")
                self.assertEqual("ready", context.state()["state"])

    def test_unavailable_vendor_still_reports_fixed_roots_and_current_identity(self):
        with tempfile.TemporaryDirectory() as temporary:
            cache = Path(temporary) / "cache"
            with patch.object(context, "cache_directory", return_value=cache), patch.object(context, "validate_installation", side_effect=context.ContextError("vendor unavailable")):
                result = context.state()
                self.assertEqual("unavailable", result["state"])
                self.assertEqual("vendor unavailable", result["reason"])
                self.assertEqual("/home/rd/proj/pm-f06r3-human-ingress-repair/pale-mirror", result["implementation"]["declared_root"])
                self.assertEqual("/home/rd/proj/pm-f06r3-human-ingress-repair", result["repository"]["root"])

    def test_vendor_session_sends_exactly_one_initialize_before_notification(self):
        requests, notifications = [], []
        session = context.VendorSession()
        session.request = lambda message: requests.append(message) or ({"result": {"protocolVersion": context.PROTOCOL,
            "serverInfo": {"name": context.SERVER_NAME, "version": context.SERVER_VERSION}}} if message["method"] == "initialize"
            else {"result": {"tools": [{"name": name} for name in ("index_repository", "index_status", "search_graph", "search_code", "trace_path")]}})
        session.notify = notifications.append
        with patch.object(context, "validate_installation"), patch.object(context, "private_directory"), patch.object(context.subprocess, "Popen", return_value=object()):
            session.__enter__()
        self.assertEqual(["initialize", "tools/list"], [message["method"] for message in requests])
        self.assertEqual(["notifications/initialized"], [message["method"] for message in notifications])
        with self.assertRaises(context.ContextError): session.initialize_once()


if __name__ == "__main__": unittest.main()
