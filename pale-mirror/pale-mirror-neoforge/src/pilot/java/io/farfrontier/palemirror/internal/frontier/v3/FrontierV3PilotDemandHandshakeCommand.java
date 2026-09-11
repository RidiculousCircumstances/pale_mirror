package io.farfrontier.palemirror.internal.frontier.v3;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3PilotChunkMapAccessor;
import io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3PilotDistanceManagerAccessor;
import io.farfrontier.palemirror.internal.frontier.v3.client.FrontierV3PilotDemandReceiptTransition;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceLocationArgument;
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

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Pilot-only acknowledgement armed before ordinary travel, retained through transfer, then read
 * after vanilla distance work and before the ordinary PM Post turn. It never queues scene
 * admission or changes ticket, chunk, canonical, cache, or lifecycle ownership.
 */
@EventBusSubscriber(modid = PaleMirrorMod.MOD_ID)
public final class FrontierV3PilotDemandHandshakeCommand {
    static final String COMMAND = "pale_mirror_pilot_demand_handshake";
    private static final TransitionAdapters TRANSITIONS = new TransitionAdapters();

    private FrontierV3PilotDemandHandshakeCommand() { }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(commandTree(source -> source.hasPermission(4), FrontierV3PilotDemandHandshakeCommand::arm));
    }

    /** The registered pilot grammar accepts resource identifiers without changing their wire form. */
    static <S> LiteralArgumentBuilder<S> commandTree(Predicate<S> authorized, ArmHandler<S> handler) {
        var z = RequiredArgumentBuilder.<S, Integer>argument("z", IntegerArgumentType.integer()).executes(context -> dispatchArm(context, handler));
        var y = RequiredArgumentBuilder.<S, Integer>argument("y", IntegerArgumentType.integer()).then(z);
        var x = RequiredArgumentBuilder.<S, Integer>argument("x", IntegerArgumentType.integer()).then(y);
        var attempt = RequiredArgumentBuilder.<S, String>argument("attempt", StringArgumentType.word()).then(x);
        var step = RequiredArgumentBuilder.<S, Integer>argument("step", IntegerArgumentType.integer(1)).then(attempt);
        var run = RequiredArgumentBuilder.<S, String>argument("run", StringArgumentType.word()).then(step);
        var dimension = RequiredArgumentBuilder.<S, ResourceLocation>argument("dimension", ResourceLocationArgument.id()).then(run);
        var assault = RequiredArgumentBuilder.<S, ResourceLocation>argument("assault", ResourceLocationArgument.id()).then(dimension);
        var request = RequiredArgumentBuilder.<S, String>argument("request", StringArgumentType.word()).then(assault);
        return LiteralArgumentBuilder.<S>literal(COMMAND).requires(authorized::test)
                .then(LiteralArgumentBuilder.<S>literal("arm").then(request));
    }

    private static <S> int dispatchArm(CommandContext<S> context, ArmHandler<S> handler) {
        FrontierV3PilotDemandReceiptTransition.Correlation correlation;
        try {
            correlation = new FrontierV3PilotDemandReceiptTransition.Correlation(StringArgumentType.getString(context, "run"),
                    IntegerArgumentType.getInteger(context, "step"), StringArgumentType.getString(context, "attempt"));
        }
        catch (IllegalArgumentException invalid) { return 0; }
        String request = StringArgumentType.getString(context, "request");
        String assault = context.getArgument("assault", ResourceLocation.class).toString();
        String dimension = context.getArgument("dimension", ResourceLocation.class).toString();
        BlockPos travelAnchor = new BlockPos(IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "y"),
                IntegerArgumentType.getInteger(context, "z"));
        if (!request.matches("[a-z][a-z0-9_-]{0,63}") || !assault.matches("assault:[a-z0-9][a-z0-9_-]{0,95}")) return 0;
        return handler.arm(context, new ArmInput(correlation, request, assault, dimension, travelAnchor));
    }

    private static int arm(CommandContext<CommandSourceStack> context, ArmInput input) {
        ResourceLocation dimensionId = ResourceLocation.tryParse(input.dimension());
        ServerPlayer player = context.getSource().getEntity() instanceof ServerPlayer value ? value : null;
        if (player == null || dimensionId == null) return 0;
        ResourceKey<Level> destination = ResourceKey.create(Registries.DIMENSION, dimensionId);
        ServerLevel destinationLevel = context.getSource().getServer().getLevel(destination);
        if (destinationLevel == null || player.serverLevel().dimension().equals(destination)) return 0;
        ArmedReceipt armed = new ArmedReceipt(input.correlation(), input.request(), input.assault(), input.dimension(), destination, input.travelAnchor());
        try {
            return TRANSITIONS.armAndDispatch(player.getUUID(), player.getGameProfile().getName(), armed.transitionArm(), command ->
                    context.getSource().getServer().getCommands().performPrefixedCommand(context.getSource(), command)) ? 1 : 0;
        } catch (RuntimeException failure) { TRANSITIONS.forget(player.getUUID()); throw failure; }
    }

    @FunctionalInterface
    interface ArmHandler<S> { int arm(CommandContext<S> context, ArmInput input); }

    record ArmInput(FrontierV3PilotDemandReceiptTransition.Correlation correlation, String request, String assault, String dimension, BlockPos travelAnchor) {
        FrontierV3PilotDemandReceiptTransition.Arm transitionArm() {
            return new FrontierV3PilotDemandReceiptTransition.Arm(correlation, request, assault, dimension, travelAnchor);
        }
    }

    /** The matching PlayerChangedDimensionEvent is the post-transfer observation boundary. */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        TRANSITIONS.observeTransfer(player.getUUID(), event.getTo().location().toString());
    }

    /** Runs after vanilla level/distance-manager work and before the default-priority PM scene Post listener. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerTick(ServerTickEvent.Post event) {
        // Commands drains the nested travel queue before this post-distance fence.  A returned
        // arm without its matching event is cancellation/no-event, never a later rearm token.
        for (FrontierV3PilotDemandReceiptTransition.Pending pending : TRANSITIONS.queuedWithoutTransfer()) {
            TRANSITIONS.resolveQueuedWithoutTransfer(pending.player());
        }
        for (FrontierV3PilotDemandReceiptTransition.Pending pending : TRANSITIONS.transferred()) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(pending.player());
            if (player == null) { TRANSITIONS.forget(pending.player()); continue; }
            ServerLevel destination = player.serverLevel();
            if (!TRANSITIONS.observeCurrentDestination(pending.player(), destination.dimension().location().toString())) continue;
            TRANSITIONS.observeAfterDistance(pending.player(), state -> postDistanceObservation(player, destination, state)).ifPresent(receipt ->
                    player.sendSystemMessage(Component.literal("PMV3_DIAG " + receipt.json())));
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) TRANSITIONS.forget(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TRANSITIONS.clear();
    }

    private static Optional<FrontierV3PilotDemandReceiptTransition.Observation<Receipt>> postDistanceObservation(ServerPlayer player, ServerLevel destination,
                                                                                                                   FrontierV3PilotDemandReceiptTransition.Pending pending) {
        ArmedReceipt armed = ArmedReceipt.from(pending.arm(), ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(pending.arm().destination())));
        if (!destination.dimension().equals(armed.destination())) return Optional.empty();
        FrontierV3PilotDemandReceiptTransition.Candidate candidate = pending.candidate().orElseGet(() -> Candidate.from(
                FrontierV3ServerLifecycle.pilotSceneDemandSnapshot(destination, new SubjectId(armed.assault()))).orElse(null));
        if (candidate == null) return Optional.empty();
        io.farfrontier.palemirror.frontier.v3.model.BlockPosition handoff = new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(
                candidate.handoff().getX(), candidate.handoff().getY(), candidate.handoff().getZ());
        FrontierV3SceneDemand.Snapshot demand = FrontierV3SceneDemand.observe(destination, handoff);
        FrontierV3PilotSceneDemandSnapshot snapshot = new FrontierV3PilotSceneDemandSnapshot(Optional.of(candidate.providerIdentity()), OptionalInt.of(1),
                Optional.of(handoff), demand.chunkLoaded(), demand.observerIds());
        TicketState ticket = ticketState(destination, candidate.handoff());
        Receipt receipt = Receipt.from(armed, Optional.of(player.getUUID()), Optional.of(player.blockPosition()), player.serverLevel() == destination, ticket, snapshot);
        return Optional.of(new FrontierV3PilotDemandReceiptTransition.Observation<>(receipt, receipt.reason() == Reason.ADMITTED, Optional.of(candidate)));
    }

    record ArmedReceipt(FrontierV3PilotDemandReceiptTransition.Correlation correlation, String request, String assault, String dimension, ResourceKey<Level> destination, BlockPos travelAnchor) {
        FrontierV3PilotDemandReceiptTransition.Arm transitionArm() { return new FrontierV3PilotDemandReceiptTransition.Arm(correlation, request, assault, dimension, travelAnchor); }
        static ArmedReceipt from(FrontierV3PilotDemandReceiptTransition.Arm arm, ResourceKey<Level> destination) {
            return new ArmedReceipt(arm.correlation(), arm.request(), arm.assault(), arm.destination(), destination, arm.travelAnchor());
        }
    }
    record Candidate(String providerIdentity, io.farfrontier.palemirror.frontier.v3.model.BlockPosition handoff) {
        static Optional<FrontierV3PilotDemandReceiptTransition.Candidate> from(FrontierV3PilotSceneDemandSnapshot snapshot) {
            if (snapshot.providerIdentity().isEmpty() || snapshot.exactCandidateCount().orElse(0) != 1 || snapshot.handoffPosition().isEmpty()) return Optional.empty();
            io.farfrontier.palemirror.frontier.v3.model.BlockPosition handoff = snapshot.handoffPosition().orElseThrow();
            return Optional.of(new FrontierV3PilotDemandReceiptTransition.Candidate(snapshot.providerIdentity().orElseThrow(), new BlockPos(handoff.x(), handoff.y(), handoff.z())));
        }
    }

    /** Adapter-facing composition shared by command, transfer, HIGHEST observation, and cleanup. */
    static final class TransitionAdapters {
        private final FrontierV3PilotDemandReceiptTransition.Custody custody = new FrontierV3PilotDemandReceiptTransition.Custody();
        boolean armAndDispatch(UUID player, String playerName, FrontierV3PilotDemandReceiptTransition.Arm arm,
                               FrontierV3PilotDemandReceiptTransition.Dispatcher dispatcher) {
            return custody.armAndDispatch(player, playerName, arm, dispatcher);
        }
        boolean observeTransfer(UUID player, String destination) { return custody.observeTransfer(player, destination); }
        boolean observeCurrentDestination(UUID player, String destination) { return custody.observeCurrentDestination(player, destination); }
        List<FrontierV3PilotDemandReceiptTransition.Pending> transferred() { return custody.transferred(); }
        List<FrontierV3PilotDemandReceiptTransition.Pending> queuedWithoutTransfer() { return custody.queuedWithoutTransfer(); }
        boolean resolveQueuedWithoutTransfer(UUID player) { return custody.resolveQueuedWithoutTransfer(player); }
        <T> Optional<T> observeAfterDistance(UUID player, java.util.function.Function<FrontierV3PilotDemandReceiptTransition.Pending,
                Optional<FrontierV3PilotDemandReceiptTransition.Observation<T>>> observer) { return custody.observeAfterDistance(player, observer); }
        void forget(UUID player) { custody.forget(player); }
        void clear() { custody.clear(); }
        boolean pending(UUID player) { return custody.pending(player); }
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

    record Receipt(FrontierV3PilotDemandReceiptTransition.Correlation correlation, String request, String assault, String destinationDimension, BlockPos travelAnchor, String playerId,
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
