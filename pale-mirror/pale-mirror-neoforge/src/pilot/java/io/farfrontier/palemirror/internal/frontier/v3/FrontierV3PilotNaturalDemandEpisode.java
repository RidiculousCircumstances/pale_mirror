package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pilot-only server-thread state for one naturally streamed player-ticket episode.
 * Membership is the complete observed ChunkMap holder set in every server level, not merely
 * direct PLAYER keys: vanilla acquires every generation-task dependency before scheduling work.
 */
final class FrontierV3PilotNaturalDemandEpisode {
    enum Status { ARMED, WAITING_FOR_WITHDRAWAL, WAITING_FOR_READINESS, ELIGIBLE, INVALID }

    record ChunkKey(String dimension, long position) {
        ChunkKey {
            if (dimension == null || dimension.isBlank()) throw new IllegalArgumentException("dimension is invalid");
        }
    }

    /** identity is the retained in-process holder reference; diagnosticIdentity is serialized only for diagnosis. */
    record Holder(Object identity, int diagnosticIdentity, int generationRefCount, boolean readyForSaving) {
        Holder {
            Objects.requireNonNull(identity, "holder identity");
            if (generationRefCount < 0) throw new IllegalArgumentException("holder observation is invalid");
        }
    }

    record Member(String dimension, int x, int z, int diagnosticIdentity, int generationRefCount, boolean readyForSaving, String terminal) { }

    private final Map<ChunkKey, Object> members = new LinkedHashMap<>();
    private Status status = Status.ARMED;
    private boolean sawPlayerDemand;
    private String failure;

    Status observe(Set<ChunkKey> playerTickets, Map<ChunkKey, Holder> completeHolders, boolean demandLossReleased) {
        Objects.requireNonNull(playerTickets, "playerTickets");
        Objects.requireNonNull(completeHolders, "completeHolders");
        if (status == Status.INVALID) return status;
        if (status == Status.ELIGIBLE && (!playerTickets.isEmpty() || hasLateDependency(completeHolders))) {
            return invalidate("late natural demand, holder membership, generation claim, or save dependency");
        }

        // A task's complete dependency cache is acquired synchronously in ChunkGenerationTask.create.
        // Before release, retaining all live ChunkMap holders is a conservative, behavior-neutral
        // superset. After release, a previously unseen holder proves the snapshot was incomplete.
        for (Map.Entry<ChunkKey, Holder> entry : completeHolders.entrySet()) {
            Object previous = members.putIfAbsent(entry.getKey(), entry.getValue().identity());
            if (previous != null && previous != entry.getValue().identity()) return invalidate("natural holder was replaced or revived");
            if (demandLossReleased && previous == null) return invalidate("late natural holder membership after demand withdrawal");
        }
        sawPlayerDemand |= !playerTickets.isEmpty();
        for (ChunkKey playerTicket : playerTickets) {
            if (!completeHolders.containsKey(playerTicket)) return invalidate("natural player ticket has no complete holder observation");
        }
        if (!demandLossReleased) return status;
        if (!sawPlayerDemand) return invalidate("natural demand episode never observed a player ticket");
        if (!playerTickets.isEmpty()) return status = Status.WAITING_FOR_WITHDRAWAL;
        if (members.isEmpty()) return invalidate("natural demand episode is empty");

        boolean pending = false;
        for (Map.Entry<ChunkKey, Object> entry : members.entrySet()) {
            Holder holder = completeHolders.get(entry.getKey());
            // The actual observer retains the exact holder object after it leaves the current map.
            if (holder == null) return invalidate("natural holder became unavailable before save readiness");
            if (holder.identity() != entry.getValue()) return invalidate("natural holder was replaced or revived");
            if (holder.generationRefCount() != 0 || !holder.readyForSaving()) pending = true;
        }
        return status = pending ? Status.WAITING_FOR_READINESS : Status.ELIGIBLE;
    }

    Status status() { return status; }
    String failure() { return failure; }
    Set<ChunkKey> trackedChunks() { return Set.copyOf(members.keySet()); }

    Map<ChunkKey, Member> terminalMembers(Map<ChunkKey, Holder> completeHolders) {
        if (status != Status.ELIGIBLE) throw new IllegalStateException("natural demand episode is not eligible");
        Map<ChunkKey, Member> result = new LinkedHashMap<>();
        Comparator<ChunkKey> stableOrder = Comparator.comparing(ChunkKey::dimension).thenComparingLong(ChunkKey::position);
        for (ChunkKey chunk : members.keySet().stream().sorted(stableOrder).toList()) {
            Object identity = members.get(chunk);
            Holder holder = completeHolders.get(chunk);
            if (holder == null || holder.identity() != identity || holder.generationRefCount() != 0 || !holder.readyForSaving()) {
                throw new IllegalStateException("natural demand episode changed after eligibility");
            }
            result.put(chunk, new Member(chunk.dimension(), (int) chunk.position(), (int) (chunk.position() >> 32),
                    holder.diagnosticIdentity(), holder.generationRefCount(), holder.readyForSaving(), "zero_ready"));
        }
        return Collections.unmodifiableMap(result);
    }

    private boolean hasLateDependency(Map<ChunkKey, Holder> completeHolders) {
        if (completeHolders.size() != members.size()) return true;
        for (Map.Entry<ChunkKey, Object> entry : members.entrySet()) {
            Holder holder = completeHolders.get(entry.getKey());
            if (holder == null || holder.identity() != entry.getValue() || holder.generationRefCount() != 0 || !holder.readyForSaving()) return true;
        }
        return false;
    }

    private Status invalidate(String reason) {
        status = Status.INVALID;
        failure = reason;
        return status;
    }
}
