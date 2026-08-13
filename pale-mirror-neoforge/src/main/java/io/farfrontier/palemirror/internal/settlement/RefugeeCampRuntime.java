package io.farfrontier.palemirror.internal.settlement;

import io.farfrontier.palemirror.api.ParcelKind;
import io.farfrontier.palemirror.api.SemanticSlotKey;
import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandExecutor;
import io.farfrontier.palemirror.domain.OperationalState;
import io.farfrontier.palemirror.domain.PopulationDisposition;
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
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Capability materialization for the selected shelter. Residents are projected by WorldJourney, never invented here. */
public final class RefugeeCampRuntime {
    private static final String CHANNEL = "shelter";
    private static final String POLICY = "pale_mirror:shelter_camp";
    private static final String VERSION = "v39-frontier-camp-1";

    private RefugeeCampRuntime() { }

    /** v35 camps never fabricate representative actors; retained as a narrow call-site compatibility predicate. */
    public static boolean isRepresentative(net.minecraft.world.entity.Entity entity) { return false; }

    public static boolean tick(MinecraftServer server, PaleMirrorSavedData data, DomainCommandExecutor commands) {
        boolean changed = false;
        for (var group : data.worldState().populationGroups()) {
            RefugeeCampRecord camp = data.refugeeCamps().get(group.id());
            if (camp == null && group.disposition() == PopulationDisposition.IN_TRANSIT && group.journeyId() != null) {
                var journey = data.worldState().journey(group.journeyId()).orElse(null);
                var path = journey == null ? null : data.worldState().worldPath(journey.pathId()).orElse(null);
                if (journey == null || path == null) continue;
                var end = path.nodes().getLast();
                ServerLevel level = level(server, end.dimensionId());
                BlockPos anchor = new BlockPos(end.x(), end.y(), end.z());
                if (level == null || !level.hasChunkAt(anchor)) continue;
                camp = createRecord(data, group.id(), group.communityId(), journey.destinationSiteId(), level, anchor);
                data.refugeeCamps().put(group.id(), camp);
                changed = true;
                continue;
            }
            if (camp == null) continue;
            MaterializationJob job = ensureJob(data, camp);
            if (job.state() == JobState.PLANNED) { job.start(); changed = true; continue; }
            if (job.state() != JobState.RUNNING && job.state() != JobState.BLOCKED) continue;
            ServerLevel level = level(server, camp.dimensionId());
            if (level == null || !level.hasChunkAt(camp.anchor())) continue;
            if (job.state() == JobState.BLOCKED) job.start();
            String failure = execute(level, data, camp, job);
            if (failure == null) {
                job.complete();
                commands.execute(data.worldState(), new DomainCommand.SetWorldSiteOperational(camp.siteId(),
                        OperationalState.OPERATIONAL, "materialization:" + job.jobId()));
            } else job.block(failure);
            changed = true;
        }
        return changed;
    }

    public static RefugeeCampRecord createRecord(PaleMirrorSavedData data, String groupId,
                                                  io.farfrontier.palemirror.domain.WorldObjectId communityId,
                                                  io.farfrontier.palemirror.domain.WorldObjectId siteId,
                                                  ServerLevel level, BlockPos anchor) {
        ParcelRecord parcel = data.parcels().reservedAt(level.dimension().location().toString(), anchor).orElse(null);
        if (parcel != null) parcel.commissionCommunity("shelter-selection:" + siteId.value());
        if (parcel == null) parcel = data.parcels().managedAt(
                level.dimension().location().toString(), anchor).orElse(null);
        if (parcel == null) {
            BlockPos min = anchor.offset(-8, -2, -8); BlockPos max = anchor.offset(8, 12, 8);
            parcel = new ParcelRecord(siteId.value() + ":parcel", siteId.value(),
                    level.dimension().location().toString(), min, max, "prepared_shelter", ParcelKind.COMMUNITY,
                    null, 1, "prepared-site:" + siteId.value());
            data.parcels().register(parcel);
        }
        String parcelId = parcel.id();
        SemanticSlotKey key = new SemanticSlotKey(siteId.value(), "camp", "footprint");
        if (data.semanticSlots().find(key).isEmpty()) {
            SemanticSlotRegistration.register(data.semanticSlots(), data.parcels(), key, parcelId,
                    level.dimension().location().toString(), ParcelKind.COMMUNITY, captureCells(level, anchor));
        }
        return new RefugeeCampRecord(groupId, communityId, siteId,
                level.dimension().location().toString(), anchor, key);
    }

