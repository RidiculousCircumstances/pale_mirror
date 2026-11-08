package io.farfrontier.palemirror.frontier.v3.model.routine;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/**
 * At most one row per resident; active traversal is derived incrementally, never a second timer
 * queue.
 */
public record ResidentRoutineState(Map<SubjectId, ResidentRoutine> rows) {
  private record Image(
      Map<SubjectId, ResidentRoutine> rows, Map<SubjectId, ResidentRoutine> active) {}

  private static final ThreadLocal<Image> ACTIVE = new ThreadLocal<>();

  public ResidentRoutineState {
    rows = ImmutableLookupMap.copyOf(Objects.requireNonNull(rows));
    if (rows.size() > SimulationCapacity.MAX_RESIDENTS)
      throw new IllegalArgumentException("routine retention capacity exceeded");
    // Exact key/foreign references are checked by the sparse world relationship owner.
  }

  public static ResidentRoutineState empty() {
    return new ResidentRoutineState(Map.of());
  }

  public ResidentRoutineState with(ResidentRoutine row) {
    return new ResidentRoutineState(ImmutableLookupMap.withEntry(rows, row.actor(), row));
  }

  public ResidentRoutineState without(SubjectId actor) {
    return new ResidentRoutineState(ImmutableLookupMap.withoutEntry(rows, actor));
  }

  public Map<SubjectId, ResidentRoutine> active() {
    var prior = ACTIVE.get();
    if (prior != null && prior.rows() == rows) return prior.active();
    var old = prior == null ? Map.<SubjectId, ResidentRoutine>of() : prior.rows();
    var result = new TreeMap<SubjectId, ResidentRoutine>(prior == null ? Map.of() : prior.active());
    ImmutableMapChanges.forEachIdentityChangedKey(
        old,
        rows,
        id -> {
          var row = rows.get(id);
          if (row == null || row.phase() == ResidentRoutine.Phase.CLOSED) result.remove(id);
          else result.put(id, row);
        });
    var value = Collections.unmodifiableMap(result);
    ACTIVE.set(new Image(rows, value));
    return value;
  }
}
