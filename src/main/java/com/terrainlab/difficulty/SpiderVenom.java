package com.terrainlab.difficulty;

import com.terrainlab.TerrainLab;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.spider.Spider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Ordinary spiders poison like cave spiders once local difficulty at the spider is above
 * {@link #VENOM_DIFFICULTY}: 7 seconds on Normal, 15 on Isolation (Hard), as cave spiders do.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class SpiderVenom {
    private static final float VENOM_DIFFICULTY = 5.0F;

    private SpiderVenom() {}

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (event.getInflictedDamage() <= 0.0F) return;
        if (!(event.getSource().getDirectEntity() instanceof Spider spider) || spider instanceof CaveSpider) return;
        if (!(spider.level() instanceof ServerLevel level)) return;
        int seconds = switch (level.getDifficulty()) {
            case NORMAL -> 7;
            case HARD -> 15;
            default -> 0;
        };
        if (seconds == 0) return;
        if (level.getCurrentDifficultyAt(spider.blockPosition()).getEffectiveDifficulty() <= VENOM_DIFFICULTY) return;
        event.getEntity().addEffect(new MobEffectInstance(MobEffects.POISON, seconds * 20, 0), spider);
    }
}
