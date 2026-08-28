package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.ExplosionEntityImpact;
import io.farfrontier.palemirror.frontier.v3.model.ExplosionItemImpact;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.InfectionCell;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Durable post-impact evidence for a real v3-owned explosion, never a replay queue. */
final class FrontierV3ManagedExplosionLedger extends SavedData {
    private static final String NAME = "pale_mirror_frontier_v3_managed_explosions";
    private static final int FORMAT = 2, MAX_EFFECTS = 64, MAX_CELLS = 65_536, MAX_ENTITIES = 128, MAX_ITEMS = 256;
    private final LinkedHashMap<String, Pending> pending;

    private FrontierV3ManagedExplosionLedger() { this(new LinkedHashMap<>()); }
    private FrontierV3ManagedExplosionLedger(LinkedHashMap<String, Pending> pending) { this.pending = pending; }

    static FrontierV3ManagedExplosionLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(FrontierV3ManagedExplosionLedger::new,
                FrontierV3ManagedExplosionLedger::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE), NAME);
    }

    boolean capture(ServerLevel level, long gameTime, PhysicalIntentId intentId, List<BlockPos> affected, FrontierV3GrayboxLedger provenance,
                    java.util.function.Predicate<BlockPos> inFrontier) {
        return capture(level, gameTime, intentId, affected, List.of(), null, provenance, null, inFrontier);
    }

    boolean capture(ServerLevel level, long gameTime, PhysicalIntentId intentId, List<BlockPos> affected, List<Entity> entities,
                    FrontierWorldState state, FrontierV3GrayboxLedger provenance, FrontierV3InfectionOverlayLedger infection,
                    java.util.function.Predicate<BlockPos> inFrontier) {
        Objects.requireNonNull(level, "level"); Objects.requireNonNull(intentId, "intent id"); Objects.requireNonNull(affected, "affected blocks");
        String key = intentId.value(); if (pending.containsKey(key)) return false;
        if (pending.size() >= MAX_EFFECTS) throw new IllegalStateException("v3 managed explosion retention exceeded");
        List<BlockCandidate> blocks = affected.stream().distinct().sorted(Comparator.comparingLong(BlockPos::asLong)).filter(inFrontier)
                .map(position -> block(level, provenance, infection, position)).flatMap(Optional::stream).toList();
        List<EntityCandidate> entityCandidates = entities.stream().distinct().sorted(Comparator.comparing(Entity::getUUID))
                .map(FrontierV3ManagedExplosionLedger::entity).toList();
        List<ItemCandidate> itemCandidates = state == null ? List.of() : items(state, affected);
        if (blocks.size() > MAX_CELLS || entityCandidates.size() > MAX_ENTITIES || itemCandidates.size() > MAX_ITEMS) {
            throw new IllegalStateException("v3 managed explosion retained evidence exceeds bounds");
        }
        int affectedOverlays = Math.toIntExact(blocks.stream().filter(candidate -> candidate.infectionCell().isPresent()).count());
        pending.put(key, new Pending(key, gameTime, blocks, entityCandidates, itemCandidates, List.of(), List.of(), blocks.size(), 0,
                affectedOverlays, 0));
        setDirty(); return true;
    }

    boolean has(PhysicalIntentId intentId) { return pending.containsKey(intentId.value()); }
    Optional<ItemReady> nextItem(PhysicalIntentId intentId, long gameTime) {
        Pending value = ready(intentId, gameTime);
        return value == null || value.items().isEmpty() ? Optional.empty() : Optional.of(new ItemReady(intentId, value.items().getFirst()));
    }
    Optional<EntityReady> nextEntity(PhysicalIntentId intentId, long gameTime) {
        Pending value = ready(intentId, gameTime);
        return value == null || value.entities().isEmpty() ? Optional.empty() : Optional.of(new EntityReady(intentId, value.entities().getFirst()));
    }
    Optional<BlockReady> nextBlock(PhysicalIntentId intentId, long gameTime) {
        Pending value = ready(intentId, gameTime);
        return value == null || value.blocks().isEmpty() ? Optional.empty() : Optional.of(new BlockReady(intentId, value.blocks().getFirst()));
    }
    Optional<Completion> completeIfResolved(PhysicalIntentId intentId, long gameTime) {
        Pending value = ready(intentId, gameTime);
        if (value == null || !value.blocks().isEmpty() || !value.entities().isEmpty() || !value.items().isEmpty()) return Optional.empty();
        pending.remove(value.intentId()); setDirty();
        return Optional.of(new Completion(intentId, value.totalBlocks(), value.changedBlocks(), value.entityImpacts(), value.itemImpacts(),
                value.affectedInfectionOverlays(), value.changedInfectionOverlays()));
    }
    /** Compatibility queue used by the block-only ledger proof. */
    Optional<Ready> nextReady(long gameTime) {
        return pending.values().stream().filter(value -> value.capturedAtGameTime() < gameTime).findFirst().map(value ->
                new Ready(new PhysicalIntentId(value.intentId()), value.blocks().isEmpty() ? Optional.empty() : Optional.of(value.blocks().getFirst()),
                        value.totalBlocks(), value.changedBlocks()));
    }
    void resolve(Ready ready, boolean changed) { resolveBlock(new BlockReady(ready.intentId(), ready.candidate().orElseThrow()), changed); }
    Completion complete(Ready ready) { return completeIfResolved(ready.intentId(), Long.MAX_VALUE).orElseThrow(); }

    void resolveBlock(BlockReady ready, boolean changed) {
        Pending value = require(ready.intentId()); requireHead(value.blocks(), ready.candidate(), "block");
        int infection = changed && ready.candidate().infectionCell().isPresent() ? 1 : 0;
        replace(value, value.blocks().subList(1, value.blocks().size()), value.entities(), value.items(), value.entityImpacts(), value.itemImpacts(),
                value.changedBlocks() + (changed ? 1 : 0), value.affectedInfectionOverlays(), value.changedInfectionOverlays() + infection);
    }
    void resolveEntity(EntityReady ready, boolean removed) {
        Pending value = require(ready.intentId()); requireHead(value.entities(), ready.candidate(), "entity");
        ArrayList<ExplosionEntityImpact> impacts = new ArrayList<>(value.entityImpacts());
        impacts.add(new ExplosionEntityImpact(ready.candidate().entityId(), ready.candidate().entityType(), ready.candidate().actorId(), removed));
        replace(value, value.blocks(), value.entities().subList(1, value.entities().size()), value.items(), impacts, value.itemImpacts(),
                value.changedBlocks(), value.affectedInfectionOverlays(), value.changedInfectionOverlays());
    }
    void resolveItem(ItemReady ready, ExplosionItemImpact.Outcome outcome) {
        Pending value = require(ready.intentId()); requireHead(value.items(), ready.candidate(), "item");
        ArrayList<ExplosionItemImpact> impacts = new ArrayList<>(value.itemImpacts());
        impacts.add(new ExplosionItemImpact(ready.candidate().itemId(), outcome));
        replace(value, value.blocks(), value.entities(), value.items().subList(1, value.items().size()), value.entityImpacts(), impacts,
                value.changedBlocks(), value.affectedInfectionOverlays(), value.changedInfectionOverlays());
    }

    private Pending ready(PhysicalIntentId intentId, long gameTime) {
        Pending value = pending.get(intentId.value()); return value == null || value.capturedAtGameTime() >= gameTime ? null : value;
    }
    private Pending require(PhysicalIntentId intentId) {
        Pending value = pending.get(intentId.value());
        if (value == null) throw new IllegalStateException("unknown managed v3 explosion intent"); return value;
    }
    private void replace(Pending old, List<BlockCandidate> blocks, List<EntityCandidate> entities, List<ItemCandidate> items,
                         List<ExplosionEntityImpact> entityImpacts, List<ExplosionItemImpact> itemImpacts, int changedBlocks, int overlays, int changedOverlays) {
        pending.put(old.intentId(), new Pending(old.intentId(), old.capturedAtGameTime(), blocks, entities, items, entityImpacts, itemImpacts,
                old.totalBlocks(), changedBlocks, overlays, changedOverlays)); setDirty();
    }
    private static <T> void requireHead(List<T> values, T candidate, String kind) {
        if (values.isEmpty() || !values.getFirst().equals(candidate)) throw new IllegalStateException("v3 managed explosion resolution is not the retained " + kind + " head");
    }
    private static Optional<BlockCandidate> block(ServerLevel level, FrontierV3GrayboxLedger provenance, FrontierV3InfectionOverlayLedger infection, BlockPos position) {
        BlockState baseline = level.getBlockState(position); if (baseline.isAir()) return Optional.empty();
        Optional<InfectionCell> cell = infection == null ? Optional.empty() : infection.cellAt(position);
        return Optional.of(new BlockCandidate(position.asLong(), NbtUtils.writeBlockState(baseline), semantic(provenance.claim(position), baseline), cell));
    }
    private static EntityCandidate entity(Entity value) {
        Optional<SubjectId> actor = actor(value);
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(value.getType()).toString();
        return new EntityCandidate(value.getUUID(), type, actor, value.blockPosition().asLong());
    }
    private static Optional<SubjectId> actor(Entity entity) {
        String value = entity.getPersistentData().getString(FrontierV3SceneExecutor.ACTOR_KEY);
        if (value.isBlank()) value = entity.getPersistentData().getString(FrontierV3AmbientActorExecutor.ACTOR_KEY);
        try { return value.isBlank() ? Optional.empty() : Optional.of(new SubjectId(value)); }
        catch (IllegalArgumentException ignored) { return Optional.empty(); }
    }
    private static List<ItemCandidate> items(FrontierWorldState state, List<BlockPos> affected) {
        java.util.Set<Long> positions = affected.stream().map(BlockPos::asLong).collect(java.util.stream.Collectors.toSet());
        return state.inventory().items().values().stream().filter(item -> item.custody() instanceof InventoryCustody.ContainerSlot)
                .map(item -> item(state, item)).flatMap(Optional::stream)
                .filter(candidate -> positions.contains(candidate.position())).sorted(Comparator.comparing(ItemCandidate::itemId)).toList();
    }
    private static Optional<ItemCandidate> item(FrontierWorldState state, ExactItemStack item) {
        InventoryCustody.ContainerSlot source = (InventoryCustody.ContainerSlot) item.custody();
        return Optional.ofNullable(state.inventory().surfaces().get(source.containerId())).map(surface ->
                new ItemCandidate(item.id(), source.containerId(), source.slot(), new BlockPos(surface.position().x(), surface.position().y(), surface.position().z()).asLong()));
    }
    private static Optional<FrontierV3PhysicalObservationLedger.Semantic> semantic(FrontierV3GrayboxLedger.Claim claim, BlockState baseline) {
        if (claim == null || claim.conflicted()) return Optional.empty();
        try {
            var material = io.farfrontier.palemirror.frontier.v3.model.GrayboxMaterial.valueOf(claim.material());
            var part = io.farfrontier.palemirror.frontier.v3.model.GrayboxSemanticPart.valueOf(claim.semanticPart());
            return baseline.equals(FrontierV3GrayboxExecutor.material(material)) ? Optional.of(new FrontierV3PhysicalObservationLedger.Semantic(claim.owner(), part.name())) : Optional.empty();
        } catch (IllegalArgumentException ignored) { return Optional.empty(); }
    }

    static FrontierV3ManagedExplosionLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        int format = tag.getInt("format"); if (format != 1 && format != FORMAT) throw new IllegalStateException("incompatible v3 managed explosion ledger");
        ListTag values = tag.getList("pending", Tag.TAG_COMPOUND);
        if (values.size() > MAX_EFFECTS) throw new IllegalStateException("v3 managed explosion retention exceeded");
        LinkedHashMap<String, Pending> pending = new LinkedHashMap<>();
        for (Tag value : values) { Pending entry = Pending.load((CompoundTag) value, format); if (pending.putIfAbsent(entry.intentId(), entry) != null) throw new IllegalStateException("duplicate managed v3 explosion intent"); }
        return new FrontierV3ManagedExplosionLedger(pending);
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", FORMAT); ListTag values = new ListTag(); pending.values().forEach(value -> values.add(value.save())); tag.put("pending", values); return tag;
    }

    record Ready(PhysicalIntentId intentId, Optional<BlockCandidate> candidate, int totalCandidates, int changedCandidates) { }
    record BlockReady(PhysicalIntentId intentId, BlockCandidate candidate) { }
    record EntityReady(PhysicalIntentId intentId, EntityCandidate candidate) { }
    record ItemReady(PhysicalIntentId intentId, ItemCandidate candidate) { }
    record Completion(PhysicalIntentId intentId, int affectedBlockCount, int changedBlockCount, List<ExplosionEntityImpact> entityImpacts,
                      List<ExplosionItemImpact> itemImpacts, int affectedInfectionOverlayCount, int changedInfectionOverlayCount) { }
    record BlockCandidate(long position, CompoundTag baseline, Optional<FrontierV3PhysicalObservationLedger.Semantic> semantic, Optional<InfectionCell> infectionCell) {
        BlockCandidate {
            baseline = baseline.copy(); semantic = Objects.requireNonNull(semantic, "semantic"); infectionCell = Objects.requireNonNull(infectionCell, "infection cell");
        }
        BlockPos blockPos() { return BlockPos.of(position); }
        BlockState baseline(HolderLookup.Provider registries) { return NbtUtils.readBlockState(registries.lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK), baseline); }
        CompoundTag save() {
            CompoundTag value = new CompoundTag(); value.putLong("pos", position); value.put("baseline", baseline.copy());
            semantic.ifPresent(known -> { value.putString("owner", known.owner()); value.putString("part", known.semanticPart()); });
            infectionCell.ifPresent(cell -> { value.putInt("infectionX", cell.x()); value.putInt("infectionZ", cell.z()); }); return value;
        }
        static BlockCandidate load(CompoundTag value) {
            if (!value.contains("pos", Tag.TAG_LONG) || !value.contains("baseline", Tag.TAG_COMPOUND)) throw new IllegalStateException("incomplete managed explosion block");
            boolean owner = value.contains("owner", Tag.TAG_STRING), part = value.contains("part", Tag.TAG_STRING), x = value.contains("infectionX", Tag.TAG_INT), z = value.contains("infectionZ", Tag.TAG_INT);
            if (owner != part || x != z) throw new IllegalStateException("partial managed explosion block evidence");
            return new BlockCandidate(value.getLong("pos"), value.getCompound("baseline"), owner ? Optional.of(new FrontierV3PhysicalObservationLedger.Semantic(value.getString("owner"), value.getString("part"))) : Optional.empty(),
                    x ? Optional.of(new InfectionCell(value.getInt("infectionX"), value.getInt("infectionZ"))) : Optional.empty());
        }
    }
    record EntityCandidate(UUID entityId, String entityType, Optional<SubjectId> actorId, long position) {
        EntityCandidate {
            Objects.requireNonNull(entityId, "entity id"); if (entityType == null || entityType.isBlank()) throw new IllegalArgumentException("invalid entity type"); actorId = Objects.requireNonNull(actorId, "actor id");
        }
        BlockPos blockPos() { return BlockPos.of(position); }
        CompoundTag save() { CompoundTag value = new CompoundTag(); value.putUUID("id", entityId); value.putString("type", entityType); value.putLong("pos", position); actorId.ifPresent(id -> value.putString("actor", id.value())); return value; }
        static EntityCandidate load(CompoundTag value) {
            if (!value.hasUUID("id") || !value.contains("type", Tag.TAG_STRING) || !value.contains("pos", Tag.TAG_LONG)) throw new IllegalStateException("incomplete managed explosion entity");
            String actor = value.getString("actor");
            return new EntityCandidate(value.getUUID("id"), value.getString("type"), actor.isBlank() ? Optional.empty() : Optional.of(new SubjectId(actor)), value.getLong("pos"));
        }
    }
    record ItemCandidate(SubjectId itemId, SubjectId containerId, int slot, long position) {
        ItemCandidate {
            Objects.requireNonNull(itemId, "item id"); Objects.requireNonNull(containerId, "container id"); if (slot < 0 || slot > 255) throw new IllegalArgumentException("invalid item slot");
        }
        BlockPos blockPos() { return BlockPos.of(position); }
        CompoundTag save() {
            CompoundTag value = new CompoundTag(); value.putString("item", itemId.value()); value.putString("container", containerId.value()); value.putByte("slot", (byte) slot); value.putLong("pos", position); return value;
        }
        static ItemCandidate load(CompoundTag value) {
            if (!value.contains("item", Tag.TAG_STRING) || !value.contains("container", Tag.TAG_STRING)
                    || !value.contains("slot", Tag.TAG_BYTE) || !value.contains("pos", Tag.TAG_LONG)) throw new IllegalStateException("incomplete managed explosion item");
            return new ItemCandidate(new SubjectId(value.getString("item")), new SubjectId(value.getString("container")), value.getByte("slot") & 0xFF, value.getLong("pos"));
        }
    }
    record Pending(String intentId, long capturedAtGameTime, List<BlockCandidate> blocks, List<EntityCandidate> entities, List<ItemCandidate> items,
                   List<ExplosionEntityImpact> entityImpacts, List<ExplosionItemImpact> itemImpacts, int totalBlocks, int changedBlocks,
                   int affectedInfectionOverlays, int changedInfectionOverlays) {
        Pending {
            if (intentId == null || intentId.isBlank() || capturedAtGameTime < 0L || blocks == null || entities == null || items == null
                    || entityImpacts == null || itemImpacts == null || blocks.size() > MAX_CELLS || entities.size() > MAX_ENTITIES
                    || items.size() > MAX_ITEMS || entityImpacts.size() > MAX_ENTITIES || itemImpacts.size() > MAX_ITEMS
                    || totalBlocks < blocks.size() || totalBlocks > MAX_CELLS || changedBlocks < 0 || changedBlocks > totalBlocks - blocks.size()
                    || affectedInfectionOverlays < changedInfectionOverlays || changedInfectionOverlays < 0) throw new IllegalArgumentException("invalid managed explosion record");
            blocks = List.copyOf(blocks); entities = List.copyOf(entities); items = List.copyOf(items); entityImpacts = List.copyOf(entityImpacts); itemImpacts = List.copyOf(itemImpacts);
        }
        CompoundTag save() {
            CompoundTag value = new CompoundTag(); value.putString("intent", intentId); value.putLong("capturedAt", capturedAtGameTime);
            value.putInt("total", totalBlocks); value.putInt("changed", changedBlocks); value.putInt("infectionTotal", affectedInfectionOverlays);
            value.putInt("infectionChanged", changedInfectionOverlays); value.put("blocks", saveList(blocks, BlockCandidate::save));
            value.put("entities", saveList(entities, EntityCandidate::save)); value.put("items", saveList(items, ItemCandidate::save));
            value.put("entityImpacts", saveList(entityImpacts, Pending::saveEntityImpact)); value.put("itemImpacts", saveList(itemImpacts, Pending::saveItemImpact)); return value;
        }
        static Pending load(CompoundTag value, int format) {
            if (format == 1) return legacy(value);
            if (!value.contains("intent", Tag.TAG_STRING) || !value.contains("capturedAt", Tag.TAG_LONG)
                    || !value.contains("total", Tag.TAG_INT) || !value.contains("changed", Tag.TAG_INT)) throw new IllegalStateException("incomplete managed explosion record");
            return new Pending(value.getString("intent"), value.getLong("capturedAt"), loadList(value, "blocks", BlockCandidate::load),
                    loadList(value, "entities", EntityCandidate::load), loadList(value, "items", ItemCandidate::load),
                    loadList(value, "entityImpacts", Pending::loadEntityImpact), loadList(value, "itemImpacts", Pending::loadItemImpact),
                    value.getInt("total"), value.getInt("changed"), value.getInt("infectionTotal"), value.getInt("infectionChanged"));
        }
        private static Pending legacy(CompoundTag value) {
            ListTag cells = value.getList("cells", Tag.TAG_COMPOUND); ArrayList<BlockCandidate> blocks = new ArrayList<>();
            for (Tag cell : cells) blocks.add(BlockCandidate.load((CompoundTag) cell));
            return new Pending(value.getString("intent"), value.getLong("capturedAt"), blocks, List.of(), List.of(), List.of(), List.of(),
                    value.getInt("total"), value.getInt("changed"), 0, 0);
        }
        private static <T> ListTag saveList(List<T> values, java.util.function.Function<T, CompoundTag> mapper) {
            ListTag tag = new ListTag(); values.forEach(value -> tag.add(mapper.apply(value))); return tag;
        }
        private static <T> List<T> loadList(CompoundTag source, String key, java.util.function.Function<CompoundTag, T> mapper) {
            if (!source.contains(key, Tag.TAG_LIST)) throw new IllegalStateException("missing managed explosion " + key);
            ListTag values = source.getList(key, Tag.TAG_COMPOUND); ArrayList<T> result = new ArrayList<>();
            for (Tag value : values) result.add(mapper.apply((CompoundTag) value)); return result;
        }
        private static CompoundTag saveEntityImpact(ExplosionEntityImpact impact) {
            CompoundTag value = new CompoundTag(); value.putUUID("id", impact.entityId()); value.putString("type", impact.entityType());
            impact.frontierActorId().ifPresent(actor -> value.putString("actor", actor.value())); value.putBoolean("removed", impact.removed()); return value;
        }
        private static ExplosionEntityImpact loadEntityImpact(CompoundTag value) {
            return new ExplosionEntityImpact(value.getUUID("id"), value.getString("type"),
                    value.getString("actor").isBlank() ? Optional.empty() : Optional.of(new SubjectId(value.getString("actor"))), value.getBoolean("removed"));
        }
        private static CompoundTag saveItemImpact(ExplosionItemImpact impact) {
            CompoundTag value = new CompoundTag(); value.putString("item", impact.itemId().value()); value.putByte("outcome", (byte) impact.outcome().ordinal()); return value;
        }
        private static ExplosionItemImpact loadItemImpact(CompoundTag value) {
            int outcome = value.getByte("outcome") & 0xFF;
            if (outcome >= ExplosionItemImpact.Outcome.values().length) throw new IllegalStateException("invalid managed explosion item outcome");
            return new ExplosionItemImpact(new SubjectId(value.getString("item")), ExplosionItemImpact.Outcome.values()[outcome]);
        }
    }
}
