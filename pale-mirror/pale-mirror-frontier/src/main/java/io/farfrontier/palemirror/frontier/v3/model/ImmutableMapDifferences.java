package io.farfrontier.palemirror.frontier.v3.model;

import java.lang.ref.WeakReference;
import java.util.*;

/** Shared disposable exact differences for closed immutable map images. Separate
 * consumers of one transition reuse its changed keys, not another trusted world.
 * Weak images and a finite window cannot retain old map/value histories. */
final class ImmutableMapDifferences {
    private static final int MAX_PAIRS = 32;
    private record Difference(WeakReference<Map<?, ?>> before, WeakReference<Map<?, ?>> after, Set<?> keys) { }
    private static final ThreadLocal<ArrayDeque<Difference>> CURRENT = ThreadLocal.withInitial(ArrayDeque::new);
    private ImmutableMapDifferences() { }
    static <K> Optional<Set<K>> keys(Map<K, ?> before, Map<K, ?> after) {
        return untyped(before, after).map(keys -> {
            // The typed caller supplies both images of the same nominal K.
            @SuppressWarnings("unchecked") var result = (Set<K>) keys;
            return result;
        });
    }
    static Optional<Set<?>> untyped(Map<?, ?> before, Map<?, ?> after) {
        if (!(before instanceof ImmutableMapImage<?, ?>) || !(after instanceof ImmutableMapImage<?, ?>)) return Optional.empty();
        var image = (ImmutableMapImage<?, ?>) after;
        var declared = image.changedKeysFrom(before);
        // Producer lineage is already cheap complete candidate evidence. Share
        // only otherwise-repeated full comparisons; do not allocate weak rows
        // for ordinary single-key updates.
        if (declared.isPresent()) return Optional.of(declared.orElseThrow());
        var window = CURRENT.get();
        for (var it = window.iterator(); it.hasNext();) {
            var entry = it.next(); var old = entry.before().get(); var next = entry.after().get();
            if (old == null || next == null) { it.remove(); continue; }
            if (old == before && next == after) {
                return Optional.of(entry.keys());
            }
        }
        var changed = new LinkedHashSet<Object>();
        for (var key : before.keySet()) if (before.get(key) != after.get(key)) changed.add(key);
        for (var key : after.keySet()) if (!before.containsKey(key)) changed.add(key);
        var keys = Collections.unmodifiableSet(changed);
        if (window.size() == MAX_PAIRS) window.removeLast();
        window.addFirst(new Difference(new WeakReference<>(before), new WeakReference<>(after), keys));
        return Optional.of(keys);
    }
}
