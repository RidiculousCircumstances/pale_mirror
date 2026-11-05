package io.farfrontier.palemirror.frontier.v3.model.buildings;

/** A coherent declared selection of templates, not a behavior or profession. */
public record BuildingStyleId(String value) {
    public BuildingStyleId {
        if (value == null || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || value.length() > 160)
            throw new IllegalArgumentException("invalid building style id");
    }
}
