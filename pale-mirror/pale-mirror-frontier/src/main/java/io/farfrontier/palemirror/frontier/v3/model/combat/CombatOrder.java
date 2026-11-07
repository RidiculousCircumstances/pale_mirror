package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import java.util.*;
/** Immutable caller permission. Tactical code owns no appointment, political relation or body. */
public record CombatOrder(ActorExecutionId execution,long revision,SubjectId weapon,CombatPartyId party,CombatEnvelope engagement,
        SurfaceAnchor targetPosition,long observedAt,long lifetime,SurfaceAnchor actorPosition,io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile navigation,
            Optional<SurfaceAnchor> station,CombatStyle style,CombatProfile profile,CombatUse use) {
    public CombatOrder {
        Objects.requireNonNull(execution);Objects.requireNonNull(weapon);Objects.requireNonNull(engagement);Objects.requireNonNull(party);
        Objects.requireNonNull(targetPosition);Objects.requireNonNull(actorPosition);Objects.requireNonNull(navigation);Objects.requireNonNull(station);Objects.requireNonNull(style);Objects.requireNonNull(profile);Objects.requireNonNull(use);
        if(use instanceof CombatUse.Melee melee && !profile.knows(melee.skill(),CombatTrainingCatalog.standard().require(melee.fallback()).minimum()))
            throw new IllegalArgumentException("combat permission needs a learned fallback");
        profile.skill(use.skill());
        if(revision<1 || observedAt<0 || lifetime<1 || !engagement.contains(targetPosition))throw new IllegalArgumentException("invalid combat permission");
    }
    /** Native planning input only; common body inspection alone publishes a position. */
    public CombatOrder atActorObservation(SurfaceAnchor observed){return new CombatOrder(execution,revision,weapon,party,engagement,targetPosition,observedAt,lifetime,observed,navigation,station,style,profile,use);}
    public CombatMethod method(){return use.method();}
    public CombatSkillId weaponSkill(){return use.skill();}
    public Set<MeleeAttack> techniques(){return use instanceof CombatUse.Melee melee?melee.techniques():Set.of();}
    public boolean permitsStrike(MeleeAttack attack){return techniques().contains(attack)||CombatContactDefence.permits(this,attack);}
    public MeleeAttack fallback(){if(use instanceof CombatUse.Melee melee)return melee.fallback();throw new IllegalArgumentException("ranged permission has no melee fallback");}
    public SubjectId owner(){return execution.activityOwnerId();}
    public CombatTarget target(){return engagement.target();}
    public boolean current(long tick){return tick>=observedAt && tick-observedAt<=lifetime;}
}
