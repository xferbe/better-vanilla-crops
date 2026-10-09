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

	/** Água azul translúcida com ondas que andam: 8 quadros, interpolados. */
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
					img.setRGB(x, f * 16 + y, argb(175, (r << 16) | (g << 8) | b));
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
		if (level > 0) {
			double bottom = down ? 0 : 1;
			double ceiling = up ? 16 : 15;
			double top = bottom + (ceiling - bottom) * level / 15.0;
			sb.append(String.format(Locale.ROOT, ",\n\t\t{\n\t\t\t\"from\": [1, %.3f, 1],\n\t\t\t\"to\": [15, %.3f, 15],\n\t\t\t\"faces\": {\n", bottom, top));
			StringBuilder water = new StringBuilder();
			// a face de cima some quando a água emenda com a do tanque de cima
			if (!(up && level == 15)) water.append("\t\t\t\t\"up\": { \"texture\": \"#water\" }");
			for (String dir : new String[] {"north", "south", "west", "east"}) {
				water.append(water.isEmpty() ? "" : ",\n").append(String.format("\t\t\t\t\"%s\": { \"texture\": \"#water\" }", dir));
			}
			sb.append(water).append("\n\t\t\t}\n\t\t}");
		}
		sb.append("\n\t]\n}\n");
		return sb.toString();
	}
}
