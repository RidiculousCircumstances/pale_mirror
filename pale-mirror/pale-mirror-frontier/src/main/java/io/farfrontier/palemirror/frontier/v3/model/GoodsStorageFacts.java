package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Market-inventory publication. Quantities come from the sole resource/item owners;
 * eligibility remains the storage owner's fact. No second ledger or stock reservation. */
final class GoodsStorageFacts {
    record Container(ResourceStorageFacts.Fact storage, Map<SubjectId, Map<String, Integer>> amounts) {
        int available(SubjectId owner, String kind) { return amounts.getOrDefault(owner, Map.of()).getOrDefault(kind, 0); }
    }
    private static final ImmutableReferenceIndex.Source<FrontierWorldState, SubjectId, ResourceStorageFacts.Fact> STORAGE =
            new ImmutableReferenceIndex.Source<>(ResourceStorageFacts::all);
    private static final ImmutableReferenceIndex.Source<FrontierWorldState, SubjectId, FungibleResourceReadIndex.Available> AMOUNTS =
            new ImmutableReferenceIndex.Source<>(s -> FungibleResourceReadIndex.stockAvailability(s.inventory().fungibleResources()));
    private static final ImmutableReferenceIndex.Source<FrontierWorldState, SubjectId, List<ExactItemStack>> ITEMS =
            new ImmutableReferenceIndex.Source<>(s -> ExactItemReadIndex.containers(s.inventory()));
    private static final ImmutableReferenceIndex.Source<FrontierWorldState, SubjectId, Boolean> CLAIMS =
            new ImmutableReferenceIndex.Source<>(WorkEquipmentClaimComposition::reservedItems);
    private static final ImmutableInputView<Map<String, GoodsStockSpecification>> SPECIFICATIONS = new ImmutableInputView<>();
    private static final ImmutableReadProjection<FrontierWorldState, SubjectId, ContainerRecord, Container> CONTAINERS =
            new ImmutableReadProjection<>(Integer.MAX_VALUE, ContainerRecord.MAX_SLOTS + 3, List.of(STORAGE, AMOUNTS, ITEMS, CLAIMS),
                    (state, row) -> {
                        var reads = new ArrayList<ImmutableReferenceIndex.Read<FrontierWorldState>>();
                        reads.add(STORAGE.at(row.id())); reads.add(AMOUNTS.at(row.id())); reads.add(ITEMS.at(row.id()));
                        for (var item : ExactItemReadIndex.containers(state.inventory()).getOrDefault(row.id(), List.of()))
                            reads.add(CLAIMS.at(item.id()));
                        return reads;
                    }, GoodsStorageFacts::project);
    private static final ImmutableGroupView<SubjectId, Container, SubjectId> OWNERS = new ImmutableGroupView<>();
    private static final ImmutableKeyedInputView<GoodsParticipantScope, Map<String, Integer>> STOCK = new ImmutableKeyedInputView<>(GoodsParticipantState.MAX_PARTICIPANTS);
    private record PositionQuery(GoodsParticipantScope owner, String kind) { }
    private static final ImmutableKeyedInputView<PositionQuery, List<BlockPosition>> LOCATIONS = new ImmutableKeyedInputView<>(512);

