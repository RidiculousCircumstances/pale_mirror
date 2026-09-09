package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3PilotChunkMapAccessor;
import io.farfrontier.palemirror.internal.frontier.v3.mixin.FrontierV3PilotDistanceManagerAccessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resolves each pilot field accessor against the mapped Minecraft class loaded
 * by the test runtime. This guards the JVM descriptor that Mixin must match,
 * rather than merely compiling an interface with a widened return type.
 */
class FrontierV3PilotAccessorAbiTest {
    @Test
    void everyPilotFieldAccessorMatchesTheCurrentMappedTargetDescriptor() {
        assertAccessorDescriptors(ChunkMap.class, FrontierV3PilotChunkMapAccessor.class);
        assertAccessorDescriptors(DistanceManager.class, FrontierV3PilotDistanceManagerAccessor.class);
        Method getter = method(ChunkMap.class, "getDistanceManager");
        assertTrue(Modifier.isPublic(getter.getModifiers()));
        assertEquals(0, getter.getParameterCount());
        assertEquals(DistanceManager.class, getter.getReturnType());
    }

    @Test
    void widenedAccessorDescriptorIsRejectedAgainstTheMappedTarget() {
        assertThrows(AssertionError.class,
                () -> assertFieldDescriptor(ChunkMap.class, "updatingChunkMap", Long2ObjectMap.class));
        assertEquals(Long2ObjectLinkedOpenHashMap.class,
                field(ChunkMap.class, "updatingChunkMap").getType());
    }

    private static void assertAccessorDescriptors(Class<?> target, Class<?> accessorType) {
        List<Method> methods = List.of(accessorType.getDeclaredMethods()).stream()
                .filter(method -> method.isAnnotationPresent(Accessor.class))
                .toList();
        assertEquals(accessorType.getDeclaredMethods().length, methods.size(), accessorType.getName());
        for (Method accessor : methods) {
            assertEquals(0, accessor.getParameterCount(), accessor.getName());
            assertFieldDescriptor(target, accessor.getAnnotation(Accessor.class).value(), accessor.getReturnType());
        }
    }

    private static void assertFieldDescriptor(Class<?> target, String name, Class<?> expectedType) {
        assertEquals(expectedType, field(target, name).getType(), target.getName() + "." + name);
    }

    private static Field field(Class<?> target, String name) {
        try {
            return target.getDeclaredField(name);
        } catch (NoSuchFieldException failure) {
            throw new AssertionError(target.getName() + "." + name, failure);
        }
    }

    private static Method method(Class<?> target, String name) {
        try {
            return target.getDeclaredMethod(name);
        } catch (NoSuchMethodException failure) {
            throw new AssertionError(target.getName() + "." + name, failure);
        }
    }
}
