package io.farfrontier.palemirror.internal.integration.distanthorizons;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfig;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;

/** Exact-version bridge. This class is loaded reflectively only after NeoForge confirms DH is present. */
public final class DistantHorizonsApiBridge implements DistantHorizonsRuntime.Bridge {
    private static final String SUPPORTED_VERSION = "3.2.0-b";
    private boolean overridden;

    public DistantHorizonsApiBridge() {
        String version = DhApi.getModVersion();
        if (!SUPPORTED_VERSION.equals(version)) {
            throw new IllegalStateException("expected " + SUPPORTED_VERSION + ", found " + version);
        }
    }

    @Override
    public String version() {
        return DhApi.getModVersion();
    }

    @Override
    public double applyCacheOnly(double runtimeRatio) {
        IDhApiConfig config = DhApi.Delayed.configs;
        if (config == null) throw new IllegalStateException("DH delayed config API is not ready");
        // Realtime chunk events still populate DH's server cache. The background
        // importer must remain disabled: even PRE_EXISTING_ONLY consumes a worldgen
        // worker while it scans and imports chunks that Minecraft has just created.
        boolean disabled = config.worldGenerator().enableDistantWorldGeneration().setValue(false);
        boolean generator = config.worldGenerator().distantGeneratorMode()
                .setValue(EDhApiDistantGeneratorMode.PRE_EXISTING_ONLY);
        boolean threads = config.multiThreading().threadCount().setValue(1);
        double acceptedRatio = clamp(config.multiThreading().threadRuntimeRatio(), runtimeRatio);
        boolean ratio = config.multiThreading().threadRuntimeRatio().setValue(acceptedRatio);
        if (!(disabled && generator && threads && ratio)) {
            clearOverrides(config);
            throw new IllegalStateException("DH rejected one or more API config overrides");
        }
        overridden = true;
        return acceptedRatio;
    }

    @Override
    public double setRuntimeRatio(double runtimeRatio) {
        IDhApiConfig config = DhApi.Delayed.configs;
        if (!overridden || config == null) throw new IllegalStateException("DH cache-only override is not active");
        IDhApiConfigValue<Double> value = config.multiThreading().threadRuntimeRatio();
        double acceptedRatio = clamp(value, runtimeRatio);
        if (!value.setValue(acceptedRatio)) throw new IllegalStateException("DH rejected runtime ratio " + acceptedRatio);
        return acceptedRatio;
    }

    @Override
    public void close() {
        IDhApiConfig config = DhApi.Delayed.configs;
        if (overridden && config != null) clearOverrides(config);
        overridden = false;
    }

    private static void clearOverrides(IDhApiConfig config) {
        config.worldGenerator().enableDistantWorldGeneration().clearValue();
        config.worldGenerator().distantGeneratorMode().clearValue();
        config.multiThreading().threadCount().clearValue();
        config.multiThreading().threadRuntimeRatio().clearValue();
    }

    private static double clamp(IDhApiConfigValue<Double> value, double requested) {
        return Math.max(value.getMinValue(), Math.min(value.getMaxValue(), requested));
    }
}
