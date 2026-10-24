package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Participant-supplied native outcome. A false callback alone never proves absence of effects. */
record FrontierV3NativeEffect(Supplier<Admission> preparation, Supplier<Outcome> mutation) {
    record Admission(java.util.Optional<String> waitingFor) {
        static final Admission READY = new Admission(java.util.Optional.empty());
        Admission {
            Objects.requireNonNull(waitingFor);
            if (waitingFor.filter(String::isBlank).isPresent()) throw new IllegalArgumentException("unnamed native prerequisite");
        }
        static Admission waiting(String dependency) { return new Admission(java.util.Optional.of(dependency)); }
        boolean ready() { return waitingFor.isEmpty(); }
    }
    enum Outcome { APPLIED, REFUSED_WITHOUT_EFFECT, UNKNOWN }
    FrontierV3NativeEffect { Objects.requireNonNull(preparation); Objects.requireNonNull(mutation); }

    /** Adapter for an effect whose negative result supplies no positive refusal evidence. */
    static FrontierV3NativeEffect observed(BooleanSupplier mutation) {
        Objects.requireNonNull(mutation);
        return new FrontierV3NativeEffect(() -> Admission.READY,
                () -> mutation.getAsBoolean() ? Outcome.APPLIED : Outcome.UNKNOWN);
    }

    /** Refusal evidence is sampled only inside the same live unsuccessful invocation. */
    static FrontierV3NativeEffect refusing(BooleanSupplier mutation, BooleanSupplier refusedWithoutEffect) {
        Objects.requireNonNull(mutation); Objects.requireNonNull(refusedWithoutEffect);
        return new FrontierV3NativeEffect(() -> Admission.READY, () -> mutation.getAsBoolean() ? Outcome.APPLIED
                : refusedWithoutEffect.getAsBoolean() ? Outcome.REFUSED_WITHOUT_EFFECT : Outcome.UNKNOWN);
    }

    FrontierV3NativeEffect preparedBy(Supplier<Admission> prerequisite) {
        Objects.requireNonNull(prerequisite);
        return new FrontierV3NativeEffect(() -> {
            var admission = Objects.requireNonNull(prerequisite.get());
            return admission.ready() ? preparation.get() : admission;
        }, mutation);
    }
}
