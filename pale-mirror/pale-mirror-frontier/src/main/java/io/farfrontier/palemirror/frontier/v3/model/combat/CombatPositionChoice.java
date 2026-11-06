package io.farfrontier.palemirror.frontier.v3.model.combat;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.Optional;
/** Shared tactical-position result; route waiting is distinct from a completed refusal. */
public record CombatPositionChoice(Optional<SurfaceAnchor> station,MeleePosition.Reason reason) {
    public CombatEvent.PositionPlanned event(CombatOrder order){return new CombatEvent.PositionPlanned(order.execution(),order.target(),order.targetPosition(),station,reason);}
}
