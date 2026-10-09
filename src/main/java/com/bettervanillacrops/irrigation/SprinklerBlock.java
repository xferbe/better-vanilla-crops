package com.bettervanillacrops.irrigation;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Sprinkler de cobre: em cima de um tanque com água, rega um quadrado 9×9 (raio 4) — conta como chuva para as
 * plantas e hidrata a terra arada. Desliga sozinho quando chove nele.
 */
public class SprinklerBlock extends Block implements EntityBlock {
	public static final BooleanProperty ACTIVE = BlockStateProperties.ENABLED;
	private static final VoxelShape SHAPE = Shapes.or(
		Block.box(4, 0, 4, 12, 2, 12),
		Block.box(7, 2, 7, 9, 5, 9),
		Block.box(5, 5, 5, 11, 8, 11));

	public SprinklerBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(ACTIVE);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SprinklerBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (type != ModBlocks.SPRINKLER_ENTITY) return null;
		if (level.isClientSide()) return (l, pos, s, be) -> SprinklerBlockEntity.clientTick(l, pos, s);
		return (l, pos, s, be) -> SprinklerBlockEntity.serverTick((ServerLevel) l, pos, s, (SprinklerBlockEntity) be);
	}
}
