package io.farfrontier.palemirror.frontier.reference;

/** Exact typed form of Python {@code HiveLifecycle}. */
public enum ReferenceHiveLifecycle {
    SEED("seed"),
    ROOTING("rooting"),
    FEEDING("feeding"),
    NETWORKED_EXPANSION("networked_expansion"),
    PREDATION("predation"),
    SIEGE("siege"),
    DECAPITATED("decapitated"),
    RECONSTITUTION("reconstitution"),
    DECAY("decay");

    private final String id;

    ReferenceHiveLifecycle(String id) { this.id = id; }

    public String id() { return id; }
}
