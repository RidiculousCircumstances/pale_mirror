package io.farfrontier.palemirror.visuals.foundry;

import io.farfrontier.palemirror.api.FoundryAuditExport;
import io.farfrontier.palemirror.api.FoundryAuditReport;
import io.farfrontier.palemirror.api.FoundryFinding;
import io.farfrontier.palemirror.api.FoundryMapSample;
import io.farfrontier.palemirror.api.FoundryMetric;
import io.farfrontier.palemirror.api.FoundrySeverity;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

/** Writes bounded self-contained Foundry artifacts only on an explicit operator request. */
public final class FoundryReportExporter {
    public FoundryAuditExport export(ServerLevel level, FoundryAuditReport report) throws IOException {
        String name = safe(report.regionId()) + "-" + report.phase().name().toLowerCase(java.util.Locale.ROOT)
                + "-tick-" + level.getGameTime();
        Path root = level.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("pale-mirror").resolve("foundry").resolve(name);
        Files.createDirectories(root);
        Files.writeString(root.resolve("report.json"), json(report), StandardCharsets.UTF_8);
        Files.writeString(root.resolve("report.html"), html(report), StandardCharsets.UTF_8);
        writeMaps(root, report);
        return new FoundryAuditExport(report, root.toAbsolutePath().normalize().toString());
    }

    private static void writeMaps(Path root, FoundryAuditReport report) throws IOException {
        if (report.mapSamples().isEmpty()) return;
        Bounds bounds = bounds(report);
        write(root.resolve("target-height.png"), image(report, bounds, Layer.TARGET));
        write(root.resolve("observed-height.png"), image(report, bounds, Layer.OBSERVED));
        write(root.resolve("cut-fill-delta.png"), image(report, bounds, Layer.DELTA));
        write(root.resolve("expected-top.png"), image(report, bounds, Layer.TOP));
        write(root.resolve("ownership.png"), image(report, bounds, Layer.OWNER));
    }

    private static BufferedImage image(FoundryAuditReport report, Bounds bounds, Layer layer) {
        BufferedImage image = new BufferedImage(bounds.width(), bounds.height(), BufferedImage.TYPE_INT_ARGB);
        int empty = new Color(22, 24, 28, 255).getRGB();
        for (int z = 0; z < bounds.height(); z++) for (int x = 0; x < bounds.width(); x++) image.setRGB(x, z, empty);
        int minimum = report.mapSamples().stream().mapToInt(FoundryMapSample::targetY).min().orElse(0);
        int maximum = report.mapSamples().stream().mapToInt(FoundryMapSample::targetY).max().orElse(minimum + 1);
        for (FoundryMapSample sample : report.mapSamples()) {
            int color = switch (layer) {
                case TARGET -> height(sample.targetY(), minimum, maximum);
                case OBSERVED -> sample.observedY() == null ? new Color(90, 32, 110).getRGB()
                        : height(sample.observedY(), minimum, maximum);
                case DELTA -> sample.observedY() == null ? new Color(90, 32, 110).getRGB()
                        : delta(sample.observedY() - sample.targetY());
                case TOP -> sample.expectedTopY() == null ? new Color(35, 35, 35).getRGB()
                        : height(sample.expectedTopY(), minimum, Math.max(maximum + 1,
                        report.mapSamples().stream().filter(value -> value.expectedTopY() != null)
                                .mapToInt(FoundryMapSample::expectedTopY).max().orElse(maximum + 1)));
                case OWNER -> owner(sample.ownerId());
            };
            image.setRGB(sample.x() - bounds.minX(), sample.z() - bounds.minZ(), color);
        }
        return image;
    }

    private static int height(int value, int minimum, int maximum) {
        double normalized = maximum == minimum ? 0.5D : (value - minimum) / (double) (maximum - minimum);
        normalized = Math.max(0D, Math.min(1D, normalized));
        return Color.HSBtoRGB((float) (0.62D - normalized * 0.52D), 0.72F, 0.92F);
    }

    private static int delta(int value) {
        int magnitude = Math.min(255, 55 + Math.abs(value) * 35);
        if (value < 0) return new Color(45, 100, magnitude).getRGB();
        if (value > 0) return new Color(magnitude, 68, 45).getRGB();
        return new Color(64, 180, 94).getRGB();
    }

    private static int owner(String id) {
        int hash = id.hashCode();
        return Color.HSBtoRGB(Math.floorMod(hash, 360) / 360F, 0.58F, 0.90F);
    }

    private static void write(Path path, BufferedImage image) throws IOException {
        if (!ImageIO.write(image, "png", path.toFile())) throw new IOException("PNG writer unavailable");
    }

    private static Bounds bounds(FoundryAuditReport report) {
        int minX = report.mapSamples().stream().mapToInt(FoundryMapSample::x).min().orElse(0);
        int maxX = report.mapSamples().stream().mapToInt(FoundryMapSample::x).max().orElse(minX);
        int minZ = report.mapSamples().stream().mapToInt(FoundryMapSample::z).min().orElse(0);
        int maxZ = report.mapSamples().stream().mapToInt(FoundryMapSample::z).max().orElse(minZ);
        return new Bounds(minX, minZ, maxX - minX + 1, maxZ - minZ + 1);
    }

