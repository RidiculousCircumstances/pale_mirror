package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExplosionObservationTest {
    @Test
    void onlyALivingBomberMayDurablyPrepareAnExplosion() {
        FrontierWorldState state = hotBomberState(new WorldId("frontier:explosion-planning"));
        SubjectId engagement = FrontierSceneBehaviors.logistics(state.sceneLeases().values().stream().findFirst().orElseThrow()).engagementId().orElseThrow();
        Bioform bomber = bomber(state, engagement);
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:explosion-planning"), PhysicalIntentKind.EXPLOSION,
                PhysicalIntentStatus.PREPARED, bomber.id(), List.of(bomber.id(), engagement), position(state, bomber.id()), 4, PhysicalPostcondition.EXPLOSION_OBSERVED);
        CommandId id = new CommandId("command:explosion-planning");
        assertInstanceOf(CommandPlan.Accepted.class, FrontierWorldRuntimeDefinition.planCommand(state, new FrontierCommand(1, id,
                state.bootstrap().worldId(), new io.farfrontier.palemirror.frontier.v3.api.Revision(0), new io.farfrontier.palemirror.frontier.v3.api.SimInstant(0),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), new PhysicalIntentPrepared(intent))));
        Bioform guard = state.bootstrap().hive().bioforms().stream().filter(value -> value.role() == BioformRole.GUARD).findFirst().orElseThrow();
        PhysicalIntent invalid = new PhysicalIntent(new PhysicalIntentId("intent:explosion-non-bomber"), PhysicalIntentKind.EXPLOSION,
                PhysicalIntentStatus.PREPARED, guard.id(), List.of(guard.id(), engagement), position(state, guard.id()), 4, PhysicalPostcondition.EXPLOSION_OBSERVED);
        assertInstanceOf(CommandPlan.Rejected.class, FrontierWorldRuntimeDefinition.planCommand(state, new FrontierCommand(1,
                new CommandId("command:explosion-non-bomber"), state.bootstrap().worldId(), new io.farfrontier.palemirror.frontier.v3.api.Revision(0),
                new io.farfrontier.palemirror.frontier.v3.api.SimInstant(0), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                CauseChain.root(new CommandId("command:explosion-non-bomber")), new PhysicalIntentPrepared(invalid))));
    }

    @Test
    void durableExplosionReceiptBindsExactGeometryAndRoundTrips() {
        FrontierWorldState state = hotBomberState(new WorldId("frontier:explosion-receipt"));
        SubjectId engagement = FrontierSceneBehaviors.logistics(state.sceneLeases().values().stream().findFirst().orElseThrow()).engagementId().orElseThrow();
        SubjectId bomber = bomber(state, engagement).id();
        FixedPosition origin = new FixedPosition(FixedScalar.whole(-400), FixedScalar.whole(64), FixedScalar.whole(400));
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:explosion-test"), PhysicalIntentKind.EXPLOSION,
                PhysicalIntentStatus.PREPARED, bomber, List.of(bomber, engagement), origin, 4, PhysicalPostcondition.EXPLOSION_OBSERVED);
        ExplosionObservation receipt = new ExplosionObservation(new PhysicalObservationId("observation:explosion-test"), intent.id(), origin, 4, 12, 8, List.of(), List.of(), 0, 0);

        FrontierWorldState running = state.preparePhysicalIntent(intent)
                .transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> running.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.CONFIRMED,
                Optional.of(new ExplosionObservation(receipt.id(), intent.id(), origin, 3, 12, 8, List.of(), List.of(), 0, 0))));

        FrontierWorldState confirmed = running.transitionPhysicalIntent(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        assertEquals(PhysicalIntentStatus.CONFIRMED, confirmed.physicalIntents().get(intent.id()).status());
        assertEquals(receipt, confirmed.physicalObservations().get(receipt.id()));
        assertEquals(confirmed, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(confirmed)));
        PhysicalIntentTransition transition = new PhysicalIntentTransition(intent.id(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        assertEquals(transition, FrontierWorldRuntimeDefinition.payloadCodecs().decode(transition.type(),
                FrontierWorldRuntimeDefinition.payloadCodecs().encode(transition)));
    }

    @Test
    void explosionReceiptRetainsExactEntityItemAndInfectionEvidence() {
        FixedPosition origin = new FixedPosition(FixedScalar.whole(4), FixedScalar.whole(64), FixedScalar.whole(-4));
        ExplosionObservation receipt = new ExplosionObservation(new PhysicalObservationId("observation:explosion-rich"), new PhysicalIntentId("intent:explosion-rich"), origin,
                4, 9, 6, List.of(new ExplosionEntityImpact(UUID.fromString("00000000-0000-0000-0000-000000000055"), "minecraft:zombie",
                Optional.of(new SubjectId("bioform:rich")), true)), List.of(new ExplosionItemImpact(new SubjectId("item:rich"), ExplosionItemImpact.Outcome.DESTROYED)), 2, 1);
        PhysicalIntentTransition transition = new PhysicalIntentTransition(receipt.intentId(), PhysicalIntentStatus.CONFIRMED, Optional.of(receipt));
        assertEquals(transition, FrontierWorldRuntimeDefinition.payloadCodecs().decode(transition.type(), FrontierWorldRuntimeDefinition.payloadCodecs().encode(transition)));
        assertThrows(IllegalArgumentException.class, () -> new ExplosionObservation(receipt.id(), receipt.intentId(), origin, 4, 9, 6,
                List.of(new ExplosionEntityImpact(UUID.fromString("00000000-0000-0000-0000-000000000055"), "minecraft:zombie", Optional.empty(), false),
                        new ExplosionEntityImpact(UUID.fromString("00000000-0000-0000-0000-000000000055"), "minecraft:zombie", Optional.empty(), false)), List.of(), 0, 0));
    }

    private static FixedPosition position(FrontierWorldState state, SubjectId actorId) {
        BlockPosition value = state.actorLocations().get(actorId).position();
        return new FixedPosition(FixedScalar.whole(value.x()), FixedScalar.whole(value.y()), FixedScalar.whole(value.z()));
    }

    private static FrontierWorldState hotBomberState(WorldId worldId) {
        FrontierWorldState state = FrontierDevelopmentScenarios.hotSceneStrikeState(worldId, 91L);
        SceneEngagementCandidate candidate = state.coldEngagementSceneCandidates().getFirst();
        io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId leaseId = new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:explosion-test");
        SceneLease lease = FrontierTestSceneLeases.exact(state, leaseId, candidate.operationId(), candidate.cargoId(),
                candidate.handoffPosition(), new io.farfrontier.palemirror.frontier.v3.api.SimInstant(2_601L),
                1L, java.util.Optional.of(candidate.engagementId()), candidate.actorIds());
        return state.prepareSceneLease(lease).transitionSceneLease(lease.id(), SceneLeaseStatus.HOT);
    }

    private static Bioform bomber(FrontierWorldState state, SubjectId engagementId) {
        java.util.Set<SubjectId> attackers = new java.util.HashSet<>(state.strategicPlans().routeEngagements().get(engagementId).attackerIds());
        return state.bootstrap().hive().bioforms().stream().filter(value -> value.role() == BioformRole.BOMBER && attackers.contains(value.id())).findFirst().orElseThrow();
    }
}
