package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.group.*;
import java.util.List;

/** Validation-only reverse reads over existing canonical rows. The same owner
 * checks run in full diagnostic/recovery entry points and on invalidated rows. */
final class UnitGroupRelationshipChecks {
    private static final ImmutableReferenceIndex<FrontierWorldState, SubjectId, ClaimAllocation> CLAIMS =
            new ImmutableReferenceIndex<>(FungibleResourceLedger.MAX_CLAIMS, 1, UnitGroupValidationReads.SOURCES,
                    (s, c) -> c.purpose() == ClaimPurpose.EXPEDITION_SUPPLY
                            ? List.of(UnitGroupValidationReads.MISSIONS.at(c.claimantId())) : List.of(),
                    (s, id, c) -> UnitGroupRelationships.validateClaim(s, c));
    // Producer bounds: <=32 members, <=1024 shipments and <=320 supply
    // allocations (three addressed rows each), plus replenishment inputs.
    private static final int MAX_READS = 4096;
    private static final ImmutableReferenceIndex<FrontierWorldState, SubjectId, UnitGroup> GROUPS =
            new ImmutableReferenceIndex<>(UnitGroupState.MAX_GROUPS, MAX_READS, UnitGroupValidationReads.SOURCES,
                    UnitGroupValidationReads::group, (s, id, group) -> UnitGroupRelationships.validateGroup(s, group));
    private static final ImmutableReferenceIndex<FrontierWorldState, SubjectId, TransportMission> MISSIONS =
            new ImmutableReferenceIndex<>(UnitGroupState.MAX_GROUPS, MAX_READS, UnitGroupValidationReads.SOURCES,
                    UnitGroupValidationReads::mission, (s, id, mission) -> UnitGroupRelationships.validateMission(s, mission));
    private static final ImmutableReferenceIndex<FrontierWorldState, SubjectId, Shipment> SHIPMENTS =
            new ImmutableReferenceIndex<>(ShipmentState.MAX_SHIPMENTS, 1, UnitGroupValidationReads.SOURCES,
                    (s, shipment) -> shipment.transportMissionId().map(id -> List.of(UnitGroupValidationReads.MISSIONS.at(id))).orElse(List.of()),
                    (s, id, shipment) -> UnitGroupRelationships.validateShipment(s, shipment));
    private UnitGroupRelationshipChecks() { }
    static void validate(FrontierWorldState state) {
        CLAIMS.validate(state.bootstrap(), state.inventory().fungibleResources().claims(), state);
        GROUPS.validate(state.bootstrap(), state.unitGroups().groups(), state);
        MISSIONS.validate(state.bootstrap(), state.shipments().missions(), state);
        SHIPMENTS.validate(state.bootstrap(), state.shipments().shipments(), state);
    }
}
