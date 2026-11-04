package io.farfrontier.palemirror.internal.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.npc.Villager;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.parts.*;
import java.util.*;

/** Optional EMF boundary. The pinned Human Era profile declares its own bone names and hand geometry. */
final class HumanEraVisuals {
    private record Binding(EMFModelPartRoot root, int variant) {}
    // A resource reload creates fresh roots; logout clears the cache and the hard cap bounds old roots.
    private static final Map<Binding,Rig> RIGS=new LinkedHashMap<>();
    private static final Map<EMFModelPartRoot,ResidentPoseOverlay> OVERLAYS=new WeakHashMap<>();
    static void clear(){OVERLAYS.values().forEach(ResidentPoseOverlay::restore);OVERLAYS.clear();RIGS.clear();}
    static void install(){
        try{
            if(!EMFAnimationApi.registerAnimationHook(new EMFAnimationApi.EMFAnimationHook(){
                @Override public boolean onAnimationStart(AnimationContext context,boolean animationCancelled){
                    var previous=OVERLAYS.remove(context.animatingModelRoot());
                    if(previous!=null)previous.restore();
                    return super.onAnimationStart(context,animationCancelled);
                }
                @Override public void onAnimationEnd(AnimationContext context,boolean animationCancelled){
                    if(animationCancelled || context.error()!=null || !(context.activeState().emfEntity() instanceof Villager actor))return;
                    var rig=rig(context);if(rig==null)return;
                    var base=ResidentPoseOverlay.capture(rig.parts());
                    if(ResidentVisuals.apply(actor,context.activeState().age()-actor.tickCount,rig))
                        OVERLAYS.put(context.animatingModelRoot(),base);
                    ResidentVisuals.rendered(actor,"human_era");
                }
            }))throw new IllegalStateException("EMF rejected the resident animation compositor");
        }catch(Exception error){throw new IllegalStateException("Cannot install the resident EMF animation adapter",error);}
    }
    // EMF selects custom children only when a concrete entity variant is animated.
    static boolean supports(ModelPart root){return root instanceof EMFModelPartRoot;}
    static Optional<Rig> current(ModelPart root){
        if (!(root instanceof EMFModelPartRoot emf)) return Optional.empty();
        return Optional.of(binding(emf).orElseThrow(() -> new IllegalStateException("Human resident rig mismatch: "
            + emf + "; customParts=" + emf.getAllParts().filter(EMFModelPartCustom.class::isInstance)
            .map(part -> ((EMFModelPartCustom)part).id).toList() + "; vanillaParts=" + emf.getAllVanillaPartsByNameEMF().keySet())));
    }
    private static Rig rig(EMFAnimationApi.EMFAnimationHook.AnimationContext context){
        return binding(context.animatingModelRoot()).orElse(null);
    }
    private static Optional<Rig> binding(EMFModelPartRoot root){
        var key=new Binding(root,root.currentModelVariant);
        var cached=RIGS.get(key);if(cached!=null)return Optional.of(cached);
        var rig=Rig.bind(root);
        rig.ifPresent(value->{if(RIGS.size()>=128){var evicted=RIGS.keySet().iterator().next();RIGS.remove(evicted);
                var base=OVERLAYS.remove(evicted.root());if(base!=null)base.restore();}RIGS.put(key,value);});
        return rig;
    }
    /** Full-length Human Era arms have no elbow joint: distribute elbow flex into the arm rotation. */
    record Rig(ModelPart carrier,ModelPart body,ModelPart right,ModelPart left,ModelPart rightLeg,ModelPart leftLeg,List<ModelPart> heads) implements ResidentPoseRig {
        static Optional<Rig> bind(EMFModelPartRoot root){
            var parts=new HashMap<String,ModelPart>();
            root.getAllParts().filter(EMFModelPartCustom.class::isInstance).map(EMFModelPartCustom.class::cast)
                .forEach(part->parts.putIfAbsent(part.id,part));
            if(!parts.keySet().containsAll(Set.of("EMF_body","EMF_right_arms","EMF_left_arms","EMF_heada","EMF_headb","EMF_right_leg","EMF_left_leg")))return Optional.empty();
            var carrier=root.getAllVanillaPartsByNameEMF().get("body");if(carrier==null)return Optional.empty();
            return Optional.of(new Rig(carrier,parts.get("EMF_body"),parts.get("EMF_right_arms"),parts.get("EMF_left_arms"),parts.get("EMF_right_leg"),parts.get("EMF_left_leg"),List.of(parts.get("EMF_heada"),parts.get("EMF_headb"))));
        }
        public void arm(HumanoidArm side,float shoulder,float elbow,float yaw,float roll){
            var arm=side==HumanoidArm.RIGHT?right:left;arm.xRot=shoulder+elbow*0.35f;arm.yRot=yaw;arm.zRot=roll;
        }
        List<ModelPart> parts(){var result=new ArrayList<ModelPart>(heads);result.add(body);result.add(right);result.add(left);result.add(rightLeg);result.add(leftLeg);return List.copyOf(result);}
        public void leg(boolean right,float pitch){if(pitch!=0)(right?rightLeg:leftLeg).xRot=pitch;}
        public void torso(float yaw,float lean){ResidentPoseOverlay.torso(body,yaw,lean,0,-12,0);}
        public void look(float yaw,float pitch){for(var head:heads){head.yRot=yaw;head.xRot=pitch;}}
        void hand(HumanoidArm side,PoseStack pose){
            carrier.translateAndRotate(pose);body.translateAndRotate(pose);
            // Native ItemInHandLayer already advances the grip along a twelve-pixel arm.
            (side==HumanoidArm.RIGHT?right:left).translateAndRotate(pose);
        }
    }
    private HumanEraVisuals(){}
}
