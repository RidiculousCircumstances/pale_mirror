package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CargoCarrierReleaseStateTest {
    @Test
    void releaseAtomicallyInterruptsRouteAndKeepsExactStacksPhysical() {
        var engine = FrontierEngines.create(FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:cargo-release"), 91L));
        for (long tick = 100L; tick <= 2_550L; tick += 50L) engine.advanceTo(new SimInstant(tick), new WorkBudget(64, 512));
        FrontierWorldState before = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        RouteOperation operation = before.operations().get(new SubjectId("operation:supply-1-2"));
        SceneLeaseId leaseId = new SceneLeaseId("lease:cargo-release");
        SceneLease lease = new SceneLease(leaseId, before.bootstrap().worldId(), operation.id(), operation.cargoId(), operation.route().getFirst(), new SimInstant(2_550L),
                engine.checkpoint().revision().value(), SceneLeaseStatus.PREPARED,
                operation.participantIds().stream().map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(before.bootstrap().worldId(), actor))).toList());
        FrontierWorldState hot = before.prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        UUID carrier = CargoCarrierIdentity.id(lease);
        CargoCarrierReleased release = new CargoCarrierReleased(leaseId, operation.cargoId(), carrier, Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000051")));
        FrontierWorldState interrupted = hot.releaseCargoCarrier(release);

        SubjectId itemId = hot.inventory().cargo().get(operation.cargoId()).itemIds().getFirst();
        assertTrue(!interrupted.inventory().cargo().containsKey(operation.cargoId()));
        assertEquals(new InventoryCustody.WorldCarrier(carrier), interrupted.inventory().items().get(itemId).custody());
        assertEquals(List.of(itemId), interrupted.inventory().worldCarrierItems().get(carrier));
        assertEquals(ContractStatus.INTERRUPTED, interrupted.contracts().values().stream().filter(contract -> contract.cargoId().equals(operation.cargoId())).findFirst().orElseThrow().status());
        assertEquals(OperationStage.INTERRUPTED, interrupted.operations().get(operation.id()).stage());
        assertEquals(SceneLeaseStatus.DRAINING, interrupted.sceneLeases().get(leaseId).status());
        assertTrue(interrupted.strategicPlans().routeEngagements().isEmpty());
        assertEquals(release, FrontierWorldRuntimeDefinition.payloadCodecs().decode(release.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(release)));
        assertEquals(interrupted, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(interrupted)));
        assertThrows(IllegalArgumentException.class, () -> hot.releaseCargoCarrier(new CargoCarrierReleased(leaseId, operation.cargoId(), UUID.randomUUID(), release.observerPlayerId())));
    }

    @Test
    void releasePayloadRecoversThePriorRequiredObserverFormat() {
        SceneLeaseId leaseId = new SceneLeaseId("lease:cargo-release-legacy-payload");
        SubjectId cargoId = new SubjectId("cargo:legacy-payload");
        UUID carrierId = UUID.fromString("00000000-0000-0000-0000-000000000053");
        UUID observerId = UUID.fromString("00000000-0000-0000-0000-000000000054");
        byte[] legacy = FrontierWorldPayloadCodecs.encodeProduction(output -> {
            FrontierWorldPayloadCodecs.writeString(output, leaseId.value());
            FrontierWorldPayloadCodecs.writeSubject(output, cargoId);
            FrontierWorldPayloadCodecs.writeString(output, carrierId.toString());
            FrontierWorldPayloadCodecs.writeString(output, observerId.toString());
        });

        assertEquals(new CargoCarrierReleased(leaseId, cargoId, carrierId, Optional.of(observerId)),
                new CargoCarrierReleasedPayloadCodec().decode(legacy));
    }

    @Test
    void interruptedCargoSceneRecoversOnlyToDrainOriginalBodies() {
        FrontierWorldState before = FrontierDevelopmentScenarios.hotSceneStrikeState(new WorldId("frontier:cargo-release-recovery"), 91L);
        SceneEngagementCandidate candidate = before.coldEngagementSceneCandidates().getFirst();
        SceneLeaseId leaseId = new SceneLeaseId("lease:cargo-release-recovery");
        SceneLease lease = new SceneLease(leaseId, before.bootstrap().worldId(), candidate.operationId(), candidate.cargoId(), candidate.handoffPosition(), new SimInstant(2_600L), 1L,
                SceneLeaseStatus.PREPARED, Optional.of(candidate.engagementId()), candidate.actorIds().stream().map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(before.bootstrap().worldId(), actor))).toList());
        FrontierWorldState hot = before.prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        FrontierWorldState interrupted = hot.releaseCargoCarrier(new CargoCarrierReleased(leaseId, lease.cargoId(), CargoCarrierIdentity.id(lease), Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000052"))));

        assertEquals(RouteEngagementStatus.RESOLVED, interrupted.strategicPlans().routeEngagements().get(candidate.engagementId()).status());
        assertEquals(RouteEngagementOutcome.ABORTED, interrupted.strategicPlans().routeEngagements().get(candidate.engagementId()).outcome().orElseThrow());
        FrontierWorldState unknown = interrupted.transitionSceneLease(leaseId, SceneLeaseStatus.UNKNOWN_AFTER_RESTART);
        assertEquals(unknown, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(unknown)));
        assertThrows(IllegalArgumentException.class, () -> unknown.transitionSceneLease(leaseId, SceneLeaseStatus.HOT));
        List<SceneMemberPosition> bodies = lease.members().stream().map(member -> new SceneMemberPosition(member.actorId(),
                unknown.actorLocations().get(member.actorId()).position(), unknown.actorLocations().get(member.actorId()).condition().health())).toList();
        FrontierWorldState closed = unknown.transitionSceneLease(leaseId, SceneLeaseStatus.DRAINING).releaseSceneLease(leaseId, bodies);
        assertEquals(SceneLeaseStatus.CLOSED, closed.sceneLeases().get(leaseId).status());
        assertEquals(RouteEngagementOutcome.ABORTED, closed.strategicPlans().routeEngagements().get(candidate.engagementId()).outcome().orElseThrow());
    }
}
