package com.horizonsunbound.effect;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Verifies the NBT backport against real vanilla item attributes and the installed durability/glint mixins. */
public final class TemperingTest {
    private TemperingTest() {}
    private static double amount(ItemStack stack, EquipmentSlot slot, Attribute attribute) {
        return stack.getAttributeModifiers(slot).get(attribute).stream()
                .filter(modifier -> modifier.getOperation() == AttributeModifier.Operation.ADDITION).mapToDouble(AttributeModifier::getAmount).sum();
    }
    public static void run(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        int swordDurability = sword.getMaxDamage();
        double damage = amount(sword, EquipmentSlot.MAINHAND, Attributes.ATTACK_DAMAGE);
        double speed = amount(sword, EquipmentSlot.MAINHAND, Attributes.ATTACK_SPEED);
        ItemEntity weapon = new ItemEntity(helper.getLevel(), 0, 0, 0, sword);
        NetherEffects.temper(helper.getLevel(), weapon);
        sword = weapon.getItem();
        helper.assertTrue(sword.getMaxDamage() == swordDurability * 3 / 2, "tempered durability must rise by 50%");
        helper.assertTrue(sword.hasFoil() && sword.getTag().getBoolean("ds_tempered"), "tempered gear must retain its marker and glint");
        helper.assertTrue(amount(sword, EquipmentSlot.MAINHAND, Attributes.ATTACK_DAMAGE) == damage + 1, "default sword damage was replaced");
        helper.assertTrue(amount(sword, EquipmentSlot.MAINHAND, Attributes.ATTACK_SPEED) == speed, "default attack speed was lost");
        NetherEffects.temper(helper.getLevel(), weapon);
        helper.assertTrue(amount(weapon.getItem(), EquipmentSlot.MAINHAND, Attributes.ATTACK_DAMAGE) == damage + 1, "a second temper added another bonus");

        ItemStack chestplate = new ItemStack(Items.DIAMOND_CHESTPLATE);
        double armor = amount(chestplate, EquipmentSlot.CHEST, Attributes.ARMOR);
        double toughness = amount(chestplate, EquipmentSlot.CHEST, Attributes.ARMOR_TOUGHNESS);
        ItemEntity armorItem = new ItemEntity(helper.getLevel(), 0, 0, 0, chestplate);
        NetherEffects.temper(helper.getLevel(), armorItem);
        helper.assertTrue(amount(armorItem.getItem(), EquipmentSlot.CHEST, Attributes.ARMOR) == armor, "base armor was lost");
        helper.assertTrue(amount(armorItem.getItem(), EquipmentSlot.CHEST, Attributes.ARMOR_TOUGHNESS) == toughness + 1, "toughness bonus is wrong");
        helper.succeed();
    }
}
