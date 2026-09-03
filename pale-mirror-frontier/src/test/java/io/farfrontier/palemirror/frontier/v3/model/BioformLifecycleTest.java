package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.AmbientActorProcess;
import io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BioformLifecycleTest {
    @Test
    void bootstrapRetainsMostBioformsInExactCocoonsWithoutAmbientBodies() {
        FrontierWorldState state = initial();

        assertEquals(48, state.hiveColony().bioformLifecycles().size());
        assertEquals(44L, state.hiveColony().bioformLifecycles().values().stream()
                .filter(lifecycle -> lifecycle.phase() == BioformLifecyclePhase.DORMANT).count());
        assertEquals(4L, state.hiveColony().bioformLifecycles().values().stream()
                .filter(lifecycle -> lifecycle.phase() == BioformLifecyclePhase.ACTIVE).count());
        assertEquals(48L, state.hiveColony().bioformLifecycles().values().stream()
                .flatMap(lifecycle -> lifecycle.homeSlot().stream()).distinct().count());

        Bioform dormant = state.bootstrap().hive().bioforms().stream().filter(bioform ->
                state.hiveColony().bioformLifecycles().get(bioform.id()).phase() == BioformLifecyclePhase.DORMANT).findFirst().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> AmbientActorProcess.nextLease(state, dormant.id(), new SimInstant(1L)));
        assertFalse(HivePhysiologySupport.permitsAmbientLease(state.hiveColony(), dormant.id()));
    }

    @Test
    void observedCocoonLossReleasesTheSameExactBioformBeforeAmbientMaterialization() {
        FrontierWorldState state = initial();
        Bioform dormant = state.bootstrap().hive().bioforms().stream().filter(bioform ->
                state.hiveColony().bioformLifecycles().get(bioform.id()).phase() == BioformLifecyclePhase.DORMANT).findFirst().orElseThrow();
        BioformLifecycle lifecycle = state.hiveColony().bioformLifecycles().get(dormant.id());
        HiveCocoonSlot slot = lifecycle.homeSlot().orElseThrow();
        HiveOrgan hibernaculum = state.bootstrap().hive().organs().stream().filter(organ -> organ.id().equals(slot.hibernaculumId())).findFirst().orElseThrow();
        BlockPosition cocoon = HiveCocoonPlan.cocoonCell(hibernaculum, slot);

        FrontierWorldState released = state.recordPhysicalDelta(new PhysicalDelta(cocoon, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                Optional.of(dormant.id()), Optional.of(GrayboxSemanticPart.COCOON), "player:test"));

        assertEquals(BioformLifecyclePhase.WAKING, released.hiveColony().bioformLifecycles().get(dormant.id()).phase());
        assertEquals(BodyPosition.above(HiveCocoonPlan.wakingSurface(hibernaculum, slot)), released.actorLocations().get(dormant.id()).body());
        assertFalse(FrontierGrayboxPlan.compile(released).cells().containsKey(cocoon));
        var lease = AmbientActorProcess.nextLease(released, dormant.id(), new SimInstant(1L));
        released = AmbientLeaseStateProcess.prepare(released, lease);
        released = AmbientLeaseStateProcess.transition(released, dormant.id(), AmbientLeaseStatus.HOT);
        assertEquals(BioformLifecyclePhase.ACTIVE, released.hiveColony().bioformLifecycles().get(dormant.id()).phase());
    }

    @Test
    void cocoonCustodyRejectsAHiddenBodyMoveOrActiveLeaseBeforeMaterialization() {
        FrontierWorldState state = initial();
        Bioform dormant = state.bootstrap().hive().bioforms().stream().filter(bioform ->
                state.hiveColony().bioformLifecycles().get(bioform.id()).phase() == BioformLifecyclePhase.DORMANT).findFirst().orElseThrow();
        HiveCocoonSlot slot = state.hiveColony().bioformLifecycles().get(dormant.id()).homeSlot().orElseThrow();
        HiveOrgan hibernaculum = state.bootstrap().hive().organs().stream()
                .filter(organ -> organ.id().equals(slot.hibernaculumId())).findFirst().orElseThrow();

        assertThrows(IllegalArgumentException.class, () -> state.withActorBody(dormant.id(),
                BodyPosition.above(HiveCocoonPlan.wakingSurface(hibernaculum, slot))));

        AmbientActorLease forbidden = new AmbientActorLease(dormant.id(), state.actorLocations().get(dormant.id()).body(),
                new SimInstant(1L), 1L, AmbientLeaseStatus.HOT, AmbientGoalKind.PATROL,
                state.actorLocations().get(dormant.id()).body());
        assertThrows(IllegalArgumentException.class, () -> HiveLifecycleStateSupport.validateCocoonCustody(state.bootstrap(),
                state.hiveColony(), state.actorLocations(), java.util.Map.of(dormant.id(), forbidden)));
    }

    @Test
    void lifecycleAndCocoonCustodySurviveTheCurrentFreshSchemaRoundTrip() {
        FrontierWorldState source = initial();
        FrontierWorldState restored = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(source));
        assertEquals(source.hiveColony().bioformLifecycles(), restored.hiveColony().bioformLifecycles());
    }

    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:bioform-lifecycle"), 18L));
    }
}
