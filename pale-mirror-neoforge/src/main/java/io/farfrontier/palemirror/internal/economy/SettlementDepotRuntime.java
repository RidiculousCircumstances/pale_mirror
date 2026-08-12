package io.farfrontier.palemirror.internal.economy;

import io.farfrontier.palemirror.api.ParcelKind;
import io.farfrontier.palemirror.api.SemanticSlotKey;
import io.farfrontier.palemirror.domain.DevelopmentIntentState;
import io.farfrontier.palemirror.domain.DevelopmentIntentType;
import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandExecutor;
import io.farfrontier.palemirror.domain.OperationalState;
import io.farfrontier.palemirror.domain.ResourceKind;
import io.farfrontier.palemirror.domain.SiteAffiliation;
import io.farfrontier.palemirror.domain.SiteAffiliationRole;
import io.farfrontier.palemirror.domain.SiteCapability;
import io.farfrontier.palemirror.domain.SiteCapabilityType;
import io.farfrontier.palemirror.domain.StructuralIntegrity;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.domain.WorldSite;
import io.farfrontier.palemirror.domain.WorldSiteType;
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
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Shared-job depot executor. It never loads a chunk and never stores canonical stock in a container. */
public final class SettlementDepotRuntime {
    public static final String TEMPLATE_VERSION = "supply-depot-v35";
    public static final String CHANNEL = "settlement_depot";
    private SettlementDepotRuntime() { }

    public static boolean tick(MinecraftServer server, PaleMirrorSavedData data, DomainCommandExecutor commands) {
        boolean changed = false;
        for (var region : data.worldState().livingRegions().stream().sorted(Comparator.comparing(value -> value.id())).toList()) {
            SettlementDepotRecord depot = data.settlementDepots().get(region.communityId());
            if (depot == null) {
                var place = data.worldRegistry().find(region.placeId()).orElse(null);
                var presentation = data.campaignRegions().get(region.id());
                if (place == null || presentation == null || presentation.depotAnchor() == null) continue;
                ServerLevel level = level(server, place.dimensionId()); BlockPos anchor = presentation.depotAnchor();
                if (level == null || !level.hasChunkAt(anchor)) continue;
                WorldObjectId siteId = new WorldObjectId(region.communityId().value() + "_supply_depot");
                SemanticSlotKey key = new SemanticSlotKey(siteId.value(), "receiving_depot", "functional_core");
                ParcelRecord parcel = data.parcels().parcels().stream()
                        .filter(candidate -> candidate.dimensionId().equals(place.dimensionId())
                                && candidate.kind() == ParcelKind.COMMUNITY && candidate.contains(anchor))
                        .findFirst().orElse(null);
                if (parcel == null) {
                    parcel = new ParcelRecord(siteId.value() + ":parcel", region.id(), place.dimensionId(),
                                anchor.offset(-3, -1, -3), anchor.offset(3, 3, 3), "supply_depot",
                                ParcelKind.COMMUNITY, null, 0, "");
                    data.parcels().register(parcel);
                }
                if (data.semanticSlots().find(key).isEmpty()) SemanticSlotRegistration.register(data.semanticSlots(),
                        data.parcels(), key, parcel.id(), place.dimensionId(), ParcelKind.COMMUNITY, capture(level, anchor));
                depot = new SettlementDepotRecord(siteId, region.communityId(), place.dimensionId(), anchor, key);
                data.settlementDepots().put(region.communityId(), depot);
                registerSite(data, commands, depot);
                changed = true;
                continue;
            }
            ServerLevel level = level(server, depot.dimensionId());
            if (level == null || !level.hasChunkAt(depot.anchor())) continue;
            Desired desired = desired(data, depot);
            MaterializationJob job = ensureJob(data, depot, desired);
            if (job.state() == JobState.PLANNED) { job.start(); changed = true; continue; }
            if (job.state() != JobState.RUNNING && job.state() != JobState.BLOCKED) continue;
            if (job.state() == JobState.BLOCKED) job.start();
            String failure = execute(level, data, depot, desired, job);
            if (failure == null) {
                job.complete();
                commands.execute(data.worldState(), new DomainCommand.SetWorldSiteOperational(depot.siteId(),
                        desired == Desired.RUINED ? OperationalState.OFFLINE : OperationalState.OPERATIONAL,
                        "materialization:" + job.jobId()));
            } else job.block(failure);
            changed = true;
        }
        return changed;
    }

    public static SettlementDepotState state(PaleMirrorSavedData data, SettlementDepotRecord depot) {
        MaterializationJob job = data.materializationJobs().activeFor(depot.siteId().value(), CHANNEL).orElse(null);
        if (job == null || job.state() == JobState.PLANNED) return SettlementDepotState.PLANNED;
        if (job.state() == JobState.RUNNING) return SettlementDepotState.RUNNING;
        if (job.state() == JobState.BLOCKED || job.state() == JobState.FAILED) return SettlementDepotState.BLOCKED;
        return data.worldState().site(depot.siteId()).map(site -> site.operationalState() == OperationalState.OPERATIONAL
                ? SettlementDepotState.ACTIVE : SettlementDepotState.BLOCKED).orElse(SettlementDepotState.BLOCKED);
    }

    public static String diagnostic(PaleMirrorSavedData data, SettlementDepotRecord depot) {
        return data.materializationJobs().activeFor(depot.siteId().value(), CHANNEL)
                .map(MaterializationJob::lastError).orElse("");
    }

