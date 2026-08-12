package io.farfrontier.palemirror.internal.settlement;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.PaleMirrorVisuals;
import io.farfrontier.palemirror.api.ParcelKind;
import io.farfrontier.palemirror.api.SemanticSlotKey;
import io.farfrontier.palemirror.domain.DevelopmentIntent;
import io.farfrontier.palemirror.domain.DevelopmentIntentState;
import io.farfrontier.palemirror.domain.DevelopmentIntentType;
import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandExecutor;
import io.farfrontier.palemirror.domain.StructuralIntegrity;
import io.farfrontier.palemirror.internal.materialization.JobState;
import io.farfrontier.palemirror.internal.materialization.MaterializationGateway;
import io.farfrontier.palemirror.internal.materialization.MaterializationJob;
import io.farfrontier.palemirror.internal.materialization.MaterializationJobClass;
import io.farfrontier.palemirror.internal.materialization.MaterializationOperation;
import io.farfrontier.palemirror.internal.materialization.MaterializationOperationType;
import io.farfrontier.palemirror.internal.materialization.OperationState;
import io.farfrontier.palemirror.internal.materialization.ParcelRecord;
import io.farfrontier.palemirror.internal.materialization.SemanticCellRecord;
import io.farfrontier.palemirror.internal.materialization.SemanticSlotRegistration;
import io.farfrontier.palemirror.internal.materialization.SemanticSlotRecord;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Staged damage, pristine reconstruction and reserved-plot development through the shared gateway. */
final class AuthoredSettlementProjectRuntime {
    private static final String STRUCTURAL_CHANNEL = "settlement_structure";
    private static final String DEVELOPMENT_CHANNEL = "settlement_development";
    private static final String VERSION = "authored-project-v35-1";
    private AuthoredSettlementProjectRuntime() { }

    static boolean tick(MinecraftServer server, PaleMirrorSavedData data, DomainCommandExecutor commands) {
        var provider = PaleMirrorVisuals.provider().orElse(null);
        if (provider == null) return false;
        Map<String, AuthoredRegionSeed> seeds = provider.discoverAuthoredRegions(server.overworld()).stream()
                .collect(Collectors.toMap(AuthoredRegionSeed::planId, Function.identity()));
        boolean changed = false;
        for (var region : data.worldState().livingRegions().stream().sorted(Comparator.comparing(value -> value.id())).toList()) {
            AuthoredRegionSeed seed = seeds.get(region.id());
            if (seed == null) continue;
            ServerLevel level = level(server, seed.dimensionId());
            if (level == null) continue;
            var place = data.worldState().place(region.placeId()).orElse(null);
            if (place == null) continue;
            DevelopmentIntent reconstruction = intent(data, region.communityId(), DevelopmentIntentType.RECONSTRUCT_PLACE);
            StructuralIntegrity desired = reconstruction != null && reconstruction.state() == DevelopmentIntentState.MATERIALIZING
                    ? StructuralIntegrity.INTACT : place.structuralIntegrity();
            int slotCount = data.semanticSlots().slots().size();
            StructuralSlots structuralSlots = ensureStructuralSlots(level, data, provider, seed, region.placeId().value());
            changed |= data.semanticSlots().slots().size() != slotCount;
            if (desired != StructuralIntegrity.INTACT || reconstruction != null
                    && reconstruction.state() == DevelopmentIntentState.MATERIALIZING) {
                changed |= structural(level, data, commands, region.placeId().value(), desired, reconstruction,
                        structuralSlots);
            }
            DevelopmentIntent expansion = intent(data, region.communityId(), DevelopmentIntentType.UPGRADE_STOREHOUSE);
            if (expansion != null && expansion.state() == DevelopmentIntentState.MATERIALIZING) {
                changed |= expansion(level, data, commands, expansion);
            }
        }
        return changed;
    }

