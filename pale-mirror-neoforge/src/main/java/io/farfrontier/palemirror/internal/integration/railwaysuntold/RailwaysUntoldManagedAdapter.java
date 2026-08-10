package io.farfrontier.palemirror.internal.integration.railwaysuntold;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.Set;

import io.farfrontier.palemirror.api.AdapterHealth;
import io.farfrontier.palemirror.api.Capability;
import io.farfrontier.palemirror.internal.adapter.FreightServiceObservation;
import io.farfrontier.palemirror.internal.adapter.FreightServiceRequest;
import io.farfrontier.palemirror.internal.adapter.FreightServiceStatus;
import io.farfrontier.palemirror.internal.adapter.RailConnectionObservation;
import io.farfrontier.palemirror.internal.adapter.RailConnectionRequest;
import io.farfrontier.palemirror.internal.adapter.RailConnectionStatus;
import io.farfrontier.palemirror.internal.adapter.RailInfrastructureAdapter;
import io.farfrontier.palemirror.internal.adapter.RailPlacementAuthority;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

/**
 * Exact-version reflection bridge to the private PM Railway Untold fork.
 * Provider classes never enter PM's generic adapter or persistence contracts.
 */
public final class RailwaysUntoldManagedAdapter implements RailInfrastructureAdapter {
    private static final String MOD_ID = "railwaysuntold";
    private static final String PINNED_VERSION = "1.2.1-pm.1";
    private static final String ENTRYPOINT = "com.vodmordia.railwaysuntold.api.managed.ManagedRailways";
    private static final String API = "com.vodmordia.railwaysuntold.api.managed.ManagedRailwayApi";
    private static final String GUARD = "com.vodmordia.railwaysuntold.api.managed.ManagedPlacementGuard";
    private static final String GUARD_DECISION = GUARD + "$Decision";

    @Override public String id() { return "pale_mirror:managed_railway"; }