    private static MaterializationJob ensureJob(PaleMirrorSavedData data, RefugeeCampRecord camp) {
        MaterializationJob existing = data.materializationJobs().activeFor(camp.siteId().value(), CHANNEL).orElse(null);
        if (existing != null) return existing;
        String id = "pm:job:shelter:" + Integer.toUnsignedString(camp.siteId().value().hashCode(), 36) + ":1";
        var operation = new MaterializationOperation(id + ":footprint", id + ":footprint",
                MaterializationOperationType.APPLY_SEMANTIC_SLOT, camp.semanticSlot().value(),
                OperationState.PENDING, 0, "");
        MaterializationJob job = new MaterializationJob(id, camp.siteId().value(), CHANNEL,
                MaterializationJobClass.CAPABILITY, 1, POLICY, VERSION, JobState.PLANNED,
                List.of(operation), 0, 0, "");
        data.materializationJobs().put(job);
        return job;
    }

    private static String execute(ServerLevel level, PaleMirrorSavedData data, RefugeeCampRecord camp,
                                  MaterializationJob job) {
        MaterializationOperation operation = job.nextOperation();
        if (operation == null) return null;
        operation.start();
        MaterializationGateway gateway = new MaterializationGateway(level, data.semanticSlots(), data.parcels());
        Map<BlockPos, BlockState> blueprint = campBlueprint(camp.anchor());
        for (SemanticCellRecord cell : data.semanticSlots().find(camp.semanticSlot()).orElseThrow().cells()) {
            BlockState desired = blueprint.get(cell.position());
            if (desired == null) return "Camp blueprint lost semantic cell " + cell.position();
            var result = gateway.setBlock(camp.semanticSlot(), cell.position(), desired, 3);
            if (result.status() == io.farfrontier.palemirror.api.GuardedWorldAccess.Status.BLOCKED) {
                operation.block(result.diagnostic()); return result.diagnostic();
            }
        }
        gateway.completeReset(camp.semanticSlot()); operation.complete(); job.advanceOperation();
        return null;
    }

    public static boolean cleanupReturnedGroups(MinecraftServer server, PaleMirrorSavedData data,
                                                DomainCommandExecutor commands) {
        boolean changed = false;
        for (RefugeeCampRecord camp : data.refugeeCamps().values()) {
            var group = data.worldState().populationGroup(camp.populationGroupId()).orElse(null);
            var site = data.worldState().site(camp.siteId()).orElse(null);
            if (group != null && group.disposition() == PopulationDisposition.RESIDENT && site != null
                    && site.operationalState() != OperationalState.OFFLINE) {
                commands.execute(data.worldState(), new DomainCommand.SetWorldSiteOperational(camp.siteId(),
                        OperationalState.OFFLINE, "policy:population-returned"));
                changed = true;
            }
        }
        return changed;
    }

