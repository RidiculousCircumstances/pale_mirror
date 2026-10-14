package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Population owner validates changed need/health/profile rows, including
 * reverse profile dependencies; another resident's hunger does not invalidate
 * every previously checked row. Recovery explicitly rechecks the whole image. */
final class SettlementPolicyValidation {
    private record Row(ResidentProfile profile, ResidentNutrition nutrition, Object health) { }
    private record Image(FrontierBootstrap bootstrap, Map<SubjectId, Row> rows) { }
    private static final ThreadLocal<Image> IMAGE = new ThreadLocal<>();
    private static final ImmutableInvariantCheck<Void> DECLARATIONS = new ImmutableInvariantCheck<>();
    private SettlementPolicyValidation() { }

    static void validate(FrontierBootstrap bootstrap, HumanPopulation population) {
        DECLARATIONS.validate(() -> {
            Set<SubjectId> expected = bootstrap.settlements().stream().map(Settlement::id)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            if (!population.quarantines().keySet().equals(expected))
                throw new IllegalArgumentException("settlement quarantine index must own every and only canonical settlement");
            if (population.schedules().values().stream().anyMatch(s -> s.dayTicks() != bootstrap.ruleset().residentLife().dayTicks()))
                throw new IllegalArgumentException("settlement schedules must use the world's canonical calendar day");
        }, bootstrap, population.quarantines(), population.schedules());
        var image = IMAGE.get();
        if (image == null || image.bootstrap() != bootstrap) {
            image = new Image(bootstrap, new HashMap<>()); IMAGE.set(image);
        }
        var rows = image.rows();
        rows.keySet().removeIf(id -> !population.residents().containsKey(id));
        var rules = bootstrap.ruleset().residentLife();
        for (var entry : population.nutrition().entrySet()) {
            var id = entry.getKey(); var need = entry.getValue();
            var resident = population.resident(id); var health = population.health(id);
            var old = rows.get(id);
            if (!ImmutableInvariantCheck.fullAudit() && old != null && old.profile() == resident
                    && old.nutrition() == need && old.health() == health) continue;
            var condition = health.starvation();
            if (condition.exposureRemainder() >= rules.starvation().gainTicksPerUnit()
                    || condition.recoveryRemainder() >= rules.starvation().recoveryTicksPerUnit())
                throw new IllegalArgumentException("starvation remainder contradicts world rules");
            if (!need.equals(need.accrueThrough(need.lastEvaluatedTick(), rules,
                    resident.characteristics().effectiveMetabolismPermille(need.lastEvaluatedTick()))))
                throw new IllegalArgumentException("nutrition category or empty-stomach remainder contradicts world rules");
            rows.put(id, new Row(resident, need, health));
        }
    }
}
