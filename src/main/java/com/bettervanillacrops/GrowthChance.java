package com.bettervanillacrops;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Troca o sorteio vanilla de crescimento ({@code random.nextInt(n) == 0}, chance 1/n) por uma chance contínua
 * multiplicada pelos {@link GrowthFactors}. Multiplicar a velocidade antes do sorteio não serviria: o vanilla
 * arredonda {@code 25 / velocidade} para inteiro, e numa horta molhada um bônus de 25% sumiria no arredondamento.
 */
public final class GrowthChance {
	private GrowthChance() {
	}

	/** Devolve 0 quando a planta deve crescer, como o {@code nextInt} que substitui. */
	public static int roll(RandomSource random, int bound, ServerLevel level, BlockPos pos, BlockState state) {
		GrowthFactors factors = GrowthFactors.of(level, pos, state);
		float multiplier = factors.multiplier();
		boolean grows = multiplier == 1.0F
			? random.nextInt(bound) == 0
			: random.nextFloat() < Math.min(1.0F, multiplier / bound);
		if (!grows) return 1;
		CropQuality.onGrow(level, pos, state, factors);
		return 0;
	}
}
