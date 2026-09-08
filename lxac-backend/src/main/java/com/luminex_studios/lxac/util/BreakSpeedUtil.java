package com.luminex_studios.lxac.util;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Accurate vanilla-like break time for 1.21.x.
 * Uses explicit formula (hardness, tool, enchants, effects, attributes)
 * and Paper Block#getBreakSpeed; takes the safer (higher expected ms) of the two.
 */
public final class BreakSpeedUtil {

    private BreakSpeedUtil() {}

    public static final class BreakEstimate {
        public final float progressPerTick;
        public final long expectedMs;
        public final float hardness;
        public final double toolSpeed;
        public final boolean correctTool;
        public final int efficiency;
        public final int haste;
        public final int fatigue;

        public BreakEstimate(float progressPerTick, long expectedMs, float hardness,
                             double toolSpeed, boolean correctTool, int efficiency,
                             int haste, int fatigue) {
            this.progressPerTick = progressPerTick;
            this.expectedMs = expectedMs;
            this.hardness = hardness;
            this.toolSpeed = toolSpeed;
            this.correctTool = correctTool;
            this.efficiency = efficiency;
            this.haste = haste;
            this.fatigue = fatigue;
        }
    }

    public static BreakEstimate estimate(Player player, Block block) {
        BreakEstimate explicit = estimateExplicit(player, block);

        float paperSpeed = 0f;
        try {
            paperSpeed = block.getBreakSpeed(player);
        } catch (Throwable ignored) {
        }

        long paperMs = speedToMs(paperSpeed);

        if (paperMs > explicit.expectedMs && paperSpeed > 0f) {
            return new BreakEstimate(paperSpeed, paperMs, explicit.hardness,
                    explicit.toolSpeed, explicit.correctTool, explicit.efficiency,
                    explicit.haste, explicit.fatigue);
        }
        return explicit;
    }

