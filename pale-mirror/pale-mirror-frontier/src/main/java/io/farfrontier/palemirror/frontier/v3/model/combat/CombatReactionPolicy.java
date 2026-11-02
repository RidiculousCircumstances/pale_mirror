package io.farfrontier.palemirror.frontier.v3.model.combat;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.*;

/** Reusable individual decision. Knows no settlement, job, scheduler, navigator or body actuator. */
public final class CombatReactionPolicy {
    public record Rules(int localRadius, long sightLifetime, long harmLifetime, int switchAdvantage) {
        public Rules {
            if (localRadius < 1 || sightLifetime < 1 || harmLifetime < 1 || switchAdvantage < 1)
                throw new IllegalArgumentException("invalid local combat policy");
        }
    }
    public record Area(SurfaceAnchor anchor, int radius) {
        public Area { Objects.requireNonNull(anchor); if (radius < 1) throw new IllegalArgumentException("invalid defence area"); }
        public boolean contains(SurfaceAnchor position) { return anchor.manhattanDistance(position) <= radius; }
    }
    public record Threat(CombatTarget target, SurfaceAnchor position, Optional<CombatAwareness.Sight> personalSight,
                         List<CombatAwareness.Harm> harms) {
        public Threat {
            Objects.requireNonNull(target); Objects.requireNonNull(position); Objects.requireNonNull(personalSight); harms = List.copyOf(harms);
            if (personalSight.filter(s -> !s.key().target().equals(target)).isPresent()
                    || harms.stream().anyMatch(h -> !h.key().attacker().equals(target)))
                throw new IllegalArgumentException("local threat evidence names another target");
        }
    }
    public static Optional<CombatEngagement> choose(Rules rules, SubjectId actor, SurfaceAnchor position, List<Area> areas,
            List<Threat> threats, Optional<CombatEngagement> current, long tick) {
        Objects.requireNonNull(actor); Objects.requireNonNull(position); Objects.requireNonNull(current);
        var choices = new ArrayList<CombatEngagement>();
        for (var threat : threats) {
            var harms = threat.harms().stream().filter(h -> h.current(tick, rules.harmLifetime())).toList();
            var self = harms.stream().filter(h -> h.key().victim().equals(actor))
                    .max(Comparator.comparingLong(CombatAwareness.Harm::atTick));
            var ownSight=threat.personalSight().filter(s -> s.key().observer().equals(actor) && s.current(tick,rules.sightLifetime()));
            var observer=ownSight.map(s -> new SurfaceAnchor(s.observerPosition()))
                    .orElseGet(() -> self.map(h -> new SurfaceAnchor(h.victimPosition())).orElse(position));
            if (self.isPresent() && observer.manhattanDistance(threat.position()) <= rules.localRadius()
                    && new SurfaceAnchor(self.orElseThrow().victimPosition()).manhattanDistance(threat.position()) <= rules.localRadius()) {
                choices.add(new CombatEngagement(threat.target(), CombatEngagement.Kind.SELF_DEFENCE,
                        new SurfaceAnchor(self.orElseThrow().victimPosition()), rules.localRadius())); continue;
            }
            var ally = harms.stream().filter(h -> !h.key().victim().equals(actor)
                            && observer.manhattanDistance(new SurfaceAnchor(h.victimPosition())) <= rules.localRadius()
                            && new SurfaceAnchor(h.victimPosition()).manhattanDistance(threat.position()) <= rules.localRadius())
                    .sorted(Comparator.comparingLong((CombatAwareness.Harm h) -> position.manhattanDistance(new SurfaceAnchor(h.victimPosition())))
                            .thenComparing(h -> h.key().victim())).findFirst();
            if (ally.isPresent()) {
                choices.add(new CombatEngagement(threat.target(), CombatEngagement.Kind.ALLY_DEFENCE,
                        new SurfaceAnchor(ally.orElseThrow().victimPosition()), rules.localRadius())); continue;
            }
            boolean seen = ownSight.isPresent();
            if (!seen || observer.manhattanDistance(threat.position()) > rules.localRadius()) continue;
            // Keep a locally defended envelope stable; visibility never drags its leash along.
            var retained = current.filter(e -> e.kind().local() && e.target().equals(threat.target()) && e.contains(threat.position()));
            if (retained.isPresent()) { choices.add(retained.orElseThrow()); continue; }
            areas.stream().filter(area -> area.contains(threat.position()))
                    .sorted(Comparator.comparingLong((Area area) -> area.anchor().manhattanDistance(threat.position()))
                            .thenComparingInt(area -> area.anchor().x()).thenComparingInt(area -> area.anchor().z()).thenComparingInt(area -> area.anchor().y()))
                    .findFirst().ifPresent(area -> choices.add(new CombatEngagement(threat.target(), CombatEngagement.Kind.AREA_DEFENCE, area.anchor(), area.radius())));
        }
        if (choices.isEmpty()) return Optional.empty();
        var locations = new HashMap<CombatTarget, SurfaceAnchor>(); threats.forEach(t -> locations.put(t.target(), t.position()));
        choices.sort(Comparator.comparingInt((CombatEngagement e) -> e.kind().wireTag())
                .thenComparingLong(e -> position.manhattanDistance(locations.get(e.target())))
                .thenComparing(e -> e.target().kind().wireTag()).thenComparing(e -> e.target().entity()).thenComparing(e -> e.target().dimension()));
        var best = choices.getFirst();
        var retained = current.flatMap(old -> choices.stream().filter(e -> e.target().equals(old.target()) && e.kind() == best.kind()).findFirst());
        return retained.filter(e -> position.manhattanDistance(locations.get(e.target()))
                <= position.manhattanDistance(locations.get(best.target())) + rules.switchAdvantage()).or(() -> Optional.of(best));
    }
    private CombatReactionPolicy() { }
}
