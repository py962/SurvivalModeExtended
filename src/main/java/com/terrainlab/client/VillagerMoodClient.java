package com.terrainlab.client;

import com.terrainlab.TerrainLab;
import com.terrainlab.client.particle.VillagerMoodParticle;
import com.terrainlab.client.particle.VillagerMoodParticle.Motion;
import com.terrainlab.registry.ModAttachments;
import com.terrainlab.registry.ModParticles;
import com.terrainlab.village.VillagerMoodService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

/**
 * Client side of villager comfort indicators. Registers the mood particle providers,
 * copies the synced {@code VILLAGER_MOOD_FLAGS} mask and gesture state onto the
 * villager render state for VillagerModelMoodMixin, and spawns one particle per villager
 * about every {@link #PARTICLE_INTERVAL} ticks, staggered by entity id and rotating
 * through the active states. Nothing is shown for sleeping, invisible or content
 * (mask 0) villagers.
 */
@EventBusSubscriber(modid = TerrainLab.MODID, value = Dist.CLIENT)
public final class VillagerMoodClient {

    public static final ContextKey<Integer> MOOD_FLAGS =
            new ContextKey<>(Identifier.fromNamespaceAndPath(TerrainLab.MODID, "villager_mood_flags"));

    /** True while the local player has a trade screen open within TRADE_RANGE; the model halves its posture then. */
    public static final ContextKey<Boolean> NEAR_OPEN_TRADE =
            new ContextKey<>(Identifier.fromNamespaceAndPath(TerrainLab.MODID, "villager_near_open_trade"));

    private static final double TRADE_RANGE_SQR = 6.0D * 6.0D;

    /** The yawn/stamp gesture last triggered by the villager's voice and its start tick (VillagerGestureAccess). */
    public static final ContextKey<Integer> GESTURE =
            new ContextKey<>(Identifier.fromNamespaceAndPath(TerrainLab.MODID, "villager_mood_gesture"));
    public static final ContextKey<Integer> GESTURE_START =
            new ContextKey<>(Identifier.fromNamespaceAndPath(TerrainLab.MODID, "villager_mood_gesture_start"));

    private static final double PARTICLE_RANGE = 16.0D;
    private static final int PARTICLE_INTERVAL = 50;

    private VillagerMoodClient() {
    }

