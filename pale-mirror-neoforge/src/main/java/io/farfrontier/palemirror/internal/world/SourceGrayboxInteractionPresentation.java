package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.Locale;

/** Readable presentation of one immutable source interaction without granting the view ownership. */
final class SourceGrayboxInteractionPresentation {
    private SourceGrayboxInteractionPresentation() { }

    static String labelText(SourceGrayboxPresentationLedger ledger, ReferenceGrayboxSnapshot.Interaction interaction) {
        int availableSlots = availableSlots(ledger, interaction);
        String slotWeight = availableSlots == 0 ? "n/a" : number(interaction.totalWeight() / availableSlots);
        return "[X] " + interaction.kind() + " target=" + interaction.subjectId() + " total=" + number(interaction.totalWeight())
                + " available-slots=" + availableSlots + " each=" + slotWeight;
    }

    /** Counts breakable presentation slots without turning the ledger into simulation state. */
    private static int availableSlots(SourceGrayboxPresentationLedger ledger, ReferenceGrayboxSnapshot.Interaction interaction) {
        int result = 0;
        for (int index = 0; index < interaction.slots().size(); index++) {
            SourceGrayboxPresentationLedger.Claim claim = ledger.claim("interaction:" + interaction.id() + ":" + index);
            if (claim == null || !claim.consumed()) result++;
        }
        return result;
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
