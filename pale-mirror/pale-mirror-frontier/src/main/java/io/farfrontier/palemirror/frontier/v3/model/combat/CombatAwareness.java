package io.farfrontier.palemirror.frontier.v3.model.combat;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Bounded dated knowledge. Old observer/victim identities are history, never current actuation. */
public record CombatAwareness(Map<SightKey, Sight> sights, Map<HarmKey, Harm> harms) {
    public static final int MAX_TARGETS_PER_SCOPE=16;
    public static final int MAX_RECORDS = SimulationCapacity.MAX_RESIDENTS * MAX_TARGETS_PER_SCOPE;
    public record SightKey(SubjectId scope, SubjectId observer, CombatTarget target) {
        public SightKey { Objects.requireNonNull(scope); Objects.requireNonNull(observer); Objects.requireNonNull(target); }
    }
    public record Sight(SightKey key, BlockPosition position, BlockPosition observerPosition, long atTick) {
        public Sight { Objects.requireNonNull(key); Objects.requireNonNull(position); Objects.requireNonNull(observerPosition); if (atTick < 0) throw new IllegalArgumentException("negative sight date"); }
        public boolean current(long tick, long lifetime) { return tick >= atTick && tick - atTick <= lifetime; }
    }
    public record HarmKey(SubjectId scope, SubjectId victim, CombatTarget attacker) {
        public HarmKey { Objects.requireNonNull(scope); Objects.requireNonNull(victim); Objects.requireNonNull(attacker); }
    }
    public record Harm(HarmKey key, BlockPosition victimPosition, long atTick) {
        public Harm { Objects.requireNonNull(key); Objects.requireNonNull(victimPosition); if (atTick < 0) throw new IllegalArgumentException("negative harm date"); }
        public boolean current(long tick, long lifetime) { return tick >= atTick && tick - atTick <= lifetime; }
    }
    private static final ImmutableInputView<Map<SubjectId, List<Harm>>> HARM_SCOPES = new ImmutableInputView<>();
    public CombatAwareness {
        sights = ImmutableLookupMap.copyOf(sights); harms = ImmutableLookupMap.copyOf(harms);
        if (sights.size() > MAX_RECORDS || harms.size() > MAX_RECORDS) throw new IllegalArgumentException("unbounded combat awareness");
        sights.forEach((key, value) -> { if (!key.equals(value.key())) throw new IllegalArgumentException("foreign personal sight"); });
        harms.forEach((key, value) -> { if (!key.equals(value.key())) throw new IllegalArgumentException("foreign witnessed harm"); });
    }
    public static CombatAwareness empty() { return new CombatAwareness(Map.of(), Map.of()); }
    public Optional<Sight> sight(SubjectId scope, SubjectId actor, CombatTarget target) {
        return Optional.ofNullable(sights.get(new SightKey(scope, actor, target)));
    }
    public List<Harm> harmsIn(SubjectId scope) {
        return HARM_SCOPES.get(List.of(harms), () -> {
            var result = new HashMap<SubjectId, List<Harm>>();
            harms.values().forEach(harm -> result.computeIfAbsent(harm.key().scope(), key -> new ArrayList<>()).add(harm));
            result.replaceAll((key, rows) -> List.copyOf(rows)); return Map.copyOf(result);
        }).getOrDefault(scope, List.of());
    }
    public CombatAwareness seen(Sight sight) {
        var prior = sights.get(sight.key());
        if (prior != null && prior.atTick() > sight.atTick()) throw new IllegalArgumentException("personal sight moves backwards");
        return new CombatAwareness(ImmutableLookupMap.withEntry(sights, sight.key(), sight), harms);
    }
    public CombatAwareness harmed(Harm harm) {
        var prior = harms.get(harm.key());
        if (prior != null && prior.atTick() > harm.atTick()) throw new IllegalArgumentException("harm observation moves backwards");
        return new CombatAwareness(sights, ImmutableLookupMap.withEntry(harms, harm.key(), harm));
    }
    public boolean needsPruning(SubjectId scope, long tick, long sightLifetime, long harmLifetime) {
        return sights.values().stream().anyMatch(s -> s.key().scope().equals(scope) && !s.current(tick, sightLifetime))
                || harmsIn(scope).stream().anyMatch(h -> !h.current(tick, harmLifetime));
    }
    public CombatAwareness pruned(SubjectId scope, long tick, long sightLifetime, long harmLifetime) {
        var nextSights = new LinkedHashMap<>(sights); var nextHarms = new LinkedHashMap<>(harms);
        nextSights.values().removeIf(s -> s.key().scope().equals(scope) && !s.current(tick, sightLifetime));
        nextHarms.values().removeIf(h -> h.key().scope().equals(scope) && !h.current(tick, harmLifetime));
        return nextSights.size() == sights.size() && nextHarms.size() == harms.size() ? this : new CombatAwareness(nextSights, nextHarms);
    }
    public CombatAwareness ended(SubjectId scope, CombatTarget target) {
        var nextSights = new LinkedHashMap<>(sights); var nextHarms = new LinkedHashMap<>(harms);
        nextSights.keySet().removeIf(key -> key.scope().equals(scope) && key.target().equals(target));
        nextHarms.keySet().removeIf(key -> key.scope().equals(scope) && key.attacker().equals(target));
        return new CombatAwareness(nextSights, nextHarms);
    }
}
