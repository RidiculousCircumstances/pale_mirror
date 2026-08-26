package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.api.PaleMirrorVisuals;
import io.farfrontier.palemirror.frontier.FrontierCommand;
import io.farfrontier.palemirror.frontier.FrontierCommandOutcome;
import io.farfrontier.palemirror.frontier.FrontierSnapshots;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

/** Server-thread coordinator for the replacement Frontier aggregate and its read-only physical projection. */
public final class FrontierRuntime {
    private static final long DAY_INTERVAL_TICKS = 1_200L;
    private static final long PROJECTION_INTERVAL_TICKS = 20L;
    private static final Map<MinecraftServer, FrontierRuntime> INSTANCES = new IdentityHashMap<>();
    private final MinecraftServer server;
    private final FrontierSavedData data;

    public FrontierRuntime(MinecraftServer server) {
        this.server = server;
        this.data = FrontierSavedData.get(server.overworld());
    }

    public static FrontierRuntime forServer(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, FrontierRuntime::new);
    }

    public static void stop(MinecraftServer server) {
        INSTANCES.remove(server);
    }

    /** Advances the replacement clock and reports whether it exclusively owns this world. */
    public boolean tick() {
        ServerLevel overworld = server.overworld();
        boolean changed = data.physicalProjectionEnabled() && reconcilePhysicalFacts(overworld);
        long gameTime = overworld.getGameTime();
        if (gameTime > 0 && gameTime % DAY_INTERVAL_TICKS == 0) {
            FrontierCommandOutcome outcome = data.execute(new FrontierCommand.AdvanceDays(1, "frontier:clock:" + gameTime));
            changed |= outcome.accepted() || !outcome.events().isEmpty();
        }
        if (data.physicalProjectionEnabled() && (changed || gameTime % PROJECTION_INTERVAL_TICKS == 0)) publish(overworld);
        return data.physicalProjectionEnabled();
    }

    public FrontierCommandOutcome advance(int days, String causationId) {
        FrontierCommandOutcome outcome = data.execute(new FrontierCommand.AdvanceDays(days, causationId));
        if (data.physicalProjectionEnabled()) publish(server.overworld());
        return outcome;
    }
    public void activatePhysicalProjection() {
        if (!data.state().profile().id().equals("graybox-10")) {
            throw new IllegalStateException("physical graybox projection requires the graybox-10 Frontier profile");
        }
        FrontierGrayboxWorldBoundary.enforce(server.overworld());
        data.enablePhysicalProjection();
        publish(server.overworld());
    }

    public String status() {
        var state = data.state();
        return "profile=" + state.profile().id() + ", day=" + state.day() + ", settlements=" + state.settlements().size()
                + ", residents=" + state.alivePopulationTotal() + ", operations=" + state.operations().size()
                + ", cargo=" + state.cargo().size() + ", hives=" + state.hives().size()
                + ", bioforms=" + state.bioforms().stream().filter(value -> value.alive()).count()
                + ", materialization=" + (data.physicalProjectionEnabled() ? "ENABLED" : "DISABLED");
    }

    public boolean observeEntityDeath(Entity entity, String causationId) {
        if (!(entity.level() instanceof ServerLevel level)) return false;
        boolean acceptedByProjection = PaleMirrorVisuals.provider()
                .map(provider -> provider.observeFrontierEntityDeath(level, entity, causationId)).orElse(false);
        // A physical fact has crossed the presentation boundary at this point. Reconcile it in
        // the same server event, rather than leaving the only copy in the provider's RAM until
        // the next tick. FrontierSavedData then durably owns both the state transition and its
        // deduplication id before a normal shutdown/restart can intervene.
        if (acceptedByProjection && reconcilePhysicalFacts(level) && data.physicalProjectionEnabled()) publish(level);
        return acceptedByProjection;
    }

    public boolean observeEntityDeath(Entity entity, DamageSource source) {
        String actor = source.getEntity() == null ? "environment" : source.getEntity().getUUID().toString();
        return observeEntityDeath(entity, "frontier:physical-death:" + actor + ":" + entity.getUUID());
    }

    public boolean observeBlockBreak(ServerLevel level, net.minecraft.core.BlockPos position, String causationId) {
        boolean acceptedByProjection = PaleMirrorVisuals.provider()
                .map(provider -> provider.observeFrontierBlockBreak(level, position, causationId)).orElse(false);
        if (acceptedByProjection && reconcilePhysicalFacts(level) && data.physicalProjectionEnabled()) publish(level);
        return acceptedByProjection;
    }

    public boolean observeBlockBreak(ServerLevel level, net.minecraft.core.BlockPos position, ServerPlayer player) {
        return observeBlockBreak(level, position, "frontier:block-break:" + player.getUUID() + ":" + position.asLong());
    }

    private boolean reconcilePhysicalFacts(ServerLevel level) {
        Collection<io.farfrontier.palemirror.api.FrontierPhysicalObservation> facts = PaleMirrorVisuals.provider()
                .map(provider -> provider.drainFrontierObservations(level)).orElse(java.util.List.of());
        boolean changed = false;
        for (io.farfrontier.palemirror.api.FrontierPhysicalObservation fact : facts) {
            io.farfrontier.palemirror.frontier.FrontierPhysicalObservation observation = switch (fact.type()) {
                case RESIDENT_DIED -> new io.farfrontier.palemirror.frontier.FrontierPhysicalObservation.ResidentDeath(
                        fact.observationId(), fact.subjectId(), fact.materializationId(), fact.expectedRevision(), fact.causationId());
                case FACILITY_DAMAGED -> new io.farfrontier.palemirror.frontier.FrontierPhysicalObservation.FacilityDamage(
                        fact.observationId(), fact.subjectId(), fact.materializationId(), fact.expectedRevision(), fact.causationId());
                case CARGO_LOST -> new io.farfrontier.palemirror.frontier.FrontierPhysicalObservation.CargoLost(
                        fact.observationId(), fact.subjectId(), fact.materializationId(), fact.expectedRevision(), fact.causationId());
                case HIVE_ORGAN_DESTROYED -> new io.farfrontier.palemirror.frontier.FrontierPhysicalObservation.HiveOrganDestroyed(
                        fact.observationId(), fact.subjectId(), fact.materializationId(), fact.expectedRevision(), fact.causationId());
                case BIOFORM_DIED -> new io.farfrontier.palemirror.frontier.FrontierPhysicalObservation.BioformDeath(
                        fact.observationId(), fact.subjectId(), fact.materializationId(), fact.expectedRevision(), fact.causationId());
                case LATENT_COLONY_CLEARED -> new io.farfrontier.palemirror.frontier.FrontierPhysicalObservation.LatentColonyCleared(
                        fact.observationId(), fact.subjectId(), fact.materializationId(), fact.expectedRevision(), fact.causationId());
            };
            FrontierCommandOutcome outcome = data.execute(new FrontierCommand.ApplyObservation(observation));
            changed |= outcome.accepted() || !outcome.events().isEmpty();
        }
        return changed;
    }

    private void publish(ServerLevel level) {
        PaleMirrorVisuals.provider().ifPresent(provider -> provider.applyFrontierProjection(level,
                FrontierProjectionMapper.map(FrontierSnapshots.snapshot(data.state()))));
    }
}