    @SubscribeEvent
    static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.VILLAGER_COLD.get(),
                sprites -> new VillagerMoodParticle.Provider(sprites, Motion.FLAKE));
        event.registerSpriteSet(ModParticles.VILLAGER_BREATH.get(),
                sprites -> new VillagerMoodParticle.Provider(sprites, Motion.PUFF));
        event.registerSpriteSet(ModParticles.VILLAGER_HOT.get(),
                sprites -> new VillagerMoodParticle.Provider(sprites, Motion.DROP));
        event.registerSpriteSet(ModParticles.VILLAGER_FEAR.get(),
                sprites -> new VillagerMoodParticle.Provider(sprites, Motion.POP));
        event.registerSpriteSet(ModParticles.VILLAGER_TIRED.get(),
                sprites -> new VillagerMoodParticle.Provider(sprites, Motion.RISE));
        event.registerSpriteSet(ModParticles.VILLAGER_HUNGRY.get(),
                sprites -> new VillagerMoodParticle.Provider(sprites, Motion.BOB));
        event.registerSpriteSet(ModParticles.VILLAGER_INJURED.get(),
                sprites -> new VillagerMoodParticle.Provider(sprites, Motion.POP));
    }

    @SubscribeEvent
    static void onRegisterRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.<Villager, VillagerRenderState>registerEntityModifier(
                VillagerRenderer.class,
                (villager, state) -> {
                    state.setRenderData(MOOD_FLAGS, villager.getData(ModAttachments.VILLAGER_MOOD_FLAGS.get()));
                    Player player = Minecraft.getInstance().player;
                    state.setRenderData(NEAR_OPEN_TRADE, player != null
                            && player.containerMenu instanceof net.minecraft.world.inventory.MerchantMenu
                            && player.distanceToSqr(villager) <= TRADE_RANGE_SQR);
                    if (villager instanceof com.terrainlab.village.VillagerGestureAccess gestures) {
                        state.setRenderData(GESTURE, gestures.terrainlab$gesture());
                        state.setRenderData(GESTURE_START, gestures.terrainlab$gestureStart());
                    }
                }
        );
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Player player = minecraft.player;

        if (level == null || player == null || minecraft.isPaused()) {
            return;
        }

        long time = level.getGameTime();

        for (Villager villager : level.getEntitiesOfClass(
                Villager.class,
                player.getBoundingBox().inflate(PARTICLE_RANGE))) {

            if (Math.floorMod(time + villager.getId() * 17L, PARTICLE_INTERVAL) != 0
                    || villager.isSleeping()
                    || villager.isInvisible()) {

                continue;
            }

            int flags = villager.getData(ModAttachments.VILLAGER_MOOD_FLAGS.get());

            if (flags == 0) {
                continue;
            }

            // Rotate between the active states.
            int active = Integer.bitCount(flags);
            int pick = (int) Math.floorMod(time / PARTICLE_INTERVAL + villager.getId(), (long) active);
            int bit = flags;
            for (int i = 0; i < pick; i++) {
                bit &= bit - 1; // drop the lowest set bit
            }
            bit = Integer.lowestOneBit(bit);

            spawn(level, villager, bit);
        }
    }

    private static void spawn(ClientLevel level, Villager villager, int state) {
        RandomSource random = level.getRandom();
        double x = villager.getX();
        double y = villager.getY();
        double z = villager.getZ();
        double top = y + villager.getBbHeight();

        Vec3 look = Vec3.directionFromRotation(0.0F, villager.getYHeadRot());
        Vec3 side = new Vec3(-look.z, 0.0D, look.x).scale(random.nextBoolean() ? 1.0D : -1.0D);

        if (state == VillagerMoodService.COLD) {
            // A snowflake off one shoulder, and a breath puff in front of the face.
            level.addParticle(ModParticles.VILLAGER_COLD.get(),
                    x + side.x * 0.35D, top - 0.55D, z + side.z * 0.35D,
                    side.x * 0.01D, 0.0D, side.z * 0.01D);
            Vec3 mouth = villager.getEyePosition().add(look.scale(0.4D)).subtract(0.0D, 0.2D, 0.0D);
            level.addParticle(ModParticles.VILLAGER_BREATH.get(),
                    mouth.x, mouth.y, mouth.z,
                    look.x * 0.03D, 0.005D, look.z * 0.03D);

        } else if (state == VillagerMoodService.HOT) {
            level.addParticle(ModParticles.VILLAGER_HOT.get(),
                    x + side.x * 0.3D, top - 0.15D, z + side.z * 0.3D,
                    side.x * 0.06D, 0.12D, side.z * 0.06D);

        } else if (state == VillagerMoodService.FEARFUL) {
            level.addParticle(ModParticles.VILLAGER_FEAR.get(),
                    x, top + 0.35D, z, 0.0D, 0.0D, 0.0D);

        } else if (state == VillagerMoodService.TIRED) {
            level.addParticle(ModParticles.VILLAGER_TIRED.get(),
                    x + side.x * 0.2D, top + 0.15D, z + side.z * 0.2D, 0.0D, 0.0D, 0.0D);

        } else if (state == VillagerMoodService.HUNGRY) {
            level.addParticle(ModParticles.VILLAGER_HUNGRY.get(),
                    x, top + 0.35D, z, 0.0D, 0.0D, 0.0D);

        } else if (state == VillagerMoodService.INJURED) {
            // A small red cross popping above the head, slightly off to one side.
            level.addParticle(ModParticles.VILLAGER_INJURED.get(),
                    x + side.x * 0.15D, top + 0.3D, z + side.z * 0.15D, 0.0D, 0.0D, 0.0D);

        } else if (state == VillagerMoodService.TRAPPED) {
            // Vanilla's angry-villager cloud; its provider adds +0.5 to Y itself.
            level.addParticle(ParticleTypes.ANGRY_VILLAGER,
                    x, top - 0.35D, z, 0.0D, 0.0D, 0.0D);
        }
    }
}
