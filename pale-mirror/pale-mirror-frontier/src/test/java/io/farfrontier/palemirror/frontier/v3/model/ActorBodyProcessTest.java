package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorBodyProcessTest {
    @Test void resourceBindingsUseCommonBodyLifetimeNotAnActivityScope() {
        var field = ResourceSiteHarvestProcessTest.coldHarvestWithCargo(125L);
        var job = field.job();
        var actor = job.workerId();
        var state = ActorBodyAuthority.demand(field.state(), actor);
        var body = ActorBodyAuthority.current(state, actor);
        var resources = state.inventory().fungibleResources();
        var account = resources.accounts().get(job.actorAccountId());
        var stack = new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ActorHand(actor,
                SceneLease.deterministicEntityId(state.bootstrap().worldId(), actor)), "minecraft:wheat",
                account.lotQuantities().values().stream().mapToInt(Integer::intValue).sum());
        var bound = resources.rebind(account.id(), 1L, FungiblePhysicalObservation.bind(resources, account.id(), 1L, java.util.List.of(stack)));
        final var prepared = state;
        assertThrows(IllegalArgumentException.class, () -> prepared.withInventory(prepared.inventory().withFungibleResources(bound)),
                "an uncreated body cannot justify a physical stack binding");
        state = ActorBodyAuthority.running(state, body);
        state = state.withInventory(state.inventory().withFungibleResources(bound));
        assertTrue(state.sceneLeases().values().stream().noneMatch(scope -> scope.retainsMemberCustody(actor)));
        assertTrue(state.ambientLeases().get(actor) == null || state.ambientLeases().get(actor).status() == AmbientLeaseStatus.CLOSED);
        var location = state.actorLocations().get(actor);
        var execution = state.actorExecutions().actors().get(actor).current().orElseThrow();
        var after = ActorBodyAuthority.died(state, new ActorBodyDied(body, location.body(), location.condition().health(),
                java.util.Optional.empty(), java.util.Optional.of(execution), "environment"), FrontierActorDeathConsequences.INSTANCE, 27_000L);
        assertEquals(state.inventory(), after.inventory(), "the original hand evidence is not a death drop or loss receipt");
        assertFalse(ActorBodyAuthority.retainsPhysicalCustody(after, actor));
        assertThrows(IllegalArgumentException.class, () -> ActorBodyAuthority.requireActuation(after, new ActorActuationId(body, execution)));
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        assertEquals(after.inventory(), codec.decode(codec.encode(after)).inventory());
    }
    @Test void deathBeforeFoodPickupReleasesOnlyTheMealClaimAndCancelsItsExactContinuation() {
        var field = ResourceSiteHarvestProcessTest.coldHarvestWithCargo(125L);
        var state = field.state();
        var farmer = field.job().workerId();
        var work = state.actorExecutions().actors().get(farmer).current().orElseThrow();
        var settlement = state.humanPopulation().resident(farmer).settlementId();
        var depot = FrontierWorldState.depotId(settlement);
        var food = state.inventory().fungibleResources().transformCold(ReferenceContainerCustody.scopeId(depot),
                java.util.Map.of(new SubjectId("lot:bootstrap-1-wheat"), 64), java.util.Map.of(),
                new ResourceLot(new SubjectId("lot:meal-death-bread"), settlement, ResidentMeal.BREAD_KIND, 64, "test", java.util.List.of()));
        state = state.withInventory(state.inventory().withFungibleResources(food));
        var started = io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess.selectSourceAtYield(state, farmer, 27_000L).orElseThrow();
        state = io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess.reduceStarted(state, farmer, started);
        var meal = state.humanPopulation().meals().get(farmer);
        assertEquals(work, state.actorExecutions().actors().get(farmer).suspended().orElseThrow());
        assertFalse(meal.carriesFood());
        assertTrue(meal.pendingPhysicalStep().isEmpty());
        state = ActorBodyAuthority.demand(state, farmer);
        var body = ActorBodyAuthority.current(state, farmer);
        state = ActorBodyAuthority.running(state, body);
        var location = state.actorLocations().get(farmer);
        var death = new ActorBodyDied(body, location.body(), location.condition().health(), java.util.Optional.empty(),
                java.util.Optional.of(meal.executionId()), "environment");
        var base = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), 125L);
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        var action = io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess.progress(meal, 27_100L);
        var engine = FrontierEngines.create(new FrontierEngineConfiguration<>(base.worldId(), state, new SimInstant(27_001L),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), codec, base.projectionMapper(), base.limits(),
                java.util.List.of(action), base.transactionCommitter(), base.stateValidator(), base.executionMetrics(), base.kernelQuarantineReporter()));
        var result = engine.submit(command(engine.checkpoint(), "command:unbegun-meal-death", death));
        assertInstanceOf(CommandResult.Accepted.class, result, result::toString);
        var after = codec.decode(engine.checkpoint().canonicalState());
        assertEquals(ActorLifeStatus.DEAD, after.actorLocations().get(farmer).condition().status());
        assertFalse(ActorBodyAuthority.retainsPhysicalCustody(after, farmer));
        assertFalse(after.humanPopulation().meals().containsKey(farmer));
        assertTrue(after.actorExecutions().actors().get(farmer).current().isEmpty());
        assertEquals(work, after.actorExecutions().actors().get(farmer).suspended().orElseThrow());
        assertEquals(ResourceSiteConflictReason.WORKER_DIED, after.resourceSites().site(field.site()).conflictDisposition().orElseThrow().reason());
        assertFalse(after.inventory().fungibleResources().claims().containsKey(meal.claimId()));
        assertEquals(state.inventory().fungibleResources().lots(), after.inventory().fungibleResources().lots());
        assertEquals(state.inventory().fungibleResources().accounts().get(meal.sourceAccountId()).lotQuantities(),
                after.inventory().fungibleResources().accounts().get(meal.sourceAccountId()).lotQuantities());
        assertEquals(state.inventory().fungibleResources().accounts().get(field.job().actorAccountId()),
                after.inventory().fungibleResources().accounts().get(field.job().actorAccountId()), "the farmer's separate harvest is not the meal portion");
        assertFalse(engine.checkpoint().schedules().stream().anyMatch(due -> due.id().equals(action.id())),
                "removing the meal without cancelling this exact due action would quarantine its later turn");
        assertEquals(EngineStatus.Kind.ACTIVE, engine.status().kind());
        assertInstanceOf(CommandResult.Rejected.class, engine.submit(command(engine.checkpoint(), "command:unbegun-meal-death-late", death)));
    }
    @Test void scopeFreeHarvestDeathAccruesOnlyThroughDeathAndStopsTheRetainedLabourClock() {
        var field = ResourceSiteHarvestProcessTest.coldHarvestAfterSteps(125L, 0);
        var job = field.job();
        var state = field.state();
        var definition = state.bootstrap().ruleset().workCatalog().require(state.resourceSite(job.siteId()).kind().resourceKind(),
                ResourceSiteHarvestLabour.operation(state, job));
        var work = WorkProgress.pending(definition.workUnits(), 22_100L).resume(22_100L, 1000, 23_100L);
        state = state.withResourceSites(state.resourceSites().replace(state.resourceSites().site(job.siteId()).withHarvestLabour(job, work)));
        state = ActorBodyAuthority.demand(state, job.workerId());
        var body = ActorBodyAuthority.current(state, job.workerId());
        state = ActorBodyAuthority.running(state, body);
        var location = state.actorLocations().get(job.workerId());
        var death = new ActorBodyDied(body, location.body(), location.condition().health(), java.util.Optional.empty(),
                state.actorExecutions().actors().get(job.workerId()).current(), "environment");
        var after = ActorBodyAuthority.died(state, death, FrontierActorDeathConsequences.INSTANCE, 22_101L);
        var paused = after.resourceSites().site(job.siteId()).harvestJob(job.id()).orElseThrow().progress().work().orElseThrow();
        assertFalse(paused.running());
        assertEquals(work.completedAt(22_101L), paused.completedMilliWork());
        assertEquals(22_101L, paused.evaluatedAtTick());
        assertEquals(paused.completedMilliWork(), paused.completedAt(30_000L));
        assertEquals(state.inventory(), after.inventory());
    }
    @Test void exactDeathNotifiesCurrentOrSuspendedHarvestWithoutDependingOnItsScene() {
        for (var scope : java.util.List.of("none", "suspended", "hot")) {
            var field = ResourceSiteHarvestProcessTest.coldHarvestWithCargo(125L);
            var job = field.job();
            var state = field.state();
            var owner = state.actorExecutions().actors().get(job.workerId()).current().orElseThrow();
            long tick = 30_000L;
            if (scope.equals("suspended")) {
                var presence = state.actorExecutions().next(job.workerId(), ActorActivityKind.PRESENCE, job.workerId());
                state = ActorExecutionComposition.LIFECYCLE.prepareBegin(state, presence, tick)
                        .commit(state, FrontierWorldStateUpdate.begin());
                assertEquals(owner, state.actorExecutions().actors().get(job.workerId()).suspended().orElseThrow());
            }
            if (scope.equals("hot")) {
                var scene = ResourceSiteHarvestProcessTest.newHarvestLease(state, field.site(), job, "death-owner");
                state = ResourceSiteHarvestProcessTest.confirmedPhysicalParticipants(state.prepareSceneLease(scene), scene)
                        .transitionSceneLease(scene.id(), SceneLeaseStatus.HOT);
            } else {
                state = ActorBodyAuthority.demand(state, job.workerId());
                state = ActorBodyAuthority.running(state, ActorBodyAuthority.current(state, job.workerId()));
                assertTrue(state.sceneLeases().isEmpty());
            }
            var body = ActorBodyAuthority.current(state, job.workerId());
            var location = state.actorLocations().get(job.workerId());
            var death = new ActorBodyDied(body, location.body(), location.condition().health(), java.util.Optional.empty(),
                    state.actorExecutions().actors().get(job.workerId()).current(), "environment");
            var base = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), 125L);
            var engine = FrontierEngines.create(new FrontierEngineConfiguration<>(base.worldId(), state, new SimInstant(tick),
                    base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec(), base.projectionMapper(),
                    base.limits(), java.util.List.of(), base.transactionCommitter(), base.stateValidator(),
                    base.executionMetrics(), base.kernelQuarantineReporter()));
            assertInstanceOf(CommandResult.Accepted.class, engine.submit(command(engine.checkpoint(), "command:field-death-" + scope, death)), scope);
            var after = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
            var site = after.resourceSites().site(job.siteId());
            assertEquals(ActorLifeStatus.DEAD, after.actorLocations().get(job.workerId()).condition().status(), scope);
            assertFalse(ActorBodyAuthority.retainsPhysicalCustody(after, job.workerId()));
            assertEquals(ResourceSitePhase.CONFLICT, site.phase(), scope);
            assertEquals(ResourceSiteConflictReason.WORKER_DIED, site.conflictDisposition().orElseThrow().reason(), scope);
            assertEquals(StrategicTaskStatus.BLOCKED, after.strategicPlans().tasks().get(job.taskId()).status(), scope);
            assertEquals(state.inventory(), after.inventory(), "death must not delete or duplicate the retained harvest cargo");
            assertEquals(job.progress(), site.harvestJob(job.id()).orElseThrow().progress());
            assertTrue(after.actorExecutions().owns(job.workerId(), ActorActivityKind.FIELD_HARVEST, job.id())
                    || after.actorExecutions().actors().get(job.workerId()).suspended().equals(java.util.Optional.of(owner)),
                    "the unresolved owner's exact claim is not a fabricated completion");
            var events = io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess.planColdProgress(after,
                    ResourceSiteHarvestContinuation.at(job, tick + 1L));
            assertTrue(events.stream().anyMatch(event -> event.payload() instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Consumed));
            assertFalse(events.stream().anyMatch(event -> event.payload() instanceof io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Rescheduled),
                    "a dead worker must not enter an ordinary-work retry loop");
            assertEquals(EngineStatus.Kind.ACTIVE, engine.status().kind(), scope);
            assertInstanceOf(CommandResult.Rejected.class, engine.submit(command(engine.checkpoint(), "command:field-death-late-" + scope, death)));
        }
    }
    @Test void staleDeathIsRejectedBeforeWalAndExactDeathNeedsNoActivityPresentationScope() {
        var base = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:body-death"), 91L);
        var state = base.initialState();
        var actor = state.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var execution = state.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
        state = ActorExecutionComposition.LIFECYCLE.prepareVacant(state, execution).commit(state, FrontierWorldStateUpdate.begin());
        state = ActorBodyAuthority.demand(state, actor);
        var body = ActorBodyAuthority.current(state, actor);
        state = ActorBodyAuthority.running(state, body);
        var location = state.actorLocations().get(actor);
        var death = new ActorBodyDied(body, location.body(), location.condition().health(), java.util.Optional.empty(),
                java.util.Optional.of(execution), "environment");
        var configuration = new FrontierEngineConfiguration<>(base.worldId(), state, base.initialInstant(),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(),
                base.limits(), java.util.List.of(), base.transactionCommitter(), base.stateValidator(),
                base.executionMetrics(), base.kernelQuarantineReporter());
        var engine = FrontierEngines.create(configuration);
        var before = engine.checkpoint();
        var stale = new ActorBodyDied(new ActorBodyId(actor, body.physicalEpoch() + 1L), location.body(),
                location.condition().health(), death.observedBody(), death.expectedExecution(), death.cause());
        assertInstanceOf(CommandResult.Rejected.class, engine.submit(command(before, "command:body-death-stale", stale)));
        assertInstanceOf(CommandResult.Rejected.class, engine.submit(command(before, "command:body-death-old-ambient",
                new FrontierPayload() { @Override public String type() { return "frontier.ambient_actor_died"; } })));
        assertArrayEquals(before.canonicalState(), engine.checkpoint().canonicalState());
        assertEquals(before.revision(), engine.checkpoint().revision());
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(command(before, "command:body-death", death)));
        var after = base.stateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(ActorLifeStatus.DEAD, after.actorLocations().get(actor).condition().status());
        assertTrue(after.actorExecutions().actors().get(actor).current().isEmpty());
        assertFalse(ActorBodyAuthority.retainsPhysicalCustody(after, actor));
        assertEquals(EngineStatus.Kind.ACTIVE, engine.status().kind());
        assertInstanceOf(CommandResult.Rejected.class, engine.submit(command(engine.checkpoint(), "command:body-death-replayed", death)));
        assertEquals(EngineStatus.Kind.ACTIVE, engine.status().kind());
    }
    @Test void activityHotTransitionsCannotManufacturePhysicalPresence() {
        var state = ResourceSiteHarvestProcessTest.initial();
        var actor = state.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var lease = io.farfrontier.palemirror.frontier.v3.process.AmbientActorProcess.nextLease(state, actor, SimInstant.ZERO);
        var prepared = io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.prepare(state, lease);
        assertThrows(IllegalArgumentException.class, () ->
                io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.transition(prepared, actor, AmbientLeaseStatus.HOT));
        assertEquals(FencedRecoveryPhase.PREPARED, ActorBodyAuthority.require(prepared, ActorBodyAuthority.current(prepared, actor)).phase());
        var cold = ResourceSiteHarvestProcessTest.coldHarvestAfterSteps(125L, 0);
        var scene = ResourceSiteHarvestProcessTest.newHarvestLease(cold.state(), cold.site(), cold.job(), "physical-independent");
        var preparedScene = cold.state().prepareSceneLease(scene);
        assertThrows(IllegalArgumentException.class, () -> preparedScene.transitionSceneLease(scene.id(), SceneLeaseStatus.HOT));
        var id = new CommandId("command:scene-without-physical-admission");
        var request = new FrontierCommand(FrontierCommand.LEGACY_SCHEMA_VERSION, id, preparedScene.bootstrap().worldId(),
                Revision.ZERO, new SimInstant(22_300L), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                CauseChain.root(id), new SceneLeaseTransition(scene.id(), SceneLeaseStatus.HOT));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Rejected.class,
                io.farfrontier.palemirror.frontier.v3.process.FrontierWorldProcessCatalog.planCommand("logistics-scenes", preparedScene, request),
                "a scene cannot defer the missing-body refusal until replay and quarantine the engine");
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan.Accepted.class,
                io.farfrontier.palemirror.frontier.v3.process.FrontierWorldProcessCatalog.planCommand("logistics-scenes",
                        ResourceSiteHarvestProcessTest.confirmedPhysicalParticipants(preparedScene, scene), request));
        assertEquals(FencedRecoveryPhase.PREPARED, ActorBodyAuthority.require(preparedScene,
                ActorBodyAuthority.current(preparedScene, cold.job().workerId())).phase());
    }
    @Test void physicalAdmissionIsReadyBeforeActivityReadinessAndDoesNotFinishItsOwner() {
        var base = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:body-present"), 91L);
        var actor = base.initialState().humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var state = base.initialState();
        var execution = state.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
        state = ActorExecutionComposition.LIFECYCLE.prepareVacant(state, execution).commit(state, FrontierWorldStateUpdate.begin());
        state = ActorBodyAuthority.demand(state, actor);
        var body = ActorBodyAuthority.current(state, actor);
        var location = state.actorLocations().get(actor);
        var actual = new BodyPosition(location.body().x() + 1, location.body().y(), location.body().z());
        var payload = new ActorBodyPresent(body, location.body(), location.condition().health(), actual, FixedScalar.whole(19));
        var codecs = io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldPayloadCodecs.create();
        assertEquals(payload, codecs.decode(payload.type(), codecs.encode(payload)));
        var truncated = java.util.Arrays.copyOf(codecs.encode(payload), codecs.encode(payload).length - 1);
        assertThrows(IllegalArgumentException.class, () -> codecs.decode(payload.type(), truncated));
        var configuration = new FrontierEngineConfiguration<>(base.worldId(), state, base.initialInstant(),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(),
                base.limits(), base.initialSchedules(), base.transactionCommitter(), base.stateValidator(),
                base.executionMetrics(), base.kernelQuarantineReporter());
        var engine = FrontierEngines.create(configuration);
        var obsolete = new ActorBodyPresent(new ActorBodyId(actor, body.physicalEpoch() + 1L),
                location.body(), location.condition().health(), actual, FixedScalar.whole(19));
        var before = engine.checkpoint();
        assertInstanceOf(CommandResult.Rejected.class, engine.submit(command(before, "command:body-present-wrong", obsolete)));
        assertArrayEquals(before.canonicalState(), engine.checkpoint().canonicalState());
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(command(before, "command:body-present", payload)));
        var after = base.stateCodec().decode(engine.checkpoint().canonicalState());
        ActorBodyAuthority.requireActuation(after, new ActorActuationId(body, execution));
        assertEquals(actual, after.actorLocations().get(actor).body());
        assertEquals(FixedScalar.whole(19), after.actorLocations().get(actor).condition().health());
        assertEquals(state.actorExecutions(), after.actorExecutions());
        assertEquals(state.humanPopulation(), after.humanPopulation());
        assertEquals(state.inventory(), after.inventory());
        assertInstanceOf(CommandResult.Rejected.class,
                engine.submit(command(engine.checkpoint(), "command:body-present-late", payload)));
        var ambiguous = ActorBodyAuthority.isolate(after, body, "restart inspection");
        var inspected = ActorBodyAuthority.present(ambiguous, new ActorBodyPresent(body, actual, FixedScalar.whole(19), actual, FixedScalar.whole(19)));
        ActorBodyAuthority.requireActuation(inspected, new ActorActuationId(body, execution));
        assertEquals(after.actorExecutions(), inspected.actorExecutions());
    }
    @Test void savedUnloadCommitsSupportedPositionAndAbsenceWithoutFinishingTheActivity() {
        var base = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:body-unload"), 91L);
        var actor = base.initialState().humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var state = base.initialState();
        var execution = state.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
        state = ActorExecutionComposition.LIFECYCLE.prepareVacant(state, execution).commit(state, FrontierWorldStateUpdate.begin());
        state = ActorBodyAuthority.demand(state, actor);
        var body = ActorBodyAuthority.current(state, actor); state = ActorBodyAuthority.running(state, body);
        var original = state.actorLocations().get(actor);
        var position = new BodyPosition(original.body().x() + 1, original.body().y(), original.body().z());
        var payload = new ActorBodyUnloaded(body, original.body(), original.condition().health(), position,
                FixedScalar.whole(19), java.util.Optional.of(execution));
        var codecs = io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldPayloadCodecs.create();
        assertEquals(payload, codecs.decode(payload.type(), codecs.encode(payload)));
        var truncated = java.util.Arrays.copyOf(codecs.encode(payload), codecs.encode(payload).length - 1);
        assertThrows(IllegalArgumentException.class, () -> codecs.decode(payload.type(), truncated));
        var configuration = new FrontierEngineConfiguration<>(base.worldId(), state, base.initialInstant(),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(),
                base.limits(), base.initialSchedules(), base.transactionCommitter(), base.stateValidator(),
                base.executionMetrics(), base.kernelQuarantineReporter());
        var engine = FrontierEngines.create(configuration);
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(command(engine.checkpoint(), "command:body-unload", payload)));
        var checkpoint = engine.checkpoint();
        var after = base.stateCodec().decode(checkpoint.canonicalState());
        assertFalse(ActorBodyAuthority.retainsPhysicalCustody(after, actor));
        assertEquals(position, after.actorLocations().get(actor).body());
        assertEquals(FixedScalar.whole(19), after.actorLocations().get(actor).condition().health());
        assertEquals(state.actorExecutions(), after.actorExecutions());
        assertEquals(state.inventory(), after.inventory());
        assertEquals(state.humanPopulation(), after.humanPopulation());
        assertInstanceOf(CommandResult.Rejected.class,
                engine.submit(command(checkpoint, "command:body-unload-duplicate", payload)));
        assertArrayEquals(checkpoint.canonicalState(), engine.checkpoint().canonicalState());
    }

    @Test void unloadCannotAcknowledgeAnotherExecutionEpochOrCanonicalBaseline() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:body-unload-fences"), 91L));
        var actor = state.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var execution = state.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
        state = ActorExecutionComposition.LIFECYCLE.prepareVacant(state, execution).commit(state, FrontierWorldStateUpdate.begin());
        state = ActorBodyAuthority.demand(state, actor);
        var body = ActorBodyAuthority.current(state, actor); state = ActorBodyAuthority.running(state, body);
        final var basis = state;
        var location = state.actorLocations().get(actor);
        var foreignEpoch = new ActorBodyId(actor, body.physicalEpoch() + 1L);
        var foreignExecution = new ActorExecutionId(actor, execution.activityKind(), execution.activityOwnerId(), execution.generation() + 1L);
        for (var invalid : java.util.List.of(
                new ActorBodyUnloaded(foreignEpoch, location.body(), location.condition().health(), location.body(), location.condition().health(), java.util.Optional.of(execution)),
                new ActorBodyUnloaded(body, location.body(), location.condition().health(), location.body(), location.condition().health(), java.util.Optional.of(foreignExecution)),
                new ActorBodyUnloaded(body, location.body(), FixedScalar.whole(1), location.body(), location.condition().health(), java.util.Optional.of(execution)),
                new ActorBodyUnloaded(body, location.body(), location.condition().health(), location.body(), location.condition().health(), java.util.Optional.empty()))) {
            assertThrows(IllegalArgumentException.class, () -> ActorBodyAuthority.unloaded(basis, invalid));
        }
        var valid = new ActorBodyUnloaded(body, location.body(), location.condition().health(), location.body(), location.condition().health(), java.util.Optional.of(execution));
        var released = ActorBodyAuthority.released(state, body);
        var replacement = ActorBodyAuthority.demand(released, actor);
        final var next = ActorBodyAuthority.running(replacement, ActorBodyAuthority.current(replacement, actor));
        assertThrows(IllegalArgumentException.class, () -> ActorBodyAuthority.unloaded(next, valid));
    }
    @Test void registeredPhysicalBoundaryCommitsOneExactReleaseAndRejectsItsLateDuplicate() {
        var base = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:body-release"), 91L);
        var actor = base.initialState().humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var state = ActorBodyAuthority.demand(base.initialState(), actor);
        var body = ActorBodyAuthority.current(state, actor);
        state = ActorBodyAuthority.running(state, body);
        var configuration = new FrontierEngineConfiguration<>(base.worldId(), state, base.initialInstant(),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(),
                base.limits(), base.initialSchedules(), base.transactionCommitter(), base.stateValidator(),
                base.executionMetrics(), base.kernelQuarantineReporter());
        var engine = FrontierEngines.create(configuration);
        var before = engine.checkpoint();
        var obsolete = new ActorBodyReleased(new ActorBodyId(actor, body.physicalEpoch() + 1L));
        var rejected = engine.submit(command(before, "command:body-wrong-epoch", obsolete));
        assertInstanceOf(CommandResult.Rejected.class, rejected);
        assertEquals(before.revision(), engine.checkpoint().revision());
        assertArrayEquals(before.canonicalState(), engine.checkpoint().canonicalState());
        assertInstanceOf(CommandResult.Accepted.class,
                engine.submit(command(before, "command:body-release", new ActorBodyReleased(body))));
        var after = engine.checkpoint();
        var recovered = base.stateCodec().decode(after.canonicalState());
        assertFalse(ActorBodyAuthority.retainsPhysicalCustody(recovered, actor));
        assertEquals(state.actorLocations(), recovered.actorLocations());
        assertEquals(state.actorExecutions(), recovered.actorExecutions());
        assertEquals(state.inventory(), recovered.inventory());
        assertInstanceOf(CommandResult.Rejected.class,
                engine.submit(command(after, "command:body-late-release", new ActorBodyReleased(body))));
        assertEquals(after.revision(), engine.checkpoint().revision());
        assertArrayEquals(after.canonicalState(), engine.checkpoint().canonicalState());
    }
    @Test void commonInspectionRestoresOnlyPhysicalEvidenceAndRejectsStaleOrUncreatedBodiesBeforeWal() {
        var base = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:body-inspection"), 91L);
        var actor = base.initialState().humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var execution = base.initialState().actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
        var state = ActorExecutionComposition.LIFECYCLE.prepareVacant(base.initialState(), execution)
                .commit(base.initialState(), FrontierWorldStateUpdate.begin());
        state = ActorBodyAuthority.demand(state, actor);
        var body = ActorBodyAuthority.current(state, actor);
        var location = state.actorLocations().get(actor);
        var observed = location.body().offset(1, 0, 0);
        var receipt = new ActorBodyInspected(body, ActorBodyInspected.Source.INDEXED_LIVING, location.body(), location.condition().health(),
                observed, location.condition().health(), java.util.Optional.of(execution));
        assertThrows(IllegalArgumentException.class, () -> ActorBodyAuthority.inspected(
                ActorBodyAuthority.demand(base.initialState(), actor), receipt), "inspection is not first admission");
        state = ModeledActorBodyFacts.present(state, actor);
        state = ActorBodyAuthority.isolate(state, body, "exact physical inspection needed");
        var configuration = new FrontierEngineConfiguration<>(base.worldId(), state, base.initialInstant(),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(),
                base.limits(), java.util.List.of(), base.transactionCommitter(), base.stateValidator(),
                base.executionMetrics(), base.kernelQuarantineReporter());
        var engine = FrontierEngines.create(configuration);
        var before = engine.checkpoint();
        var stale = new ActorBodyInspected(body, ActorBodyInspected.Source.INDEXED_LIVING, location.body(), location.condition().health(),
                observed, location.condition().health(), java.util.Optional.empty());
        assertInstanceOf(CommandResult.Rejected.class, engine.submit(command(before, "command:inspection-stale", stale)));
        assertArrayEquals(before.canonicalState(), engine.checkpoint().canonicalState());
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        var encoded = codecs.encode(receipt);
        assertEquals(receipt, codecs.decode(receipt.type(), encoded));
        assertThrows(IllegalArgumentException.class, () -> codecs.decode(receipt.type(), java.util.Arrays.copyOf(encoded, encoded.length - 1)));
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(command(before, "command:inspection", receipt)));
        var after = base.stateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(observed, after.actorLocations().get(actor).body());
        assertEquals(FencedRecoveryPhase.RUNNING, ActorBodyAuthority.require(after, body).phase());
        assertEquals(state.actorExecutions(), after.actorExecutions());
        assertEquals(state.inventory(), after.inventory());
        assertEquals(state.resourceSites(), after.resourceSites());
        assertEquals(state.sceneLeases(), after.sceneLeases());
        assertEquals(after, base.stateCodec().decode(base.stateCodec().encode(after)));
        assertInstanceOf(CommandResult.Rejected.class, engine.submit(command(engine.checkpoint(), "command:inspection-late", receipt)));
        assertEquals(EngineStatus.Kind.ACTIVE, engine.status().kind());
    }
    @Test void savedCheckpointRecordsPoseWithoutGrantingActuationThenExactUnloadAtomicallyRetiresAmbiguity() {
        var base = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:body-saved-checkpoint"), 91L);
        var actor = base.initialState().humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var execution = base.initialState().actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
        var state = ActorExecutionComposition.LIFECYCLE.prepareVacant(base.initialState(), execution)
                .commit(base.initialState(), FrontierWorldStateUpdate.begin());
        state = ModeledActorBodyFacts.present(state, actor);
        var body = ActorBodyAuthority.current(state, actor);
        state = ActorBodyAuthority.isolate(state, body, "restart body needs exact inspection");
        var location = state.actorLocations().get(actor);
        var actual = location.body().offset(1, 0, 0);
        var observation = new ActorBodyInspected(body, ActorBodyInspected.Source.SAVED_DEPARTURE,
                location.body(), location.condition().health(), actual, FixedScalar.whole(19), java.util.Optional.of(execution));
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(observation, codecs.decode(observation.type(), codecs.encode(observation)));
        assertThrows(IllegalArgumentException.class, () -> ActorBodyInspected.Source.fromWireTag(0));
        var configuration = new FrontierEngineConfiguration<>(base.worldId(), state, base.initialInstant(),
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(),
                base.limits(), java.util.List.of(), base.transactionCommitter(), base.stateValidator(),
                base.executionMetrics(), base.kernelQuarantineReporter());
        var engine = FrontierEngines.create(configuration);
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(command(engine.checkpoint(), "command:saved-checkpoint", observation)));
        var checkpointed = base.stateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(actual, checkpointed.actorLocations().get(actor).body());
        assertEquals(FixedScalar.whole(19), checkpointed.actorLocations().get(actor).condition().health());
        assertEquals(FencedRecoveryPhase.AMBIGUOUS, ActorBodyAuthority.require(checkpointed, body).phase());
        assertThrows(IllegalArgumentException.class, () -> ActorBodyAuthority.requireActuation(checkpointed, new ActorActuationId(body, execution)));
        assertEquals(state.inventory(), checkpointed.inventory());
        assertEquals(state.actorExecutions(), checkpointed.actorExecutions());
        assertEquals(checkpointed, base.stateCodec().decode(base.stateCodec().encode(checkpointed)));
        var unload = new ActorBodyUnloaded(body, actual, FixedScalar.whole(19), actual, FixedScalar.whole(19), java.util.Optional.of(execution));
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(command(engine.checkpoint(), "command:saved-absence", unload)));
        var absent = base.stateCodec().decode(engine.checkpoint().canonicalState());
        assertFalse(ActorBodyAuthority.retainsPhysicalCustody(absent, actor));
        assertEquals(checkpointed.actorLocations(), absent.actorLocations());
        assertEquals(checkpointed.inventory(), absent.inventory());
        assertEquals(checkpointed.actorExecutions(), absent.actorExecutions());
        assertInstanceOf(CommandResult.Rejected.class, engine.submit(command(engine.checkpoint(), "command:late-saved-absence", unload)));
        assertEquals(EngineStatus.Kind.ACTIVE, engine.status().kind());
    }

    @Test void processReleaseCannotReplaceTheCommonBodyObservationOrGrantColdCustody() {
        var cold = ResourceSiteHarvestProcessTest.coldHarvestAfterSteps(125L, 0);
        var scene = ResourceSiteHarvestProcessTest.newHarvestLease(cold.state(), cold.site(), cold.job(), "common-release-observation");
        var state = ResourceSiteHarvestProcessTest.confirmedPhysicalParticipants(cold.state().prepareSceneLease(scene), scene)
                .transitionSceneLease(scene.id(), SceneLeaseStatus.HOT).transitionSceneLease(scene.id(), SceneLeaseStatus.DRAINING);
        var actor = cold.job().workerId();
        var location = state.actorLocations().get(actor);
        var displaced = location.body().offset(1, 0, 0);
        var positions = java.util.List.of(new SceneMemberPosition(actor, displaced, FixedScalar.whole(19)));
        assertThrows(IllegalArgumentException.class, () -> state.releaseSceneLease(scene.id(), positions),
                "scene closure cannot manufacture the body's displacement or injury");
        var recorded = ActorBodyAuthority.inspected(state, new ActorBodyInspected(ActorBodyAuthority.current(state, actor),
                ActorBodyInspected.Source.INDEXED_LIVING, location.body(), location.condition().health(), displaced,
                FixedScalar.whole(19), state.actorExecutions().actors().get(actor).current()));
        var closed = recorded.releaseSceneLease(scene.id(), positions);
        assertEquals(SceneLeaseStatus.CLOSED, closed.sceneLeases().get(scene.id()).status());
        assertEquals(recorded.actorLocations(), closed.actorLocations());
        assertEquals(recorded.inventory(), closed.inventory());
        assertEquals(recorded.actorExecutions(), closed.actorExecutions());
        assertTrue(ActorBodyAuthority.retainsPhysicalCustody(closed, actor), "scope closure does not unload the living body");

        var ambientState = ResourceSiteHarvestProcessTest.initial();
        var resident = ambientState.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var lease = io.farfrontier.palemirror.frontier.v3.process.AmbientActorProcess.nextLease(ambientState, resident, SimInstant.ZERO);
        ambientState = io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.prepare(ambientState, lease);
        ambientState = ModeledActorBodyFacts.present(ambientState, resident);
        ambientState = io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.transition(ambientState, resident, AmbientLeaseStatus.HOT);
        final var draining = io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.transition(ambientState, resident, AmbientLeaseStatus.DRAINING);
        var current = draining.actorLocations().get(resident);
        assertThrows(IllegalArgumentException.class, () -> io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.release(draining,
                new AmbientLeaseReleased(resident, current.body().offset(1, 0, 0), current.condition().health())));
        var released = io.farfrontier.palemirror.frontier.v3.process.AmbientLeaseStateProcess.release(draining,
                new AmbientLeaseReleased(resident, current.body(), current.condition().health()));
        assertEquals(draining.actorLocations(), released.actorLocations());
        assertTrue(ActorBodyAuthority.retainsPhysicalCustody(released, resident));
    }
    private static FrontierCommand command(CheckpointImage checkpoint, String id, FrontierPayload payload) {
        var commandId = new CommandId(id);
        return new FrontierCommand(FrontierCommand.LEGACY_SCHEMA_VERSION, commandId, checkpoint.worldId(),
                checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                CauseChain.root(commandId), payload);
    }
}
