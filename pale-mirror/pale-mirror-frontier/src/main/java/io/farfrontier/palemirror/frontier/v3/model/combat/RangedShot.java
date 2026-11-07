package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import java.util.*;
/** Exact native emission intent. A projectile, never a ranged MeleeStrike, owns its later collision. */
public record RangedShot(ActorActuationId attacker, CombatMethod method, long ownerRevision, SubjectId weapon,
        ItemCondition before, CombatTarget target, long targetIncarnation, SubjectId ammunitionAccount,
        SubjectId ammunitionLot, String ammunition, PhysicalStackBinding binding, long attemptOrdinal, long preparedAt, CombatProfile actorProfile, RangedTechnique technique, SurfaceAnchor origin) {
    public RangedShot {
        Objects.requireNonNull(technique);Objects.requireNonNull(origin);Objects.requireNonNull(actorProfile);
        if(!technique.unlocked(actorProfile))throw new IllegalArgumentException("unlearned ranged technique");actorProfile.skill(CombatSkillId.CROSSBOW);Objects.requireNonNull(attacker);Objects.requireNonNull(weapon);
                Objects.requireNonNull(before);Objects.requireNonNull(target);
        Objects.requireNonNull(ammunitionAccount);Objects.requireNonNull(ammunitionLot);Objects.requireNonNull(ammunition);Objects.requireNonNull(binding);
        if(method!=CombatMethod.CROSSBOW || ownerRevision<1 || targetIncarnation<0 || attemptOrdinal<1 || preparedAt<0 || before.remaining()<1
                || !binding.accountId().equals(ammunitionAccount) || !binding.itemKind().equals(ammunition)
                || binding.lotQuantities().getOrDefault(ammunitionLot,0)<1 || !binding.claimQuantities().isEmpty()
                || !(binding.address() instanceof PhysicalStackAddress.ActorStack address)
                || !address.actorId().equals(attacker.execution().actorId()))throw new IllegalArgumentException("invalid ranged shot declaration");
    }
    public SubjectId owner(){return attacker.execution().activityOwnerId();}
    public UUID projectileId(){return UUID.nameUUIDFromBytes(("arrow|"+((PhysicalStackAddress.ActorStack)binding.address()).entityId()+"|"+attacker.body().physicalEpoch()+"|"+attacker.execution().generation()+"|"
            +attemptOrdinal).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
}
