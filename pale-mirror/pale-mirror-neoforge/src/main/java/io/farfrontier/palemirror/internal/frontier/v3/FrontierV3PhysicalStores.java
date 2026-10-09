package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
import java.util.function.Supplier;

/** Shared lifecycle only. Exact world/store identity is supplied by the owning adapter. */
final class FrontierV3PhysicalStores {
    private record Key(WorldId world, FrontierV3PhysicalStoreKind kind) { }
    private record Entry(Object owner, Runnable flush, Runnable finish, Supplier<String> diagnostic) { }
    private static final Map<ServerLevel, Map<Key,Entry>> STORES = new IdentityHashMap<>();
    private FrontierV3PhysicalStores() { }
    static void register(ServerLevel level, FrontierV3PhysicalStoreKind kind, FrontierV3JournaledSavedData ledger) {
        register(level, FrontierV3PhysicalWorld.WORLD_ID, kind, ledger, () -> ledger.persist(level), ledger::finish, ledger::journalDiagnostic);
    }
    static void register(ServerLevel level, WorldId world, FrontierV3PhysicalStoreKind kind, Object owner,
            Runnable flush, Runnable finish, Supplier<String> diagnostic) {
        Objects.requireNonNull(world); Objects.requireNonNull(kind); Objects.requireNonNull(owner);
        var entries = STORES.computeIfAbsent(level, ignored -> new LinkedHashMap<>());
        var prior = entries.putIfAbsent(new Key(world,kind), new Entry(owner,flush,finish,diagnostic));
        if (prior != null && prior.owner != owner) throw new IllegalStateException("competing physical store owner: " + world + "/" + kind);
    }
    static void release(ServerLevel level, WorldId world, FrontierV3PhysicalStoreKind kind, Object expected) {
        var entries=STORES.get(level); var key=new Key(world,kind); var entry=entries == null ? null : entries.get(key);
        if (entry == null || entry.owner != expected) throw new IllegalStateException("physical release lacks exact owner");
        entry.finish.run(); entries.remove(key); if (entries.isEmpty()) STORES.remove(level);
    }
    static void flush(ServerLevel level) {
        var entries = STORES.get(level);
        if (entries != null) entries.values().forEach(entry -> entry.flush.run());
    }
    static void finish(MinecraftServer server) {
        var owned = STORES.keySet().stream().filter(level -> level.getServer() == server).toList();
        // Retain all registrations on failure; failed durability must not release an owner.
        for (var level : owned) STORES.get(level).values().forEach(entry -> entry.finish.run());
        owned.forEach(STORES::remove);
    }
    static String diagnostic(ServerLevel level) {
        var entries = STORES.get(level);
        if (entries == null) return "[]";
        return entries.entrySet().stream().map(entry -> "{\"world\":\"" + FrontierV3DiagnosticJson.quote(entry.getKey().world.value())
                + "\",\"kind\":\"" + entry.getKey().kind.name() + "\",\"journal\":" + entry.getValue().diagnostic.get() + "}")
                .collect(java.util.stream.Collectors.joining(",","[","]"));
    }
}
