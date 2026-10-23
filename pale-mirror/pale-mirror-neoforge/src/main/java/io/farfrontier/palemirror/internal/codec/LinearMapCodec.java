package io.farfrontier.palemirror.internal.codec;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.*;

/** Native compound-map format without DFU BaseMapCodec's quadratic array-map
 * insertion. Decode entries once, then establish exact unique semantic keys. */
public final class LinearMapCodec {
    private LinearMapCodec() { }
    public static <K, V> Codec<Map<K, V>> of(Codec<K> keys, Codec<V> values) {
        return Codec.compoundList(keys, values).flatXmap(entries -> {
            var indexed = new LinkedHashMap<K, V>();
            for (var entry : entries) {
                if (indexed.putIfAbsent(entry.getFirst(), entry.getSecond()) != null)
                    return DataResult.error(() -> "duplicate decoded map key: " + entry.getFirst());
            }
            return DataResult.success(Collections.unmodifiableMap(indexed));
        }, map -> DataResult.success(map.entrySet().stream()
                .map(entry -> Pair.of(entry.getKey(), entry.getValue())).toList()));
    }
}
