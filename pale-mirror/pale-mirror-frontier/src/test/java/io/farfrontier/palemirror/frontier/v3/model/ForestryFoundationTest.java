package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.forestry.*;
import io.farfrontier.palemirror.frontier.v3.model.tooling.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ForestryFoundationTest {
    private static final FrontierBootstrap BOOTSTRAP = FrontierBootstrapper.create(new WorldId("frontier:forest"), 126,
            FrontierRulesets.installed("frontier-v3-forestry-graybox-r28"));

    @Test void authoredForestUsesThreeDeclaredHomesAndOneConsistentTreeAcrossChunkQueries() {
        var rules = BOOTSTRAP.ruleset().forestry(); var plan = GrayboxForestPlan.compile(BOOTSTRAP);
        var miners = GrayboxQuarryPlan.producerSettlements(BOOTSTRAP).stream().map(Settlement::id).collect(java.util.stream.Collectors.toSet());
        assertEquals(3, rules.patches().size());
        assertEquals(1, rules.patches().stream().filter(patch -> miners.contains(patch.knownBy())).count());
        var trees = new HashMap<TreeId, TreeDescriptor>();
        var images = new HashMap<ForestRegion, Map<BlockPosition, io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction.Block>>();
        for (var patch : rules.patches()) {
            var home = BOOTSTRAP.settlements().stream().filter(s -> s.id().equals(patch.knownBy())).findFirst().orElseThrow();
            int before = trees.size();
            for (int x = (home.anchor().x() - patch.outerRadius()) >> 4; x <= (home.anchor().x() + patch.outerRadius()) >> 4; x++)
                for (int z = (home.anchor().z() - patch.outerRadius()) >> 4; z <= (home.anchor().z() + patch.outerRadius()) >> 4; z++)
                    for (var tree : plan.touching(new ForestRegion(x, z))) {
                        var previous = trees.putIfAbsent(tree.id(), tree);
                        if (previous != null) assertEquals(previous, tree, "chunk query cannot change a tree's composition");
                    }
            assertTrue(trees.size() > before, "each declared home must have an actual usable forest");
        }
        for (var tree : trees.values()) for (int i = 0; i < tree.logs().size(); i++) {
            var position = tree.logs().position(tree.origin(), i); var region = ForestRegion.containing(position);
            assertTrue(plan.touching(region).contains(tree));
            assertEquals(tree.logs().block(i), images.computeIfAbsent(region, plan::blocksIn).get(position));
        }
    }
    @Test void aCapturedTreeOutsideAuthoredPatchesStillOverridesAnUnknownRegion() {
        var origin = new BlockPosition(BOOTSTRAP.bounds().minX() + 32, 64, BOOTSTRAP.bounds().minZ() + 32);
        var region = ForestRegion.containing(origin);
        assertFalse(GrayboxForestPlan.compile(BOOTSTRAP).mayTouch(region));
        var prototype = firstTree();
        var tree = new TreeDescriptor(new TreeId(UUID.fromString("bcc6524a-67a7-454d-a7ef-1cb9537e46f4")), 1,
                "minecraft:tree_feature", prototype.species(), origin, prototype.logs());
        var state = FrontierWorldState.initial(BOOTSTRAP);
        state = state.withChanges(FrontierWorldStateUpdate.begin().forest(state.forest().admit(tree)));
        var geometry = new ForestryKnownGeometry().snapshot(state).overlay((x, z) -> null);
        assertTrue(geometry.collisionAt(origin).isPresent(), "captured trees must not depend on initial forest coverage");
        assertTrue(geometry.collisionAt(origin.offset(0, -1, 0)).isEmpty(), "a sparse overlay cannot invent ground knowledge");
    }
    @Test void playerWithdrawalPersistsThroughUnrelatedUpdateAndRecoveryWithoutIssuingWood() {
        var initial = FrontierWorldState.initial(BOOTSTRAP); var tree = firstTree();
        var referenceBefore = TreeReference.declaredBy(tree);
        var preparing = ForestrySourceCustody.apply(initial, ForestrySourceCustody.object(referenceBefore),
                new ForestrySourceBoundary(referenceBefore, ForestrySourceBoundary.Operation.PREPARE, 0, 0,
                        ForestrySourceCustody.fingerprint(ForestTree.initial(tree))), 1);
        initial = initial.withChanges(FrontierWorldStateUpdate.begin().replicaCustody(preparing));
        var lease = preparing.custodyByScope().get(ForestrySourceCustody.scope(referenceBefore));
        var observation = new ForestObservation.Withdrawn(List.of(new ForestObservation.Withdrawal(tree, 1,
                lease.authorityEpoch(), lease.expectedReplicaRevision(), List.of(0))));
        var changed = ForestObservation.apply(initial, observation, 2);
        assertSame(initial.inventory(), changed.inventory(), "external removal is not a lumberjack receipt");
        var unrelated = changed.withHumanPopulation(changed.humanPopulation());
        var reference = TreeReference.declaredBy(tree);
        assertFalse(unrelated.forest().require(reference).present(0));
        var codec = new FrontierWorldStateCodec(); var restored = codec.decode(codec.encode(unrelated));
        assertEquals(changed.forest(), restored.forest());
        assertFalse(restored.forest().require(reference).present(0));
        assertThrows(IllegalArgumentException.class, () -> ForestObservation.apply(restored, observation, 3));
        assertThrows(IllegalArgumentException.class, () -> restored.forest().require(new TreeReference(reference.id(),
                reference.generation() + 1, reference.region())));
        assertThrows(IllegalArgumentException.class, () -> restored.forest().replace(restored.forest().require(reference),
                new ForestTree(tree, 3, new long[Math.ceilDiv(tree.logs().size(), 64)])));
    }
    @Test void reserveStopsAtItsTargetAndRestartsOnlyBelowTheResumeThresholdAfterActualLoss() {
        var state = FrontierWorldState.initial(BOOTSTRAP); var home = new SubjectId("settlement:7");
        var rules = BOOTSTRAP.ruleset().forestry(); String kind = "minecraft:oak_log"; var initial = state;
        var container = state.inventory().containers().values().stream().filter(record -> record.ownerId().equals(home)
                && record.storage().filter(policy -> policy.filter().accepts(kind)).isPresent()
                && FungibleResourceCustodySupport.accountAtContainer(initial, record.id()).isPresent()).findFirst().orElseThrow();
        var lot = new SubjectId("lot:forest-reserve-fixture"); var staging = new SubjectId("custody:forest-reserve-fixture");
        var account = FungibleResourceCustodySupport.accountAtContainer(state, container.id()).orElseThrow().id();
        var resources = state.inventory().fungibleResources().issue(new ResourceLot(lot, home, kind, rules.reserveItems(), "fixture:wood", List.of()),
                new CustodyAccount(staging, new ResourceCustody.Player(UUID.fromString("00000000-0000-0000-0000-000000000264")),
                        Map.of(lot, rules.reserveItems()), Map.of())).transfer(staging, account, Map.of(lot, rules.reserveItems()), Map.of());
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        assertFalse(ForestryStockDemand.requested(state, home, kind, Optional.empty()));
        resources = resources.destroy(account, Map.of(lot, rules.reserveItems()-rules.resumeBelowItems()), Map.of());
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        assertFalse(ForestryStockDemand.requested(state, home, kind, Optional.empty()), "small withdrawals must not restart harvesting");
        resources = resources.destroy(account, Map.of(lot, 1), Map.of());
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        assertTrue(ForestryStockDemand.requested(state, home, kind, Optional.empty()));
    }
    @Test void axeUsesExistingIndividualManufactureRepairAndExplicitChopCapability() {
        var rules = BOOTSTRAP.ruleset(); var axe = rules.tools().require("minecraft:iron_axe");
        assertTrue(axe.supports(new ToolRequirement(ToolAbilities.CHOP, 1, 1)));
        assertFalse(rules.tools().require("minecraft:iron_pickaxe").supports(new ToolRequirement(ToolAbilities.CHOP, 1, 1)));
        var recipes = rules.production().recipes().stream().filter(recipe -> recipe.product().itemKind().equals(axe.itemKind())).toList();
        assertEquals(2, recipes.size());
        assertTrue(recipes.stream().allMatch(recipe -> recipe.ingredient().acceptedItems().equals(Set.of("minecraft:iron_ingot"))));
        assertEquals(ForestryRules.disabled(), FrontierRulesets.installed("frontier-v3-quarry-graybox-r27").forestry());
    }
    @Test void regrowthNeedsANewSourceAndCannotOverwriteAnUnsettledPriorGeneration() {
        var initial = FrontierWorldState.initial(BOOTSTRAP); var old = firstTree();
        var successor = new TreeDescriptor(new TreeId(UUID.fromString("137514d3-880a-477b-9651-182db2e97d5d")), 1,
                "minecraft:tree_feature", old.species(), old.origin(), old.logs());
        var observation = new ForestObservation.Generated(BOOTSTRAP.ruleset().contentSha256(), List.of(successor));
        assertThrows(IllegalArgumentException.class, () -> ForestObservation.apply(initial, observation, 1));
        var retired = ForestTree.initial(old);
        for (int log = 0; log < old.logs().size(); log++) retired = retired.external(log);
        var forest = initial.forest().admit(old); forest = forest.replace(forest.require(TreeReference.declaredBy(old)), retired);
        var withdrawn = initial.withChanges(FrontierWorldStateUpdate.begin().forest(forest));
        var regrown = ForestObservation.apply(withdrawn, observation, 2);
        assertSame(withdrawn.inventory(), regrown.inventory(), "growth registers a source, not harvested resources");
        assertEquals(0, regrown.forest().require(TreeReference.declaredBy(old)).remaining());
        assertEquals(successor.logs().size(), regrown.forest().require(TreeReference.declaredBy(successor)).remaining());
        assertEquals(TreeReference.declaredBy(successor), ForestKnowledge.at(regrown, successor.origin()).orElseThrow().tree());
        assertEquals(regrown, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(regrown)));
    }
    private static TreeDescriptor firstTree() {
        var plan = GrayboxForestPlan.compile(BOOTSTRAP); var patch = BOOTSTRAP.ruleset().forestry().patches().getFirst();
        var home = BOOTSTRAP.settlements().stream().filter(s -> s.id().equals(patch.knownBy())).findFirst().orElseThrow();
        for (int x = (home.anchor().x() - patch.outerRadius()) >> 4; x <= (home.anchor().x() + patch.outerRadius()) >> 4; x++)
            for (int z = (home.anchor().z() - patch.outerRadius()) >> 4; z <= (home.anchor().z() + patch.outerRadius()) >> 4; z++) {
                var trees = plan.touching(new ForestRegion(x, z)); if (!trees.isEmpty()) return trees.getFirst();
            }
        throw new AssertionError("declared forest is empty");
    }
}
