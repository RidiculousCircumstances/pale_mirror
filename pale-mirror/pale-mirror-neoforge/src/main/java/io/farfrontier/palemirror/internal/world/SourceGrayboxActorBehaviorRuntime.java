package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxActorExecutionState;
import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Continuous, source-derived physical behavior for HOT graybox bodies.
 *
 * <p>This is deliberately a movement brain, not a second strategic simulator:
 * the source snapshot selects identity, role, deployment and swarm phase while
 * the execution ledger owns only the current physical hand-off. Native goals
 * stay disabled, so an unloaded actor has no unobserved Minecraft decision.
 * Combat is intentionally separate because durable cooldown/effect receipts
 * are required before it may create a canonical perturbation.</p>
 */
final class SourceGrayboxActorBehaviorRuntime {
    private static final int WORK_BUDGET_PER_TICK = 64;
    private static final double HUMAN_SPEED = 0.075d;
    private static final double HIVE_SPEED = 0.095d;
    private static final double GUARD_ENGAGEMENT_RADIUS = 18.0d;
    private static final double CIVILIAN_FEAR_RADIUS = 10.0d;
    private static final double HIVE_PURSUIT_RADIUS = 24.0d;
    private static final double ARRIVAL_DISTANCE = 0.45d;
    private static final long INTENT_INTERVAL_TICKS = 10L;
    private final Map<String, Vec3> intents = new LinkedHashMap<>();
    private List<ActorView> physical = List.of();
    private int cursor;
    private String intentRevision = "";

    void tick(ServerLevel level, ReferenceGrayboxSnapshot snapshot, ReferenceGrayboxActorExecutionState execution,
              SourceGrayboxMaterializer materializer, Map<String, Entity> admittedEntities, long gameTick) {
        boolean replan = gameTick % INTENT_INTERVAL_TICKS == 0L || !snapshot.stateRevision().equals(intentRevision);
        if (replan) {
            physical = physicalActors(level, snapshot, execution, materializer, admittedEntities);
            intents.clear();
            intentRevision = snapshot.stateRevision();
        }
        if (physical.isEmpty()) return;
        int processed = Math.min(WORK_BUDGET_PER_TICK, physical.size());
        int start = Math.floorMod(cursor, physical.size());
        cursor = Math.floorMod(start + processed, physical.size());
        for (int offset = 0; offset < processed; offset++) {
            ActorView actor = physical.get((start + offset) % physical.size());
            if (actor.body().isRemoved()) continue;
            Vec3 target = replan || !intents.containsKey(actor.id())
                    ? target(actor, physical, gameTick) : intents.get(actor.id());
            if (target != null) intents.put(actor.id(), target);
            if (target != null) moveToward(level, actor.body(), target, movementSpeed(actor));
        }
    }

    static List<ActorView> physicalActors(ServerLevel level, ReferenceGrayboxSnapshot snapshot,
                                          ReferenceGrayboxActorExecutionState execution,
                                          SourceGrayboxMaterializer materializer,
                                          Map<String, Entity> admittedEntities) {
        Map<String, ReferenceGrayboxSnapshot.Resident> residents = snapshot.residents().stream()
                .collect(java.util.stream.Collectors.toMap(ReferenceGrayboxSnapshot.Resident::id, value -> value));
        Map<String, ReferenceGrayboxSnapshot.Bioform> bioforms = snapshot.bioforms().stream()
                .collect(java.util.stream.Collectors.toMap(ReferenceGrayboxSnapshot.Bioform::id, value -> value));
        List<ActorView> result = new ArrayList<>();
        for (ReferenceGrayboxActorExecutionState.ActorState state : execution.actors()) {
            if (state.mode() != ReferenceGrayboxActorExecutionState.Mode.HOT) continue;
            Entity entity = materializer.actorEntity(level, admittedEntities, state);
            if (!(entity instanceof Mob body) || entity.isRemoved()) continue;
            switch (state.kind()) {
                case RESIDENT -> {
                    ReferenceGrayboxSnapshot.Resident resident = residents.get(state.id());
                    if (resident != null) result.add(ActorView.resident(state, body, resident));
                }
                case BIOFORM -> {
                    ReferenceGrayboxSnapshot.Bioform bioform = bioforms.get(state.id());
                    if (bioform != null) result.add(ActorView.bioform(state, body, bioform));
                }
            }
        }
        result.sort(Comparator.comparing(ActorView::id));
        return List.copyOf(result);
    }

    private static Vec3 target(ActorView actor, List<ActorView> physical, long gameTick) {
        return switch (actor.kind()) {
            case RESIDENT -> residentTarget(actor, physical, gameTick);
            case BIOFORM -> bioformTarget(actor, physical, gameTick);
        };
    }

    private static Vec3 residentTarget(ActorView actor, List<ActorView> physical, long gameTick) {
        ReferenceGrayboxSnapshot.Resident resident = actor.resident();
        ActorView hostile = nearest(actor, physical, ReferenceGrayboxActorExecutionState.ActorKind.BIOFORM,
                guard(resident) ? GUARD_ENGAGEMENT_RADIUS : CIVILIAN_FEAR_RADIUS);
        if (hostile != null) {
            if (guard(resident)) return hostile.body().position();
            Vec3 escape = actor.body().position().subtract(hostile.body().position());
            if (escape.horizontalDistanceSqr() > 0.0001d) return actor.body().position().add(escape.normalize().scale(8.0d));
        }
        return patrol(actor, gameTick, residentPatrolRadius(resident));
    }

