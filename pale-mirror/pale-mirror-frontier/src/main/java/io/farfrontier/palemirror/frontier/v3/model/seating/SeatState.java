package io.farfrontier.palemirror.frontier.v3.model.seating;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Sole shared seat occupancy board. Its index is read-only and discarded with immutable inputs. */
public record SeatState(Map<SubjectId, SeatUse> uses) {
    private static final class OccupancyImage {
        Map<SubjectId, SeatUse> source = Map.of();
        final Map<SeatPlace.Id, SubjectId> occupants = new HashMap<>();
    }
    private static final ThreadLocal<OccupancyImage> OCCUPANTS = ThreadLocal.withInitial(OccupancyImage::new);
    private static OccupancyImage occupancy(Map<SubjectId, SeatUse> source) {
        var image = OCCUPANTS.get();
        if (image.source == source) return image;
        try {
            var changed = new LinkedHashSet<SubjectId>();
            ImmutableMapChanges.forEachIdentityChangedKey(image.source, source, changed::add);
            for (var actor : changed) {
                var prior = image.source.get(actor);
                if (prior != null && !image.occupants.remove(prior.place().id(), actor))
                    throw new IllegalArgumentException("seat occupancy index lost its immutable predecessor");
            }
            for (var actor : changed) {
                var use = source.get(actor);
                if (use != null && (!actor.equals(use.execution().actorId()) || image.occupants.putIfAbsent(use.place().id(), actor) != null))
                    throw new IllegalArgumentException("competing or foreign seat reservation");
            }
            image.source = source;
            return image;
        } catch (RuntimeException | Error failed) { OCCUPANTS.remove(); throw failed; }
    }
    public SeatState {
        uses = ImmutableLookupMap.copyOf(uses);
        if (uses.size() > SimulationCapacity.MAX_RESIDENTS)
            throw new IllegalArgumentException("unbounded seat reservations");
        occupancy(uses);
    }
    public static SeatState empty() { return new SeatState(Map.of()); }
    public SeatState reserve(SeatUse use) {
        if (uses.containsKey(use.execution().actorId()) || !free(use.place().id()) || use.phase() != SeatUse.Phase.RESERVED
                || use.revision() != 1 || use.pendingBody().isPresent())
            throw new IllegalArgumentException("seat admission competes with an existing reservation");
        return with(use);
    }
    public SeatState with(SeatUse use) { return new SeatState(ImmutableLookupMap.withEntry(uses, use.execution().actorId(), use)); }
    public SeatState without(SubjectId actor) {
        var use = Objects.requireNonNull(uses.get(actor), "seat reservation");
        if (use.pendingBody().isPresent() || use.phase() != SeatUse.Phase.RESERVED)
            throw new IllegalArgumentException("seat is still occupied or unsettled");
        return new SeatState(ImmutableLookupMap.withoutEntry(uses, actor));
    }
    public boolean free(SeatPlace.Id id) {
        return !occupancy(uses).occupants.containsKey(id);
    }
}
