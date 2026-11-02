package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.model.combat.*;
import java.util.List;
/** Application composition only. New combat-capable roles register their own permission adapter. */
public final class CombatComposition {
    public static final CombatCapabilities CAPABILITIES=new CombatCapabilities(List.of(new GuardCombatCapability()));
    private CombatComposition(){}
}
