package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FrontierV3ActorActuationTest {
    @Test void canonicalOrIncompleteBodyMetadataCannotUseUnversionedNavigationBeforeItsFirstCapturedCommand() {
        var empty = new net.minecraft.nbt.CompoundTag();
        assertTrue(FrontierV3GoalNavigation.permitsUnmodeledNavigation(empty, false));
        assertFalse(FrontierV3GoalNavigation.permitsUnmodeledNavigation(empty, true));
        for (var key : java.util.List.of(FrontierV3ActorCarrierComposition.ACTOR_KEY, FrontierV3ActorCarrierComposition.KIND_KEY,
                FrontierV3ActorCarrierComposition.OWNER_KEY, FrontierV3ActorCarrierComposition.REPRESENTATION_KEY,
                FrontierV3ActorCarrierComposition.REVISION_KEY, FrontierV3ActorCarrierComposition.EPOCH_KEY,
                FrontierV3ActorBodyController.RESIDENCE_KEY)) {
            var partial = new net.minecraft.nbt.CompoundTag();
            partial.putBoolean(key, true); // Even an invalid field type cannot fall through to unmodeled motion.
            assertFalse(FrontierV3GoalNavigation.permitsUnmodeledNavigation(partial, false));
        }
    }
    @Test void migratedPhysicalCallersCannotIssueUnversionedNavigationOrBypassTheNavigatorWithStop() throws Exception {
        // Census of every compiled production class, not a list that can omit a new caller.
        for (var method : java.util.List.of("pursue", "pursueLocalFeetTarget", "pursueRetainedEdge", "stop")) {
            var unversioned = FrontierV3ActorCarrierCompositionTest.methodCallBoundaries(FrontierV3GoalNavigation.class,
                    method, descriptor -> !descriptor.contains("FrontierV3ActorActuation;"));
            assertEquals(method.equals("stop") ? java.util.Set.of(FrontierV3ControlledMobMotion.class.getName())
                    : java.util.Set.of(), unversioned, "production unversioned navigation: " + method);
        }
        assertEquals(java.util.Set.of(FrontierV3ControlledMobMotion.class.getName()),
                FrontierV3ActorCarrierCompositionTest.methodCallBoundaries(FrontierV3ControlledMobMotion.class, "stop"),
                "a family cannot stop a native path behind the shared owner");
        for (var method : java.util.List.of("moveToward", "moveWithinWorldBounds", "moveWithinEnvelope",
                "moveWithinSemanticEnvelope", "pursueRetainedCheckpoint", "pursueRetainedSemanticCheckpoint",
                "followContinuously", "tendCurrentCrop", "holdRetainedCheckpoint"))
            assertTrue(FrontierV3ActorCarrierCompositionTest.methodCallBoundaries(FrontierV3ControlledMobMotion.class, method)
                    .isEmpty(), "an active legacy actuator remains reachable: " + method);
        assertEquals(java.util.Set.of(FrontierV3GoalNavigation.class.getName()),
                FrontierV3ActorCarrierCompositionTest.methodCallBoundaries(FrontierV3RouteNavigation.class, "pursue"));
        assertEquals(java.util.Set.of(FrontierV3RouteNavigation.class.getName()),
                FrontierV3ActorCarrierCompositionTest.methodCallBoundaries(FrontierV3MinecraftGoalNavigation.class, "pursue"));
        assertEquals(java.util.Set.of(FrontierV3GoalNavigation.class.getName()),
                FrontierV3ActorCarrierCompositionTest.methodCallBoundaries(FrontierV3MinecraftGoalNavigation.class, "advanceAtEntityBoundary"));
        assertEquals(java.util.Set.of(FrontierV3GoalNavigation.class.getName()),
                FrontierV3ActorCarrierCompositionTest.methodCallBoundaries(FrontierV3MinecraftGoalNavigation.class, "controls"));
        assertEquals(java.util.Set.of(FrontierV3GoalNavigation.class.getName(), FrontierV3RouteNavigation.class.getName(),
                FrontierV3MinecraftGoalNavigation.class.getName()),
                FrontierV3ActorCarrierCompositionTest.methodCallBoundaries(FrontierV3MinecraftGoalNavigation.class, "stop"));
    }

    @Test void physicalProviderCannotMintOrOmitTheNavigatorsCapturedPermission() throws Exception {
        assertTrue(FrontierV3GoalNavigation.ProviderPermission.class.isSealed());
        for (var permission : FrontierV3GoalNavigation.ProviderPermission.class.getPermittedSubclasses()) {
            for (var constructor : permission.getDeclaredConstructors())
                assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
            assertEquals(java.util.Set.of(FrontierV3GoalNavigation.class.getName()),
                    FrontierV3ActorCarrierCompositionTest.methodCallBoundaries(permission, "<init>"),
                    "only common navigation can grant physical provider permission");
        }
        assertThrows(NullPointerException.class, () -> FrontierV3RouteNavigation.pursue(null, null, null, null));
        assertThrows(NullPointerException.class, () -> FrontierV3MinecraftGoalNavigation.pursue(
                null, null, null, null, null, null));
    }

    @Test void retainedCommandCannotAcquireSuccessorExecutionOrNewBodyEpochAtTheDeferredTick() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:actuation-fence"), 71L));
        var actor = state.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var execution = state.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
        state = ActorExecutionComposition.LIFECYCLE.prepareVacant(state, execution)
                .commit(state, FrontierWorldStateUpdate.begin());
        state = ActorBodyAuthority.demand(state, actor);
        var body = ActorBodyAuthority.current(state, actor);
        state = ActorBodyAuthority.running(state, body);
        var source = new AtomicReference<>(Optional.of(state));
        var declaration = FrontierV3ActorCarrierComposition.fromCanonical(state, actor, ActorKind.RESIDENT,
                FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY,
                ActorBodyId.entityId(state.bootstrap().worldId(), actor),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 0L, body.physicalEpoch());
        var old = new FrontierV3ActorActuation(new ActorActuationId(body, execution), source::get);
        assertTrue(old.current(declaration));
        var successor = state.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
        state = ActorExecutionComposition.LIFECYCLE.prepareBegin(state, successor, 1L)
                .commit(state, FrontierWorldStateUpdate.begin());
        source.set(Optional.of(state));
        var next = new FrontierV3ActorActuation(new ActorActuationId(body, successor), source::get);
        assertFalse(old.current(declaration), "cached path/STOP cannot be blessed by a fresh current-execution lookup");
        assertTrue(next.current(declaration), "activity replacement preserves the same physical incarnation");
        var codec = new FrontierWorldStateCodec();
        source.set(Optional.of(codec.decode(codec.encode(state))));
        assertFalse(old.current(declaration));
        assertTrue(next.current(declaration));
        state = ActorBodyAuthority.released(state, body);
        source.set(Optional.of(state));
        assertFalse(next.current(declaration), "unloaded representation cannot retain actuator permission");
        state = ActorBodyAuthority.demand(state, actor);
        var replacement = ActorBodyAuthority.current(state, actor);
        state = ActorBodyAuthority.running(state, replacement);
        source.set(Optional.of(state));
        assertFalse(next.current(declaration), "same UUID at a new body epoch is not yesterday's actuator");
        source.set(Optional.empty());
        assertFalse(next.current(declaration), "stopped or quarantined runtime grants no motion");
    }

    @Test void bodyDeclarationCannotSubstituteUuidKindRepresentationOrEpochForExactAuthority() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:actuation-tuple"), 71L));
        var actor = state.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var execution = state.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
        state = ActorExecutionComposition.LIFECYCLE.prepareVacant(state, execution)
                .commit(state, FrontierWorldStateUpdate.begin());
        state = ActorBodyAuthority.demand(state, actor);
        var body = ActorBodyAuthority.current(state, actor);
        state = ActorBodyAuthority.running(state, body);
        var source = new AtomicReference<>(Optional.of(state));
        var permit = new FrontierV3ActorActuation(new ActorActuationId(body, execution), source::get);
        var valid = FrontierV3ActorCarrierComposition.fromCanonical(state, actor, ActorKind.RESIDENT,
                FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY,
                ActorBodyId.entityId(state.bootstrap().worldId(), actor),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 0L, body.physicalEpoch());
        assertTrue(permit.current(valid));
        assertFalse(permit.current(valid.inactiveCarrier()));
        assertFalse(permit.current(new FrontierV3ActorCarrierComposition.Declaration(actor, ActorKind.BIOFORM,
                valid.owner(), valid.entityId(), valid.representation(), valid.authorityRevision(), valid.epoch())));
        assertFalse(permit.current(new FrontierV3ActorCarrierComposition.Declaration(actor, valid.kind(),
                valid.owner(), new UUID(0L, 1L), valid.representation(), valid.authorityRevision(), valid.epoch())));
        assertFalse(permit.current(valid.liveBody(valid.owner(), valid.authorityRevision(), valid.epoch() + 1L)));
    }
}
