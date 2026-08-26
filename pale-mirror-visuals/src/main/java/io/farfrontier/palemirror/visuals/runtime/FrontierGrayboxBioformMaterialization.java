package io.farfrontier.palemirror.visuals.runtime;

import io.farfrontier.palemirror.api.FrontierProjection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Projects all moving hive forms while preserving the canonical run as the placement authority. */
final class FrontierGrayboxBioformMaterialization {
    void materialize(ServerLevel level, FrontierGrayboxRuntime runtime, FrontierGrayboxRuntime.ProjectionState state,
                     FrontierProjection projection) {
        Map<String, FrontierProjection.Hive> hives = projection.hives().stream()
                .collect(java.util.stream.Collectors.toMap(FrontierProjection.Hive::id, value -> value));
        Map<String, FrontierProjection.Assault> assaults = new HashMap<>();
        for (FrontierProjection.Assault assault : projection.assaults()) {
            if (assault.state().equals("COMPLETED") || assault.state().equals("ABORTED")) continue;
            for (String participant : assault.participantIds()) assaults.put(participant, assault);
        }
        Map<String, FrontierProjection.HarvesterRun> harvesters = new HashMap<>();
        for (FrontierProjection.HarvesterRun run : projection.harvesterRuns()) {
            if (!run.state().equals("COMPLETED") && !run.state().equals("ABORTED")) harvesters.put(run.bioformId(), run);
        }
        Map<String, FrontierProjection.PropagationRun> propagations = new HashMap<>();
        for (FrontierProjection.PropagationRun run : projection.propagationRuns()) {
            if (run.state().equals("OUTBOUND")) propagations.put(run.bioformId(), run);
        }
        Map<String, Integer> indices = new HashMap<>();
        for (FrontierProjection.Bioform bioform : projection.bioforms().stream().sorted(Comparator.comparing(FrontierProjection.Bioform::id)).toList()) {
            FrontierProjection.Hive hive = hives.get(bioform.hiveId());
            if (hive == null) continue;
            int index = indices.merge(bioform.hiveId(), 1, Integer::sum) - 1;
            FrontierProjection.Assault assault = assaults.get(bioform.id());
            FrontierProjection.HarvesterRun harvester = harvesters.get(bioform.id());
            FrontierProjection.PropagationRun propagation = propagations.get(bioform.id());
            BlockPos position = harvester != null ? FrontierGrayboxRuntime.bioformPosition(harvester.cellX(), harvester.cellZ(), 0)
                    : propagation != null ? FrontierGrayboxRuntime.bioformPosition(propagation.cellX(), propagation.cellZ(), 0)
                    : assault == null ? FrontierGrayboxRuntime.bioformPosition(hive, index)
                    : FrontierGrayboxRuntime.bioformPosition(assault.cellX(), assault.cellZ(), assault.participantIds().indexOf(bioform.id()));
            if (bioform.alive()) runtime.ensureZombie(level, state, bioform, position);
            else runtime.retireEntity(level, bioform.id());
        }
    }
}
