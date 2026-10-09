package com.bettervanillacrops.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.bettervanillacrops.GrowthChance;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Trigo, cenoura, batata, beterraba e tocha-flor: o sorteio de crescer passa pelos fatores do mod. */
@Mixin(CropBlock.class)
public abstract class CropBlockMixin {
	@WrapOperation(method = "randomTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"))
	private int better_vanilla_crops$growthChance(RandomSource random, int bound, Operation<Integer> original,
		BlockState state, ServerLevel level, BlockPos pos, RandomSource r) {
		return GrowthChance.roll(random, bound, level, pos, state);
	}
}
