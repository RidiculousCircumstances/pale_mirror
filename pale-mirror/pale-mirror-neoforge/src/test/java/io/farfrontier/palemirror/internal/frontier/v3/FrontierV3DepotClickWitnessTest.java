package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FungiblePhysicalObservation;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrontierV3DepotClickWitnessTest {
    @Test void witnessRecoveryRecognizesOnlyTheExactAlreadyClosedMutationNotAnOrdinaryRelease() {
        var prior = io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaRecord.expected(DEPOT, "inventory", 0, "before", "provenance")
                .observe(0, "before", "provenance", 0);
        var closed = prior.reemit(2, "after", "provenance");
        var lease = new io.farfrontier.palemirror.frontier.v3.model.PhysicalCustodyLease(
                io.farfrontier.palemirror.frontier.v3.model.ReferenceContainerCustody.scopeId(DEPOT), DEPOT,
                io.farfrontier.palemirror.frontier.v3.model.ReferenceContainerCustody.PROVIDER_ID, 7, 0, prior.replicaRevision(),
                io.farfrontier.palemirror.frontier.v3.model.PhysicalCustodyLeaseStatus.RELEASED, null);
        org.junit.jupiter.api.Assertions.assertTrue(FrontierV3ReferenceContainerCustodyExecutor.confirmedMutationAlreadyClosed(
                lease, closed, "after", "after", "provenance"));
        org.junit.jupiter.api.Assertions.assertFalse(FrontierV3ReferenceContainerCustodyExecutor.confirmedMutationAlreadyClosed(
                lease, prior, "before", "before", "provenance"));
        org.junit.jupiter.api.Assertions.assertFalse(FrontierV3ReferenceContainerCustodyExecutor.confirmedMutationAlreadyClosed(
                lease, closed, "after", "foreign", "provenance"));
        org.junit.jupiter.api.Assertions.assertFalse(FrontierV3ReferenceContainerCustodyExecutor.confirmedMutationAlreadyClosed(
                lease, closed, "after", "after", "foreign"));
        org.junit.jupiter.api.Assertions.assertFalse(FrontierV3ReferenceContainerCustodyExecutor.confirmedMutationAlreadyClosed(
                lease, closed, "changed-again", "after", "provenance"));
    }
    @Test void composedWitnessFencesEveryMemberAndRecoversAsOneTransaction() {
        var second = new SubjectId("container:second-depot");
        var left = new FrontierV3DepotClickWitness(DEPOT, ACCOUNT, OWNER, 7, PLAYER, INTERACTION, List.of(), Optional.empty());
        var right = new FrontierV3DepotClickWitness(second, new SubjectId("custody:second-depot"), OWNER, 7,
                PLAYER, INTERACTION, List.of(), Optional.empty());
        var prepared = new FrontierV3ContainerClickTransaction(List.of(left, right));
        assertEquals(prepared, FrontierV3ContainerClickTransaction.read(prepared.save()));
        var empty = new net.minecraft.nbt.CompoundTag(); empty.putInt("format", 3); empty.put("pending", new net.minecraft.nbt.ListTag());
        var ledger = FrontierV3DepotClickLedger.load(empty, null); ledger.prepare(prepared);
        assertEquals(left, ledger.pending(DEPOT)); assertEquals(right, ledger.pending(second));
        assertThrows(IllegalArgumentException.class, () -> ledger.prepare(right));
        assertThrows(IllegalArgumentException.class, () -> ledger.retire(left));
        var observed = new FrontierV3ContainerClickTransaction(List.of(left.observed(List.of(stack(0, 1))), right.observed(List.of())));
        ledger.observe(prepared, observed);
        var recovered = FrontierV3DepotClickLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag(), null), null);
        assertEquals(observed, recovered.transaction(second));
        assertThrows(IllegalArgumentException.class, () -> recovered.retire(left));
        recovered.retire(observed); assertEquals(List.of(), recovered.pending());
        assertThrows(IllegalArgumentException.class, () -> new FrontierV3ContainerClickTransaction(List.of(left.observed(List.of()), right)));
        assertThrows(IllegalArgumentException.class, () -> new FrontierV3ContainerClickTransaction(List.of(left, left)));
    }
    private static final SubjectId DEPOT = new SubjectId("container:one-depot");
    private static final SubjectId ACCOUNT = new SubjectId("custody:container-one-depot");
    private static final SubjectId OWNER = new SubjectId("settlement:one");
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000141");
    private static final UUID INTERACTION = UUID.fromString("00000000-0000-0000-0000-000000000142");

    @Test void activeQuarryChestUsesTheSamePlayerEditBoundaryAsADepot() {
        var state = io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState.initial(
                io.farfrontier.palemirror.frontier.v3.model.FrontierBootstrapper.create(
                        new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:quarry-click-owner"),
                        20260918065L, io.farfrontier.palemirror.frontier.v3.model.FrontierRulesets.installed(
                                "frontier-v3-quarry-graybox-r4")));
        var site = state.extractionSites().deposits().values().iterator().next().site();
        var inventory = state.inventory().withSurfaceStatus(io.farfrontier.palemirror.frontier.v3.model.QuarryStorageFixture.container(state, site),
                io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(io.farfrontier.palemirror.frontier.v3.model.QuarryStorageFixture.container(state, site), io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus.ACTIVE);
        state = state.withChanges(io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate.begin().inventory(inventory));
        var at = inventory.surfaces().get(io.farfrontier.palemirror.frontier.v3.model.QuarryStorageFixture.container(state, site)).position();
        var position = new net.minecraft.core.BlockPos(at.x(), at.y(), at.z());
        assertEquals(io.farfrontier.palemirror.frontier.v3.model.QuarryStorageFixture.container(state, site), FrontierV3ContainerClickExecutor.ownedContainer(state, position).id());
        assertEquals(null, FrontierV3ContainerClickExecutor.ownedContainer(state, position.above(10)));
    }

    @Test void durablePreAndPostLayoutsRoundTripIncludingAnEmptyChest() {
        var prepared = new FrontierV3DepotClickWitness(DEPOT, ACCOUNT, OWNER, 7, PLAYER, INTERACTION,
                List.of(stack(0, 64), stack(1, 8)), Optional.empty());
        assertEquals(prepared, FrontierV3DepotClickWitness.read(prepared.save()));
        var observed = prepared.observed(List.of());
        assertEquals(observed, FrontierV3DepotClickWitness.read(observed.save()));
        assertThrows(IllegalArgumentException.class, () -> observed.observed(List.of(stack(0, 1))));
    }

    @Test void trackedReturnPersistsBothExactSourceAndPostimageAndRejectsMissingEvidence() {
        var source = new FrontierV3DepotClickWitness.ReturnSource(new SubjectId("binding:player-return"),
                new SubjectId("custody:player-return"), 3, 9, "minecraft:bread", 10);
        var prepared = new FrontierV3DepotClickWitness(DEPOT, ACCOUNT, OWNER, 7, PLAYER, INTERACTION,
                List.of(), Optional.empty(), Optional.of(source), Optional.empty());
        assertEquals(prepared, FrontierV3DepotClickWitness.read(prepared.save()));
        assertThrows(IllegalArgumentException.class, () -> prepared.observed(List.of(stack(0, 6))));
        var observed = prepared.observed(List.of(stack(0, 6)), Optional.of(4));
        assertEquals(observed, FrontierV3DepotClickWitness.read(observed.save()));
        assertThrows(IllegalArgumentException.class, () -> prepared.observed(List.of(stack(0, 6)), Optional.of(11)));
    }

    private static FungiblePhysicalObservation.Stack stack(int slot, int count) {
        return new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(
                new InventoryCustody.ContainerSlot(DEPOT, slot)), "minecraft:bread", count);
    }
}
