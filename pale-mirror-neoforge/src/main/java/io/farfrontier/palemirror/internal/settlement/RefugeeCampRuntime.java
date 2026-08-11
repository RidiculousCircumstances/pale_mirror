package io.farfrontier.palemirror.internal.settlement;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.farfrontier.palemirror.domain.OperationalState;
import io.farfrontier.palemirror.domain.DomainCommand;
import io.farfrontier.palemirror.domain.DomainCommandProcessor;
import io.farfrontier.palemirror.domain.PopulationDisposition;
import io.farfrontier.palemirror.domain.SiteCapability;
import io.farfrontier.palemirror.domain.SiteCapabilityType;
import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.domain.WorldSite;
import io.farfrontier.palemirror.domain.WorldSiteType;
import io.farfrontier.palemirror.internal.economy.SettlementDepotState;
import io.farfrontier.palemirror.internal.world.MutableCell;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/** Representative camp materialization for displaced groups; no physical NPC is canonical population. */
public final class RefugeeCampRuntime {
    public static final String REPRESENTATIVE_KEY = "pale_mirror_refugee_representative";
    public static final String GROUP_KEY = "pale_mirror_population_group";
    private static final int[] RADII = {96, 112, 128, 144, 160};

    private RefugeeCampRuntime() { }

    public static boolean tick(MinecraftServer server, PaleMirrorSavedData data, DomainCommandProcessor commands) {
        boolean changed = false;
        for (var group : data.worldState().populationGroups()) {
            RefugeeCampRecord camp = data.refugeeCamps().get(group.id());
            if (camp == null) {
                if (group.disposition() != PopulationDisposition.DISPLACED) continue;
                var origin = data.worldRegistry().find(group.originPlaceId()).orElse(null);
                if (origin == null) continue;
                ServerLevel level = level(server, origin.dimensionId());
                if (level == null) continue;
                BlockPos anchor = findAnchor(level, origin.anchor(), group.id().hashCode());
                if (anchor == null) continue;
                WorldObjectId siteId = new WorldObjectId("pale_mirror:refugee_camp_"
                        + Integer.toUnsignedString(group.id().hashCode(), 36));
                int representatives = Math.max(2, Math.min(4, (group.size() + 19) / 20));
                List<UUID> ids = new ArrayList<>();
                for (int slot = 0; slot < representatives; slot++) ids.add(UUID.nameUUIDFromBytes(
                        (group.id() + ":representative:" + slot).getBytes(StandardCharsets.UTF_8)));
                camp = new RefugeeCampRecord(group.id(), group.communityId(), siteId, origin.dimensionId(), anchor,
                        captureCells(level, anchor), ids, SettlementDepotState.PLANNED, "");
                data.refugeeCamps().put(group.id(), camp);
                commands.execute(data.worldState(), new DomainCommand.RegisterAutonomousRefugeeShelter(group.communityId(),
                        new WorldSite(siteId, WorldSiteType.SHELTER, OperationalState.DEGRADED),
                        new SiteCapability(siteId, SiteCapabilityType.SHELTER, null, data.worldState().population(group.communityId())),
                        "policy:autonomous-refugee-site:" + group.id()));
                changed = true;
                continue;
            }
            if (data.worldState().site(camp.siteId()).isEmpty()) continue;
            ServerLevel level = level(server, camp.dimensionId());
            if (level == null || !level.hasChunkAt(camp.anchor()) || camp.state() == SettlementDepotState.BLOCKED) continue;
            if (camp.state() == SettlementDepotState.PLANNED) {
                camp.start();
                changed = true;
            } else if (camp.state() == SettlementDepotState.RUNNING) {
                String failure = ensure(level, camp);
                if (failure == null) {
                    camp.activate();
                    commands.execute(data.worldState(), new DomainCommand.SetWorldSiteOperational(camp.siteId(),
                            OperationalState.OPERATIONAL, "materialization:refugee-camp:" + camp.populationGroupId()));
                } else camp.block(failure);
                changed = true;
            } else if ((group.disposition() == PopulationDisposition.DISPLACED
                    || group.disposition() == PopulationDisposition.RESETTLED)
                    && ensureRepresentatives(level, camp)) changed = true;
        }
        return changed;
    }

    public static boolean isRepresentative(net.minecraft.world.entity.Entity entity) {
        return entity.getPersistentData().getBoolean(REPRESENTATIVE_KEY);
    }

