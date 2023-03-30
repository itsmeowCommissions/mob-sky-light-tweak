package dev.itsmeow.modskylighttweak.mixin;

import dev.itsmeow.modskylighttweak.ModSkyLightTweak;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Monster.class)
public class MixinMonster {

    @Inject(method = "isDarkEnoughToSpawn(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)Z", at = @At("HEAD"), cancellable = true)
    private static void isDarkEnoughToSpawn(ServerLevelAccessor serverLevelAccessor, BlockPos blockPos, RandomSource randomSource, CallbackInfoReturnable<Boolean> result) {
        int block_light_req = serverLevelAccessor.dimensionType().monsterSpawnBlockLightLimit();
        boolean use_sky_light = ModSkyLightTweak.CONFIG_WRAPPER.use_sky_light.getValue();
        int override_block_light = ModSkyLightTweak.CONFIG_WRAPPER.override_block_light_level.getValue();
        if(override_block_light != -1) {
            block_light_req = override_block_light;
        } else if(!use_sky_light) {
            return;
        }
        if(use_sky_light && blockPos.getY() >= ModSkyLightTweak.CONFIG_WRAPPER.use_sky_y_above.getValue()) {
            int sky_light_req = ModSkyLightTweak.CONFIG_WRAPPER.override_sky_light_level.getValue();
            if (sky_light_req < 15 && serverLevelAccessor.getBrightness(LightLayer.SKY, blockPos) > sky_light_req) {
                result.setReturnValue(false);
                result.cancel();
            } else if(!ModSkyLightTweak.CONFIG_WRAPPER.use_block_light_with_sky.getValue()) {
                // Don't call the parent, since it will check default block light
                if (serverLevelAccessor.getBrightness(LightLayer.SKY, blockPos) > randomSource.nextInt(32)) { // recreate parent filtering
                    result.setReturnValue(false);
                }
                result.cancel();
            } else if(override_block_light != -1) { // check using the override value instead of the parent.
                if (block_light_req < 15 && serverLevelAccessor.getBrightness(LightLayer.BLOCK, blockPos) > block_light_req) {
                    result.setReturnValue(false);
                    result.cancel();
                }
            }
        } else if(override_block_light != -1) {
            if (block_light_req < 15 && serverLevelAccessor.getBrightness(LightLayer.BLOCK, blockPos) > block_light_req) {
                result.setReturnValue(false);
                result.cancel();
            }
        }
    }

}
