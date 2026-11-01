package io.farfrontier.palemirror.internal.frontier.v3;
import io.farfrontier.palemirror.frontier.v3.model.security.WeaponProfile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
/** Actual version-matched item/mod attribute input, not a sword-name damage table. */
final class FrontierV3WeaponProfiles {
    static WeaponProfile read(ItemStack stack) {
        var modifiers=stack.getAttributeModifiers();
        var damage=new ItemAttributeModifiers(modifiers.modifiers().stream().filter(value->value.attribute().equals(Attributes.ATTACK_DAMAGE)).toList(),false)
                .compute(1.0,EquipmentSlot.MAINHAND);
        var speed=new ItemAttributeModifiers(modifiers.modifiers().stream().filter(value->value.attribute().equals(Attributes.ATTACK_SPEED)).toList(),false)
                .compute(4.0,EquipmentSlot.MAINHAND);
        if(!Double.isFinite(speed)||speed<=0)throw new IllegalArgumentException("weapon has no supported attack interval");
        double reach=new ItemAttributeModifiers(modifiers.modifiers().stream().filter(value->value.attribute().equals(Attributes.ENTITY_INTERACTION_RANGE)).toList(),false)
                .compute(3.0,EquipmentSlot.MAINHAND);
        return new WeaponProfile(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),damage,(long)Math.ceil(20.0/speed),reach,1);
    }
    private FrontierV3WeaponProfiles(){}
}