    private static boolean structural(ServerLevel level, PaleMirrorSavedData data, DomainCommandExecutor commands,
                                      String placeId, StructuralIntegrity desired,
                                      DevelopmentIntent reconstruction, StructuralSlots structuralSlots) {
        if (!structuralSlots.complete()) return false;
        List<SemanticSlotKey> slots = structuralSlots.keys();
        String policy = "pale_mirror:settlement_" + desired.name().toLowerCase(java.util.Locale.ROOT);
        MaterializationJob job = ensureJob(data, placeId, STRUCTURAL_CHANNEL, MaterializationJobClass.PRESENTATION,
                desired.ordinal() + (reconstruction == null ? 0 : 10), policy, slots);
        boolean changed = runOne(level, data, job, desired == StructuralIntegrity.INTACT
                ? SemanticCellRecord::baselineState : cell -> damaged(cell, desired));
        if (job.state() == JobState.BLOCKED && reconstruction != null) {
            commands.execute(data.worldState(), new DomainCommand.BlockDevelopmentIntent(reconstruction.id(), job.lastError()));
        } else if (job.state() == JobState.COMPLETED && reconstruction != null
                && reconstruction.state() == DevelopmentIntentState.MATERIALIZING) {
            commands.execute(data.worldState(), new DomainCommand.CompleteDevelopmentIntent(reconstruction.id()));
        }
        return changed;
    }

    private static StructuralSlots ensureStructuralSlots(ServerLevel level, PaleMirrorSavedData data,
                                                          io.farfrontier.palemirror.api.VisualProvider provider,
                                                          AuthoredRegionSeed seed, String placeId) {
        List<SemanticSlotKey> keys = new ArrayList<>();
        boolean complete = true;
        for (int index = 0; index < seed.modules().size(); index++) {
            var module = seed.modules().get(index);
            if (!(module.role().equals("CIVIC") || module.role().equals("DEFENCE")
                    || module.role().equals("LOGISTICS") || module.role().equals("ECONOMY"))) continue;
            SemanticSlotKey key = new SemanticSlotKey(placeId, "authored_module_" + index, "damage_shell");
            keys.add(key);
            if (data.semanticSlots().find(key).isPresent()) continue;
            if (!provider.authoredModuleReady(level, seed, module)
                    || !allChunksLoaded(level, block(module.footprint().min()), block(module.footprint().max()))) {
                complete = false;
                continue;
            }
            List<SemanticCellRecord> cells = sampleShell(level, block(module.footprint().min()), block(module.footprint().max()), key.value());
            if (cells.isEmpty()) { complete = false; continue; }
            SemanticSlotRegistration.register(data.semanticSlots(), data.parcels(), key,
                    seed.planId() + ":parcel:module_" + index, seed.dimensionId(),
                    ParcelKind.COMMUNITY, cells);
        }
        return new StructuralSlots(List.copyOf(keys), complete
                && keys.stream().allMatch(key -> data.semanticSlots().find(key).isPresent()));
    }

    private static List<SemanticCellRecord> sampleShell(ServerLevel level, BlockPos min, BlockPos max, String seed) {
        List<SemanticCellRecord> cells = new ArrayList<>();
        for (int x = min.getX(); x <= max.getX() && cells.size() < 12; x++) for (int z = min.getZ(); z <= max.getZ() && cells.size() < 12; z++) {
            if (Math.floorMod((seed + ":" + x + ":" + z).hashCode(), 11) != 0) continue;
            for (int y = max.getY(); y >= min.getY(); y--) {
                BlockPos position = new BlockPos(x, y, z); BlockState state = level.getBlockState(position);
                if (!state.isAir() && level.getBlockEntity(position) == null && state.getDestroySpeed(level, position) >= 0) {
                    cells.add(new SemanticCellRecord(position, state, state)); break;
                }
            }
        }
        return List.copyOf(cells);
    }

    private static BlockState damaged(SemanticCellRecord cell, StructuralIntegrity integrity) {
        int roll = Math.floorMod(Long.hashCode(cell.position().asLong()), 7);
        if (integrity == StructuralIntegrity.RUINED && roll <= 2) return Blocks.AIR.defaultBlockState();
        if (roll == 3) return Blocks.COBWEB.defaultBlockState();
        if (cell.baselineState().is(Blocks.STONE_BRICKS)) return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        return integrity == StructuralIntegrity.RUINED ? Blocks.COBBLESTONE.defaultBlockState() : cell.baselineState();
    }

