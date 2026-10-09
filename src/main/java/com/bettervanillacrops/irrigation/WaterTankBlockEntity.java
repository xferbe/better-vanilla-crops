package com.bettervanillacrops.irrigation;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Água do tanque, contada em tiques de uso do sprinkler: um balde rega um dia de jogo. */
public class WaterTankBlockEntity extends BlockEntity {
	public static final long BUCKET = 24_000;
	public static final long CAPACITY = 5 * BUCKET;

	private long water;

	public WaterTankBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.WATER_TANK_ENTITY, pos, state);
	}

	public long water() {
		return water;
	}

	public boolean isFull() {
		return water >= CAPACITY;
	}

	/** Enche até a capacidade; o que passar se perde, como derramar o resto do balde. */
	public void fill(long amount) {
		set(Math.min(CAPACITY, water + amount));
	}

	public void drain(long amount) {
		set(Math.max(0, water - amount));
	}

	public void set(long amount) {
		if (amount == water) return;
		water = amount;
		setChanged();
		if (level == null) return;
		// a altura desenhada sobe de degrau em degrau; qualquer gota conta como o primeiro
		int shown = water == 0 ? 0 : Math.max(1, Mth.ceil(water * 15.0 / CAPACITY));
		BlockState state = getBlockState();
		if (state.getValue(WaterTankBlock.LEVEL) != shown) {
			level.setBlock(worldPosition, state.setValue(WaterTankBlock.LEVEL, shown), Block.UPDATE_ALL);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		water = input.getLongOr("water", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putLong("water", water);
	}
}
