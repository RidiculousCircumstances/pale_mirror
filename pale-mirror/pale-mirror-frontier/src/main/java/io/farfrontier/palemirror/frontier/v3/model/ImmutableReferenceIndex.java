package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import java.util.function.*;

/** Disposable reverse index of producer-declared immutable reads. It selects
 * checks, never an owner, behavior or canonical result. Full ingress ignores it.
 * A failed update discards the whole image before any evidence can be reused. */
public final class ImmutableReferenceIndex<C, K, D> {
    /** Nominal read source; the producer declares its map and exact key type. */
    public static final class Source<C, I, V> {
        private final Function<C, Map<I, V>> values;
        private final BiPredicate<V, V> unchanged;
        public Source(Function<C, Map<I, V>> values) { this(values, (before, after) -> before == after); }
        /** A producer may declare exact equality for a complete small immutable
         * projection. Canonical aggregate sources retain identity comparison. */
        public Source(Function<C, Map<I, V>> values, BiPredicate<V, V> unchanged) {
            this.values = Objects.requireNonNull(values); this.unchanged = Objects.requireNonNull(unchanged);
        }
        @SuppressWarnings("unchecked")
        private boolean sameValue(Object before, Object after) { return unchanged.test((V) before, (V) after); }
        public Read<C> at(I key) { return new Binding<>(this, Objects.requireNonNull(key)); }
        Map<I, V> values(C context) { return Objects.requireNonNull(values.apply(context)); }
    }
    public sealed interface Read<C> permits Binding {
        Source<C, ?, ?> source();
        Object key();
        Object value(C context);
    }
    private record Binding<C, I, V>(Source<C, I, V> source, I key) implements Read<C> {
        @Override public V value(C context) { return source.values(context).get(key); }
    }
    private record Row<C>(List<Read<C>> reads) { }
    private static final class Image<C, K, D> {
        final Object epoch;
        Map<K, D> declarations = Map.of();
        final Map<K, Row<C>> rows = new HashMap<>();
        final Map<Source<C, ?, ?>, Map<?, ?>> roots = new IdentityHashMap<>();
        final Map<Source<C, ?, ?>, Map<Object, Set<K>>> readers = new IdentityHashMap<>();
        Image(Object epoch) { this.epoch = epoch; }
    }
    @FunctionalInterface public interface Check<C, K, D> { void validate(C context, K key, D declaration); }
    private final int maximumRows, maximumReads;
    private final BiFunction<C, D, List<Read<C>>> compile;
    private final Check<C, K, D> check;
    private final Set<Source<C, ?, ?>> sources = Collections.newSetFromMap(new IdentityHashMap<>());
    private final ThreadLocal<Image<C, K, D>> current = new ThreadLocal<>();
    /** One coherent source capture for a projection evaluation. Source functions
     * may themselves prepare owner facts; validation must not prepare them again. */
    static final class Inputs<C> {
        final List<Source<C, ?, ?>> sources;
        final List<Map<?, ?>> roots;
        Inputs(List<Source<C, ?, ?>> sources, C context, List<Map<?, ?>> previous) {
            this.sources = sources;
            ArrayList<Map<?, ?>> captured = null;
            for (int i = 0; i < sources.size(); i++) {
                var value = sources.get(i).values(context);
                if (captured == null && previous != null && previous.get(i) == value) continue;
                if (captured == null) {
                    captured = new ArrayList<>(sources.size());
                    if (previous != null) captured.addAll(previous.subList(0, i));
                }
                captured.add(value);
            }
            roots = captured == null ? previous : List.copyOf(captured);
        }
        Map<?, ?> values(Source<C, ?, ?> source) {
            for (int i = 0; i < sources.size(); i++) if (sources.get(i) == source) return roots.get(i);
            throw new IllegalArgumentException("uncaptured immutable reference source");
        }
    }
    public ImmutableReferenceIndex(int maximumRows, int maximumReads, List<Source<C, ?, ?>> sources,
            BiFunction<C, D, List<Read<C>>> compile, Check<C, K, D> check) {
        if (maximumRows < 1 || maximumReads < 1 || sources.isEmpty()) throw new IllegalArgumentException("positive reference-index bounds required");
        this.maximumRows = maximumRows; this.maximumReads = maximumReads;
        this.compile = Objects.requireNonNull(compile); this.check = Objects.requireNonNull(check);
        for (var source : sources) if (!this.sources.add(Objects.requireNonNull(source)))
            throw new IllegalArgumentException("duplicate reference source declaration");
    }
    public void validate(Object epoch, Map<K, D> declarations, C context) {
        validate(epoch, declarations, context, null);
    }
    void validate(Object epoch, Map<K, D> declarations, C context, Inputs<C> inputs) {
        Objects.requireNonNull(epoch); Objects.requireNonNull(declarations); Objects.requireNonNull(context);
        if (declarations.size() > maximumRows) throw new IllegalArgumentException("reference index exceeds its declared row bound");
        var image = current.get();
        if (image == null || image.epoch != epoch || ImmutableInvariantCheck.fullAudit()) {
            image = new Image<>(epoch); current.set(image);
        }
        try { update(image, declarations, context, inputs); }
        catch (RuntimeException | Error failed) { current.remove(); throw failed; }
    }
    private void update(Image<C, K, D> image, Map<K, D> declarations, C context, Inputs<C> inputs) {
        var dirty = new LinkedHashSet<K>();
        ImmutableMapChanges.forEachIdentityChangedKey(image.declarations, declarations, dirty::add);
        // Each used source is compared once, not once per registered consumer.
        for (var source : List.copyOf(image.roots.keySet())) {
            var next = inputs == null ? source.values(context) : inputs.values(source);
            var prior = image.roots.get(source);
            changedKeys(prior, next, key -> {
                if (source.sameValue(prior.get(key), next.get(key))) return;
                var readers = image.readers.get(source).get(key);
                if (readers != null) dirty.addAll(readers);
            });
            image.roots.put(source, next);
        }
        for (var key : dirty) {
            var declaration = declarations.get(key); var previous = image.rows.get(key);
            if (declaration == null) { unlink(image, key, previous); continue; }
            var declaredReads = List.copyOf(compile.apply(context, declaration));
            if (declaredReads.size() > maximumReads) throw new IllegalArgumentException("reference row exceeds its declared read bound");
            var retained = List.copyOf(new LinkedHashSet<>(declaredReads));
            for (var read : retained) if (!sources.contains(read.source()))
                throw new IllegalArgumentException("unregistered immutable reference source");
            check.validate(context, key, declaration); // No successful index evidence is installed before the real owner check.
            // A changed value still runs its check, but unchanged read addresses
            // already have the exact reverse links. No unlink/rebind is needed.
            if (previous != null && previous.reads().equals(retained)) continue;
            unlink(image, key, previous);
            for (var read : retained) {
                image.roots.putIfAbsent(read.source(), inputs == null ? read.source().values(context) : inputs.values(read.source()));
                image.readers.computeIfAbsent(read.source(), ignored -> new HashMap<>())
                        .computeIfAbsent(read.key(), ignored -> new HashSet<>()).add(key);
            }
            image.rows.put(key, new Row<>(retained));
        }
        image.declarations = declarations;
    }
    private void unlink(Image<C, K, D> image, K key, Row<C> row) {
        if (row == null) return;
        for (var read : row.reads()) {
            var addresses = image.readers.get(read.source()); var readers = addresses.get(read.key());
            readers.remove(key); if (readers.isEmpty()) addresses.remove(read.key());
            if (addresses.isEmpty()) { image.readers.remove(read.source()); image.roots.remove(read.source()); }
        }
        image.rows.remove(key);
    }
    @SuppressWarnings("unchecked")
    private static void changedKeys(Map<?, ?> before, Map<?, ?> after, Consumer<Object> changed) {
        // Both maps come from the same nominal Source. Erasure stays inside this
        // read-only index; a caller cannot construct an untyped authoritative ID.
        ImmutableMapChanges.forEachIdentityChangedKey((Map<Object, ?>) before, (Map<Object, ?>) after, changed);
    }
}
