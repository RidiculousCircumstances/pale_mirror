package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.*;

/** Immutable witness queue: removing a head retains its backing image without a suffix copy. */
final class FrontierV3WitnessQueue<T> extends AbstractList<T> implements RandomAccess {
    static final int SHARD = 256;
    private final List<T> values;
    private final int offset, end;
    private final long origin;
    private FrontierV3WitnessQueue(List<T> values, int offset, int end, long origin) {
        this.values = values; this.offset = offset; this.end = end; this.origin = origin;
        if (offset < 0 || end < offset || end > values.size() || origin < 0 || (!values.isEmpty() && origin % SHARD != 0))
            throw new IllegalArgumentException("invalid witness queue image");
    }
    static <T> FrontierV3WitnessQueue<T> copy(List<T> values) {
        if (values instanceof FrontierV3WitnessQueue<T> queue) return queue;
        return new FrontierV3WitnessQueue<>(List.copyOf(values),0,values.size(),0);
    }
    static <T> FrontierV3WitnessQueue<T> restored(List<T> values, long start) {
        if (start < 0 || start > 65536) throw new IllegalArgumentException("invalid witness queue cursor");
        if (values.isEmpty()) return new FrontierV3WitnessQueue<>(List.of(),0,0,start);
        int offset = (int)(start % SHARD);
        return new FrontierV3WitnessQueue<>(List.copyOf(values), offset, values.size(), start-offset);
    }
    long start() { return origin+offset; }
    long finish() { return origin+end; }
    Object imageIdentity() { return values; }
    List<T> shard(long id) {
        int begin = Math.toIntExact(id*SHARD-origin), limit = Math.min(begin+SHARD,end);
        return values.subList(begin,limit);
    }
    List<T> retained() { return isEmpty() ? List.of() : values.subList(offset / SHARD * SHARD, end); }
    public T get(int index) { Objects.checkIndex(index,size()); return values.get(offset+index); }
    public int size() { return end-offset; }
    @Override public List<T> subList(int from, int to) {
        Objects.checkFromToIndex(from,to,size()); return new FrontierV3WitnessQueue<>(values,offset+from,offset+to,origin);
    }
}
