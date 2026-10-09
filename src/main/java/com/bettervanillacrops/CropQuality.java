package com.bettervanillacrops;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Qualidade da colheita (normal, boa, excelente): a média dos {@link GrowthFactors#points() pontos} de cada estágio
 * que a planta cresceu, fechada quando ela amadurece. Boa rende +1 do produto e excelente +2. Fica num anexo do
 * chunk (posição → pontos), sem mexer no estado dos blocos vanilla. A planta excelente brilha.
 */
public final class CropQuality {
	private CropQuality() {
	}

	public enum Quality {
		NORMAL("normal"), GOOD("boa"), EXCELLENT("excelente");

		public final String label;

		Quality(String label) {
			this.label = label;
		}

		/** Produtos a mais na colheita. */
		public int extra() {
			return ordinal();
		}

		static Quality fromAverage(float average) {
			CropConfig cfg = CropConfig.get();
			if (average >= cfg.qualityExcellentAverage) return EXCELLENT;
			return average >= cfg.qualityGoodAverage ? GOOD : NORMAL;
		}
	}

	/** O que a planta dá: o produto (que a qualidade multiplica) e a semente (que o replantio gasta). */
	public record Product(Item product, Item seed) {
	}

	private static final Map<Block, Product> PRODUCTS = Map.of(
		Blocks.WHEAT, new Product(Items.WHEAT, Items.WHEAT_SEEDS),
		Blocks.CARROTS, new Product(Items.CARROT, Items.CARROT),
		Blocks.POTATOES, new Product(Items.POTATO, Items.POTATO),
		Blocks.BEETROOTS, new Product(Items.BEETROOT, Items.BEETROOT_SEEDS));

	public static final AttachmentType<Data> DATA = AttachmentRegistry.create(BetterVanillaCrops.id("quality"),
		b -> b.persistent(Data.CODEC).initializer(Data::new));

	/** Planta de comida que tem qualidade (trigo, cenoura, batata, beterraba), ou {@code null}. */
	public static Product product(BlockState state) {
		return PRODUCTS.get(state.getBlock());
	}

	public static boolean isMature(BlockState state) {
		return product(state) != null && state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
	}

	/** Chamado quando o sorteio manda a planta crescer um estágio: soma os pontos do momento. */
	static void onGrow(ServerLevel level, BlockPos pos, BlockState state, GrowthFactors factors) {
		if (product(state) == null || !(state.getBlock() instanceof CropBlock crop)) return;
		int age = crop.getAge(state);
		LevelChunk chunk = level.getChunkAt(pos);
		Data data = chunk.getAttachedOrCreate(DATA);
		// idade 0 é planta nova: o que tinha nessa posição era de outra plantação
		Entry e = age == 0 ? new Entry() : data.entries.computeIfAbsent(pos.asLong(), k -> new Entry());
		data.entries.put(pos.asLong(), e);
		e.sum += factors.points();
		e.samples++;
		if (age + 1 >= crop.getMaxAge()) e.quality = (byte) Quality.fromAverage(e.average()).ordinal();
		chunk.markUnsaved();
	}

	/** Qualidade da planta nessa posição. Sem histórico (farinha de osso, planta de antes do mod), vale o momento. */
	public static Quality quality(ServerLevel level, BlockPos pos, BlockState state) {
		Entry e = entry(level, pos);
		if (e != null && e.quality >= 0) return Quality.values()[e.quality];
		if (e != null && e.samples > 0) return Quality.fromAverage(e.average());
		return Quality.fromAverage(GrowthFactors.of(level, pos, state).points());
	}

	/** Média de pontos e quantos estágios entraram nela, para o {@code /bvc}. */
	public static Entry entry(Level level, BlockPos pos) {
		Data data = level.getChunkAt(pos).getAttached(DATA);
		return data == null ? null : data.entries.get(pos.asLong());
	}

	/**
	 * Planta madura sendo colhida (quebrada por jogador, aldeão, pistão ou água): soma os produtos da qualidade e
	 * esquece a posição.
	 */
	public static void onDrops(ServerLevel level, BlockPos pos, BlockState state, List<ItemStack> drops) {
		Product product = product(state);
		if (product == null || !isMature(state)) return;
		int extra = quality(level, pos, state).extra();
		if (extra > 0) drops.add(new ItemStack(product.product(), extra));
		forget(level, pos);
	}

	public static void forget(ServerLevel level, BlockPos pos) {
		LevelChunk chunk = level.getChunkAt(pos);
		Data data = chunk.getAttached(DATA);
		if (data != null && data.entries.remove(pos.asLong()) != null) {
			if (data.entries.isEmpty()) chunk.removeAttached(DATA);
			chunk.markUnsaved();
		}
	}

	/** A cada meio segundo, brilho dourado nas plantas excelentes maduras perto dos jogadores. */
	static void tick(MinecraftServer server) {
		if (server.getTickCount() % 10 != 0) return;
		for (ServerLevel level : server.getAllLevels()) {
			LongOpenHashSet seen = new LongOpenHashSet();
			for (ServerPlayer player : level.players()) {
				ChunkPos center = player.chunkPosition();
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						int cx = center.x() + dx, cz = center.z() + dz;
						if (!seen.add(ChunkPos.pack(cx, cz))) continue;
						LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
						if (chunk != null) sparkle(level, chunk);
					}
				}
			}
		}
	}

	private static void sparkle(ServerLevel level, LevelChunk chunk) {
		Data data = chunk.getAttached(DATA);
		if (data == null) return;
		var it = data.entries.long2ObjectEntrySet().iterator();
		while (it.hasNext()) {
			Long2ObjectMap.Entry<Entry> e = it.next();
			BlockPos pos = BlockPos.of(e.getLongKey());
			BlockState state = level.getBlockState(pos);
			if (product(state) == null) {
				// a planta sumiu sem ser colhida (modo criativo, explosão sem drop): limpa
				it.remove();
				chunk.markUnsaved();
				continue;
			}
			if (e.getValue().quality == Quality.EXCELLENT.ordinal() && isMature(state) && level.getRandom().nextFloat() < 0.5F) {
				level.sendParticles(ParticleTypes.WAX_ON, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.3, 0.25, 0.3, 0.0);
			}
		}
	}

	public static final class Entry {
		public float sum;
		public int samples;
		/** Qualidade fechada ao amadurecer, ou −1 enquanto cresce. */
		public byte quality = -1;

		public float average() {
			return samples == 0 ? 0 : sum / samples;
		}
	}

	private record Saved(long pos, float sum, int samples, byte quality) {
		static final Codec<Saved> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("p").forGetter(Saved::pos),
			Codec.FLOAT.fieldOf("s").forGetter(Saved::sum),
			Codec.INT.fieldOf("n").forGetter(Saved::samples),
			Codec.BYTE.fieldOf("q").forGetter(Saved::quality)
		).apply(i, Saved::new));
	}

	public static final class Data {
		static final Codec<Data> CODEC = Saved.CODEC.listOf().xmap(Data::fromList, Data::toList);

		final Long2ObjectOpenHashMap<Entry> entries = new Long2ObjectOpenHashMap<>();

		private static Data fromList(List<Saved> list) {
			Data d = new Data();
			for (Saved s : list) {
				Entry e = new Entry();
				e.sum = s.sum();
				e.samples = s.samples();
				e.quality = s.quality();
				d.entries.put(s.pos(), e);
			}
			return d;
		}

		private List<Saved> toList() {
			List<Saved> out = new ArrayList<>(entries.size());
			for (Long2ObjectMap.Entry<Entry> e : entries.long2ObjectEntrySet()) {
				Entry v = e.getValue();
				out.add(new Saved(e.getLongKey(), v.sum, v.samples, v.quality));
			}
			return out;
		}
	}
}
