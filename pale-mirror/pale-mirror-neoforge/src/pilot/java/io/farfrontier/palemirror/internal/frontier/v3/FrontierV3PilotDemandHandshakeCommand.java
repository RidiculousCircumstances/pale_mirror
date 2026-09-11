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
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pilot-only acknowledgement armed before ordinary travel and fulfilled immediately after the
 * ordinary destination transfer. It only reads current player/ticket/holder/runtime state; in
 * particular it never queues scene admission or changes ticket, chunk, canonical, cache, or
 * lifecycle ownership.
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
                                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                                        .then(Commands.argument("z", IntegerArgumentType.integer()).executes(
                                                                FrontierV3PilotDemandHandshakeCommand::arm)))))))));
    }

    private static int arm(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        String request = StringArgumentType.getString(context, "request");
        String assault = StringArgumentType.getString(context, "assault");
        String dimension = StringArgumentType.getString(context, "dimension");
        BlockPos travelAnchor = new BlockPos(IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "y"),
                IntegerArgumentType.getInteger(context, "z"));
        if (!request.matches("[a-z][a-z0-9_-]{0,63}") || !assault.matches("assault:[a-z0-9][a-z0-9_-]{0,95}")) return 0;
        ResourceLocation dimensionId = ResourceLocation.tryParse(dimension);
        ServerPlayer player = context.getSource().getEntity() instanceof ServerPlayer value ? value : null;
        if (player == null || dimensionId == null) return 0;
        ResourceKey<Level> destination = ResourceKey.create(Registries.DIMENSION, dimensionId);
        if (context.getSource().getServer().getLevel(destination) == null) return 0;
        ARMED.arm(player.getUUID(), new ArmedReceipt(request, assault, dimension, destination, travelAnchor));
        return 1;
    }

    /** The matching PlayerChangedDimensionEvent is the post-transfer observation boundary. */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ARMED.fulfillAfterOrdinaryTransfer(player.getUUID(), event.getTo()).ifPresent(armed -> {
            ServerLevel destination = player.serverLevel();
            FrontierV3PilotDemandHandshakeCommand.receipt(player, destination, armed).ifPresent(value ->
                    player.sendSystemMessage(Component.literal("PMV3_DIAG " + value.json())));
        });
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ARMED.forget(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ARMED.clear();
    }

    private static Optional<Receipt> receipt(ServerPlayer player, ServerLevel destination, ArmedReceipt armed) {
        if (!destination.dimension().equals(armed.destination())) return Optional.empty();
        FrontierV3PilotSceneDemandSnapshot snapshot = FrontierV3ServerLifecycle.pilotSceneDemandSnapshot(destination, new SubjectId(armed.assault()));
        BlockPos handoff = snapshot == null ? null : snapshot.handoffPosition().map(position -> new BlockPos(position.x(), position.y(), position.z())).orElse(null);
        TicketState ticket = handoff == null ? TicketState.absent() : ticketState(destination, handoff);
        boolean destinationObserved = player.serverLevel() == destination;
        return Optional.of(Receipt.from(armed.request(), armed.assault(), armed.dimension(), armed.travelAnchor(), Optional.of(player.getUUID()),
                Optional.of(player.blockPosition()), destinationObserved, ticket, snapshot));
    }

    record ArmedReceipt(String request, String assault, String dimension, ResourceKey<Level> destination, BlockPos travelAnchor) { }

    /** One pending receipt per player; a different transfer cannot consume it and logout/server stop clears it. */
    static final class ArmedReceipts {
        private final Map<UUID, ArmedReceipt> receipts = new HashMap<>();
        void arm(UUID player, ArmedReceipt receipt) { receipts.put(player, receipt); }
        Optional<ArmedReceipt> fulfillAfterOrdinaryTransfer(UUID player, ResourceKey<Level> destination) {
            ArmedReceipt receipt = receipts.get(player);
            if (receipt == null || !receipt.destination().equals(destination)) return Optional.empty();
            receipts.remove(player);
            return Optional.of(receipt);
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

    record Receipt(String request, String assault, String destinationDimension, BlockPos travelAnchor, String playerId,
                   Optional<BlockPos> serverPlayerPosition, boolean destinationObserved, TicketState ticket, Optional<String> providerIdentity,
                   Optional<Integer> exactCandidateCount, Optional<io.farfrontier.palemirror.frontier.v3.model.BlockPosition> candidateHandoff,
                   boolean demandChunkLoaded, java.util.Set<UUID> demandObserverIds, boolean requestedObserverPresent, Reason reason) {
        static Receipt from(String request, String assault, String dimension, BlockPos travelAnchor, Optional<UUID> player,
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
            return new Receipt(request, assault, dimension, travelAnchor, playerId, serverPlayerPosition, destinationObserved, ticket, provider,
                    candidates, handoff, chunkLoaded, java.util.Set.copyOf(observers), requestedObserver, reason);
        }

        String json() {
            JsonObject value = new JsonObject(); value.addProperty("schema", 1); value.addProperty("kind", "demand_handshake");
            value.addProperty("id", request); value.addProperty("assault", assault); value.addProperty("destinationDimension", destinationDimension);
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
