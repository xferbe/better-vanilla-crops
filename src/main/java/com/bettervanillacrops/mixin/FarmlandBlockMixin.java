package com.bettervanillacrops.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.bettervanillacrops.irrigation.SprinklerIndex;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FarmlandBlock;

/** Terra arada no alcance de um sprinkler ligado fica molhada mesmo sem água nenhuma por perto. */
@Mixin(FarmlandBlock.class)
public abstract class FarmlandBlockMixin {
	@ModifyReturnValue(method = "isNearWater", at = @At("RETURN"))
	private static boolean better_vanilla_crops$sprinkler(boolean nearWater, LevelReader level, BlockPos pos) {
		return nearWater || level instanceof Level l && SprinklerIndex.isWatered(l, pos.above());
	}
}
