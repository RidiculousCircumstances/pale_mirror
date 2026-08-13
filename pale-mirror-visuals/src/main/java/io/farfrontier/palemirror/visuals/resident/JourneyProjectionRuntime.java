package io.farfrontier.palemirror.visuals.resident;

import io.farfrontier.palemirror.api.AuthoredRegionSeed;
import io.farfrontier.palemirror.api.JourneyObservation;
import io.farfrontier.palemirror.api.JourneyProjection;
import io.farfrontier.palemirror.api.ResidentSeed;
import io.farfrontier.palemirror.api.VisualPoint;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

/** Bounded physical projection of canonical travel. It owns no route progress or casualty truth. */
public final class JourneyProjectionRuntime {
    private static final double OBSERVATION_RADIUS = 96.0D;
    private final ResidentMaterializer residents = new ResidentMaterializer();
    private final Map<String, JourneyProjection> projections = new LinkedHashMap<>();
    private final Map<String, JourneyObservation> observations = new LinkedHashMap<>();

    public synchronized void reconcile(ServerLevel level, JourneyProjection projection, AuthoredRegionSeed region) {
        projections.put(projection.journeyId(), projection);
        Map<String, ResidentSeed> roster = region.residents().stream()
                .collect(java.util.stream.Collectors.toMap(ResidentSeed::residentId, value -> value));
        Vec3 focus = pointAlong(projection);
        boolean observed = level.players().stream().anyMatch(player -> player.distanceToSqr(focus) <= square(OBSERVATION_RADIUS));
        projection.retireAtOriginResidentIds().forEach(residentId -> {
            Entity original = level.getEntity(UUID.fromString(residentId));
            if (original != null && ManagedResident.isManaged(original)) original.discard();
        });
        int spawned = 0;
        for (var lease : projection.residentLeases()) {
            ResidentSeed seed = roster.get(lease.residentId());
            if (seed == null) continue;
            if (lease.phase().equals("RELEASED")) {
                release(level, projection, seed);
                continue;
            }
            if (lease.phase().equals("RETIRE_ORIGIN")) {
                retireOrigin(level, projection, seed, lease.revision());
                continue;
            }
            if (!lease.phase().equals("READY")) continue;
            UUID carrierId = carrierUuid(seed.residentId(), projection.journeyId());
            Entity loaded = level.getEntity(carrierId);
            if (!observed || terminalWithoutDestination(projection)) {
                if (loaded != null) loaded.discard();
                continue;
            }
            if (loaded == null && spawned < projection.spawnBudgetPerTick() && level.hasChunkAt(BlockPos.containing(focus))) {
                Villager carrier = residents.create(level, region, seed, carrierId);
                ManagedResident.attachJourney(carrier, projection.journeyId());
                Vec3 spawn = formationPoint(focus, seed.residentId());
                carrier.setPos(spawn.x, spawn.y, spawn.z);
                if (level.addFreshEntity(carrier)) { loaded = carrier; spawned++; }
            }
            if (loaded instanceof Villager villager) guide(villager, focus, seed.residentId());
        }
        if (projection.restoreAtOrigin()) {
            for (String residentId : projection.restoreResidentIds()) {
                if (spawned >= projection.spawnBudgetPerTick()) break;
                ResidentSeed seed = roster.get(residentId);
                if (seed != null && restore(level, region, seed)) spawned++;
            }
        }
        observeCheckpoint(level, projection, focus);
    }

    private void release(ServerLevel level, JourneyProjection projection, ResidentSeed seed) {
        Entity carrier = level.getEntity(carrierUuid(seed.residentId(), projection.journeyId()));
        if (carrier != null) carrier.discard();
    }

    private boolean restore(ServerLevel level, AuthoredRegionSeed region, ResidentSeed seed) {
        BlockPos home = new BlockPos(seed.home().x(), seed.home().y(), seed.home().z());
        boolean observed = level.players().stream().anyMatch(player -> player.distanceToSqr(Vec3.atCenterOf(home))
                <= square(OBSERVATION_RADIUS));
        UUID residentId = UUID.fromString(seed.residentId());
        if (!observed || !level.hasChunkAt(home) || level.getEntity(residentId) != null) return false;
        Vec3 spawn = ResidentMaterializer.safeSpawn(level, home);
        if (spawn == null) return false;
        Villager resident = residents.create(level, region, seed, residentId);
        resident.setPos(spawn.x, spawn.y, spawn.z);
        return level.addFreshEntity(resident);
    }

