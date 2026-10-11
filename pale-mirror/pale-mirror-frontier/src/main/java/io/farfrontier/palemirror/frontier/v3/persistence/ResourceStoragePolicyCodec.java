package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.io.*;
import java.util.*;

/** Stable current-format warehouse policy, including frozen category membership. */
final class ResourceStoragePolicyCodec {
    private ResourceStoragePolicyCodec() { }
    static void write(DataOutputStream out, ResourceStoragePolicy value) throws IOException {
        ShipmentStateCodec.endpoint(out, value.endpoint()); out.writeInt(value.priority()); out.writeByte(value.role().wireTag());
        var filter = value.filter(); out.writeBoolean(filter.allItems()); strings(out, filter.items());
        out.writeInt(filter.categories().size());
        for (var entry : filter.categories().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            out.writeUTF(entry.getKey()); strings(out, entry.getValue());
        }
    }
    static ResourceStoragePolicy read(DataInputStream in) throws IOException {
        var endpoint = ShipmentStateCodec.endpoint(in); int priority = in.readInt();
        var role = ResourceStorageRole.decode(in.readUnsignedByte());
        boolean all = in.readBoolean(); var items = strings(in); var categories = new LinkedHashMap<String, Set<String>>();
        int remaining = 4096 - items.size();
        for (int i = 0, count = count(in, 256); i < count; i++) {
            String category = in.readUTF(); var members = strings(in, remaining); remaining -= members.size();
            if (categories.putIfAbsent(category, members) != null) throw new IllegalArgumentException("duplicate storage category");
        }
        return new ResourceStoragePolicy(endpoint, new ResourceStorageFilter(all, items, categories), priority, role);
    }
    private static void strings(DataOutputStream out, Set<String> values) throws IOException {
        out.writeInt(values.size()); for (String value : values.stream().sorted().toList()) out.writeUTF(value);
    }
    private static Set<String> strings(DataInputStream in) throws IOException {
        return strings(in, 4096);
    }
    private static Set<String> strings(DataInputStream in, int maximum) throws IOException {
        var result = new HashSet<String>();
        for (int i = 0, count = count(in, maximum); i < count; i++)
            if (!result.add(in.readUTF())) throw new IllegalArgumentException("duplicate storage item");
        return Set.copyOf(result);
    }
    private static int count(DataInputStream in, int maximum) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > maximum) throw new IllegalArgumentException("invalid storage policy count");
        return count;
    }
}
