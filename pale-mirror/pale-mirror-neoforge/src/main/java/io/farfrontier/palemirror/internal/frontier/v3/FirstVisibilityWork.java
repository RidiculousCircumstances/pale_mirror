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
    private int preparedRemaining;

    FirstVisibilityWork(List<GrayboxCell> cells) {
        this.pending = new ArrayDeque<>(cells);
        this.cells = cells.size();
        this.remainingInPass = cells.size();
    }
    boolean preparePending(int limit, java.util.function.Predicate<List<GrayboxCell>> beforeWrite) {
        if (limit < 1 || current != null) throw new IllegalArgumentException("invalid planned visibility window");
        if (preparedRemaining > 0 || pending.isEmpty()) return true;
        var window = pending.stream().limit(limit).toList();
        if (!beforeWrite.test(window)) return false;
        preparedRemaining = window.size(); // Publish only after successful durable preparation.
        return true;
    }
    GrayboxCell next() { return current = pending.removeFirst(); }
    /** Same cursor as the writer lane, but no writes or mutation admission. */
    void observeCurrent(java.util.function.Predicate<GrayboxCell> currentImage, FrontierV3FirstVisibilityBudget budget) {
        while (!pending.isEmpty() && budget.takeRead()) {
            if (!currentImage.test(pending.peekFirst())) return;
            next(); observe(ProjectionResult.CURRENT);
        }
    }
    void observe(ProjectionResult result) {
        if (result == ProjectionResult.YIELDED) {
            // Resume this attempt before repeating a previously deferred cell in the same pass.
            pending.addFirst(current);
            current = null;
            return;
        }
        if (preparedRemaining > 0) preparedRemaining--;
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
    int remaining() { return pending.size(); }
}
