package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.geometry.KnownBlockExclusions;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ExtractionTerrainFactsTest {
    @Test void cultivationReadsTheAuthoredNativeSurfaceRatherThanUniformGrass() {
        var bootstrap = bootstrap("frontier-v3-quarry-graybox-r21");
        var state = FrontierWorldState.initial(bootstrap);
        var knowledge = new DevelopmentGroundKnowledge(state);
        int y = bootstrap.ruleset().extraction().surfaceCut().orElseThrow().topY();
        var seen = new java.util.HashSet<String>();
        for (int x = 120; x < 130; x++) for (int z = 340; z < 350; z++) {
            var position = new BlockPosition(x, y, z);
            var expected = bootstrap.ruleset().fields().agriculture().orElseThrow().surfaceAt(x, z).orElseThrow();
            var actual = ExtractionTerrainFacts.at(bootstrap, position).orElseThrow();
            assertEquals(expected.kind(), actual.block().kind());
            assertEquals(expected.properties(), actual.block().properties());
            seen.add(actual.block().kind());
            var sample = knowledge.at(position);
            if (sample.isPresent()) {
                assertEquals(expected, sample.orElseThrow().block());
                assertThrows(IllegalArgumentException.class, () -> new FieldDevelopmentCell(new SurfaceAnchor(position),
                        sample.orElseThrow(), knowledge.at(position.offset(0, 1, 0)).orElseThrow()),
                        "rock or coarse dirt cannot become a farmland declaration without earthworks");
            }
        }
        assertEquals(Set.of("minecraft:stone", "minecraft:coarse_dirt"), seen);
        assertEquals("minecraft:grass_block", ExtractionTerrainFacts.at(bootstrap, new BlockPosition(150, y, 50))
                .orElseThrow().block().kind());
    }
    @Test void boundedGeologyFenceIsAllOrNothingAndRetainsEveryPositionThroughRecovery() throws Exception {
        var before = FrontierWorldState.initial(bootstrap("frontier-v3-quarry-graybox-r7"));
        var observations = new java.util.ArrayList<ExtractionGeologyInvalidated.Observation>();
        for (int x = 0; x < ExtractionGeologyInvalidated.MAX_OBSERVATIONS; x++)
            observations.add(new ExtractionGeologyInvalidated.Observation(new BlockPosition(x, 65, 0), 1));
        var fence = new ExtractionGeologyInvalidated(observations);
        var after = fence.apply(before, ExtractionGeologyInvalidated.OWNER);
        assertEquals(256, after.extractionSites().geologicalExclusions().positions().size());
        for (var observation : observations) {
            assertTrue(before.extractionSites().geologicalExclusions().positions().isEmpty());
            assertTrue(ExtractionGroundKnowledge.forState(after).at(observation.position()).isEmpty());
        }
        assertEquals(before.inventory(), after.inventory());
        assertEquals(before.extractionSites().deposits(), after.extractionSites().deposits());
        assertEquals(before.actorLocations(), after.actorLocations());
        assertEquals(before.replicaCustody(), after.replicaCustody(), "an intent is not a physical custody acknowledgement");
        assertTrue(fence.requiresDurableBeforeEffect());
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        assertEquals(after, codec.decode(codec.encode(after)));
        var payloads = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(fence, payloads.decode(fence.type(), payloads.encode(fence)));
        assertThrows(RuntimeException.class, () -> payloads.decode("frontier.extraction_geology_invalidated", payloads.encode(fence)));
        assertThrows(RuntimeException.class, () -> payloads.decode(fence.type(), java.util.Arrays.copyOf(payloads.encode(fence), 4)));
        var tooMany = new java.util.ArrayList<>(observations);
        tooMany.add(new ExtractionGeologyInvalidated.Observation(new BlockPosition(257, 65, 0), 1));
        assertThrows(IllegalArgumentException.class, () -> new ExtractionGeologyInvalidated(tooMany));
        assertThrows(IllegalArgumentException.class, () -> new ExtractionGeologyInvalidated(java.util.List.of(observations.getFirst(), observations.getFirst())));
        assertThrows(IllegalArgumentException.class, () -> fence.apply(before, new io.farfrontier.palemirror.frontier.v3.api.SubjectId("geometry:foreign")));
        var stale = new java.util.ArrayList<>(observations);
        stale.set(255, new ExtractionGeologyInvalidated.Observation(observations.get(255).position(), 2));
        assertThrows(IllegalArgumentException.class, () -> new ExtractionGeologyInvalidated(stale).apply(before, ExtractionGeologyInvalidated.OWNER));
        var owned = before.extractionSites().deposits().values().iterator().next().site().layout().cells().getFirst().source();
        assertThrows(IllegalArgumentException.class, () -> new ExtractionGeologyInvalidated(java.util.List.of(observations.getFirst(),
                new ExtractionGeologyInvalidated.Observation(owned, 1))).apply(before, ExtractionGeologyInvalidated.OWNER));
        assertTrue(before.extractionSites().geologicalExclusions().positions().isEmpty(), "a rejected batch cannot publish its valid prefix");
    }

    private static FrontierBootstrap bootstrap(String rules) {
        return FrontierBootstrapper.create(new WorldId("frontier:terrain-facts"), 20260918065L, FrontierRulesets.installed(rules));
    }

    @Test void coverageAndRetainedSamplesAgreeAcrossStratumCapAndUnknownWorldBounds() {
        var bootstrap = bootstrap("frontier-v3-quarry-graybox-r7");
        for (int y : new int[]{-64, -63, 62, 63, 64, 2047}) {
            var position = new BlockPosition(0, y, 0);
            assertEquals(ExtractionTerrainFacts.at(bootstrap, position).isPresent(), ExtractionTerrainFacts.contains(bootstrap, position));
        }
        var stone = new BlockPosition(0, 62, 0);
        assertSame(ExtractionTerrainFacts.at(bootstrap, stone).orElseThrow(),
                ExtractionTerrainFacts.at(bootstrap, new BlockPosition(1, 61, 1)).orElseThrow(),
                "unchanged terrain queries reuse the validated immutable sample, not per-voxel regex/block construction");
        var outside = new BlockPosition(10000, 62, 10000);
        assertFalse(ExtractionTerrainFacts.contains(bootstrap, outside));
        assertTrue(ExtractionTerrainFacts.at(bootstrap, outside).isEmpty());
    }

    @Test void immutableCoverageProofCannotAuthorizeChangedExclusionsOrDifferentBootstrap() {
        var known = bootstrap("frontier-v3-quarry-graybox-r7");
        var exclusions = new KnownBlockExclusions(Set.of(new BlockPosition(0, 62, 0)));
        assertDoesNotThrow(() -> ExtractionTerrainFacts.validateExclusions(known, exclusions));
        assertDoesNotThrow(() -> ExtractionTerrainFacts.validateExclusions(known, exclusions));
        var invalid = exclusions.invalidate(new BlockPosition(10000, 62, 10000));
        assertThrows(IllegalArgumentException.class, () -> ExtractionTerrainFacts.validateExclusions(known, invalid));
        var withoutGeology = bootstrap("frontier-v3-quarry-graybox-r1");
        assertThrows(IllegalArgumentException.class, () -> ExtractionTerrainFacts.validateExclusions(withoutGeology, exclusions));
        assertTrue(ExtractionTerrainFacts.at(withoutGeology, new BlockPosition(0, 62, 0)).isEmpty());
        assertEquals("minecraft:stone", ExtractionTerrainFacts.at(known, new BlockPosition(0, 62, 0)).orElseThrow().block().kind());
    }

    @Test void appendedInvalidationsRemainImmutableAndValidatedAcrossCompactionAndRecovery() {
        var known = bootstrap("frontier-v3-quarry-graybox-r7");
        var supplied = new java.util.HashSet<BlockPosition>();
        supplied.add(new BlockPosition(0, 62, 0));
        var initial = new KnownBlockExclusions(supplied); supplied.clear();
        var current = initial;
        for (int x = 1; x <= 150; x++) {
            var next = current.invalidate(new BlockPosition(x, 62, 0));
            assertFalse(current.positions().contains(new BlockPosition(x, 62, 0)));
            assertEquals(x + 1, next.positions().size());
            assertDoesNotThrow(() -> ExtractionTerrainFacts.validateExclusions(known, next));
            current = next;
        }
        assertEquals(Set.of(new BlockPosition(0, 62, 0)), initial.positions());
        var retained = current;
        assertSame(retained, retained.invalidate(new BlockPosition(150, 62, 0)));
        assertThrows(UnsupportedOperationException.class, () -> retained.positions().add(new BlockPosition(200, 62, 0)));
        assertThrows(UnsupportedOperationException.class, () -> retained.positions().iterator().remove());
        var state = FrontierWorldState.initial(known).withChanges(FrontierWorldStateUpdate.begin()
                .extractionSites(new io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionSiteState(
                        java.util.Map.of(), java.util.Map.of(), 1, retained)));
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        assertEquals(state, codec.decode(codec.encode(state)));
        var invalid = retained.invalidate(new BlockPosition(10000, 62, 0));
        assertThrows(IllegalArgumentException.class, () -> ExtractionTerrainFacts.validateExclusions(known, invalid));
    }

    @Test void normalWorldProjectionCanCrossOldLifetimeLimitAndRecoverWithoutForgettingEdits() {
        var before = FrontierWorldState.initial(bootstrap("frontier-v3-quarry-graybox-r7"));
        var positions = new java.util.HashSet<BlockPosition>();
        for (int x = -128; x < 128; x++) for (int z = 0; z < 128; z++)
            positions.add(new BlockPosition(x, 65, z));
        var old = new KnownBlockExclusions(positions);
        var added = Set.of(new BlockPosition(-129, 65, -1), new BlockPosition(129, 65, 0));
        var next = old.invalidate(added);
        assertEquals(32770, next.positions().size());
        assertEquals(added, next.additionsSince(old).orElseThrow());
        assertEquals(32768, old.positions().size());
        assertTrue(old.additionsSince(next).isEmpty());
        var after = before.withChanges(FrontierWorldStateUpdate.begin().extractionSites(
                new io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionSiteState(
                        java.util.Map.of(), java.util.Map.of(), 1, next)));
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        assertEquals(after, codec.decode(codec.encode(after)));
        assertEquals(before.inventory(), after.inventory());
        assertTrue(before.extractionSites().geologicalExclusions().positions().isEmpty());
    }

    @Test void remoteChangesDoNotRebuildSitesButLocalInvalidationsStillReachTheirProviderAndRecovery() {
        var before = FrontierWorldState.initial(bootstrap("frontier-v3-quarry-graybox-r7"));
        var sites = KnownSiteGeometry.forState(before);
        var provider = new ExtractionKnownGeometry(); var priorVersion = provider.version(before);
        var remote = new ExtractionGeologyInvalidated(new BlockPosition(0, 65, 0), 1)
                .apply(before, ExtractionGeologyInvalidated.OWNER);
        assertSame(sites, KnownSiteGeometry.forState(remote));
        assertSame(priorVersion, provider.version(remote));
        var local = before.extractionSites().deposits().values().stream()
                .flatMap(deposit -> deposit.site().layout().terrainSupports().keySet().stream())
                .filter(position -> before.extractionSites().deposits().values().stream().noneMatch(deposit ->
                        deposit.site().layout().fixedBlocks().containsKey(position)
                        || deposit.site().layout().cells().stream().anyMatch(cell -> cell.source().equals(position))))
                .findFirst().orElseThrow();
        var changed = new ExtractionGeologyInvalidated(local, 1).apply(remote, ExtractionGeologyInvalidated.OWNER);
        assertNotSame(priorVersion, provider.version(changed), "local natural geometry changes must still reach the site owner");
        assertTrue(ExtractionGroundKnowledge.forState(changed).at(local).isEmpty());
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        var recovered = codec.decode(codec.encode(changed));
        assertTrue(ExtractionGroundKnowledge.forState(recovered).at(local).isEmpty());
        assertEquals(KnownSiteGeometry.forState(changed), KnownSiteGeometry.forState(recovered));
    }
}
