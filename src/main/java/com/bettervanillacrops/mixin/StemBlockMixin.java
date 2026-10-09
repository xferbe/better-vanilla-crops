package com.bettervanillacrops.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.bettervanillacrops.GrowthChance;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Talo de melancia e abóbora: vale para crescer e para soltar a fruta (é o mesmo sorteio). */
@Mixin(StemBlock.class)
public abstract class StemBlockMixin {
	@WrapOperation(method = "randomTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"))
	private int better_vanilla_crops$growthChance(RandomSource random, int bound, Operation<Integer> original,
		BlockState state, ServerLevel level, BlockPos pos, RandomSource r) {
		return GrowthChance.roll(random, bound, level, pos, state);
	}
}
