package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;

import java.util.Objects;

/** Durable admission of one new exact resident; physical materialization is a later HOT lease. */
public record ResidentBorn(ResidentProfile resident, BlockPosition position) implements FrontierPayload {
    public ResidentBorn { Objects.requireNonNull(resident, "resident"); Objects.requireNonNull(position, "birth position"); }
    @Override public String type() { return "frontier.resident_born"; }
}