    private static String json(FoundryAuditReport report) {
        StringBuilder out = new StringBuilder(16_384);
        out.append("{\n  \"formatVersion\": ").append(report.formatVersion())
                .append(",\n  \"regionId\": \"").append(jsonString(report.regionId()))
                .append("\",\n  \"catalogHash\": \"").append(jsonString(report.catalogHash()))
                .append("\",\n  \"phase\": \"").append(report.phase()).append("\",\n  \"passed\": ")
                .append(report.passed()).append(",\n  \"metrics\": [\n");
        for (int i = 0; i < report.metrics().size(); i++) {
            FoundryMetric metric = report.metrics().get(i);
            out.append("    {\"id\":\"").append(jsonString(metric.id())).append("\",\"value\":")
                    .append(metric.value()).append(",\"unit\":\"").append(jsonString(metric.unit())).append("\"}")
                    .append(i + 1 == report.metrics().size() ? "\n" : ",\n");
        }
        out.append("  ],\n  \"findings\": [\n");
        for (int i = 0; i < report.findings().size(); i++) {
            FoundryFinding finding = report.findings().get(i);
            out.append("    {\"ruleId\":\"").append(jsonString(finding.ruleId()))
                    .append("\",\"severity\":\"").append(finding.severity())
                    .append("\",\"targetKind\":\"").append(jsonString(finding.targetKind()))
                    .append("\",\"targetId\":\"").append(jsonString(finding.targetId()))
                    .append("\",\"x\":").append(finding.position().x()).append(",\"y\":")
                    .append(finding.position().y()).append(",\"z\":").append(finding.position().z())
                    .append(",\"message\":\"").append(jsonString(finding.message()))
                    .append("\",\"remediation\":\"").append(jsonString(finding.remediation())).append("\"}")
                    .append(i + 1 == report.findings().size() ? "\n" : ",\n");
        }
        out.append("  ],\n  \"mapSamples\": [\n");
        for (int i = 0; i < report.mapSamples().size(); i++) {
            FoundryMapSample sample = report.mapSamples().get(i);
            out.append("    {\"x\":").append(sample.x()).append(",\"z\":").append(sample.z())
                    .append(",\"targetY\":").append(sample.targetY()).append(",\"observedY\":")
                    .append(sample.observedY() == null ? "null" : sample.observedY())
                    .append(",\"expectedTopY\":").append(sample.expectedTopY() == null ? "null" : sample.expectedTopY())
                    .append(",\"ownerId\":\"").append(jsonString(sample.ownerId()))
                    .append("\",\"loaded\":").append(sample.loaded()).append("}")
                    .append(i + 1 == report.mapSamples().size() ? "\n" : ",\n");
        }
        return out.append("  ]\n}\n").toString();
    }

    private static String html(FoundryAuditReport report) {
        Map<FoundrySeverity, Long> counts = new LinkedHashMap<>();
        for (FoundrySeverity severity : FoundrySeverity.values()) counts.put(severity, report.count(severity));
        StringBuilder out = new StringBuilder(16_384);
        out.append("<!doctype html><meta charset=\"utf-8\"><title>PM Foundry ")
                .append(htmlString(report.regionId())).append("</title><style>")
                .append("body{font:14px system-ui;background:#15171a;color:#e8e8e8;margin:2rem}h1,h2{color:#fff}")
                .append(".maps{display:flex;flex-wrap:wrap;gap:1rem}.map{background:#222;padding:1rem}")
                .append("img{image-rendering:pixelated;min-width:260px;border:1px solid #555}")
                .append("table{border-collapse:collapse;width:100%}td,th{border:1px solid #444;padding:.45rem;text-align:left}")
                .append(".BLOCKER,.ERROR{color:#ff766f}.WARNING{color:#ffd166}.INFO{color:#8ecae6}</style>")
                .append("<h1>Pale Mirror Foundry</h1><p>").append(htmlString(report.summary()))
                .append("</p><p>Catalog <code>").append(htmlString(report.catalogHash())).append("</code></p>")
                .append("<p>");
        counts.forEach((severity, count) -> out.append("<span class=\"").append(severity).append("\">")
                .append(severity).append(": ").append(count).append("</span> &nbsp; "));
        out.append("</p><h2>Maps</h2><div class=\"maps\">");
        for (String file : new String[]{"target-height.png", "observed-height.png", "cut-fill-delta.png",
                "expected-top.png", "ownership.png"}) {
            out.append("<div class=\"map\"><div>").append(file).append("</div><img src=\"")
                    .append(file).append("\"></div>");
        }
        out.append("</div><h2>Metrics</h2><table><tr><th>ID</th><th>Value</th><th>Unit</th></tr>");
        report.metrics().forEach(metric -> out.append("<tr><td>").append(htmlString(metric.id()))
                .append("</td><td>").append(metric.value()).append("</td><td>")
                .append(htmlString(metric.unit())).append("</td></tr>"));
        out.append("</table><h2>Findings</h2><table><tr><th>Severity</th><th>Rule</th><th>Target</th>")
                .append("<th>Position</th><th>Finding</th><th>Remediation</th></tr>");
        report.findings().stream().sorted(Comparator.comparing(FoundryFinding::severity).reversed())
                .forEach(finding -> out.append("<tr><td class=\"").append(finding.severity()).append("\">")
                        .append(finding.severity()).append("</td><td>").append(htmlString(finding.ruleId()))
                        .append("</td><td>").append(htmlString(finding.targetKind())).append(":")
                        .append(htmlString(finding.targetId())).append("</td><td>")
                        .append(finding.position().x()).append(", ").append(finding.position().y()).append(", ")
                        .append(finding.position().z()).append("</td><td>").append(htmlString(finding.message()))
                        .append("</td><td>").append(htmlString(finding.remediation())).append("</td></tr>"));
        return out.append("</table>").toString();
    }

    private static String safe(String value) { return value.replaceAll("[^a-zA-Z0-9._-]+", "_"); }
    private static String jsonString(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r"); }
    private static String htmlString(String value) { return value.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;"); }

    private enum Layer { TARGET, OBSERVED, DELTA, TOP, OWNER }
    private record Bounds(int minX, int minZ, int width, int height) { }
}