    public static boolean cleanupReturnedGroups(MinecraftServer server, PaleMirrorSavedData data, DomainCommandProcessor commands) {
        boolean changed = false;
        for (RefugeeCampRecord camp : data.refugeeCamps().values()) {
            var group = data.worldState().populationGroup(camp.populationGroupId()).orElse(null);
            if (group == null || group.disposition() != PopulationDisposition.RESIDENT
                    || camp.state() != SettlementDepotState.ACTIVE) continue;
            ServerLevel level = level(server, camp.dimensionId());
            if (level == null || !level.hasChunkAt(camp.anchor())) continue;
            String failure = cleanup(level, camp);
            if (failure == null) {
                camp.block("Population returned home");
                commands.execute(data.worldState(), new DomainCommand.SetWorldSiteOperational(camp.siteId(),
                        OperationalState.OFFLINE, "materialization:refugee-camp-return:" + camp.populationGroupId()));
            } else camp.block(failure);
            changed = true;
        }
        return changed;
    }

    private static String ensure(ServerLevel level, RefugeeCampRecord camp) {
        for (MutableCell cell : camp.cells()) {
            String current = blockId(level, cell.position());
            if (cell.conflicted() || !current.equals(cell.baselineBlock()) && !current.equals(cell.lastAppliedBlock())) {
                cell.conflict();
                return "Refugee camp cell changed outside Pale Mirror at " + cell.position();
            }
            Block desired = desired(camp.anchor(), cell.position());
            String desiredId = BuiltInRegistries.BLOCK.getKey(desired).toString();
            if (!current.equals(desiredId)) level.setBlock(cell.position(), desired.defaultBlockState(), 3);
            if (!blockId(level, cell.position()).equals(desiredId)) return "Refugee camp postcondition failed";
            cell.markApplied(desiredId);
        }
        return null;
    }

    private static String cleanup(ServerLevel level, RefugeeCampRecord camp) {
        for (MutableCell cell : camp.cells()) {
            String current = blockId(level, cell.position());
            if (cell.conflicted() || !current.equals(cell.lastAppliedBlock()) && !current.equals(cell.baselineBlock())) {
                cell.conflict();
                return "Refugee camp cleanup conflicts at " + cell.position();
            }
            Block baseline = BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse(cell.baselineBlock()));
            level.setBlock(cell.position(), baseline.defaultBlockState(), 3);
            if (!blockId(level, cell.position()).equals(cell.baselineBlock())) return "Refugee camp cleanup postcondition failed";
            cell.markApplied(cell.baselineBlock());
        }
        camp.representativeIds().forEach(id -> {
            var entity = level.getEntity(id);
            if (entity != null && isRepresentative(entity)) entity.discard();
        });
        return null;
    }

    private static boolean ensureRepresentatives(ServerLevel level, RefugeeCampRecord camp) {
        boolean changed = false;
        for (int slot = 0; slot < camp.representativeIds().size(); slot++) {
            UUID id = camp.representativeIds().get(slot);
            if (level.getEntity(id) != null) continue;
            Villager villager = EntityType.VILLAGER.create(level);
            if (villager == null) continue;
            villager.setUUID(id);
            villager.setNoAi(true);
            villager.setInvulnerable(true);
            villager.setPersistenceRequired();
            villager.getPersistentData().putBoolean(REPRESENTATIVE_KEY, true);
            villager.getPersistentData().putString(GROUP_KEY, camp.populationGroupId());
            villager.moveTo(camp.anchor().offset(-1 + slot, 1, 0), 0, 0);
            level.addFreshEntity(villager);
            changed = true;
        }
        return changed;
    }

    private static BlockPos findAnchor(ServerLevel level, BlockPos center, int seed) {
        for (int index = 0; index < 16; index++) {
            int radius = RADII[Math.floorMod(index + seed, RADII.length)];
            double angle = Math.PI * 2.0 * Math.floorMod(index + seed, 16) / 16.0;
            int x = center.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * radius);
            BlockPos candidate = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (safeAnchor(level, candidate)) return candidate;
        }
        return null;
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

    public static List<MutableCell> captureCells(ServerLevel level, BlockPos anchor) {
        List<MutableCell> cells = new ArrayList<>();
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            if (Math.abs(x) == 3 || Math.abs(z) == 3 || x == 0 || z == 0) add(level, cells, anchor.offset(x, 0, z));
        }
        for (BlockPos pos : List.of(anchor.above(), anchor.offset(-2, 1, -2), anchor.offset(2, 1, 2))) add(level, cells, pos);
        return List.copyOf(cells);
    }

    private static void add(ServerLevel level, List<MutableCell> cells, BlockPos pos) {
        String baseline = blockId(level, pos);
        cells.add(new MutableCell(pos, baseline, baseline, false));
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
    private static String blockId(ServerLevel level, BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
    }
}
