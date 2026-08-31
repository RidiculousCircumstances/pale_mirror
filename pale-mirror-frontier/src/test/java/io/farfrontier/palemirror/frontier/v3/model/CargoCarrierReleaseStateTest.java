package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEngine;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CargoCarrierReleaseStateTest {
    @Test
    void releaseAtomicallyInterruptsRouteAndKeepsExactStacksPhysical() {
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.routeSceneReturnConfiguration(
                new WorldId("frontier:cargo-release"), 91L));
        FrontierWorldState before = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        RouteOperation operation = before.operations().get(new SubjectId("operation:supply-1-2"));
        SceneLeaseId leaseId = new SceneLeaseId("lease:cargo-release");
        SceneLease lease = FrontierTestSceneLeases.exact(before, leaseId, operation.id(), operation.cargoId(),
                operation.currentPosition(), new SimInstant(2_550L), engine.checkpoint().revision().value(),
                Optional.empty(), operation.participantIds());
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
        SceneLease lease = FrontierTestSceneLeases.exact(before, leaseId, candidate.operationId(), candidate.cargoId(),
                candidate.handoffPosition(), new SimInstant(2_600L), 1L, Optional.of(candidate.engagementId()),
                candidate.actorIds());
        FrontierWorldState hot = before.prepareSceneLease(lease).transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        CargoCarrierReleased released = new CargoCarrierReleased(leaseId, FrontierSceneBehaviors.logistics(lease).cargoId(),
                CargoCarrierIdentity.id(lease), Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000052")));
        FrontierWorldState interrupted = hot.releaseCargoCarrier(released);

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

    @Test
    void physicalCargoLossClosesHotSceneWithoutInventingColdCombat() {
        WorldId world = new WorldId("frontier:cargo-release-live-path");
        FrontierEngine<FrontierWorldProjection> engine = FrontierEngines.create(
                FrontierV3FixtureCatalog.hotSceneStrikeConfiguration(world, 91L));
        FrontierWorldState before = state(engine);
        SceneEngagementCandidate candidate = before.coldEngagementSceneCandidates().getFirst();
        SceneLeaseId leaseId = new SceneLeaseId("lease:cargo-release-live-path");
        SceneLease lease = FrontierTestSceneLeases.exact(before, leaseId, candidate.operationId(), candidate.cargoId(),
                candidate.handoffPosition(), engine.checkpoint().instant(), engine.checkpoint().revision().value(),
                Optional.of(candidate.engagementId()), candidate.actorIds());

        submit(engine, world, "prepare", new SceneLeasePrepared(lease));
        submit(engine, world, "hot", new SceneLeaseTransition(leaseId, SceneLeaseStatus.HOT));
        submit(engine, world, "cargo-loss", new CargoCarrierReleased(leaseId, FrontierSceneBehaviors.logistics(lease).cargoId(), CargoCarrierIdentity.id(lease),
                Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000055"))));
        FrontierWorldState interrupted = state(engine);
        assertEquals(OperationStage.INTERRUPTED, interrupted.operations().get(candidate.operationId()).stage());
        assertEquals(RouteEngagementOutcome.ABORTED,
                interrupted.strategicPlans().routeEngagements().get(candidate.engagementId()).outcome().orElseThrow());
        assertEquals(SceneLeaseStatus.DRAINING, interrupted.sceneLeases().get(leaseId).status());

        List<SceneMemberPosition> survivors = lease.members().stream().map(member -> new SceneMemberPosition(member.actorId(),
                interrupted.actorLocations().get(member.actorId()).position(),
                interrupted.actorLocations().get(member.actorId()).condition().health())).toList();
        submit(engine, world, "drain", new SceneLeaseReleased(leaseId, survivors));

        FrontierWorldState closed = state(engine);
        assertEquals(SceneLeaseStatus.CLOSED, closed.sceneLeases().get(leaseId).status());
        assertEquals(OperationStage.INTERRUPTED, closed.operations().get(candidate.operationId()).stage());
        assertEquals(RouteEngagementOutcome.ABORTED,
                closed.strategicPlans().routeEngagements().get(candidate.engagementId()).outcome().orElseThrow());
        assertTrue(engine.checkpoint().schedules().stream().noneMatch(action -> action.kind().equals("frontier.hive_route_engagement.combat")),
                "an aborted physical scene must not manufacture a COLD combat continuation");
    }

    private static FrontierWorldState state(FrontierEngine<FrontierWorldProjection> engine) {
        return new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
    }

    private static void submit(FrontierEngine<FrontierWorldProjection> engine, WorldId world, String suffix,
                               FrontierPayload payload) {
        CommandId command = new CommandId("command:cargo-release-live-path-" + suffix);
        var checkpoint = engine.checkpoint();
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(new FrontierCommand(1, command, world,
                checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                CauseChain.root(command), payload)));
    }
}
