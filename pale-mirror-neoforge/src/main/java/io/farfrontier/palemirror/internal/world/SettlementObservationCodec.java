package io.farfrontier.palemirror.internal.world;

import java.util.LinkedHashMap;
import java.util.Map;

import io.farfrontier.palemirror.domain.DamageAttribution;
import io.farfrontier.palemirror.domain.EvidenceReliability;
import io.farfrontier.palemirror.domain.SettlementCohort;
import io.farfrontier.palemirror.domain.SettlementEvidenceType;
import io.farfrontier.palemirror.domain.WorldObjectId;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import io.farfrontier.palemirror.domain.StructuralIntegrity;

/** Schema-v20 codec for bounded typed settlement evidence and representative membership. */
final class SettlementObservationCodec {
    private SettlementObservationCodec() { }

    static void write(CompoundTag tag, Map<WorldObjectId, SettlementObservationRecord> records) {
        ListTag values = new ListTag();
        records.values().forEach(record -> {
            CompoundTag value = new CompoundTag();
            value.putString("id", record.id().value());
            value.putString("dimension", record.dimensionId());
            value.putLong("anchor", record.anchor().asLong());
            value.putLong("min", record.minBounds().asLong());
            value.putLong("max", record.maxBounds().asLong());
            value.putString("provenance", record.provenance());
            value.putInt("population", record.observedPopulation());
            value.putInt("guards", record.observedGuards());
            value.putLong("lastObserved", record.lastObservedGameTime());
            value.putLong("loadedDuration", record.loadedDurationTicks());
            value.putBoolean("fullBoundsLoaded", record.lastFullBoundsLoaded());
            value.putBoolean("initialMembershipEstablished", record.initialMembershipEstablished());
            value.putString("reliability", record.reliability().name());
            value.putString("lastEvidenceId", record.lastEvidenceId());
            value.putString("lastEvidenceType", record.lastEvidenceType().name());
            value.putString("lastDamageAttribution", record.lastDamageAttribution().name());
            value.putInt("residentDeaths", record.observedResidentDeaths());
            value.putInt("guardDeaths", record.observedGuardDeaths());
            value.putString("inferredIntegrity", record.inferredIntegrity().name());
            ListTag structure = new ListTag();
            record.structureSamples().forEach(sample -> {
                CompoundTag cell = new CompoundTag();
                cell.putLong("pos", sample.position().asLong());
                cell.putString("baseline", sample.baselineBlock());
                structure.add(cell);
            });
            value.put("structureSamples", structure);
            ListTag representatives = new ListTag();
            record.representatives().values().forEach(representative -> {
                CompoundTag member = new CompoundTag();
                member.putString("nativeId", representative.nativeId());
                member.putString("cohort", representative.cohort().name());
                member.putLong("firstSeen", representative.firstSeenAt());
                member.putLong("lastSeen", representative.lastSeenAt());
                member.putLong("consecutiveSeen", representative.consecutiveSeenTicks());
                member.putString("status", representative.status().name());
                representatives.add(member);
            });
            value.put("representatives", representatives);
            values.add(value);
        });
        tag.put("settlementObservations", values);
    }

    static Map<WorldObjectId, SettlementObservationRecord> read(CompoundTag tag) {
        Map<WorldObjectId, SettlementObservationRecord> result = new LinkedHashMap<>();
        for (Tag element : tag.getList("settlementObservations", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) element;
            WorldObjectId id = new WorldObjectId(value.getString("id"));
            Map<String, SettlementRepresentativeRecord> representatives = new LinkedHashMap<>();
            for (Tag memberTag : value.getList("representatives", Tag.TAG_COMPOUND)) {
                CompoundTag member = (CompoundTag) memberTag;
                SettlementRepresentativeRecord representative = new SettlementRepresentativeRecord(member.getString("nativeId"),
                        SettlementCohort.valueOf(member.getString("cohort")), member.getLong("firstSeen"),
                        member.getLong("lastSeen"), member.getLong("consecutiveSeen"),
                        RepresentativeMembershipStatus.valueOf(member.getString("status")));
                representatives.put(representative.nativeId(), representative);
            }
            SettlementObservationRecord record = new SettlementObservationRecord(id, value.getString("dimension"),
                    BlockPos.of(value.getLong("anchor")), BlockPos.of(value.getLong("min")),
                    BlockPos.of(value.getLong("max")), value.getInt("population"), value.getInt("guards"),
                    value.getLong("lastObserved"), value.getString("provenance"), value.getLong("loadedDuration"),
                    value.getBoolean("fullBoundsLoaded"), value.getBoolean("initialMembershipEstablished"),
                    EvidenceReliability.valueOf(value.getString("reliability")), value.getString("lastEvidenceId"),
                    SettlementEvidenceType.valueOf(value.getString("lastEvidenceType")),
                    DamageAttribution.valueOf(value.getString("lastDamageAttribution")), value.getInt("residentDeaths"),
                    value.getInt("guardDeaths"), representatives);
            java.util.List<SettlementStructureSampleCell> structure = new java.util.ArrayList<>();
            for (Tag cellTag : value.getList("structureSamples", Tag.TAG_COMPOUND)) {
                CompoundTag cell = (CompoundTag) cellTag;
                structure.add(new SettlementStructureSampleCell(BlockPos.of(cell.getLong("pos")), cell.getString("baseline")));
            }
            record.restoreStructure(structure, value.contains("inferredIntegrity", Tag.TAG_STRING)
                    ? StructuralIntegrity.valueOf(value.getString("inferredIntegrity")) : StructuralIntegrity.INTACT);
            result.put(id, record);
        }
        return result;
    }
}