    private void retireOrigin(ServerLevel level, JourneyProjection projection, ResidentSeed seed, long revision) {
        Entity original = level.getEntity(UUID.fromString(seed.residentId()));
        if (original != null && ManagedResident.isManaged(original)) original.discard();
        String id = "journey-retire:" + projection.journeyId() + ":" + seed.residentId() + ":" + revision;
        observations.putIfAbsent(id, new JourneyObservation(id, projection.journeyId(),
                JourneyObservation.Type.IDENTITY_RETIRED, projection.checkpointIndex(), seed.residentId(), ""));
    }

    private void observeCheckpoint(ServerLevel level, JourneyProjection projection, Vec3 focus) {
        int count = projection.path().size();
        int reached = Math.min(count - 1, (int) Math.floor(projection.progress() * (count - 1)));
        if (reached <= projection.checkpointIndex()) return;
        boolean witnessed = level.players().stream().anyMatch(player -> player.distanceToSqr(focus) <= square(OBSERVATION_RADIUS));
        if (!witnessed) return;
        String resident = projection.residentLeases().isEmpty() ? "" : projection.residentLeases().getFirst().residentId();
        String id = "journey-checkpoint:" + projection.journeyId() + ":" + reached;
        observations.putIfAbsent(id, new JourneyObservation(id, projection.journeyId(),
                JourneyObservation.Type.CHECKPOINT_REACHED, reached, resident, ""));
    }

    public synchronized boolean suppressJoin(Villager villager) {
        String residentId = villager.getPersistentData().getString(ManagedResident.ID);
        if (residentId.isBlank()) return false;
        for (JourneyProjection projection : projections.values()) {
            if (projection.retireAtOriginResidentIds().contains(residentId)
                    && !ManagedResident.isJourneyCarrier(villager, projection.journeyId())) return true;
            boolean leased = projection.residentLeases().stream().anyMatch(value -> value.residentId().equals(residentId)
                    && !value.phase().equals("RELEASED"));
            if (leased && !ManagedResident.isJourneyCarrier(villager, projection.journeyId())) return true;
        }
        return false;
    }

    public synchronized Collection<JourneyObservation> drain() {
        var result = java.util.List.copyOf(observations.values());
        observations.clear();
        return result;
    }

    public synchronized void clear() { projections.clear(); observations.clear(); }

    private static Vec3 pointAlong(JourneyProjection projection) {
        double scaled = Math.max(0.0D, Math.min(1.0D, projection.progress())) * (projection.path().size() - 1);
        int fromIndex = Math.min(projection.path().size() - 2, (int) Math.floor(scaled));
        double local = scaled - fromIndex;
        VisualPoint from = projection.path().get(fromIndex); VisualPoint to = projection.path().get(fromIndex + 1);
        return new Vec3(lerp(from.x(), to.x(), local) + 0.5D, lerp(from.y(), to.y(), local) + 1.0D,
                lerp(from.z(), to.z(), local) + 0.5D);
    }

    private static Vec3 formationPoint(Vec3 center, String residentId) {
        int hash = residentId.hashCode();
        return center.add(Math.floorMod(hash, 7) - 3, 0, Math.floorMod(hash / 7, 7) - 3);
    }

    private static void guide(Villager villager, Vec3 focus, String residentId) {
        Vec3 target = formationPoint(focus, residentId);
        if (villager.distanceToSqr(target) > 32.0D * 32.0D) villager.teleportTo(target.x, target.y, target.z);
        else villager.getNavigation().moveTo(target.x, target.y, target.z, 0.72D);
    }

    private static boolean terminalWithoutDestination(JourneyProjection projection) {
        return projection.state().equals("LOST") || projection.state().equals("CANCELLED");
    }

    private static UUID carrierUuid(String residentId, String journeyId) {
        return UUID.nameUUIDFromBytes(("pm-journey:" + journeyId + ":" + residentId).getBytes(StandardCharsets.UTF_8));
    }
    private static double lerp(double from, double to, double amount) { return from + (to - from) * amount; }
    private static double square(double value) { return value * value; }
}
