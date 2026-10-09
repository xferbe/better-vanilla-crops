import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import javax.imageio.ImageIO;

/**
 * Gera as texturas do tanque (vidro com moldura de cobre e a água animada), os modelos dele — um para cada
 * combinação de altura da água (0 a 15) e de tanque encostado em cima e embaixo — e o blockstate. Numa coluna de
 * tanques a moldura horizontal e a tampa só aparecem nas pontas, então a coluna parece um tanque só.
 * Rodar da raiz do projeto: {@code java tools/GenAssets.java}.
 */
public class GenAssets {
	static final Path ASSETS = Path.of("src/main/resources/assets/better_vanilla_crops");

	public static void main(String[] args) throws IOException {
		Files.createDirectories(ASSETS.resolve("textures/block"));
		Files.createDirectories(ASSETS.resolve("models/block"));
		Files.createDirectories(ASSETS.resolve("blockstates"));
		// tampa e fundo: moldura nos quatro lados
		texture("water_tank", true, true);
		// laterais: a moldura de cima só existe sem tanque em cima, a de baixo só sem tanque embaixo
		texture("water_tank_side", true, true);
		texture("water_tank_side_top", true, false);
		texture("water_tank_side_bottom", false, true);
		texture("water_tank_side_middle", false, false);
		water();
		Files.createDirectories(ASSETS.resolve("textures/item"));
		for (int level = 0; level <= 5; level++) bucket(level);
		try (var old = Files.list(ASSETS.resolve("models/block"))) {
			for (Path p : old.toList()) if (p.getFileName().toString().startsWith("water_tank_")) Files.delete(p);
		}
		StringBuilder variants = new StringBuilder();
		for (boolean down : new boolean[] {false, true}) {
			for (boolean up : new boolean[] {false, true}) {
				for (int level = 0; level <= 15; level++) {
					String name = modelName(down, up, level);
					Files.writeString(ASSETS.resolve("models/block/" + name + ".json"), tankModel(down, up, level));
					if (!variants.isEmpty()) variants.append(",\n");
					variants.append(String.format(Locale.ROOT, "\t\t\"down=%s,level=%d,up=%s\": { \"model\": \"better_vanilla_crops:block/%s\" }",
						down, level, up, name));
				}
			}
		}
		Files.writeString(ASSETS.resolve("blockstates/water_tank.json"), "{\n\t\"variants\": {\n" + variants + "\n\t}\n}\n");
	}

	static String modelName(boolean down, boolean up, int level) {
		String part = down && up ? "middle" : down ? "top" : up ? "bottom" : "single";
		return "water_tank_" + part + "_" + level;
	}

