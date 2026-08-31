package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierSceneBehaviors;
import io.farfrontier.palemirror.frontier.v3.model.SceneLease;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneCause;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Bounded, non-canonical evidence that joins one physical player action to its accepted v3 transaction. */
final class FrontierV3DiagnosticTrace {
    private static final int MAX_ENTRIES = 256;
    private static final Map<MinecraftServer, Deque<Entry>> ENTRIES = new IdentityHashMap<>();

    private FrontierV3DiagnosticTrace() { }

    /** Stable, bounded correlation for the one physical change at an exact canonical cell. */
    static String physicalDeltaCorrelation(BlockPosition position) {
        Objects.requireNonNull(position, "position");
        return "physical-delta:" + position.x() + "," + position.y() + "," + position.z();
    }

    /** One work-order trace, shared by its observed pickup and later physical cell placement. */
    static String routeConstructionCorrelation(SubjectId projectId) {
        Objects.requireNonNull(projectId, "route construction project");
        return "route-construction:" + projectId.value();
    }

    /** One exact cargo crossing between the two roots of the distributed hive organism. */
    static String hiveNutrientCorrelation(SubjectId transferId) {
        Objects.requireNonNull(transferId, "hive nutrient transfer");
        return "hive-nutrient:" + transferId.value();
    }

    /** One exact depot stack's irreversible hand-off to its named defender. */
    static String defenderEquipmentCorrelation(SubjectId itemId) {
        Objects.requireNonNull(itemId, "defender equipment item");
        return "defender-equipment:" + itemId.value();
    }

    static void record(MinecraftServer server, String correlation, String kind, SubjectId subject, CommandResult result) {
        record(server, correlation, kind, subject, result, Context.empty());
    }

    /** Records the bounded operation → lease → cargo/actor chain for one scene transition. */
    static void recordScene(MinecraftServer server, String kind, SceneLease lease, CommandResult result) {
        Objects.requireNonNull(lease, "scene lease");
        if (FrontierSceneBehaviors.isSettlementAssault(lease)) {
            SettlementAssaultSceneCause assault = FrontierSceneBehaviors.settlementAssault(lease);
            record(server, "assault:" + assault.assaultId().value(), kind, assault.assaultId(), result,
                    new Context(assault.assaultId().value(), lease.id().value(), "",
                            lease.members().stream().map(member -> member.actorId().value()).sorted().toList()));
            return;
        }
        var logistics = FrontierSceneBehaviors.logistics(lease);
        record(server, "operation:" + logistics.operationId().value(), kind, logistics.operationId(), result,
                new Context(logistics.operationId().value(), lease.id().value(), logistics.cargoId().value(),
                        lease.members().stream().map(member -> member.actorId().value()).sorted().toList()));
    }

    /**
     * Records a physical perception fact without losing the carrier provenance that admitted it.
     * The observing Scout is deliberately appended to the bounded scene-member list: it may be
     * near the carrier without being a participant in the caravan scene.
     */
    static void recordScoutSighting(MinecraftServer server, String correlation, SceneLease lease, SubjectId scoutId, CommandResult result) {
        Objects.requireNonNull(lease, "scene lease"); Objects.requireNonNull(scoutId, "scout id");
        List<String> actors = java.util.stream.Stream.concat(lease.members().stream().map(member -> member.actorId().value()),
                        java.util.stream.Stream.of(scoutId.value())).distinct().sorted().toList();
        record(server, correlation, "hot_scout_operation_observed", scoutId, result,
                new Context(FrontierSceneBehaviors.logistics(lease).operationId().value(), lease.id().value(), FrontierSceneBehaviors.logistics(lease).cargoId().value(), actors));
    }

    private static void record(MinecraftServer server, String correlation, String kind, SubjectId subject, CommandResult result, Context context) {
        Objects.requireNonNull(server, "server"); Objects.requireNonNull(correlation, "correlation");
        Objects.requireNonNull(kind, "kind"); Objects.requireNonNull(subject, "subject"); Objects.requireNonNull(result, "result"); Objects.requireNonNull(context, "context");
        if (!(result instanceof CommandResult.Accepted accepted)) return;
        Deque<Entry> entries = ENTRIES.computeIfAbsent(server, unused -> new ArrayDeque<>());
        Entry entry = new Entry(correlation, kind, subject.value(), accepted.commandId().value(), accepted.transactionId().value(), accepted.revision().value(), context);
        entries.removeIf(candidate -> candidate.commandId().equals(entry.commandId()));
        entries.addLast(entry);
        while (entries.size() > MAX_ENTRIES) entries.removeFirst();
        PaleMirrorMod.LOGGER.info("PMV3_TRACE correlation={} kind={} subject={} command={} transaction={} revision={} operation={} lease={} cargo={} actors={}",
                entry.correlation(), entry.kind(), entry.subject(), entry.commandId(), entry.transactionId(), entry.revision(),
                entry.context().operationId(), entry.context().leaseId(), entry.context().cargoId(), entry.context().actorIds());
    }

    static Optional<Entry> latest(MinecraftServer server, String correlation) {
        Objects.requireNonNull(server, "server"); Objects.requireNonNull(correlation, "correlation");
        Deque<Entry> entries = ENTRIES.get(server);
        if (entries == null) return Optional.empty();
        for (Iterator<Entry> iterator = entries.descendingIterator(); iterator.hasNext();) {
            Entry entry = iterator.next();
            if (entry.correlation().equals(correlation) || entry.commandId().equals(correlation)) return Optional.of(entry);
        }
        return Optional.empty();
    }

    static void forget(MinecraftServer server) { ENTRIES.remove(Objects.requireNonNull(server, "server")); }

    record Entry(String correlation, String kind, String subject, String commandId, String transactionId, long revision, Context context) {
        Entry(String correlation, String kind, String subject, String commandId, String transactionId, long revision) {
            this(correlation, kind, subject, commandId, transactionId, revision, Context.empty());
        }
        Entry {
            if (correlation.isBlank() || kind.isBlank() || subject.isBlank() || commandId.isBlank() || transactionId.isBlank()) {
                throw new IllegalArgumentException("diagnostic trace entry is invalid");
            }
            context = Objects.requireNonNull(context, "diagnostic trace context");
        }
    }

    /** Bounded noncanonical join keys only: never player UUIDs, coordinates or inventory. */
    record Context(String operationId, String leaseId, String cargoId, List<String> actorIds) {
        Context {
            operationId = Objects.requireNonNull(operationId, "trace operation"); leaseId = Objects.requireNonNull(leaseId, "trace lease");
            cargoId = Objects.requireNonNull(cargoId, "trace cargo"); actorIds = List.copyOf(Objects.requireNonNull(actorIds, "trace actors"));
            if (actorIds.size() > 32 || actorIds.stream().anyMatch(String::isBlank)
                    || (!operationId.isEmpty() && leaseId.isEmpty())) {
                throw new IllegalArgumentException("diagnostic trace context is invalid");
            }
        }
        static Context empty() { return new Context("", "", "", List.of()); }
    }
}
