package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Durable obligation to retire one exact physical-first effect after canonical acceptance.
 * The historical receipt, not the next cell, current hand or live scene, owns acknowledgement.
 */
public record ResourceSiteHarvestWorkAcceptance(ResourceSiteHarvestProgressed receipt,
                                                 ResourceFieldCellTransition transition) {
    public ResourceSiteHarvestWorkAcceptance {
        Objects.requireNonNull(receipt, "accepted field work receipt");
        Objects.requireNonNull(transition, "accepted field work transition");
        if (receipt.observedHand().isEmpty()
                || !receipt.siteId().equals(transition.siteId()) || receipt.epoch() != transition.epoch()
                || receipt.layoutRevision() != transition.layoutRevision()
                || !receipt.cellId().equals(transition.cellId())
                || (receipt.outcome() == ResourceFieldCycle.WorkOutcome.HARVESTED) != transition.isHarvest()
                || receipt.outcome() != ResourceFieldCycle.WorkOutcome.HARVESTED
                    && receipt.outcome() != ResourceFieldCycle.WorkOutcome.PLANTED
                    && receipt.outcome() != ResourceFieldCycle.WorkOutcome.TILLED)
            throw new IllegalArgumentException("field acceptance has no exact physical work and actor receipt");
        var expected = switch (receipt.outcome()) {
            case HARVESTED -> ResourceFieldCellTransition.harvest(receipt.siteId(), receipt.epoch(),
                    receipt.layoutRevision(), receipt.cellId(), transition.before());
            case PLANTED, TILLED -> {
                var soil = transition.before().soil();
                if (receipt.outcome() == ResourceFieldCycle.WorkOutcome.PLANTED
                        ? soil != ResourceFieldCycle.Soil.FARMLAND : !soil.untilled())
                    throw new IllegalArgumentException("accepted cultivation has an unsuitable soil predecessor");
                if (!transition.before().equals(new ResourceFieldPhysicalSurface.Condition(soil, ResourceFieldCycle.Crop.ABSENT, 0)))
                    throw new IllegalArgumentException("accepted sowing has a foreign declared work predecessor");
                yield ResourceFieldCellTransition.between(receipt.siteId(), receipt.epoch(), receipt.layoutRevision(), receipt.cellId(),
                        transition.before(), new ResourceFieldPhysicalSurface.Condition(receipt.outcome() == ResourceFieldCycle.WorkOutcome.TILLED
                                ? ResourceFieldCycle.Soil.catalog().step(soil).after() : ResourceFieldCycle.Soil.FARMLAND,
                                receipt.outcome() == ResourceFieldCycle.WorkOutcome.PLANTED
                                        ? ResourceFieldCycle.Crop.GROWING : ResourceFieldCycle.Crop.ABSENT, 0));
            }
            default -> throw new IllegalArgumentException("a skipped cell cannot retain a physical work acceptance");
        };
        if (!transition.equals(expected)) throw new IllegalArgumentException("field acceptance outcome disagrees with its exact block effect");
    }

    public String causationId() {
        return FieldCellOperation.id(receipt.jobId(), receipt.epoch(), receipt.cellId(), receipt.generation());
    }
}
