package io.farfrontier.palemirror.domain;

import java.util.List;

/** Fail-closed diagnostic for an internally inconsistent canonical snapshot. */
public final class DomainStateValidationException extends IllegalStateException {
    private final List<String> violations;

    public DomainStateValidationException(List<String> violations) {
        super("Invalid Pale Mirror canonical state: " + String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> violations() {
        return violations;
    }
}
