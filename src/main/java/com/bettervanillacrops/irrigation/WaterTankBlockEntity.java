package com.bettervanillacrops.irrigation;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Água de um bloco de tanque, contada em tiques de uso do sprinkler: um balde rega um dia de jogo, cabem 5 por bloco.
 * Tanques empilhados formam uma coluna só: a água enche de baixo para cima e o sprinkler bebe de cima para baixo,
 * então ela fica sempre assentada no fundo, como num aquário.
 */
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

	/** Enche este bloco e devolve o que não coube. */
	long add(long amount) {
		long room = CAPACITY - water;
		long put = Math.min(room, amount);
		set(water + put);
		return amount - put;
	}

	/** Tira deste bloco e devolve quanto tirou. */
	long take(long amount) {
		long got = Math.min(water, amount);
		set(water - got);
		return got;
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

	/** Os blocos da coluna de tanques que passa por {@code pos}, de baixo para cima. */
	public static List<WaterTankBlockEntity> column(Level level, BlockPos pos) {
		BlockPos bottom = pos;
		while (level.getBlockEntity(bottom.below()) instanceof WaterTankBlockEntity) bottom = bottom.below();
		List<WaterTankBlockEntity> out = new ArrayList<>();
		for (BlockPos p = bottom; level.getBlockEntity(p) instanceof WaterTankBlockEntity tank; p = p.above()) out.add(tank);
		return out;
	}

	/** Junta toda a água da coluna e enche de novo de baixo para cima, como se ela tivesse escorrido para o fundo. */
	public static void settle(Level level, BlockPos pos) {
		List<WaterTankBlockEntity> tanks = column(level, pos);
		long left = 0;
		for (WaterTankBlockEntity tank : tanks) left += tank.water;
		for (WaterTankBlockEntity tank : tanks) {
			long here = Math.min(CAPACITY, left);
			tank.set(here);
			left -= here;
		}
	}

	/** Enche a coluna a partir do fundo. Falso se ela já estava cheia (nada entrou). */
	public static boolean fillColumn(Level level, BlockPos pos, long amount) {
		long left = amount;
		for (WaterTankBlockEntity tank : column(level, pos)) {
			left = tank.add(left);
			if (left == 0) break;
		}
		return left < amount;
	}

	public static boolean isColumnFull(Level level, BlockPos pos) {
		for (WaterTankBlockEntity tank : column(level, pos)) if (tank.water < CAPACITY) return false;
		return true;
	}

	public static long columnWater(Level level, BlockPos pos) {
		long total = 0;
		for (WaterTankBlockEntity tank : column(level, pos)) total += tank.water;
		return total;
	}

	/** Tira da coluna a partir do bloco de cima ({@code top}) para baixo; devolve quanto tirou. */
	public static long drainFromTop(Level level, BlockPos top, long amount) {
		long got = 0;
		for (BlockPos p = top; got < amount && level.getBlockEntity(p) instanceof WaterTankBlockEntity tank; p = p.below()) {
			got += tank.take(amount - got);
		}
		return got;
	}

	/** Tem água em algum bloco da coluna, descendo a partir de {@code top}? Para no primeiro que tiver. */
	public static boolean hasWaterBelow(Level level, BlockPos top) {
		for (BlockPos p = top; level.getBlockEntity(p) instanceof WaterTankBlockEntity tank; p = p.below()) {
			if (tank.water > 0) return true;
		}
		return false;
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