    private static boolean expansion(ServerLevel level, PaleMirrorSavedData data, DomainCommandExecutor commands,
                                     DevelopmentIntent intent) {
        ParcelRecord parcel = data.parcels().parcels().stream().filter(value -> value.bindingId().equals(intent.targetSiteId().value()))
                .findFirst().orElse(null);
        if (parcel == null) { commands.execute(data.worldState(), new DomainCommand.BlockDevelopmentIntent(
                intent.id(), "Development plot has no parcel")); return true; }
        if (parcel.kind() == ParcelKind.RESERVED) {
            if (!allChunksLoaded(level, parcel.min(), parcel.max())) return false;
            if (!plotAvailable(level, parcel)) {
                commands.execute(data.worldState(), new DomainCommand.BlockDevelopmentIntent(intent.id(), "Reserved plot is occupied"));
                return true;
            }
            parcel.commissionCommunity("development-intent:" + intent.id());
        }
        List<SemanticSlotKey> slots = ensureExpansionSlots(level, data, intent, parcel);
        if (slots.isEmpty()) return false;
        MaterializationJob job = ensureJob(data, intent.targetSiteId().value(), DEVELOPMENT_CHANNEL,
                MaterializationJobClass.CAPABILITY, 1, "pale_mirror:storehouse_annex", slots);
        boolean changed = runOne(level, data, job, cell -> expansionBlock(cell.position(), parcel));
        if (job.state() == JobState.BLOCKED) commands.execute(data.worldState(),
                new DomainCommand.BlockDevelopmentIntent(intent.id(), job.lastError()));
        else if (job.state() == JobState.COMPLETED) commands.execute(data.worldState(),
                new DomainCommand.CompleteDevelopmentIntent(intent.id()));
        return changed;
    }

    private static List<SemanticSlotKey> ensureExpansionSlots(ServerLevel level, PaleMirrorSavedData data,
                                                               DevelopmentIntent intent, ParcelRecord parcel) {
        int centerX = (parcel.min().getX() + parcel.max().getX()) / 2;
        int centerZ = (parcel.min().getZ() + parcel.max().getZ()) / 2;
        int baseY = parcel.min().getY() + 2;
        List<SemanticSlotKey> keys = new ArrayList<>();
        for (String layer : List.of("foundation", "shell", "roof")) {
            SemanticSlotKey key = new SemanticSlotKey(intent.targetSiteId().value(), "storehouse_annex", layer); keys.add(key);
            if (data.semanticSlots().find(key).isPresent()) continue;
            List<SemanticCellRecord> cells = new ArrayList<>();
            for (int x = centerX - 3; x <= centerX + 3; x++) for (int z = centerZ - 3; z <= centerZ + 3; z++) {
                if (layer.equals("foundation")) add(level, cells, new BlockPos(x, baseY, z));
                else if (layer.equals("roof")) add(level, cells, new BlockPos(x, baseY + 4, z));
                else if (x == centerX - 3 || x == centerX + 3 || z == centerZ - 3 || z == centerZ + 3) {
                    for (int y = baseY + 1; y <= baseY + 3; y++) add(level, cells, new BlockPos(x, y, z));
                }
            }
            SemanticSlotRegistration.register(data.semanticSlots(), data.parcels(), key, parcel.id(), parcel.dimensionId(),
                    ParcelKind.COMMUNITY, cells);
        }
        return List.copyOf(keys);
    }

    private static BlockState expansionBlock(BlockPos position, ParcelRecord parcel) {
        int base = parcel.min().getY() + 2;
        if (position.getY() == base) return Blocks.STONE_BRICKS.defaultBlockState();
        if (position.getY() == base + 4) return Blocks.SPRUCE_SLAB.defaultBlockState();
        int centerX = (parcel.min().getX() + parcel.max().getX()) / 2;
        int centerZ = (parcel.min().getZ() + parcel.max().getZ()) / 2;
        if (position.getZ() == centerZ - 3 && position.getX() == centerX && position.getY() <= base + 2) {
            return Blocks.AIR.defaultBlockState();
        }
        return Blocks.SPRUCE_PLANKS.defaultBlockState();
    }