    public static boolean safeAnchor(ServerLevel level, BlockPos anchor) {
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
            BlockPos pos = anchor.offset(x, 0, z);
            if (!level.hasChunkAt(pos) || !level.getBlockState(pos.below()).isSolid()
                    || !level.isEmptyBlock(pos) || !level.isEmptyBlock(pos.above())
                    || level.getBlockEntity(pos) != null) return false;
        }
        return true;
    }

    private static List<SemanticCellRecord> captureCells(ServerLevel level, BlockPos anchor) {
        List<SemanticCellRecord> cells = new ArrayList<>();
        campBlueprint(anchor).keySet().forEach(pos -> add(level, cells, pos));
        return List.copyOf(cells);
    }

    private static void add(ServerLevel level, List<SemanticCellRecord> cells, BlockPos pos) {
        var baseline = level.getBlockState(pos);
        cells.add(new SemanticCellRecord(pos, baseline, baseline));
    }

    private static Map<BlockPos, BlockState> campBlueprint(BlockPos anchor) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (int offset = -8; offset <= 8; offset++) {
            blocks.put(anchor.offset(offset, 0, 0), Blocks.COARSE_DIRT.defaultBlockState());
            blocks.put(anchor.offset(0, 0, offset), Blocks.COARSE_DIRT.defaultBlockState());
        }
        tent(blocks, anchor.offset(-5, 0, -5), Blocks.WHITE_WOOL.defaultBlockState());
        tent(blocks, anchor.offset(5, 0, -5), Blocks.LIGHT_GRAY_WOOL.defaultBlockState());
        tent(blocks, anchor.offset(-5, 0, 5), Blocks.BROWN_WOOL.defaultBlockState());
        tent(blocks, anchor.offset(5, 0, 5), Blocks.WHITE_WOOL.defaultBlockState());
        blocks.put(anchor.above(), Blocks.CAMPFIRE.defaultBlockState());
        blocks.put(anchor.offset(2, 1, 0), Blocks.CAULDRON.defaultBlockState());
        blocks.put(anchor.offset(-2, 1, 0), Blocks.HAY_BLOCK.defaultBlockState());
        for (int x = -3; x <= 3; x++) {
            blocks.put(anchor.offset(x, 1, -8), Blocks.SPRUCE_FENCE.defaultBlockState());
            blocks.put(anchor.offset(x, 3, -8), Blocks.SPRUCE_SLAB.defaultBlockState());
        }
        for (int x : new int[]{-3, 3}) for (int y = 1; y <= 3; y++) {
            blocks.put(anchor.offset(x, y, -8), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
        }
        for (int x : new int[]{-8, 8}) for (int z : new int[]{-8, 8}) {
            blocks.put(anchor.offset(x, 1, z), Blocks.SPRUCE_FENCE.defaultBlockState());
            blocks.put(anchor.offset(x, 2, z), Blocks.SPRUCE_FENCE.defaultBlockState());
            blocks.put(anchor.offset(x, 3, z), Blocks.LANTERN.defaultBlockState());
        }
        return Map.copyOf(blocks);
    }

    private static void tent(Map<BlockPos, BlockState> blocks, BlockPos center, BlockState cloth) {
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            blocks.put(center.offset(x, 0, z), Blocks.COARSE_DIRT.defaultBlockState());
            boolean edge = Math.abs(x) == 2 || Math.abs(z) == 2;
            if (edge && !(z == -2 && x == 0)) blocks.put(center.offset(x, 1, z), cloth);
            if (Math.abs(x) <= 1) blocks.put(center.offset(x, 3, z), cloth);
            else blocks.put(center.offset(x, 2, z), cloth);
        }
        blocks.put(center.offset(-1, 1, 0), Blocks.RED_CARPET.defaultBlockState());
        blocks.put(center.offset(-1, 1, 1), Blocks.RED_CARPET.defaultBlockState());
        blocks.put(center.offset(1, 1, 0), Blocks.BLUE_CARPET.defaultBlockState());
        blocks.put(center.offset(1, 1, 1), Blocks.BLUE_CARPET.defaultBlockState());
    }

    private static ServerLevel level(MinecraftServer server, String dimensionId) {
        for (ServerLevel level : server.getAllLevels()) if (level.dimension().location().toString().equals(dimensionId)) return level;
        return null;
    }
}
