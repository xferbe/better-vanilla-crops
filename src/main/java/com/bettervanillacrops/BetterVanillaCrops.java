package com.bettervanillacrops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import com.bettervanillacrops.irrigation.ModBlocks;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * A plantação vanilla reage ao ambiente: sol, chuva, neve, água corrente, variedade de culturas e colmeias mudam a
 * velocidade de crescimento e a qualidade da colheita. Botão direito colhe e replanta. Tanque de água + sprinkler
 * regam como chuva onde não chove. Não cria planta nem comida nova.
 */
public class BetterVanillaCrops implements ModInitializer {
	public static final String MOD_ID = "better_vanilla_crops";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Culturas que seguem as regras do mod (e contam para a variedade). */
	public static final TagKey<Block> CROPS = TagKey.create(Registries.BLOCK, id("crops"));

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		CropConfig.load();
		ModBlocks.init();
		// força o registro do anexo antes de qualquer chunk carregar
		CropQuality.DATA.identifier();
		UseBlockCallback.EVENT.register(Harvest::onUse);
		ServerTickEvents.END_SERVER_TICK.register(CropQuality::tick);
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> CropsCommand.register(dispatcher));
	}
}
