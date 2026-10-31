package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.List;
import java.util.Set;
import static io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.*;
import static io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner.*;
import static io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentRoleSchema.*;
import static io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.*;

/** Closed wiring names family implementations; the shared equipment protocol does not. */
public final class EquipmentServiceComposition {
    private EquipmentServiceComposition() { }
    private static final EquipmentServiceCapabilities.Key ENGINEERING_ISSUE = new EquipmentServiceCapabilities.Key(
            ENGINEERING_WORKSITE, EQUIPMENT_ISSUE, ENGINEERING_EQUIPMENT_ISSUE);
    private static final EquipmentServiceCapabilities.Key ENGINEERING_RETURN = new EquipmentServiceCapabilities.Key(
            ENGINEERING_WORKSITE, EQUIPMENT_RETURN, ENGINEERING_EQUIPMENT_RETURN);
    private static final EquipmentServiceCapabilities.Key ASSAULT_ISSUE = new EquipmentServiceCapabilities.Key(
            PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT, EQUIPMENT_ISSUE, ASSAULT_EQUIPMENT_ISSUE);
    private static final EquipmentServiceCapabilities.Key ASSAULT_RETURN = new EquipmentServiceCapabilities.Key(
            PhysicalIntentLifecycleOwner.SETTLEMENT_ASSAULT, EQUIPMENT_RETURN, ASSAULT_EQUIPMENT_RETURN);
    private static final EquipmentServiceCapabilities.Key GUARD_ISSUE = new EquipmentServiceCapabilities.Key(
            PhysicalIntentLifecycleOwner.GUARD_DUTY, EQUIPMENT_ISSUE, GUARD_EQUIPMENT_ISSUE);
    private static final EquipmentServiceCapabilities.Key GUARD_RETURN = new EquipmentServiceCapabilities.Key(
            PhysicalIntentLifecycleOwner.GUARD_DUTY, EQUIPMENT_RETURN, GUARD_EQUIPMENT_RETURN);
    public static final EquipmentServiceCapabilities CAPABILITIES = new EquipmentServiceCapabilities(
            Set.of(ENGINEERING_ISSUE, ENGINEERING_RETURN, ASSAULT_ISSUE, ASSAULT_RETURN, GUARD_ISSUE, GUARD_RETURN), List.of(
            new EquipmentServiceCapabilities.Capability(GUARD_RETURN, PhysicalIntentSubjectRole.GUARD_DUTY, GUARD_RESIDENT,
                    FencedRecoveryAsset.EFFECT, ServiceAccessDemand.Kind.GUARD_EQUIPMENT,
                    GuardEquipmentStateSupport::validateReturn, GuardEquipmentStateSupport::validateCommitment,
                    GuardEquipmentStateSupport::serviceStation),
            new EquipmentServiceCapabilities.Capability(GUARD_ISSUE, PhysicalIntentSubjectRole.GUARD_DUTY, GUARD_RESIDENT,
                    FencedRecoveryAsset.EFFECT, ServiceAccessDemand.Kind.GUARD_EQUIPMENT,
                    GuardEquipmentStateSupport::validateIssue, GuardEquipmentStateSupport::validateCommitment,
                    GuardEquipmentStateSupport::serviceStation),
            new EquipmentServiceCapabilities.Capability(ENGINEERING_ISSUE, ENGINEERING_WORK_ORDER, ENGINEERING_WORKER, FencedRecoveryAsset.EFFECT, ServiceAccessDemand.Kind.ENGINEERING_EQUIPMENT,
                    EngineeringEquipmentStateSupport::validateIssue, EngineeringEquipmentStateSupport::validateCommitment,
                    EngineeringEquipmentStateSupport::issueStation),
            new EquipmentServiceCapabilities.Capability(ENGINEERING_RETURN, ENGINEERING_WORK_ORDER, ENGINEERING_WORKER, FencedRecoveryAsset.CONTAINER, ServiceAccessDemand.Kind.ENGINEERING_EQUIPMENT,
                    EngineeringEquipmentStateSupport::validateReturn, EngineeringEquipmentStateSupport::validateCommitment,
                    EngineeringEquipmentStateSupport::returnStation),
            new EquipmentServiceCapabilities.Capability(ASSAULT_ISSUE, PhysicalIntentSubjectRole.SETTLEMENT_ASSAULT, ASSAULT_DEFENDER, FencedRecoveryAsset.EFFECT, ServiceAccessDemand.Kind.DEFENDER_EQUIPMENT,
                    AssaultEquipmentStateSupport::validateIssue, AssaultEquipmentStateSupport::validateCommitment,
                    AssaultEquipmentStateSupport::serviceStation),
            new EquipmentServiceCapabilities.Capability(ASSAULT_RETURN, PhysicalIntentSubjectRole.SETTLEMENT_ASSAULT, ASSAULT_DEFENDER, FencedRecoveryAsset.EFFECT, ServiceAccessDemand.Kind.DEFENDER_EQUIPMENT,
                    AssaultEquipmentStateSupport::validateReturn, AssaultEquipmentStateSupport::validateCommitment,
                    AssaultEquipmentStateSupport::serviceStation)));
}