	static int argb(int a, int rgb) {
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	/**
	 * Vidro levemente azulado e translúcido com moldura de cobre. As bordas verticais sempre existem; a de cima e a de
	 * baixo só quando pedidas, e os rebites só nos cantos onde duas bordas se encontram.
	 */
	static void texture(String name, boolean top, boolean bottom) throws IOException {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				boolean side = x == 0 || x == 15;
				boolean horizontal = (top && y == 0) || (bottom && y == 15);
				boolean corner = (x < 2 || x > 13) && ((top && y < 2) || (bottom && y > 13));
				int c;
				if (corner) {
					boolean rivet = (x == 1 || x == 14) && (y == 1 || y == 14);
					c = argb(255, rivet ? 0xFC9982 : 0x9C4529);
				} else if (horizontal) {
					c = argb(255, y == 0 ? 0xE77C56 : 0x9C4529);
				} else if (side) {
					c = argb(255, x == 0 ? 0xE77C56 : 0x9C4529);
				} else if (x + y == 6 || x + y == 7 || x + y == 11) {
					c = argb(120, 0xFFFFFF); // reflexo do vidro
				} else {
					c = argb(45, 0xD6F1F5);
				}
				img.setRGB(x, y, c);
			}
		}
		ImageIO.write(img, "png", ASSETS.resolve("textures/block/" + name + ".png").toFile());
	}

	/** Desenho do balde: O contorno, H brilho, C cobre, D sombra, I boca, G visor de vidro. */
	static final String[] BUCKET = {
		"................",
		"................",
		".....OOOOOO.....",
		"....O......O....",
		"...O........O...",
		"..OOOOOOOOOOOO..",
		"..OIIIIIIIIIIO..",
		"..OHCCCCCCCCDO..",
		"...OHCCGGCCDO...",
		"...OHCCGGCCDO...",
		"...OHCCGGCCDO...",
		"...OHCCGGCCDO...",
		"....OHCGGCDO....",
		"....OOOOOOOO....",
		"................",
		"................",
	};

	/**
	 * Balde de cobre com {@code level} baldes de água (0 = vazio). A boca fica azul com qualquer água, e o visor de
	 * vidro na frente acende de baixo para cima, uma linha por balde.
	 */
	static void bucket(int level) throws IOException {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		// linhas do visor de baixo (12) para cima (8): o nível n acende as n de baixo
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				char c = BUCKET[y].charAt(x);
				int color = switch (c) {
					case 'O' -> argb(255, 0x6E3420);
					case 'H' -> argb(255, 0xFC9982);
					case 'C' -> argb(255, 0xE77C56);
					case 'D' -> argb(255, 0xA54E2E);
					case 'I' -> level > 0 ? argb(255, (x + y) % 3 == 0 ? 0x5A8FF0 : 0x3F76E4) : argb(255, 0x4A2416);
					case 'G' -> 12 - y < level ? argb(255, x == 7 ? 0x5A8FF0 : 0x3F76E4) : argb(255, x == 7 ? 0xD6F1F5 : 0xA9C9CF);
					default -> 0;
				};
				img.setRGB(x, y, color);
			}
		}
		String name = level == 0 ? "copper_bucket" : "water_copper_bucket_" + level;
		ImageIO.write(img, "png", ASSETS.resolve("textures/item/" + name + ".png").toFile());
	}

	/** Plano fino com uma face só, virada para dentro do tanque. */
	static String inner(String from, String to, String face, String texture) {
		return String.format(",\n\t\t{\n\t\t\t\"from\": %s,\n\t\t\t\"to\": %s,\n\t\t\t\"faces\": {\n\t\t\t\t\"%s\": { \"texture\": \"%s\" }\n\t\t\t}\n\t\t}",
			from, to, face, texture);
	}

	/**
	 * Água azul com ondas que andam: 8 quadros, interpolados. Opaca de propósito: translúcida, cada face só aparece de
	 * um lado (a água parecia um plano solto) e a ordem com o vidro do mesmo bloco fazia ela piscar.
	 */
	static void water() throws IOException {
		int frames = 8;
		BufferedImage img = new BufferedImage(16, 16 * frames, BufferedImage.TYPE_INT_ARGB);
		for (int f = 0; f < frames; f++) {
			double phase = f * Math.PI * 2 / frames;
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					double wave = Math.sin(x * 0.8 + phase) + Math.sin(y * 0.9 - phase * 2 + x * 0.3) * 0.7;
					double t = (wave + 1.7) / 3.4;
					int r = (int) (0x2E + (0x5A - 0x2E) * t), g = (int) (0x5F + (0x8F - 0x5F) * t), b = (int) (0xC9 + (0xF0 - 0xC9) * t);
					img.setRGB(x, f * 16 + y, argb(255, (r << 16) | (g << 8) | b));
				}
			}
		}
		ImageIO.write(img, "png", ASSETS.resolve("textures/block/tank_water.png").toFile());
		Files.writeString(ASSETS.resolve("textures/block/tank_water.png.mcmeta"),
			"{\n\t\"animation\": {\n\t\t\"frametime\": 3,\n\t\t\"interpolate\": true\n\t}\n}\n");
	}

	/**
	 * Casca de vidro (sem tampa onde tem tanque em cima, sem fundo onde tem embaixo) e, dentro, a água. Encostada
	 * num tanque, a água vai até a borda do bloco para emendar com a do vizinho.
	 */
	static String tankModel(boolean down, boolean up, int level) {
		String side = "better_vanilla_crops:block/water_tank_side" + (down && up ? "_middle" : down ? "_top" : up ? "_bottom" : "");
		StringBuilder sb = new StringBuilder();
		sb.append("{\n\t\"parent\": \"minecraft:block/block\",\n\t\"textures\": {\n")
			.append("\t\t\"particle\": \"better_vanilla_crops:block/water_tank\",\n")
			.append("\t\t\"cap\": \"better_vanilla_crops:block/water_tank\",\n")
			.append("\t\t\"side\": \"").append(side).append("\",\n")
			.append("\t\t\"water\": \"better_vanilla_crops:block/tank_water\"\n\t},\n\t\"elements\": [\n");
		sb.append("\t\t{\n\t\t\t\"from\": [0, 0, 0],\n\t\t\t\"to\": [16, 16, 16],\n\t\t\t\"faces\": {\n");
		StringBuilder faces = new StringBuilder();
		if (!down) faces.append("\t\t\t\t\"down\": { \"texture\": \"#cap\", \"cullface\": \"down\" }");
		if (!up) faces.append(faces.isEmpty() ? "" : ",\n").append("\t\t\t\t\"up\": { \"texture\": \"#cap\", \"cullface\": \"up\" }");
		for (String dir : new String[] {"north", "south", "west", "east"}) {
			faces.append(faces.isEmpty() ? "" : ",\n").append(String.format("\t\t\t\t\"%s\": { \"texture\": \"#side\", \"cullface\": \"%s\" }", dir, dir));
		}
		sb.append(faces).append("\n\t\t\t}\n\t\t}");
		// o mesmo vidro virado para dentro: olhando de cima por dentro da coluna, as paredes aparecem
		sb.append(inner("[0, 0, 0.01]", "[16, 16, 0.01]", "south", "#side"));
		sb.append(inner("[0, 0, 15.99]", "[16, 16, 15.99]", "north", "#side"));
		sb.append(inner("[0.01, 0, 0]", "[0.01, 16, 16]", "east", "#side"));
		sb.append(inner("[15.99, 0, 0]", "[15.99, 16, 16]", "west", "#side"));
		if (!up) sb.append(inner("[0, 15.99, 0]", "[16, 15.99, 16]", "down", "#cap"));
		if (!down) sb.append(inner("[0, 0.01, 0]", "[16, 0.01, 16]", "up", "#cap"));
		if (level > 0) {
			// a água encosta no vidro (só um décimo de pixel de folga, para os dois não brigarem na tela), senão sobra
			// um vão em volta e o tanque cheio parece faltar um pedaço
			double gap = 0.1;
			double bottom = down ? 0 : gap;
			double ceiling = up ? 16 : 16 - gap;
			double top = bottom + (ceiling - bottom) * level / 15.0;
			sb.append(String.format(Locale.ROOT, ",\n\t\t{\n\t\t\t\"from\": [%.3f, %.3f, %.3f],\n\t\t\t\"to\": [%.3f, %.3f, %.3f],\n\t\t\t\"faces\": {\n",
				gap, bottom, gap, 16 - gap, top, 16 - gap));
			// a face de cima fica sempre: o modelo não sabe se o tanque de cima tem água. Se tiver, ela fica escondida
			// dentro da água opaca dele; sem ela, o bloco cheio embaixo de um vazio ficava sem superfície
			StringBuilder water = new StringBuilder("\t\t\t\t\"up\": { \"texture\": \"#water\" }");
			for (String dir : new String[] {"north", "south", "west", "east"}) {
				water.append(",\n").append(String.format("\t\t\t\t\"%s\": { \"texture\": \"#water\" }", dir));
			}
			sb.append(water).append("\n\t\t\t}\n\t\t}");
		}
		sb.append("\n\t]\n}\n");
		return sb.toString();
	}
}
