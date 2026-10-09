package com.bettervanillacrops;

import java.util.HashSet;
import java.util.Set;

import com.bettervanillacrops.irrigation.SprinklerIndex;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.tags.FluidTags;

/**
 * O que está valendo para uma planta agora, e o multiplicador que isso dá na chance de crescer a cada tique
 * aleatório. O cálculo vanilla (terra molhada, vizinho igual) continua por baixo; isto vem por cima.
 *
 * @param sun luz do céu chega na planta (céu aberto ou vidro)
 * @param skyLight luz do céu no bloco da planta
 * @param precipitation chuva ou neve caindo na planta agora
 * @param sprinkler sprinkler ligado regando a planta (vale como chuva, não soma com ela)
 * @param flowingWater água corrente no alcance da hidratação
 * @param varietyTypes tipos diferentes de cultura no 3×3 em volta, já limitado ao máximo
 * @param hives colmeias e ninhos com mel no raio
 * @param hiveBonus bônus das colmeias, já limitado ao teto
 * @param multiplier multiplicador final da chance de crescer
 */
public record GrowthFactors(boolean sun, int skyLight, Biome.Precipitation precipitation, boolean sprinkler, boolean flowingWater,
	int varietyTypes, int hives, float hiveBonus, float multiplier) {

	public static GrowthFactors of(Level level, BlockPos pos, BlockState state) {
		CropConfig cfg = CropConfig.get();
		int skyLight = level.getBrightness(LightLayer.SKY, pos);
		boolean sun = skyLight >= cfg.minSkyLight;
		// já considera se está chovendo, se o céu está aberto, se tem bloco por cima (vidro barra) e o bioma
		Biome.Precipitation precipitation = level.precipitationAt(pos);
		boolean sprinkler = SprinklerIndex.isWatered(level, pos);
		boolean flowing = hasFlowingWater(level, pos.below());
		int variety = Math.min(cfg.varietyMaxTypes, countOtherCrops(level, pos, state));
		int hives = 0;
		float honey = 0.0F;
		if (level instanceof ServerLevel server) {
			// colmeia e ninho já são pontos de interesse do vanilla: a consulta olha só o índice, sem varrer blocos
			for (BlockPos hive : server.getPoiManager().getInRange(
				type -> type.is(PoiTypes.BEEHIVE) || type.is(PoiTypes.BEE_NEST), pos, cfg.hiveRadius, PoiManager.Occupancy.ANY)
				.map(r -> r.getPos()).toList()) {
				int honeyLevel = server.getBlockState(hive).getValueOrElse(BeehiveBlock.HONEY_LEVEL, 0);
				if (honeyLevel == 0) continue;
				hives++;
				honey += honeyLevel / (float) BeehiveBlock.MAX_HONEY_LEVELS;
			}
		}
		float hiveBonus = Math.min(cfg.hiveMaxBonus, honey * cfg.hiveBonusPerHive);

		float bonus = variety * cfg.varietyBonusPerType + hiveBonus;
		if (precipitation == Biome.Precipitation.RAIN || sprinkler) bonus += cfg.rainBonus;
		if (flowing) bonus += cfg.flowingWaterBonus;
		float multiplier = 1.0F + bonus;
		if (!sun) multiplier *= cfg.noSunMultiplier;
		if (precipitation == Biome.Precipitation.SNOW) multiplier *= cfg.snowMultiplier;
		multiplier = Math.min(multiplier, cfg.maxMultiplier);
		return new GrowthFactors(sun, skyLight, precipitation, sprinkler, flowing, variety, hives, hiveBonus, multiplier);
	}

	/**
	 * Pontos de qualidade deste momento: +1 chuva ou sprinkler, +1 água corrente, +1 variedade (+2 com o máximo de tipos),
	 * +1 colmeias (+2 no teto); −1 sem sol, −1 neve. A qualidade da colheita é a média ao longo do crescimento.
	 */
	public int points() {
		CropConfig cfg = CropConfig.get();
		int p = 0;
		if (precipitation == Biome.Precipitation.RAIN || sprinkler) p++;
		if (flowingWater) p++;
		if (varietyTypes > 0) p += varietyTypes >= cfg.varietyMaxTypes ? 2 : 1;
		if (hiveBonus > 0) p += hiveBonus >= cfg.hiveMaxBonus - 0.0001F ? 2 : 1;
		if (!sun) p--;
		if (precipitation == Biome.Precipitation.SNOW) p--;
		return p;
	}

	/** Mesmo alcance da hidratação vanilla (4 blocos para os lados, mesmo nível e um acima da terra), só água que não é fonte. */
	private static boolean hasFlowingWater(Level level, BlockPos farmland) {
		for (BlockPos p : BlockPos.betweenClosed(farmland.offset(-4, 0, -4), farmland.offset(4, 1, 4))) {
			FluidState fluid = level.getFluidState(p);
			if (fluid.is(FluidTags.WATER) && !fluid.isSource()) return true;
		}
		return false;
	}

	private static int countOtherCrops(Level level, BlockPos pos, BlockState state) {
		Block own = cropType(state.getBlock());
		Set<Block> types = new HashSet<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) continue;
				BlockState other = level.getBlockState(pos.offset(dx, 0, dz));
				if (!other.is(BetterVanillaCrops.CROPS)) continue;
				Block type = cropType(other.getBlock());
				if (type != own) types.add(type);
			}
		}
		return types.size();
	}

	/** Talo com fruta pendurada é a mesma cultura que o talo. */
	private static Block cropType(Block block) {
		if (block == Blocks.ATTACHED_MELON_STEM) return Blocks.MELON_STEM;
		if (block == Blocks.ATTACHED_PUMPKIN_STEM) return Blocks.PUMPKIN_STEM;
		return block;
	}
}
