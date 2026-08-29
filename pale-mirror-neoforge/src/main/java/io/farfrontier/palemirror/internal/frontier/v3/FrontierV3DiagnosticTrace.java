package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.PaleMirrorMod;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Bounded, non-canonical evidence that joins one physical player action to its accepted v3 transaction. */
final class FrontierV3DiagnosticTrace {
    private static final int MAX_ENTRIES = 256;
    private static final Map<MinecraftServer, Deque<Entry>> ENTRIES = new IdentityHashMap<>();

    private FrontierV3DiagnosticTrace() { }

    static void record(MinecraftServer server, String correlation, String kind, SubjectId subject, CommandResult result) {
        Objects.requireNonNull(server, "server"); Objects.requireNonNull(correlation, "correlation");
        Objects.requireNonNull(kind, "kind"); Objects.requireNonNull(subject, "subject"); Objects.requireNonNull(result, "result");
        if (!(result instanceof CommandResult.Accepted accepted)) return;
        Deque<Entry> entries = ENTRIES.computeIfAbsent(server, unused -> new ArrayDeque<>());
        Entry entry = new Entry(correlation, kind, subject.value(), accepted.commandId().value(), accepted.transactionId().value(), accepted.revision().value());
        entries.removeIf(candidate -> candidate.commandId().equals(entry.commandId()));
        entries.addLast(entry);
        while (entries.size() > MAX_ENTRIES) entries.removeFirst();
        PaleMirrorMod.LOGGER.info("PMV3_TRACE correlation={} kind={} subject={} command={} transaction={} revision={}",
                entry.correlation(), entry.kind(), entry.subject(), entry.commandId(), entry.transactionId(), entry.revision());
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

    record Entry(String correlation, String kind, String subject, String commandId, String transactionId, long revision) {
        Entry {
            if (correlation.isBlank() || kind.isBlank() || subject.isBlank() || commandId.isBlank() || transactionId.isBlank()) {
                throw new IllegalArgumentException("diagnostic trace entry is invalid");
            }
        }
    }
}
