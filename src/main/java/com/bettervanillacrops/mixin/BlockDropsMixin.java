package com.bettervanillacrops.mixin;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.bettervanillacrops.CropQuality;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Todo drop de bloco quebrado passa por um destes dois {@code getDrops} (jogador, aldeão fazendeiro, pistão, água):
 * é aqui que a qualidade soma os produtos a mais.
 */
@Mixin(Block.class)
public abstract class BlockDropsMixin {
	@ModifyReturnValue(method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)Ljava/util/List;", at = @At("RETURN"))
	private static List<ItemStack> better_vanilla_crops$quality(List<ItemStack> drops, BlockState state, ServerLevel level, BlockPos pos,
		@Nullable BlockEntity blockEntity) {
		return withQuality(drops, state, level, pos);
	}

	@ModifyReturnValue(method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemInstance;)Ljava/util/List;", at = @At("RETURN"))
	private static List<ItemStack> better_vanilla_crops$qualityByEntity(List<ItemStack> drops, BlockState state, ServerLevel level, BlockPos pos,
		@Nullable BlockEntity blockEntity, @Nullable Entity breaker, ItemInstance tool) {
		return withQuality(drops, state, level, pos);
	}

	private static List<ItemStack> withQuality(List<ItemStack> drops, BlockState state, ServerLevel level, BlockPos pos) {
		if (CropQuality.product(state) == null) return drops;
		// a lista do loot pode ser imutável
		List<ItemStack> out = new java.util.ArrayList<>(drops);
		CropQuality.onDrops(level, pos, state, out);
		return out;
	}
}