    @Override
    public AdapterHealth health() {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return new AdapterHealth(AdapterHealth.Status.ABSENT, "PM Railway Untold is not installed", Set.of());
        }
        String version = ModList.get().getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString()).orElse("unknown");
        if (!PINNED_VERSION.equals(version)) {
            return blocked("Railway Untold " + version + " is installed; PM requires private fork " + PINNED_VERSION);
        }
        try {
            Object api = api();
            if (!Boolean.TRUE.equals(api.getClass().getMethod("isManagedMode").invoke(api))) {
                return blocked("Railway Untold managed mode is disabled in its server config");
            }
            return new AdapterHealth(AdapterHealth.Status.AVAILABLE,
                    "Private Railway Untold managed API is available",
                    Set.of(Capability.MANAGED_RAIL_CONNECTION, Capability.MANAGED_FREIGHT_SERVICE));
        } catch (ReflectiveOperationException | LinkageError failure) {
            return blocked("Pinned managed railway API is unavailable: " + rootMessage(failure));
        }
    }

    private static AdapterHealth blocked(String detail) {
        return new AdapterHealth(AdapterHealth.Status.BLOCKED, detail, Set.of());
    }

    @Override
    public void installPlacementAuthority(RailPlacementAuthority authority) {
        if (health().status() != AdapterHealth.Status.AVAILABLE) return;
        try {
            Class<?> guardType = Class.forName(GUARD, false, getClass().getClassLoader());
            Class<?> decisionType = Class.forName(GUARD_DECISION, false, getClass().getClassLoader());
            Object allow = enumValue(decisionType, "ALLOW");
            Object deny = enumValue(decisionType, "DENY");
            Object proxy = Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { guardType }, (value, method, args) -> {
                if (method.getDeclaringClass() == Object.class) return objectMethod(value, method, args);
                if (!"evaluate".equals(method.getName()) || args == null || args.length != 5) return deny;
                return authority.mayReplace((ServerLevel) args[0], (String) args[1], (BlockPos) args[2],
                        (BlockState) args[3], (BlockState) args[4]) ? allow : deny;
            });
            api().getClass().getMethod("registerPlacementGuard", guardType).invoke(api(), proxy);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot install managed railway placement authority", failure);
        }
    }

    @Override public RailConnectionObservation plan(ServerLevel level, RailConnectionRequest request) {
        try {
            Object result = api().getClass().getMethod("plan", ServerLevel.class, String.class, BlockPos.class,
                    BlockPos.class, Direction.Axis.class, int.class).invoke(api(), level, request.connectionId(),
                    request.start(), request.target(), request.trackAxis(), request.maximumLength());
            return connection(result);
        } catch (ReflectiveOperationException failure) { return railFailure(request.connectionId(), failure); }
    }

    @Override public RailConnectionObservation start(ServerLevel level, String connectionId) {
        return invokeConnection("start", level, connectionId, "");
    }

    @Override public Optional<RailConnectionObservation> connection(ServerLevel level, String connectionId) {
        try {
            Object optional = api().getClass().getMethod("snapshot", ServerLevel.class, String.class)
                    .invoke(api(), level, connectionId);
            return ((Optional<?>) optional).map(RailwaysUntoldManagedAdapter::connection);
        } catch (ReflectiveOperationException failure) { return Optional.empty(); }
    }

    @Override public RailConnectionObservation suspend(ServerLevel level, String connectionId, String reason) {
        return invokeConnection("suspend", level, connectionId, reason);
    }

    @Override public RailConnectionObservation resume(ServerLevel level, String connectionId) {
        return invokeConnection("resume", level, connectionId, "");
    }

    @Override public FreightServiceObservation ensureFreightService(ServerLevel level, FreightServiceRequest request) {
        try {
            Object result = api().getClass().getMethod("ensureFreightService", ServerLevel.class, String.class,
                    String.class, BlockPos.class, Direction.class, String.class, String.class).invoke(api(), level,
                    request.serviceId(), request.connectionId(), request.assemblyTrack(), request.assemblyDirection(),
                    request.originStation(), request.destinationStation());
            return freight(result);
        } catch (ReflectiveOperationException failure) { return freightFailure(request.serviceId(), failure); }
    }

    @Override public Optional<FreightServiceObservation> freightService(ServerLevel level, String serviceId) {
        try {
            Object optional = api().getClass().getMethod("freightServiceSnapshot", ServerLevel.class, String.class)
                    .invoke(api(), level, serviceId);
            return ((Optional<?>) optional).map(RailwaysUntoldManagedAdapter::freight);
        } catch (ReflectiveOperationException failure) { return Optional.empty(); }
    }

    @Override public FreightServiceObservation parkFreightService(ServerLevel level, String serviceId) {
        return invokeFreight("requestParkAtOrigin", level, serviceId);
    }

    @Override public FreightServiceObservation resumeFreightService(ServerLevel level, String serviceId) {
        return invokeFreight("resumeFreightService", level, serviceId);
    }

    private RailConnectionObservation invokeConnection(String method, ServerLevel level, String id, String reason) {
        try {
            Method target = "suspend".equals(method)
                    ? api().getClass().getMethod(method, ServerLevel.class, String.class, String.class)
                    : api().getClass().getMethod(method, ServerLevel.class, String.class);
            Object result = "suspend".equals(method) ? target.invoke(api(), level, id, reason) : target.invoke(api(), level, id);
            return connection(result);
        } catch (ReflectiveOperationException failure) { return railFailure(id, failure); }
    }

    private FreightServiceObservation invokeFreight(String method, ServerLevel level, String id) {
        try {
            return freight(api().getClass().getMethod(method, ServerLevel.class, String.class).invoke(api(), level, id));
        } catch (ReflectiveOperationException failure) { return freightFailure(id, failure); }
    }

    private static Object api() throws ReflectiveOperationException {
        Class<?> entrypoint = Class.forName(ENTRYPOINT, false, RailwaysUntoldManagedAdapter.class.getClassLoader());
        Object api = entrypoint.getMethod("api").invoke(null);
        if (!Class.forName(API, false, RailwaysUntoldManagedAdapter.class.getClassLoader()).isInstance(api)) {
            throw new NoSuchMethodException("ManagedRailwayApi type mismatch");
        }
        return api;
    }

    private static RailConnectionObservation connection(Object value) {
        try {
            Class<?> type = value.getClass();
            Object nativeId = type.getMethod("nativeHeadId").invoke(value);
            return new RailConnectionObservation((String) type.getMethod("connectionId").invoke(value),
                    RailConnectionStatus.valueOf(type.getMethod("status").invoke(value).toString()),
                    (BlockPos) type.getMethod("currentPosition").invoke(value),
                    (String) type.getMethod("planHash").invoke(value), (int) type.getMethod("plannedLength").invoke(value),
                    (int) type.getMethod("remainingDistance").invoke(value), nativeId == null ? "" : nativeId.toString(),
                    (String) type.getMethod("diagnostic").invoke(value));
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Invalid managed connection snapshot", failure); }
    }

    private static FreightServiceObservation freight(Object value) {
        try {
            Class<?> type = value.getClass();
            Object nativeId = type.getMethod("nativeTrainId").invoke(value);
            return new FreightServiceObservation((String) type.getMethod("serviceId").invoke(value),
                    FreightServiceStatus.valueOf(type.getMethod("status").invoke(value).toString()),
                    nativeId == null ? "" : nativeId.toString(), (String) type.getMethod("currentStation").invoke(value),
                    (String) type.getMethod("scheduleFingerprint").invoke(value),
                    (String) type.getMethod("diagnostic").invoke(value));
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Invalid managed freight snapshot", failure); }
    }

    private static RailConnectionObservation railFailure(String id, Throwable failure) {
        return new RailConnectionObservation(id, RailConnectionStatus.BLOCKED, null, "", 0, 0, "", rootMessage(failure));
    }

    private static FreightServiceObservation freightFailure(String id, Throwable failure) {
        return new FreightServiceObservation(id, FreightServiceStatus.BLOCKED, "", "", "", rootMessage(failure));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Class<?> type, String name) { return Enum.valueOf((Class<? extends Enum>) type, name); }
    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> "PaleMirrorManagedPlacementGuard";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == (args == null ? null : args[0]);
            default -> null;
        };
    }
    private static String rootMessage(Throwable failure) {
        Throwable root = failure instanceof InvocationTargetException wrapped && wrapped.getCause() != null
                ? wrapped.getCause() : failure;
        return root.getClass().getSimpleName() + (root.getMessage() == null ? "" : ": " + root.getMessage());
    }
}
