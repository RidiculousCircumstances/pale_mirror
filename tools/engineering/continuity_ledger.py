from __future__ import annotations

import argparse
from collections.abc import Sequence
from pathlib import Path
import re
import sys


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_LEDGER_PATH = REPOSITORY_ROOT / "CONTINUITY.md"
MAX_LEDGER_LINES = 240
REQUIRED_HEADINGS = (
    "# Continuity Ledger",
    "## Goal (success criteria)",
    "## Constraints/Assumptions",
    "## Key decisions",
    "## State",
    "### Done",
    "### Now",
    "### Next",
    "## Open questions",
    "## Working set",
)
ARCHIVE_REFERENCE_PATTERN = re.compile(r"docs/archive/[A-Za-z0-9_.-]+\.md")


class ContinuityLedgerError(RuntimeError):
    """Raised when the active continuity ledger is not a usable session brief."""


def validate_ledger(
    path: Path = DEFAULT_LEDGER_PATH,
    *,
    repository_root: Path = REPOSITORY_ROOT,
) -> None:
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as exc:
        raise ContinuityLedgerError(f"cannot read continuity ledger {path}: {exc}") from exc

    if len(lines) > MAX_LEDGER_LINES:
        raise ContinuityLedgerError(
            f"continuity ledger has {len(lines)} lines; maximum is {MAX_LEDGER_LINES}"
        )

    positions: list[int] = []
    for heading in REQUIRED_HEADINGS:
        try:
            positions.append(lines.index(heading))
        except ValueError as exc:
            raise ContinuityLedgerError(
                f"continuity ledger is missing heading: {heading}"
            ) from exc
    if positions != sorted(positions) or len(set(positions)) != len(positions):
        raise ContinuityLedgerError("continuity ledger headings are out of order")

    text = "\n".join(lines)
    for archive_reference in ARCHIVE_REFERENCE_PATTERN.findall(text):
        if not (repository_root / archive_reference).is_file():
            raise ContinuityLedgerError(
                f"continuity archive does not exist: {archive_reference}"
            )


def _parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate the compact Pale Mirror continuity ledger."
    )
    parser.add_argument("--ledger", type=Path, default=DEFAULT_LEDGER_PATH)
    parser.add_argument("--root", type=Path, default=REPOSITORY_ROOT)
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = _parse_args(argv)
    try:
        validate_ledger(args.ledger, repository_root=args.root)
    except ContinuityLedgerError as exc:
        print(f"continuity ledger failed: {exc}", file=sys.stderr)
        return 2
    print(f"continuity ledger valid: {args.ledger}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
