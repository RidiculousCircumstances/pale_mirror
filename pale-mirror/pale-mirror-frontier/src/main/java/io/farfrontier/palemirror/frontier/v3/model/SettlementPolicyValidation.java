package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Population owner validates changed need/health/profile rows, including
 * reverse profile dependencies. Recovery explicitly rechecks the whole image. */
final class SettlementPolicyValidation {
    private record Row(ResidentProfile profile, ResidentNutrition nutrition, ResidentHealth health) { }
    private record Image(FrontierBootstrap bootstrap, Map<SubjectId, ResidentProfile> residents,
                         Map<SubjectId, ResidentNutrition> nutrition, Map<SubjectId, ResidentHealth> health,
                         Map<SubjectId, Row> rows) { }
    private static final ThreadLocal<Image> IMAGE = new ThreadLocal<>();
    private static final ImmutableInvariantCheck<Void> COMPLETE = new ImmutableInvariantCheck<>();
    private static final ImmutableInvariantCheck<Void> DECLARATIONS = new ImmutableInvariantCheck<>();
    private SettlementPolicyValidation() { }

    static void validate(FrontierBootstrap bootstrap, HumanPopulation population) {
        COMPLETE.validate(() -> validateRows(bootstrap, population), bootstrap, population.quarantines(),
                population.schedules(), population.residents(), population.nutrition(), population.health());
    }
    private static void validateRows(FrontierBootstrap bootstrap, HumanPopulation population) {
        DECLARATIONS.validate(() -> {
            Set<SubjectId> expected = bootstrap.settlements().stream().map(Settlement::id)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            if (!population.quarantines().keySet().equals(expected))
                throw new IllegalArgumentException("settlement quarantine index must own every and only canonical settlement");
            if (population.schedules().values().stream().anyMatch(s -> s.dayTicks() != bootstrap.ruleset().residentLife().dayTicks()))
                throw new IllegalArgumentException("settlement schedules must use the world's canonical calendar day");
        }, bootstrap, population.quarantines(), population.schedules());
        var image = IMAGE.get();
        boolean full = image == null || image.bootstrap() != bootstrap || ImmutableInvariantCheck.fullAudit();
        var changed = new HashSet<SubjectId>();
        if (full) changed.addAll(population.nutrition().keySet());
        else {
            ImmutableMapChanges.forEachIdentityChangedKey(image.residents(), population.residents(), changed::add);
            ImmutableMapChanges.forEachIdentityChangedKey(image.nutrition(), population.nutrition(), changed::add);
            ImmutableMapChanges.forEachIdentityChangedKey(image.health(), population.health(), changed::add);
        }
        var rows = full ? new HashMap<SubjectId, Row>() : image.rows();
        var rules = bootstrap.ruleset().residentLife();
        try {
            for (var id : changed) {
                var resident = population.resident(id); var need = population.nutrition().get(id); var health = population.health(id);
                if (resident == null && need == null && health == null) { rows.remove(id); continue; }
                if (resident == null || need == null || health == null)
                    throw new IllegalArgumentException("population policy row lost its declared profile, nutrition or health");
                var old = rows.get(id);
                if (!full && old != null && old.profile() == resident && old.nutrition() == need && old.health() == health) continue;
                var condition = health.starvation();
                if (condition.exposureRemainder() >= rules.starvation().gainTicksPerUnit()
                        || condition.recoveryRemainder() >= rules.starvation().recoveryTicksPerUnit())
                    throw new IllegalArgumentException("starvation remainder contradicts world rules");
                if (!need.equals(need.accrueThrough(need.lastEvaluatedTick(), rules,
                        resident.characteristics().effectiveMetabolismPermille(need.lastEvaluatedTick()))))
                    throw new IllegalArgumentException("nutrition category or empty-stomach remainder contradicts world rules");
                rows.put(id, new Row(resident, need, health));
            }
            // Install the complete source basis only after every changed row passes.
            IMAGE.set(new Image(bootstrap, population.residents(), population.nutrition(), population.health(), rows));
        } catch (RuntimeException | Error failed) { IMAGE.remove(); throw failed; }
    }
}
