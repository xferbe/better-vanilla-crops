package com.bettervanillacrops;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Multiplicadores de crescimento, em {@code config/better-vanilla-crops.json}. O arquivo é criado com os valores
 * padrão na primeira vez e reescrito a cada início, para ganhar as chaves novas sem perder o que foi ajustado.
 */
public final class CropConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static CropConfig instance = new CropConfig();

	/** Sem luz do céu (teto que não é vidro): multiplica a chance de crescer. */
	public float noSunMultiplier = 0.4F;
	/** Luz do céu mínima para contar como sol. 15 = céu aberto ou vidro. */
	public int minSkyLight = 15;
	/** Chuva caindo na planta: soma na chance. */
	public float rainBonus = 0.25F;
	/** Neve caindo na planta: multiplica a chance. */
	public float snowMultiplier = 0.6F;
	/** Água corrente no alcance da hidratação (4 blocos): soma na chance. */
	public float flowingWaterBonus = 0.10F;
	/** Cada tipo diferente de cultura no 3×3 em volta: soma na chance. */
	public float varietyBonusPerType = 0.10F;
	public int varietyMaxTypes = 3;
	/** Cada colmeia ou ninho no raio, proporcional ao mel (0 a 5): soma na chance. Colmeia sem mel não conta. */
	public float hiveBonusPerHive = 0.03F;
	public int hiveRadius = 16;
	/** Teto do bônus somado de todas as colmeias. */
	public float hiveMaxBonus = 0.15F;
	/** Média de pontos ao longo do crescimento para a colheita ser boa (+1 produto) e excelente (+2). */
	public float qualityGoodAverage = 2.0F;
	public float qualityExcellentAverage = 4.0F;
	/** Teto do multiplicador final. */
	public float maxMultiplier = 2.0F;

	public static CropConfig get() {
		return instance;
	}

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("better-vanilla-crops.json");
		try {
			if (Files.exists(file)) {
				CropConfig read = GSON.fromJson(Files.readString(file), CropConfig.class);
				if (read != null) instance = read;
			}
			Files.writeString(file, GSON.toJson(instance));
		} catch (IOException | RuntimeException e) {
			BetterVanillaCrops.LOGGER.warn("Não deu para ler {}, usando os valores padrão", file, e);
		}
	}
}
