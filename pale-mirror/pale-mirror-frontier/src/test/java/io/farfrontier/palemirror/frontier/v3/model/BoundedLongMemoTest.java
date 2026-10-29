package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoundedLongMemoTest {
    @Test void exactKeysReplacementAndLongLivedEvictionMatchBoundedLru() {
        for (int capacity : new int[]{1, 2, 17, 64, 257}) {
            var memo = new BoundedLongMemo<String>(capacity);
            var reference = new LinkedHashMap<Long, String>(16, .75f, true);
            var random = new Random(18);
            long[] keys = {0, Long.MIN_VALUE, Long.MAX_VALUE, -1, 1, 1L << 32, 0xffffffffL, -137L << 32 | 347};
            for (int i = 0; i < 4000; i++) {
                long key = i % 3 == 0 ? keys[random.nextInt(keys.length)] : random.nextInt(113) * 0x100000001L;
                if (i % 2 == 0) {
                    String value = "value-" + i;
                    memo.put(key, value); reference.put(key, value);
                    if (reference.size() > capacity) reference.remove(reference.keySet().iterator().next());
                } else assertEquals(reference.get(key), memo.get(key));
                assertEquals(reference.get(key), memo.get(key));
            }
            assertThrows(NullPointerException.class, () -> memo.put(42, null));
        }
    }
}
