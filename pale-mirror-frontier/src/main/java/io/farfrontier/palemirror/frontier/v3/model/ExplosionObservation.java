package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.FixedPosition;

import java.util.List;
import java.util.Objects;

/**
 * Terminal receipt for one real blast after every retained block candidate has been inspected.
 *
 * <p>The individual changed cells remain {@link PhysicalDelta} evidence. This bounded receipt
 * binds that complete post-impact pass to the durable non-replayable intent without importing
 * Minecraft block state into the pure canonical model.</p>
 */
public record ExplosionObservation(PhysicalObservationId id, PhysicalIntentId intentId, FixedPosition origin,
                                   int radiusBlocks, int affectedBlockCount, int changedBlockCount,
                                   List<ExplosionEntityImpact> entityImpacts, List<ExplosionItemImpact> itemImpacts,
                                   int affectedInfectionOverlayCount, int changedInfectionOverlayCount) implements PhysicalEffectObservation {
    private static final int MAX_ENTITY_IMPACTS = 128, MAX_ITEM_IMPACTS = 256;

    public ExplosionObservation {
        Objects.requireNonNull(id, "explosion observation id"); Objects.requireNonNull(intentId, "explosion intent id");
        Objects.requireNonNull(origin, "explosion origin");
        entityImpacts = List.copyOf(Objects.requireNonNull(entityImpacts, "entity impacts"));
        itemImpacts = List.copyOf(Objects.requireNonNull(itemImpacts, "item impacts"));
        if (radiusBlocks < 1 || radiusBlocks > 64 || affectedBlockCount < 0 || changedBlockCount < 0
                || changedBlockCount > affectedBlockCount || entityImpacts.size() > MAX_ENTITY_IMPACTS
                || itemImpacts.size() > MAX_ITEM_IMPACTS || entityImpacts.stream().map(ExplosionEntityImpact::entityId).distinct().count() != entityImpacts.size()
                || itemImpacts.stream().map(ExplosionItemImpact::itemId).distinct().count() != itemImpacts.size()
                || affectedInfectionOverlayCount < 0 || changedInfectionOverlayCount < 0
                || changedInfectionOverlayCount > affectedInfectionOverlayCount) {
            throw new IllegalArgumentException("explosion receipt has invalid bounded impact counts");
        }
    }
}
