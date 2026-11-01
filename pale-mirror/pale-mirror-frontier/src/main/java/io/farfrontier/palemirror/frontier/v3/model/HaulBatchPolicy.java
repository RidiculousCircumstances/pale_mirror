package io.farfrontier.palemirror.frontier.v3.model;

/** Pure dispatch policy. No inventory, producer, navigation or settlement authority. */
public final class HaulBatchPolicy {
    public enum Reason { ACCUMULATING, USEFUL_LOAD, RECEIVER_SHORTAGE, SOURCE_PRESSURE, WAIT_DEADLINE }
    public record Request(int available, int capacity, long travelTicks, long firstAvailableTick,
                          long now, long reviewTicks, boolean receiverShortage, boolean sourcePressure) {
        public Request {
            if (available < 0 || capacity < 1 || travelTicks < 0 || firstAvailableTick < 0
                    || now < firstAvailableTick || reviewTicks < 1) throw new IllegalArgumentException("invalid haul batch facts");
        }
    }
    public record Decision(int quantity, int usefulLoad, long deadline, Reason reason) {
        public boolean dispatch() { return quantity > 0; }
    }
    public static Decision decide(Request facts) {
        int useful = (int) Math.min(facts.capacity(), Math.max(16L, 16L + facts.travelTicks() / 40L));
        long wait = Math.max(facts.reviewTicks(), Math.min(2400L, Math.multiplyExact(2L, Math.max(1, facts.travelTicks()))));
        long deadline = Math.addExact(facts.firstAvailableTick(), wait);
        var reason = facts.available() >= useful ? Reason.USEFUL_LOAD
                : facts.receiverShortage() ? Reason.RECEIVER_SHORTAGE
                : facts.sourcePressure() ? Reason.SOURCE_PRESSURE
                : facts.now() >= deadline ? Reason.WAIT_DEADLINE : Reason.ACCUMULATING;
        return new Decision(reason == Reason.ACCUMULATING ? 0 : Math.min(facts.capacity(), facts.available()), useful, deadline, reason);
    }
    private HaulBatchPolicy() { }
}
