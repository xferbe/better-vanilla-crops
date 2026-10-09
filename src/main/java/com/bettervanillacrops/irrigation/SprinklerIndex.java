package com.bettervanillacrops.irrigation;

import java.util.Map;
import java.util.WeakHashMap;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sprinklers carregados em cada mundo, mantidos pelos próprios block entities (entram ao carregar, saem ao
 * descarregar ou quebrar). Assim a planta pergunta "tem sprinkler ligado perto?" sem varrer blocos.
 */
public final class SprinklerIndex {
	private static final Map<Level, LongOpenHashSet> LOADED = new WeakHashMap<>();

	private SprinklerIndex() {
	}

	static synchronized void add(Level level, BlockPos pos) {
		LOADED.computeIfAbsent(level, l -> new LongOpenHashSet()).add(pos.asLong());
	}

	static synchronized void remove(Level level, BlockPos pos) {
		LongOpenHashSet set = LOADED.get(level);
		if (set != null) set.remove(pos.asLong());
	}

	/** Tem sprinkler ligado regando essa planta (quadrado 9×9, até um bloco acima ou abaixo)? */
	public static synchronized boolean isWatered(Level level, BlockPos crop) {
		LongOpenHashSet set = LOADED.get(level);
		if (set == null || set.isEmpty()) return false;
		for (LongIterator it = set.iterator(); it.hasNext(); ) {
			long packed = it.nextLong();
			int dx = BlockPos.getX(packed) - crop.getX(), dy = BlockPos.getY(packed) - crop.getY(), dz = BlockPos.getZ(packed) - crop.getZ();
			if (Math.abs(dx) > SprinklerBlockEntity.RADIUS || Math.abs(dz) > SprinklerBlockEntity.RADIUS || Math.abs(dy) > 1) continue;
			BlockState state = level.getBlockState(BlockPos.of(packed));
			if (state.is(ModBlocks.SPRINKLER) && state.getValue(SprinklerBlock.ACTIVE)) return true;
		}
		return false;
	}
}
