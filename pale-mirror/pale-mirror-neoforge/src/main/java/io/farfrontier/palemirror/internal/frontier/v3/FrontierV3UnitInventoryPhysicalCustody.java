package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import java.util.Comparator;

/** Observes an already-present inventory; never spawns a body, writes a stack or opens a chunk. */
final class FrontierV3UnitInventoryPhysicalCustody {
    private FrontierV3UnitInventoryPhysicalCustody() { }
    static boolean bindOne(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state) {
        var resources = state.inventory().fungibleResources();
        for (var account : resources.accounts().values().stream().sorted(Comparator.comparing(CustodyAccount::id)).toList()) {
            if (!(account.custody() instanceof ResourceCustody.Actor actor) || !account.claimQuantities().isEmpty()
                    || account.actorPresentation().isEmpty() || resources.bindings().values().stream()
                        .anyMatch(binding -> binding.accountId().equals(account.id()))
                    || !ActorBodyAuthority.retainsPhysicalCustody(state, actor.actorId())) continue;
            var identity = ActorBodyAuthority.current(state, actor.actorId());
            if (ActorBodyAuthority.require(state, identity).phase() != FencedRecoveryPhase.RUNNING
                    || !(level.getEntity(ActorBodyId.entityId(state.bootstrap().worldId(), actor.actorId())) instanceof Mob body)
                    || !body.isAlive() || !FrontierV3ActorBodyController.recognizes(state, body)) continue;
            var placement = UnitInventoryPresentation.inventory(state, actor.actorId()).get(account.id());
            placement.requireAccount(resources);
            var kind = resources.lots().get(account.lotQuantities().keySet().iterator().next()).itemKind();
            var quantity = account.lotQuantities().values().stream().mapToInt(Integer::intValue).sum();
            var expected = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(kind)), quantity);
            var actual = FrontierV3ActorResourceSlots.get(body, placement.slot());
            if (!ItemStack.matches(actual, expected)) continue;
            var receipt = new UnitInventoryBoundObserved(identity, account.id(),
                    new FungiblePhysicalObservation.Stack(FrontierV3ActorResourceSlots.address(actor.actorId(), body, placement.slot()), kind, quantity));
            var result = FrontierV3CommandSubmission.submit(runtime, "unit-inventory-bind", account.id().value(), receipt);
            if (result instanceof CommandResult.Accepted) {
                FrontierV3ActorCarryProjection.rememberConfirmed(runtime.decodedState().orElseThrow(), actor.actorId(), body);
                return true;
            }
        }
        return false;
    }
}
