package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Test-only coordinate translation; never an active simulation provider. */
final class FrontierV3BootstrapGameTestFixtures {
    private FrontierV3BootstrapGameTestFixtures() { }
    static FrontierBootstrap translatedBootstrap(FrontierBootstrap source, int dx, int dy, int dz) {
        java.util.function.Function<BlockPosition, BlockPosition> translate = position -> new BlockPosition(position.x() + dx, position.y() + dy, position.z() + dz);
        List<Settlement> settlements = source.settlements().stream().map(settlement -> new Settlement(settlement.id(), settlement.displayName(),
                translate.apply(settlement.anchor()), settlement.residents().stream().map(resident -> new Resident(resident.id(), resident.settlementId(),
                resident.role(), translate.apply(resident.home()))).toList(), settlement.structures().stream().map(structure -> new SettlementStructure(
                structure.id(), structure.settlementId(), structure.kind(), translate.apply(structure.anchor()), structure.facing())).toList())).toList();
        List<HiveNest> nests = source.hive().seedNests().stream().map(nest -> new HiveNest(nest.id(), nest.hiveId(), translate.apply(nest.anchor()))).toList();
        List<HiveOrgan> organs = source.hive().organs().stream().map(organ -> new HiveOrgan(organ.id(), organ.hiveId(), organ.nestId(), organ.kind(),
                translate.apply(organ.anchor()), organ.containerId())).toList();
        List<Bioform> bioforms = source.hive().bioforms().stream().map(bioform -> new Bioform(bioform.id(), bioform.hiveId(), bioform.nestId(),
                bioform.chassis(), bioform.mutations(), bioform.assignment(), translate.apply(bioform.position()))).toList();
        Map<TerrainColumn, Integer> surveyed = new LinkedHashMap<>();
        source.terrain().surveyedSupportY().forEach((column, supportY) -> surveyed.put(new TerrainColumn(column.x() + dx, column.z() + dz), supportY + dy));
        return new FrontierBootstrap(source.worldId(), source.seed(), new WorldBounds(source.bounds().minX() + dx, source.bounds().minZ() + dz,
                source.bounds().width(), source.bounds().depth()), settlements, new Hive(source.hive().id(), nests, organs, bioforms), source.ruleset(),
                new TerrainSurfacePlan(source.terrain().baselineSupportY() + dy, surveyed));
    }
}
