import jdk.jfr.consumer.*;
import java.nio.file.*;
import java.io.*;
import java.time.Instant;
import java.util.*;

/** Streaming, bounded, game-independent JFR normalization. Domain state is never opened. */
public class SimulationTraceExport {
    static final long MAX_BYTES = 256L << 20;
    static final int MAX_EVENTS = 1_000_000;
    static final Set<String> JDK = Set.of("jdk.ExecutionSample", "jdk.NativeMethodSample", "jdk.GCPhasePause",
            "jdk.ZAllocationStall", "jdk.ThreadPark", "jdk.JavaMonitorEnter", "jdk.ThreadSleep", "jdk.DataLoss");
    static long epoch(Instant t) { return Math.addExact(Math.multiplyExact(t.getEpochSecond(), 1_000_000_000L), t.getNano()); }
    static String quote(String s) {
        var b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\""); case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n"); case '\r' -> b.append("\\r"); case '\t' -> b.append("\\t");
                default -> { if (c < 32) b.append(String.format("\\u%04x", (int)c)); else b.append(c); }
            }
        }
        return b.append('"').toString();
    }
    static String json(Object v) {
        if (v == null) return "null";
        if (v instanceof String s) return quote(s);
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        if (v instanceof Map<?,?> m) return "{" + String.join(",", m.entrySet().stream().map(e -> quote(e.getKey().toString())+":"+json(e.getValue())).toList()) + "}";
        if (v instanceof Collection<?> c) return "[" + String.join(",", c.stream().map(SimulationTraceExport::json).toList()) + "]";
        throw new IllegalArgumentException("unsupported normalized value " + v.getClass());
    }
    static long number(RecordedEvent e, String key, long fallback) { return e.hasField(key) ? e.getLong(key) : fallback; }
    /** Closed JFR v1/v2 chunk headers: OpenJDK ChunkHeader (68 bytes).
     * Event start times can predate recording (e.g. a long ThreadPark).
     * Reject live/partial/unknown files instead of expanding the capture window. */
    static long[] bounds(Path input) throws IOException {
        long first = Long.MAX_VALUE, last = Long.MIN_VALUE;
        try (var file = new RandomAccessFile(input.toFile(), "r")) {
            long offset = 0, length = file.length();
            while (offset < length) {
                if (length - offset < 68) throw new IOException("partial JFR chunk header");
                file.seek(offset);
                if (file.readInt() != 0x464c5200) throw new IOException("invalid JFR chunk magic");
                int major = file.readUnsignedShort(); file.readUnsignedShort();
                if (major != 1 && major != 2) throw new IOException("unsupported JFR chunk version");
                long size = file.readLong();
                if (size < 68 || size > length - offset) throw new IOException("incomplete JFR chunk");
                file.seek(offset + 32); long start = file.readLong(), duration = file.readLong();
                file.seek(offset + 64);
                if (file.readUnsignedByte() != 0 || duration < 0) throw new IOException("JFR chunk is not closed");
                first = Math.min(first, start); last = Math.max(last, Math.addExact(start, duration));
                offset += size;
            }
        }
        if (first == Long.MAX_VALUE) throw new IOException("empty JFR recording");
        return new long[] {first, last};
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: java SimulationTraceExport.java recording.jfr trace.ndjson");
        Path input = Path.of(args[0]), output = Path.of(args[1]);
        if (Files.size(input) > MAX_BYTES) throw new IllegalArgumentException("Recording exceeds256MiB; shorten capture, no silent truncation");
        Long anchor = null, anchorEpoch = null;
        long[] bounds = bounds(input); int scanned = 0;
        try (var r = new RecordingFile(input)) {
            while (r.hasMoreEvents()) {
                var e = r.readEvent(); String type = e.getEventType().getName();
                if (++scanned > 2_000_000) throw new IllegalArgumentException("recording scan capacity exceeded");
                if (!type.startsWith("pale_mirror.") || anchor != null) continue;
                long mono = number(e,"startedNanos",number(e,"monotonicNanos",0));
                if (mono > 0) { anchor = mono; anchorEpoch = epoch(e.getStartTime()); }
            }
        }
        if (anchor == null) throw new IllegalArgumentException("No simulation monotonic anchor; enable causal JFR events");
        Files.createDirectories(output.toAbsolutePath().getParent()); int count = 0;
        try (var writer = Files.newBufferedWriter(output); var r = new RecordingFile(input)) {
            writer.write(json(Map.of("event","trace.metadata","schema",2,"anchorMonotonicNanos",anchor,"anchorEpochNanos",anchorEpoch,"inputBytes",Files.size(input),"recordingFirstNs",bounds[0]-anchorEpoch,"recordingLastNs",bounds[1]-anchorEpoch,"boundsSource","closed_jfr_chunks"))); writer.newLine();
            while (r.hasMoreEvents()) {
                var e = r.readEvent(); String type = e.getEventType().getName();
                if (!type.startsWith("pale_mirror.") && !JDK.contains(type)) continue;
                if (++count > MAX_EVENTS) throw new IllegalArgumentException("Event capacity exceeded; shorten capture, incomplete output rejected");
                var fields = new LinkedHashMap<String,Object>();
                for (var f : e.getEventType().getFields()) {
                    Object v = e.getValue(f.getName());
                    if (v instanceof String || v instanceof Number || v instanceof Boolean) fields.put(f.getName(),v);
                }
                long start = type.startsWith("pale_mirror.") ? number(e,"startedNanos",number(e,"monotonicNanos",0)) - anchor : epoch(e.getStartTime()) - anchorEpoch;
                long end = type.startsWith("pale_mirror.") && e.hasField("endedNanos") ? e.getLong("endedNanos") - anchor : start + e.getDuration().toNanos();
                var row = new LinkedHashMap<String,Object>(); row.put("event",type); row.put("start",start); row.put("end",end); row.put("fields",fields);
                var thread = type.equals("jdk.ExecutionSample") || type.equals("jdk.NativeMethodSample") ? e.getThread("sampledThread") : e.getThread();
                row.put("tid",thread == null ? 0 : thread.getJavaThreadId()); row.put("thread",thread == null ? "" : thread.getJavaName());
                if (e.getStackTrace()!=null) row.put("stack",e.getStackTrace().getFrames().stream().limit(32).map(f -> f.getMethod() == null ? "[unresolved " + f.getType() + " frame]"
                        : f.getMethod().getType().getName()+"."+f.getMethod().getName()+":"+f.getLineNumber()).toList());
                writer.write(json(row)); writer.newLine();
            }
        }
    }
}
