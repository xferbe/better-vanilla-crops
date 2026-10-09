package com.bettervanillacrops.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.bettervanillacrops.CropQuality;
import com.bettervanillacrops.GrowthFactors;
import com.bettervanillacrops.Harvest;
import com.bettervanillacrops.irrigation.ModBlocks;
import com.bettervanillacrops.irrigation.SprinklerBlock;
import com.bettervanillacrops.irrigation.WaterTankBlock;
import com.bettervanillacrops.irrigation.WaterTankBlockEntity;
import com.mojang.logging.LogUtils;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import com.bettervanillacrops.BetterVanillaCrops;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mundo plano com uma horta de teste a cada 12 blocos no eixo x (cada uma num quadrado 3×3 de terra molhada):
 * <ol>
 *   <li>x = 0: céu aberto;</li>
 *   <li>x = 12: teto de pedra → sem sol;</li>
 *   <li>x = 24: teto de vidro → sol, mas sem chuva;</li>
 *   <li>x = 36: trigo cercado de cenoura, batata, beterraba e talo de melancia → variedade (máx. 3);</li>
 *   <li>x = 48: valeta com água corrente do lado;</li>
 *   <li>x = 60: bioma de planície nevada → neve quando chove.</li>
 *   <li>x = 120: três colmeias cheias, uma pela metade, uma vazia (não conta) e uma cheia fora do raio;</li>
 *   <li>x = 180: sete colmeias cheias → bate no teto do bônus.</li>
 *   <li>x = 300: tanque com sprinkler no meio de terra seca; trigo dentro (x = 303) e fora (x = 305) do alcance;</li>
 *   <li>x = 240: variedade máxima + colmeias no teto (4 pontos) → colheita excelente; o céu aberto (x = 0) é normal.</li>
 * </ol>
 * Confere os multiplicadores com tempo limpo e com chuva, e sorteia o crescimento de verdade (tique aleatório
 * chamado na mão) no céu aberto e sob o teto para ver que o gancho no vanilla está funcionando.
 */
public class BetterVanillaCropsClientTest implements FabricClientGameTest {
	private static final int Y = -61;
	private static final float EPS = 0.001F;

	private static String cmd(String f, Object... a) {
		return String.format(Locale.ROOT, f, a);
	}

	private static BlockPos crop(int x) {
		return new BlockPos(x, Y + 1, 0);
	}

