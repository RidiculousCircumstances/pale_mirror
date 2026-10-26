package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3NativeAutosave;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import java.io.File;
import java.util.Map;
import java.util.function.BiConsumer;

/** Only vanilla's implicit autosave request uses the enqueue port. Explicit owner saves retain their barriers. */
@Mixin(DimensionDataStorage.class)
abstract class FrontierV3NativeAutosaveMixin {
    @Shadow @Final private HolderLookup.Provider registries;
    @Invoker("getDataFile") public abstract File frontierV3$dataFile(String name);
    @WrapOperation(method = "save", at = @At(value = "INVOKE", target = "Ljava/util/Map;forEach(Ljava/util/function/BiConsumer;)V"), require = 1)
    private void frontierV3$submitNativeJournals(Map<String, SavedData> cache, BiConsumer<String, SavedData> save,
            Operation<Void> original) {
        if (!FrontierV3NativeAutosave.active()) { original.call(cache, save); return; }
        original.call(cache, (BiConsumer<String, SavedData>) (name, data) -> {
            if (data instanceof FrontierV3NativeAutosave.Target target) target.submitAutosave(frontierV3$dataFile(name), registries);
            else save.accept(name, data);
        });
    }
}
