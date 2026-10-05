package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellTransition;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPhysicalSurface;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldForeignChangeHeld;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3ResourceFieldWitnessTest {
    @Test void observedBiologyRecoveryCannotUseAFabricatedWorldReview() {
        var cycle = ResourceFieldCycle.seeded(SITE, layout(), 1);
        var witness = FrontierV3ResourceFieldWitness.claimed(SITE, 1, ResourceFieldPhysicalSurface.fromCycle(cycle));
        var grown = cycle.advanceGrowth(FIRST);
        var accepted = new io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<>(
                new io.farfrontier.palemirror.frontier.v3.api.WorldId("frontier:test"),
                new io.farfrontier.palemirror.frontier.v3.api.Revision(1),
                new io.farfrontier.palemirror.frontier.v3.api.SimInstant(1), grown);
        var fabricated = FrontierV3ResourceFieldObservation.compare(witness, layout(), FIRST,
                new FrontierV3ResourceFieldObservation.Owned(ResourceFieldPhysicalSurface.Condition.of(grown.cell(FIRST))));
        assertEquals(FrontierV3ResourceFieldObservation.Disposition.OWNED_DRIFT, fabricated.disposition());
        assertThrows(IllegalArgumentException.class, () -> witness.acknowledgeObservedGrowth(accepted, FIRST, fabricated));
        assertEquals(0, witness.cell(FIRST).committed().growthStage());
    }

    @Test void acceleratedGrowthRetainsExactSuccessorAcrossPhysicalWitnessRecovery() {
        var change = io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved.Change.CROP_GROWN;
        var observed = new io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved(SITE, 1,
                layout().revision(), FIRST, PLANTED, RIPE, change,
                io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved.Source.WORLD, "world:bone-meal-test");
        var witness = new FrontierV3ResourceFieldWorldChangeWitness(observed);
        var encoded = witness.write();
        assertEquals(witness, FrontierV3ResourceFieldWorldChangeWitness.read(encoded));
        encoded.remove("afterStage");
        assertThrows(IllegalStateException.class, () -> FrontierV3ResourceFieldWorldChangeWitness.read(encoded));
        assertThrows(IllegalArgumentException.class, () -> new io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved(
                SITE, 1, layout().revision(), FIRST, RIPE, PLANTED, change,
                io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved.Source.WORLD, "world:backwards"));
    }
    @Test void externalHarvestAndReplantHasExactRecoverableWorldWitness() {
        var after = new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.FARMLAND, ResourceFieldCycle.Crop.GROWING, 0);
        var change = FrontierV3ResourceFieldWorldChangeExecutor.classify(RIPE, after);
        assertEquals(io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved.Change.CROP_REPLANTED, change);
        var observed = new io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved(SITE, 1,
                layout().revision(), FIRST, RIPE, after, change,
                io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved.Source.WORLD, "world:replant-test");
        var witness = new FrontierV3ResourceFieldWorldChangeWitness(observed);
        assertEquals(witness, FrontierV3ResourceFieldWorldChangeWitness.read(witness.write()));
        assertEquals(io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved.Change.CROP_GROWN,
                FrontierV3ResourceFieldWorldChangeExecutor.classify(after, RIPE),
                "growth is a separate observation, never an external harvest");
        assertEquals(null, FrontierV3ResourceFieldWorldChangeExecutor.classify(after, after));
    }
    private static final SubjectId SITE = new SubjectId("site:field-witness-test");
    private static final ResourceFieldLayout.CellId FIRST = new ResourceFieldLayout.CellId(1);
    private static final ResourceFieldLayout.CellId SECOND = new ResourceFieldLayout.CellId(2);
    private static final ResourceFieldPhysicalSurface.Condition DIRT = new ResourceFieldPhysicalSurface.Condition(
            ResourceFieldCycle.Soil.DIRT, ResourceFieldCycle.Crop.ABSENT, 0);
    private static final ResourceFieldPhysicalSurface.Condition BARE = new ResourceFieldPhysicalSurface.Condition(
            ResourceFieldCycle.Soil.FARMLAND, ResourceFieldCycle.Crop.ABSENT, 0);
    private static final ResourceFieldPhysicalSurface.Condition PLANTED = new ResourceFieldPhysicalSurface.Condition(
            ResourceFieldCycle.Soil.FARMLAND, ResourceFieldCycle.Crop.GROWING, 0);
    private static final ResourceFieldPhysicalSurface.Condition RIPE = new ResourceFieldPhysicalSurface.Condition(
            ResourceFieldCycle.Soil.FARMLAND, ResourceFieldCycle.Crop.MATURE, 7);

    @Test void playerCropLossRequiresTheDurableActionAndAcceptedCanonicalPostcondition() {
        ResourceFieldCycle cycle = ResourceFieldCycle.seeded(SITE, layout(), 1);
        var before = ResourceFieldPhysicalSurface.Condition.of(cycle.cell(FIRST));
        var witness = FrontierV3ResourceFieldWitness.claimed(SITE, 1, ResourceFieldPhysicalSurface.fromCycle(cycle));
        var prepared = new io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPlayerBreakPrepared(
                SITE, 1, layout().revision(), FIRST, before,
                UUID.fromString("00000000-0000-0000-0000-000000000127"), "action:player-crop-1");
        var action = FrontierV3ResourceFieldPlayerBreakWitness.prepared(prepared);
        assertEquals(action, FrontierV3ResourceFieldPlayerBreakWitness.read(action.write()));
        var changed = action.observed(io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved.Change.CROP_REMOVED);
        assertEquals(changed, FrontierV3ResourceFieldPlayerBreakWitness.read(changed.write()));
        var pending = cycle.preparePlayerBreak(FIRST, new ResourceFieldCycle.PendingPlayerBreak(
                prepared.playerId(), prepared.actionId(), before));
        var accepted = pending.closePlayerBreak(FIRST, prepared.actionId()).cropRemoved(FIRST);
        var physical = new FrontierV3ResourceFieldObservation.Owned(BARE);
        assertThrows(IllegalArgumentException.class, () -> witness.acknowledgePlayerBreak(action, accepted, physical));
        assertThrows(IllegalArgumentException.class, () -> witness.acknowledgePlayerBreak(changed, pending, physical));
        assertEquals(BARE, witness.acknowledgePlayerBreak(changed, accepted, physical).cell(FIRST).committed());
        assertEquals(ResourceFieldPhysicalSurface.Condition.of(cycle.cell(FIRST)), witness.cell(FIRST).committed(),
                "accepting a player result cannot mutate the predecessor witness in place");
    }

    @Test void worldCellLossRequiresExactDurablePostconditionBeforeClaimMoves() {
        ResourceFieldCycle cycle = ResourceFieldCycle.seeded(SITE, layout(), 1);
        var before = ResourceFieldPhysicalSurface.Condition.of(cycle.cell(FIRST));
        var after = BARE;
        var observed = new io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved(SITE, 1,
                layout().revision(), FIRST, before, after,
                io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved.Change.CROP_REMOVED,
                io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved.Source.WORLD,
                "world:crop-loss-test");
        var change = new FrontierV3ResourceFieldWorldChangeWitness(observed);
        assertEquals(change, FrontierV3ResourceFieldWorldChangeWitness.read(change.write()));
        assertEquals(observed.change(), FrontierV3ResourceFieldWorldChangeExecutor.classify(before, after));
        assertEquals(io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved.Change.CROP_GROWN,
                FrontierV3ResourceFieldWorldChangeExecutor.classify(before, RIPE));
        assertEquals(io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved.Change.SOIL_BECAME_DIRT,
                FrontierV3ResourceFieldWorldChangeExecutor.classify(before, DIRT));
        var witness = FrontierV3ResourceFieldWitness.claimed(SITE, 1, ResourceFieldPhysicalSurface.fromCycle(cycle));
        var physical = new FrontierV3ResourceFieldObservation.Owned(after);
        assertThrows(IllegalArgumentException.class, () -> witness.acknowledgeWorldChange(change, cycle, physical));
        assertEquals(after, witness.acknowledgeWorldChange(change, cycle.cropRemoved(FIRST), physical)
                .cell(FIRST).committed());
        assertEquals(before, witness.cell(FIRST).committed());
    }

    @Test void foreignIncidentRetainsExactNbtAndClearanceNeedsAnOwnedPhysicalResult() {
        ResourceFieldCycle cycle = ResourceFieldCycle.seeded(SITE, layout(), 1);
        var held = new ResourceFieldForeignChangeHeld(SITE, 1, layout().revision(), FIRST,
                cycle.cell(FIRST), "world:foreign-stone");
        var physical = new FrontierV3ResourceFieldForeignChangeWitness.Blocks(
                block("minecraft:farmland"), block("minecraft:stone"));
        var change = new FrontierV3ResourceFieldForeignChangeWitness(held, java.util.Optional.of(physical));
        assertEquals(change, FrontierV3ResourceFieldForeignChangeWitness.read(change.write()));
        var blocked = new ResourceFieldCycle.CellState(ResourceFieldCycle.Soil.FARMLAND,
                ResourceFieldCycle.Crop.OBSTRUCTED, 0, false, false);
        var accepted = cycle.observedForeignTransition(FIRST, blocked);
        var claim = FrontierV3ResourceFieldWitness.claimed(SITE, 1, ResourceFieldPhysicalSurface.fromCycle(cycle));
        var reading = new FrontierV3ResourceFieldObservation.Foreign(
                new FrontierV3ResourceFieldWitness.ForeignIncident(physical.soil(), physical.crop(), "observation:different-cause"));
        assertThrows(IllegalArgumentException.class, () -> claim.acknowledgeForeignChange(change, cycle, physical, reading));
        assertThrows(IllegalArgumentException.class, () -> claim.acknowledgeForeignChange(change, accepted,
                new FrontierV3ResourceFieldForeignChangeWitness.Blocks(block("minecraft:farmland"), block("minecraft:diamond_block")), reading));
        var blockedClaim = claim.acknowledgeForeignChange(change, accepted, physical, reading);
        assertEquals(FrontierV3HotHandoff.Status.READY, FrontierV3ResourceFieldHandoff.inspectCells(
                accepted, blockedClaim, List.of(layout().requireCell(FIRST)), ignored -> reading).status(),
                "an exactly observed canonical obstruction excludes one cell, not the whole field's handoff");
        assertEquals(FrontierV3HotHandoff.Status.CONFLICT, FrontierV3ResourceFieldHandoff.inspectCells(
                accepted, blockedClaim, List.of(layout().requireCell(FIRST)), ignored ->
                        new FrontierV3ResourceFieldObservation.Foreign(new FrontierV3ResourceFieldWitness.ForeignIncident(
                                physical.soil(), block("minecraft:diamond_block"), "world:new-drift"))).status());
        assertEquals(physical.crop(), blockedClaim.cell(FIRST).foreign().orElseThrow().observedCrop());
        assertEquals(PLANTED, blockedClaim.cell(FIRST).committed(),
                "a foreign block cannot become an owned projection predecessor");

        var clearHold = new ResourceFieldForeignChangeHeld(SITE, 1, layout().revision(), FIRST,
                blocked, "world:clear-stone");
        var clearBlocks = new FrontierV3ResourceFieldForeignChangeWitness.Blocks(
                block("minecraft:farmland"), block("minecraft:air"));
        var clearance = new FrontierV3ResourceFieldForeignChangeWitness(clearHold, java.util.Optional.of(clearBlocks));
        var cleared = accepted.observedForeignTransition(FIRST, new ResourceFieldCycle.CellState(
                ResourceFieldCycle.Soil.FARMLAND, ResourceFieldCycle.Crop.ABSENT, 0, false, false));
        var clearedClaim = blockedClaim.acknowledgeForeignChange(clearance, cleared, clearBlocks,
                new FrontierV3ResourceFieldObservation.Owned(BARE));
        assertTrue(clearedClaim.cell(FIRST).foreign().isEmpty());
        assertEquals(BARE, clearedClaim.cell(FIRST).committed());
    }

    @Test void coldEpochRebasePreservesOldPhysicalCellsButCannotErasePendingHotWork() {
        ResourceFieldLayout layout = layout();
        var original = FrontierV3ResourceFieldWitness.claimed(SITE, 1,
                ResourceFieldPhysicalSurface.restore(layout, Map.of(FIRST, RIPE, SECOND, PLANTED)));
        var later = ResourceFieldCycle.seeded(SITE, layout, 4);
        var rebased = FrontierV3ResourceFieldWitness.read(original.rebaseColdEpoch(later).write());
        assertTrue(rebased.matchesCycle(later));
        assertEquals(RIPE, rebased.cell(FIRST).committed(),
                "an epoch label cannot pretend that COLD successor blocks were materialized");
        assertEquals(PLANTED, rebased.cell(SECOND).committed());
        var foreign = new FrontierV3ResourceFieldWitness.ForeignIncident(
                block("minecraft:dirt"), block("minecraft:stone"), "player:old-epoch-obstruction");
        var rebasedForeign = FrontierV3ResourceFieldWitness.read(original.foreign(SECOND, foreign)
                .rebaseColdEpoch(later).write());
        assertEquals(foreign, rebasedForeign.cell(SECOND).foreign().orElseThrow(),
                "a later canonical cycle cannot silently clear an old physical obstruction");
        assertThrows(IllegalArgumentException.class, () -> original.rebaseColdEpoch(ResourceFieldCycle.seeded(SITE, layout, 1)));
        assertThrows(IllegalArgumentException.class, () -> original.rebaseColdEpoch(ResourceFieldCycle.seeded(
                new SubjectId("site:foreign"), layout, 4)));
        var playerPending = later.preparePlayerBreak(FIRST, new ResourceFieldCycle.PendingPlayerBreak(
                UUID.fromString("00000000-0000-0000-0000-000000000126"), "player:unresolved:1",
                ResourceFieldPhysicalSurface.Condition.of(later.cell(FIRST))));
        assertThrows(IllegalArgumentException.class, () -> original.rebaseColdEpoch(playerPending),
                "a successor with an unresolved Vanilla action cannot be used as a physical hand-off");
        var pending = original.begin(ResourceFieldCellTransition.harvestAndReplant(SITE, 1, 1, FIRST, RIPE),
                "farmer:unresolved:1");
        assertThrows(IllegalStateException.class, () -> pending.rebaseColdEpoch(later),
                "even an unloaded later epoch must not erase an unfinished physical cause");
        assertEquals(1, pending.epoch());
    }

    @Test void committedCellPrefixAndForeignIncidentSurviveExactNbtRecovery() {
        ResourceFieldLayout layout = layout();
        var surface = ResourceFieldPhysicalSurface.restore(layout, Map.of(FIRST, DIRT, SECOND, PLANTED));
        var transition = ResourceFieldCellTransition.between(SITE, 1, 1, FIRST, DIRT, PLANTED);
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ResourceFieldWitness.claimed(SITE, 1, surface).begin(
                ResourceFieldCellTransition.between(new SubjectId("site:foreign"), 1, 1, FIRST, DIRT, PLANTED),
                "farmer:foreign:repair"), "matching cell geometry cannot borrow another site's effect owner");
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ResourceFieldWitness.claimed(SITE, 2, surface).begin(
                transition, "farmer:old-epoch:repair"), "a previous cycle cannot begin work in the current epoch");
        var pending = FrontierV3ResourceFieldWitness.claimed(SITE, 1, surface).begin(transition, "farmer:repair:1");
        assertEquals(SITE, pending.cell(FIRST).pending().orElseThrow().transition().siteId());
        var recovered = FrontierV3ResourceFieldWitness.read(pending.write());
        assertEquals(1, recovered.epoch());
        assertEquals(SITE, recovered.cell(FIRST).pending().orElseThrow().transition().siteId());
        assertTrue(recovered.matchesLayout(SITE, layout));
        assertTrue(recovered.matchesCycle(ResourceFieldCycle.seeded(SITE, layout, 1)));
        assertFalse(recovered.matchesCycle(ResourceFieldCycle.seeded(SITE, layout, 2)),
                "same field geometry in a successor cycle cannot adopt its predecessor witness");
        assertFalse(recovered.matchesLayout(new SubjectId("site:foreign"), layout));
        assertEquals(DIRT, recovered.cell(FIRST).committed());
        assertEquals(0, recovered.cell(FIRST).pending().orElseThrow().completedSteps());
        assertEquals("farmer:repair:1", recovered.cell(FIRST).pending().orElseThrow().causationId());
        assertEquals(PLANTED, recovered.cell(SECOND).committed());
        assertThrows(IllegalArgumentException.class, () -> recovered.confirm(FIRST, review(recovered, layout, FIRST, PLANTED)),
                "a synthetic comparison cannot confirm a later physical prefix");
        assertEquals(FrontierV3ResourceFieldObservation.Disposition.LATER_STEP_APPLIED,
                review(recovered, layout, FIRST, PLANTED).disposition(),
                "a crash after both writes must retain a recoverable later-prefix classification");
        assertThrows(IllegalArgumentException.class, () -> recovered.confirm(FIRST, review(recovered, layout, FIRST, BARE)),
                "a synthetic comparison cannot confirm even the correct next step");

        var foreignSoil = block("minecraft:diamond_block");
        var foreignCrop = block("minecraft:stone");
        var incident = new FrontierV3ResourceFieldWitness.ForeignIncident(foreignSoil, foreignCrop, "player:exact-edit");
        var blocked = FrontierV3ResourceFieldWitness.read(recovered.foreign(SECOND, incident).write());
        foreignSoil.putString("Name", "minecraft:dirt");
        assertEquals("minecraft:diamond_block", blocked.cell(SECOND).foreign().orElseThrow().observedSoil().getString("Name"));
        assertThrows(IllegalArgumentException.class, () -> blocked.begin(
                ResourceFieldCellTransition.between(SITE, 1, 1, SECOND, PLANTED, BARE), "farmer:blocked:2"));
        assertFalse(blocked.matchesLayout(SITE, new ResourceFieldLayout(2, 3, layout.cells(), List.of())));
        var shifted = SurfaceAnchor.at(100, 63, 10);
        assertFalse(blocked.matchesLayout(SITE, new ResourceFieldLayout(1, 3, List.of(
                new ResourceFieldLayout.Cell(FIRST, shifted.support().offset(0, 1, 0), shifted, shifted),
                layout.cells().get(1)), List.of())),
                "same revision, IDs and count cannot adopt another site's physical geometry");
        var changedCellId = blocked.write();
        changedCellId.getList("cells", Tag.TAG_COMPOUND).getCompound(0).putLong("id", 99);
        assertFalse(FrontierV3ResourceFieldWitness.read(changedCellId).matchesLayout(SITE, layout),
                "a recovered claim cannot adopt matching geometry metadata with different cell identities");
    }

    @Test void corruptFormatDuplicateCellAndMissingProjectionCursorFailBeforeUse() {
        var original = FrontierV3ResourceFieldWitness.claimed(SITE, 1, ResourceFieldPhysicalSurface.restore(
                layout(), Map.of(FIRST, DIRT, SECOND, PLANTED)));
        var old = original.write(); old.putInt("format", 0);
        assertThrows(IllegalStateException.class, () -> FrontierV3ResourceFieldWitness.read(old));
        var missingEpoch = original.write(); missingEpoch.remove("epoch");
        assertThrows(IllegalStateException.class, () -> FrontierV3ResourceFieldWitness.read(missingEpoch));
        var oldEpochlessFormat = original.write(); oldEpochlessFormat.putInt("format", 5);
        assertThrows(IllegalStateException.class, () -> FrontierV3ResourceFieldWitness.read(oldEpochlessFormat));
        var untypedOriginFormat = original.write(); untypedOriginFormat.putInt("format", 6);
        assertThrows(IllegalStateException.class, () -> FrontierV3ResourceFieldWitness.read(untypedOriginFormat));
        var previousFormat = original.write(); previousFormat.putInt("format", 3);
        assertThrows(IllegalStateException.class, () -> FrontierV3ResourceFieldWitness.read(previousFormat));
        var preAcknowledgementFormat = original.write(); preAcknowledgementFormat.putInt("format", 4);
        assertThrows(IllegalStateException.class, () -> FrontierV3ResourceFieldWitness.read(preAcknowledgementFormat),
                "a format that forgot physically complete non-harvest causes cannot be adopted");
        var missingSite = original.write(); missingSite.remove("site");
        assertThrows(IllegalStateException.class, () -> FrontierV3ResourceFieldWitness.read(missingSite));
        var noGeometry = original.write(); noGeometry.remove("layoutFingerprint");
        assertThrows(IllegalStateException.class, () -> FrontierV3ResourceFieldWitness.read(noGeometry));
        var duplicate = original.write();
        var cells = duplicate.getList("cells", Tag.TAG_COMPOUND);
        cells.add(cells.get(0).copy());
        assertThrows(IllegalStateException.class, () -> FrontierV3ResourceFieldWitness.read(duplicate));
        var pending = original.begin(ResourceFieldCellTransition.between(SITE, 1, 1, FIRST, DIRT, PLANTED), "farmer:repair:1").write();
        pending.getList("cells", Tag.TAG_COMPOUND).getCompound(0).getCompound("pending").remove("completed");
        assertThrows(IllegalStateException.class, () -> FrontierV3ResourceFieldWitness.read(pending));
        var missingCause = original.begin(ResourceFieldCellTransition.between(SITE, 1, 1, FIRST, DIRT, PLANTED),
                "farmer:repair:1").write();
        missingCause.getList("cells", Tag.TAG_COMPOUND).getCompound(0).getCompound("pending").remove("cause");
        assertThrows(IllegalStateException.class, () -> FrontierV3ResourceFieldWitness.read(missingCause));
        var missingHandFlag = original.begin(ResourceFieldCellTransition.between(SITE, 1, 1, FIRST, DIRT, PLANTED),
                "farmer:repair:1").write();
        missingHandFlag.getList("cells", Tag.TAG_COMPOUND).getCompound(0).getCompound("pending")
                .remove("handConfirmed");
        assertThrows(IllegalStateException.class, () -> FrontierV3ResourceFieldWitness.read(missingHandFlag));
        var missingOrigin = original.begin(ResourceFieldCellTransition.between(SITE, 1, 1, FIRST, DIRT, PLANTED),
                "farmer:repair:1").write();
        missingOrigin.getList("cells", Tag.TAG_COMPOUND).getCompound(0).getCompound("pending").remove("origin");
        assertThrows(IllegalStateException.class, () -> FrontierV3ResourceFieldWitness.read(missingOrigin));
        var wrongPendingType = original.write();
        wrongPendingType.getList("cells", Tag.TAG_COMPOUND).getCompound(0).putString("pending", "not-a-cursor");
        assertThrows(IllegalStateException.class, () -> FrontierV3ResourceFieldWitness.read(wrongPendingType));
        var wrongForeignType = original.write();
        wrongForeignType.getList("cells", Tag.TAG_COMPOUND).getCompound(0).putString("foreign", "not-an-incident");
        assertThrows(IllegalStateException.class, () -> FrontierV3ResourceFieldWitness.read(wrongForeignType));
    }

    @Test void actualHarvestRetainsAndRecoversTheBareCropBetweenCutAndReplant() {
        var original = FrontierV3ResourceFieldWitness.claimed(SITE, 1, ResourceFieldPhysicalSurface.restore(
                layout(), Map.of(FIRST, RIPE, SECOND, PLANTED)));
        var effect = ResourceFieldCellTransition.harvestAndReplant(SITE, 1, 1, FIRST, RIPE);
        var pending = FrontierV3ResourceFieldWitness.read(original.begin(effect, "farmer:harvest:1").write());
        assertTrue(pending.cell(FIRST).pending().orElseThrow().transition().isHarvestAndReplant());
        assertThrows(IllegalArgumentException.class, () -> pending.confirm(FIRST, review(pending, layout(), FIRST, BARE)),
                "a predicted AIR prefix is not a physical observation");
        var wrongMode = pending.write();
        wrongMode.getList("cells", Tag.TAG_COMPOUND).getCompound(0).getCompound("pending")
                .putString("mode", "DIRECT");
        assertThrows(IllegalStateException.class, () -> FrontierV3ResourceFieldWitness.read(wrongMode));
    }

    @Test void harvestCannotCloseOnCropAloneOrOnSyntheticWorkerHand() {
        var layout = layout();
        var actor = new SubjectId("resident:1-1");
        var body = UUID.fromString("00000000-0000-0000-0000-000000000125");
        var hand = new FrontierV3ResourceFieldWitness.HandEffect(SITE,
                new SubjectId("job:site-harvest-1"), actor, body, 3, 36);
        var transition = ResourceFieldCellTransition.harvestAndReplant(SITE, 1, 1, FIRST, RIPE);
        var original = FrontierV3ResourceFieldWitness.claimed(SITE, 1, ResourceFieldPhysicalSurface.restore(
                layout, Map.of(FIRST, RIPE, SECOND, PLANTED)));
        assertThrows(IllegalArgumentException.class, () -> original.beginHarvest(transition, "farmer:paired:1", hand,
                review(original, layout, FIRST, RIPE),
                FrontierV3ActorHandObservation.classify(actor, body, "minecraft:wheat", 36)),
                "predicted predecessor values cannot start an irreversible paired physical effect");
        var pendingTag = original.begin(transition, "farmer:paired:1").write();
        var handTag = new CompoundTag();
        handTag.putString("site", hand.siteId().value());
        handTag.putString("job", hand.jobId().value());
        handTag.putString("actor", hand.actorId().value());
        handTag.putUUID("entity", hand.entityId());
        handTag.putLong("authorityEpoch", hand.authorityEpoch());
        handTag.putInt("before", hand.beforeCount());
        handTag.putInt("after", hand.afterCount());
        pendingTag.getList("cells", Tag.TAG_COMPOUND).getCompound(0).getCompound("pending").put("hand", handTag);
        var recovered = FrontierV3ResourceFieldWitness.read(pendingTag);
        assertEquals(hand, recovered.cell(FIRST).pending().orElseThrow().handEffect().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> recovered.confirmHand(FIRST,
                FrontierV3ActorHandObservation.classify(actor, body, "minecraft:wheat", 37)),
                "a caller-supplied wheat count cannot substitute for real world observation");
        assertThrows(IllegalArgumentException.class, () -> recovered.confirmHand(FIRST,
                FrontierV3ActorHandObservation.classify(actor, body, "minecraft:wheat", 36)),
                "even a real hand observation must not close before both crop writes");
        assertThrows(IllegalArgumentException.class, () -> recovered.confirm(FIRST, review(recovered, layout, FIRST, PLANTED)),
                "the correct synthetic crop terminal is not a physical receipt either");

        var corruptSuccessor = recovered.write();
        corruptSuccessor.getList("cells", Tag.TAG_COMPOUND).getCompound(0).getCompound("pending")
                .getCompound("hand").putInt("after", 38);
        assertThrows(IllegalStateException.class, () -> FrontierV3ResourceFieldWitness.read(corruptSuccessor));
        var missingOwner = recovered.write();
        missingOwner.getList("cells", Tag.TAG_COMPOUND).getCompound(0).getCompound("pending")
                .getCompound("hand").remove("entity");
        assertThrows(IllegalStateException.class, () -> FrontierV3ResourceFieldWitness.read(missingOwner));
        var foreignSite = recovered.write();
        foreignSite.getList("cells", Tag.TAG_COMPOUND).getCompound(0).getCompound("pending")
                .getCompound("hand").putString("site", "site:foreign");
        assertThrows(IllegalStateException.class, () -> FrontierV3ResourceFieldWitness.read(foreignSite));
        assertThrows(IllegalArgumentException.class, () -> new FrontierV3ResourceFieldWitness.HandEffect(
                SITE, hand.jobId(), actor, body, 3, 64), "one offhand cannot hold an unbounded next part");
    }

    @Test void physicallyConfirmedPairRemainsPendingAcrossRecoveryUntilCanonicalReceipt() {
        var layout = layout();
        var hand = new FrontierV3ResourceFieldWitness.HandEffect(SITE, new SubjectId("job:site-harvest-1"),
                new SubjectId("resident:1-1"), UUID.fromString("00000000-0000-0000-0000-000000000125"), 3, 36);
        var transition = ResourceFieldCellTransition.harvestAndReplant(SITE, 1, 1, FIRST, RIPE);
        var claimed = FrontierV3ResourceFieldWitness.claimed(SITE, 1, ResourceFieldPhysicalSurface.restore(
                layout, Map.of(FIRST, RIPE, SECOND, PLANTED)));
        var physicallyReady = claimed.begin(transition, "farmer:paired:1").write();
        var effect = physicallyReady.getList("cells", Tag.TAG_COMPOUND).getCompound(0).getCompound("pending");
        effect.putInt("completed", transition.steps().size());
        effect.putBoolean("handConfirmed", true);
        var handTag = new CompoundTag();
        handTag.putString("site", hand.siteId().value());
        handTag.putString("job", hand.jobId().value());
        handTag.putString("actor", hand.actorId().value());
        handTag.putUUID("entity", hand.entityId());
        handTag.putLong("authorityEpoch", hand.authorityEpoch());
        handTag.putInt("before", hand.beforeCount());
        handTag.putInt("after", hand.afterCount());
        effect.put("hand", handTag);
        physicallyReady.getList("cells", Tag.TAG_COMPOUND).getCompound(0)
                .put("committed", effect.getCompound("after").copy());
        var recovered = FrontierV3ResourceFieldWitness.read(physicallyReady);
        var retained = recovered.cell(FIRST).pending().orElseThrow();
        assertTrue(retained.handConfirmed());
        assertEquals(hand, retained.handEffect().orElseThrow());
        assertEquals("farmer:paired:1", retained.causationId());
        assertTrue(FrontierV3ResourceFieldWitness.read(recovered.write()).cell(FIRST)
                .pending().orElseThrow().handConfirmed());
        assertThrows(IllegalArgumentException.class, () -> recovered.begin(transition, "farmer:replay"),
                "a physically completed harvest cannot be replayed before canonical accounting");

        var receipt = new io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestProgressed(SITE, 1,
                hand.jobId(), 37, 1, FIRST, 0, ResourceFieldCycle.WorkOutcome.HARVESTED,
                new io.farfrontier.palemirror.frontier.v3.api.ScheduleId("schedule:accepted-cell"), 20,
                java.util.Optional.of(new io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestProgressed.HandObservation(
                        new io.farfrontier.palemirror.frontier.v3.model.PhysicalStackAddress.ActorHand(hand.actorId(), hand.entityId()),
                        hand.authorityEpoch(), hand.afterCount())));
        var acceptance = new io.farfrontier.palemirror.frontier.v3.model.ResourceSiteHarvestWorkAcceptance(receipt, transition);
        assertThrows(IllegalArgumentException.class, () -> recovered.retireWork(acceptance), "foreign cause must fail before retirement");
        var exactTag = recovered.write();
        exactTag.getList("cells", Tag.TAG_COMPOUND).getCompound(0).getCompound("pending").putString("cause", acceptance.causationId());
        var exact = FrontierV3ResourceFieldWitness.read(exactTag);
        var retired = exact.retireWork(acceptance);
        assertTrue(retired.cell(FIRST).pending().isEmpty());
        assertEquals(acceptance, retired.cell(FIRST).retiredWork().orElseThrow());
        var restarted = FrontierV3ResourceFieldWitness.read(retired.write());
        assertEquals(restarted, restarted.retireWork(acceptance), "crash between seal and canonical ACK is idempotent");
        var absent = FrontierV3ResourceFieldWitness.claimed(SITE, 1, ResourceFieldPhysicalSurface.restore(
                layout, Map.of(FIRST, PLANTED, SECOND, PLANTED)));
        assertThrows(IllegalArgumentException.class, () -> absent.retireWork(acceptance), "absence alone is never a retirement receipt");
        var unconfirmedTag = exact.write();
        unconfirmedTag.getList("cells", Tag.TAG_COMPOUND).getCompound(0).getCompound("pending").putBoolean("handConfirmed", false);
        var unconfirmed = FrontierV3ResourceFieldWitness.read(unconfirmedTag);
        assertThrows(IllegalArgumentException.class, () -> unconfirmed.retireWork(acceptance));

        var noHand = recovered.write();
        noHand.getList("cells", Tag.TAG_COMPOUND).getCompound(0).getCompound("pending").remove("hand");
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ResourceFieldWitness.read(noHand));
        var unfinishedCrop = recovered.write();
        unfinishedCrop.getList("cells", Tag.TAG_COMPOUND).getCompound(0).getCompound("pending")
                .putInt("completed", 1);
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ResourceFieldWitness.read(unfinishedCrop));
    }

    @Test void physicallyCompletePlantingStillRetainsItsCauseAcrossRecovery() {
        var layout = layout();
        var transition = ResourceFieldCellTransition.between(SITE, 1, 1, FIRST, DIRT, PLANTED);
        var tag = FrontierV3ResourceFieldWitness.claimed(SITE, 1, ResourceFieldPhysicalSurface.restore(
                layout, Map.of(FIRST, DIRT, SECOND, PLANTED))).begin(transition, "farmer:repair:1").write();
        var first = tag.getList("cells", Tag.TAG_COMPOUND).getCompound(0);
        first.getCompound("pending").putInt("completed", transition.steps().size());
        first.put("committed", first.getCompound("pending").getCompound("after").copy());
        var recovered = FrontierV3ResourceFieldWitness.read(tag);
        assertEquals(PLANTED, recovered.cell(FIRST).committed());
        assertEquals("farmer:repair:1", recovered.cell(FIRST).pending().orElseThrow().causationId());
        assertEquals(2, recovered.cell(FIRST).pending().orElseThrow().completedSteps());
        assertTrue(FrontierV3ResourceFieldWitness.read(recovered.write()).cell(FIRST).pending().isPresent());
        assertThrows(IllegalArgumentException.class, () -> recovered.begin(transition, "farmer:repair:replay"));
    }

    @Test void readOnlyCellComparisonDoesNotAdoptDriftOrUncommittedEffects() {
        var layout = layout();
        var witness = FrontierV3ResourceFieldWitness.claimed(SITE, 1, ResourceFieldPhysicalSurface.restore(
                layout, Map.of(FIRST, RIPE, SECOND, PLANTED)));
        var pending = witness.begin(ResourceFieldCellTransition.harvestAndReplant(SITE, 1, 1, FIRST, RIPE), "farmer:harvest:1");
        var unloaded = FrontierV3ResourceFieldObservation.compare(pending, layout, FIRST,
                FrontierV3ResourceFieldObservation.Unloaded.INSTANCE);
        assertEquals(FrontierV3ResourceFieldObservation.Disposition.UNLOADED, unloaded.disposition());
        assertThrows(IllegalArgumentException.class, () -> pending.confirm(FIRST, unloaded));
        assertEquals(FrontierV3ResourceFieldObservation.Disposition.CURRENT,
                FrontierV3ResourceFieldObservation.compare(pending, layout, FIRST,
                        new FrontierV3ResourceFieldObservation.Owned(RIPE)).disposition());
        var nextStep = review(pending, layout, FIRST, BARE);
        assertEquals(FrontierV3ResourceFieldObservation.Disposition.NEXT_STEP_APPLIED, nextStep.disposition());
        var bothPending = pending.begin(ResourceFieldCellTransition.between(SITE, 1, 1, SECOND, PLANTED, BARE),
                "farmer:clear:2");
        assertThrows(IllegalArgumentException.class, () -> bothPending.confirm(SECOND, nextStep),
                "a different cell cannot consume another cell's observation");
        assertThrows(IllegalArgumentException.class, () -> pending.confirm(FIRST,
                FrontierV3ResourceFieldObservation.compare(pending, layout, FIRST,
                        new FrontierV3ResourceFieldObservation.Foreign(new FrontierV3ResourceFieldWitness.ForeignIncident(
                                block("minecraft:stone"), block("minecraft:air"), "player:replace")))));
        assertEquals(FrontierV3ResourceFieldObservation.Disposition.OWNED_DRIFT,
                FrontierV3ResourceFieldObservation.compare(witness, layout, FIRST,
                        new FrontierV3ResourceFieldObservation.Owned(BARE)).disposition());
        assertEquals(FrontierV3ResourceFieldObservation.Disposition.FOREIGN,
                FrontierV3ResourceFieldObservation.compare(witness, layout, FIRST,
                        new FrontierV3ResourceFieldObservation.Foreign(new FrontierV3ResourceFieldWitness.ForeignIncident(
                                block("minecraft:stone"), block("minecraft:air"), "player:replace"))).disposition());
        assertEquals(RIPE, witness.cell(FIRST).committed(), "inspection must not confirm or repair a field cell");
    }

    @Test void retiredToZeroCellsStillRetainsTheExactFieldLayoutIdentity() {
        var empty = new ResourceFieldLayout(2, 3, List.of(), List.of());
        var witness = FrontierV3ResourceFieldWitness.read(FrontierV3ResourceFieldWitness.claimed(
                SITE, 1, ResourceFieldPhysicalSurface.restore(empty, Map.of())).write());
        assertTrue(witness.matchesLayout(SITE, empty));
        assertFalse(witness.matchesLayout(SITE, new ResourceFieldLayout(2, 3, List.of(),
                List.of(new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(1, 63, 1)))));
        assertThrows(IllegalArgumentException.class, () -> witness.cell(FIRST));
    }

    @Test void recoveredWitnessSelectsOnlyNaturallyLoadedChunkCellsFromCanonicalLayout() {
        var east = SurfaceAnchor.at(31, 63, 0);
        var west = SurfaceAnchor.at(-1, 63, 0);
        var north = SurfaceAnchor.at(31, 63, -1);
        ResourceFieldLayout layout = new ResourceFieldLayout(3, 4, List.of(
                new ResourceFieldLayout.Cell(FIRST, east.support().offset(0, 1, 0), east, east),
                new ResourceFieldLayout.Cell(SECOND, west.support().offset(0, 1, 0), west, west),
                new ResourceFieldLayout.Cell(new ResourceFieldLayout.CellId(3),
                        north.support().offset(0, 1, 0), north, north)), List.of());
        FrontierV3ResourceFieldWitness recovered = FrontierV3ResourceFieldWitness.read(
                FrontierV3ResourceFieldWitness.claimed(SITE, 1, ResourceFieldPhysicalSurface.restore(layout,
                        Map.of(FIRST, RIPE, SECOND, DIRT, new ResourceFieldLayout.CellId(3), PLANTED))).write());
        var cycle = ResourceFieldCycle.seeded(SITE, layout, 1);
        var eastCells = recovered.cellsIn(cycle, new ResourceFieldLayout.ChunkColumn(1, 0));
        assertEquals(List.of(FIRST), eastCells.stream().map(cell -> cell.geometry().id()).toList());
        assertEquals(RIPE, eastCells.getFirst().claim().committed());
        assertEquals(List.of(SECOND), recovered.cellsIn(cycle, new ResourceFieldLayout.ChunkColumn(-1, 0))
                .stream().map(cell -> cell.geometry().id()).toList());
        assertEquals(1, recovered.cellsIn(cycle, new ResourceFieldLayout.ChunkColumn(1, -1)).size());
        assertTrue(recovered.cellsIn(cycle, new ResourceFieldLayout.ChunkColumn(0, 0)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> recovered.cellsIn(
                ResourceFieldCycle.seeded(new SubjectId("site:foreign"), layout, 1), new ResourceFieldLayout.ChunkColumn(1, 0)));
        assertThrows(IllegalArgumentException.class, () -> recovered.cellsIn(
                ResourceFieldCycle.seeded(SITE, layout, 2), new ResourceFieldLayout.ChunkColumn(1, 0)));
        assertThrows(IllegalArgumentException.class, () -> recovered.cellsIn(
                ResourceFieldCycle.seeded(SITE, new ResourceFieldLayout(4, 4, layout.cells(), List.of()), 1),
                new ResourceFieldLayout.ChunkColumn(1, 0)));
    }

    private static CompoundTag block(String id) {
        var tag = new CompoundTag(); tag.putString("Name", id); return tag;
    }

    private static FrontierV3ResourceFieldObservation.Review review(FrontierV3ResourceFieldWitness witness,
            ResourceFieldLayout layout, ResourceFieldLayout.CellId id, ResourceFieldPhysicalSurface.Condition condition) {
        return FrontierV3ResourceFieldObservation.compare(witness, layout, id,
                new FrontierV3ResourceFieldObservation.Owned(condition));
    }

    private static ResourceFieldLayout layout() {
        var first = SurfaceAnchor.at(10, 63, 10);
        var second = SurfaceAnchor.at(11, 63, 10);
        return new ResourceFieldLayout(1, 3, List.of(
                new ResourceFieldLayout.Cell(FIRST, first.support().offset(0, 1, 0), first, first),
                new ResourceFieldLayout.Cell(SECOND, second.support().offset(0, 1, 0), second, second)), List.of());
    }
}
