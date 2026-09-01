package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.TerrainColumn;
import io.farfrontier.palemirror.frontier.v3.model.TerrainSurfacePlan;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stable bootstrap-header codec for the immutable terrain provider survey. */
final class TerrainSurfacePlanCodec {
    private TerrainSurfacePlanCodec() { }

    static void write(DataOutputStream output, TerrainSurfacePlan plan) throws IOException {
        output.writeInt(plan.baselineSupportY());
        output.writeShort(plan.surveyedSupportY().size());
        for (Map.Entry<TerrainColumn, Integer> entry : plan.surveyedSupportY().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparingInt((TerrainColumn column) -> column.x())
                        .thenComparingInt(TerrainColumn::z))).toList()) {
            output.writeInt(entry.getKey().x()); output.writeInt(entry.getKey().z()); output.writeInt(entry.getValue());
        }
    }

    static TerrainSurfacePlan read(DataInputStream input) throws IOException {
        int baseline = input.readInt(); int count = input.readUnsignedShort();
        if (count > TerrainSurfacePlan.MAX_SURVEYED_COLUMNS) throw new IllegalArgumentException("terrain survey column limit exceeded");
        Map<TerrainColumn, Integer> columns = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            TerrainColumn column = new TerrainColumn(input.readInt(), input.readInt());
            if (columns.put(column, input.readInt()) != null) throw new IllegalArgumentException("duplicate terrain survey column");
        }
        return new TerrainSurfacePlan(baseline, columns);
    }
}
