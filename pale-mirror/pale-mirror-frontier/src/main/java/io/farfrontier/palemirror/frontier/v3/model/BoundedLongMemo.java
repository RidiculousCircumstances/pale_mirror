package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Arrays;
import java.util.Objects;

/** Disposable exact-long-key facts with bounded LRU retention. Hits and eviction
 * allocate no key wrappers or hash nodes. Null is a miss, never a stored value;
 * callers compute outside this monitor against their captured immutable inputs. */
public final class BoundedLongMemo<V> {
    private final int capacity;
    private long[] keys;
    private Object[] values;
    private int[] buckets, previous, next;
    private int mask;
    private int size, first = -1, last = -1;
    public BoundedLongMemo(int capacity) {
        if (capacity < 1 || capacity > (1 << 28)) throw new IllegalArgumentException("positive array-addressable memo capacity required");
        this.capacity = capacity;
        int initial = Math.min(capacity, 64);
        keys = new long[initial]; values = new Object[initial];
        previous = new int[initial]; next = new int[initial];
        buckets = new int[tableSize(initial)]; mask = buckets.length - 1;
    }
    private static int tableSize(int rows) {
        int table = 2;
        while (table < rows * 2) table <<= 1;
        return table;
    }
    private void grow() {
        int rows = Math.min(capacity, keys.length * 2);
        keys = Arrays.copyOf(keys, rows); values = Arrays.copyOf(values, rows);
        previous = Arrays.copyOf(previous, rows); next = Arrays.copyOf(next, rows);
        buckets = new int[tableSize(rows)]; mask = buckets.length - 1;
        for (int row = 0; row < size; row++) buckets[find(keys[row])] = row + 1;
    }

    public synchronized V get(long key) {
        int bucket = find(key), row = buckets[bucket] - 1;
        if (row < 0) return null;
        touch(row);
        @SuppressWarnings("unchecked") V result = (V) values[row];
        return result;
    }
    public synchronized void put(long key, V value) {
        Objects.requireNonNull(value);
        int bucket = find(key), row = buckets[bucket] - 1;
        if (row >= 0) { values[row] = value; touch(row); return; }
        if (size < capacity) {
            if (size == keys.length) { grow(); bucket = find(key); }
            row = size++;
        }
        else {
            row = first;
            removeBucket(find(keys[row])); unlink(row);
            bucket = find(key);
        }
        keys[row] = key; values[row] = value; buckets[bucket] = row + 1;
        append(row);
    }
    private int home(long key) {
        long mixed = key;
        mixed ^= mixed >>> 33; mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33; mixed *= 0xc4ceb9fe1a85ec53L;
        return (int) (mixed ^ (mixed >>> 33)) & mask;
    }
    private int find(long key) {
        int bucket = home(key);
        while (buckets[bucket] != 0 && keys[buckets[bucket] - 1] != key) bucket = (bucket + 1) & mask;
        return bucket;
    }
    /** Close a deleted probe-chain hole; no tombstones accumulate over long runs. */
    private void removeBucket(int hole) {
        buckets[hole] = 0;
        for (int at = (hole + 1) & mask; buckets[at] != 0; at = (at + 1) & mask) {
            int origin = home(keys[buckets[at] - 1]);
            if (((hole - origin) & mask) < ((at - origin) & mask)) {
                buckets[hole] = buckets[at]; buckets[at] = 0; hole = at;
            }
        }
    }
    private void touch(int row) {
        if (last == row) return;
        unlink(row); append(row);
    }
    private void unlink(int row) {
        int before = previous[row], after = next[row];
        if (before < 0) first = after; else next[before] = after;
        if (after < 0) last = before; else previous[after] = before;
    }
    private void append(int row) {
        previous[row] = last; next[row] = -1;
        if (last < 0) first = row; else next[last] = row;
        last = row;
    }
}
