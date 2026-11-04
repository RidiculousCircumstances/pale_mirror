package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.*;
/** Explicit closed registration by execution kind. Missing implementations fail visibly. */
public final class CombatCapabilities {
    private final Map<ActorActivityKind,CombatCapability> entries;
    public CombatCapabilities(List<CombatCapability> values) {
        var registered=new EnumMap<ActorActivityKind,CombatCapability>(ActorActivityKind.class);
        for(var value:values)if(registered.putIfAbsent(value.kind(),value)!=null)throw new IllegalArgumentException("duplicate combat capability");
        entries=Map.copyOf(registered);
    }
    public CombatCapability require(ActorExecutionId execution) {
        var value=entries.get(execution.activityKind());
        if(value==null)throw new IllegalArgumentException("unregistered combat execution kind: "+execution.activityKind());return value;
    }
    /** Read-only capability discovery, e.g. recording a mixture of combatants and ordinary bystanders. */
    public boolean supports(ActorActivityKind kind){return entries.containsKey(Objects.requireNonNull(kind));}
    public Optional<CombatOrder> current(FrontierWorldState state,ActorExecutionId execution,long tick) {
        var value=require(execution);value.requireReference(state,execution);return value.current(state,execution,tick);
    }
    public List<CombatOrder> orders(FrontierWorldState state,long tick) {
        var values=entries.values().stream().flatMap(value->value.orders(state,tick).stream())
                .sorted(Comparator.comparing(CombatOrder::owner)).toList();
        if(values.stream().map(CombatOrder::owner).distinct().count()!=values.size())throw new IllegalArgumentException("duplicate combat order owner");
        return values;
    }
}
