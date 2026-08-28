package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;
public record CargoLoaded(SubjectId contractId, CargoBatch cargo) implements FrontierPayload {
    public CargoLoaded { Objects.requireNonNull(contractId); Objects.requireNonNull(cargo); }
    @Override public String type() { return "frontier.cargo_loaded"; }
}
