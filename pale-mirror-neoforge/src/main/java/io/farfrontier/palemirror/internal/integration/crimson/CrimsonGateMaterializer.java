package io.farfrontier.palemirror.internal.integration.crimson;

import java.util.List;
import java.util.Optional;

import io.farfrontier.palemirror.domain.GatePhaseRef;
import io.farfrontier.palemirror.domain.GatePlanRef;
import io.farfrontier.palemirror.internal.adapter.ActorOperationResult;
import io.farfrontier.palemirror.internal.adapter.SourceGateLayout;
import io.farfrontier.palemirror.internal.adapter.SourceGatePartSpec;
import io.farfrontier.palemirror.internal.combat.ThreatCombatLedger;
import io.farfrontier.palemirror.internal.world.MutableCell;
import io.farfrontier.palemirror.internal.world.PaleMirrorSavedData;
import io.farfrontier.palemirror.internal.world.SourceGatePartRef;
import io.farfrontier.palemirror.internal.world.TestMineRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;

/** Crimson-specific gate layout, entity setup and node provenance checks. */
final class CrimsonGateMaterializer {
    private static final String NODE_PROFILE_PREFIX = "pale_mirror:crimson_gate_node";
    private final CrimsonPresentationRuntime presentation;

    CrimsonGateMaterializer(CrimsonPresentationRuntime presentation) {
        this.presentation = presentation;
    }

    Optional<SourceGateLayout> layout(TestMineRecord site, GatePlanRef plan, GatePhaseRef phase) {
        List<SourceGatePartSpec> parts = switch (phase.id()) {
            case "nodes" -> nodeLayout(site, phase);
            case "boss" -> List.of(new SourceGatePartSpec("boss", plan.opaqueParameters().get("boss_profile"),
                    site.anchor().offset(0, 1, -2)));
            case "bloodlink_i" -> List.of(new SourceGatePartSpec("bloodlink_i", "pale_mirror:bloodlink_i",
                    site.anchor().offset(0, 1, 2)));
            case "bloodlink_ii" -> List.of(new SourceGatePartSpec("bloodlink_ii", "pale_mirror:bloodlink_ii",
                    site.anchor().offset(2, 1, 0)));
            case "bloodlink_iii" -> List.of(new SourceGatePartSpec("bloodlink_iii", "pale_mirror:bloodlink_iii",
                    site.anchor().offset(-2, 1, 0)));
            default -> List.of();
        };
        if (parts.isEmpty() || !parts.stream().map(SourceGatePartSpec::slotId).collect(java.util.stream.Collectors.toSet())
                .containsAll(phase.requiredPartIds())) return Optional.empty();
        return Optional.of(new SourceGateLayout(plan.id(), plan.version(), parts));
    }

    ActorOperationResult ensurePart(ServerLevel level, TestMineRecord mine, String jobId, SourceGatePartRef part) {
        if (part.profileId().startsWith(NODE_PROFILE_PREFIX)) return ensureNode(level, mine, part);
        CrimsonSiegeProfile profile = CrimsonSiegeProfile.byId(part.profileId()).orElse(null);
        if (profile == null) return ActorOperationResult.unavailable("Unsupported Crimson gate profile " + part.profileId());
        if (part.entityId() != null) {
            Entity existing = level.getEntity(part.entityId());
            if (CrimsonSandboxAdapter.isOwnedGatePart(existing, mine, part.slotId()) && profile.matches(existing)) {
                mine.gate().activate(part.slotId(), part.entityId());
                return ActorOperationResult.materialized();
            }
            if (existing != null) return ActorOperationResult.unavailable("Crimson gate identity conflict for slot " + part.slotId());
        }
        Mob entity = profile.create(level);
        if (entity == null) return ActorOperationResult.unavailable("Could not create Crimson gate entity");
        entity.moveTo(part.position().getX() + 0.5D, part.position().getY(), part.position().getZ() + 0.5D, 0.0F, 0.0F);
        entity.setPersistenceRequired();
        entity.getPersistentData().putString(CrimsonSandboxAdapter.OBJECT_ID_KEY, mine.id().value());
        entity.getPersistentData().putString(CrimsonSandboxAdapter.JOB_ID_KEY, jobId);
        entity.getPersistentData().putString(CrimsonSandboxAdapter.ROLE_KEY, CrimsonSandboxAdapter.GATE_ROLE);
        entity.getPersistentData().putString(CrimsonSandboxAdapter.SLOT_KEY, part.slotId());
        entity.getPersistentData().putString(CrimsonSandboxAdapter.PROFILE_KEY, profile.id());
        if (!level.addFreshEntity(entity)) return ActorOperationResult.unavailable("Could not add Crimson gate entity to level");
        if (!CrimsonProtocol1431.initializeSiegeEntity(level, entity, profile)
                || !CrimsonSandboxAdapter.isOwnedGatePart(entity, mine, part.slotId()) || !profile.matches(entity)) {
            presentation.discardVisualChildren(entity);
            entity.discard();
            return ActorOperationResult.unavailable("Crimson gate initializer postcondition failed");
        }
        entity.getPersistentData().putInt(CrimsonSandboxAdapter.CONTROL_SCHEMA_KEY, CrimsonSandboxAdapter.CONTROL_SCHEMA);
        CrimsonActorRuntime.holdControlled(entity);
        presentation.spawned(level, entity, profile.id());
        mine.gate().activate(part.slotId(), entity.getUUID());
        CrimsonSandboxAdapter.attachGateControl(level, mine, part.slotId(), profile, entity.getUUID());
        return ActorOperationResult.materialized();
    }

