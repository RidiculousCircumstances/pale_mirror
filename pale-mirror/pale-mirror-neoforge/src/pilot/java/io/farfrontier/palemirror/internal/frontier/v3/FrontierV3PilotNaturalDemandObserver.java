package io.farfrontier.palemirror.internal.frontier.v3;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3PilotChunkMapAccessor;
import io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3PilotDistanceManagerAccessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.SortedArraySet;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Non-packaged pilot observer for the complete actual vanilla PLAYER-ticket episode.
 * It never mutates command, ticket, task, save or chunk state; the separate pilot command owns
 * the sole one-use shutdown admission after this observer freshly reads the retained episode.
 */
@EventBusSubscriber(modid = PaleMirrorMod.MOD_ID)
public final class FrontierV3PilotNaturalDemandObserver {
    private static final String ARM_KIND = "f02b-natural-demand-episode-arm";
    private static final String ARM_ACK_KIND = "f02b-natural-demand-episode-arm-ack";
    private static final String STOP_OUTCOME_KIND = "f02b-natural-demand-stop-outcome";
    private static final String RECEIPT_KIND = "f02b-natural-demand-episode";
    private static final Map<MinecraftServer, Episode> EPISODES = new IdentityHashMap<>();

    private FrontierV3PilotNaturalDemandObserver() { }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        Episode episode = EPISODES.get(server);
        try {
            if (episode == null) {
                episode = arm(server);
                if (episode == null) return;
                EPISODES.put(server, episode);
                publishArmAcknowledgement(episode);
            }
            boolean released = normalDemandLossReleased(episode);
            // The normal-demand receipt may be published by a neighbouring Post listener.
            // First retain that same server-tick's complete holder snapshot; only a following
            // server tick may use release as the closure boundary. This is scheduler ordering,
            // not an elapsed-time settling heuristic.
            boolean releasedForEligibility = episode.releaseObserved;
            Observation observation = observe(server, episode, releasedForEligibility);
            episode.releaseObserved |= released;
            FrontierV3PilotNaturalDemandEpisode.Status prior = episode.state.status();
            FrontierV3PilotNaturalDemandEpisode.Status status = episode.state.observe(observation.playerTickets, observation.holders, releasedForEligibility);
            if (status == FrontierV3PilotNaturalDemandEpisode.Status.INVALID && !episode.invalidPublished) {
                publish(episode, "invalid", observation, episode.state.failure());
                episode.invalidPublished = true;
            } else if (status == FrontierV3PilotNaturalDemandEpisode.Status.ELIGIBLE && !episode.eligiblePublished) {
                publish(episode, "eligible", observation, null);
                episode.eligiblePublished = true;
            } else if (prior == FrontierV3PilotNaturalDemandEpisode.Status.ELIGIBLE && status != prior && !episode.invalidPublished) {
                publish(episode, "invalid", observation, "eligible observation was invalidated");
                episode.invalidPublished = true;
            }
        } catch (IOException | IllegalArgumentException failure) {
            throw new IllegalStateException("pilot natural-demand observer could not retain exact evidence", failure);
        }
    }

    static FrontierV3PilotNaturalDemandEpisode.Status observeForTest(FrontierV3PilotNaturalDemandEpisode state,
            Set<FrontierV3PilotNaturalDemandEpisode.ChunkKey> playerTickets,
            Map<FrontierV3PilotNaturalDemandEpisode.ChunkKey, FrontierV3PilotNaturalDemandEpisode.Holder> holders, boolean released) {
        return state.observe(playerTickets, holders, released);
    }

    /**
     * Called only from the pilot command's RCON-dispatched server-thread action.  In particular,
     * no lifecycle file is read here to decide whether a halt may occur: the retained in-memory
     * episode owns identity, release and closure truth at the command/use boundary.
     */
    static void admitGracefulStop(MinecraftServer server, String nonce, Runnable haltAction) {
        Episode episode = EPISODES.get(server);
        if (episode == null) throw new IllegalStateException("pilot natural-demand stop admission is unarmed");
        try {
            if (!episode.releaseObserved || !server.getPlayerList().getPlayers().isEmpty()
                    || !FrontierV3ServerLifecycle.normalDemandLossReleased(server)) {
                throw new IllegalStateException("pilot natural-demand stop admission is unarmed or natural demand is still live");
            }
            episode.stopAdmission.admit(server, System.getProperty(FrontierV3PilotLifecycleSignal.RUN_ID_PROPERTY, ""),
                    ProcessHandle.current().pid(), nonce, () -> {
                        Observation observation = observe(server, episode, true);
                        FrontierV3PilotNaturalDemandEpisode.Status status = episode.state.observe(
                                observation.playerTickets, observation.holders, true);
                        if (status == FrontierV3PilotNaturalDemandEpisode.Status.INVALID && !episode.invalidPublished) {
                            try {
                                publish(episode, "invalid", observation, episode.state.failure());
                                episode.invalidPublished = true;
                            } catch (IOException failure) {
                                throw new IllegalStateException("pilot natural-demand invalidation evidence could not be retained", failure);
                            }
                        }
                        return status;
                    }, haltAction);
            publishStopOutcome(episode, nonce, "admitted", null);
        } catch (IllegalStateException failure) {
            if (episode.stopAdmission.matchesNonce(nonce)) publishStopOutcome(episode, nonce, "rejected", failure.getMessage());
            throw failure;
        }
    }

    private static Episode arm(MinecraftServer server) throws IOException {
        String configured = System.getProperty(FrontierV3PilotLifecycleSignal.CONTROL_DIRECTORY_PROPERTY, "");
        String serverRunId = System.getProperty(FrontierV3PilotLifecycleSignal.RUN_ID_PROPERTY, "");
        if (configured.isBlank() || !serverRunId.matches("[0-9a-f-]{36}")) return null;
        Path directory = Path.of(configured);
        Path token = directory.resolve("natural-demand-episode-arm-" + serverRunId + ".json");
        if (!Files.isRegularFile(token)) return null;
        JsonObject identity = JsonParser.parseString(Files.readString(directory.resolve("identity.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject arm = JsonParser.parseString(Files.readString(token, StandardCharsets.UTF_8)).getAsJsonObject();
        if (arm.get("schema") == null || arm.get("schema").getAsInt() != 1 || arm.get("kind") == null
                || !ARM_KIND.equals(arm.get("kind").getAsString()) || arm.get("identity") == null
                || !identity.equals(arm.getAsJsonObject("identity")) || arm.get("serverRunId") == null
                || !serverRunId.equals(arm.get("serverRunId").getAsString()) || arm.get("serverPid") == null
                || arm.get("serverPid").getAsLong() != ProcessHandle.current().pid() || arm.get("stopAdmissionNonce") == null
                || !arm.get("stopAdmissionNonce").getAsString().matches("[0-9a-f-]{36}")) {
            throw new IllegalArgumentException("natural-demand arm is stale, foreign or malformed");
        }
        return new Episode(directory, identity, server, serverRunId, ProcessHandle.current().pid(), arm.get("stopAdmissionNonce").getAsString());
    }

    private static boolean normalDemandLossReleased(Episode episode) throws IOException {
        Path signal = episode.directory.resolve("signals/normal_demand_loss_release-" + episode.serverRunId + ".json");
        if (!Files.isRegularFile(signal)) return false;
        JsonObject value = JsonParser.parseString(Files.readString(signal, StandardCharsets.UTF_8)).getAsJsonObject();
        if (value.get("schema") == null || value.get("schema").getAsInt() != 1 || value.get("signal") == null
                || !"normal_demand_loss_release".equals(value.get("signal").getAsString()) || value.get("suffix") == null
                || !episode.serverRunId.equals(value.get("suffix").getAsString()) || value.get("identity") == null
                || !episode.identity.equals(value.getAsJsonObject("identity")) || value.get("detail") == null
                || value.getAsJsonObject("detail").get("serverRunId") == null
                || !episode.serverRunId.equals(value.getAsJsonObject("detail").get("serverRunId").getAsString())
                || value.getAsJsonObject("detail").get("serverPid") == null
                || episode.serverPid != value.getAsJsonObject("detail").get("serverPid").getAsLong()) {
            throw new IllegalArgumentException("normal demand-loss release is stale, foreign, or malformed");
        }
        return true;
    }

    private static Observation observe(MinecraftServer server, Episode episode, boolean released) {
        Set<FrontierV3PilotNaturalDemandEpisode.ChunkKey> playerTickets = new LinkedHashSet<>();
        Map<FrontierV3PilotNaturalDemandEpisode.ChunkKey, ChunkHolder> currentHolders = new LinkedHashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            String dimension = level.dimension().location().toString();
            ChunkMap map = level.getChunkSource().chunkMap;
            FrontierV3PilotChunkMapAccessor chunkMap = (FrontierV3PilotChunkMapAccessor) map;
            DistanceManager manager = chunkMap.paleMirror$distanceManager();
            Long2ObjectMap<SortedArraySet<Ticket<?>>> tickets = ((FrontierV3PilotDistanceManagerAccessor) manager).paleMirror$tickets();
            for (Long2ObjectMap.Entry<SortedArraySet<Ticket<?>>> entry : tickets.long2ObjectEntrySet()) {
                if (entry.getValue().stream().anyMatch(ticket -> ticket.getType() == TicketType.PLAYER)) {
                    playerTickets.add(new FrontierV3PilotNaturalDemandEpisode.ChunkKey(dimension, entry.getLongKey()));
                }
            }
            for (Long2ObjectMap.Entry<ChunkHolder> entry : chunkMap.paleMirror$updatingChunks().long2ObjectEntrySet()) {
                currentHolders.put(new FrontierV3PilotNaturalDemandEpisode.ChunkKey(dimension, entry.getLongKey()), entry.getValue());
            }
        }
        Set<FrontierV3PilotNaturalDemandEpisode.ChunkKey> observed = new LinkedHashSet<>(episode.state.trackedChunks());
        observed.addAll(currentHolders.keySet());
        Map<FrontierV3PilotNaturalDemandEpisode.ChunkKey, FrontierV3PilotNaturalDemandEpisode.Holder> holders = new LinkedHashMap<>();
        for (FrontierV3PilotNaturalDemandEpisode.ChunkKey position : observed) {
            ChunkHolder current = currentHolders.get(position);
            ChunkHolder retained = episode.holders.get(position);
            // Once admitted, preserve the exact object even after it has left the current
            // updating map.  This is only a reference for a server-thread readiness read;
            // it neither creates demand nor retains a chunk in vanilla's lifecycle.
            if (current != null && retained == null && !released) {
                episode.holders.put(position, current);
                retained = current;
            }
            ChunkHolder holder = current != null ? current : retained;
            if (holder != null) holders.put(position, new FrontierV3PilotNaturalDemandEpisode.Holder(
                    holder, System.identityHashCode(holder), holder.getGenerationRefCount(), holder.isReadyForSaving()));
        }
        return new Observation(playerTickets, holders);
    }

    private static void publish(Episode episode, String status, Observation observation, String failure) throws IOException {
        JsonObject value = new JsonObject();
        value.addProperty("schema", 1); value.addProperty("kind", RECEIPT_KIND); value.addProperty("status", status);
        value.add("identity", episode.identity);
        JsonObject server = new JsonObject(); server.addProperty("serverRunId", episode.serverRunId); server.addProperty("serverPid", episode.serverPid); value.add("server", server);
        value.addProperty("released", true);
        JsonArray members = new JsonArray();
        if ("eligible".equals(status)) {
            for (FrontierV3PilotNaturalDemandEpisode.Member member : episode.state.terminalMembers(observation.holders).values()) {
                JsonObject item = new JsonObject(); item.addProperty("dimension", member.dimension()); item.addProperty("x", member.x()); item.addProperty("z", member.z());
                item.addProperty("holderDiagnostic", member.diagnosticIdentity()); item.addProperty("generationRefCount", member.generationRefCount());
                item.addProperty("readyForSaving", member.readyForSaving()); item.addProperty("terminal", member.terminal()); members.add(item);
            }
        }
        value.add("members", members);
        if (failure != null) value.addProperty("failure", failure);
        String text = value + "\n";
        Path target = episode.directory.resolve("signals/natural-demand-episode-" + status + "-" + episode.serverRunId + ".json");
        FrontierV3LifecycleFilePublisher.publish(episode.directory.resolve("staging"), target, text);
    }

    private static void publishArmAcknowledgement(Episode episode) throws IOException {
        JsonObject value = baseReceipt(episode, ARM_ACK_KIND, "armed");
        value.addProperty("stopAdmissionNonce", episode.stopAdmissionNonce);
        Path target = episode.directory.resolve("signals/natural-demand-episode-armed-" + episode.serverRunId + ".json");
        FrontierV3LifecycleFilePublisher.publish(episode.directory.resolve("staging"), target, value + "\n");
    }

    private static void publishStopOutcome(Episode episode, String nonce, String status, String reason) {
        try {
            JsonObject value = baseReceipt(episode, STOP_OUTCOME_KIND, status);
            value.addProperty("stopAdmissionNonce", nonce);
            if (reason != null) value.addProperty("reason", reason);
            Path target = episode.directory.resolve("signals/natural-demand-stop-outcome-" + episode.serverRunId + "-" + nonce + ".json");
            FrontierV3LifecycleFilePublisher.publish(episode.directory.resolve("staging"), target, value + "\n");
        } catch (IOException failure) {
            throw new IllegalStateException("pilot natural-demand stop outcome could not be retained", failure);
        }
    }

    private static JsonObject baseReceipt(Episode episode, String kind, String status) {
        JsonObject value = new JsonObject();
        value.addProperty("schema", 1); value.addProperty("kind", kind); value.addProperty("status", status);
        value.add("identity", episode.identity);
        JsonObject server = new JsonObject(); server.addProperty("serverRunId", episode.serverRunId); server.addProperty("serverPid", episode.serverPid); value.add("server", server);
        return value;
    }

    private record Observation(Set<FrontierV3PilotNaturalDemandEpisode.ChunkKey> playerTickets,
            Map<FrontierV3PilotNaturalDemandEpisode.ChunkKey, FrontierV3PilotNaturalDemandEpisode.Holder> holders) { }
    private static final class Episode {
        private final Path directory; private final JsonObject identity; private final String serverRunId; private final long serverPid; private final String stopAdmissionNonce;
        private final FrontierV3PilotNaturalDemandEpisode state = new FrontierV3PilotNaturalDemandEpisode();
        private final Map<FrontierV3PilotNaturalDemandEpisode.ChunkKey, ChunkHolder> holders = new LinkedHashMap<>();
        private final FrontierV3PilotNaturalDemandStopAdmission stopAdmission;
        private boolean releaseObserved;
        private boolean eligiblePublished; private boolean invalidPublished;
        private Episode(Path directory, JsonObject identity, MinecraftServer server, String serverRunId, long serverPid, String stopAdmissionNonce) {
            this.directory = directory; this.identity = identity; this.serverRunId = serverRunId; this.serverPid = serverPid; this.stopAdmissionNonce = stopAdmissionNonce;
            this.stopAdmission = new FrontierV3PilotNaturalDemandStopAdmission(server, serverRunId, serverPid, stopAdmissionNonce);
        }
    }
}
