package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Pure compiler for the exact, local floor columns of a settlement assault. */
public final class FrontierSettlementAssaultBattlefield {
    private FrontierSettlementAssaultBattlefield() { }

    public static Optional<List<BlockPosition>> attackerFloors(FrontierWorldState state, HiveSettlementKnowledge.Sighting sighting,
                                                         List<SubjectId> defenderIds, int attackerCount) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), sighting.settlementId());
        if (!settlement.anchor().equals(sighting.settlementAnchor()) || attackerCount < 1 || attackerCount > SettlementAssault.MAX_ATTACKERS) {
            return Optional.empty();
        }
        Set<BlockPosition> structureCells = FrontierSettlementActorSlots.intactStructureOccupancy(state.bootstrap().terrain(), settlement.structures());
        long settlementEnvelopeSquared = residentEnvelopeSquared(state, settlement);
        Provider provider = providerView(state);
        Set<BlockPosition> occupied = new LinkedHashSet<>();
        for (SubjectId defender : defenderIds) {
            ActorLocation location = state.actorLocations().get(defender);
            if (location == null || location.condition().status() != ActorLifeStatus.ALIVE
                    || !serviceableFloor(provider, location.supportingSurface().support())
                    || !localClearFloor(state.bootstrap().bounds(), settlement.anchor(), settlementEnvelopeSquared, structureCells,
                    location.supportingSurface().support()) || !occupied.add(location.supportingSurface().support())) {
                return Optional.empty();
            }
        }
        List<BlockPosition> choices = declaredProviderFloors(state, settlement).stream()
                .filter(position -> localClearFloor(state.bootstrap().bounds(), settlement.anchor(), settlementEnvelopeSquared, structureCells, position))
                .filter(position -> !occupied.contains(position)).limit(attackerCount).toList();
        return choices.size() == attackerCount ? Optional.of(choices) : Optional.empty();
    }

    public static Optional<SettlementAssaultSceneCandidate> candidate(FrontierWorldState state, SettlementAssault assault) {
        return candidate(state, assault, providerView(state));
    }

    static Provider providerView(FrontierWorldState state) {
        return new ProviderView(FrontierGrayboxPlan.compile(state).cells());
    }

    static Optional<SettlementAssaultSceneCandidate> candidate(FrontierWorldState state, SettlementAssault assault,
                                                                Provider provider) {
        if ((assault.status() != SettlementAssaultStatus.WAITING_FOR_BATTLE && assault.status() != SettlementAssaultStatus.COLD_COMBAT)
                || !FrontierSettlementAssaultSceneSupport.targetIntact(state, assault)) {
            return Optional.empty();
        }
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), assault.settlementId());
        Set<BlockPosition> structureCells = FrontierSettlementActorSlots.intactStructureOccupancy(state.bootstrap().terrain(), settlement.structures());
        long settlementEnvelopeSquared = residentEnvelopeSquared(state, settlement);
        Map<SubjectId, BlockPosition> positions = new LinkedHashMap<>();
        List<SubjectId> members = new ArrayList<>(assault.attackerIds()); members.addAll(assault.defenderIds());
        members.sort(Comparator.naturalOrder());
        for (SubjectId member : members) {
            ActorLocation location = state.actorLocations().get(member);
            if (location == null || location.condition().status() != ActorLifeStatus.ALIVE
                    || !serviceableFloor(provider, location.supportingSurface().support())
                    || !localClearFloor(state.bootstrap().bounds(), settlement.anchor(), settlementEnvelopeSquared, structureCells,
                    location.supportingSurface().support()) || positions.put(member, location.supportingSurface().support()) != null) {
                return Optional.empty();
            }
        }
        if (positions.values().stream().distinct().count() != positions.size()) return Optional.empty();
        return Optional.of(new SettlementAssaultSceneCandidate(assault.id(), settlement.id(), settlement.anchor(), positions));
    }

    static boolean localClearFloor(FrontierWorldState state, Settlement settlement, BlockPosition position) {
        return localClearFloor(state.bootstrap().bounds(), settlement.anchor(), residentEnvelopeSquared(state, settlement),
                FrontierSettlementActorSlots.intactStructureOccupancy(state.bootstrap().terrain(), settlement.structures()), position);
    }

    /**
     * The graybox plan is the one registered physical provider for the current flat-provider
     * contract.  A battle may use only one of its declared load-bearing surface columns; clear
     * terrain is not a provider and cannot become one when the scene is visited.
     */
    static boolean serviceableFloor(FrontierWorldState state, BlockPosition position) {
        return serviceableFloor(providerView(state), position);
    }

    /**
     * Bounded read-only structural observation for one exact admission derivation.  Admission
     * can ask only for a named cell; it cannot enumerate geometry, compile a plan, or retain a
     * second grammar.  The NeoForge projector supplies its already-compiled snapshot here.
     */
    @FunctionalInterface
    public interface Provider {
        Optional<GrayboxCell> cellAt(BlockPosition position);
    }

    /** Default pure-model provider used outside the NeoForge projection-to-admission seam. */
    private static final class ProviderView implements Provider {
        private final Map<BlockPosition, GrayboxCell> cells;

        private ProviderView(Map<BlockPosition, GrayboxCell> cells) {
            this.cells = cells;
        }

        @Override public Optional<GrayboxCell> cellAt(BlockPosition position) { return Optional.ofNullable(cells.get(position)); }
    }

    private static boolean serviceableFloor(Provider provider, BlockPosition position) {
        Optional<GrayboxCell> support = provider.cellAt(position);
        Optional<GrayboxCell> head = provider.cellAt(position.offset(0, 1, 0));
        Optional<GrayboxCell> headroom = provider.cellAt(position.offset(0, 2, 0));
        return support.filter(FrontierSettlementAssaultBattlefield::isDeclaredBodySurface).isPresent()
                && head.isEmpty() && headroom.isEmpty();
    }

    private static List<BlockPosition> declaredProviderFloors(FrontierWorldState state, Settlement settlement) {
        Provider provider = providerView(state);
        return SettlementResidentIngressPlan.compile(state.bootstrap().bounds(), state.bootstrap().terrain(), settlement,
                state.bootstrap().ruleset().facilityCapacity().intactHousingBeds()).perimeterSurfaces().stream()
                .map(SurfaceAnchor::support).sorted(Comparator.comparingLong((BlockPosition position) -> distanceSquared(settlement.anchor(), position))
                        .thenComparingInt(BlockPosition::x)
                        .thenComparingInt(BlockPosition::y).thenComparingInt(BlockPosition::z))
                .filter(position -> serviceableFloor(provider, position)).toList();
    }

    private static boolean isDeclaredBodySurface(GrayboxCell cell) {
        return cell.semanticPart() == GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE
                || cell.semanticPart() == GrayboxSemanticPart.ROUTE_SURFACE;
    }

    private static boolean localClearFloor(WorldBounds bounds, BlockPosition anchor, long settlementEnvelopeSquared,
                                           Set<BlockPosition> structureCells, BlockPosition position) {
        return nearby(anchor, position, settlementEnvelopeSquared) && FrontierSettlementActorSlots.clearFloor(bounds, structureCells, position);
    }

    private static long residentEnvelopeSquared(FrontierWorldState state, Settlement settlement) {
        return SettlementResidentIngressPlan.compile(state.bootstrap().bounds(), state.bootstrap().terrain(), settlement,
                        state.bootstrap().ruleset().facilityCapacity().intactHousingBeds()).homeSlots().stream()
                .mapToLong(position -> distanceSquared(settlement.anchor(), position)).max()
                .orElseThrow(() -> new IllegalArgumentException("settlement battlefield requires one planned resident home"));
    }

    private static boolean nearby(BlockPosition anchor, BlockPosition position, long settlementEnvelopeSquared) {
        return distanceSquared(anchor, position) <= settlementEnvelopeSquared;
    }

    private static long distanceSquared(BlockPosition anchor, BlockPosition position) {
        long x = (long) anchor.x() - position.x(), y = (long) anchor.y() - position.y(), z = (long) anchor.z() - position.z();
        return x * x + y * y + z * z;
    }
}
