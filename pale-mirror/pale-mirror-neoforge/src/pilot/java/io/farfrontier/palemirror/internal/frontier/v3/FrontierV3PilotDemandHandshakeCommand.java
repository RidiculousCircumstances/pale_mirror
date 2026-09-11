package io.farfrontier.palemirror.internal.frontier.v3;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3PilotChunkMapAccessor;
import io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3PilotDistanceManagerAccessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Function;

/**
 * Pilot-only acknowledgement armed before ordinary travel, retained through transfer, then read
 * after vanilla distance work and before the ordinary PM Post turn. It never queues scene
 * admission or changes ticket, chunk, canonical, cache, or lifecycle ownership.
 */
@EventBusSubscriber(modid = PaleMirrorMod.MOD_ID)
public final class FrontierV3PilotDemandHandshakeCommand {
    static final String COMMAND = "pale_mirror_pilot_demand_handshake";
    private static final ArmedReceipts ARMED = new ArmedReceipts();

    private FrontierV3PilotDemandHandshakeCommand() { }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(COMMAND).requires(source -> source.hasPermission(4))
                .then(Commands.literal("arm").then(Commands.argument("request", StringArgumentType.word())
                        .then(Commands.argument("assault", StringArgumentType.word())
                                .then(Commands.argument("dimension", StringArgumentType.word())
                                        .then(Commands.argument("run", StringArgumentType.word())
                                                .then(Commands.argument("step", IntegerArgumentType.integer(1))
                                                        .then(Commands.argument("attempt", StringArgumentType.word())
                                                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                                                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                                                                .then(Commands.argument("z", IntegerArgumentType.integer()).executes(
                                                                                        FrontierV3PilotDemandHandshakeCommand::arm))))))))))));
    }

    private static int arm(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        String request = StringArgumentType.getString(context, "request");
        String assault = StringArgumentType.getString(context, "assault");
        String dimension = StringArgumentType.getString(context, "dimension");
        Correlation correlation;
        try { correlation = new Correlation(StringArgumentType.getString(context, "run"), IntegerArgumentType.getInteger(context, "step"), StringArgumentType.getString(context, "attempt")); }
        catch (IllegalArgumentException invalid) { return 0; }
        BlockPos travelAnchor = new BlockPos(IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "y"),
                IntegerArgumentType.getInteger(context, "z"));
        if (!request.matches("[a-z][a-z0-9_-]{0,63}") || !assault.matches("assault:[a-z0-9][a-z0-9_-]{0,95}")) return 0;
        ResourceLocation dimensionId = ResourceLocation.tryParse(dimension);
        ServerPlayer player = context.getSource().getEntity() instanceof ServerPlayer value ? value : null;
        if (player == null || dimensionId == null) return 0;
        ResourceKey<Level> destination = ResourceKey.create(Registries.DIMENSION, dimensionId);
        ServerLevel destinationLevel = context.getSource().getServer().getLevel(destination);
        if (destinationLevel == null || player.serverLevel().dimension().equals(destination)) return 0;
        ArmedReceipt armed = new ArmedReceipt(correlation, request, assault, dimension, destination, travelAnchor);
        if (!ARMED.arm(player.getUUID(), armed)) return 0;
        try {
            // This is the same ordinary ServerPlayer teleport API used by TeleportCommand; no ticket or PM state is manufactured here.
            player.teleportTo(destinationLevel, travelAnchor.getX(), travelAnchor.getY(), travelAnchor.getZ(), player.getYRot(), player.getXRot());
            player.setDeltaMovement(player.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D)); player.setOnGround(true);
            return 1;
        } catch (RuntimeException failure) { ARMED.forget(player.getUUID()); throw failure; }
    }

    /** The matching PlayerChangedDimensionEvent is the post-transfer observation boundary. */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ARMED.observeTransfer(player.getUUID(), event.getTo());
    }

    /** Runs after vanilla level/distance-manager work and before the default-priority PM scene Post listener. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerTick(ServerTickEvent.Post event) {
        for (Pending pending : ARMED.transferred()) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(pending.player());
            if (player == null) { ARMED.forget(pending.player()); continue; }
            ServerLevel destination = player.serverLevel();
            ARMED.observeAfterDistance(pending.player(), state -> postDistanceObservation(player, destination, state)).ifPresent(receipt ->
                    player.sendSystemMessage(Component.literal("PMV3_DIAG " + receipt.json())));
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ARMED.forget(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ARMED.clear();
    }

    private static Optional<PostDistanceObservation> postDistanceObservation(ServerPlayer player, ServerLevel destination, Pending pending) {
        ArmedReceipt armed = pending.armed();
        if (!destination.dimension().equals(armed.destination())) return Optional.empty();
        Candidate candidate = pending.candidate().orElseGet(() -> Candidate.from(
                FrontierV3ServerLifecycle.pilotSceneDemandSnapshot(destination, new SubjectId(armed.assault()))).orElse(null));
        if (candidate == null) return Optional.empty();
        FrontierV3SceneDemand.Snapshot demand = FrontierV3SceneDemand.observe(destination, candidate.handoff());
        FrontierV3PilotSceneDemandSnapshot snapshot = new FrontierV3PilotSceneDemandSnapshot(Optional.of(candidate.providerIdentity()), OptionalInt.of(1),
                Optional.of(candidate.handoff()), demand.chunkLoaded(), demand.observerIds());
        TicketState ticket = ticketState(destination, new BlockPos(candidate.handoff().x(), candidate.handoff().y(), candidate.handoff().z()));
        Receipt receipt = Receipt.from(armed, Optional.of(player.getUUID()), Optional.of(player.blockPosition()), player.serverLevel() == destination, ticket, snapshot);
        return Optional.of(new PostDistanceObservation(receipt, Optional.of(candidate)));
    }

    record Correlation(String runId, int actionStep, String actionAttempt) {
        Correlation {
            UUID.fromString(runId); if (actionStep < 1) throw new IllegalArgumentException("action step"); UUID.fromString(actionAttempt);
        }
    }
    record ArmedReceipt(Correlation correlation, String request, String assault, String dimension, ResourceKey<Level> destination, BlockPos travelAnchor) { }
    record Candidate(String providerIdentity, io.farfrontier.palemirror.frontier.v3.model.BlockPosition handoff) {
        static Optional<Candidate> from(FrontierV3PilotSceneDemandSnapshot snapshot) {
            if (snapshot.providerIdentity().isEmpty() || snapshot.exactCandidateCount().orElse(0) != 1 || snapshot.handoffPosition().isEmpty()) return Optional.empty();
            return Optional.of(new Candidate(snapshot.providerIdentity().orElseThrow(), snapshot.handoffPosition().orElseThrow()));
        }
    }
    record Pending(UUID player, ArmedReceipt armed, boolean transferObserved, Optional<Candidate> candidate) {
        Pending { candidate = candidate == null ? Optional.empty() : candidate; }
        Pending transferred() { return new Pending(player, armed, true, candidate); }
        Pending retain(Optional<Candidate> observed) { return new Pending(player, armed, transferObserved, candidate.isPresent() ? candidate : observed); }
    }
    record PostDistanceObservation(Receipt receipt, Optional<Candidate> candidate) { }

    /** One pending receipt per player; a different transfer cannot consume it and logout/server stop clears it. */
    static final class ArmedReceipts {
        private final Map<UUID, Pending> receipts = new HashMap<>();
        boolean arm(UUID player, ArmedReceipt receipt) {
            if (receipts.containsKey(player)) return false;
            receipts.put(player, new Pending(player, receipt, false, Optional.empty())); return true;
        }
        boolean observeTransfer(UUID player, ResourceKey<Level> destination) {
            Pending receipt = receipts.get(player);
            if (receipt == null || receipt.transferObserved() || !receipt.armed().destination().equals(destination)) return false;
            receipts.put(player, receipt.transferred()); return true;
        }
        List<Pending> transferred() { return new ArrayList<>(receipts.values()).stream().filter(Pending::transferObserved).toList(); }
        Optional<Receipt> observeAfterDistance(UUID player, Function<Pending, Optional<PostDistanceObservation>> observe) {
            Pending pending = receipts.get(player); if (pending == null || !pending.transferObserved()) return Optional.empty();
            Optional<PostDistanceObservation> observed = observe.apply(pending); if (observed.isEmpty()) return Optional.empty();
            Pending retained = pending.retain(observed.orElseThrow().candidate());
            if (observed.orElseThrow().receipt().reason() == Reason.ADMITTED) { receipts.remove(player); return Optional.of(observed.orElseThrow().receipt()); }
            receipts.put(player, retained); return Optional.empty();
        }
        void forget(UUID player) { receipts.remove(player); }
        void clear() { receipts.clear(); }
        boolean pending(UUID player) { return receipts.containsKey(player); }
    }

    private static TicketState ticketState(ServerLevel level, BlockPos anchor) {
        ChunkMap map = level.getChunkSource().chunkMap;
        long chunk = ChunkPos.asLong(anchor.getX() >> 4, anchor.getZ() >> 4);
        DistanceManager manager = map.getDistanceManager();
        Long2ObjectMap<SortedArraySet<Ticket<?>>> tickets = ((FrontierV3PilotDistanceManagerAccessor) manager).paleMirror$tickets();
        SortedArraySet<Ticket<?>> atChunk = tickets.get(chunk);
        boolean playerTicket = atChunk != null && atChunk.stream().anyMatch(ticket -> ticket.getType() == net.minecraft.server.level.TicketType.PLAYER);
        ChunkHolder holder = ((FrontierV3PilotChunkMapAccessor) map).paleMirror$updatingChunks().get(chunk);
        return holder == null ? new TicketState(playerTicket, false, -1, false)
                : new TicketState(playerTicket, true, holder.getGenerationRefCount(), holder.isReadyForSaving());
    }

    enum Reason { NO_PROVIDER, NO_CANDIDATE, NO_DEMAND, ADMITTED }

    record TicketState(boolean playerTicket, boolean holderPresent, int generationRefCount, boolean readyForSaving) {
        static TicketState absent() { return new TicketState(false, false, -1, false); }
    }

    record Receipt(Correlation correlation, String request, String assault, String destinationDimension, BlockPos travelAnchor, String playerId,
                   Optional<BlockPos> serverPlayerPosition, boolean destinationObserved, TicketState ticket, Optional<String> providerIdentity,
                   Optional<Integer> exactCandidateCount, Optional<io.farfrontier.palemirror.frontier.v3.model.BlockPosition> candidateHandoff,
                   boolean demandChunkLoaded, java.util.Set<UUID> demandObserverIds, boolean requestedObserverPresent, Reason reason) {
        static Receipt from(ArmedReceipt armed, Optional<UUID> player,
                            Optional<BlockPos> serverPlayerPosition, boolean destinationObserved, TicketState ticket,
                            FrontierV3PilotSceneDemandSnapshot snapshot) {
            String playerId = player.map(UUID::toString).orElse("");
            Optional<String> provider = snapshot == null ? Optional.empty() : snapshot.providerIdentity();
            Optional<Integer> candidates = snapshot == null || snapshot.exactCandidateCount().isEmpty() ? Optional.empty()
                    : Optional.of(snapshot.exactCandidateCount().getAsInt());
            Optional<io.farfrontier.palemirror.frontier.v3.model.BlockPosition> handoff = snapshot == null ? Optional.empty() : snapshot.handoffPosition();
            boolean chunkLoaded = snapshot != null && snapshot.demandChunkLoaded();
            java.util.Set<UUID> observers = snapshot == null ? java.util.Set.of() : snapshot.demandObserverIds();
            boolean requestedObserver = player.isPresent() && observers.contains(player.orElseThrow());
            Reason reason = FrontierV3PilotDemandHandshakeCommand.reason(destinationObserved, ticket, provider, candidates, handoff,
                    chunkLoaded, requestedObserver);
            return new Receipt(armed.correlation(), armed.request(), armed.assault(), armed.dimension(), armed.travelAnchor(), playerId, serverPlayerPosition, destinationObserved, ticket, provider,
                    candidates, handoff, chunkLoaded, java.util.Set.copyOf(observers), requestedObserver, reason);
        }

        String json() {
            JsonObject value = new JsonObject(); value.addProperty("schema", 1); value.addProperty("kind", "demand_handshake");
            value.addProperty("id", request); value.addProperty("assault", assault); value.addProperty("destinationDimension", destinationDimension);
            value.addProperty("pilotRunId", correlation.runId()); value.addProperty("pilotActionStep", correlation.actionStep()); value.addProperty("pilotActionAttempt", correlation.actionAttempt());
            value.add("travelAnchor", point(travelAnchor)); value.addProperty("playerId", playerId);
            if (serverPlayerPosition.isPresent()) value.add("serverPlayerPosition", point(serverPlayerPosition.orElseThrow())); else value.add("serverPlayerPosition", com.google.gson.JsonNull.INSTANCE);
            value.addProperty("destinationObserved", destinationObserved);
            value.addProperty("destinationPlayerTicket", ticket.playerTicket()); value.addProperty("destinationHolder", ticket.holderPresent());
            value.addProperty("holderGenerationRefCount", ticket.generationRefCount()); value.addProperty("holderReadyForSaving", ticket.readyForSaving());
            if (providerIdentity.isPresent()) value.addProperty("providerIdentity", providerIdentity.orElseThrow()); else value.add("providerIdentity", com.google.gson.JsonNull.INSTANCE);
            if (exactCandidateCount.isPresent()) value.addProperty("exactCandidateCount", exactCandidateCount.orElseThrow()); else value.add("exactCandidateCount", com.google.gson.JsonNull.INSTANCE);
            if (candidateHandoff.isPresent()) value.add("candidateHandoff", point(candidateHandoff.orElseThrow())); else value.add("candidateHandoff", com.google.gson.JsonNull.INSTANCE);
            value.addProperty("sceneDemandChunkLoaded", demandChunkLoaded); JsonArray observers = new JsonArray(); demandObserverIds.stream().map(UUID::toString).sorted().forEach(observers::add);
            value.add("sceneDemandObserverIds", observers); value.addProperty("requestedObserverPresent", requestedObserverPresent); value.addProperty("reason", reason.name());
            return value.toString();
        }

        private static JsonObject point(BlockPos position) { return point(position.getX(), position.getY(), position.getZ()); }
        private static JsonObject point(io.farfrontier.palemirror.frontier.v3.model.BlockPosition position) { return point(position.x(), position.y(), position.z()); }
        private static JsonObject point(int x, int y, int z) {
            JsonObject point = new JsonObject(); point.addProperty("x", x); point.addProperty("y", y); point.addProperty("z", z); return point;
        }
    }

    static Reason reason(boolean destinationObserved, TicketState ticket, Optional<String> provider, Optional<Integer> candidates,
                         Optional<io.farfrontier.palemirror.frontier.v3.model.BlockPosition> handoff,
                         boolean demandChunkLoaded, boolean requestedObserverPresent) {
        if (!destinationObserved) return Reason.NO_DEMAND;
        if (provider.isEmpty()) return Reason.NO_PROVIDER;
        if (candidates.orElse(0) != 1 || handoff.isEmpty()) return Reason.NO_CANDIDATE;
        return !ticket.playerTicket() || !ticket.holderPresent() || !demandChunkLoaded || !requestedObserverPresent
                ? Reason.NO_DEMAND : Reason.ADMITTED;
    }
}
