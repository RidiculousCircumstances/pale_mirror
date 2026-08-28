package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalPostcondition;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierWorldStateTest {
    private static final FixedRatio HALF = new FixedRatio(new FixedScalar(500_000L));

    @Test
    void initialStateOwnsEveryExactActorAndFunctionalStructure() {
        FrontierWorldState state = initial();

        assertEquals(state.bootstrap().residentCount() + state.bootstrap().bioformCount(), state.actorLocations().size());
        assertEquals(12 * StructureKind.values().length, state.structureConditions().size());
        assertEquals(15, state.inventory().containers().size());
        assertEquals(FrontierRouteNetwork.OWNER, state.inventory().containers().get(FrontierRouteNetwork.MAINTENANCE_CONTAINER).ownerId());
        assertEquals(2, state.inventory().items().size());
        assertEquals(state.inventory().containers().keySet(), state.inventory().surfaces().keySet());
        assertTrue(state.productionJobs().isEmpty());
        assertTrue(state.operations().isEmpty());
        assertTrue(state.physicalIntents().isEmpty());
        assertTrue(state.structureConditions().values().stream().allMatch(condition -> condition == StructureCondition.INTACT));
        assertEquals(2, state.infection().size());
    }

    @Test
    void stateTransitionsRemainBoundedAndSparse() {
        FrontierWorldState state = initial();
        SubjectId resident = new SubjectId("resident:1-1");
        SubjectId structure = new SubjectId("structure:1-hall");
        InfectionCell cell = InfectionCell.at(new BlockPosition(-1, 64, -1));

        FrontierWorldState changed = state.withActorLocation(resident, new BlockPosition(-10, 64, -10))
                .withStructureCondition(structure, StructureCondition.DAMAGED)
                .withInfection(cell, HALF);
        assertEquals(new BlockPosition(-10, 64, -10), changed.actorLocations().get(resident).position());
        assertEquals(StructureCondition.DAMAGED, changed.structureConditions().get(structure));
        assertEquals(HALF, changed.infection().get(cell));
        assertEquals(new InfectionCell(-1, -1), cell);
        assertEquals(2, changed.withInfection(cell, new FixedRatio(FixedScalar.ZERO)).infection().size());
        assertThrows(IllegalArgumentException.class, () -> state.withActorLocation(resident, new BlockPosition(512, 64, 0)));
        assertThrows(IllegalArgumentException.class, () -> state.withStructureCondition(new SubjectId("structure:missing"), StructureCondition.DESTROYED));
    }

    @Test
    void codecRoundTripsCanonicalMutableStateAndRejectsInvalidState() {
        FrontierWorldState baseline = initial();
        SubjectId container = new SubjectId("container:1-depot");
        SubjectId item = new SubjectId("item:codec");
        ExactInventory inventory = new ExactInventory(baseline.inventory().containers(), Map.of(item,
                new ExactItemStack(item, "minecraft:iron_ingot", 64, new InventoryCustody.ContainerSlot(container, 0))), Map.of(), Map.of(), Map.of(), Map.of(), baseline.inventory().surfaces());
        inventory = inventory.recordConflict(new InventoryConflict(new SubjectId("conflict:codec-item"), item, container, 0, InventoryConflictKind.MISSING));
        ProductionJob activeJob = new ProductionJob(new SubjectId("job:production-1-1"), new SubjectId("settlement:1"),
                new SubjectId("structure:1-workshop"), new SubjectId("resident:1-3"), new SubjectId("item:bootstrap-1-wheat"),
                new SubjectId("item:production-1-1-bread"), "minecraft:bread", 64);
        FrontierWorldState source = baseline.withInventory(inventory).withActorLocation(new SubjectId("bioform:west-0"), new BlockPosition(-400, 64, 400))
                .withStructureCondition(new SubjectId("structure:2-depot"), StructureCondition.DESTROYED)
                .withInfection(new InfectionCell(-100, 100), HALF).withProductionJob(activeJob);
        FrontierWorldStateCodec codec = new FrontierWorldStateCodec();
        byte[] encoded = codec.encode(source);
        assertEquals(source, codec.decode(encoded));
        assertEquals(1, codec.decode(encoded).inventory().conflicts().size());
        encoded[4] = 17;
        assertThrows(IllegalArgumentException.class, () -> codec.decode(encoded));

        Map<SubjectId, ActorLocation> missingActor = new LinkedHashMap<>(source.actorLocations());
        missingActor.remove(new SubjectId("resident:1-1"));
        assertThrows(IllegalArgumentException.class, () -> new FrontierWorldState(source.bootstrap(), missingActor,
                source.structureConditions(), source.infection(), source.inventory(), source.productionJobs(), source.contracts(), source.operations(), source.physicalIntents(),
                source.physicalObservations(), source.sceneLeases(), source.hiveColony(), source.structureDamage(), source.physicalDeltas(), source.ambientLeases(), source.routeTopology()));
    }

    @Test
    void physicalDeltasRetainExactKnownLossesAndUnknownScarsWithoutTemplateRepair() {
        FrontierWorldState baseline = initial();
        HiveOrgan organ = baseline.bootstrap().hive().organs().getFirst();
        GrayboxCell cell = FrontierGrayboxPlan.compile(baseline).cells().values().stream()
                .filter(value -> value.ownerId().equals(organ.id())).findFirst().orElseThrow();
        PhysicalDelta known = new PhysicalDelta(cell.position(), PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                java.util.Optional.of(organ.id()), java.util.Optional.of(cell.semanticPart()), "player:test");
        PhysicalDelta scar = new PhysicalDelta(new BlockPosition(1, 64, 1), PhysicalDeltaKind.UNKNOWN_SCAR,
                java.util.Optional.empty(), java.util.Optional.empty(), "explosion:test");

        FrontierWorldState changed = baseline.recordPhysicalDelta(known).recordPhysicalDelta(scar);
        assertEquals(2, changed.physicalDeltas().size());
        assertTrue(!FrontierGrayboxPlan.compile(changed).cells().containsKey(cell.position()));
        assertTrue(changed.isHiveOrganOperational(organ.id()));
        assertTrue(new PhysicalDeltaObserved(known).requiresDurableBeforeEffect());
        assertEquals(changed, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(changed)));
        assertThrows(IllegalArgumentException.class, () -> changed.recordPhysicalDelta(known));
        assertThrows(IllegalArgumentException.class, () -> baseline.recordPhysicalDelta(new PhysicalDelta(new BlockPosition(1, 64, 1),
                PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS, java.util.Optional.of(organ.id()), java.util.Optional.of(GrayboxSemanticPart.WALL), "bad")));
    }

    @Test
    void infectionOverlayPlanKeepsOneBoundedReadableMarkerPerSparseSourceCell() {
        FrontierWorldState baseline = initial();
        InfectionCell trace = new InfectionCell(-100, 100);
        InfectionCell bloom = new InfectionCell(100, 100);
        FrontierWorldState state = baseline.withInfection(trace, new FixedRatio(new FixedScalar(249_999L)))
                .withInfection(bloom, new FixedRatio(new FixedScalar(500_000L)));

        FrontierInfectionOverlayPlan plan = FrontierInfectionOverlayPlan.compile(state);
        InfectionOverlayCell traceMarker = plan.cells().get(trace);
        InfectionOverlayCell bloomMarker = plan.cells().get(bloom);
        assertEquals(state.infection().size(), plan.cells().size());
        assertEquals(-399, traceMarker.x());
        assertEquals(401, traceMarker.z());
        assertEquals(InfectionOverlayStage.TRACE, traceMarker.stage());
        assertEquals(InfectionOverlayStage.BLOOM, bloomMarker.stage());
        assertThrows(IllegalArgumentException.class, () -> new InfectionOverlayCell(trace, traceMarker.x() + 1, traceMarker.z(), traceMarker.stage()));
    }

    @Test
    void physicalIntentCannotReferenceAForeignCauseSubject() {
        FrontierWorldState state = initial();
        PhysicalIntent intent = new PhysicalIntent(new PhysicalIntentId("intent:cargo-handoff-foreign"), PhysicalIntentKind.CARGO_HANDOFF,
                PhysicalIntentStatus.PREPARED, new SubjectId("operation:foreign"), List.of(new SubjectId("resident:1-1")),
                new FixedPosition(FixedScalar.ZERO, FixedScalar.ZERO, FixedScalar.ZERO), 0, PhysicalPostcondition.CARGO_HANDOFF_OBSERVED);

        assertThrows(IllegalArgumentException.class, () -> new FrontierWorldState(state.bootstrap(), state.actorLocations(), state.structureConditions(),
                state.infection(), state.inventory(), state.productionJobs(), state.contracts(), state.operations(), Map.of(intent.id(), intent), state.physicalObservations(),
                state.sceneLeases(), state.hiveColony(), state.structureDamage(), state.physicalDeltas(), state.ambientLeases(), state.routeTopology()));
    }

    @Test
    void dynamicHiveColonyKeepsExactGrowthIdentitiesThroughLaterStateChangesAndRecovery() {
        FrontierWorldState baseline = initial();
        SubjectId hive = baseline.bootstrap().hive().id();
        SubjectId eastNest = new SubjectId("nest:seed-east");
        HiveOrgan organ = new HiveOrgan(new SubjectId("organ:east-grown-heart-1"), hive, eastNest, HiveOrganKind.HEART,
                new BlockPosition(420, 64, 432), java.util.Optional.empty());
        Bioform bioform = new Bioform(new SubjectId("bioform:east-grown-1"), hive, eastNest, BioformRole.GUARD, new BlockPosition(424, 64, 428));

        FrontierWorldState grown = baseline.addHiveOrgan(organ).spawnBioform(bioform)
                .withInfection(InfectionCell.at(new BlockPosition(420, 64, 428)), HALF);
        assertEquals(organ, grown.hiveColony().addedOrgans().get(organ.id()));
        assertEquals(bioform, grown.hiveColony().spawnedBioforms().get(bioform.id()));
        assertEquals(bioform.position(), grown.actorLocations().get(bioform.id()).position());
        assertEquals(grown, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(grown)));
        assertThrows(IllegalArgumentException.class, () -> grown.spawnBioform(bioform));
        assertThrows(IllegalArgumentException.class, () -> baseline.addHiveOrgan(new HiveOrgan(organ.id(), hive, eastNest, HiveOrganKind.HEART,
                new BlockPosition(520, 64, 432), java.util.Optional.empty())));
    }

    @Test
    void hiveGrowthConsumesOneExactStoreItemBeforeItPublishesItsNewIdentities() {
        FrontierWorldState baseline = initial(); SubjectId hive = baseline.bootstrap().hive().id(); SubjectId west = new SubjectId("nest:seed-west");
        HiveGrowthJob job = new HiveGrowthJob(new SubjectId("job:hive-growth-1"), hive, west, new SubjectId("item:bootstrap-hive-biomass"),
                new HiveOrgan(new SubjectId("organ:west-grown-heart-1"), hive, west, HiveOrganKind.HEART, new BlockPosition(-408, 64, 432), java.util.Optional.empty()),
                new Bioform(new SubjectId("bioform:west-grown-1"), hive, west, BioformRole.GUARD, new BlockPosition(-404, 64, 432)));
        FrontierWorldState active = baseline.startHiveGrowth(job);
        assertTrue(!active.inventory().items().containsKey(job.consumedItemId()));
        assertEquals(job, active.hiveColony().growthJobs().get(job.id()));
        assertEquals(active, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(active)));
        assertThrows(IllegalArgumentException.class, () -> baseline.startHiveGrowth(new HiveGrowthJob(new SubjectId("job:hive-growth-bad"), hive, west,
                new SubjectId("item:bootstrap-1-wheat"), job.organ(), job.bioform())));
        assertThrows(IllegalArgumentException.class, () -> HiveGrowthProcess.reduceBlocked(baseline, hive,
                new HiveGrowthBlocked(hive, west, new SubjectId("work:hive-growth-1"), HiveGrowthBlockReason.BIOMASS_UNAVAILABLE)));
        FrontierWorldState completed = active.completeHiveGrowth(job.id());
        assertTrue(completed.hiveColony().growthJobs().isEmpty());
        assertEquals(job.organ(), completed.hiveColony().addedOrgans().get(job.organ().id()));
        assertEquals(job.bioform(), completed.hiveColony().spawnedBioforms().get(job.bioform().id()));
        assertEquals(job.bioform().position(), completed.actorLocations().get(job.bioform().id()).position());
    }

    private static FrontierWorldState initial() {
        return FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:state"), 1234L));
    }
}
