package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.ProductionStationClock;
import java.io.*;
import java.util.Optional;

/** An admitted attendance interval is independent of whether the machine currently has burning fuel. */
final class StationAttendanceCodec {
    static void write(DataOutputStream out, Optional<ProductionStationClock.Attendance> attendance) throws IOException {
        out.writeBoolean(attendance.isPresent());
        if (attendance.isPresent()) { out.writeInt(attendance.orElseThrow().speedPermille()); out.writeLong(attendance.orElseThrow().permittedUntilTick()); }
    }
    static Optional<ProductionStationClock.Attendance> read(DataInputStream in) throws IOException {
        return in.readBoolean() ? Optional.of(new ProductionStationClock.Attendance(in.readInt(), in.readLong())) : Optional.empty();
    }
    private StationAttendanceCodec() { }
}