    ActorOperationResult removePart(ServerLevel level, TestMineRecord mine, String slotId) {
        SourceGatePartRef part = mine.gate().part(slotId).orElse(null);
        if (part == null) return ActorOperationResult.materialized();
        if (part.profileId().startsWith(NODE_PROFILE_PREFIX)) return removeNode(level, mine, part);
        if (part.entityId() == null) return ActorOperationResult.materialized();
        Entity entity = level.getEntity(part.entityId());
        if (entity != null && !CrimsonSandboxAdapter.isOwnedGatePart(entity, mine, slotId)) {
            return ActorOperationResult.unavailable("Crimson gate identity conflict during cleanup for slot " + slotId);
        }
        if (entity != null) {
            presentation.discardVisualChildren(entity);
            entity.discard();
        }
        mine.gate().remove(slotId);
        PaleMirrorSavedData.get(level.getServer().overworld()).threatCombat()
                .retireActor("crimson", mine.id().value(), "gate", slotId);
        return ActorOperationResult.materialized();
    }

    Optional<String> partAt(TestMineRecord site, BlockPos position) {
        return site.gate().parts().stream()
                .filter(part -> part.status() == SourceGatePartRef.Status.ACTIVE)
                .filter(part -> part.profileId().startsWith(NODE_PROFILE_PREFIX))
                .filter(part -> part.position().equals(position))
                .map(SourceGatePartRef::slotId).findFirst();
    }

    void observedDestroyed(TestMineRecord site, String slotId) {
        site.gate().part(slotId).filter(part -> part.profileId().startsWith(NODE_PROFILE_PREFIX))
                .flatMap(part -> site.mutableCell(part.position())).ifPresent(cell -> cell.markApplied("minecraft:air"));
    }

    private static List<SourceGatePartSpec> nodeLayout(TestMineRecord site, GatePhaseRef phase) {
        List<MutableCell> cells = site.nodeCells();
        if (cells.size() != phase.requiredPartIds().size()) return List.of();
        java.util.ArrayList<SourceGatePartSpec> parts = new java.util.ArrayList<>();
        for (int index = 0; index < cells.size(); index++) {
            parts.add(new SourceGatePartSpec(phase.requiredPartIds().get(index),
                    NODE_PROFILE_PREFIX + ":" + phase.requiredPartIds().get(index), cells.get(index).position()));
        }
        return List.copyOf(parts);
    }

    private static ActorOperationResult ensureNode(ServerLevel level, TestMineRecord mine, SourceGatePartRef part) {
        MutableCell cell = mine.mutableCell(part.position()).orElse(null);
        if (cell == null || !mine.nodeCells().contains(cell)) return ActorOperationResult.unavailable("Crimson gate node is outside PM-owned node cells");
        if (cell.conflicted()) return ActorOperationResult.unavailable("PM gate node cell is conflicted");
        String current = blockId(level, cell.position());
        if (!current.equals(cell.baselineBlock()) && !current.equals(cell.lastAppliedBlock())) {
            cell.conflict();
            return ActorOperationResult.unavailable("PM gate node cell was changed outside Pale Mirror");
        }
        if (!current.equals("minecraft:sea_lantern")) level.setBlock(cell.position(), Blocks.SEA_LANTERN.defaultBlockState(), 3);
        if (!blockId(level, cell.position()).equals("minecraft:sea_lantern")) return ActorOperationResult.unavailable("Crimson gate node postcondition failed");
        cell.markApplied("minecraft:sea_lantern");
        mine.gate().activate(part.slotId(), null);
        return ActorOperationResult.materialized();
    }

    private static ActorOperationResult removeNode(ServerLevel level, TestMineRecord mine, SourceGatePartRef part) {
        MutableCell cell = mine.mutableCell(part.position()).orElse(null);
        if (cell == null) return ActorOperationResult.materialized();
        if (cell.conflicted()) return ActorOperationResult.unavailable("PM gate node cell is conflicted");
        String current = blockId(level, cell.position());
        if (!current.equals(cell.baselineBlock()) && !current.equals(cell.lastAppliedBlock())) {
            cell.conflict();
            return ActorOperationResult.unavailable("PM gate node cell was changed outside Pale Mirror");
        }
        if (!current.equals(cell.baselineBlock())) {
            level.setBlock(cell.position(), BuiltInRegistries.BLOCK.get(ResourceLocation.parse(cell.baselineBlock())).defaultBlockState(), 3);
        }
        if (!blockId(level, cell.position()).equals(cell.baselineBlock())) return ActorOperationResult.unavailable("Crimson gate node cleanup postcondition failed");
        cell.markApplied(cell.baselineBlock());
        mine.gate().remove(part.slotId());
        return ActorOperationResult.materialized();
    }

    private static String blockId(ServerLevel level, BlockPos position) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(position).getBlock()).toString();
    }
}
