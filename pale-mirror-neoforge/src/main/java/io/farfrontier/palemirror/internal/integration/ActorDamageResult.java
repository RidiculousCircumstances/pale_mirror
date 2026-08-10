package io.farfrontier.palemirror.internal.integration;

/**
 * Result of an adapter-owned incoming-damage decision.  The event bridge owns
 * cancellation and observation publication; an adapter may only classify the
 * already-registered physical actor.
 */
public record ActorDamageResult(Disposition disposition, String diagnostic) {
    public enum Disposition { PASS_THROUGH, BLOCKED, CONSUMED, DEFEATED }

    public ActorDamageResult {
        if (disposition == null) throw new IllegalArgumentException("disposition");
        diagnostic = diagnostic == null ? "" : diagnostic;
    }

    public static ActorDamageResult passThrough() { return new ActorDamageResult(Disposition.PASS_THROUGH, ""); }
    public static ActorDamageResult blocked(String reason) { return new ActorDamageResult(Disposition.BLOCKED, reason); }
    public static ActorDamageResult consumed() { return new ActorDamageResult(Disposition.CONSUMED, ""); }
    public static ActorDamageResult defeated() { return new ActorDamageResult(Disposition.DEFEATED, ""); }
    public boolean intercepts() { return disposition != Disposition.PASS_THROUGH; }
}