    public static BreakEstimate estimateExplicit(Player player, Block block) {
        Material type = block.getType();
        float hardness = type.getHardness();

        if (hardness < 0) {
            return new BreakEstimate(0f, 60_000L, hardness, 1, false, 0, 0, 0);
        }
        if (hardness == 0f) {
            return new BreakEstimate(1f, 0L, hardness, 1, true, 0, 0, 0);
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        Material toolMat = (tool == null || tool.getType().isAir()) ? Material.AIR : tool.getType();

        boolean correct = isCorrectTool(toolMat, type);
        double speed = correct ? getToolBaseSpeed(toolMat) : 1.0;

        int efficiency = 0;
        if (tool != null && !tool.getType().isAir()) {
            efficiency = tool.getEnchantmentLevel(Enchantment.EFFICIENCY);
        }

        if (correct && speed > 1.0 && efficiency > 0) {
            speed += 1.0 + (efficiency * efficiency);
        }

        int haste = 0;
        PotionEffect hasteFx = player.getPotionEffect(PotionEffectType.HASTE);
        if (hasteFx != null) {
            haste = hasteFx.getAmplifier() + 1;
            speed *= 1.0 + 0.2 * haste;
        }

        int fatigue = 0;
        PotionEffect fatigueFx = player.getPotionEffect(PotionEffectType.MINING_FATIGUE);
        if (fatigueFx != null) {
            fatigue = Math.min(fatigueFx.getAmplifier() + 1, 4);
            speed *= Math.pow(0.3, fatigue);
        }

        if (player.getEyeLocation().getBlock().isLiquid()) {
            boolean aqua = tool != null && tool.getEnchantmentLevel(Enchantment.AQUA_AFFINITY) > 0;
            if (!aqua) {
                speed /= 5.0;
            }
        }

        if (!player.isOnGround()) {
            speed /= 5.0;
        }

        speed *= getAttrMultiplier(player, "BLOCK_BREAK_SPEED");
        if (correct) {
            speed += getAttrValue(player, "MINING_EFFICIENCY");
        }

        if (speed <= 0) speed = 0.0001;

        boolean canHarvest = canHarvest(toolMat, type);
        double damage = canHarvest
                ? speed / (hardness * 30.0)
                : speed / (hardness * 100.0);

        float progress = (float) damage;
        return new BreakEstimate(progress, speedToMs(progress), hardness, speed,
                correct, efficiency, haste, fatigue);
    }

    public static long speedToMs(float progressPerTick) {
        if (progressPerTick <= 0f) return 60_000L;
        if (progressPerTick >= 1.0f) return 0L;
        return (long) (Math.ceil(1.0 / progressPerTick) * 50.0);
    }

    private static double getAttrMultiplier(Player player, String name) {
        try {
            Attribute a = (Attribute) Attribute.class.getField(name).get(null);
            AttributeInstance i = player.getAttribute(a);
            if (i == null) return 1.0;
            return Math.max(0.0001, i.getValue());
        } catch (Throwable t) {
            return 1.0;
        }
    }

    private static double getAttrValue(Player player, String name) {
        try {
            Attribute a = (Attribute) Attribute.class.getField(name).get(null);
            AttributeInstance i = player.getAttribute(a);
            if (i == null) return 0.0;
            return Math.max(0.0, i.getValue());
        } catch (Throwable t) {
            return 0.0;
        }
    }

    private static double getToolBaseSpeed(Material tool) {
        String n = tool.name();
        if (n.startsWith("WOODEN_")) return 2.0;
        if (n.startsWith("STONE_")) return 4.0;
        if (n.startsWith("IRON_")) return 6.0;
        if (n.startsWith("DIAMOND_")) return 8.0;
        if (n.startsWith("NETHERITE_")) return 9.0;
        if (n.startsWith("GOLDEN_")) return 12.0;
        if (tool == Material.SHEARS) return 2.0;
        return 1.0;
    }

    private static boolean isCorrectTool(Material tool, Material block) {
        if (tool == Material.AIR) return false;
        String t = tool.name();
        String b = block.name();

        if (t.endsWith("_PICKAXE")) {
            try {
                if (Tag.MINEABLE_PICKAXE.isTagged(block)) return true;
            } catch (Throwable ignored) {}
            return b.contains("STONE") || b.contains("ORE") || b.contains("DEEPSLATE")
                    || b.contains("NETHERRACK") || b.contains("BASALT") || b.contains("OBSIDIAN")
                    || b.contains("CONCRETE") || b.contains("TERRACOTTA") || b.contains("BRICK")
                    || b.contains("PRISMARINE") || b.contains("ANDESITE") || b.contains("DIORITE")
                    || b.contains("GRANITE") || b.contains("COBBLE") || b.contains("BLACKSTONE")
                    || b.contains("END_STONE") || b.contains("PURPUR") || b.contains("QUARTZ")
                    || b.contains("SANDSTONE") || b.contains("COPPER") || b.contains("AMETHYST")
                    || b.contains("ICE") || block == Material.ANCIENT_DEBRIS
                    || b.contains("RAW_") || b.contains("DIAMOND") || b.contains("EMERALD")
                    || b.contains("IRON") || b.contains("GOLD") || b.contains("COAL")
                    || b.contains("LAPIS") || b.contains("REDSTONE") || b.contains("NETHERITE");
        }
        if (t.endsWith("_AXE")) {
            try {
                if (Tag.MINEABLE_AXE.isTagged(block)) return true;
            } catch (Throwable ignored) {}
            return b.contains("LOG") || b.contains("WOOD") || b.contains("PLANKS")
                    || b.contains("FENCE") || b.contains("STEM") || b.contains("HYPHAE");
        }
        if (t.endsWith("_SHOVEL")) {
            try {
                if (Tag.MINEABLE_SHOVEL.isTagged(block)) return true;
            } catch (Throwable ignored) {}
            return b.contains("DIRT") || b.contains("SAND") || b.contains("GRAVEL")
                    || b.contains("CLAY") || b.contains("SOUL") || b.contains("MUD")
                    || b.contains("SNOW");
        }
        if (t.endsWith("_HOE")) {
            try {
                if (Tag.MINEABLE_HOE.isTagged(block)) return true;
            } catch (Throwable ignored) {}
            return b.contains("LEAVES") || b.contains("SCULK") || b.contains("MOSS")
                    || b.contains("WART") || b.contains("HAY");
        }
        if (tool == Material.SHEARS) {
            return b.contains("LEAVES") || b.contains("WOOL") || block == Material.COBWEB;
        }
        return false;
    }

    private static boolean canHarvest(Material tool, Material block) {
        float h = block.getHardness();
        if (h <= 0) return true;
        try {
            if (Tag.MINEABLE_PICKAXE.isTagged(block)) {
                return tool.name().endsWith("_PICKAXE");
            }
        } catch (Throwable ignored) {}
        String b = block.name();
        if (b.contains("ORE") || b.contains("STONE") || b.contains("DEEPSLATE")
                || block == Material.OBSIDIAN || block == Material.ANCIENT_DEBRIS) {
            return tool.name().endsWith("_PICKAXE");
        }
        return true;
    }
}
