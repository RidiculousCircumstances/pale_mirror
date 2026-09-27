package io.farfrontier.palemirror.frontier.v3.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Physical inventory evidence, separate from economic custody. Ordinary fungible stock in a
 * bulk container is identified by kind and total quantity, not by a transient Minecraft slot.
 * Exact items and machine ports retain their slot identity. This image never creates a lot,
 * transfers ownership, or accepts an unclassified physical change.
 */
public final class MaterialContainerImage {
    private MaterialContainerImage() { }

    public enum Layout { BULK, FIXED_PORTS }

    public record Slot(int index, String itemId, String itemKind, int count) {
        public Slot {
            if (index < 0 || itemId == null || itemKind == null || count < 0 || count > 64
                    || count == 0 && (!itemId.isEmpty() || !itemKind.isEmpty())
                    || count > 0 && itemKind.isEmpty()) throw new IllegalArgumentException("invalid material slot");
        }

        public static Slot empty(int index) { return new Slot(index, "", "", 0); }
        public static Slot fungible(int index, String kind, int count) { return new Slot(index, "", kind, count); }
        public static Slot exact(int index, String id, String kind, int count) {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("exact item identity is required");
            return new Slot(index, id, kind, count);
        }

        public boolean empty() { return count == 0; }
        public boolean fungible() { return count > 0 && itemId.isEmpty(); }
    }

    /** Hashes one complete bounded image; slot-only permutations of BULK stock are equal. */
    public static String fingerprint(String scopeKind, String containerId, Layout layout, List<Slot> slots) {
        Objects.requireNonNull(scopeKind, "scope kind"); Objects.requireNonNull(containerId, "container id");
        Objects.requireNonNull(layout, "material layout"); Objects.requireNonNull(slots, "material slots");
        if (slots.isEmpty() || slots.size() > 54) throw new IllegalArgumentException("material image has invalid capacity");
        StringBuilder value = new StringBuilder(scopeKind).append('|').append(containerId).append('|');
        if (layout == Layout.FIXED_PORTS) {
            for (int slot = 0; slot < slots.size(); slot++) {
                Slot item = checked(slots, slot);
                value.append(slot).append(':');
                appendItem(value, item);
                value.append('|');
            }
        } else {
            Map<String, Long> totals = new TreeMap<>();
            value.append("bulk:").append(slots.size()).append('|');
            for (int slot = 0; slot < slots.size(); slot++) {
                Slot item = checked(slots, slot);
                if (item.fungible()) totals.merge(item.itemKind(), (long) item.count(), Math::addExact);
                else if (!item.empty()) {
                    value.append("exact@").append(slot).append(':');
                    appendItem(value, item);
                    value.append('|');
                }
            }
            for (var entry : totals.entrySet()) value.append("stock:").append(entry.getKey()).append(':').append(entry.getValue()).append('|');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8));
            return "sha256:" + java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static Slot checked(List<Slot> slots, int index) {
        Slot item = Objects.requireNonNull(slots.get(index), "material slot");
        if (item.index() != index) throw new IllegalArgumentException("material image slots are incomplete or unordered");
        return item;
    }

    private static void appendItem(StringBuilder value, Slot item) {
        if (item.empty()) value.append("empty");
        else if (item.fungible()) value.append("fungible:").append(item.itemKind()).append(':').append(item.count());
        else value.append("exact:").append(item.itemId()).append(':').append(item.itemKind()).append(':').append(item.count());
    }
}
