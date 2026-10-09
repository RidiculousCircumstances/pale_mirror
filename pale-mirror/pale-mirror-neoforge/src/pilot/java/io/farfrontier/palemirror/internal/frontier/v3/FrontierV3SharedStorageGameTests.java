package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.nbt.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
import java.util.function.*;

/** Actual level-owned composition and flush; no world/canonical mutation or test player. */
@GameTestHolder(PaleMirrorMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FrontierV3SharedStorageGameTests {
    private record Entry(FrontierV3JournaledSavedData ledger, Function<CompoundTag,FrontierV3JournaledSavedData> decode) { }
    @GameTest(batch="pm-frontier-v3-shared-storage",templateNamespace="minecraft",template="bastion/mobs/empty",timeoutTicks=40)
    public static void everyPhysicalStoreUsesCompleteRecoveryAndSharedFlush(GameTestHelper helper) {
        var level=helper.getLevel();
        var entries=List.of(
            new Entry(FrontierV3ResourceSiteLedger.get(level),tag -> FrontierV3ResourceSiteLedger.load(tag,level.registryAccess())),
            new Entry(FrontierV3GrayboxLedger.get(level),tag -> FrontierV3GrayboxLedger.load(tag,level.registryAccess())),
            new Entry(FrontierV3DepotClickLedger.get(level),tag -> FrontierV3DepotClickLedger.load(tag,level.registryAccess())),
            new Entry(FrontierV3ObjectBoardLedger.get(level),tag -> FrontierV3ObjectBoardLedger.load(tag,level.registryAccess())),
            new Entry(FrontierV3HopperCarrierLedger.get(level),tag -> FrontierV3HopperCarrierLedger.load(tag,level.registryAccess())),
            new Entry(FrontierV3PhysicalObservationLedger.get(level),tag -> FrontierV3PhysicalObservationLedger.load(tag,level.registryAccess())),
            new Entry(FrontierV3ManagedExplosionLedger.get(level),tag -> FrontierV3ManagedExplosionLedger.load(tag,level.registryAccess())),
            new Entry(FrontierV3InfectionOverlayLedger.get(level),tag -> FrontierV3InfectionOverlayLedger.load(tag,level.registryAccess())));
        FrontierV3PhysicalStores.flush(level);
        for(var entry:entries) {
            var ledger=entry.ledger; var kind=ledger.storeKind(); var empty=ledger.metadata();
            for(var table:kind.tables) empty.put(table.name(),new ListTag());
            var recovered=FrontierV3JournaledSavedData.readFile(FrontierV3JournaledSavedData.storageFile(level,kind),
                    FrontierV3PhysicalWorld.WORLD_ID.value(),level.dimension().location().toString(),
                    () -> entry.decode.apply(empty),(tag,registries) -> entry.decode.apply(tag),level.registryAccess());
            helper.assertValueEqual(recovered.save(new CompoundTag(),level.registryAccess()),
                    ledger.save(new CompoundTag(),level.registryAccess()),"shared flush must persist each real adapter including its tail: "+kind);
            helper.assertTrue(!ledger.isDirty(),"a durable flush clears dirty state only after receipt: "+kind);
        }
        helper.assertTrue(FrontierV3PhysicalStores.diagnostic(level).contains("\"kind\":\"FIELDS\""),
                "common diagnostics include the non-actor stores");
        helper.succeed();
    }
}
