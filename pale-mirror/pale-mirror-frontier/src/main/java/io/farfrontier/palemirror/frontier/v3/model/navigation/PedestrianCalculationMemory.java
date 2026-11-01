package io.farfrontier.palemirror.frontier.v3.model.navigation;

import java.util.*;

/** Completed reads owned by one unfinished calculation, independent of the shared
 * result cache. Immutable facts only: no reader, parent session, actor or world writer. */
public final class PedestrianCalculationMemory {
    // Charge metadata, dependency tokens and packed coordinates. A scalar answer
    // is not a full journey. A caller's acknowledged reads are not an LRU cache:
    // evicting its prefix can make a root-resumed calculation repeat forever.
    private static final long MAX_RETAINED_UNITS = 131_072;
    private static final PedestrianCalculationMemory EMPTY = new PedestrianCalculationMemory(List.of());
    private sealed interface Fence permits Exact, Regional {
        boolean matches(PedestrianRouteGeometry geometry);
        long retainedUnits();
    }
    private record Exact(Object version) implements Fence {
        public boolean matches(PedestrianRouteGeometry geometry) { return version.equals(geometry.version()); }
        public long retainedUnits() { return 1; }
    }
    private record Regional(PedestrianRegionDependencies dependencies, Object baseline) implements Fence {
        public boolean matches(PedestrianRouteGeometry geometry) {
            return geometry.regionalRevision().filter(value -> value.baseline() == baseline).isPresent()
                    && dependencies.matches(geometry);
        }
        public long retainedUnits() { return 2L + dependencies.retainedUnits(); }
    }
    private sealed interface Saved permits Direct, Packed {
        PedestrianRouteResult restore();
        long coordinates();
    }
    private record Direct(PedestrianRouteResult result) implements Saved {
        public PedestrianRouteResult restore() { return result; }
        public long coordinates() { return result.route().size(); }
    }
    /** Lossless existing RLE, rather than retaining every distant block coordinate. */
    private record Packed(PedestrianRouteResult.Status status, PedestrianPathProof path, long work, int regions, String reason) implements Saved {
        public PedestrianRouteResult restore() { return new PedestrianRouteResult(status, path.surfaces(), work, regions, reason); }
        public long coordinates() { return 1L + 2L * path.runs().size(); }
    }
    public static final class Read {
        private final PedestrianDemandKey key;
        private final Saved saved;
        private final Fence fence;
        private Read(PedestrianRouteRequest request, PedestrianRouteResult result, Fence fence) {
            if (!capturable(result)) throw new IllegalArgumentException("calculation memory needs completed evidence");
            this.key = PedestrianDemandKey.of(request); this.fence = fence;
            this.saved = result.route().isEmpty() ? new Direct(result) : new Packed(result.status(),
                    PedestrianPathProof.from(result.route()), result.workUnits(), result.regions(), result.reason());
        }
        static Read conservative(PedestrianRouteRequest request, PedestrianRouteResult result) {
            return new Read(request, result, new Exact(request.version()));
        }
        static Read regional(PedestrianRouteRequest request, PedestrianRouteResult result,
                             PedestrianRegionDependencies dependencies) {
            return request.geometry().regionalRevision().map(revision -> new Read(request, result,
                    new Regional(dependencies, revision.baseline()))).orElseGet(() -> conservative(request, result));
        }
        public PedestrianRouteResult result() { return saved.restore(); }
        long retainedUnits() { return 32L + fence.retainedUnits() + saved.coordinates(); }
        boolean matches(PedestrianRouteRequest request) {
            return key.equals(PedestrianDemandKey.of(request)) && fence.matches(request.geometry());
        }
    }
    static boolean capturable(PedestrianRouteResult result) { return result.status() != PedestrianRouteResult.Status.PLANNING && !result.awaitable(); }
    private final List<Read> reads;
    /** Derived lookup over these same immutable facts, never a second result authority. */
    private final Map<PedestrianDemandKey, Read> byDemand;
    private PedestrianCalculationMemory(List<Read> reads) {
        this.reads = List.copyOf(reads);
        var index = new HashMap<PedestrianDemandKey, Read>();
        for (var read : this.reads) index.put(read.key, read);
        byDemand = Collections.unmodifiableMap(index);
    }
    public static PedestrianCalculationMemory empty() { return EMPTY; }
    Optional<Read> find(PedestrianRouteRequest request) {
        var read = byDemand.get(PedestrianDemandKey.of(request));
        return read != null && read.fence.matches(request.geometry()) ? Optional.of(read) : Optional.empty();
    }
    /** Delivery acknowledgment is independent of later geometry validity or business time. */
    public boolean holdsReadFor(PedestrianRouteRequest request) {
        return byDemand.containsKey(PedestrianDemandKey.of(request));
    }
    public PedestrianCalculationMemory including(Read read) {
        var builder = new Builder(); builder.addAll(this); builder.add(Objects.requireNonNull(read)); return builder.snapshot();
    }
    static final class Builder {
        private final Map<PedestrianDemandKey, Read> reads = new LinkedHashMap<>();
        private boolean retiredPrefix;
        void add(Read read) { reads.put(read.key, read); }
        void retirePrefix() { reads.clear(); retiredPrefix = true; }
        void addAll(PedestrianCalculationMemory memory) { memory.reads.forEach(this::add); }
        void add(Update update) {
            if (update.retiresPrefix) retirePrefix();
            addAll(update.result);
        }
        Update update(PedestrianCalculationMemory basis) {
            var captured = snapshot();
            return new Update(basis, retiredPrefix ? captured : basis.merge(captured), retiredPrefix);
        }
        PedestrianCalculationMemory snapshot() {
            var values = new ArrayList<>(reads.values());
            long units = values.stream().mapToLong(Read::retainedUnits).sum();
            if (units > MAX_RETAINED_UNITS)
                throw new IllegalStateException("unfinished pedestrian calculation exceeds retained fact budget: " + units);
            return values.isEmpty() ? EMPTY : new PedestrianCalculationMemory(values);
        }
    }
    /** Disposable publication instruction. Memory itself contains facts only.
     * Retirement addresses the exact captured reads, never later deliveries. */
    public record Update(PedestrianCalculationMemory basis, PedestrianCalculationMemory result, boolean retiresPrefix) {
        public Update { Objects.requireNonNull(basis); Objects.requireNonNull(result); }
        public static Update adding(PedestrianCalculationMemory reads) { return new Update(EMPTY, reads, false); }
        public Update basedOn(PedestrianCalculationMemory memory) {
            if (basis != EMPTY) throw new IllegalStateException("read capture already has a basis");
            return new Update(memory, retiresPrefix ? result : memory.merge(result), retiresPrefix);
        }
        public Update then(Update next) {
            if (next.basis != result) throw new IllegalArgumentException("disconnected pedestrian read phases");
            return new Update(basis, next.result, retiresPrefix || next.retiresPrefix);
        }
        public PedestrianCalculationMemory applyTo(PedestrianCalculationMemory current) {
            if (current == basis) return result;
            var captured = new LinkedHashMap<PedestrianDemandKey, Read>();
            basis.reads.forEach(read -> captured.put(read.key, read));
            var latest = new LinkedHashMap<PedestrianDemandKey, Read>();
            current.reads.forEach(read -> latest.put(read.key, read));
            var builder = new Builder();
            for (var read : current.reads)
                if (!retiresPrefix || captured.get(read.key) != read) builder.add(read);
            for (var read : result.reads) {
                var delivered = latest.get(read.key);
                // Acknowledged evidence delivered after capture wins over its older image.
                if (delivered == null || delivered == captured.get(read.key)) builder.add(read);
            }
            return builder.snapshot();
        }
    }
    public PedestrianCalculationMemory merge(PedestrianCalculationMemory other) {
        if (other == EMPTY || other == this) return this;
        if (this == EMPTY) return other;
        var builder = new Builder(); builder.addAll(this); builder.addAll(other); return builder.snapshot();
    }
}
