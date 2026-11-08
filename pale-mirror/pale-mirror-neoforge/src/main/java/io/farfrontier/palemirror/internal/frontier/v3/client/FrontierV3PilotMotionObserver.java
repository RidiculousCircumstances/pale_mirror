package io.farfrontier.palemirror.internal.frontier.v3.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.farfrontier.palemirror.PaleMirrorMod;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Bounded read-only local pose series for a named client-rendered retained body. */
final class FrontierV3PilotMotionObserver {
    private static long actionStartedTick = Long.MIN_VALUE;
    private static int entityRuntimeId = -1;
    private static List<Pose> samples = List.of();
    private static Set<Long> sampledTicks = Set.of();
    /** Monotonic client turns expose a player-visible server freeze even while game time stops. */
    private static long elapsedClientTicks;
    private static Vec3 circuitOrigin;
    private static int circuitLeg;
    private static int pursuitStall;
    private static double circuitYaw,circuitDistance;
    private static Vec3 previousPlayerPosition;

    private FrontierV3PilotMotionObserver() { }

    static boolean observe(Minecraft minecraft, JsonObject action, long startedTick, int actionStep, String milestone) {
        if (actionStartedTick != startedTick) { reset(); actionStartedTick = startedTick; }
        long clientTick = elapsedClientTicks++;
        ResourceLocation expectedType = ResourceLocation.parse(action.get("entityType").getAsString());
        // Once this bounded action has selected its exact rendered body, a terminal receipt may
        // legitimately retire the process diagnostic. Keep observing that same runtime body so
        // the carrier can prove that completion/release did not silently despawn and recreate
        // the worker between two diagnostic polls.
        String worker = workerId(action);
        String expectedName = entityRuntimeId < 0 ? expectedName(minecraft, action) : "";
        if (expectedName == null) {
            if (clientTick * 50L >= action.get("timeoutMs").getAsLong()) {
                throw new IllegalStateException("motion observation lacks the exact worker presentation diagnostic: " + action.get("id").getAsString());
            }
            return false;
        }
        Vec3 workTarget=null;
        if(action.has("workFacingTarget")){
            var position=FrontierV3TestPilotClient.resolvedPosition(minecraft,action,"workFacingTarget");
            if(position==null)return false;
            workTarget=Vec3.atCenterOf(position);
        }
        double maximum = action.has("maxDistance") ? action.get("maxDistance").getAsDouble() : 64.0D;
        Vec3 anchor = action.has("anchor") ? position(action.getAsJsonObject("anchor")) : null;
        UUID expectedUuid = FrontierV3PilotEntityIdentity.expectedEntityUuid(worker);
        // A terminal receipt legitimately retires its process diagnostic, but it does not
        // retire the actor identity.  Bind to the actor's read-only UUID admission before
        // choosing a visible body, so an adjacent similarly named farmer cannot satisfy a
        // post-closure continuity trace.
        if (entityRuntimeId < 0 && worker != null && expectedUuid == null) {
            // This is the same bounded read-only diagnostic cadence used after selection.  An
            // earlier retry-on-every-client-turn loop saturated normal command feedback before
            // the first actor receipt could arrive, then misreported that self-inflicted loss
            // as a missing exact UUID.  It neither creates demand nor changes custody.
            if (clientTick % 20L == 0L) minecraft.player.connection.sendCommand("pale_mirror v3 inspect actor " + worker);
            if (clientTick * 50L >= action.get("timeoutMs").getAsLong()) {
                throw new IllegalStateException("motion observation lacks exact actor UUID admission: " + worker);
            }
            return false;
        }
        Entity body = entityRuntimeId < 0
                ? (expectedUuid != null ? exactVisibleEntity(minecraft, expectedType, expectedName, expectedUuid, maximum)
                : anchor != null ? nearest(minecraft, expectedType, anchor, maximum)
                : expectedName.isBlank() ? nearest(minecraft, expectedType, minecraft.player.position(), maximum)
                : FrontierV3TestPilotPresentation.nearestVisibleNamedEntity(minecraft, expectedType, expectedName, maximum))
                : minecraft.level.getEntity(entityRuntimeId);
        if (!matches(body, expectedType, expectedName, expectedUuid)) {
            if(entityRuntimeId>=0){
                emit(action,expectedType,action.get("sampleEveryTicks").getAsInt(),actionStep,milestone,"failed");
                throw new IllegalStateException("retained motion body disappeared or changed identity: runtime="+entityRuntimeId
                        +" expectedUuid="+expectedUuid+" actor="+receivedDiagnostic("actor",worker));
            }
            if (clientTick * 50L >= action.get("timeoutMs").getAsLong()) {
                throw new IllegalStateException("local named motion body was unavailable: " + expectedType + " " + expectedName);
            }
            return false;
        }
        if(action.has("requiredCombatAttacks") || action.has("requiredWorkGestures") || action.has("observerCircuit") || action.has("requiredModelRig"))FrontierV3TestPilotClient.look(minecraft,body.getEyePosition());
        if(action.has("observerCircuit"))circuit(minecraft,action);
        if (entityRuntimeId < 0) { entityRuntimeId = body.getId(); samples = new ArrayList<>(); sampledTicks = new HashSet<>(); }
        int interval = action.get("sampleEveryTicks").getAsInt();
        long gameTime = minecraft.level.getGameTime();
        // The declarative action caps this collection at 12,000 ticks.  Keep the complete
        // requested bounded window: truncating it at 256 could make a station-only prefix look
        // like evidence for the later retained travel and work phases.
        if (clientTick % interval == 0L && samples.size() < action.get("durationTicks").getAsInt() && sampledTicks.add(clientTick)) {
            // Read the already-delivered process result and periodically renew it through the
            // same ordinary player diagnostic path.  A rendered pose alone must never be
            // mistaken for a semantic work duty.
            if (!action.has("requiredCombatAttacks") && !action.has("observerCircuit") && clientTick % 20L == 0L) minecraft.player.connection.sendCommand("pale_mirror v3 inspect process " + action.get("id").getAsString());
            // This is a bounded diagnostic correlation, not a second pose source: it answers
            // whether a visually held remote body was also held by the authoritative server.
            // The actor receipt pairs a read-only authoritative position with the rendered one.
            // It shares the process receipt's bounded cadence: a motion carrier must not turn
            // diagnostic polling itself into a high-frequency scene-admission workload.
            if (worker != null && clientTick % 20L == 0L) minecraft.player.connection.sendCommand("pale_mirror v3 inspect actor " + worker);
            // Observe the actual final rig layer. A delivered packet or a native Villager
            // swing flag alone does not prove that the resident model rendered work.
            var renderedWork=io.farfrontier.palemirror.internal.client.PaleMirrorResidentWorkClient.rendered(body);
            boolean workAnimation = renderedWork!=null;
            // This is deliberately an observed client value, paired with the rendered body
            // position.  It lets the native carrier distinguish a frozen authoritative body
            // from a body which has a delivered interpolation/velocity but is not rendering it;
            // it is never an alternative motion oracle.
            Vec3 velocity = body.getDeltaMovement();
            if(action.has("observerCircuit") && !samples.isEmpty()){
                var previous=samples.getLast();
                boolean stationary=Math.hypot(body.getX()-previous.x(),body.getZ()-previous.z())<0.01;
                pursuitStall=stationary && body.distanceTo(minecraft.player)>4.5?pursuitStall+interval:0;
                if(pursuitStall>40)throw new IllegalStateException("guard stalled outside melee reach while the player moved around it; actor="+receivedDiagnostic("actor",worker));
            }
            Vec3 authoritative = authoritativePosition(action);
            samples.add(new Pose(clientTick, gameTime, body.getX(), body.getY(), body.getZ(), minecraft.player.getX(), minecraft.player.getZ(), velocity.x, velocity.z,
                    authoritative == null ? null : authoritative.x, authoritative == null ? null : authoritative.z,
                    workAnimation, semanticPhase(action, body.getId()),
                    body instanceof net.minecraft.world.entity.npc.Villager resident
                        ? io.farfrontier.palemirror.internal.client.ResidentVisuals.renderedRig(resident) : "unobserved",
                    io.farfrontier.palemirror.internal.client.PaleMirrorCombatClient.rendered(body),renderedWork,
                    renderedWork==null || workTarget==null?null:(double)Math.abs(net.minecraft.util.Mth.wrapDegrees(
                            (float)Math.toDegrees(Math.atan2(body.getX()-workTarget.x,workTarget.z-body.getZ()))-renderedWork.bodyYaw()))));
        }
        if (clientTick < action.get("durationTicks").getAsLong()) return false;
        try{
            if(action.has("observerCircuit") && circuitDistance<16)throw new IllegalStateException("player did not actually traverse the declared pursuit circuit: "+circuitDistance);
            try { requireRepositioning(action);requireModelRig(action);requireCombat(action);requireWork(action); }
            catch(RuntimeException failure){emit(action,expectedType,interval,actionStep,milestone,"failed");throw failure;}
            emit(action,expectedType,interval,actionStep,milestone,"ok");return true;
        }finally{releaseCircuit();}
    }

