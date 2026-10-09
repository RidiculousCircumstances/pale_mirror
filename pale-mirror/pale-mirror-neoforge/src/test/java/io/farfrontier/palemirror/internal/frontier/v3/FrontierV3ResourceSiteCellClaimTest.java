package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPhysicalSurface;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSiteKind;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontierV3ResourceSiteCellClaimTest {
    private static final SubjectId SITE = new SubjectId("site:cell-claim-test");
    private static final PhysicalIntentId INTENT = new PhysicalIntentId("intent:cell-claim-test");

    @Test void laggingBiologyObservationSurvivesBothSidesOfItsSavedDataAcknowledgement() {
        var site = site();
        var id = site.layout().cells().getFirst().id();
        var projected = ResourceFieldCycle.seeded(SITE, site.layout(), 1).advanceGrowth(id);
        var witness = FrontierV3ResourceFieldWitness.claimed(SITE, 1, ResourceFieldPhysicalSurface.fromCycle(projected));
        var fixture = FrontierV3ResourceSiteLedger.fixture();
        fixture.reserveFieldInitialization(site, INTENT, projected);
        var encoded = fixture.save(new CompoundTag(), null);
        var row = encoded.getList("fieldClaims", 10).getCompound(0);
        row.putString("kind", "OWNED"); row.putString("status", "ACTIVE");
        row.remove("initial"); row.put("witness", witness.write());
        var ledger = FrontierV3ResourceSiteLedger.load(encoded, null);
        var canonical = projected;
        for (int stage = 0; stage < 4; stage++) canonical = canonical.advanceGrowth(id);
        var actual = canonical.advanceGrowth(id);
        var before = ResourceFieldPhysicalSurface.Condition.of(canonical.cell(id));
        var after = ResourceFieldPhysicalSurface.Condition.of(actual.cell(id));
        var change = new FrontierV3ResourceFieldWorldChangeWitness(
                new io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved(SITE, 1,
                        site.layout().revision(), id, before, after,
                        io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved.Change.CROP_GROWN,
                        io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved.Source.WORLD, "world:lagging-growth"));
        ledger.beginFieldWorldChange(change);
        var recovered = FrontierV3ResourceSiteLedger.load(ledger.save(new CompoundTag(), null), null);
        assertEquals(change, recovered.fieldWorldChange(SITE, id));
        var owner = (FrontierV3ResourceSiteLedger.FieldOwnership) recovered.fieldClaim(SITE);
        var acknowledged = owner.witness().acknowledgeWorldChange(change, actual,
                new FrontierV3ResourceFieldObservation.Owned(after));
        recovered.replaceFieldClaim(owner, owner.withWitness(acknowledged));
        var afterAcknowledgement = FrontierV3ResourceSiteLedger.load(recovered.save(new CompoundTag(), null), null);
        assertEquals(change, afterAcknowledgement.fieldWorldChange(SITE, id));
        afterAcknowledgement.retireFieldWorldChange(change);
        assertEquals(after, ((FrontierV3ResourceSiteLedger.FieldOwnership) afterAcknowledgement.fieldClaim(SITE))
                .witness().cell(id).committed());
        ledger.retireFieldWorldChange(change);
        var loss = new FrontierV3ResourceFieldWorldChangeWitness(
                new io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved(SITE, 1,
                        site.layout().revision(), id, before,
                        new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.FARMLAND, ResourceFieldCycle.Crop.ABSENT, 0),
                        io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved.Change.CROP_REMOVED,
                        io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved.Source.WORLD, "world:not-lagging-growth"));
        assertThrows(IllegalStateException.class, () -> ledger.beginFieldWorldChange(loss));
    }

    @Test void initialProjectionRetainsTheActualColdSurfaceInsteadOfReplayingAgeZero() {
        var site = site();
        var cycle = ResourceFieldCycle.seeded(SITE, site.layout(), 4);
        var id = site.layout().cells().getFirst().id();
        for (int stage = 0; stage < 7; stage++) cycle = cycle.advanceGrowth(id);
        var ledger = FrontierV3ResourceSiteLedger.fixture();
        ledger.reserveFieldInitialization(site, INTENT, cycle);
        var restored = FrontierV3ResourceSiteLedger.load(ledger.save(new CompoundTag(), null), null);
        var initial = (FrontierV3ResourceSiteLedger.FieldInitialization) restored.fieldClaim(SITE);
        assertEquals(4, initial.cursor().epoch());
        assertEquals(7, initial.cursor().target().cell(id).committed().growthStage());
        assertEquals(0, initial.cursor().nextWrite());
        assertTrue(initial.cursor().target().matchesCycle(cycle));
        var incompatible = restored.save(new CompoundTag(), null);
        incompatible.putInt("format", 14);
        assertThrows(IllegalStateException.class, () -> FrontierV3ResourceSiteLedger.load(incompatible, null));
    }

    @Test void currentSavedDataRetainsOnlyTheCellOwnerAndRejectsLegacyMutation() {
        var firstCell = layout().cells().getFirst();
        var support = firstCell.soil().support().offset(1, 0, 0);
        var secondSoil = SurfaceAnchor.at(support.x(), support.y(), support.z());
        var twoCells = new ResourceFieldLayout(1, 3, List.of(firstCell,
                new ResourceFieldLayout.Cell(new ResourceFieldLayout.CellId(2),
                        support.offset(0, 1, 0), secondSoil, secondSoil)), List.of());
        var site = new ResourceSite(SITE, site().settlementId(), site().facilityId(), ResourceSiteKind.WHEAT_FIELD, twoCells);
        var cycle = ResourceFieldCycle.seeded(SITE, site.layout(), 1);
        var witness = FrontierV3ResourceFieldWitness.claimed(SITE, 1, ResourceFieldPhysicalSurface.fromCycle(cycle));
        var ledger = FrontierV3ResourceSiteLedger.fixture();
        ledger.reserveFieldInitialization(site, INTENT);
        var pending = (FrontierV3ResourceSiteLedger.FieldInitialization) ledger.fieldClaim(SITE);
        assertEquals(0, pending.cursor().nextWrite());
        var midRestart = FrontierV3ResourceSiteLedger.load(ledger.save(new CompoundTag(), null), null);
        assertEquals(pending, midRestart.fieldClaim(SITE));
        int writes = FrontierV3ResourceFieldInitialPlan.writeCount(site);
        assertThrows(IllegalStateException.class, () -> ledger.replaceFieldClaim(pending,
                new FrontierV3ResourceSiteLedger.FieldOwnership(SITE, INTENT,
                        FrontierV3ResourceSiteLedger.Status.ACTIVE, witness)),
                "generic replacement cannot bypass the initial physical-write cursor");
        CompoundTag completedFixture = ledger.save(new CompoundTag(), null);
        completedFixture.getList("fieldClaims", 10).getCompound(0).getCompound("initial").putInt("next", writes);
        var completedLedger = FrontierV3ResourceSiteLedger.load(completedFixture, null);
        assertThrows(IllegalStateException.class, () -> completedLedger.replaceFieldClaim(completedLedger.fieldClaim(SITE),
                new FrontierV3ResourceSiteLedger.FieldOwnership(SITE, INTENT,
                        FrontierV3ResourceSiteLedger.Status.ACTIVE, witness)),
                "even a completed cursor cannot bypass the live-world activation check");
        // This serialized active fixture tests the SavedData owner's mutation rules only;
        // native GameTests exercise the required real-world activation boundary.
        CompoundTag activeFixture = completedLedger.save(new CompoundTag(), null);
        CompoundTag activeRow = activeFixture.getList("fieldClaims", 10).getCompound(0);
        activeRow.putString("kind", "OWNED");
        activeRow.putString("status", "ACTIVE");
        activeRow.remove("initial");
        activeRow.put("witness", witness.write());
        CompoundTag oldFormat = activeFixture.copy();
        oldFormat.putInt("format", 10);
        assertThrows(IllegalStateException.class, () -> FrontierV3ResourceSiteLedger.load(oldFormat, null),
                "a pre-hand-witness field cannot be silently recovered under the new physical writer");
        var activeLedger = FrontierV3ResourceSiteLedger.load(activeFixture, null);
        var first = cycle.layout().cells().getFirst();
        var playerPrepared = new io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPlayerBreakPrepared(
                SITE, cycle.epoch(), cycle.layout().revision(), first.id(),
                ResourceFieldPhysicalSurface.Condition.of(cycle.cell(first.id())),
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000128"), "action:cell-claim-player-break");
        var playerFence = FrontierV3ResourceFieldPlayerBreakWitness.prepared(playerPrepared);
        activeLedger.beginFieldPlayerBreak(playerFence);
        var recoveredPlayer = FrontierV3ResourceSiteLedger.load(activeLedger.save(new CompoundTag(), null), null);
        assertEquals(playerFence, recoveredPlayer.fieldPlayerBreak(SITE, first.id()));
        assertThrows(IllegalStateException.class, () -> recoveredPlayer.beginFieldPlayerBreak(
                FrontierV3ResourceFieldPlayerBreakWitness.prepared(new io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPlayerBreakPrepared(
                        SITE, cycle.epoch(), cycle.layout().revision(), first.id(), playerPrepared.before(),
                        playerPrepared.playerId(), "action:foreign-player-break"))));
        var playerObserved = playerFence.observed(io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved.Change.CROP_REMOVED);
        recoveredPlayer.observeFieldPlayerBreak(playerFence, playerObserved);
        assertEquals(playerObserved, FrontierV3ResourceSiteLedger.load(recoveredPlayer.save(new CompoundTag(), null), null)
                .fieldPlayerBreak(SITE, first.id()));
        recoveredPlayer.retireFieldPlayerBreak(playerObserved);
        activeLedger.observeFieldPlayerBreak(playerFence, playerObserved);
        activeLedger.retireFieldPlayerBreak(playerObserved);
        var worldBefore = ResourceFieldPhysicalSurface.Condition.of(cycle.cell(first.id()));
        var worldAfter = new ResourceFieldPhysicalSurface.Condition(ResourceFieldCycle.Soil.FARMLAND,
                ResourceFieldCycle.Crop.ABSENT, 0);
        var worldChange = new FrontierV3ResourceFieldWorldChangeWitness(
                new io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved(SITE,
                        cycle.epoch(), cycle.layout().revision(), first.id(), worldBefore, worldAfter,
                        io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved.Change.CROP_REMOVED,
                        io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved.Source.WORLD,
                        "world:cell-claim-test"));
        activeLedger.beginFieldWorldChange(worldChange);
        var restoredWorldChange = FrontierV3ResourceSiteLedger.load(activeLedger.save(new CompoundTag(), null), null);
        assertEquals(worldChange, restoredWorldChange.fieldWorldChange(SITE, first.id()));
        assertThrows(IllegalStateException.class, () -> restoredWorldChange.beginFieldWorldChange(
                new FrontierV3ResourceFieldWorldChangeWitness(
                        new io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved(SITE,
                                cycle.epoch(), cycle.layout().revision(), first.id(), worldBefore, worldAfter,
                                io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved.Change.CROP_REMOVED,
                                io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCellObserved.Source.WORLD,
                                "world:competing-cell-claim-test"))));
        restoredWorldChange.retireFieldWorldChange(worldChange);
        activeLedger.retireFieldWorldChange(worldChange);
        var foreignHold = new io.farfrontier.palemirror.frontier.v3.model.ResourceFieldForeignChangeHeld(
                SITE, cycle.epoch(), cycle.layout().revision(), first.id(), cycle.cell(first.id()),
                "world:cell-claim-foreign-test");
        var foreignBefore = new FrontierV3ResourceFieldForeignChangeWitness(foreignHold, java.util.Optional.empty());
        activeLedger.beginFieldForeignChange(foreignBefore);
        var recoveredForeign = FrontierV3ResourceSiteLedger.load(activeLedger.save(new CompoundTag(), null), null);
        assertEquals(foreignBefore, recoveredForeign.fieldForeignChange(SITE, first.id()));
        var foreignAfter = foreignBefore.observe(new FrontierV3ResourceFieldForeignChangeWitness.Blocks(
                block("minecraft:farmland"), block("minecraft:stone")));
        recoveredForeign.observeFieldForeignChange(foreignBefore, foreignAfter);
        assertEquals(foreignAfter, FrontierV3ResourceSiteLedger.load(
                recoveredForeign.save(new CompoundTag(), null), null).fieldForeignChange(SITE, first.id()));
        assertThrows(IllegalStateException.class, () -> recoveredForeign.beginFieldWorldChange(worldChange),
                "one site cannot retain owned and foreign world causes at once");
        var recaptured = foreignAfter.observe(new FrontierV3ResourceFieldForeignChangeWitness.Blocks(
                block("minecraft:stone"), block("minecraft:air")));
        recoveredForeign.observeFieldForeignChange(foreignAfter, recaptured);
        var afterSupersession = FrontierV3ResourceSiteLedger.load(recoveredForeign.save(new CompoundTag(), null), null);
        assertEquals(2L, afterSupersession.fieldForeignChange(SITE, first.id()).observationVersion());
        assertEquals(recaptured, afterSupersession.fieldForeignChange(SITE, first.id()));
        assertThrows(IllegalStateException.class, () -> afterSupersession.observeFieldForeignChange(foreignAfter, recaptured),
                "a stale capture cannot replace the next durable observation version");
        var neighbor = cycle.layout().cells().get(1);
        var neighborHold = new io.farfrontier.palemirror.frontier.v3.model.ResourceFieldForeignChangeHeld(
                SITE, cycle.epoch(), cycle.layout().revision(), neighbor.id(), cycle.cell(neighbor.id()), "world:neighbor-cell");
        afterSupersession.beginFieldForeignChange(new FrontierV3ResourceFieldForeignChangeWitness(neighborHold, java.util.Optional.empty()));
        assertEquals(2, FrontierV3ResourceSiteLedger.load(afterSupersession.save(new CompoundTag(), null), null)
                .pendingFieldForeignChanges().size(), "distinct cells in one site own independent causes");
        var badFamily = recaptured.write(); badFamily.putInt("mutationFamily", 255);
        assertThrows(IllegalArgumentException.class, () -> FrontierV3ResourceFieldForeignChangeWitness.read(badFamily));
        recoveredForeign.retireFieldForeignChange(recaptured);
        activeLedger.retireFieldForeignChange(foreignBefore);
        var hand = new FrontierV3ResourceSiteHandProjectionWitness(SITE,
                new SubjectId("job:site-harvest-cell-claim-test"), new SubjectId("custody:field-actor-test"),
                new SubjectId("lot:field-part-test"), new SubjectId("resident:cell-claim-test"),
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000091"),
                new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:field-delivery-cell-claim-test"),
                2L, 1L, 7, 7);
        activeLedger.beginFieldHandProjection(hand);
        var restoredHand = FrontierV3ResourceSiteLedger.load(activeLedger.save(new CompoundTag(), null), null);
        assertEquals(hand, restoredHand.fieldHandProjection(SITE));
        assertThrows(IllegalStateException.class, () -> restoredHand.beginFieldHandProjection(
                new FrontierV3ResourceSiteHandProjectionWitness(hand.siteId(), hand.jobId(), hand.actorAccountId(),
                        hand.lotId(), hand.workerId(), hand.entityId(), hand.leaseId(), hand.actorEpoch(),
                        hand.fieldEpoch(), hand.accountedPrefix(), 8)));
        restoredHand.retireFieldHandProjection(hand);
        activeLedger.retireFieldHandProjection(hand);
        var delivery = new FrontierV3ResourceSiteDeliveryWitness(SITE,
                new SubjectId("job:site-harvest-cell-claim-test"), new PhysicalIntentId("intent:site-harvest-cell-claim-test"),
                new SubjectId("resident:cell-claim-test"), java.util.UUID.fromString("00000000-0000-0000-0000-000000000091"),
                new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:field-delivery-cell-claim-test"),
                2L, new SubjectId("container:1-depot"), 1, 7, 3L,
                "sha256:" + "a".repeat(64), "sha256:" + "b".repeat(64), "witness:field-delivery-test", 7L);
        activeLedger.beginFieldHandProjection(hand);
        assertThrows(IllegalStateException.class, () -> activeLedger.beginFieldDelivery(delivery),
                "depot effect cannot overtake an unretired farmer-hand projection");
        activeLedger.retireFieldHandProjection(hand);
        activeLedger.beginFieldDelivery(delivery);
        var restoredDelivery = FrontierV3ResourceSiteLedger.load(activeLedger.save(new CompoundTag(), null), null);
        assertEquals(delivery, restoredDelivery.fieldDelivery(SITE));
        assertTrue(restoredDelivery.hasPendingFieldDelivery(delivery.containerId()));
        assertThrows(IllegalStateException.class, () -> restoredDelivery.beginFieldDelivery(new FrontierV3ResourceSiteDeliveryWitness(
                delivery.siteId(), delivery.jobId(), delivery.intentId(), delivery.workerId(), delivery.entityId(), delivery.leaseId(),
                delivery.leaseRevision(), delivery.containerId(), 2, delivery.quantity(), delivery.depotEpoch(),
                delivery.beforeFingerprint(), delivery.afterFingerprint(), delivery.witnessId(), delivery.resourceEpoch())));
        restoredDelivery.retireFieldDelivery(delivery);
        assertTrue(restoredDelivery.pendingFieldDeliveries().isEmpty());
        activeLedger.retireFieldDelivery(delivery);
        var claim = (FrontierV3ResourceSiteLedger.FieldOwnership) activeLedger.fieldClaim(SITE);
        var conflictedCopy = FrontierV3ResourceSiteLedger.load(activeLedger.save(new CompoundTag(), null), null);
        conflictedCopy.conflict(SITE);
        assertEquals(FrontierV3ResourceSiteLedger.Status.CONFLICT, conflictedCopy.fieldClaim(SITE).status(),
                "an accepted cell-owned incident must stay local instead of throwing at the legacy claim API");
        var successor = claim.withWitness(witness.rebaseColdEpoch(ResourceFieldCycle.seeded(SITE, site.layout(), 3)));
        activeLedger.replaceFieldClaim(claim, successor);
        assertThrows(IllegalStateException.class, () -> activeLedger.replaceFieldClaim(claim, successor),
                "a stale cell owner cannot replace a later epoch");
        var recovered = FrontierV3ResourceSiteLedger.load(activeLedger.save(new CompoundTag(), null), null);
        assertEquals(INTENT, recovered.fieldClaim(SITE).intentId());
        assertTrue(((FrontierV3ResourceSiteLedger.FieldOwnership) recovered.fieldClaim(SITE)).witness()
                .matchesCycle(ResourceFieldCycle.seeded(SITE, site.layout(), 3)));
        var owned = (FrontierV3ResourceSiteLedger.FieldOwnership) recovered.fieldClaim(SITE);
        recovered.replaceFieldClaim(owned, owned.conflicted());
        var conflicted = (FrontierV3ResourceSiteLedger.FieldOwnership) recovered.fieldClaim(SITE);
        assertThrows(IllegalStateException.class, () -> recovered.replaceFieldClaim(recovered.fieldClaim(SITE),
                conflicted.withWitness(conflicted.witness())),
                "a conflicted physical cell witness is frozen until an explicit recovery owner exists");
        assertThrows(IllegalStateException.class, () -> recovered.replaceFieldClaim(recovered.fieldClaim(SITE),
                new FrontierV3ResourceSiteLedger.FieldOwnership(SITE, INTENT,
                        FrontierV3ResourceSiteLedger.Status.ACTIVE, conflicted.witness())));
    }

    @Test void formatAndCompetingSameSiteOwnersFailClosed() {
        var field = FrontierV3ResourceSiteLedger.fixture();
        field.reserveFieldInitialization(site(), INTENT);
        CompoundTag competing = field.save(new CompoundTag(), null);
        competing.getList("fieldClaims",10).add(competing.getList("fieldClaims",10).getFirst().copy());
        assertThrows(IllegalStateException.class, () -> FrontierV3ResourceSiteLedger.load(competing, null));
        CompoundTag old = field.save(new CompoundTag(), null);
        old.putInt("format", 8);
        assertThrows(IllegalStateException.class, () -> FrontierV3ResourceSiteLedger.load(old, null));
        CompoundTag missingPrepared = field.save(new CompoundTag(), null);
        missingPrepared.getList("fieldClaims", 10).getCompound(0).getCompound("initial").remove("target");
        assertThrows(IllegalStateException.class, () -> FrontierV3ResourceSiteLedger.load(missingPrepared, null));
        CompoundTag invalidPrepared = field.save(new CompoundTag(), null);
        invalidPrepared.getList("fieldClaims", 10).getCompound(0).getCompound("initial").putByte("batch", (byte) 2);
        assertThrows(IllegalStateException.class, () -> FrontierV3ResourceSiteLedger.load(invalidPrepared, null));
        for (String section : List.of("fieldClaims", "fieldDeliveries", "fieldHandProjections", "fieldPlayerBreaks",
                "fieldWorldChanges", "fieldForeignChanges")) {
            CompoundTag missing = field.save(new CompoundTag(), null);
            missing.remove(section);
            assertThrows(IllegalStateException.class, () -> FrontierV3ResourceSiteLedger.load(missing, null),
                    "a missing current-schema section cannot silently become empty: " + section);
            CompoundTag mistyped = field.save(new CompoundTag(), null);
            var rows = new net.minecraft.nbt.ListTag();
            rows.add(net.minecraft.nbt.StringTag.valueOf("not-a-claim"));
            mistyped.put(section, rows);
            assertThrows(IllegalStateException.class, () -> FrontierV3ResourceSiteLedger.load(mistyped, null),
                    "a corrupt current-schema section cannot silently become empty: " + section);
        }
    }

    @Test void coldActorHandAndDepotDeliveryDoNotWaitForRemoteFieldBlockProjection() {
        var hand = new FrontierV3ResourceSiteHandProjectionWitness(SITE,
                new SubjectId("job:site-harvest-cell-claim-cold"), new SubjectId("custody:field-actor-cold"),
                new SubjectId("lot:field-part-cold"), new SubjectId("resident:field-cold"),
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000092"),
                new io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId("lease:field-cold"),
                2L, 2L, 64, 64);
        var delivery = new FrontierV3ResourceSiteDeliveryWitness(SITE, hand.jobId(),
                new PhysicalIntentId("intent:site-harvest-cell-claim-cold"), hand.workerId(),
                hand.entityId(), hand.leaseId(), hand.actorEpoch(), new SubjectId("container:1-depot"),
                1, 64, 3L, "sha256:" + "a".repeat(64), "sha256:" + "b".repeat(64),
                "witness:field-cold-delivery", 5L);
        var pendingField = FrontierV3ResourceSiteLedger.fixture();
        pendingField.reserveFieldInitialization(site(), INTENT);
        for (var ledger : List.of(FrontierV3ResourceSiteLedger.fixture(), pendingField)) {
            ledger.beginFieldHandProjection(hand);
            var recoveredHand = FrontierV3ResourceSiteLedger.load(ledger.save(new CompoundTag(), null), null);
            assertEquals(hand, recoveredHand.fieldHandProjection(SITE));
            assertThrows(IllegalStateException.class, () -> recoveredHand.beginFieldDelivery(delivery),
                    "delivery cannot overtake the durable hand write");
            recoveredHand.retireFieldHandProjection(hand);
            recoveredHand.beginFieldDelivery(delivery);
            var recoveredDelivery = FrontierV3ResourceSiteLedger.load(recoveredHand.save(new CompoundTag(), null), null);
            assertEquals(delivery, recoveredDelivery.fieldDelivery(SITE));
            recoveredDelivery.retireFieldDelivery(delivery);
        }

    }

    private static ResourceFieldLayout layout() {
        var soil = SurfaceAnchor.at(12, 63, 34);
        return new ResourceFieldLayout(1, 2, List.of(new ResourceFieldLayout.Cell(
                new ResourceFieldLayout.CellId(1), soil.support().offset(0, 1, 0), soil, soil)), List.of());
    }

    private static ResourceSite site() {
        return new ResourceSite(SITE, new SubjectId("settlement:cell-claim-test"),
                new SubjectId("structure:cell-claim-test"), ResourceSiteKind.WHEAT_FIELD, layout());
    }

    private static CompoundTag block(String name) {
        CompoundTag tag = new CompoundTag(); tag.putString("Name", name); return tag;
    }
}
