package io.farfrontier.palemirror.internal.frontier.v3.client;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Pilot-only custody for one travel/request attempt.  It deliberately knows no scene, ticket,
 * or admission API: adapters supply the real command dispatch, transfer event, and read-only
 * post-distance observation.  A nested Minecraft command queues behind its owning command;
 * custody therefore survives dispatcher return until either that queue produces its transfer or
 * the first post-distance boundary proves the queued command produced none.
 */
public final class FrontierV3PilotDemandReceiptTransition {
    private FrontierV3PilotDemandReceiptTransition() { }

    public record Correlation(String runId, int actionStep, String actionAttempt) {
        public Correlation {
            UUID.fromString(Objects.requireNonNull(runId, "run ID"));
            if (actionStep < 1) throw new IllegalArgumentException("action step");
            UUID.fromString(Objects.requireNonNull(actionAttempt, "action attempt"));
        }
    }

    public record Arm(Correlation correlation, String request, String assault, String destination, BlockPos travelAnchor) {
        public Arm {
            Objects.requireNonNull(correlation, "correlation"); Objects.requireNonNull(request, "request");
            Objects.requireNonNull(assault, "assault"); Objects.requireNonNull(destination, "destination");
            Objects.requireNonNull(travelAnchor, "travel anchor");
        }
    }

    /** The provider identity and its immutable handoff retained before normal scene consumption. */
    public record Candidate(String providerIdentity, BlockPos handoff) {
        public Candidate { Objects.requireNonNull(providerIdentity, "provider identity"); Objects.requireNonNull(handoff, "handoff"); }
    }

    /** Exact former player command semantics; server adapters pass it to Minecraft's dispatcher verbatim. */
    public static String travelCommand(Arm arm, String playerName) {
        Objects.requireNonNull(arm, "arm"); Objects.requireNonNull(playerName, "player name");
        BlockPos target = arm.travelAnchor();
        return "execute in " + arm.destination() + " run tp " + playerName + " " + target.getX() + " " + target.getY() + " " + target.getZ();
    }

    /** Client command which arms the server before that server dispatches {@link #travelCommand}. */
    public static String armCommand(Arm arm) {
        return "pale_mirror_pilot_demand_handshake arm " + arm.request() + " " + arm.assault() + " " + arm.destination() + " "
                + arm.correlation().runId() + " " + arm.correlation().actionStep() + " " + arm.correlation().actionAttempt() + " "
                + arm.travelAnchor().getX() + " " + arm.travelAnchor().getY() + " " + arm.travelAnchor().getZ();
    }

    @FunctionalInterface
    public interface Dispatcher { void dispatch(String command); }

    public record Pending(UUID player, Arm arm, boolean dispatchReturned, boolean transferObserved, Optional<Candidate> candidate) {
        public Pending {
            Objects.requireNonNull(player, "player"); Objects.requireNonNull(arm, "arm");
            candidate = candidate == null ? Optional.empty() : candidate;
        }
        Pending dispatched() { return new Pending(player, arm, true, transferObserved, candidate); }
        Pending transferred() { return new Pending(player, arm, dispatchReturned, true, candidate); }
        Pending retain(Optional<Candidate> observed) { return new Pending(player, arm, dispatchReturned, transferObserved, candidate.isPresent() ? candidate : observed); }
    }

    public record Observation<T>(T value, boolean admitted, Optional<Candidate> candidate) {
        public Observation { candidate = candidate == null ? Optional.empty() : candidate; }
    }

    /**
     * Shared adapter composition.  Nested {@code performPrefixedCommand} calls return while the
     * owning command's execution context still holds the exact travel command.  The HIGHEST
     * post-distance adapter resolves a returned-without-transfer arm only after that queue drain.
     */
    public static final class Custody {
        private final Map<UUID, Pending> pending = new HashMap<>();

        public boolean armAndDispatch(UUID player, String playerName, Arm arm, Dispatcher dispatcher) {
            Objects.requireNonNull(player, "player"); Objects.requireNonNull(dispatcher, "dispatcher");
            if (pending.containsKey(player)) return false;
            Pending armed = new Pending(player, arm, false, false, Optional.empty()); pending.put(player, armed);
            try {
                dispatcher.dispatch(travelCommand(arm, playerName));
            } catch (RuntimeException failure) {
                pending.remove(player, armed); throw failure;
            }
            pending.computeIfPresent(player, (ignored, current) -> current == armed || current.arm().equals(arm) ? current.dispatched() : current);
            return pending.containsKey(player);
        }

        /** Called only from the real dimension-change adapter during command dispatch. */
        public boolean observeTransfer(UUID player, String destination) {
            Pending armed = pending.get(player);
            if (armed == null || armed.transferObserved() || !armed.arm().destination().equals(destination)) return false;
            pending.put(player, armed.transferred()); return true;
        }

        /** A post-transfer departure invalidates, rather than retargets, this exact arm. */
        public boolean observeCurrentDestination(UUID player, String destination) {
            Pending armed = pending.get(player);
            if (armed == null) return false;
            if (!armed.arm().destination().equals(destination)) { pending.remove(player); return false; }
            return armed.transferObserved();
        }

        public List<Pending> transferred() {
            return new ArrayList<>(pending.values()).stream().filter(Pending::transferObserved).toList();
        }

        /** Called at the queue-drain/post-distance fence: a queued command that never transferred is terminal. */
        public boolean resolveQueuedWithoutTransfer(UUID player) {
            Pending armed = pending.get(player);
            if (armed == null || !armed.dispatchReturned() || armed.transferObserved()) return false;
            pending.remove(player); return true;
        }

        public List<Pending> queuedWithoutTransfer() {
            return new ArrayList<>(pending.values()).stream().filter(value -> value.dispatchReturned() && !value.transferObserved()).toList();
        }

        /** Post-distance observations retain the first exact candidate until one admitted send. */
        public <T> Optional<T> observeAfterDistance(UUID player, Function<Pending, Optional<Observation<T>>> observer) {
            Pending armed = pending.get(player);
            if (armed == null || !armed.transferObserved()) return Optional.empty();
            Optional<Observation<T>> observed = observer.apply(armed);
            if (observed.isEmpty()) return Optional.empty();
            Observation<T> value = observed.orElseThrow();
            Pending retained = armed.retain(value.candidate());
            if (value.admitted()) { pending.remove(player); return Optional.of(value.value()); }
            pending.put(player, retained); return Optional.empty();
        }

        public void forget(UUID player) { pending.remove(player); }
        public void clear() { pending.clear(); }
        public boolean pending(UUID player) { return pending.containsKey(player); }
        public Optional<Pending> pendingValue(UUID player) { return Optional.ofNullable(pending.get(player)); }
    }
}
