package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Native hand/provenance component boundary, not a full field cycle or process-crash claim. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3HarvestHandOperationGameTests {
    @GameTest(batch = "pm-frontier-v3-field-turns", templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 80)
    public static void coldHandUsesOneAttemptAndRetainedCarryCannotReissueMissingStock(GameTestHelper helper) {
        for (var mode : FrontierV3ResourceSiteHandProjectionWitness.Mode.values()) check(helper, mode);
        helper.succeed();
    }

    private static void check(GameTestHelper helper, FrontierV3ResourceSiteHandProjectionWitness.Mode mode) {
        var level = helper.getLevel(); var point = helper.absolutePos(new BlockPos(1, 1, 1));
        var state = FrontierWorldRuntimeDefinition.configuration(new WorldId("frontier:field-hand-" + mode.name().toLowerCase(Locale.ROOT)), 95L).initialState();
        var actor = state.humanPopulation().residents().keySet().stream().sorted().findFirst().orElseThrow();
        var execution = state.actorExecutions().next(actor, ActorActivityKind.PRESENCE, actor);
        state = ActorExecutionComposition.LIFECYCLE.prepareVacant(state, execution).commit(state, FrontierWorldStateUpdate.begin());
        state = ActorBodyAuthority.demand(state, actor); var bodyId = ActorBodyAuthority.current(state, actor);
        state = ActorBodyAuthority.running(state, bodyId);
        var body = EntityType.VILLAGER.create(level);
        body.setUUID(io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.entityId(state.bootstrap().worldId(), actor));
        body.setPos(point.getX() + .5, point.getY(), point.getZ() + .5); body.setNoAi(true);
        FrontierV3ActorCarrierComposition.stamp(body, FrontierV3ActorCarrierComposition.fromCanonical(state, actor,
                ActorKind.RESIDENT, FrontierV3ActorCarrierComposition.Owner.ACTOR_BODY, body.getUUID(),
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 0L, bodyId.physicalEpoch()));
        helper.assertTrue(level.addFreshEntity(body), "exact native hand participant admitted");
        try {
            var account = new SubjectId("custody:field-actor-hand-test");
            var lot = new ResourceLot(new SubjectId("lot:field-hand-test"), state.humanPopulation().resident(actor).settlementId(),
                    "minecraft:wheat", 7, "fixture:field-hand", List.of());
            var resources = state.inventory().fungibleResources().issue(lot, new CustodyAccount(account,
                    new ResourceCustody.Actor(actor), Map.of(lot.id(), 7), Map.of(),
                    Optional.of(new ActorItemSlot.Hand(ActorContainerItemOrder.Hand.OFF))));
            state = state.withInventory(state.inventory().withFungibleResources(resources));
            var witness = new FrontierV3ResourceSiteHandProjectionWitness(new SubjectId("site:hand-test"),
                    new SubjectId("job:site-harvest-hand-test"), account, lot.id(), actor, body.getUUID(),
                    new SceneLeaseId("lease:hand-test"), 1, 1, 7, 7, mode);
            witness = FrontierV3ResourceSiteHandProjectionWitness.read(witness.write());
            if (mode == FrontierV3ResourceSiteHandProjectionWitness.Mode.BIND_RETAINED) {
                body.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.WHEAT, 7));
                FrontierV3ActorCarryProjection.rememberConfirmed(state, actor, body);
            }
            var identity = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId(bodyId,
                    state.actorExecutions().next(actor, ActorActivityKind.FIELD_HARVEST, witness.jobId()));
            helper.assertTrue(FrontierV3HarvestHandAftermath.capture(level, state, witness, body, identity).isEmpty(),
                    "native item counts alone cannot prove a projection for death aftermath");
            var result = FrontierV3HarvestHandOperation.apply(level, state, witness, body);
            helper.assertTrue(result.complete(), "declared projection reaches its exact physical hand");
            helper.assertValueEqual(body.getOffhandItem().getCount(), 7, "only the accounted quantity is presented");
            var repeated = FrontierV3HarvestHandOperation.apply(level, state, witness, body);
            helper.assertValueEqual(repeated.status(), FrontierV3MinecraftOperation.Status.OBSERVED, "same intent observes, never reissues");
            helper.assertValueEqual(result.ticket(), repeated.ticket(), "one common operation owns both calls");
            var aftermath = FrontierV3HarvestHandAftermath.capture(level, state, witness, body, identity).orElseThrow();
            helper.assertValueEqual(aftermath.child().ticket(), result.ticket(), "aftermath retains the original projection, not another issue");
            helper.assertValueEqual(FrontierV3OperationAftermath.load(aftermath.save()), aftermath,
                    "the exact body/execution and observed hand survive the pre-loot snapshot codec");
            body.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
            var changed = FrontierV3HarvestHandOperation.apply(level, state, witness, body);
            helper.assertTrue(!changed.complete() && body.getOffhandItem().isEmpty(), "external removal cannot mint a replacement stack");
            helper.assertTrue(FrontierV3HarvestHandAftermath.capture(level, state, witness, body, identity).isEmpty(),
                    "historical applied receipt cannot bind a missing pre-loot hand");
            if (mode == FrontierV3ResourceSiteHandProjectionWitness.Mode.BIND_RETAINED) {
                var other = new FrontierV3ResourceSiteHandProjectionWitness(witness.siteId(), witness.jobId(), account,
                        lot.id(), actor, body.getUUID(), witness.leaseId(), 2, 1, 7, 7, mode);
                helper.assertTrue(!FrontierV3HarvestHandOperation.apply(level, state, other, body).complete()
                        && body.getOffhandItem().isEmpty(), "a fresh retained-binding request still cannot issue missing cargo");
            }
        } finally { body.discard(); }
    }
}
