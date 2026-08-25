package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/**
 * Sole public domain boundary for the source-parity graybox simulation.
 *
 * <p>NeoForge may schedule, persist and materialize this aggregate, but it
 * cannot reach into mutable source owners. A save is always the complete,
 * versioned source-shaped document; restoration is all-or-nothing.</p>
 */
public final class ReferenceGrayboxSimulation {
    private final ReferenceWorld world;

    private ReferenceGrayboxSimulation(ReferenceWorld world) {
        this.world = Objects.requireNonNull(world, "world");
        ReferenceGrayboxLayout.requireSupported(world);
    }

    public static ReferenceGrayboxSimulation create(long seed) {
        return new ReferenceGrayboxSimulation(new ReferenceWorld(ReferenceWorldConfig.graybox1To40(seed)));
    }

    public static ReferenceGrayboxSimulation restore(byte[] document) {
        return new ReferenceGrayboxSimulation(ReferenceGrayboxWorldHydrator.restore(document));
    }

    /** Complete source-state persistence document; never a presentation cache. */
    public byte[] save() {
        return ReferenceGrayboxStateDocument.encode(ReferenceGrayboxCanonicalState.capture(world));
    }

    /** Immutable, presentation-only view of the current source state. */
    public ReferenceGrayboxSnapshot snapshot() {
        return ReferenceGrayboxProjection.from(world);
    }

    public int day() {
        return world.day();
    }

    /** Advance exactly one source simulation day. */
    public void tick() {
        world.tick();
    }

    public ReferenceGrayboxObservationOutcome observe(ReferenceGrayboxResidentObservation observation) {
        return world.observe(observation);
    }

    public ReferenceGrayboxObservationOutcome observe(ReferenceGrayboxBioformObservation observation) {
        return world.observe(observation);
    }

    public ReferenceGrayboxObservationOutcome observe(ReferenceGrayboxStructureObservation observation) {
        return world.observe(observation);
    }
}
