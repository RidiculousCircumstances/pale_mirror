package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.EngineStatus;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEngine;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.HiveMobilizationProcess;
import io.farfrontier.palemirror.frontier.v3.process.HiveSettlementAssaultProcess;
import io.farfrontier.palemirror.frontier.v3.process.ResourceSiteHarvestProcess;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3FixtureCatalogTest {
    @Test void freshBakeryProfileStartsBeforeTheFirstWheatTransfer() {
        var configuration = FrontierV3FixtureCatalog.configuration("bakery-fresh-job",
                new WorldId("frontier:bakery-fresh-profile"), 41L);
        var state = configuration.initialState();
        assertEquals(1, state.productionJobs().size());
        var job = state.productionJobs().values().iterator().next();
        assertEquals(BakeryWorkState.Phase.DEPOT_PICKUP, job.bakeryWork().orElseThrow().phase());
        assertTrue(state.inventory().fungibleResources().lots().containsKey(job.consumedItemId()));
        assertTrue(!state.inventory().fungibleResources().lots().containsKey(job.outputItemId()));
        assertTrue(state.sceneLeases().isEmpty());
        assertTrue(!configuration.initialSchedules().isEmpty());
    }

    @Test void fungibleProductionProfileRetainsOrdinaryUnfinishedColdWork() {
        var configuration = FrontierV3FixtureCatalog.configuration("fungible-production-work",
                new WorldId("frontier:fungible-work-profile"), 41L);
        var state = configuration.initialState();
        assertEquals(1, state.productionJobs().size());
        var job = state.productionJobs().values().iterator().next();
        assertEquals(BakeryWorkState.Phase.PROCESSING, job.bakeryWork().orElseThrow().phase());
        assertEquals(7, job.bakeryWork().orElseThrow().completedWorkTicks());
        assertEquals(new SubjectId("job:production-" + job.taskId().value().substring("task:".length())), job.id());
        assertTrue(SettlementWorkPolicy.permissions(state, job.settlementId())
                .permits(ResidentWorkKind.BAKING, job.workerId()));
        assertEquals(new SubjectId("lot:" + job.id().value().substring("job:".length()) + "-bread"), job.outputItemId());
        assertTrue(state.inventory().fungibleResources().lots().containsKey(job.consumedItemId()));
        assertTrue(!state.inventory().fungibleResources().lots().containsKey(job.outputItemId()));
        assertTrue(state.sceneLeases().isEmpty(), "profile begins COLD, not in an injected HOT scene");
        assertTrue(!configuration.initialSchedules().isEmpty(), "ordinary work continuation must be retained");
        assertEquals("disposable_lite", FrontierV3FixtureCatalog.profile("fungible-production-work").allowedRunner());
    }
    @Test void twoLotProductionProfileRetainsOneOrdinaryJobAndBothExactInputPortions() {
        var configuration = FrontierV3FixtureCatalog.configuration("fungible-production-two-lot-work",
                new WorldId("frontier:fungible-two-lot-work-profile"), 41L);
        var state = configuration.initialState();
        assertEquals(1, state.productionJobs().size());
        var job = state.productionJobs().values().iterator().next();
        assertEquals(BakeryWorkState.Phase.PROCESSING, job.bakeryWork().orElseThrow().phase());
        assertEquals(7, job.bakeryWork().orElseThrow().completedWorkTicks());
        var held = assertInstanceOf(ProductionInputHold.FungibleCold.class, job.inputHold());
        assertEquals(java.util.Map.of(new SubjectId("lot:production-two-field-first"), 32,
                new SubjectId("lot:production-two-field-second"), 32), held.inputLots());
        assertEquals(1, state.inventory().fungibleResources().claims().size());
        assertEquals(new SubjectId("claim:" + job.id().value().substring("job:".length())), held.claimId());
        assertEquals(java.util.Map.of(held.claimId(), 64), state.inventory().fungibleResources().accounts()
                .get(job.bakeryWork().orElseThrow().stationAccountId()).claimQuantities());
        assertTrue(state.sceneLeases().isEmpty());
        assertTrue(!configuration.initialSchedules().isEmpty());
    }
    @Test void coldBomberProfileRetainsAnOrdinaryDueActionThatEmitsTheExactUnvisitedCause() {
        var configuration = FrontierV3FixtureCatalog.coldBomberAftermathConfiguration(new WorldId("frontier:cold-bomber-profile"), 41L);
        FrontierWorldState state = configuration.initialState();
        var due = configuration.initialSchedules().stream().filter(action -> action.kind().equals("frontier.settlement_assault.combat")).findFirst().orElseThrow();
        var aftermath = HiveSettlementAssaultProcess.planCombat(state, due).stream().map(ProposedEvent::payload)
                .filter(DeferredAftermathPrepared.class::isInstance).map(DeferredAftermathPrepared.class::cast).findFirst().orElseThrow().aftermath();
        assertEquals("cause:development-settlement-assault-epoch-4-attacker-bioform-west-19", aftermath.causeId().value());
        assertTrue(state.deferredAftermath().entries().isEmpty(), "the fixture retains only the ordinary due action, never an injected aftermath");
    }
    @Test void coldBomberDueActionCommitsItsSemanticLossWithTheExactCause() {
        var configuration = FrontierV3FixtureCatalog.coldBomberAftermathConfiguration(new WorldId("frontier:cold-bomber-cause"), 41L);
        var engine = (io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalStateAccess<FrontierWorldState, FrontierWorldProjection>)
                FrontierEngines.createCanonicalStateAccess(configuration);
        long due = configuration.initialSchedules().getFirst().dueAt().ticks();
        var result = engine.advanceTo(new SimInstant(due), new WorkBudget(64, 512));
        assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("active"));
        FrontierWorldState committed = engine.canonicalState().state();
        DeferredAftermath aftermath = committed.deferredAftermath().entries().values().stream().findFirst().orElseThrow();
        assertEquals(PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS, committed.physicalDeltas().get(aftermath.cells().getFirst().position()).kind());
        assertEquals(aftermath.causeId().value(), committed.physicalDeltas().get(aftermath.cells().getFirst().position()).cause());
    }
    @Test void obstructionLivenessProfileRetainsASeparateDueResidentNeedWithoutAnInjectedOutcome() {
        var configuration = FrontierV3FixtureCatalog.productionObstructionLivenessConfiguration(
                new WorldId("frontier:obstruction-liveness-profile"), 41L);
        FrontierWorldState state = configuration.initialState(); SubjectId resident = new SubjectId("resident:2-1");
        assertEquals(ResidentNutritionStatus.NOURISHED, state.humanPopulation().nutrition(resident).status());
        assertTrue(configuration.initialSchedules().stream().anyMatch(action -> action.subject().equals(resident)
                && action.kind().equals("frontier.resident.need.review") && action.dueAt().ticks() == 1_000L));
        assertTrue(state.humanPopulation().provisions().isEmpty());
    }
    @Test void obstructionLivenessNeedAdvancesThroughTheOrdinaryScheduledConsumer() {
        var configuration = FrontierV3FixtureCatalog.productionObstructionLivenessConfiguration(
                new WorldId("frontier:obstruction-liveness-consumer"), 41L);
        var engine = (io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalStateAccess<FrontierWorldState, FrontierWorldProjection>)
                FrontierEngines.createCanonicalStateAccess(configuration);
        engine.advanceTo(new SimInstant(1_000L), new WorkBudget(64, 512));
        var result = engine.advanceTo(new SimInstant(1_001L), new WorkBudget(64, 512));
        assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("active"));
        ResidentNutrition need = engine.canonicalState().state().humanPopulation().nutrition(new SubjectId("resident:2-1"));
        assertEquals(ResidentNutritionStatus.HUNGRY, need.status(),
                "revision=" + engine.canonicalState().revision().value() + " schedules=" + engine.checkpoint().schedules());
        assertEquals(configuration.initialState().bootstrap().ruleset().residentLife().eatBelowUnits() - 1, need.satietyUnits());
    }

    @Test
    void harvestFixtureRetainsTheOrdinaryFirstCropBoundaryWithoutInjectingHotWork() {
        var configuration = FrontierV3FixtureCatalog.resourceSiteHarvestConfiguration(new WorldId("frontier:harvest-first-crop-fixture"), 41L);
        FrontierWorldState state = configuration.initialState();
        ResourceSiteHarvestJob job = (ResourceSiteHarvestJob) state.resourceSites().site(new SubjectId("site:1-wheat-field"))
                .harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        assertTrue(ResourceSiteHarvestGoal.actorAtWorkCell(state, job) && job.progress().completedCropSlots() == 0 && !job.progress().hasPendingCrop(),
                "the fixture must stop at the actual farmer's first retained crop station, not pre-consume a field cell");
        assertEquals(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus.PREPARED,
                state.physicalIntents().get(job.intentId()).status());
        assertTrue(state.sceneLeases().isEmpty(), "natural demand must still be the only source of a HOT harvest lease");
        assertEquals(1L, configuration.initialSchedules().stream().filter(action -> action.subject().equals(job.siteId())
                && action.kind().equals(ResourceSiteHarvestProcess.COLD_PROGRESS_KIND)).count());
    }

    @Test
    void fullPressureFixtureRetainsDisjointColdAssaultAndHarvestOwnersWithoutBodies() {
        FrontierWorldState state = FrontierV3FixtureCatalog.multiFrontPressureConfiguration(
                new WorldId("frontier:multi-front-pressure"), 41L).initialState();
        assertEquals(12, state.bootstrap().settlements().size());
        assertEquals(2, state.bootstrap().hive().seedNests().size());
        assertEquals(1, state.coldSettlementAssaultSceneCandidates().size());
        ResourceSiteHarvestJob harvest = (ResourceSiteHarvestJob) state.resourceSites().site(new SubjectId("site:1-wheat-field"))
                .harvestJobs().values().stream().reduce(HarvestFixtureOwners::rejectMultiple).orElseThrow();
        assertTrue(ResourceSiteHarvestGoal.actorAtWorkCell(state, harvest) && state.sceneLeases().isEmpty() && state.ambientLeases().isEmpty(),
                "one natural visit must be the only physical admission for both retained fronts");
        assertTrue(state.strategicPlans().settlementAssaults().values().stream().allMatch(value -> value.status() == SettlementAssaultStatus.COLD_COMBAT));
    }

    @Test
    void everyDeclaredFixtureProfileHasExactlyOneLoadedProviderAndRequiredEvidenceContract() {
        List<FrontierV3FixtureCatalog.Profile> profiles = FrontierV3FixtureCatalog.profiles();
        assertTrue(!profiles.isEmpty(), "the catalog must declare at least one production fixture");
        assertEquals(profiles.size(), profiles.stream().map(FrontierV3FixtureCatalog.Profile::id).distinct().count());
        for (int index = 0; index < profiles.size(); index++) {
            FrontierV3FixtureCatalog.Profile profile = profiles.get(index);
            assertTrue(profile.provider().length() > 0);
            assertTrue(java.util.Set.of("production", "expedition-candidate").contains(profile.rulesetId()));
            assertTrue(profile.sourceProfile().length() > 0);
            assertTrue(profile.requiredAssertion().length() > 0);
            String world = "frontier:catalog-" + profile.id();
            var configuration = FrontierV3FixtureCatalog.configuration(profile.id(), new WorldId(world), 41L);
            assertEquals(world, configuration.worldId().value());
            assertEquals(profile.rulesetId().equals("production") ? FrontierRulesets.production()
                    : FrontierRulesets.installed("frontier-v3-expedition-candidate-r1"), configuration.initialState().bootstrap().ruleset(),
                    "a fixture must use its explicitly declared immutable ruleset");
        }
    }

    @Test
    void unknownAndDuplicateFixtureProfilesFailBeforeAScenarioCanStart() {
        assertThrows(IllegalArgumentException.class, () -> FrontierV3FixtureCatalog.profile("not-a-profile"));
        FrontierV3FixtureCatalog.Profile duplicate = new FrontierV3FixtureCatalog.Profile("duplicate", "world", "production", "normal-world", "unit",
                "terminal", FrontierWorldRuntimeDefinition::configuration);
        assertThrows(IllegalArgumentException.class, () -> FrontierV3FixtureCatalog.catalog(duplicate, duplicate));
    }

    @Test
    void defenderEquipmentFixtureStartsBeforeAnyIssueOrPhysicalSurfaceExists() {
        var configuration = FrontierV3FixtureCatalog.defenderEquipmentConfiguration(new WorldId("frontier:defender-equipment-fixture"), 41L);
        FrontierWorldState state = configuration.initialState();
        assertEquals(ContainerSurfaceStatus.UNMATERIALIZED, state.inventory().surfaces().get(new SubjectId("container:1-depot")).status());
        ExactItemStack sword = state.inventory().items().get(new SubjectId("item:development-defender-sword"));
        assertEquals("minecraft:iron_sword", sword.itemKind());
        assertTrue(state.physicalIntents().isEmpty(), "the fixture may declare canonical preconditions but may not pre-issue the hand-off");
        assertTrue(configuration.initialSchedules().stream().anyMatch(action -> action.kind().equals("frontier.population.defender_equipment.review")));
    }

    @Test
    void medicalTreatmentFixtureStartsBeforeItsDepotAndCareOperationExist() {
        var configuration = FrontierV3FixtureCatalog.medicalTreatmentConfiguration(new WorldId("frontier:medical-treatment-fixture"), 41L);
        FrontierWorldState state = configuration.initialState();
        SubjectId depot = new SubjectId("container:1-depot");
        ExactItemStack remedy = state.inventory().items().get(new SubjectId("item:development-medical-remedy"));
        assertEquals(ContainerSurfaceStatus.UNMATERIALIZED, state.inventory().surfaces().get(depot).status());
        assertEquals("minecraft:honey_bottle", remedy.itemKind());
        assertTrue(state.humanPopulation().medicalOperations().isEmpty(), "the fixture must not fabricate an active treatment before its actual depot exists");
        assertTrue(configuration.initialSchedules().stream().anyMatch(action -> action.kind().equals("frontier.objective.review") && action.dueAt().ticks() == 1_000L));
    }

    @Test
    void serviceDecontaminationFixtureStartsBeforeItsDepotLeaseAndEffectExist() {
        var configuration = FrontierV3FixtureCatalog.serviceDecontaminationConfiguration(new WorldId("frontier:service-decontamination-fixture"), 41L);
        FrontierWorldState state = configuration.initialState(); SubjectId depot = new SubjectId("container:9-depot");
        ExactItemStack reagent = state.inventory().items().get(new SubjectId("item:development-service-decontamination-reagent"));

        assertEquals(ContainerSurfaceStatus.UNMATERIALIZED, state.inventory().surfaces().get(depot).status());
        assertEquals("minecraft:glowstone_dust", reagent.itemKind());
        assertTrue(state.serviceWorks().isEmpty() && state.sceneLeases().isEmpty() && state.physicalIntents().isEmpty(),
                "the fixture may retain an ordinary strategic precondition, never a hidden worker, effect or receipt");
        assertEquals(1, state.strategicPlans().tasks().size());
        assertTrue(configuration.initialSchedules().stream().anyMatch(action -> action.kind().equals("frontier.decontamination.scan") && action.dueAt().ticks() == 1_000L));
    }

    @Test
    void serviceDecontaminationAdmissionRemainsCanonicalWhenItsOrdinaryDepotIsActive() {
        WorldId world = new WorldId("frontier:service-decontamination-admission");
        var base = FrontierV3FixtureCatalog.serviceDecontaminationConfiguration(world, 41L);
        SubjectId depot = new SubjectId("container:9-depot");
        FrontierWorldState materialized = base.initialState().withInventory(base.initialState().inventory()
                .withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE));
        var configuration = new FrontierEngineConfiguration<>(base.worldId(), materialized, base.initialInstant(), base.commandPlanner(),
                base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(), base.initialSchedules(),
                base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        var engine = (io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalStateAccess<FrontierWorldState, FrontierWorldProjection>) FrontierEngines.createCanonicalStateAccess(configuration);

        var result = engine.advanceTo(new SimInstant(1_000L), new WorkBudget(64, 256));

        assertEquals(EngineStatus.Kind.ACTIVE, result.status().kind(), result.status().failureDetail().orElse("service admission quarantined"));
        FrontierWorldState admitted = engine.canonicalState().state();
        assertEquals(1, admitted.serviceWorks().size());
        assertEquals(2, admitted.physicalIntents().size());
        var inputIssue = admitted.physicalIntents().values().stream()
                .filter(intent -> intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.SETTLEMENT_SERVICE_INPUT_ISSUE)
                .findFirst().orElseThrow();
        assertEquals(SettlementServiceInputIssueStateSupport.ExecutionEligibility.DEFERRED,
                SettlementServiceInputIssueStateSupport.executionEligibility(admitted, inputIssue),
                "the exact reagent remains reserved while its medic traverses to the source station; it is not a conflict");
        var endpoint = admitted.physicalIntents().values().stream()
                .filter(intent -> intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.DECONTAMINATION)
                .findFirst().orElseThrow();
        assertEquals(SettlementServiceDecontaminationStateSupport.ExecutionEligibility.DEFERRED,
                SettlementServiceDecontaminationStateSupport.executionEligibility(admitted, endpoint),
                "the endpoint is durably reserved but must wait for the same medic's retained work progress");
    }

    @Test
    void admittedServiceWorkCorridorsNeverPlaceTheMedicBodyInsideCompiledGrayboxGeometry() {
        WorldId world = new WorldId("frontier:service-decontamination-geometry");
        var base = FrontierV3FixtureCatalog.serviceDecontaminationConfiguration(world, 41L);
        SubjectId depot = new SubjectId("container:9-depot");
        FrontierWorldState materialized = base.initialState().withInventory(base.initialState().inventory()
                .withSurfaceStatus(depot, ContainerSurfaceStatus.PREPARED).withSurfaceStatus(depot, ContainerSurfaceStatus.ACTIVE));
        var configuration = new FrontierEngineConfiguration<>(base.worldId(), materialized, base.initialInstant(), base.commandPlanner(),
                base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(), base.initialSchedules(),
                base.transactionCommitter(), base.stateValidator(), base.executionMetrics());
        var engine = (io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalStateAccess<FrontierWorldState, FrontierWorldProjection>) FrontierEngines.createCanonicalStateAccess(configuration);

        assertEquals(EngineStatus.Kind.ACTIVE, engine.advanceTo(new SimInstant(1_000L), new WorkBudget(64, 256)).status().kind());
        SettlementServiceWork work = engine.canonicalState().state().serviceWorks().values().iterator().next();
        var geometry = FrontierGrayboxPlan.compile(engine.canonicalState().state()).cells();

        java.util.stream.Stream.concat(work.inputTraversal().linearCorridorSurfaces().stream(), work.workTraversal().linearCorridorSurfaces().stream())
                .forEach(surface -> {
                    assertTrue(!geometry.containsKey(surface.support().offset(0, 1, 0)),
                            () -> "service corridor body feet collide with planned geometry at " + surface);
                    assertTrue(!geometry.containsKey(surface.support().offset(0, 2, 0)),
                            () -> "service corridor body head collides with planned geometry at " + surface);
                });
    }

    @Test
    void steppedRouteFixtureCompilesOneBoundedTwoBlockRiseAndItsProviderOwnedFootings() {
        FrontierWorldState state = FrontierV3FixtureCatalog.steppedRouteConfiguration(new WorldId("frontier:stepped-route-fixture"), 41L).initialState();
        SubjectId settlement = state.bootstrap().settlements().getFirst().id();
        TraversalTopology topology = state.routeTopology().settlementTraversalTopology(state.bootstrap(), settlement);

        assertEquals(4L, topology.edges().stream().filter(edge -> edge.grade() == 1).count());
        assertEquals(1, topology.edges().stream().mapToInt(TraversalTopology.Edge::grade).max().orElseThrow());
        assertTrue(state.physicalDeltas().isEmpty(), "the fixture declares geometry but cannot pre-place a Minecraft support or surface");
        assertTrue(!FrontierRouteNetwork.foundationCells(state.bootstrap(), state.routeTopology()).isEmpty(),
                "the immutable provider compiles the real ramp footing rather than requiring pilot scaffolding");
        assertTrue(FrontierGrayboxPlan.compile(state).cells().values().stream().anyMatch(cell -> cell.semanticPart() == GrayboxSemanticPart.ROUTE_FOUNDATION));
    }

    @Test
    void hiveMobilizationFixtureDeclaresTheExactTaskGroupWithoutPreopeningACocoon() {
        FrontierWorldState state = FrontierV3FixtureCatalog.hiveMobilizationConfiguration(
                new WorldId("frontier:hive-mobilization-fixture"), 41L).initialState();
        HiveMobilization mobilization = state.hiveColony().mobilizations().values().stream().findFirst().orElseThrow();
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)),
                "the exact waking group must survive the checkpoint boundary that precedes physical release");
        assertEquals(HiveMobilizationStatus.WAKING, mobilization.status());
        assertEquals(List.of(new SubjectId("bioform:east-11"), new SubjectId("bioform:east-14"), new SubjectId("bioform:east-2"),
                new SubjectId("bioform:east-23")), mobilization.memberIds());
        assertEquals(new SubjectId("bioform:east-23"), mobilization.overseerId());
        assertTrue(mobilization.memberIds().stream().allMatch(id ->
                state.hiveColony().bioformLifecycles().get(id).phase() == BioformLifecyclePhase.WAKING));
        assertTrue(mobilization.memberIds().stream().allMatch(id ->
                !HivePhysiologySupport.permitsAmbientLease(state, id)),
                "fixture task selection is not permission to create a body before a real cocoon release");
        SubjectId first = mobilization.memberIds().getFirst();
        SubjectId hibernaculum = state.hiveColony().bioformLifecycles().get(first).homeSlot().orElseThrow().hibernaculumId();
        assertEquals(new HiveCocoonSlot(new SubjectId("organ:east-hibernaculum-1"), 3),
                state.hiveColony().bioformLifecycles().get(first).homeSlot().orElseThrow());
        FrontierObjectBoard board = FrontierReadabilityPlan.compile(state).boards().get(hibernaculum);
        assertEquals(FrontierObjectBoard.Tone.WARNING, board.tone());
        assertTrue(board.text().endsWith("WAKE SEQUENCE · 0/4"), "the physical tray must explain its own waking state without a HUD");
    }
    @Test
    void hiveReturnFixtureRetainsTheSameSurvivingExpeditionBeforeAnyPhysicalVisit() {
        FrontierWorldState state = FrontierV3FixtureCatalog.hiveReturnConfiguration(
                new WorldId("frontier:hive-return-fixture"), 41L).initialState();
        HiveMobilization returnParent = state.hiveColony().mobilizations().values().stream().findFirst().orElseThrow();
        assertEquals(HiveMobilizationStatus.RETURNING, returnParent.status());
        assertEquals(returnParent.memberIds(), returnParent.returnAssembly().orElseThrow().members().keySet().stream().sorted().toList());
        assertTrue(returnParent.memberIds().stream().allMatch(member -> state.hiveColony().bioformLifecycles().get(member).phase()
                == BioformLifecyclePhase.RETURNING));
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }

    @Test
    void hiveAssemblyRoutesRetainTwoCellClearanceAgainstEveryUnreleasedHiveCell() {
        FrontierWorldState state = FrontierV3FixtureCatalog.hiveMobilizationConfiguration(
                new WorldId("frontier:hive-mobilization-cocoon-clearance"), 41L).initialState();
        HiveMobilization initial = state.hiveColony().mobilizations().values().stream().findFirst().orElseThrow();
        for (SubjectId member : initial.memberIds()) {
            HiveMobilization current = state.hiveColony().mobilizations().get(initial.id());
            state = HiveMobilizationProcess.reduceReleaseStarted(state, initial.hiveId(), new HiveMobilizationReleaseStarted(current.id()));
            state = HiveMobilizationProcess.reduceCocoonReleased(state, initial.hiveId(), new HiveMobilizationCocoonReleased(current.id(), member, HiveAssemblyExecutionAuthority.admission(state, current.id(), member)));
        }
        FrontierWorldState assembledState = state;
        HiveTaskAssembly assembly = assembledState.hiveColony().mobilizations().get(initial.id()).assembly().orElseThrow();
        java.util.Set<BlockPosition> occupiedHiveCells = new java.util.LinkedHashSet<>(FrontierGrayboxPlan.intactOrganOccupancy(
                assembledState.bootstrap().hive().organs()));
        assembledState.bootstrap().hive().organs().forEach(organ -> occupiedHiveCells.addAll(
                HiveOrganSupportPlan.foundationCells(assembledState.bootstrap().terrain(), organ)));
        assembledState.hiveColony().bioformLifecycles().entrySet().stream()
                .filter(entry -> entry.getValue().phase().occupiesCocoon()).map(entry -> {
                    HiveCocoonSlot slot = entry.getValue().homeSlot().orElseThrow();
                    HiveOrgan tray = assembledState.bootstrap().hive().organs().stream().filter(organ -> organ.id().equals(slot.hibernaculumId())).findFirst().orElseThrow();
                    return HiveCocoonPlan.cocoonCell(tray, slot);
                }).forEach(occupiedHiveCells::add);
        java.util.Set<SurfaceAnchor> bodyColumnsBlockedByHive = occupiedHiveCells.stream()
                .flatMap(cell -> java.util.stream.Stream.of(new SurfaceAnchor(cell.offset(0, -1, 0)), new SurfaceAnchor(cell.offset(0, -2, 0))))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertTrue(assembly.members().values().stream().flatMap(member -> member.corridor().stream())
                        .noneMatch(bodyColumnsBlockedByHive::contains),
                "a retained HOT/COLD bioform corridor must preserve both body cells above every materialized hive cell, including hiveroot");
    }

    @Test
    void defenderEquipmentReturnFixtureStartsWithOneResolvedExactActorHeldSwordAndNoPhysicalShortcut() {
        var configuration = FrontierV3FixtureCatalog.defenderEquipmentReturnConfiguration(new WorldId("frontier:defender-equipment-return-fixture"), 41L);
        FrontierWorldState state = configuration.initialState();
        ExactItemStack sword = state.inventory().items().get(new SubjectId("item:development-defender-return-sword"));

        assertEquals("minecraft:iron_sword", sword.itemKind());
        assertTrue(sword.custody() instanceof InventoryCustody.Actor);
        assertEquals(SettlementAssaultStatus.RESOLVED, state.strategicPlans().settlementAssaults().values().iterator().next().status());
        assertTrue(state.physicalIntents().isEmpty(), "the fixture may declare canonical custody but may not pre-return the physical stack");
        assertTrue(configuration.initialSchedules().stream().anyMatch(action -> action.kind().equals("frontier.population.defender_equipment_return.review")));
    }

    @Test
    void engineeringEquipmentFixtureRetainsOnlyCanonicalCrewAndDepotToolsBeforeAVisit() {
        var configuration = FrontierV3FixtureCatalog.engineeringEquipmentConfiguration(new WorldId("frontier:engineering-equipment-fixture"), 41L);
        FrontierWorldState state = configuration.initialState();
        SubjectId projectId = new SubjectId("construction:route-reroute-settlement-1--366-64--304");
        RouteConstruction project = state.routeConstructions().get(projectId);
        ExactItemStack tool = state.inventory().items().get(new SubjectId("item:bootstrap-1-engineering-tool-1"));
        assertTrue(project != null && project.team().isPresent());
        assertEquals(ContainerSurfaceStatus.UNMATERIALIZED, state.inventory().surfaces().get(new SubjectId("container:1-depot")).status());
        assertEquals(new InventoryCustody.ContainerSlot(new SubjectId("container:1-depot"), 20), tool.custody());
        assertTrue(state.physicalIntents().isEmpty(), "the fixture must not pre-issue or materialize an engineering tool");
        assertTrue(configuration.initialSchedules().stream().anyMatch(action -> action.kind().equals("frontier.route_construction.scan")));
    }

    @Test
    void routeMaintenanceFairnessFixtureRetainsOneColdSourceAndOneIndependentLoadedWorksite() {
        FrontierWorldState state = FrontierV3FixtureCatalog.routeMaintenanceColdSourceFairnessConfiguration(
                new WorldId("frontier:route-maintenance-fairness-fixture"), 41L).initialState();
        RouteMaintenance cold = state.routeMaintenances().get(new SubjectId("maintenance:route--380-64--304"));
        RouteMaintenance loaded = state.routeMaintenances().get(new SubjectId("maintenance:route--140-64--304"));

        assertTrue(cold != null && cold.cargoId().isEmpty());
        assertTrue(loaded != null && loaded.cargoId().isPresent() && loaded.assembly().orElseThrow().complete());
        assertTrue(state.physicalIntents().values().stream().anyMatch(intent -> intent.causeSubjectId().equals(cold.id())
                && intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_MAINTENANCE_MATERIAL_LOADING));
        assertTrue(state.physicalIntents().values().stream().noneMatch(intent -> intent.kind() == io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind.ROUTE_MAINTENANCE
                        && intent.roles().require(io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentSubjectRole.ROUTE_MAINTENANCE).equals(loaded.id())),
                "the fixture may retain COLD-ready cargo and crew, but a repair intent belongs exclusively to the later HOT worksite");
        assertEquals(ContainerSurfaceStatus.ACTIVE, state.inventory().surfaces().get(FrontierRouteNetwork.MAINTENANCE_CONTAINER).status());
    }

    @Test
    void engineeringFixtureCompilesOneDeterministicallyCompletableExactCrewApproach() {
        FrontierWorldState state = FrontierV3FixtureCatalog.engineeringEquipmentConfiguration(
                new WorldId("frontier:engineering-joint-approach-fixture"), 41L).initialState();
        SubjectId projectId = new SubjectId("construction:route-reroute-settlement-1--366-64--304");
        EngineeringWorkAssembly assembly = EngineeringWorksite.compile(state, state.routeConstructions().get(projectId));
        int boundedMoves = assembly.members().values().stream().mapToInt(member -> member.corridor().size() - 1).sum();
        for (int move = 0; move < boundedMoves; move++) {
            int moveIndex = move;
            SubjectId advancing = assembly.nextSafeAdvance().orElseThrow(
                    () -> new AssertionError("compiled engineering crew must not deadlock at move " + moveIndex));
            assembly = assembly.advance(advancing);
            assertEquals(assembly.members().size(), assembly.positions().values().stream().distinct().count(),
                    "one COLD move may not overlap exact people");
        }
        assertTrue(assembly.complete(), "the retained COLD schedule must reach every distinct work-site slot");
    }

    @Test
    void engineeringWorksiteLeaseClosesThroughItsProjectOwnerRatherThanALogisticsBinding() {
        WorldId world = new WorldId("frontier:engineering-worksite-release");
        var engine = FrontierEngines.create(FrontierV3FixtureCatalog.engineeringWorksiteConfiguration(world, 41L));
        FrontierWorldState initial = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        EngineeringWorkSceneCandidate candidate = FrontierEngineeringWorkSceneSupport.candidates(initial).stream().findFirst().orElseThrow();
        assertTrue(FrontierSceneAdmission.available(initial, candidate.memberPositions().keySet()),
                "the COLD-ready crew is free for its own scene hand-off");
        assertTrue(candidate.memberPositions().keySet().stream().allMatch(actor -> FrontierSceneAdmission.reserved(initial, actor)),
                "the next engineering worksite owns its COLD-ready crew before ambient demand can reclaim it");
        SceneLeaseId leaseId = new SceneLeaseId("lease:engineering-worksite-release");
        SceneLease lease = SceneLease.forCause(leaseId, world, new EngineeringWorkSceneCause(candidate.projectId(), candidate.workCellIndex()),
                candidate.workCell(), engine.checkpoint().instant(), engine.checkpoint().revision().value(), SceneLeaseStatus.PREPARED,
                candidate.memberPositions().keySet().stream().sorted().map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(world, leaseId, actor))).toList(), java.util.Set.of(), Optional.empty());
        RouteConstruction project = initial.routeConstructions().get(candidate.projectId());
        var workIntent = io.farfrontier.palemirror.frontier.v3.process.RouteConstructionProcess.workIntent(project,
                project.cargoId().orElseThrow(), project.cargoId().map(initial.inventory().cargo()::get).orElseThrow().itemIds().getFirst());
        assertTrue(!FrontierEngineeringWorkSceneSupport.permitsCurrentWorkIntent(initial, workIntent),
                "a retained construction item may not execute before its exact worksite is HOT");
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class,
                submit(engine, world, "engineering-worksite-prepare", new EngineeringWorkSceneLeasePrepared(lease)));
        FrontierWorldState prepared = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertTrue(!FrontierEngineeringWorkSceneSupport.permitsCurrentWorkIntent(prepared, workIntent),
                "PREPARED is not physical-work authority");
        for (SubjectId actor : candidate.memberPositions().keySet()) ModeledActorBodyFacts.present(engine, actor);
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class,
                submit(engine, world, "engineering-worksite-hot", new SceneLeaseTransition(leaseId, SceneLeaseStatus.HOT)));
        FrontierWorldState hot = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertTrue(FrontierEngineeringWorkSceneSupport.permitsCurrentWorkIntent(hot, workIntent),
                "the exact HOT lease and independently present crew authorize current work");
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class,
                submit(engine, world, "engineering-worksite-restart", new SceneLeaseTransition(leaseId, SceneLeaseStatus.UNKNOWN_AFTER_RESTART)));
        FrontierWorldState recovering = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertTrue(!FrontierEngineeringWorkSceneSupport.permitsCurrentWorkIntent(recovering, workIntent),
                "a retained unstarted work intent must wait for the exact scene recovery after restart");
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class,
                submit(engine, world, "engineering-worksite-reclaimed", new SceneLeaseTransition(leaseId, SceneLeaseStatus.HOT)));
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class,
                submit(engine, world, "engineering-worksite-draining", new SceneLeaseTransition(leaseId, SceneLeaseStatus.DRAINING)));
        List<SceneMemberPosition> captured = lease.members().stream().map(member -> new SceneMemberPosition(member.actorId(),
                BodyPosition.above(new SurfaceAnchor(candidate.memberPositions().get(member.actorId()))), FixedScalar.whole(20))).toList();
        assertInstanceOf(io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted.class,
                submit(engine, world, "engineering-worksite-release", new SceneLeaseReleased(leaseId, captured)));
        FrontierWorldState closed = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(SceneLeaseStatus.CLOSED, closed.sceneLeases().get(leaseId).status());
        assertEquals(initial.routeConstructions().get(candidate.projectId()).settlementId(),
                FrontierSceneOwnerSupport.owner(closed, closed.sceneLeases().get(leaseId)));
    }

    private static io.farfrontier.palemirror.frontier.v3.api.CommandResult submit(FrontierEngine<FrontierWorldProjection> engine, WorldId world,
                                                                                    String command, io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        var checkpoint = engine.checkpoint();
        var commandId = new io.farfrontier.palemirror.frontier.v3.api.CommandId("command:" + command);
        return engine.submit(new io.farfrontier.palemirror.frontier.v3.api.FrontierCommand(1, commandId, world,
                checkpoint.revision(), checkpoint.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR,
                io.farfrontier.palemirror.frontier.v3.api.CauseChain.root(commandId), payload));
    }
}
