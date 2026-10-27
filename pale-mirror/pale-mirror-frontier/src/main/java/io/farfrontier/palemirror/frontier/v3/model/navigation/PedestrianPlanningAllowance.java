package io.farfrontier.palemirror.frontier.v3.model.navigation;

/** One owner-thread calculation allowance. In-flight slices reserve work; unused
 * work returns only to their original still-open turn. Closing never cancels a
 * search or grants the next turn credit. No simulation clock or world authority. */
public final class PedestrianPlanningAllowance implements AutoCloseable {
    private final int maximum;
    private int available;
    private boolean closed;
    public PedestrianPlanningAllowance(int maximum) {
        if (maximum < HierarchicalPedestrianSearch.MIN_SLICE_WORK)
            throw new IllegalArgumentException("planning allowance cannot fit a bounded slice");
        this.maximum = maximum; available = maximum;
    }
    int available() { return closed ? 0 : available; }
    void reserve(int work) {
        if (work < 0 || work > available()) throw new IllegalStateException("planning work exceeds its retained allowance");
        available -= work;
    }
    void settle(int reserved, int spent) {
        if (spent < 0 || spent > reserved) throw new IllegalStateException("worker exceeded its reservation");
        if (closed) return;
        available = Math.addExact(available, reserved - spent);
        if (available > maximum) throw new IllegalStateException("planning reservation settled twice");
    }
    @Override public void close() { closed = true; available = 0; }
}