    /** Actual relative trajectories distinguish an outer approach from a declared flank label. */
    private static void requireRepositioning(JsonObject action){
        if(!action.has("minimumAngularTravelDeg") && !action.has("minimumTravelBlocks"))return;
        double angle=0,minimum=0,maximum=0,travel=0;var first=samples.getFirst();double previous=Math.atan2(first.z()-first.observerZ(),first.x()-first.observerX());
        for(var sample:samples){
            double next=Math.atan2(sample.z()-sample.observerZ(),sample.x()-sample.observerX());
            angle+=Math.atan2(Math.sin(next-previous),Math.cos(next-previous));previous=next;minimum=Math.min(minimum,angle);maximum=Math.max(maximum,angle);
            travel=Math.max(travel,Math.hypot(sample.x()-first.x(),sample.z()-first.z()));
        }
        if(action.has("minimumAngularTravelDeg") && Math.toDegrees(maximum-minimum)<action.get("minimumAngularTravelDeg").getAsDouble())
            throw new IllegalStateException("fighter did not actually change side around the observed player: "+Math.toDegrees(maximum-minimum));
        if(action.has("minimumTravelBlocks") && travel<action.get("minimumTravelBlocks").getAsDouble())
            throw new IllegalStateException("fighter did not actually traverse the required approach: "+travel);
    }

