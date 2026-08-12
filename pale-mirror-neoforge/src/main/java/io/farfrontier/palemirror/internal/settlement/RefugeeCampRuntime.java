package io.farfrontier.palemirror.internal.settlement;

import io.farfrontier.palemirror.api.ParcelKind;
import io.farfrontier.palemirror.api.SemanticSlotKey;
import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandProcessor;
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
import io.farfrontier.palemirror.internal.materialization.SemanticSlotRecord;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Capability materialization for the selected shelter. Residents are projected by WorldJourney, never invented here. */
public final class RefugeeCampRuntime {
    private static final String CHANNEL = "shelter";
    private static final String POLICY = "pale_mirror:shelter_camp";
    private static final String VERSION = "v34-1";

    private RefugeeCampRuntime() { }

    /** v34 camps never fabricate representative actors; retained as a narrow call-site compatibility predicate. */
    public static boolean isRepresentative(net.minecraft.world.entity.Entity entity) { return false; }

    public static boolean tick(MinecraftServer server, PaleMirrorSavedData data, DomainCommandProcessor commands) {
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
        var parcel = data.parcels().reservedAt(level.dimension().location().toString(), anchor).orElse(null);
        if (parcel != null) parcel.commissionCommunity("shelter-selection:" + siteId.value());
        if (data.parcels().managedAt(level.dimension().location().toString(), anchor).isEmpty()) {
            BlockPos min = anchor.offset(-8, -2, -8); BlockPos max = anchor.offset(8, 12, 8);
            data.parcels().register(new ParcelRecord(siteId.value() + ":parcel", siteId.value(),
                    level.dimension().location().toString(), min, max, "prepared_shelter", ParcelKind.COMMUNITY,
                    null, 1, "prepared-site:" + siteId.value()));
        }
        SemanticSlotKey key = new SemanticSlotKey(siteId.value(), "camp", "footprint");
        if (data.semanticSlots().find(key).isEmpty()) {
            data.semanticSlots().register(new SemanticSlotRecord(key, ParcelKind.COMMUNITY,
                    captureCells(level, anchor), false, "", ""));
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
        for (SemanticCellRecord cell : data.semanticSlots().find(camp.semanticSlot()).orElseThrow().cells()) {
            var result = gateway.setBlock(camp.semanticSlot(), cell.position(), desired(camp.anchor(), cell.position()).defaultBlockState(), 3);
            if (result.status() == io.farfrontier.palemirror.api.GuardedWorldAccess.Status.BLOCKED) {
                operation.block(result.diagnostic()); return result.diagnostic();
            }
        }
        gateway.completeReset(camp.semanticSlot()); operation.complete(); job.advanceOperation();
        return null;
    }

    public static boolean cleanupReturnedGroups(MinecraftServer server, PaleMirrorSavedData data,
                                                DomainCommandProcessor commands) {
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
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            BlockPos pos = anchor.offset(x, 0, z);
            if (!level.hasChunkAt(pos) || !level.getBlockState(pos.below()).isSolid()
                    || !level.isEmptyBlock(pos) || !level.isEmptyBlock(pos.above())
                    || level.getBlockEntity(pos) != null) return false;
        }
        return true;
    }

    private static List<SemanticCellRecord> captureCells(ServerLevel level, BlockPos anchor) {
        List<SemanticCellRecord> cells = new ArrayList<>();
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            if (Math.abs(x) == 3 || Math.abs(z) == 3 || x == 0 || z == 0) add(level, cells, anchor.offset(x, 0, z));
        }
        for (BlockPos pos : List.of(anchor.above(), anchor.offset(-2, 1, -2), anchor.offset(2, 1, 2))) add(level, cells, pos);
        return List.copyOf(cells);
    }

    private static void add(ServerLevel level, List<SemanticCellRecord> cells, BlockPos pos) {
        var baseline = level.getBlockState(pos);
        cells.add(new SemanticCellRecord(pos, baseline, baseline));
    }

    private static Block desired(BlockPos anchor, BlockPos pos) {
        if (pos.equals(anchor.above())) return Blocks.CAMPFIRE;
        if (pos.getY() > anchor.getY()) return Blocks.WHITE_WOOL;
        return Blocks.COARSE_DIRT;
    }

    private static ServerLevel level(MinecraftServer server, String dimensionId) {
        for (ServerLevel level : server.getAllLevels()) if (level.dimension().location().toString().equals(dimensionId)) return level;
        return null;
    }
}
