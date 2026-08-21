from __future__ import annotations

from pathlib import Path
import sys
import tempfile
import unittest


sys.path.insert(0, str(Path(__file__).resolve().parent))

from continuity_ledger import (  # noqa: E402
    ContinuityLedgerError,
    MAX_LEDGER_LINES,
    REQUIRED_HEADINGS,
    validate_ledger,
)


def valid_ledger() -> str:
    sections = {
        "# Continuity Ledger": (),
        "## Goal (success criteria)": ("- Goal",),
        "## Constraints/Assumptions": ("- Constraint",),
        "## Key decisions": ("- Decision",),
        "## State": (),
        "### Done": ("- Done",),
        "### Now": ("- Now",),
        "### Next": ("- Next",),
        "## Open questions": ("- None",),
        "## Working set": ("- None",),
    }
    lines: list[str] = []
    for heading in REQUIRED_HEADINGS:
        lines.append(heading)
        lines.extend(sections[heading])
    return "\n".join(lines) + "\n"


class ContinuityLedgerTest(unittest.TestCase):
    def test_active_ledger_is_valid(self) -> None:
        validate_ledger()

    def test_missing_heading_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            ledger = root / "CONTINUITY.md"
            ledger.write_text(
                valid_ledger().replace("### Next\n- Next\n", ""),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ContinuityLedgerError, "missing heading"):
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
                valid_ledger().replace("- Constraint", "- docs/archive/missing.md"),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ContinuityLedgerError, "archive"):
                validate_ledger(ledger, repository_root=root)


if __name__ == "__main__":
    unittest.main()
