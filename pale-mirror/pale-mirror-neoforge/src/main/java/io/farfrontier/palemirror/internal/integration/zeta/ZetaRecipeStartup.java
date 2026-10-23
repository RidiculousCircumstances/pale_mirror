package io.farfrontier.palemirror.internal.integration.zeta;

import io.farfrontier.palemirror.PaleMirrorMod;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

/** Optional pack integration: finish Zeta's pending recipe initialization after
 * tags/recipes exist, before gameplay. No world tick or tick event is dispatched.
 * Zeta keeps its own mutex, readiness flags, recipe crawl/digest and reload lifecycle. */
public final class ZetaRecipeStartup {
    private static final String VERSION = "1.1-40";
    private static final String CONTEXT = "org.violetmoon.zeta.event.play.ZServerTick$Start";
    private static final String OWNER = "org.violetmoon.zeta.util.handler.RecipeCrawlHandler";
    private ZetaRecipeStartup() { }

    public static void finishPending(MinecraftServer server) {
        var installed = ModList.get().getModContainerById("zeta");
        if (installed.isEmpty()) return;
        var version = installed.orElseThrow().getModInfo().getVersion().toString();
        if (!VERSION.equals(version)) {
            PaleMirrorMod.LOGGER.warn("Zeta recipe startup integration supports {}; installed {} retains native initialization", VERSION, version);
            return;
        }
        try {
            var loader = ZetaRecipeStartup.class.getClassLoader();
            var context = Class.forName(CONTEXT, false, loader);
            // The pinned public handler consumes only getServer(). Call that one
            // owner directly; never publish a synthetic server tick to either bus.
            var access = Proxy.newProxyInstance(loader, new Class<?>[]{context}, (proxy, method, args) -> switch (method.getName()) {
                case "getServer" -> server;
                case "toString" -> "Zeta recipe startup context";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new IllegalStateException("unsupported Zeta recipe context method: " + method);
            });
            long started = System.nanoTime();
            Class.forName(OWNER, true, loader).getMethod("onTick", context).invoke(null, access);
            PaleMirrorMod.LOGGER.info("Zeta pending recipe initialization completed during server startup in {} ms",
                    (System.nanoTime() - started) / 1_000_000L);
        } catch (InvocationTargetException failure) {
            throw new IllegalStateException("Zeta recipe initialization failed during server startup", failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("pinned Zeta recipe initialization surface changed", failure);
        }
    }
}
