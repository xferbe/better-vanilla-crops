package com.bettervanillacrops.irrigation;

import java.util.Set;
import java.util.function.Function;

import com.bettervanillacrops.BetterVanillaCrops;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import com.mojang.serialization.Codec;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Tanque de água, sprinkler e balde de cobre: o que o mod acrescenta ao jogo. */
public final class ModBlocks {
	private ModBlocks() {
	}

	public static final Block WATER_TANK = block("water_tank", WaterTankBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_ORANGE).strength(0.8F).sound(SoundType.COPPER).noOcclusion()
		.isValidSpawn((state, level, pos, type) -> false).isRedstoneConductor((state, level, pos) -> false)
		.isSuffocating((state, level, pos) -> false));

	public static final Block SPRINKLER = block("sprinkler", SprinklerBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_ORANGE).strength(1.5F, 6.0F).sound(SoundType.COPPER).noOcclusion().requiresCorrectToolForDrops());

	public static final BlockEntityType<WaterTankBlockEntity> WATER_TANK_ENTITY = blockEntity("water_tank",
		new BlockEntityType<>(WaterTankBlockEntity::new, Set.of(WATER_TANK)));
	public static final BlockEntityType<SprinklerBlockEntity> SPRINKLER_ENTITY = blockEntity("sprinkler",
		new BlockEntityType<>(SprinklerBlockEntity::new, Set.of(SPRINKLER)));

	/** Chiado de água do sprinkler ligado: a chuva do jogo, mais baixa e mais aguda. */
	public static final SoundEvent SPRINKLER_SPRAY = Registry.register(BuiltInRegistries.SOUND_EVENT,
		BetterVanillaCrops.id("block.sprinkler.spray"), SoundEvent.createVariableRangeEvent(BetterVanillaCrops.id("block.sprinkler.spray")));

	/** Baldes de água dentro do balde de cobre (1 a 5). */
	public static final DataComponentType<Integer> WATER_LEVEL = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
		BetterVanillaCrops.id("water_level"), DataComponentType.<Integer>builder()
			.persistent(Codec.intRange(1, CopperBucketItem.MAX)).networkSynchronized(ByteBufCodecs.VAR_INT).build());

	public static final Item COPPER_BUCKET = item("copper_bucket", props -> new CopperBucketItem(false, props.stacksTo(16)));
	public static final Item WATER_COPPER_BUCKET = item("water_copper_bucket",
		props -> new CopperBucketItem(true, props.stacksTo(1).component(WATER_LEVEL, 1)));

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(out -> {
			out.accept(WATER_TANK);
			out.accept(SPRINKLER);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(out -> {
			out.accept(COPPER_BUCKET);
			out.accept(CopperBucketItem.withWater(CopperBucketItem.MAX));
		});
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props) {
		Block block = Blocks.register(ResourceKey.create(Registries.BLOCK, BetterVanillaCrops.id(name)), factory, props);
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, BetterVanillaCrops.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	private static Item item(String name, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BetterVanillaCrops.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
	}

	private static <T extends net.minecraft.world.level.block.entity.BlockEntity> BlockEntityType<T> blockEntity(String name, BlockEntityType<T> type) {
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, BetterVanillaCrops.id(name), type);
	}
}
