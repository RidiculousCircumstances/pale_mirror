package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ServerLifecycle;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** After the cancellable event and duplicate-death guard, before death loot. */
@Mixin(LivingEntity.class)
abstract class FrontierV3LivingDeathMixin {
    @Shadow protected boolean dead;

    @WrapOperation(method = "die", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
            target = "Lnet/minecraft/world/entity/LivingEntity;dead:Z"), require = 1)
    private void frontierV3$commitDeath(LivingEntity entity, boolean value, Operation<Void> original, DamageSource source) {
        if (value && entity.level() instanceof ServerLevel level
                && FrontierV3ServerLifecycle.commitLivingDeath(level, entity, source.getEntity(),
                    () -> original.call(entity, value), () -> dead)) return;
        original.call(entity, value);
    }
}