    private static void requireModelRig(JsonObject action) {
        if (!action.has("requiredModelRig")) return;
        String expected = action.get("requiredModelRig").getAsString();
        long observed = samples.stream().filter(sample -> expected.equals(sample.rig())).count();
        if (observed < 3) throw new IllegalStateException("Required resident rig was not actually rendered: " + expected);
    }

    /** Optional combat assertion consumes the same actually rendered bounded pose series. */
    private static void requireCombat(JsonObject action){
        if(!action.has("requiredCombatAttacks"))return;
        var observed=new HashSet<String>();boolean moving=false,maneuverMoving=false;
        for(int i=0;i<samples.size();i++){
            var sample=samples.get(i);var combat=sample.combat();if(combat==null)continue;
            if(combat.age()<=combat.preparation()+(java.util.Set.of("BURST","BACKSTEP").contains(combat.attack())?20:1))observed.add(combat.attack());
            if(i>0 && combat.age()>=combat.preparation() && java.util.Set.of("BURST","BACKSTEP").contains(combat.attack())){
                var before=samples.get(i-1);var prior=before.combat();
                if(prior!=null && prior.attempt()==combat.attempt() && Math.hypot(sample.x()-before.x(),sample.z()-before.z())>0.005){
                    for(int j=i-1;j>=0;j--){var start=samples.get(j);var cue=start.combat();
                        if(cue==null || cue.attempt()!=combat.attempt() || !cue.attack().equals(combat.attack()) || cue.age()<cue.preparation())break;
                        if(Math.hypot(sample.x()-start.x(),sample.z()-start.z())>=(combat.attack().equals("BACKSTEP")?0.5:1.5)){maneuverMoving=true;break;}
                    }
                }
            }
            if(i==0 || combat.age()>=combat.preparation())continue;
            var before=samples.get(i-1);var prior=before.combat();
            if(prior!=null && prior.attempt()==combat.attempt() && prior.age()<prior.preparation()
                    && Math.hypot(sample.x()-before.x(),sample.z()-before.z())>0.005)moving=true;
        }
        for(var required:action.getAsJsonArray("requiredCombatAttacks"))if(!observed.contains(required.getAsString()))
            throw new IllegalStateException("client did not render required attack "+required+"; observed="+observed);
        if(action.has("requireManeuverMotion") && action.get("requireManeuverMotion").getAsBoolean() && !maneuverMoving)
            throw new IllegalStateException("no actual body motion during the rendered maneuver");
        if(action.has("requireWindupMotion") && action.get("requireWindupMotion").getAsBoolean() && !moving)
            throw new IllegalStateException("no actual body motion during the rendered preparation");
    }

