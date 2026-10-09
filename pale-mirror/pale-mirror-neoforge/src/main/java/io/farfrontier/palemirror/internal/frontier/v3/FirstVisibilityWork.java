package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.GrayboxCell;
import java.util.ArrayDeque;
import java.util.List;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3GrayboxExecutor.ProjectionResult;

/** One retained chunk pass; budget yields never classify physical obstruction. */
final class FirstVisibilityWork {
    private final ArrayDeque<GrayboxCell> pending;
    private final int cells;
    private int remainingInPass;
    private boolean progressedInPass;
    private boolean blocked;
    private GrayboxCell current;

    FirstVisibilityWork(List<GrayboxCell> cells) {
        this.pending = new ArrayDeque<>(cells);
        this.cells = cells.size();
        this.remainingInPass = cells.size();
    }
    GrayboxCell next() { return current = pending.removeFirst(); }
    void observe(ProjectionResult result) {
        if (result == ProjectionResult.YIELDED) {
            pending.addLast(current);
            current = null;
            return;
        }
        if (result == ProjectionResult.DEFERRED) pending.addLast(current); else progressedInPass = true;
        remainingInPass--;
        if (remainingInPass == 0 && !pending.isEmpty()) {
            if (!progressedInPass) blocked = true;
            else { remainingInPass = pending.size(); progressedInPass = false; }
        }
        current = null;
    }
    boolean complete() { return pending.isEmpty(); }
    boolean blocked() { return blocked; }
    int cells() { return cells; }
}
