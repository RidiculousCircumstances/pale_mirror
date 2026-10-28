package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** A fact owner identifies economically significant changes, without calculating plans. */
interface EconomicInputChangePort {
    void collect(FrontierWorldState before, FrontierWorldState after, Sink changes);
    @FunctionalInterface interface Sink {
        void changed(SubjectId owner, SettlementEconomicPlanningState.Change reason, Set<String> resources);
        default void resource(SubjectId owner, SettlementEconomicPlanningState.Change reason, String item) {
            changed(owner, reason, Set.of(item));
        }
    }
    /** Only inspect the modified immutable component. No world-state key or expensive fact assembly. */
    static <K, V> void difference(Map<K, V> before, Map<K, V> after, java.util.function.BiConsumer<V, V> change) {
        ImmutableMapChanges.forEach(before, after, delta -> change.accept(delta.before(), delta.after()));
    }
}