    /** Work proof reads only poses actually applied by the shared renderer, not an arm event flag. */
    private static void requireWork(JsonObject action){
        if(!action.has("requiredWorkGestures"))return;
        if(action.has("workFacingTarget")){
            var work=samples.stream().filter(p->p.work()!=null).toList();
            if(work.isEmpty() || work.stream().anyMatch(p->p.workFacingError()==null || p.workFacingError()>20))
                throw new IllegalStateException("rendered work body faces away from its declared station; errors="
                        +work.stream().map(Pose::workFacingError).toList());
        }
        for(var required:action.getAsJsonArray("requiredWorkGestures")){
            var poses=samples.stream().filter(p->p.work()!=null && p.work().gesture().equals(required.getAsString())).toList();
            double min=poses.stream().mapToDouble(p->p.work().shoulder()).min().orElse(0);
            double max=poses.stream().mapToDouble(p->p.work().shoulder()).max().orElse(0);
            if(poses.size()<3 || max-min<0.05)throw new IllegalStateException("no changing rendered work gesture "+required+"; samples="+poses.size());
        }
    }

    private static void circuit(Minecraft minecraft,JsonObject action){
        if(circuitOrigin==null){circuitOrigin=minecraft.player.position();previousPlayerPosition=circuitOrigin;circuitYaw=Math.toRadians(minecraft.player.getYRot());}
        circuitDistance+=minecraft.player.position().subtract(previousPlayerPosition).horizontalDistance();previousPlayerPosition=minecraft.player.position();
        var points=action.getAsJsonArray("observerCircuit");var point=points.get(circuitLeg%points.size()).getAsJsonObject();
        double x=point.get("x").getAsDouble(),z=point.get("z").getAsDouble();
        var target=circuitOrigin.add(Math.cos(circuitYaw)*x-Math.sin(circuitYaw)*z,0,Math.sin(circuitYaw)*x+Math.cos(circuitYaw)*z);var delta=target.subtract(minecraft.player.position());
        if(delta.horizontalDistanceSqr()<0.3){circuitLeg++;return;}
        double yaw=Math.toRadians(minecraft.player.getYRot());double forward=-Math.sin(yaw)*delta.x+Math.cos(yaw)*delta.z;
        double left=Math.cos(yaw)*delta.x+Math.sin(yaw)*delta.z;
        minecraft.options.keyUp.setDown(forward>0.25);minecraft.options.keyDown.setDown(forward< -0.25);
        minecraft.options.keyLeft.setDown(left>0.25);minecraft.options.keyRight.setDown(left< -0.25);minecraft.options.keySprint.setDown(true);
    }
    private static void releaseCircuit(){
        if(circuitOrigin==null)return;var minecraft=Minecraft.getInstance();minecraft.options.keyUp.setDown(false);minecraft.options.keyDown.setDown(false);
        minecraft.options.keyLeft.setDown(false);minecraft.options.keyRight.setDown(false);minecraft.options.keySprint.setDown(false);circuitOrigin=null;circuitLeg=0;pursuitStall=0;circuitDistance=0;previousPlayerPosition=null;
    }
    static void reset() {releaseCircuit(); actionStartedTick = Long.MIN_VALUE; entityRuntimeId = -1; samples = List.of(); sampledTicks = Set.of(); elapsedClientTicks = 0L; }

    /** Reads only a diagnostic the normal client packet handler already retained. */
    private static JsonObject receivedDiagnostic(String kind, String id) {
        FrontierV3TestPilotClient.ObservedDiagnostic observed = FrontierV3TestPilotClient.diagnostics.get(new FrontierV3TestPilotClient.DiagnosticIdentity(kind, id));
        return observed == null ? null : observed.value();
    }

