package com.bettervanillacrops;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Botão direito numa planta madura colhe e replanta: solta o que ela daria quebrando (com a qualidade), gasta uma
 * semente no replantio e garante pelo menos uma semente a mais para quem colheu. Agachado, o clique segue normal.
 */
public final class Harvest {
	private Harvest() {
	}

	public static InteractionResult onUse(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || player.isSecondaryUseActive()) return InteractionResult.PASS;
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		if (!CropQuality.isMature(state)) return InteractionResult.PASS;
		if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;

		CropQuality.Product product = CropQuality.product(state);
		// passa pelo mesmo caminho de quebrar: a qualidade entra aqui e a posição é esquecida
		List<ItemStack> drops = Block.getDrops(state, server, pos, null, player, player.getMainHandItem());
		takeOne(drops, product.seed());
		if (drops.stream().noneMatch(s -> s.is(product.seed()))) drops.add(new ItemStack(product.seed()));

		server.setBlock(pos, ((CropBlock) state.getBlock()).getStateForAge(0), Block.UPDATE_CLIENTS);
		for (ItemStack stack : drops) Block.popResource(server, pos, stack);
		server.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
		server.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
		return InteractionResult.SUCCESS;
	}

	private static void takeOne(List<ItemStack> drops, net.minecraft.world.item.Item item) {
		for (ItemStack stack : drops) {
			if (stack.is(item)) {
				stack.shrink(1);
				break;
			}
		}
		drops.removeIf(ItemStack::isEmpty);
	}
}
