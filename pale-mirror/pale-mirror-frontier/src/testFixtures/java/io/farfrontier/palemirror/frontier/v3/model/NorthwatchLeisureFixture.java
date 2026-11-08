package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import java.util.*;

/** Two nourished, unassigned visitors near authored rest areas. No execution, route or seat outcome. */
final class NorthwatchLeisureFixture {
  static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration(WorldId world, long seed) {
    var base = FrontierWorldRuntimeDefinition.configuration(world, seed, FrontierRulesets.installed("frontier-v3-leisure-seats-r37"));
    var state = base.initialState();
    var home = state.bootstrap().settlements().getFirst();
    var permissions = SettlementWorkPolicy.permissions(state, home.id());
    for (var entry : Map.of(new SubjectId("resident:1-12"), StructureKind.KITCHEN,
        new SubjectId("resident:1-17"), StructureKind.HALL).entrySet()) {
      var building = home.structures().stream().filter(b -> b.kind() == entry.getValue()).findFirst().orElseThrow();
      var seat = building.definition().seats().values().stream()
          .filter(d -> building.definition().zones().get(d.zoneId()).use() == io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingDefinition.ZoneUse.REST)
          .sorted(Comparator.comparing(io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingSeat::id)).findFirst().orElseThrow();
      var start = entry.getValue() == StructureKind.HALL ? seat.approach().offset(0,0,2)
          : seat.approach().offset(-2 * seat.facing().x(),0,-2 * seat.facing().z());
      state = state.withActorBody(entry.getKey(), building.placement().support(start).standingBody());
      permissions = permissions.withoutResident(entry.getKey());
    }
    state = state.withStrategicPlans(state.strategicPlans().withWorkPermissions(home.id(), permissions));
    return new FrontierEngineConfiguration<>(base.worldId(), state, base.initialInstant(), base.commandPlanner(), base.scheduledPlanner(), base.reducer(),
        base.stateCodec(), base.projectionMapper(), base.limits(), base.initialSchedules(), base.transactionCommitter(), base.stateValidator(), base.executionMetrics(), base.kernelQuarantineReporter());
  }
  private NorthwatchLeisureFixture() {}
}
