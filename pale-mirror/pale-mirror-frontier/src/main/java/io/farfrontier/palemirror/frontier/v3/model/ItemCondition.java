package io.farfrontier.palemirror.frontier.v3.model;

/** Condition of an individual durable item; title, custody and capability are separate concerns. */
public record ItemCondition(int maximumDamage, int damage) {
    public static final ItemCondition NONE = new ItemCondition(0, 0);
    public ItemCondition {
        if (maximumDamage < 0 || damage < 0 || damage > maximumDamage)
            throw new IllegalArgumentException("invalid individual item condition");
    }
    public boolean durable() { return maximumDamage > 0; }
    public int remaining() { return maximumDamage - damage; }
    public boolean usable() { return durable() && remaining() > 0; }
    public ItemCondition wear(int amount) {
        if (!durable() || amount < 1 || amount > remaining())
            throw new IllegalArgumentException("item cannot sustain the declared use");
        return new ItemCondition(maximumDamage, Math.addExact(damage, amount));
    }
    public ItemCondition restore(int amount) {
        if (!durable() || amount < 1 || amount > damage)
            throw new IllegalArgumentException("repair exceeds actual item damage");
        return new ItemCondition(maximumDamage, damage - amount);
    }
}
