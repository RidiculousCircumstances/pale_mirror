package io.farfrontier.palemirror.internal.settlement;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.StagedVisualModule;
import io.farfrontier.palemirror.api.VisualBlockPlacement;
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
import net.minecraft.world.level.block.state.BlockState;

/** Staged damage, pristine reconstruction and reserved-plot development through the shared gateway. */
final class AuthoredSettlementProjectRuntime {
    private static final String STRUCTURAL_CHANNEL = "settlement_structure";
    private static final String DEVELOPMENT_CHANNEL = "settlement_development";
    private static final String VERSION = "authored-project-v39-frontier-art-1";
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
                Map<Long, BlockState> authoredState = authoredState(provider, seed, desired);
                changed |= structural(level, data, commands, region.placeId().value(), desired, reconstruction,
                        structuralSlots, authoredState);
            }
            DevelopmentIntent expansion = intent(data, region.communityId(), DevelopmentIntentType.UPGRADE_STOREHOUSE);
            if (expansion != null && expansion.state() == DevelopmentIntentState.MATERIALIZING) {
                changed |= expansion(level, data, commands, provider, seed, expansion);
            }
            DevelopmentIntent dispatch = intent(data, region.communityId(),
                    DevelopmentIntentType.COMMISSION_ALTERNATE_DISPATCH);
            if (dispatch != null && dispatch.state() == DevelopmentIntentState.MATERIALIZING) {
                changed |= alternateDispatch(level, data, commands, provider, seed, dispatch);
            }
        }
        return changed;
    }

    private static boolean alternateDispatch(ServerLevel level, PaleMirrorSavedData data,
                                               DomainCommandExecutor commands,
                                               io.farfrontier.palemirror.api.VisualProvider provider,
                                               AuthoredRegionSeed seed, DevelopmentIntent intent) {
        var stages = seed.alternateMineSite().stagedModules();
        if (stages.isEmpty()) {
            commands.execute(data.worldState(), new DomainCommand.BlockDevelopmentIntent(intent.id(),
                    "Alternate MineSite has no staged dispatch blueprint"));
            return true;
        }
        for (int index = 0; index < stages.size(); index++) {
            StagedVisualModule stage = stages.get(index);
            String channel = "alternate_dispatch_stage_" + index;
            String binding = AuthoredBlueprintSlots.binding(seed, stage);
            ParcelRecord parcel = data.parcels().parcels().stream()
                    .filter(value -> value.bindingId().equals(binding)).findFirst().orElse(null);
            if (parcel == null) {
                commands.execute(data.worldState(), new DomainCommand.BlockDevelopmentIntent(intent.id(),
                        "Missing reserved parcel for " + stage.stage()));
                return true;
            }
            if (!allChunksLoaded(level, parcel.min(), parcel.max())) return false;
            var snapshot = provider.compileAuthoredModule(stage).orElse(null);
            if (snapshot == null) {
                commands.execute(data.worldState(), new DomainCommand.BlockDevelopmentIntent(intent.id(),
                        "Cannot compile authored stage " + stage.stage()));
                return true;
            }
            List<SemanticSlotKey> slots = AuthoredBlueprintSlots.keys(data, seed, stage);
            int expectedParts = (snapshot.blocks().size() + 1_023) / 1_024;
            if (slots.size() != expectedParts) return false;
            if (parcel.kind() == ParcelKind.RESERVED) {
                parcel.commissionCommunity("development-intent:" + intent.id());
                slots.forEach(key -> data.semanticSlots().find(key).orElseThrow().commissionCommunity());
            }
            MaterializationJob job = ensureJob(data, intent.targetSiteId().value(), channel,
                    MaterializationJobClass.CAPABILITY, index + 1L,
                    "pale_mirror:alternate_dispatch_" + stage.stage(), slots);
            Map<Long, BlockState> desired = snapshot.blocks().stream().collect(Collectors.toMap(
                    value -> block(value.position()).asLong(), VisualBlockPlacement::state, (left, right) -> right));
            boolean changed = runOne(level, data, job, cell -> desired.getOrDefault(
                    cell.position().asLong(), cell.lastAppliedState()));
            if (job.state() == JobState.BLOCKED) {
                commands.execute(data.worldState(), new DomainCommand.BlockDevelopmentIntent(intent.id(), job.lastError()));
                return true;
            }
            if (job.state() != JobState.COMPLETED) return changed;
            advanceFutureStagePreconditions(data, seed, stages, index, desired);
        }
        commands.execute(data.worldState(), new DomainCommand.CompleteDevelopmentIntent(intent.id()));
        return true;
    }

    /**
     * A later blueprint may deliberately replace cells written by an earlier stage. Transfer only the exact
     * PM-authored postcondition; never bless the currently observed world state, which may contain a player edit.
     */
    private static void advanceFutureStagePreconditions(PaleMirrorSavedData data, AuthoredRegionSeed seed,
                                                         List<StagedVisualModule> stages, int completedIndex,
                                                         Map<Long, BlockState> completedDesired) {
        for (int index = completedIndex + 1; index < stages.size(); index++) {
            for (SemanticSlotKey key : AuthoredBlueprintSlots.keys(data, seed, stages.get(index))) {
                SemanticSlotRecord slot = data.semanticSlots().find(key).orElseThrow();
                for (SemanticCellRecord cell : slot.cells()) {
                    BlockState expected = completedDesired.get(cell.position().asLong());
                    if (expected != null) cell.applied(expected);
                }
            }
        }
    }

    private static boolean structural(ServerLevel level, PaleMirrorSavedData data, DomainCommandExecutor commands,
                                      String placeId, StructuralIntegrity desired,
                                      DevelopmentIntent reconstruction, StructuralSlots structuralSlots,
                                      Map<Long, BlockState> authoredState) {
        if (!structuralSlots.complete()) return false;
        List<SemanticSlotKey> slots = structuralSlots.keys();
        String policy = "pale_mirror:settlement_" + desired.name().toLowerCase(java.util.Locale.ROOT);
        MaterializationJob job = ensureJob(data, placeId, STRUCTURAL_CHANNEL, MaterializationJobClass.PRESENTATION,
                desired.ordinal() + (reconstruction == null ? 0 : 10), policy, slots);
        boolean changed = runOne(level, data, job, desired == StructuralIntegrity.INTACT
                ? SemanticCellRecord::baselineState
                : cell -> authoredState.getOrDefault(cell.position().asLong(), cell.baselineState()));
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
            SemanticSlotKey key = new SemanticSlotKey(placeId, module.instanceId(), "authored_state_overlay");
            keys.add(key);
            if (data.semanticSlots().find(key).isPresent()) continue;
            if (!provider.authoredModuleReady(level, seed, module)
                    || !allChunksLoaded(level, block(module.footprint().min()), block(module.footprint().max()))) {
                complete = false;
                continue;
            }
            var state = provider.compileAuthoredModuleState(module, "RUINED").orElse(null);
            if (state == null) { complete = false; continue; }
            List<SemanticCellRecord> cells = stateCells(level, state);
            if (cells.isEmpty()) { complete = false; continue; }
            SemanticSlotRegistration.register(data.semanticSlots(), data.parcels(), key,
                    seed.planId() + ":parcel:module_" + index, seed.dimensionId(),
                    ParcelKind.COMMUNITY, cells);
        }
        return new StructuralSlots(List.copyOf(keys), complete
                && keys.stream().allMatch(key -> data.semanticSlots().find(key).isPresent()));
    }

    private static List<SemanticCellRecord> stateCells(ServerLevel level,
                                                        io.farfrontier.palemirror.api.VisualModuleSnapshot state) {
        List<SemanticCellRecord> cells = new ArrayList<>();
        for (VisualBlockPlacement placement : state.blocks()) {
            BlockPos position = block(placement.position());
            BlockState baseline = level.getBlockState(position);
            if (level.getBlockEntity(position) == null && baseline.getDestroySpeed(level, position) >= 0) {
                cells.add(new SemanticCellRecord(position, baseline, baseline));
            }
        }
        return List.copyOf(cells);
    }

    private static Map<Long, BlockState> authoredState(io.farfrontier.palemirror.api.VisualProvider provider,
                                                        AuthoredRegionSeed seed, StructuralIntegrity state) {
        if (state == StructuralIntegrity.INTACT) return Map.of();
        String name = state == StructuralIntegrity.RUINED ? "RUINED" : "DAMAGED";
        Map<Long, BlockState> result = new java.util.LinkedHashMap<>();
        for (var module : seed.modules()) {
            if (!(module.role().equals("CIVIC") || module.role().equals("DEFENCE")
                    || module.role().equals("LOGISTICS") || module.role().equals("ECONOMY"))) continue;
            provider.compileAuthoredModuleState(module, name).ifPresent(snapshot -> snapshot.blocks().forEach(
                    placement -> result.put(block(placement.position()).asLong(), placement.state())));
        }
        return java.util.Map.copyOf(result);
    }

    private static boolean expansion(ServerLevel level, PaleMirrorSavedData data, DomainCommandExecutor commands,
                                     io.farfrontier.palemirror.api.VisualProvider provider,
                                     AuthoredRegionSeed seed, DevelopmentIntent intent) {
        ParcelRecord parcel = data.parcels().parcels().stream().filter(value -> value.bindingId().equals(intent.targetSiteId().value()))
                .findFirst().orElse(null);
        if (parcel == null) { commands.execute(data.worldState(), new DomainCommand.BlockDevelopmentIntent(
                intent.id(), "Development plot has no parcel")); return true; }
        var module = developmentModule(seed, parcel);
        var snapshot = provider.compileAuthoredModule(new StagedVisualModule("development", module)).orElse(null);
        if (snapshot == null) {
            commands.execute(data.worldState(), new DomainCommand.BlockDevelopmentIntent(intent.id(),
                    "Development module could not be compiled"));
            return true;
        }
        if (parcel.kind() == ParcelKind.RESERVED) {
            if (!allChunksLoaded(level, parcel.min(), parcel.max())) return false;
            if (!plotAvailable(level, module.footprint())) {
                commands.execute(data.worldState(), new DomainCommand.BlockDevelopmentIntent(intent.id(), "Reserved plot is occupied"));
                return true;
            }
            parcel.commissionCommunity("development-intent:" + intent.id());
        }
        List<SemanticSlotKey> slots = ensureExpansionSlots(level, data, intent, parcel, snapshot);
        if (slots.isEmpty()) return false;
        MaterializationJob job = ensureJob(data, intent.targetSiteId().value(), DEVELOPMENT_CHANNEL,
                MaterializationJobClass.CAPABILITY, 1, "pale_mirror:storehouse_annex", slots);
        Map<Long, BlockState> desired = snapshot.blocks().stream().collect(Collectors.toMap(
                value -> block(value.position()).asLong(), VisualBlockPlacement::state, (left, right) -> right));
        boolean changed = runOne(level, data, job,
                cell -> desired.getOrDefault(cell.position().asLong(), cell.baselineState()));
        if (job.state() == JobState.BLOCKED) commands.execute(data.worldState(),
                new DomainCommand.BlockDevelopmentIntent(intent.id(), job.lastError()));
        else if (job.state() == JobState.COMPLETED) commands.execute(data.worldState(),
                new DomainCommand.CompleteDevelopmentIntent(intent.id()));
        return changed;
    }

    private static List<SemanticSlotKey> ensureExpansionSlots(ServerLevel level, PaleMirrorSavedData data,
                                                               DevelopmentIntent intent, ParcelRecord parcel,
                                                               io.farfrontier.palemirror.api.VisualModuleSnapshot snapshot) {
        SemanticSlotKey key = new SemanticSlotKey(intent.targetSiteId().value(),
                "frontier_storehouse_annex", "authored_building");
        if (data.semanticSlots().find(key).isEmpty()) {
            List<SemanticCellRecord> cells = snapshot.blocks().stream().map(value -> {
                BlockPos position = block(value.position());
                BlockState baseline = level.getBlockState(position);
                return new SemanticCellRecord(position, baseline, baseline);
            }).toList();
            SemanticSlotRegistration.register(data.semanticSlots(), data.parcels(), key, parcel.id(),
                    parcel.dimensionId(), ParcelKind.COMMUNITY, cells);
        }
        return List.of(key);
    }

    private static io.farfrontier.palemirror.api.VisualModulePlacement developmentModule(
            AuthoredRegionSeed seed, ParcelRecord parcel) {
        int centerX = (parcel.min().getX() + parcel.max().getX()) / 2;
        int centerZ = (parcel.min().getZ() + parcel.max().getZ()) / 2;
        int groundY = parcel.min().getY() + 2;
        var origin = new io.farfrontier.palemirror.api.VisualPoint(centerX, groundY, centerZ);
        var footprint = new io.farfrontier.palemirror.api.VisualBounds(
                new io.farfrontier.palemirror.api.VisualPoint(centerX - 5, groundY + 1, centerZ - 4),
                new io.farfrontier.palemirror.api.VisualPoint(centerX + 4, groundY + 7, centerZ + 3));
        String family = seed.climate().equals("dry_arid") ? "temperate" : seed.climate();
        var entrance = new io.farfrontier.palemirror.api.VisualPoint(centerX, groundY + 1, centerZ - 4);
        return new io.farfrontier.palemirror.api.VisualModulePlacement("development_storehouse",
                "pale_mirror_visuals:" + family + "/workshop_1", seed.climate(), "LOGISTICS", origin, 0,
                footprint, "development_foundation", "frontier_freight",
                List.of(new io.farfrontier.palemirror.api.VisualPort("public",
                        io.farfrontier.palemirror.api.VisualPortKind.PUBLIC_ENTRANCE, entrance, 3)));
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

    private static boolean plotAvailable(ServerLevel level, io.farfrontier.palemirror.api.VisualBounds footprint) {
        for (int x = footprint.min().x(); x <= footprint.max().x(); x++) {
            for (int z = footprint.min().z(); z <= footprint.max().z(); z++) {
                for (int y = footprint.min().y(); y <= footprint.max().y(); y++) {
                    if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) return false;
                }
            }
        }
        return true;
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
