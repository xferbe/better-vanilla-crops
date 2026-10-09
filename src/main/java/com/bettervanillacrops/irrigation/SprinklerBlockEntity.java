package com.bettervanillacrops.irrigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Liga com água na coluna de tanques de baixo e sem chuva em cima, e gasta um tique de água por tique de jogo — só com o chunk
 * ativo, que é quando as plantas crescem. Quando a noite é pulada dormindo, o relógio do dia salta e o salto é
 * descontado de uma vez.
 */
public class SprinklerBlockEntity extends BlockEntity {
	/** Alcance para os lados: quadrado 9×9 com o sprinkler no meio, igual à hidratação da água vanilla. */
	public static final int RADIUS = 4;

	/** Relógio do dia no último tique (não é salvo: depois de carregar, o primeiro tique conta como 1). */
	private long lastClock = -1;

	public SprinklerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.SPRINKLER_ENTITY, pos, state);
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		if (!level.isClientSide()) SprinklerIndex.add(level, worldPosition);
	}

	@Override
	public void clearRemoved() {
		super.clearRemoved();
		if (level != null && !level.isClientSide()) SprinklerIndex.add(level, worldPosition);
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		if (level != null && !level.isClientSide()) SprinklerIndex.remove(level, worldPosition);
	}

	static void serverTick(ServerLevel level, BlockPos pos, BlockState state, SprinklerBlockEntity sprinkler) {
		long clock = level.getDefaultClockTime();
		long jump = sprinkler.lastClock < 0 ? 1 : clock - sprinkler.lastClock;
		sprinkler.lastClock = clock;
		// tique normal anda 1; relógio parado anda 0 e conta 1; noite pulada salta de uma vez (até um dia)
		long used = jump > 1 && jump <= WaterTankBlockEntity.BUCKET ? jump : 1;

		// bebe da coluna de tanques embaixo, de cima para baixo
		boolean raining = level.precipitationAt(pos.above()) != Biome.Precipitation.NONE;
		boolean active = !raining && WaterTankBlockEntity.hasWaterBelow(level, pos.below());
		if (active) WaterTankBlockEntity.drainFromTop(level, pos.below(), used);
		if (state.getValue(SprinklerBlock.ACTIVE) != active) {
			level.setBlock(pos, state.setValue(SprinklerBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
		}
	}

	/** Dois jatos opostos girando: a cada tique, gotas ao longo de cada jato até a borda do quadrado. */
	static void clientTick(Level level, BlockPos pos, BlockState state) {
		if (!state.getValue(SprinklerBlock.ACTIVE)) return;
		RandomSource random = level.getRandom();
		double cx = pos.getX() + 0.5, cz = pos.getZ() + 0.5, top = pos.getY() + 0.5;
		double spin = level.getGameTime() * 0.35;
		for (int arm = 0; arm < 2; arm++) {
			double angle = spin + arm * Math.PI;
			double cos = Math.cos(angle), sin = Math.sin(angle);
			// distância até a borda do quadrado nessa direção: os cantos ficam mais longe que os lados
			double reach = (RADIUS + 0.5) / Math.max(Math.abs(cos), Math.abs(sin));
			for (int i = 0; i < 4; i++) {
				double f = 0.12 + random.nextDouble() * 0.88;
				double d = f * reach;
				// arco: sobe um pouco saindo do bico e desce até o chão na ponta
				double y = top + 0.9 * f * (1 - f) * 2 - 0.85 * f;
				double jitter = (random.nextDouble() - 0.5) * 0.25;
				level.addParticle(ParticleTypes.SPLASH, cx + cos * d - sin * jitter, y, cz + sin * d + cos * jitter,
					cos * 0.04, 0.0, sin * 0.04);
			}
			if (random.nextInt(3) == 0) {
				level.addParticle(ParticleTypes.SPLASH, cx + cos * reach, pos.getY() + 0.05, cz + sin * reach, 0, 0, 0);
			}
		}
		// som de água jorrando a cada 2 s, defasado por posição para vários sprinklers não baterem juntos
		if ((level.getGameTime() + pos.asLong()) % 40 == 0) {
			level.playLocalSound(pos, ModBlocks.SPRINKLER_SPRAY, SoundSource.BLOCKS, 1.0F, 0.9F + random.nextFloat() * 0.2F, false);
		}
		if (random.nextInt(4) == 0) {
			level.addParticle(ParticleTypes.FALLING_WATER, cx + Mth.nextDouble(random, -0.2, 0.2), top, cz + Mth.nextDouble(random, -0.2, 0.2), 0, 0, 0);
		}
	}
}
