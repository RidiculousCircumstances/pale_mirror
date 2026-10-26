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
    @Test void activityOwnerRetainsOnlyItsExactCurrentGoalWithoutRenewingBodyAuthority() {
        var configuration = FrontierV3FixtureCatalog.resourceSiteHarvestConfiguration(
                new WorldId("frontier:owner-retained-goal"), 421L);
        var state = configuration.initialState();
        var site = new io.farfrontier.palemirror.frontier.v3.api.SubjectId("site:1-wheat-field");
        var job = state.resourceSites().site(site).harvestJobs().values().stream()
                .reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        state = ModeledActorBodyFacts.present(state, job.workerId());
        var execution = state.actorExecutions().actors().get(job.workerId()).current().orElseThrow();
        var source = new AtomicReference<>(Optional.of(state));
        var permission = new FrontierV3ActorActuation(new ActorActuationId(
                ActorBodyAuthority.current(state, job.workerId()), execution), source::get);
        var original = ResourceSiteHarvestGoal.current(state, job).movementOrder();
        assertTrue(permission.currentActivityGoal(original));
        var cycle = state.resourceSites().cycle(site);
        int next = (job.progress().nextCropSlotIndex() + 1) % cycle.layout().cells().size();
        var changed = state.withResourceSites(state.resourceSites().replace(
                state.resourceSites().site(site).retargetHarvestCell(job, next, cycle)));
        source.set(Optional.of(changed));
        var following = changed.resourceSites().site(site).harvestJob(job.id()).orElseThrow();
        assertFalse(permission.currentActivityGoal(original), "same execution cannot retain yesterday's cell");
        var followingOrder = ResourceSiteHarvestGoal.current(changed, following).movementOrder();
        assertTrue(permission.currentActivityGoal(followingOrder));
        var position = changed.resourceSite(site).cropSlots().getFirst();
        var conflict = new ResourceSiteConflictObserved(site, position, ResourceSiteDiagnosticProducer.ORDINARY_OBSERVATION_MISMATCH);
        var blocked = io.farfrontier.palemirror.frontier.v3.process.ResourceSiteProcess.reduceConflict(changed, site, conflict);
        source.set(Optional.of(blocked));
        assertFalse(permission.currentActivityGoal(followingOrder), "a retained causal job is not continuing motion permission");
        source.set(Optional.empty());
        assertFalse(permission.currentActivityGoal(original));
    }

    @Test void scoutPermissionRetainsItsSemanticGoalVersionAcrossPhysicalMovementAndRejectsAbaTargetReuse() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:scout-motion-goal"), 71L));
        var scout = state.bootstrap().hive().bioforms().stream().filter(Bioform::isScout).findFirst().orElseThrow();
        state = io.farfrontier.palemirror.frontier.v3.process.HiveScoutPatrolProcess.reduceStarted(state, state.bootstrap().hive().id(),
                io.farfrontier.palemirror.frontier.v3.process.HiveScoutPatrolProcess.start(state, scout.id()));
        var scope = io.farfrontier.palemirror.frontier.v3.process.AmbientActorProcess.nextLease(state, scout.id(),
                io.farfrontier.palemirror.frontier.v3.api.SimInstant.ZERO);
        state = io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.prepare(state, scope);
        state = ModeledActorBodyFacts.present(state, scout.id());
        state = io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.transition(state, scout.id(), AmbientLeaseStatus.HOT);
        scope = state.ambientLeases().get(scout.id());
        var journey = io.farfrontier.palemirror.frontier.v3.process.HiveScoutPatrolProcess.journey(state, scout.id());
        assertTrue(FrontierV3ScoutPatrolMotion.current(state, journey, scope));
        var goal = FrontierV3GoalNavigation.Goal.routed(journey.order(), java.util.List.of(), state.bootstrap().bounds());
        assertEquals(TraversalCapability.PEDESTRIAN, goal.capability(), "the scout uses the existing registered ground provider");
        assertEquals(Optional.of(journey.order()), goal.order());
        state = ModeledActorBodyFacts.inspected(state, scout.id(), journey.target().standingBody());
        assertTrue(FrontierV3ScoutPatrolMotion.current(state, journey, scope), "observed movement preserves the goal authority");
        var newGoal = journey.next(journey.target()); // Same coordinates and scope are not the same semantic command.
        state = state.withChanges(FrontierWorldStateUpdate.begin().strategicPlans(state.strategicPlans().withScoutPatrol(newGoal)));
        assertFalse(FrontierV3ScoutPatrolMotion.current(state, journey, scope));
        assertTrue(FrontierV3ScoutPatrolMotion.current(state, newGoal, scope));
    }
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
                null, null, null, null, null, null, true));
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

    @Test void retainedMovementAuthorityRejectsReplacedGoalAndRemovalWithoutReauthorizingTheBody() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:retained-motion"), 71L));
        var actor = state.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var execution = state.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
        state = ActorExecutionComposition.LIFECYCLE.prepareVacant(state, execution).commit(state, FrontierWorldStateUpdate.begin());
        state = ActorBodyAuthority.demand(state, actor);
        var body = ActorBodyAuthority.current(state, actor);
        state = ActorBodyAuthority.running(state, body);
        var owner = state.humanPopulation().residents().get(actor).settlementId();
        var container = state.inventory().containers().values().stream()
                .filter(value -> value.ownerId().equals(owner) && value.storage().isPresent()).findFirst().orElseThrow();
        var point = new ServicePointId(ServicePointId.Kind.SETTLEMENT_DEPOT, owner, container.id());
        var order = new io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder(actor, actor, 0, 1,
                java.util.List.of(state.actorLocations().get(actor).supportingSurface()), TraversalCapability.PEDESTRIAN,
                io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder.ArrivalPolicy.EXACT_STATION);
        var movement = new io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement(order, 0,
                new io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementContext.ResourceAccessExit(point, actor), execution);
        var source = new AtomicReference<>(Optional.of(state));
        var permit = new FrontierV3ActorActuation(new ActorActuationId(body, execution), source::get);
        assertFalse(permit.currentMovement(order));
        state = state.withChanges(FrontierWorldStateUpdate.begin().actorMovements(java.util.Map.of(actor, movement)));
        source.set(Optional.of(state));
        assertTrue(permit.currentMovement(order));
        var replacement = new io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder(actor, actor, 0, 2,
                order.legalStations(), order.capability(), order.arrivalPolicy());
        source.set(Optional.of(state.withChanges(FrontierWorldStateUpdate.begin().actorMovements(java.util.Map.of(actor,
                new io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement(replacement, 1, movement.context(), execution))))));
        assertFalse(permit.currentMovement(order), "same owner, actor and target do not preserve a superseded order");
        assertTrue(permit.currentMovement(replacement));
        source.set(Optional.of(state.withChanges(FrontierWorldStateUpdate.begin().actorMovements(java.util.Map.of()))));
        assertFalse(permit.currentMovement(order), "retirement revokes motion immediately without an elapsed-time timeout");
        source.set(Optional.empty());
        assertFalse(permit.currentMovement(order));
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