    private static Vec3 bioformTarget(ActorView actor, List<ActorView> physical, long gameTick) {
        ReferenceGrayboxSnapshot.Bioform bioform = actor.bioform();
        boolean pursuing = bioform.feral() || bioform.phase().equals("engaging") || bioform.phase().equals("advancing");
        ActorView resident = pursuing ? nearest(actor, physical, ReferenceGrayboxActorExecutionState.ActorKind.RESIDENT, HIVE_PURSUIT_RADIUS) : null;
        return resident == null ? patrol(actor, gameTick, 5.0d) : resident.body().position();
    }

    private static ActorView nearest(ActorView actor, List<ActorView> physical, ReferenceGrayboxActorExecutionState.ActorKind kind,
                                     double radius) {
        double limit = radius * radius;
        return physical.stream().filter(candidate -> !candidate.body().isRemoved() && candidate.kind() == kind)
                .filter(candidate -> actor.body().distanceToSqr(candidate.body()) <= limit)
                .min(Comparator.comparingDouble(candidate -> actor.body().distanceToSqr(candidate.body()))).orElse(null);
    }

    static boolean guard(ReferenceGrayboxSnapshot.Resident resident) {
        if (resident.occupation().equals("guard") || resident.occupation().equals("soldier")) return true;
        return switch (resident.deploymentRole() == null ? "" : resident.deploymentRole()) {
            // The first three names are the exact source HumanUnitKind
            // vocabulary. The latter names preserve existing pre-source-v2
            // fixtures and do not create a second role model.
            case "line", "scout", "assault", "defend", "escort", "patrol" -> true;
            default -> false;
        };
    }

    /**
     * A resident's snapshot slot is the only local formation datum. The
     * source has already selected its operation, deployment role and exact
     * source anchor; this routine merely gives the visible person an
     * intelligible amount of motion around that slot. In particular, a medic
     * or logistics worker does not begin a private combat patrol just because
     * the player loaded their chunk.
     */
    private static double residentPatrolRadius(ReferenceGrayboxSnapshot.Resident resident) {
        if (resident.condition().equals("wounded")) return 1.25d;
        return switch (resident.deploymentRole() == null ? "" : resident.deploymentRole()) {
            case "scout" -> 7.0d;
            case "line" -> 3.5d;
            case "assault" -> 2.5d;
            case "engineer", "medic", "logistics" -> 1.5d;
            case "defend", "escort", "patrol" -> 4.0d;
            case "" -> 4.0d;
            default -> 3.0d;
        };
    }

    private static double movementSpeed(ActorView actor) {
        if (actor.kind() == ReferenceGrayboxActorExecutionState.ActorKind.BIOFORM) return HIVE_SPEED;
        ReferenceGrayboxSnapshot.Resident resident = actor.resident();
        if (resident.condition().equals("wounded")) return HUMAN_SPEED * 0.55d;
        return switch (resident.deploymentRole() == null ? "" : resident.deploymentRole()) {
            case "scout" -> HUMAN_SPEED * 1.20d;
            case "assault" -> HUMAN_SPEED * 1.10d;
            case "engineer", "medic", "logistics" -> HUMAN_SPEED * 0.85d;
            default -> HUMAN_SPEED;
        };
    }

    private static Vec3 patrol(ActorView actor, long gameTick, double radius) {
        int phase = Math.floorMod(actor.id().hashCode() + (int) Math.floorMod(Math.floorDiv(gameTick, 80L), 8L), 8);
        double angle = phase * (Math.PI * 2.0d / 8.0d);
        return new Vec3(actor.state().anchorXSixteenths() / (double) ReferenceGrayboxActorExecutionState.POSITION_SCALE
                + Math.cos(angle) * radius, actor.body().getY(),
                actor.state().anchorZSixteenths() / (double) ReferenceGrayboxActorExecutionState.POSITION_SCALE + Math.sin(angle) * radius);
    }

    private static void moveToward(ServerLevel level, Mob actor, Vec3 target, double speed) {
        Vec3 delta = target.subtract(actor.position());
        double distance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (distance <= ARRIVAL_DISTANCE) return;
        double x = delta.x / distance * speed;
        double z = delta.z / distance * speed;
        Vec3 step = firstOpenStep(level, actor, x, z);
        if (step == null) return;
        actor.setYRot((float) Math.toDegrees(Math.atan2(-step.x, step.z)));
        actor.yBodyRot = actor.getYRot();
        actor.move(MoverType.SELF, step);
    }

    private static Vec3 firstOpenStep(ServerLevel level, Mob actor, double x, double z) {
        List<Vec3> candidates = List.of(new Vec3(x, 0.0d, z), new Vec3(-z, 0.0d, x), new Vec3(z, 0.0d, -x));
        for (Vec3 candidate : candidates) {
            AABB next = actor.getBoundingBox().move(candidate);
            if (level.noCollision(actor, next)) return candidate;
        }
        return null;
    }

    record ActorView(ReferenceGrayboxActorExecutionState.ActorState state, Mob body,
                     ReferenceGrayboxSnapshot.Resident resident, ReferenceGrayboxSnapshot.Bioform bioform) {
        static ActorView resident(ReferenceGrayboxActorExecutionState.ActorState state, Mob body, ReferenceGrayboxSnapshot.Resident resident) {
            return new ActorView(state, body, resident, null);
        }

        static ActorView bioform(ReferenceGrayboxActorExecutionState.ActorState state, Mob body, ReferenceGrayboxSnapshot.Bioform bioform) {
            return new ActorView(state, body, null, bioform);
        }

        String id() { return state.id(); }
        ReferenceGrayboxActorExecutionState.ActorKind kind() { return state.kind(); }
    }
}
