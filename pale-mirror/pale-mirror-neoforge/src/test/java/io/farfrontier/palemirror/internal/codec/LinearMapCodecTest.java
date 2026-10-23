package io.farfrontier.palemirror.internal.codec;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LinearMapCodecTest {
    @Test void preservesNativeCompoundFormatAndRejectsMalformedValues() {
        var original = Codec.unboundedMap(Codec.STRING, Codec.INT);
        var linear = LinearMapCodec.of(Codec.STRING, Codec.INT);
        var values = Map.of("block-a", 4, "block-b", 7);
        var nbt = original.encodeStart(NbtOps.INSTANCE, values).getOrThrow();
        assertEquals(values, linear.parse(NbtOps.INSTANCE, nbt).getOrThrow());
        assertEquals(nbt, linear.encodeStart(NbtOps.INSTANCE, values).getOrThrow());
        assertTrue(linear.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"block-a\":\"bad\"}")).error().isPresent());
    }
    @Test void differentEncodedKeysMayNotCollapseIntoOneSemanticAddress() {
        var keys = Codec.STRING.xmap(Integer::parseInt, Object::toString);
        var codec = LinearMapCodec.of(keys, Codec.INT);
        assertTrue(codec.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"1\":4,\"01\":7}")).error().isPresent());
    }
}
