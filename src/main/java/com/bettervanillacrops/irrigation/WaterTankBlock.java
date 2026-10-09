package com.bettervanillacrops.irrigation;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Tanque de vidro com quinas de cobre que alimenta o sprinkler de cima. Balde de água com botão direito enche um dia
 * de uso (cabem 5 por bloco). Empilhados, viram uma coluna só, sem limite de altura: a moldura fica só em volta da
 * coluna e a água é uma só. A água aparece dentro do vidro e vai descendo; o comparador lê o nível do bloco.
 */
public class WaterTankBlock extends Block implements EntityBlock {
	/** Altura da água desenhada dentro do vidro, de 0 (vazio) a 15 (cheio). */
	public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL;
	/** Tem tanque encostado em cima / embaixo (mesma coluna). */
	public static final BooleanProperty UP = BlockStateProperties.UP;
	public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

	public WaterTankBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(LEVEL, 0).setValue(UP, false).setValue(DOWN, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LEVEL, UP, DOWN);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		return defaultBlockState()
			.setValue(UP, level.getBlockState(pos.above()).is(this))
			.setValue(DOWN, level.getBlockState(pos.below()).is(this));
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
		Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (direction == Direction.UP) return state.setValue(UP, neighbourState.is(this));
		if (direction == Direction.DOWN) return state.setValue(DOWN, neighbourState.is(this));
		return state;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new WaterTankBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
		InteractionHand hand, BlockHitResult hit) {
		if (stack.is(ModBlocks.WATER_COPPER_BUCKET)) return pourCopperBucket(stack, level, pos, player, hand);
		if (!stack.is(Items.WATER_BUCKET)) return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (!(level.getBlockEntity(pos) instanceof WaterTankBlockEntity) || WaterTankBlockEntity.isColumnFull(level, pos)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!level.isClientSide()) {
			WaterTankBlockEntity.fillColumn(level, pos, WaterTankBlockEntity.BUCKET);
			player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
			level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
			level.gameEvent(player, GameEvent.FLUID_PLACE, pos);
		}
		return InteractionResult.SUCCESS;
	}

	/** Despeja o balde de cobre balde por balde até ele esvaziar ou a coluna encher; o que sobrar fica no balde. */
	private InteractionResult pourCopperBucket(ItemStack stack, Level level, BlockPos pos, Player player, InteractionHand hand) {
		if (!(level.getBlockEntity(pos) instanceof WaterTankBlockEntity) || WaterTankBlockEntity.isColumnFull(level, pos)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!level.isClientSide()) {
			int water = CopperBucketItem.water(stack);
			int poured = 0;
			while (poured < water && !WaterTankBlockEntity.isColumnFull(level, pos)) {
				WaterTankBlockEntity.fillColumn(level, pos, WaterTankBlockEntity.BUCKET);
				poured++;
			}
			if (!player.getAbilities().instabuild) player.setItemInHand(hand, CopperBucketItem.withWater(water - poured));
			level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 0.9F);
			level.gameEvent(player, GameEvent.FLUID_PLACE, pos);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
		return state.getValue(LEVEL);
	}
}
