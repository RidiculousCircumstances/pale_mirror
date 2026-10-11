package io.farfrontier.palemirror.internal.frontier.v3;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real argument syntax: namespaced IDs must not be truncated at their colon. */
class FrontierV3StorageCommandsTest {
    @Test void storageArgumentsAcceptCompleteNamespacedIdentityAndQuotedItemLists() throws Exception {
        var root = FrontierV3StorageCommands.branch().build();
        var home = (ArgumentCommandNode<?, ?>) root.getChild("create").getChild("home");
        var container = (ArgumentCommandNode<?, ?>) home.getChild("container");
        var items = (ArgumentCommandNode<?, ?>) container.getChild("chest").getChild("station_support").getChild("items");
        var homeReader = new StringReader("settlement:1");
        assertEquals("settlement:1", home.getType().parse(homeReader).toString());
        assertFalse(homeReader.canRead());
        var containerReader = new StringReader("container:ground-recovery");
        assertEquals("container:ground-recovery", container.getType().parse(containerReader).toString());
        assertFalse(containerReader.canRead());
        var itemsReader = new StringReader("\"minecraft:wheat,minecraft:bread\"");
        assertEquals("minecraft:wheat,minecraft:bread", items.getType().parse(itemsReader));
        assertFalse(itemsReader.canRead());
        var configured = (ArgumentCommandNode<?, ?>) root.getChild("configure").getChild("container");
        assertEquals("container:ground-recovery", configured.getType().parse(new StringReader("container:ground-recovery")).toString());
        assertNotNull(configured.getChild("items").getChild("priority").getCommand());
    }
}
