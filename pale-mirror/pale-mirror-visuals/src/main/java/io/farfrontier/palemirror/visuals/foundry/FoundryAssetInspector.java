package io.farfrontier.palemirror.visuals.foundry;

import io.farfrontier.palemirror.api.FoundryAuditPhase;
import io.farfrontier.palemirror.api.FoundryFinding;
import io.farfrontier.palemirror.api.FoundrySeverity;
import io.farfrontier.palemirror.api.VisualModulePlacement;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.visuals.genesis.AuthoredModuleCompiler;
import java.util.ArrayList;
import java.util.List;

/** NBT/template lint which runs through the same sanitized compiler used by materialization. */
final class FoundryAssetInspector {
    Inspection inspect(VisualModulePlacement module, String dimensionId, FoundryAuditPhase phase) {
        var snapshot = AuthoredModuleCompiler.compile(module);
        List<FoundryFinding> findings = new ArrayList<>();
        int blockEntities = 0;
        int escaped = 0;
        int falling = 0;
        var occupied = snapshot.blocks().stream().filter(value -> !value.state().isAir())
                .collect(java.util.stream.Collectors.toMap(value -> key(value.position()), value -> value.state(),
                        (first, second) -> second));
        for (var block : snapshot.blocks()) {
            if (!module.footprint().contains(block.position())) {
                escaped++;
                add(findings, "asset.footprint.escape", FoundrySeverity.BLOCKER, phase, module, dimensionId,
                        block.position(), "Sanitized NBT cell escaped the declared true footprint.",
                        "Correct the asset dimensions, origin or rotation transform.");
            }
            if (block.state().hasBlockEntity()) {
                blockEntities++;
                if (block.blockEntityData().isPresent()
                        && !block.blockEntityData().orElseThrow().contains("id")) {
                    add(findings, "asset.block_entity.untyped", FoundrySeverity.BLOCKER, phase, module, dimensionId,
                            block.position(), "Authored block entity retained an untyped payload.",
                            "Provide a valid block-entity id or remove the source payload.");
                }
            }
            if (block.state().getBlock() instanceof net.minecraft.world.level.block.FallingBlock
                    && block.position().y() > module.footprint().min().y()
                    && !occupied.containsKey(key(new VisualPoint(block.position().x(), block.position().y() - 1,
                    block.position().z())))) {
                falling++;
                add(findings, "asset.falling.unsupported", FoundrySeverity.WARNING, phase, module, dimensionId,
                        block.position(), "Falling authored block has no support inside its template.",
                        "Replace it with stable paving/support or include a real supporting cell.");
            }
        }
        return new Inspection(snapshot.blocks().size(), escaped, blockEntities, falling, List.copyOf(findings));
    }

    private static void add(List<FoundryFinding> findings, String rule, FoundrySeverity severity,
                            FoundryAuditPhase phase, VisualModulePlacement module, String dimension,
                            VisualPoint position, String message, String remediation) {
        if (findings.stream().filter(value -> value.ruleId().equals(rule)).count() >= 8) return;
        findings.add(new FoundryFinding(rule, severity, phase, "asset", module.templateId(), dimension,
                position, message, remediation));
    }

    private static String key(VisualPoint point) { return point.x() + ":" + point.y() + ":" + point.z(); }

    record Inspection(int cells, int escaped, int blockEntities, int unsupportedFalling,
                      List<FoundryFinding> findings) { }
}
