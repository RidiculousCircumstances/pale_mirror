package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReferenceGrayboxCargoObservationTest {
    @Test
    void exactItemDepositsAndWithdrawalsEnterOnlyTheNamedOperationCargo() {
        ReferenceWorld world = grayboxWorld();
        ReferenceOperation operation = operation(world, 991, 8.0d);
        ReferenceGrayboxSnapshot before = ReferenceGrayboxProjection.from(world);
        String cargoId = "operation:991:cargo:food";

        ReferenceGrayboxObservationOutcome deposit = world.observe(new ReferenceGrayboxCargoObservation(
                ReferenceGrayboxCargoObservation.VERSION, "cargo-test:deposit", before.stateRevision(), cargoId,
                ReferenceResource.FOOD, 3));

        assertTrue(deposit.applied(), "a current player deposit must enter the exact source operation cargo");
        assertEquals(8.0d + 3.0d / 64.0d, operation.cargo().get(ReferenceResource.FOOD), 1.0e-9d);
        assertEquals(2.0d, operation.cargo().get(ReferenceResource.MEDICINE), 1.0e-9d,
                "one container movement may not alter a different resource in the same operation");

        ReferenceGrayboxObservationOutcome withdrawal = world.observe(new ReferenceGrayboxCargoObservation(
                ReferenceGrayboxCargoObservation.VERSION, "cargo-test:withdrawal", deposit.stateRevision(), cargoId,
                ReferenceResource.FOOD, -2));

        assertTrue(withdrawal.applied(), "a backed withdrawal must remain inside the exact source cargo owner");
        assertEquals(8.0d + 1.0d / 64.0d, operation.cargo().get(ReferenceResource.FOOD), 1.0e-9d);
        assertEquals(ReferenceGrayboxObservationOutcome.Status.REJECTED_STALE,
                world.observe(new ReferenceGrayboxCargoObservation(ReferenceGrayboxCargoObservation.VERSION, "cargo-test:stale",
                        before.stateRevision(), cargoId, ReferenceResource.FOOD, 1)).status(),
                "an old container view must not rebase an item movement onto a new operation state");
        world.assertProfileInvariants();
    }

    @Test
    void wrongResourceOrExcessWithdrawalDoesNotInventCargo() {
        ReferenceWorld world = grayboxWorld();
        ReferenceOperation operation = operation(world, 992, 1.0d);
        ReferenceGrayboxSnapshot snapshot = ReferenceGrayboxProjection.from(world);
        String cargoId = "operation:992:cargo:food";

        assertEquals(ReferenceGrayboxObservationOutcome.Status.REJECTED_CONFLICT,
                world.observe(new ReferenceGrayboxCargoObservation(ReferenceGrayboxCargoObservation.VERSION, "cargo-test:wrong-resource",
                        snapshot.stateRevision(), cargoId, ReferenceResource.ORE, 1)).status(),
                "a barrel tagged for food may not become an arbitrary resource injection");
        assertEquals(ReferenceGrayboxObservationOutcome.Status.REJECTED_CONFLICT,
                world.observe(new ReferenceGrayboxCargoObservation(ReferenceGrayboxCargoObservation.VERSION, "cargo-test:too-much",
                        snapshot.stateRevision(), cargoId, ReferenceResource.FOOD, -65)).status(),
                "a physical withdrawal must not push canonical operation cargo below zero");
        assertEquals(1.0d, operation.cargo().get(ReferenceResource.FOOD), 1.0e-9d);
    }

    @Test
    void cargoPalletPositionDoesNotShiftWhenAnotherResourceIsExhausted() {
        ReferenceWorld world = grayboxWorld();
        ReferenceOperation operation = operation(world, 993, 1.0d);
        ReferenceGrayboxSnapshot before = ReferenceGrayboxProjection.from(world);
        ReferenceGrayboxLayout.Rectangle medicinePosition = cargo(before, "operation:993:cargo:medicine").rectangle();

        operation.cargo(Map.of(ReferenceResource.MEDICINE, 2.0d));
        ReferenceGrayboxSnapshot after = ReferenceGrayboxProjection.from(world);

        assertEquals(medicinePosition, cargo(after, "operation:993:cargo:medicine").rectangle(),
                "the retained medicine barrel must not move when food is consumed");
    }

    @Test
    void fieldPostPalletPositionDoesNotShiftWhenAnotherResourceIsExhausted() {
        ReferenceWorld world = grayboxWorld();
        ReferenceSettlement settlement = world.settlements().get(1);
        ReferenceFieldCampaign campaign = world.field().createCampaign(world, ReferenceCampaignKind.CONTAINMENT, settlement.id(), Set.of(),
                "cell", null, 10, 10, "cargo position");
        assertTrue(campaign != null);
        ReferenceFieldPost post = startPostWithFood(world, campaign, settlement, 1.0d);
        post.stock(ReferenceResource.MEDICINE, 1.0d);
        ReferenceGrayboxSnapshot before = ReferenceGrayboxProjection.from(world);
        String medicineId = "field_post:" + post.id() + ":cargo:medicine";
        ReferenceGrayboxLayout.Rectangle medicinePosition = cargo(before, medicineId).rectangle();

        post.stock(ReferenceResource.FOOD, 0.0d);
        ReferenceGrayboxSnapshot after = ReferenceGrayboxProjection.from(world);

        assertEquals(medicinePosition, cargo(after, medicineId).rectangle(),
                "the retained field-post medicine barrel must not move when food is consumed");
    }

    @Test
    void exactItemDepositEntersOnlyTheNamedFieldPostCargo() {
        ReferenceWorld world = grayboxWorld();
        ReferenceSettlement settlement = world.settlements().get(1);
        ReferenceFieldCampaign campaign = world.field().createCampaign(world, ReferenceCampaignKind.CONTAINMENT, settlement.id(), Set.of(),
                "cell", null, 10, 10, "cargo receipt");
        assertTrue(campaign != null);
        ReferenceFieldPost post = startPostWithFood(world, campaign, settlement, 1.0d);
        double foodBefore = post.stock(ReferenceResource.FOOD);
        ReferenceGrayboxSnapshot before = ReferenceGrayboxProjection.from(world);
        String cargoId = "field_post:" + post.id() + ":cargo:food";

        ReferenceGrayboxObservationOutcome outcome = world.observe(new ReferenceGrayboxCargoObservation(
                ReferenceGrayboxCargoObservation.VERSION, "cargo-test:field-post-deposit", before.stateRevision(), cargoId,
                ReferenceResource.FOOD, 5));

        assertEquals(ReferenceGrayboxObservationOutcome.Status.APPLIED, outcome.status(),
                "a field-post barrel deposit must enter its exact source stock: " + outcome.reason());
        assertEquals(foodBefore + 5.0d / 64.0d, post.stock(ReferenceResource.FOOD), 1.0e-9d);
        world.assertProfileInvariants();
    }

    @Test
    void fieldPostDepositCannotBypassItsCanonicalStorageLimit() {
        ReferenceWorld world = grayboxWorld();
        ReferenceSettlement settlement = world.settlements().get(1);
        ReferenceFieldCampaign campaign = world.field().createCampaign(world, ReferenceCampaignKind.CONTAINMENT, settlement.id(), Set.of(),
                "cell", null, 10, 10, "cargo capacity");
        assertTrue(campaign != null);
        ReferenceFieldPost post = startPostWithFood(world, campaign, settlement, 8.0d);
        double foodBefore = post.stock(ReferenceResource.FOOD);
        ReferenceGrayboxSnapshot before = ReferenceGrayboxProjection.from(world);

        ReferenceGrayboxObservationOutcome outcome = world.observe(new ReferenceGrayboxCargoObservation(
                ReferenceGrayboxCargoObservation.VERSION, "cargo-test:field-post-over-capacity", before.stateRevision(),
                "field_post:" + post.id() + ":cargo:food", ReferenceResource.FOOD, 1));

        assertEquals(ReferenceGrayboxObservationOutcome.Status.REJECTED_CONFLICT, outcome.status(),
                "a real extra item may not bypass the source post's finite storage");
        assertEquals(foodBefore, post.stock(ReferenceResource.FOOD), 1.0e-9d);
    }

    private static ReferenceOperation operation(ReferenceWorld world, int id, double food) {
        ReferenceSettlement settlement = world.settlements().get(1);
        ReferenceOperation operation = new ReferenceOperation(id, ReferenceAgentKind.SETTLEMENT, ReferenceOperationKind.RECON,
                new ReferenceAgentRef(ReferenceAgentKind.SETTLEMENT, settlement.id()), ReferenceTargetRef.cell(settlement.x(), settlement.y()),
                world.day(), settlement.x(), settlement.y(), settlement.x(), settlement.y());
        operation.cargo(Map.of(ReferenceResource.FOOD, food, ReferenceResource.MEDICINE, 2.0d));
        world.operations().mutableActive().add(operation);
        return operation;
    }

    private static ReferenceFieldPost startPostWithFood(ReferenceWorld world, ReferenceFieldCampaign campaign,
                                                          ReferenceSettlement settlement, double initialFood) {
        for (int y = 1; y < world.config().height() - 1; y++) for (int x = 1; x < world.config().width() - 1; x++) {
            ReferenceFieldPost post = world.field().startPost(world, campaign.id(), ReferenceFieldPostKind.CHECKPOINT, x, y,
                    settlement.id(), Set.of(settlement.id()), Map.of(settlement.id(), 1.0d), Map.of(ReferenceResource.FOOD, initialFood), Map.of());
            if (post != null) return post;
        }
        throw new AssertionError("graybox test world has no buildable field-post location");
    }

    private static ReferenceGrayboxSnapshot.Cargo cargo(ReferenceGrayboxSnapshot snapshot, String id) {
        return snapshot.cargoes().stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }

    private static ReferenceWorld grayboxWorld() {
        return new ReferenceWorld(ReferenceWorldConfig.graybox1To40(7L));
    }
}
