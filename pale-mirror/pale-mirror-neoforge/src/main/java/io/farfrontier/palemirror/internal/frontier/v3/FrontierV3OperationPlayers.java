package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

/** Player.dat is a separate native save participant, never an entity-column receipt.
 * The menu cursor is not saved in Inventory: snapshots with a carried stack cannot
 * acknowledge inventory operations. No restore, guessed item match or forced UI close. */
public final class FrontierV3OperationPlayers {
    static final String KEY = "pm_v3_player_operations";
    static final String HEALTH_KEY="pm_v3_player_health_operations";
    private record Aspect(String key,FrontierV3OperationReceipt.Medium medium){}
    private static final List<Aspect> ASPECTS=List.of(new Aspect(KEY,FrontierV3OperationReceipt.Medium.PLAYER),
            new Aspect(HEALTH_KEY,FrontierV3OperationReceipt.Medium.PLAYER_HEALTH));
    private FrontierV3OperationPlayers() { }
    static FrontierV3MinecraftOperation.Participant current(ServerPlayer player) { return current(player,ASPECTS.getFirst()); }
    static FrontierV3MinecraftOperation.Participant currentHealth(ServerPlayer player) { return current(player,ASPECTS.get(1)); }
    private static FrontierV3MinecraftOperation.Participant current(ServerPlayer player,Aspect aspect) {
        var data=player.getPersistentData();
        if(!data.contains(aspect.key()))data.put(aspect.key(),new CompoundTag());
        if(!data.contains(aspect.key(),Tag.TAG_COMPOUND))throw new IllegalStateException("invalid player operation aspect");
        var bag=data.getCompound(aspect.key());
        var identity=new FrontierV3OperationReceipt.Participant(aspect.medium(),player.getStringUUID(),FrontierV3NativeOperationMarkers.lifetime(bag));
        return new FrontierV3MinecraftOperation.Participant(identity,bag,()->{});
    }
    /** Decorate only the detached image actually passed to the native file writer. */
    public static CompoundTag snapshot(Player player, CompoundTag image) {
        return snapshot(image, player.containerMenu.getCarried().isEmpty());
    }
    static CompoundTag snapshot(CompoundTag image, boolean inventoryCursorEmpty) {
        if (inventoryCursorEmpty) return image;
        var result = image.copy(); result.getCompound("NeoForgeData").remove(KEY); return result;
    }
    static List<FrontierV3OperationReceipt.NativeReceipt> receipts(UUID player,CompoundTag image) {
        var data=image.getCompound("NeoForgeData");var result=new ArrayList<FrontierV3OperationReceipt.NativeReceipt>();
        for(var aspect:ASPECTS) {
            if(!data.contains(aspect.key()))continue;
            if(!image.hasUUID("UUID")||!image.getUUID("UUID").equals(player)||!data.contains(aspect.key(),Tag.TAG_COMPOUND))
                throw new IllegalStateException("player save has foreign identity or operation aspect");
            var bag=data.getCompound(aspect.key());
            for(var receipt:FrontierV3NativeOperationMarkers.read(bag)) {
                var identity=receipt.participant();
                if(identity.medium()!=aspect.medium() || !identity.identity().equals(player.toString())
                        ||!bag.contains(FrontierV3NativeOperationMarkers.LIFETIME,Tag.TAG_LONG)
                        ||identity.incarnation()!=bag.getLong(FrontierV3NativeOperationMarkers.LIFETIME))
                    throw new IllegalStateException("player save has foreign native participant");
                result.add(receipt);
            }
        }
        return List.copyOf(result);
    }
    /** Native safeReplaceFile returns void and may fail. Read and force the actual
     * target, never acknowledge the intended temp image or PlayerSavingEvent alone. */
    static CompoundTag durableImage(Path target) throws java.io.IOException {
        CompoundTag image;
        try (var file = FileChannel.open(target, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            file.force(true);
            image = NbtIo.readCompressed(Channels.newInputStream(file), NbtAccounter.create(16L * 1024 * 1024));
        }
        try (var directory = FileChannel.open(target.getParent(), StandardOpenOption.READ)) { directory.force(true); }
        return image;
    }
    public static void saved(Player nativePlayer, Path directory) {
        if (!(nativePlayer instanceof ServerPlayer player) || player.getServer() == null) return;
        var data = player.getPersistentData();
        if (ASPECTS.stream().noneMatch(aspect->data.contains(aspect.key()))) return;
        var level = player.getServer().getLevel(FrontierV3PhysicalWorld.DIMENSION);
        if (level == null || !FrontierV3ServerLifecycle.acceptsNativeOperationSaves(level)) return;
        var ledger=FrontierV3OperationLedger.get(level);
        var bags=ASPECTS.stream().filter(aspect->data.contains(aspect.key())).map(aspect->data.getCompound(aspect.key())).toList();
        bags.forEach(bag->FrontierV3NativeOperationMarkers.prune(bag,ledger));
        if(bags.stream().flatMap(bag->FrontierV3NativeOperationMarkers.read(bag).stream()).noneMatch(receipt->ledger.find(receipt.ticket())
                .map(operation->!operation.saved().contains(receipt.participant())).orElse(false)))return;
        var target = directory.resolve(player.getStringUUID() + ".dat");
        if (!Files.isRegularFile(target)) return;
        try {
            for (var receipt : receipts(player.getUUID(), durableImage(target))) {
                if (ledger.find(receipt.ticket()).isEmpty()) continue;
                ledger.saved(receipt);
                if (ledger.require(receipt.ticket()).retirable()) ledger.retire(receipt.ticket());
            }
            ledger.persist(level);
            bags.forEach(bag->FrontierV3NativeOperationMarkers.prune(bag,ledger));
        } catch (java.io.IOException failure) {
            io.farfrontier.palemirror.PaleMirrorMod.LOGGER.error(
                    "Player {} operation save could not be confirmed; receipts retained", player.getUUID(), failure);
        }
    }
}
