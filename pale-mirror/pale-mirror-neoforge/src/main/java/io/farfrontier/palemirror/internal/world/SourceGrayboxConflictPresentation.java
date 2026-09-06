package io.farfrontier.palemirror.internal.world;

/** Readable retained record for a physical change that the source did not accept as a new fact. */
final class SourceGrayboxConflictPresentation {
    private SourceGrayboxConflictPresentation() { }

    static String labelText(SourceGrayboxPresentationLedger.Claim claim) {
        if (claim.kind().equals("SOURCE_CONTAINER_RELOCATING")) {
            return "[!] cargo relocation pending target=" + claim.subjectId()
                    + " outcome=old-custody-not-loaded source=retained";
        }
        if (claim.kind().equals("SOURCE_ACTOR_OBSTRUCTION")) {
            return "[!] actor materialization blocked target=" + claim.subjectId()
                    + " outcome=no-collision-free-body source=retained";
        }
        String outcome = claim.consumed() ? "replayed-event"
                : !claim.installed() ? "foreign-obstruction"
                : "foreign-or-stale-change";
        return "[!] conflict " + claim.kind() + " target=" + claim.subjectId() + " outcome=" + outcome + " source=retained";
    }

    /**
     * A cell, sector metric or tissue clump is a visual substrate, not a
     * player-facing object. Its conflict remains an exact retained ledger fact
     * and is visible as a scar/changed geometry, but it must not manufacture a
     * map-wide cloud of technical boards. Objects the player can act on keep a
     * single local conflict board.
     */
    static boolean needsLocalBoard(String kind) {
        return switch (kind) {
            case "FACILITY", "RESOURCE_SITE", "ROUTE", "HIVE_ORGAN", "CARGO", "FIELD_POST", "FIELD_POST_MODULE",
                    "FIELD_LINK", "ACTIVITY", "ACTIVITY_BEACON", "ACTIVITY_CAMPAIGN_CAMP", "ACTIVITY_FRONT_RECON_SCOUT",
                    "ACTIVITY_FRONT_ASSEMBLY", "ACTIVITY_FRONT_ESTABLISH", "ACTIVITY_FRONT_CORDON", "ACTIVITY_FRONT_CLEAR_LINE",
                    "ACTIVITY_FRONT_HOLD", "ACTIVITY_FRONT_RESTORE", "ACTIVITY_FRONT_WITHDRAW_COLUMN",
                    "CHRYSALIS", "INTERACTION", "SOURCE_CONTAINER", "SOURCE_CONTAINER_RELOCATING",
                    "SOURCE_ACTOR_OBSTRUCTION" -> true;
            default -> false;
        };
    }

    /**
     * Keeps one local board per source object without allowing an old,
     * low-signal conflict to hide a later player-visible consequence. The
     * complete coordinate-level history remains in the presentation ledger.
     */
    static SourceGrayboxPresentationLedger.Claim preferredBoard(
            SourceGrayboxPresentationLedger.Claim first,
            SourceGrayboxPresentationLedger.Claim second) {
        int firstPriority = boardPriority(first);
        int secondPriority = boardPriority(second);
        if (secondPriority != firstPriority) return secondPriority > firstPriority ? second : first;
        return second.id().compareTo(first.id()) < 0 ? second : first;
    }

    private static int boardPriority(SourceGrayboxPresentationLedger.Claim claim) {
        // A replay is an exact player action which the source deliberately
        // retained rather than applying twice. It is the clearest local
        // explanation and must win over an older passive obstruction.
        if (claim.consumed()) return 3;
        if (!claim.installed()) return 2;
        return 1;
    }
}