    /** Never fall back to nearest-of-type: a motion receipt belongs to the current exact lease worker. */
    private static String expectedName(Minecraft minecraft, JsonObject action) {
        if (action.has("nameContains")) return FrontierV3PilotPresentationName.resolve(minecraft, action);
        // An explicit worker must pass exact UUID admission before body selection.
        // Nameless pack animals need no unrelated resident/process name receipt.
        if (action.has("workerId")) return "";
        JsonObject process = receivedDiagnostic("process", action.get("id").getAsString());
        if (process == null || !process.has("identity") || !process.get("identity").isJsonObject()) return null;
        JsonObject identity = process.getAsJsonObject("identity");
        return identity.has("workerPresentation") && identity.get("workerPresentation").isJsonPrimitive()
                ? identity.get("workerPresentation").getAsString() : null;
    }
    private static String semanticPhase(JsonObject action, int bodyRuntimeId) {
        // A station gesture is delivered by the server at PREPARED -> RUNNING and carries its
        // exact typed duty. Prefer it only for its six-tick bounded lifetime: unlike the
        // ordinary 20-tick inspect cadence, this keeps a visible interaction from being
        // labelled with a stale pre-arrival TRAVELLING receipt.
        String activeCue = io.farfrontier.palemirror.internal.client.PaleMirrorStationWorkGestureClient.observedDutyPhase(bodyRuntimeId);
        if (activeCue != null) return activeCue;
        JsonObject process = receivedDiagnostic("process", action.get("id").getAsString());
        if (process == null || !process.has("result") || !process.get("result").isJsonObject()) return actorDutyPhase(action);
        JsonObject result = process.getAsJsonObject("result");
        if (result.has("dutyPhase") && result.get("dutyPhase").isJsonPrimitive()) return result.get("dutyPhase").getAsString();
        if (!result.has("sitePhase") || !result.has("intentStatus")) return actorDutyPhase(action);
        if (process.has("cursor") && process.get("cursor").isJsonObject()) {
            JsonObject cursor = process.getAsJsonObject("cursor");
            if (cursor.has("index") && cursor.has("length") && cursor.get("index").getAsInt() < cursor.get("length").getAsInt() - 1) {
                return "TRAVELLING:" + result.get("intentStatus").getAsString();
            }
        }
        return "HARVESTING:" + result.get("intentStatus").getAsString();
    }

    /** A retired harvest process may no longer answer, while its exact actor is lawfully HOT under shared custody. */
    private static String actorDutyPhase(JsonObject action) {
        String worker = workerId(action);
        JsonObject actor = worker == null ? null : receivedDiagnostic("actor", worker);
        if (actor != null && actor.has("dutyPhase") && actor.get("dutyPhase").isJsonPrimitive()) {
            return actor.get("dutyPhase").getAsString();
        }
        return "UNOBSERVED";
    }
    private static String workerId(JsonObject action) {
        if (action.has("workerId")) return action.get("workerId").getAsString();
        JsonObject process = receivedDiagnostic("process", action.get("id").getAsString());
        if (process == null || !process.has("identity") || !process.get("identity").isJsonObject()) return null;
        JsonObject identity = process.getAsJsonObject("identity");
        return identity.has("worker") && identity.get("worker").isJsonPrimitive() ? identity.get("worker").getAsString() : null;
    }
    private static Vec3 authoritativePosition(JsonObject action) {
        String worker = workerId(action);
        JsonObject actor = worker == null ? null : receivedDiagnostic("actor", worker);
        if (actor == null || !actor.has("physicalAdmission") || !actor.get("physicalAdmission").isJsonObject()) return null;
        JsonObject admission = actor.getAsJsonObject("physicalAdmission");
        if (!admission.has("observedExact") || !admission.get("observedExact").isJsonObject()) return null;
        JsonObject observed = admission.getAsJsonObject("observedExact");
        return observed.has("x") && observed.has("y") && observed.has("z")
                ? new Vec3(observed.get("x").getAsDouble(), observed.get("y").getAsDouble(), observed.get("z").getAsDouble()) : null;
    }