	@Override
	public void runTest(ClientGameTestContext ctx) {
		List<String> failures = new ArrayList<>();
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			TestServerContext server = sp.getServer();
			ctx.waitTicks(20);
			server.runCommand("time set noon");
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("gamerule random_tick_speed 0");
			server.runCommand("weather clear");

			server.runCommand("forceload add 100 -20 320 20");
			for (int x : new int[] {0, 12, 24, 36, 48, 60, 120, 180, 240}) {
				server.runCommand(cmd("fill %d %d -1 %d %d 1 farmland[moisture=7]", x - 1, Y, x + 1, Y));
				server.runCommand(cmd("setblock %d %d 0 wheat", x, Y + 1));
			}
			server.runCommand(cmd("fill 10 %d -2 14 %d 2 stone", Y + 3, Y + 3));
			server.runCommand(cmd("fill 22 %d -2 26 %d 2 glass", Y + 3, Y + 3));
			server.runCommand(cmd("setblock 35 %d -1 carrots", Y + 1));
			server.runCommand(cmd("setblock 37 %d -1 potatoes", Y + 1));
			server.runCommand(cmd("setblock 35 %d 1 beetroots", Y + 1));
			server.runCommand(cmd("setblock 37 %d 1 melon_stem", Y + 1));
			server.runCommand(cmd("fill 46 %d 3 50 %d 3 air", Y, Y));
			server.runCommand(cmd("setblock 46 %d 3 water", Y));
			server.runCommand(cmd("fillbiome 56 -64 -4 64 -40 4 snowy_plains"));
			for (int i = 0; i < 3; i++) server.runCommand(cmd("setblock %d %d 6 beehive[honey_level=5]", 117 + 3 * i, Y + 1));
			server.runCommand(cmd("setblock 117 %d -6 beehive[honey_level=2]", Y + 1));
			server.runCommand(cmd("setblock 120 %d -6 beehive[honey_level=0]", Y + 1));
			server.runCommand(cmd("setblock 140 %d 0 beehive[honey_level=5]", Y + 1));
			for (int i = 0; i < 7; i++) server.runCommand(cmd("setblock %d %d 8 bee_nest[honey_level=5]", 171 + 3 * i, Y + 1));
			for (String c : new String[] {"239 %d -1 carrots", "241 %d -1 potatoes", "239 %d 1 beetroots", "241 %d 1 melon_stem"}) {
				server.runCommand(cmd("setblock " + c, Y + 1));
			}
			for (int i = 0; i < 5; i++) server.runCommand(cmd("setblock %d %d 6 beehive[honey_level=5]", 234 + 3 * i, Y + 1));
			ctx.waitTicks(40);

			check(server, failures, "céu aberto", 0, 1.0F);
			check(server, failures, "teto de pedra", 12, 0.4F);
			check(server, failures, "teto de vidro", 24, 1.0F);
			check(server, failures, "variedade", 36, 1.3F);
			check(server, failures, "água corrente", 48, 1.1F);
			check(server, failures, "bioma nevado sem chuva", 60, 1.0F);
			check(server, failures, "colmeias (3 cheias + 1 pela metade)", 120, 1.0F + 3.4F * 0.03F);
			check(server, failures, "colmeias no teto", 180, 1.15F);

			double open = growthRate(server, 0);
			double roofed = growthRate(server, 12);
			LogUtils.getLogger().info("[teste] taxa de crescimento: céu aberto {} / teto {}", open, roofed);
			if (open < 0.28 || open > 0.39) failures.add("taxa no céu aberto fora do esperado (~0,33): " + open);
			if (roofed < 0.09 || roofed > 0.18) failures.add("taxa sob o teto fora do esperado (~0,13): " + roofed);

			qualityTests(server, failures);
			sprinklerTests(ctx, server, failures);
			columnTests(ctx, server, failures);

			server.runCommand("gamemode spectator @a");
			server.runCommand(cmd("tp @a 30 %d -24 0 45", Y + 26));
			ctx.waitTicks(20);
			ctx.takeScreenshot("01-hortas");
			server.runCommand(cmd("tp @a 240.5 %.1f -1.2 0 50", Y + 2.6));
			ctx.waitTicks(30);
			ctx.takeScreenshot("03-trigo-excelente");
			server.runCommand(cmd("tp @a 300.5 %.1f -4.5 0 35", Y + 4.5));
			ctx.waitTicks(15);
			ctx.takeScreenshot("04-sprinkler");
			// tanques de mostra acima do chão, só para ver a água no vidro (vazio, 1/3, 2/3, cheio)
			int[] levels = {0, 5, 10, 15};
			for (int i = 0; i < levels.length; i++) {
				server.runCommand(cmd("setblock %d %d 8 better_vanilla_crops:water_tank[level=%d]", 297 + 2 * i, Y + 1, levels[i]));
			}
			// e uma coluna de 4 tanques com 12 baldes: dois cheios, um com 2/5 e o de cima vazio
			for (int y = 1; y <= 4; y++) server.runCommand(cmd("setblock 306 %d 8 better_vanilla_crops:water_tank", Y + y));
			server.runOnServer(s -> WaterTankBlockEntity.fillColumn(s.overworld(), new BlockPos(306, Y + 4, 8), 12 * WaterTankBlockEntity.BUCKET));
			server.runCommand(cmd("tp @a 301.5 %.1f 1.0 0 10", Y + 3.0));
			ctx.waitTicks(15);
			ctx.takeScreenshot("05-tanques");
			server.runCommand(cmd("tp @a 302.2 %.1f -2.0 45 30", Y + 2.6));
			ctx.waitTicks(10);
			ctx.takeScreenshot("06-sprinkler-de-perto");

			server.runCommand("weather rain");
			ctx.waitTicks(120);
			check(server, failures, "céu aberto na chuva", 0, 1.25F);
			check(server, failures, "teto de pedra na chuva", 12, 0.4F);
			check(server, failures, "teto de vidro na chuva", 24, 1.0F);
			check(server, failures, "variedade na chuva", 36, 1.55F);
			check(server, failures, "neve", 60, 0.6F);
			sprinklerInRain(ctx, server, failures);
			ctx.takeScreenshot("02-hortas-na-chuva");
		}
		if (!failures.isEmpty()) throw new AssertionError(String.join("\n", failures));
	}

	/**
	 * Cresce do zero na horta excelente (x = 240) e na normal (x = 0), confere a qualidade fechada, colhe com botão
	 * direito (replanta e solta 1 + 2 trigos) e, depois de crescer de novo, quebra e confere os drops.
	 */
	private static void qualityTests(TestServerContext server, List<String> failures) {
		String result = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			List<String> out = new ArrayList<>();
			RandomSource random = RandomSource.create(7);
			BlockPos excellent = crop(240), normal = crop(0);
			for (BlockPos pos : new BlockPos[] {excellent, normal}) growToMature(level, pos, random);

			CropQuality.Entry e = CropQuality.entry(level, excellent);
			LogUtils.getLogger().info("[teste] horta excelente: média {} em {} estágios, qualidade {}", e.average(), e.samples, e.quality);
			if (e.samples != 7) out.add("esperava 7 estágios de crescimento, veio " + e.samples);
			if (CropQuality.quality(level, excellent, level.getBlockState(excellent)) != CropQuality.Quality.EXCELLENT) out.add("horta x=240 deveria ser excelente");
			if (CropQuality.quality(level, normal, level.getBlockState(normal)) != CropQuality.Quality.NORMAL) out.add("horta x=0 deveria ser normal");

			// botão direito na excelente: replanta e solta 3 trigos e pelo menos 1 semente
			ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
			Harvest.onUse(player, level, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(excellent), Direction.UP, excellent, false));
			int age = level.getBlockState(excellent).getValue(CropBlock.AGE);
			int wheat = collect(level, excellent, Items.WHEAT), seeds = collect(level, excellent, Items.WHEAT_SEEDS);
			LogUtils.getLogger().info("[teste] colheita com botão direito: idade {}, trigo {}, sementes {}", age, wheat, seeds);
			if (age != 0) out.add("depois de colher, a planta deveria voltar à idade 0 (veio " + age + ")");
			if (wheat != 3) out.add("colheita excelente com botão direito deveria dar 3 trigos, deu " + wheat);
			if (seeds < 1) out.add("colher com botão direito deveria dar pelo menos 1 semente");
			if (CropQuality.entry(level, excellent) != null) out.add("a posição colhida deveria ser esquecida");

			// cresce de novo e quebra: a excelente dá 3 trigos, a normal 1
			growToMature(level, excellent, random);
			level.destroyBlock(excellent, true);
			level.destroyBlock(normal, true);
			int brokenExcellent = collect(level, excellent, Items.WHEAT), brokenNormal = collect(level, normal, Items.WHEAT);
			LogUtils.getLogger().info("[teste] quebrando: excelente {} trigo(s), normal {}", brokenExcellent, brokenNormal);
			if (brokenExcellent != 3) out.add("quebrar a excelente deveria dar 3 trigos, deu " + brokenExcellent);
			if (brokenNormal != 1) out.add("quebrar a normal deveria dar 1 trigo, deu " + brokenNormal);

			// replanta e deixa madura para o screenshot do brilho
			level.setBlock(excellent, Block.byItem(Items.WHEAT_SEEDS).defaultBlockState(), 2);
			growToMature(level, excellent, random);
			level.setBlock(normal, Block.byItem(Items.WHEAT_SEEDS).defaultBlockState(), 2);
			return String.join("\n", out);
		});
		if (!result.isEmpty()) failures.add(result);
	}

	private static final BlockPos TANK = new BlockPos(300, -61, 0);
	private static final BlockPos SPRINKLER = TANK.above();

	/** Tanque + sprinkler numa horta seca: liga, rega como chuva, molha a terra, gasta água e desconta a noite pulada. */
	private static void sprinklerTests(ClientGameTestContext ctx, TestServerContext server, List<String> failures) {
		server.runCommand(cmd("fill 295 %d -5 306 %d 5 farmland[moisture=0]", Y, Y));
		server.runCommand(cmd("setblock 300 %d 0 better_vanilla_crops:water_tank", Y));
		server.runCommand(cmd("setblock 300 %d 0 better_vanilla_crops:sprinkler", Y + 1));
		server.runCommand(cmd("setblock 303 %d 0 wheat", Y + 1));
		server.runCommand(cmd("setblock 305 %d 0 wheat", Y + 1));
		String filled = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			List<String> out = new ArrayList<>();
			for (String recipe : new String[] {"water_tank", "sprinkler"}) {
				if (s.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, BetterVanillaCrops.id(recipe))).isEmpty()) out.add("receita " + recipe + " não carregou");
			}
			ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
			for (int i = 0; i < 2; i++) {
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
				level.getBlockState(TANK).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(TANK), Direction.NORTH, TANK, false));
			}
			long water = tank(level).water();
			int shown = level.getBlockState(TANK).getValue(WaterTankBlock.LEVEL);
			LogUtils.getLogger().info("[teste] tanque com 2 baldes: {} tiques, nível desenhado {}", water, shown);
			if (water != 2 * WaterTankBlockEntity.BUCKET) out.add("2 baldes deveriam dar " + 2 * WaterTankBlockEntity.BUCKET + " tiques de água, deu " + water);
			if (shown != 6) out.add("tanque com 2 de 5 baldes deveria desenhar nível 6, desenhou " + shown);
			return String.join("\n", out);
		});
		if (!filled.isEmpty()) failures.add(filled);
		ctx.waitTicks(5);

		String watering = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			List<String> out = new ArrayList<>();
			if (!level.getBlockState(SPRINKLER).getValue(SprinklerBlock.ACTIVE)) out.add("sprinkler com água e sem chuva deveria estar ligado");
			BlockPos inside = new BlockPos(303, Y + 1, 0), outside = new BlockPos(305, Y + 1, 0);
			GrowthFactors in = GrowthFactors.of(level, inside, level.getBlockState(inside));
			GrowthFactors outF = GrowthFactors.of(level, outside, level.getBlockState(outside));
			LogUtils.getLogger().info("[teste] sprinkler: dentro {} / fora {}", in, outF);
			if (!in.sprinkler() || Math.abs(in.multiplier() - 1.25F) > EPS) out.add("trigo no alcance deveria ganhar o bônus da chuva: " + in);
			if (outF.sprinkler() || Math.abs(outF.multiplier() - 1.0F) > EPS) out.add("trigo fora do alcance não deveria ganhar nada: " + outF);
			RandomSource random = RandomSource.create(3);
			for (BlockPos crop : new BlockPos[] {inside, outside}) level.getBlockState(crop.below()).randomTick(level, crop.below(), random);
			int wet = level.getBlockState(inside.below()).getValue(FarmlandBlock.MOISTURE);
			int dry = level.getBlockState(outside.below()).getValue(FarmlandBlock.MOISTURE);
			LogUtils.getLogger().info("[teste] umidade da terra: dentro {} / fora {}", wet, dry);
			if (wet != 7) out.add("terra no alcance do sprinkler deveria ficar molhada (7), ficou " + wet);
			if (dry != 0) out.add("terra fora do alcance deveria continuar seca, ficou " + dry);
			return String.join("\n", out);
		});
		if (!watering.isEmpty()) failures.add(watering);

		long before = server.computeOnServer(s -> tank(s.overworld()).water());
		ctx.waitTicks(40);
		long after = server.computeOnServer(s -> tank(s.overworld()).water());
		LogUtils.getLogger().info("[teste] gasto em 40 tiques: {}", before - after);
		if (before - after < 30 || before - after > 50) failures.add("em 40 tiques deveria gastar ~40 de água, gastou " + (before - after));

		server.runCommand("time add 1000");
		ctx.waitTicks(2);
		long skipped = server.computeOnServer(s -> tank(s.overworld()).water());
		LogUtils.getLogger().info("[teste] gasto com 1000 tiques pulados: {}", after - skipped);
		if (after - skipped < 1000) failures.add("pular 1000 tiques do dia deveria gastar pelo menos 1000 de água, gastou " + (after - skipped));
	}

	/**
	 * Coluna de 3 tanques com sprinkler em cima: os blocos se reconhecem, 7 baldes enchem de baixo para cima, o
	 * sprinkler bebe do bloco do meio (o de cima está vazio) e, quebrando o do meio, a coluna se divide.
	 */
	private static void columnTests(ClientGameTestContext ctx, TestServerContext server, List<String> failures) {
		BlockPos bottom = new BlockPos(312, Y + 1, -8), middle = bottom.above(), top = middle.above();
		for (BlockPos p : new BlockPos[] {bottom, middle, top}) {
			server.runCommand(cmd("setblock %d %d %d better_vanilla_crops:water_tank", p.getX(), p.getY(), p.getZ()));
		}
		server.runCommand(cmd("setblock %d %d %d better_vanilla_crops:sprinkler", top.getX(), top.getY() + 1, top.getZ()));
		String filled = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			List<String> out = new ArrayList<>();
			BlockState b = level.getBlockState(bottom), m = level.getBlockState(middle), t = level.getBlockState(top);
			if (b.getValue(WaterTankBlock.DOWN) || !b.getValue(WaterTankBlock.UP)) out.add("tanque de baixo deveria ligar só para cima: " + b);
			if (!m.getValue(WaterTankBlock.DOWN) || !m.getValue(WaterTankBlock.UP)) out.add("tanque do meio deveria ligar para os dois lados: " + m);
			if (!t.getValue(WaterTankBlock.DOWN) || t.getValue(WaterTankBlock.UP)) out.add("tanque de cima deveria ligar só para baixo: " + t);
			ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
			for (int i = 0; i < 7; i++) {
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
				level.getBlockState(middle).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(middle), Direction.NORTH, middle, false));
			}
			long wb = water(level, bottom), wm = water(level, middle), wt = water(level, top);
			LogUtils.getLogger().info("[teste] coluna com 7 baldes: baixo {}, meio {}, cima {}", wb, wm, wt);
			if (wb != WaterTankBlockEntity.CAPACITY || wm != 2 * WaterTankBlockEntity.BUCKET || wt != 0) {
				out.add("7 baldes deveriam encher o de baixo (5) e pôr 2 no do meio, veio " + wb + " / " + wm + " / " + wt);
			}
			return String.join("\n", out);
		});
		if (!filled.isEmpty()) failures.add(filled);

		ctx.waitTicks(40);
		String drained = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			List<String> out = new ArrayList<>();
			boolean on = level.getBlockState(top.above()).getValue(SprinklerBlock.ACTIVE);
			long used = 2 * WaterTankBlockEntity.BUCKET - water(level, middle);
			LogUtils.getLogger().info("[teste] coluna: sprinkler ligado={}, gasto do meio {}, baixo {}", on, used, water(level, bottom));
			if (!on) out.add("sprinkler em cima de coluna com água embaixo deveria ligar");
			if (used < 30 || used > 50) out.add("o sprinkler deveria beber do tanque do meio (~40 em 40 tiques), bebeu " + used);
			if (water(level, bottom) != WaterTankBlockEntity.CAPACITY) out.add("o tanque de baixo não deveria perder água antes do de cima");

			level.destroyBlock(middle, false);
			BlockState b = level.getBlockState(bottom), t = level.getBlockState(top);
			if (b.getValue(WaterTankBlock.UP) || t.getValue(WaterTankBlock.DOWN)) out.add("quebrando o do meio, a coluna deveria se dividir: " + b + " / " + t);
			if (water(level, bottom) != WaterTankBlockEntity.CAPACITY) out.add("o tanque de baixo deveria manter a água depois de dividir");
			return String.join("\n", out);
		});
		if (!drained.isEmpty()) failures.add(drained);
	}

	private static long water(ServerLevel level, BlockPos pos) {
		return ((WaterTankBlockEntity) level.getBlockEntity(pos)).water();
	}

	/** Chovendo no sprinkler: desliga e para de gastar. */
	private static void sprinklerInRain(ClientGameTestContext ctx, TestServerContext server, List<String> failures) {
		long before = server.computeOnServer(s -> tank(s.overworld()).water());
		ctx.waitTicks(20);
		String result = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			List<String> out = new ArrayList<>();
			if (level.getBlockState(SPRINKLER).getValue(SprinklerBlock.ACTIVE)) out.add("sprinkler deveria desligar na chuva");
			long now = tank(level).water();
			LogUtils.getLogger().info("[teste] chuva: sprinkler ligado={}, gasto em 20 tiques {}", level.getBlockState(SPRINKLER).getValue(SprinklerBlock.ACTIVE), before - now);
			if (now != before) out.add("na chuva o tanque não deveria gastar, gastou " + (before - now));
			return String.join("\n", out);
		});
		if (!result.isEmpty()) failures.add(result);
	}

	private static WaterTankBlockEntity tank(ServerLevel level) {
		return (WaterTankBlockEntity) level.getBlockEntity(TANK);
	}

	private static void growToMature(ServerLevel level, BlockPos pos, RandomSource random) {
		for (int i = 0; i < 5000; i++) {
			BlockState state = level.getBlockState(pos);
			if (((CropBlock) state.getBlock()).isMaxAge(state)) return;
			state.randomTick(level, pos, random);
		}
	}

	/** Soma e remove os itens de um tipo jogados no chão em volta. */
	private static int collect(ServerLevel level, BlockPos pos, Item item) {
		int total = 0;
		for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2))) {
			if (!entity.getItem().is(item)) continue;
			total += entity.getItem().getCount();
			entity.discard();
		}
		return total;
	}

	private static void check(TestServerContext server, List<String> failures, String name, int x, float expected) {
		GrowthFactors f = server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			return GrowthFactors.of(level, crop(x), level.getBlockState(crop(x)));
		});
		LogUtils.getLogger().info("[teste] {}: {}", name, f);
		if (Math.abs(f.multiplier() - expected) > EPS) failures.add(name + ": esperado ×" + expected + ", veio " + f);
	}

	/** Chama o tique aleatório da planta muitas vezes, voltando para a idade 0 sempre que ela cresce. */
	private static double growthRate(TestServerContext server, int x) {
		return server.computeOnServer(s -> {
			ServerLevel level = s.overworld();
			BlockPos pos = crop(x);
			RandomSource random = RandomSource.create(42);
			BlockState young = level.getBlockState(pos).setValue(CropBlock.AGE, 0);
			int grew = 0;
			int trials = 4000;
			for (int i = 0; i < trials; i++) {
				level.setBlock(pos, young, 2);
				young.randomTick(level, pos, random);
				if (level.getBlockState(pos).getValue(CropBlock.AGE) > 0) grew++;
			}
			level.setBlock(pos, young, 2);
			return (double) grew / trials;
		});
	}
}
