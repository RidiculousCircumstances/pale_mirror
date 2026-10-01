from __future__ import annotations

from pathlib import Path
import sys
import tempfile
import unittest


sys.path.insert(0, str(Path(__file__).resolve().parent))

from continuity_ledger import (  # noqa: E402
    ContinuityLedgerError,
    MAX_LEDGER_LINES,
    validate_ledger,
)


def valid_ledger() -> str:
    return "# Continuity Ledger\n\nMain alone fixes physical observation; deployment pending.\n"


class ContinuityLedgerTest(unittest.TestCase):
    def test_active_ledger_is_valid(self) -> None:
        validate_ledger()

    def test_free_form_brief_without_prescribed_headings_is_valid(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            ledger = root / "CONTINUITY.md"
            ledger.write_text("Main alone; source verified; server deployment pending.\n", encoding="utf-8")
            validate_ledger(ledger, repository_root=root)

    def test_empty_or_heading_only_brief_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            ledger = root / "CONTINUITY.md"
            for text in ("", " \n", "# Continuity Ledger\n\n## State\n"):
                ledger.write_text(text, encoding="utf-8")
                with self.assertRaisesRegex(ContinuityLedgerError, "no brief content"):
                    validate_ledger(ledger, repository_root=root)

    def test_oversized_ledger_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            ledger = root / "CONTINUITY.md"
            extra = "- detail\n" * MAX_LEDGER_LINES
            ledger.write_text(valid_ledger() + extra, encoding="utf-8")
            with self.assertRaisesRegex(ContinuityLedgerError, "maximum"):
                validate_ledger(ledger, repository_root=root)

    def test_missing_archive_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            ledger = root / "CONTINUITY.md"
            ledger.write_text(
                valid_ledger() + "- docs/archive/missing.md\n",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ContinuityLedgerError, "archive"):
                validate_ledger(ledger, repository_root=root)


if __name__ == "__main__":
    unittest.main()
