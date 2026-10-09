package com.bettervanillacrops.irrigation;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Balde de cobre: carrega até 5 baldes de água, um a cada clique numa fonte — o suficiente para encher um bloco de
 * tanque de uma vez. Não despeja água no mundo, só em tanque e em caldeirão. Vazio, empilha como o balde de ferro.
 */
public class CopperBucketItem extends Item {
	public static final int MAX = 5;

	private final boolean filled;

	public CopperBucketItem(boolean filled, Item.Properties properties) {
		super(properties);
		this.filled = filled;
	}

	/** Baldes de água dentro: 0 no vazio, 1 a 5 no cheio. */
	public static int water(ItemStack stack) {
		if (stack.is(ModBlocks.COPPER_BUCKET)) return 0;
		return stack.is(ModBlocks.WATER_COPPER_BUCKET) ? stack.getOrDefault(ModBlocks.WATER_LEVEL, 1) : 0;
	}

	/** Balde de cobre com essa quantidade de água (0 = vazio). */
	public static ItemStack withWater(int amount) {
		if (amount <= 0) return new ItemStack(ModBlocks.COPPER_BUCKET);
		ItemStack stack = new ItemStack(ModBlocks.WATER_COPPER_BUCKET);
		stack.set(ModBlocks.WATER_LEVEL, Math.min(MAX, amount));
		return stack;
	}

	/** Clique numa fonte de água: pega mais um balde, até 5. */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		int water = water(stack);
		if (water >= MAX) return InteractionResult.PASS;
		BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
		if (hit.getType() != HitResult.Type.BLOCK) return InteractionResult.PASS;
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		if (!level.mayInteract(player, pos) || !level.getFluidState(pos).is(FluidTags.WATER) || !level.getFluidState(pos).isSource()
			|| !(state.getBlock() instanceof BucketPickup pickup)) {
			return InteractionResult.PASS;
		}
		if (pickup.pickupBlock(player, level, pos, state).isEmpty()) return InteractionResult.PASS;
		pickup.getPickupSound().ifPresent(sound -> player.playSound(sound, 1.0F, 1.0F));
		level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
		return InteractionResult.SUCCESS.heldItemTransformedTo(ItemUtils.createFilledResult(stack, player, withWater(water + 1)));
	}

	/**
	 * Caldeirão: com água no balde, enche o caldeirão (gasta um balde); com espaço no balde, esvazia um caldeirão
	 * cheio (ganha um balde), como o balde de ferro.
	 */
	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState state = level.getBlockState(pos);
		Player player = context.getPlayer();
		ItemStack stack = context.getItemInHand();
		int water = water(stack);
		boolean cauldronFull = state.is(Blocks.WATER_CAULDRON) && state.getValue(LayeredCauldronBlock.LEVEL) == LayeredCauldronBlock.MAX_FILL_LEVEL;
		boolean canFill = filled && water > 0 && (state.is(Blocks.CAULDRON) || state.is(Blocks.WATER_CAULDRON) && !cauldronFull);
		boolean canTake = water < MAX && cauldronFull;
		if (!canFill && !canTake) return InteractionResult.PASS;
		if (!level.isClientSide()) {
			if (canFill) {
				level.setBlock(pos, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL), Block.UPDATE_ALL);
				level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
				level.gameEvent(player, GameEvent.FLUID_PLACE, pos);
			} else {
				level.setBlock(pos, Blocks.CAULDRON.defaultBlockState(), Block.UPDATE_ALL);
				level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
				level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
			}
			ItemStack result = withWater(canFill ? water - 1 : water + 1);
			if (player != null) player.setItemInHand(context.getHand(), ItemUtils.createFilledResult(stack, player, result));
		}
		return InteractionResult.SUCCESS;
	}
}