    private static boolean matches(Entity body, ResourceLocation expectedType, String expectedName, UUID expectedUuid) {
        return body != null && !body.isRemoved() && BuiltInRegistries.ENTITY_TYPE.getKey(body.getType()).equals(expectedType)
                && (expectedUuid == null || expectedUuid.equals(body.getUUID()))
                && (expectedName.isBlank() || body.getCustomName() != null && body.getCustomName().getString().contains(expectedName));
    }
    private static Entity exactVisibleEntity(Minecraft minecraft, ResourceLocation expectedType, String expectedName, UUID expectedUuid, double maximum) {
        return minecraft.level.getEntitiesOfClass(Entity.class, minecraft.player.getBoundingBox().inflate(maximum), entity ->
                        matches(entity, expectedType, expectedName, expectedUuid) && minecraft.player.hasLineOfSight(entity))
                .stream().findFirst().orElse(null);
    }
    private static Entity nearest(Minecraft minecraft, ResourceLocation expectedType, Vec3 anchor, double maximum) {
        return minecraft.level.getEntitiesOfClass(Entity.class, new net.minecraft.world.phys.AABB(anchor, anchor).inflate(maximum), entity ->
                        !entity.isRemoved() && BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(expectedType)
                                && minecraft.player.hasLineOfSight(entity))
                .stream().sorted(java.util.Comparator.comparingDouble((Entity entity) -> entity.distanceToSqr(anchor)).thenComparing(Entity::getUUID))
                .findFirst().orElse(null);
    }
    private static Vec3 position(JsonObject value) { return new Vec3(value.get("x").getAsDouble(), value.get("y").getAsDouble(), value.get("z").getAsDouble()); }
    private static void emit(JsonObject action, ResourceLocation type, int interval, int actionStep, String milestone,String status) {
        JsonObject value = new JsonObject(); value.addProperty("kind", "pilot_motion"); value.addProperty("id", action.get("id").getAsString());
        value.addProperty("status", status); value.addProperty("entityType", type.toString()); value.addProperty("sampleEveryTicks", interval);
        value.addProperty("durationTicks", action.get("durationTicks").getAsLong()); value.addProperty("entityRuntimeId", entityRuntimeId);
        Entity retained = minecraftEntity();
        if (retained != null) value.addProperty("entityUuid", retained.getUUID().toString());
        JsonArray values = new JsonArray(); for (Pose pose : samples) { JsonObject sample = new JsonObject(); sample.addProperty("tick", pose.tick()); sample.addProperty("serverTick", pose.serverTick());
            sample.addProperty("x", pose.x()); sample.addProperty("y", pose.y()); sample.addProperty("z", pose.z());
            sample.addProperty("observerX",pose.observerX());sample.addProperty("observerZ",pose.observerZ());
            sample.addProperty("velocityX", pose.velocityX()); sample.addProperty("velocityZ", pose.velocityZ()); sample.addProperty("workAnimation", pose.workAnimation());
            if (pose.authoritativeX() != null) { sample.addProperty("authoritativeX", pose.authoritativeX()); sample.addProperty("authoritativeZ", pose.authoritativeZ()); }
            if(pose.combat()!=null){var combat=pose.combat();var rendered=new JsonObject();rendered.addProperty("attack",combat.attack());
                rendered.addProperty("attempt",combat.attempt());rendered.addProperty("age",combat.age());rendered.addProperty("preparation",combat.preparation());
                rendered.addProperty("shoulder",combat.shoulder());rendered.addProperty("elbow",combat.elbow());sample.add("combat",rendered);}
            if(pose.work()!=null){var work=pose.work();var rendered=new JsonObject();rendered.addProperty("gesture",work.gesture());
                rendered.addProperty("age",work.age());rendered.addProperty("shoulder",work.shoulder());rendered.addProperty("elbow",work.elbow());rendered.addProperty("bodyYaw",work.bodyYaw());
                if(pose.workFacingError()!=null)rendered.addProperty("facingErrorDeg",pose.workFacingError());sample.add("work",rendered);}
            sample.addProperty("modelRig", pose.rig()); sample.addProperty("semanticPhase", pose.semanticPhase()); values.add(sample); }
        value.add("samples", values); FrontierV3PilotSessionControl.stampDiagnostic(value, actionStep, milestone);
        PaleMirrorMod.LOGGER.info("PMV3_PILOT_DIAGNOSTIC {}", value);
    }
    private static Entity minecraftEntity() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null || entityRuntimeId < 0 ? null : minecraft.level.getEntity(entityRuntimeId);
    }
    private record Pose(long tick, long serverTick, double x, double y, double z, double observerX, double observerZ, double velocityX, double velocityZ,
                        Double authoritativeX, Double authoritativeZ, boolean workAnimation, String semanticPhase, String rig,
                        io.farfrontier.palemirror.internal.client.PaleMirrorCombatClient.Rendered combat,
                        io.farfrontier.palemirror.internal.client.PaleMirrorResidentWorkClient.Rendered work,Double workFacingError) { }
}
