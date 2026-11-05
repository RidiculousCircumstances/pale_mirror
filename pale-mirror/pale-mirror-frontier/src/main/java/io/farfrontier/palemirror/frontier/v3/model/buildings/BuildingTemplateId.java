package io.farfrontier.palemirror.frontier.v3.model.buildings;

/** Producer-declared authored template identity, independent of building function. */
public record BuildingTemplateId(String value) {
    public BuildingTemplateId {
        if (value == null || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || value.length() > 160)
            throw new IllegalArgumentException("invalid building template id");
    }
}
