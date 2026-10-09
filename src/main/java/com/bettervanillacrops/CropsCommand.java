package com.bettervanillacrops;

import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * {@code /bvc}: olhando para uma planta (ou para a terra embaixo dela), mostra no chat os fatores que estão valendo
 * e o multiplicador final. Só lê, então não precisa de permissão de comandos.
 */
public final class CropsCommand {
	private CropsCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("bvc").executes(CropsCommand::inspect));
	}

	private static int inspect(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		CommandSourceStack src = c.getSource();
		ServerPlayer player = src.getPlayerOrException();
		ServerLevel level = src.getLevel();
		HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return none(src);

		BlockPos pos = blockHit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		if (!state.is(BetterVanillaCrops.CROPS)) {
			pos = pos.above();
			state = level.getBlockState(pos);
			if (!state.is(BetterVanillaCrops.CROPS)) return none(src);
		}

		GrowthFactors f = GrowthFactors.of(level, pos, state);
		CropConfig cfg = CropConfig.get();
		MutableComponent msg = Component.empty()
			.append(state.getBlock().getName().withStyle(ChatFormatting.GOLD))
			.append(Component.literal(" — crescimento ×" + fmt(f.multiplier())).withStyle(color(f.multiplier())));
		line(msg, "Sol", f.sun() ? "sim" : "não (×" + fmt(cfg.noSunMultiplier) + ")", f.sun());
		String sky = switch (f.precipitation()) {
			case RAIN -> "chuva (+" + pct(cfg.rainBonus) + ")";
			case SNOW -> "neve (×" + fmt(cfg.snowMultiplier) + ")";
			case NONE -> "nada caindo";
		};
		line(msg, "Céu", sky, f.precipitation() != Biome.Precipitation.SNOW);
		line(msg, "Sprinkler", f.sprinkler() ? "regando (+" + pct(cfg.rainBonus) + ", não soma com a chuva)" : "não", true);
		line(msg, "Água corrente", f.flowingWater() ? "sim (+" + pct(cfg.flowingWaterBonus) + ")" : "não", true);
		line(msg, "Variedade", f.varietyTypes() + " tipo(s) vizinho(s) (+" + pct(f.varietyTypes() * cfg.varietyBonusPerType) + ")", true);
		line(msg, "Colmeias", f.hives() + " com mel (+" + pct(f.hiveBonus()) + ")", true);
		if (CropQuality.product(state) != null) {
			CropQuality.Entry e = CropQuality.entry(level, pos);
			String history = e == null || e.samples == 0 ? "sem histórico, vale o agora: " + f.points() + " ponto(s)"
				: "média " + fmt(e.average()) + " ponto(s) em " + e.samples + " estágio(s)";
			String q = CropQuality.isMature(state) ? CropQuality.quality(level, pos, state).label : "crescendo";
			line(msg, "Qualidade", q + " (" + history + ")", true);
		}
		src.sendSuccess(() -> msg, false);
		return 1;
	}

	private static void line(MutableComponent msg, String label, String value, boolean good) {
		msg.append(Component.literal("\n  " + label + ": ").withStyle(ChatFormatting.GRAY))
			.append(Component.literal(value).withStyle(good ? ChatFormatting.WHITE : ChatFormatting.RED));
	}

	private static ChatFormatting color(float multiplier) {
		if (multiplier > 1.0F) return ChatFormatting.GREEN;
		return multiplier < 1.0F ? ChatFormatting.RED : ChatFormatting.WHITE;
	}

	private static String fmt(float v) {
		return String.format(Locale.ROOT, "%.2f", v).replace('.', ',');
	}

	private static String pct(float v) {
		return Math.round(v * 100) + "%";
	}

	private static int none(CommandSourceStack src) {
		src.sendFailure(Component.literal("Olhe para uma plantação (ou para a terra embaixo dela)."));
		return 0;
	}
}