    private static Desired desired(PaleMirrorSavedData data, SettlementDepotRecord depot) {
        boolean ruined = data.worldState().communityPlaceBinding(depot.communityId())
                .flatMap(binding -> data.worldState().place(binding.placeId()))
                .map(place -> place.structuralIntegrity() == StructuralIntegrity.RUINED).orElse(false);
        if (ruined) return Desired.RUINED;
        boolean upgrading = data.worldState().developmentIntents().stream().anyMatch(intent ->
                intent.communityId().equals(depot.communityId()) && intent.type() == DevelopmentIntentType.UPGRADE_STOREHOUSE
                        && depot.siteId().equals(intent.targetSiteId())
                        && intent.state() == DevelopmentIntentState.MATERIALIZING);
        return upgrading ? Desired.UPGRADED : Desired.BASELINE;
    }

    private static MaterializationJob ensureJob(PaleMirrorSavedData data, SettlementDepotRecord depot, Desired desired) {
        String policy = "pale_mirror:depot_" + desired.name().toLowerCase(java.util.Locale.ROOT);
        MaterializationJob current = data.materializationJobs().activeFor(depot.siteId().value(), CHANNEL).orElse(null);
        if (current != null && current.policyId().equals(policy) && current.policyVersion().equals(TEMPLATE_VERSION)) return current;
        if (current != null) current.cancel("Superseded by " + policy);
        long revision = desired.ordinal() + 1L;
        String id = "pm:job:depot:" + Integer.toUnsignedString(depot.siteId().hashCode(), 36) + ":" + revision;
        MaterializationOperation operation = new MaterializationOperation(id + ":slot", id + ":slot",
                MaterializationOperationType.APPLY_SEMANTIC_SLOT, depot.semanticSlot().value(), OperationState.PENDING, 0, "");
        MaterializationJob job = new MaterializationJob(id, depot.siteId().value(), CHANNEL,
                MaterializationJobClass.CAPABILITY, revision, policy, TEMPLATE_VERSION, JobState.PLANNED,
                List.of(operation), 0, 0, ""); data.materializationJobs().put(job); return job;
    }

    private static String execute(ServerLevel level, PaleMirrorSavedData data, SettlementDepotRecord depot,
                                  Desired desired, MaterializationJob job) {
        MaterializationOperation operation = job.nextOperation(); if (operation == null) return null;
        operation.start(); MaterializationGateway gateway = new MaterializationGateway(level, data.semanticSlots(), data.parcels());
        for (SemanticCellRecord cell : data.semanticSlots().find(depot.semanticSlot()).orElseThrow().cells()) {
            var result = gateway.setBlock(depot.semanticSlot(), cell.position(), block(depot, cell.position(), desired).defaultBlockState(), 3);
            if (result.status() == io.farfrontier.palemirror.api.GuardedWorldAccess.Status.BLOCKED) {
                operation.block(result.diagnostic()); return result.diagnostic();
            }
        }
        gateway.completeReset(depot.semanticSlot()); operation.complete(); job.advanceOperation(); return null;
    }

    private static void registerSite(PaleMirrorSavedData data, DomainCommandExecutor commands,
                                     SettlementDepotRecord depot) {
        if (data.worldState().site(depot.siteId()).isPresent()) return;
        int capacity = data.worldState().economy(depot.communityId()).orElseThrow().require(ResourceKind.IRON).capacity();
        commands.execute(data.worldState(), new DomainCommand.RegisterWorldSite(
                new WorldSite(depot.siteId(), WorldSiteType.STORAGE, OperationalState.DEGRADED),
                List.of(new SiteAffiliation(depot.siteId(), depot.communityId(), SiteAffiliationRole.RECIPIENT)),
                List.of(new SiteCapability(depot.siteId(), SiteCapabilityType.STORAGE, ResourceKind.IRON, capacity)),
                "depot:" + depot.siteId().value()));
    }

    private static List<SemanticCellRecord> capture(ServerLevel level, BlockPos anchor) {
        List<SemanticCellRecord> result = new ArrayList<>();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) add(level, result, anchor.offset(x, 0, z));
        add(level, result, anchor.above()); add(level, result, anchor.offset(2, 1, 2)); return List.copyOf(result);
    }
    private static void add(ServerLevel level, List<SemanticCellRecord> cells, BlockPos pos) {
        var value = level.getBlockState(pos); cells.add(new SemanticCellRecord(pos, value, value));
    }
    private static Block block(SettlementDepotRecord depot, BlockPos pos, Desired desired) {
        if (desired == Desired.RUINED) return pos.equals(depot.interactionPosition()) ? Blocks.IRON_BARS
                : pos.equals(depot.anchor().offset(2, 1, 2)) ? Blocks.SOUL_LANTERN : Blocks.CRACKED_STONE_BRICKS;
        if (pos.equals(depot.interactionPosition())) return Blocks.BARREL;
        if (pos.equals(depot.anchor().offset(2, 1, 2))) return Blocks.LANTERN;
        return desired == Desired.UPGRADED ? Blocks.STONE_BRICKS : Blocks.POLISHED_ANDESITE;
    }
    private static ServerLevel level(MinecraftServer server, String id) {
        for (ServerLevel value : server.getAllLevels()) if (value.dimension().location().toString().equals(id)) return value;
        return null;
    }
    private enum Desired { BASELINE, RUINED, UPGRADED }
}