    private static MaterializationJob ensureJob(PaleMirrorSavedData data, String target, String channel,
                                                MaterializationJobClass jobClass, long revision, String policy,
                                                List<SemanticSlotKey> slots) {
        MaterializationJob current = data.materializationJobs().activeFor(target, channel).orElse(null);
        if (current != null && current.isFor(revision, policy, VERSION)) return current;
        if (current != null) current.cancel("Superseded by " + policy);
        String id = "pm:job:" + channel + ":" + Integer.toUnsignedString(target.hashCode(), 36) + ":" + revision;
        List<MaterializationOperation> operations = slots.stream().map(slot -> new MaterializationOperation(
                id + ":" + slot.slotId(), id + ":" + slot.value(), MaterializationOperationType.APPLY_SEMANTIC_SLOT,
                slot.value(), OperationState.PENDING, 0, "")).toList();
        MaterializationJob job = new MaterializationJob(id, target, channel, jobClass, revision, policy, VERSION,
                JobState.PLANNED, operations, 0, 0, ""); data.materializationJobs().put(job); return job;
    }

    private static boolean runOne(ServerLevel level, PaleMirrorSavedData data, MaterializationJob job,
                                  Function<SemanticCellRecord, BlockState> desired) {
        if (job.state() == JobState.PLANNED || job.state() == JobState.BLOCKED) { job.start(); return true; }
        if (job.state() != JobState.RUNNING) return false;
        MaterializationOperation operation = job.nextOperation();
        if (operation == null) { job.complete(); return true; }
        var key = data.semanticSlots().slots().stream().map(SemanticSlotRecord::key)
                .filter(value -> value.value().equals(operation.target())).findFirst().orElse(null);
        if (key == null) { job.block("Missing semantic slot " + operation.target()); return true; }
        operation.start(); MaterializationGateway gateway = new MaterializationGateway(level, data.semanticSlots(), data.parcels());
        for (SemanticCellRecord cell : data.semanticSlots().find(key).orElseThrow().cells()) {
            var result = gateway.setBlock(key, cell.position(), desired.apply(cell), 3);
            if (result.status() == io.farfrontier.palemirror.api.GuardedWorldAccess.Status.BLOCKED) {
                operation.block(result.diagnostic()); job.block(result.diagnostic()); return true;
            }
        }
        gateway.completeReset(key); operation.complete(); job.advanceOperation();
        if (job.nextOperation() == null) job.complete(); return true;
    }

    private static boolean plotAvailable(ServerLevel level, ParcelRecord parcel) {
        int baseY = parcel.min().getY() + 2;
        int centerX = (parcel.min().getX() + parcel.max().getX()) / 2;
        int centerZ = (parcel.min().getZ() + parcel.max().getZ()) / 2;
        for (int x = centerX - 3; x <= centerX + 3; x++) for (int z = centerZ - 3; z <= centerZ + 3; z++) {
            for (int y = baseY + 1; y <= baseY + 4; y++) if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) return false;
        }
        return true;
    }
    private static void add(ServerLevel level, List<SemanticCellRecord> cells, BlockPos pos) {
        BlockState state = level.getBlockState(pos); cells.add(new SemanticCellRecord(pos, state, state));
    }
    private static boolean allChunksLoaded(ServerLevel level, BlockPos min, BlockPos max) {
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) {
            if (!level.hasChunk(x, z)) return false;
        }
        return true;
    }
    private static BlockPos block(io.farfrontier.palemirror.api.VisualPoint point) { return new BlockPos(point.x(), point.y(), point.z()); }
    private static DevelopmentIntent intent(PaleMirrorSavedData data, io.farfrontier.palemirror.domain.WorldObjectId community,
                                            DevelopmentIntentType type) {
        return data.worldState().developmentIntents().stream().filter(value -> value.communityId().equals(community)
                && value.type() == type && value.state() != DevelopmentIntentState.CANCELLED).findFirst().orElse(null);
    }
    private static ServerLevel level(MinecraftServer server, String id) {
        for (ServerLevel value : server.getAllLevels()) if (value.dimension().location().toString().equals(id)) return value;
        return null;
    }
    private record StructuralSlots(List<SemanticSlotKey> keys, boolean complete) { }
}
