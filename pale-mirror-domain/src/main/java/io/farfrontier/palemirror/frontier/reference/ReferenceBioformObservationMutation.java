package io.farfrontier.palemirror.frontier.reference;

/** Exact no-RNG Zombie mutation after the infection owner accepts a physical fact. */
final class ReferenceBioformObservationMutation {
    private ReferenceBioformObservationMutation() { }

    static boolean killExact(ReferenceInfectionModel infection, String bioformId) {
        for (ReferenceSwarm swarm : infection.swarms()) {
            if (!swarm.killExactBioform(bioformId)) continue;
            if (swarm.composition().isEmpty()) infection.removeSwarm(swarm);
            return true;
        }
        return false;
    }
}
