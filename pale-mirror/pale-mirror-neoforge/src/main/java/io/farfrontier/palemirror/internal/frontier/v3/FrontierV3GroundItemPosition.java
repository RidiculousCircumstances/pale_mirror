package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;

/** Native movement changes a source location sparsely, retaining all quantities and the existing owner-local claim-loss protocol. */
public final class FrontierV3GroundItemPosition {
    static final String MARKER = "pmv3_ground_resource_v1";
    public static void observed(ItemEntity entity) {
        if (entity.isRemoved() || !entity.getPersistentData().getBoolean(MARKER)
                || !(entity.level() instanceof ServerLevel level) || !FrontierV3PhysicalWorld.isPhysical(level)) return;
        var runtime = FrontierV3ServerLifecycle.runtimeFor(level.getServer());
        if (runtime == null || runtime.status().kind() != FrontierV3RuntimeStatus.Kind.ACTIVE) return;
        var journal = FrontierV3ResourceHandoffLedger.get(level);
        if (journal.blocks(entity.getUUID())) return;
        var state = runtime.decodedState().orElseThrow(); var resources = state.inventory().fungibleResources();
        var account = resources.accounts().values().stream().filter(value -> value.custody() instanceof ResourceCustody.Ground ground
                && ground.entityId().equals(entity.getUUID())).findFirst().orElse(null);
        if (account == null) return;
        if (journal.blocksAccount(account.id())) return;
        var ground = (ResourceCustody.Ground) account.custody(); var p = entity.blockPosition();
        var position = new BlockPosition(p.getX(), p.getY(), p.getZ());
        if (position.equals(ground.position())) return;
        var bindings = resources.bindings().values().stream().filter(binding -> binding.accountId().equals(account.id())).toList();
        if (bindings.size() != 1) throw new IllegalStateException("ground item position has no exact native stack binding: " + account.id());
        var binding = bindings.getFirst(); var stack = entity.getItem();
        if (!stack.getComponentsPatch().isEmpty() || stack.getCount() != binding.quantity()
                || !BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(binding.itemKind())) return;
        long epoch = Math.addExact(binding.authorityEpoch(), 1);
        var receipt = FungiblePhysicalHandoff.departToNew(resources, account.id(), binding.authorityEpoch(), binding, 0,
                new SubjectId("custody:ground-" + entity.getUUID() + "-" + epoch), new ResourceCustody.Ground(entity.getUUID(), position), epoch,
                new PhysicalStackAddress.WorldEntity(entity.getUUID())).forfeitAffectedClaims(resources);
        if (!receipt.forfeitedClaimIds().isEmpty() && !FungibleClaimForfeitureStateSupport.supports(state, receipt)) return;
        journal.prepare(entity.getUUID(), receipt); journal.persist(level);
        FrontierV3ContainerDrops.reconcile(level, runtime, entity.getUUID(), receipt);
    }
    private FrontierV3GroundItemPosition() { }
}
