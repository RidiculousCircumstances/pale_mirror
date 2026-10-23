package io.farfrontier.palemirror.frontier.v3.model.supply;

import java.util.*;

/** Bounded capacity advice for independent renewable sources sharing one worker pool.
 * Preparation occupies workers, biological waiting does not. No assignment or stock authority. */
public final class RenewableWorkForecast {
    public record Source(int yield, long preparation, long harvest, long remainingGrowth, long fullGrowth,
                         long repeatPreparation, boolean planted, boolean renew) {
        public Source {
            if (yield < 1 || preparation < 0 || harvest < 1 || remainingGrowth < 0 || fullGrowth < 1
                    || repeatPreparation < 1) throw new IllegalArgumentException("invalid renewable source forecast");
        }
    }
    private record Task(int source, boolean harvest, long readyAt, long work) { }
    private record Input(List<Source> sources, int workers, long day, long dailyWork, long horizon) {
        Input { sources = List.copyOf(sources); }
    }
    private static final io.farfrontier.palemirror.frontier.v3.model.BoundedValueMemo<Input,
            List<SupplyPlanner.OutputRelease>> FORECASTS = new io.farfrontier.palemirror.frontier.v3.model.BoundedValueMemo<>(32);
    public static List<SupplyPlanner.OutputRelease> assess(List<Source> sources, int workers, long day,
            long workPerDay, long horizon) {
        if (sources.size() > 4096 || workers < 1 || workers > 256 || day < 1 || workPerDay < 1
                || workPerDay > day || horizon < 1) throw new IllegalArgumentException("invalid renewable work calendar");
        var input = new Input(sources, workers, day, workPerDay, horizon);
        return FORECASTS.get(input, () -> calculate(input.sources(), workers, day, workPerDay, horizon));
    }
    private static List<SupplyPlanner.OutputRelease> calculate(List<Source> sources, int workers, long day,
            long workPerDay, long horizon) {
        var waiting = new PriorityQueue<Task>(Comparator.comparingLong(Task::readyAt)
                .thenComparing(Task::harvest, Comparator.reverseOrder()).thenComparingInt(Task::source));
        var ready = new PriorityQueue<Task>(Comparator.comparing(Task::harvest, Comparator.reverseOrder())
                .thenComparingInt(Task::source));
        var free = new PriorityQueue<Long>();
        for (int i = 0; i < workers; i++) free.add(0L);
        for (int i = 0; i < sources.size(); i++) {
            var source = sources.get(i);
            waiting.add(new Task(i, source.planted(), source.planted() ? source.remainingGrowth() : 0,
                    source.planted() ? source.harvest() : source.preparation()));
        }
        var output = new TreeMap<Long, Integer>();
        // A truncated forecast is less capacity, never invented supply or an unbounded planning turn.
        for (int operation = 0; operation < 32768 && (!waiting.isEmpty() || !ready.isEmpty()); operation++) {
            long at = free.remove();
            if (ready.isEmpty() && !waiting.isEmpty()) at = Math.max(at, waiting.peek().readyAt());
            if (at >= horizon) break;
            while (!waiting.isEmpty() && waiting.peek().readyAt() <= at) ready.add(waiting.remove());
            var task = ready.remove(); var source = sources.get(task.source());
            long completed = finish(at, task.work(), day, workPerDay);
            free.add(completed);
            if (completed > horizon) continue;
            if (task.harvest()) {
                output.merge(completed, source.yield(), Math::addExact);
                if (output.size() >= 4096) break;
                if (source.renew()) waiting.add(new Task(task.source(), false, completed, source.repeatPreparation()));
            } else waiting.add(new Task(task.source(), true, Math.addExact(completed, source.fullGrowth()), source.harvest()));
        }
        var result = new ArrayList<SupplyPlanner.OutputRelease>(); int total = 0;
        for (var release : output.entrySet()) {
            total = Math.addExact(total, release.getValue());
            result.add(new SupplyPlanner.OutputRelease(release.getKey(), total));
        }
        return List.copyOf(result);
    }
    /** Pessimistic unknown phase: day's non-work time precedes its work window. */
    private static long finish(long at, long work, long day, long dailyWork) {
        if (work == 0) return at;
        long available = Math.addExact(Math.multiplyExact(at / day, dailyWork),
                Math.max(0, at % day - (day - dailyWork)));
        long target = Math.addExact(available, work);
        return Math.addExact(target, Math.multiplyExact(Math.ceilDiv(target, dailyWork), day - dailyWork));
    }
    private RenewableWorkForecast() { }
}
