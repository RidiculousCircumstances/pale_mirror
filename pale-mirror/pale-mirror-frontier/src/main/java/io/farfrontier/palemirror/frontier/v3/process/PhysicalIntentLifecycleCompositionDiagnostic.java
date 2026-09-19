package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;

import java.util.List;

/** Read-only bounded account derived from the installed physical lifecycle declarations. */
public record PhysicalIntentLifecycleCompositionDiagnostic(String fingerprint, List<Owner> owners) {
    public PhysicalIntentLifecycleCompositionDiagnostic {
        fingerprint = java.util.Objects.requireNonNull(fingerprint, "physical lifecycle fingerprint");
        owners = List.copyOf(java.util.Objects.requireNonNull(owners, "physical lifecycle diagnostic owners"));
    }

    public record Owner(PhysicalIntentLifecycleOwner owner, int declarationVersion, List<Integer> schemaTags,
                        int unresolved, int resolvedRetained, int currentRecoveryBindings,
                        int maxUnresolved, int maxResolvedRetention, Pressure pressure) {
        public Owner {
            owner = java.util.Objects.requireNonNull(owner, "physical lifecycle diagnostic owner");
            schemaTags = List.copyOf(java.util.Objects.requireNonNull(schemaTags, "physical lifecycle diagnostic schema tags"));
            pressure = java.util.Objects.requireNonNull(pressure, "physical lifecycle diagnostic pressure");
        }
    }

    public enum Pressure { OPEN, COMPACTION_REQUIRED, UNRESOLVED_SATURATED }
}
