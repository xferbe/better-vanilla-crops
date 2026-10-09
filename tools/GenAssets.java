import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import javax.imageio.ImageIO;

/**
 * Gera as texturas do tanque (vidro com quinas de cobre e a água animada), os 16 modelos do tanque (um por altura da
 * água) e o blockstate dele. Rodar da raiz do projeto: {@code java tools/GenAssets.java}.
 */
public class GenAssets {
	static final Path ASSETS = Path.of("src/main/resources/assets/better_vanilla_crops");

	public static void main(String[] args) throws IOException {
		Files.createDirectories(ASSETS.resolve("textures/block"));
		Files.createDirectories(ASSETS.resolve("models/block"));
		Files.createDirectories(ASSETS.resolve("blockstates"));
		tankSide();
		water();
		StringBuilder variants = new StringBuilder();
		for (int level = 0; level <= 15; level++) {
			Files.writeString(ASSETS.resolve("models/block/water_tank_" + level + ".json"), tankModel(level));
			variants.append(String.format(Locale.ROOT, "%s\t\t\"level=%d\": { \"model\": \"better_vanilla_crops:block/water_tank_%d\" }",
				level == 0 ? "" : ",\n", level, level));
		}
		Files.writeString(ASSETS.resolve("blockstates/water_tank.json"), "{\n\t\"variants\": {\n" + variants + "\n\t}\n}\n");
	}

	static int argb(int a, int rgb) {
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	/** Vidro levemente azulado e translúcido, com moldura de cobre e um rebite em cada quina. */
	static void tankSide() throws IOException {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int c;
				boolean corner = (x < 2 || x > 13) && (y < 2 || y > 13);
				if (corner) {
					boolean rivet = (x == 1 || x == 14) && (y == 1 || y == 14);
					c = argb(255, rivet ? 0xFC9982 : 0x9C4529);
				} else if (y == 0 || x == 0) {
					c = argb(255, 0xE77C56);
				} else if (y == 15 || x == 15) {
					c = argb(255, 0x9C4529);
				} else if (x + y == 6 || x + y == 7 || x + y == 11) {
					c = argb(120, 0xFFFFFF); // reflexo do vidro
				} else {
					c = argb(45, 0xD6F1F5);
				}
				img.setRGB(x, y, c);
			}
		}
		ImageIO.write(img, "png", ASSETS.resolve("textures/block/water_tank.png").toFile());
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
		File out = ASSETS.resolve("textures/block/tank_water.png").toFile();
		ImageIO.write(img, "png", out);
		Files.writeString(ASSETS.resolve("textures/block/tank_water.png.mcmeta"),
			"{\n\t\"animation\": {\n\t\t\"frametime\": 3,\n\t\t\"interpolate\": true\n\t}\n}\n");
	}

	/** Casca de vidro do bloco inteiro e, dentro, a água com a altura do nível (1 a 15 pixels). */
	static String tankModel(int level) {
		StringBuilder sb = new StringBuilder();
		sb.append("{\n\t\"parent\": \"minecraft:block/block\",\n\t\"textures\": {\n")
			.append("\t\t\"particle\": \"better_vanilla_crops:block/water_tank\",\n")
			.append("\t\t\"glass\": \"better_vanilla_crops:block/water_tank\",\n")
			.append("\t\t\"water\": \"better_vanilla_crops:block/tank_water\"\n\t},\n\t\"elements\": [\n");
		sb.append("\t\t{\n\t\t\t\"from\": [0, 0, 0],\n\t\t\t\"to\": [16, 16, 16],\n\t\t\t\"faces\": {\n");
		String[] dirs = {"down", "up", "north", "south", "west", "east"};
		for (int i = 0; i < dirs.length; i++) {
			sb.append(String.format("\t\t\t\t\"%s\": { \"texture\": \"#glass\", \"cullface\": \"%s\" }%s\n", dirs[i], dirs[i], i < dirs.length - 1 ? "," : ""));
		}
		sb.append("\t\t\t}\n\t\t}");
		if (level > 0) {
			double top = 1 + 14.0 * level / 15.0;
			sb.append(String.format(Locale.ROOT, ",\n\t\t{\n\t\t\t\"from\": [1, 1, 1],\n\t\t\t\"to\": [15, %.3f, 15],\n\t\t\t\"faces\": {\n", top));
			String[] water = {"up", "north", "south", "west", "east"};
			for (int i = 0; i < water.length; i++) {
				sb.append(String.format("\t\t\t\t\"%s\": { \"texture\": \"#water\" }%s\n", water[i], i < water.length - 1 ? "," : ""));
			}
			sb.append("\t\t\t}\n\t\t}");
		}
		sb.append("\n\t]\n}\n");
		return sb.toString();
	}
}
