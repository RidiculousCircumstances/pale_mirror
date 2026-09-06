package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;

import java.util.Map;

/** Closed owner for exact human-equipment issue and inverse-return boundaries. */
public final class HumanEquipmentStateSupport {
    private HumanEquipmentStateSupport() { }

    public static boolean owns(PhysicalIntent intent) {
        return intent.kind() == PhysicalIntentKind.EQUIPMENT_ISSUE || intent.kind() == PhysicalIntentKind.EQUIPMENT_RETURN;
    }

    public static void validateIntent(FrontierWorldState state, PhysicalIntent intent) {
        if (intent.kind() == PhysicalIntentKind.EQUIPMENT_ISSUE) EquipmentIssueStateSupport.validateIntent(state, intent);
        else if (intent.kind() == PhysicalIntentKind.EQUIPMENT_RETURN) EquipmentReturnStateSupport.validateIntent(state, intent);
    }

    public static FrontierWorldState complete(FrontierWorldState state, PhysicalIntent intent, PhysicalEffectObservation evidence,
                                              Map<PhysicalIntentId, PhysicalIntent> nextIntents) {
        if (intent.kind() == PhysicalIntentKind.EQUIPMENT_ISSUE) {
            return EquipmentIssueStateSupport.complete(state, intent, EquipmentIssueStateSupport.requireReceipt(evidence), nextIntents);
        }
        if (intent.kind() == PhysicalIntentKind.EQUIPMENT_RETURN) {
            return EquipmentReturnStateSupport.complete(state, intent, EquipmentReturnStateSupport.requireReceipt(evidence), nextIntents);
        }
        throw new IllegalArgumentException("not a human equipment intent");
    }
}