    private static Map<SubjectId, Container> all(FrontierWorldState state) {
        return CONTAINERS.project(state.bootstrap(), state.inventory().containers(), state);
    }
    private static List<Container> owned(FrontierWorldState state, SubjectId owner) {
        return OWNERS.unorderedRows(state.bootstrap(), all(state), row -> Set.of(row.storage().owner()), owner);
    }
    static Map<String, Integer> stock(FrontierWorldState state, GoodsParticipant p) {
        var home = owned(state, p.settlementId());
        var party = p.party().id().equals(p.settlementId()) ? List.<Container>of() : owned(state, p.party().id());
        return STOCK.getPublished(state.bootstrap(), GoodsParticipantScope.of(p), List.of(home, party), () -> {
            var result = new TreeMap<String, Integer>();
            for (var kind : specifications(state).keySet()) {
                int total = 0;
                for (var rows : List.of(home, party)) for (var row : rows)
                    if (eligible(row, kind)) total = Math.addExact(total, row.available(p.party().id(), kind));
                result.put(kind, total);
            }
            return Map.copyOf(result);
        });
    }
    static int at(FrontierWorldState state, GoodsParticipant p, SubjectId container, String kind) {
        GoodsStockSpecification.forKind(state.bootstrap().ruleset().goodsTrade(), kind);
        var row = all(state).get(container);
        // Destroyed/withdrawn storage is an ordinary absent source. An exact
        // transfer still validates its retained endpoint through its own owner.
        return row == null ? 0 : row.available(p.party().id(), kind);
    }
    static List<BlockPosition> positions(FrontierWorldState state, GoodsParticipant p, String kind) {
        var home = ResourceStorageFacts.owned(state, p.settlementId());
        var party = p.party().id().equals(p.settlementId()) ? List.<ResourceStorageFacts.Fact>of() : ResourceStorageFacts.owned(state, p.party().id());
        return LOCATIONS.getPublished(state.bootstrap(), new PositionQuery(GoodsParticipantScope.of(p), kind), List.of(home, party), () -> {
            var result = new ArrayList<BlockPosition>();
            for (var rows : List.of(home, party)) for (var row : rows)
                if (eligible(row, kind)) result.add(row.position().orElseThrow());
            result.sort(Comparator.comparingInt(BlockPosition::x).thenComparingInt(BlockPosition::y).thenComparingInt(BlockPosition::z));
            return List.copyOf(result);
        });
    }
    private static boolean eligible(Container row, String kind) { return eligible(row.storage(), kind); }
    private static boolean eligible(ResourceStorageFacts.Fact row, String kind) {
        return row.usable() && row.policy().filter(policy -> policy.role() == ResourceStorageRole.WAREHOUSE && policy.filter().accepts(kind)).isPresent();
    }
    private static Container project(FrontierWorldState state, ContainerRecord record) {
        var specs = specifications(state);
        var amounts = new HashMap<SubjectId, Map<String, Integer>>();
        var fungible = FungibleResourceReadIndex.stockAvailability(state.inventory().fungibleResources()).get(record.id());
        if (fungible != null) fungible.unclaimed().forEach((owner, values) -> {
            var selected = new HashMap<String, Integer>();
            values.forEach((kind, quantity) -> { if (specs.containsKey(kind) && specs.get(kind).representation() == GoodsStockSpecification.Representation.FUNGIBLE)
                selected.put(kind, quantity); });
            amounts.put(owner, selected);
        });
        var claims = WorkEquipmentClaimComposition.reservedItems(state);
        for (var item : ExactItemReadIndex.containers(state.inventory()).getOrDefault(record.id(), List.of())) {
            var spec = specs.get(item.itemKind());
            if (spec != null && spec.accepts(item) && !claims.containsKey(item.id()))
                amounts.computeIfAbsent(item.economicOwnerId(), ignored -> new HashMap<>()).merge(item.itemKind(), 1, Math::addExact);
        }
        amounts.replaceAll((owner, values) -> Map.copyOf(values));
        return new Container(ResourceStorageFacts.all(state).get(record.id()), Map.copyOf(amounts));
    }
    private static Map<String, GoodsStockSpecification> specifications(FrontierWorldState state) {
        var rules = state.bootstrap().ruleset().goodsTrade();
        return SPECIFICATIONS.get(List.of(rules), () -> {
            var result = new HashMap<String, GoodsStockSpecification>();
            rules.policies().values().forEach(values -> values.forEach(value -> result.put(value.itemKind(), value.stock())));
            return Map.copyOf(result);
        });
    }
    private GoodsStorageFacts() { }
}
