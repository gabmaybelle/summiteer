package com.gabbymaybelle.summiteer;

import com.gabbymaybelle.summiteer.platform.Services;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * The settings, in {@code config/summiteer.properties}. A missing file gets the defaults written
 * out with a comment per setting; a value that doesn't make sense keeps the default with a warning
 * in the log, and a key this version doesn't know is kept as it was (it's probably a setting from
 * another version). The file is only rewritten when a setting is missing, and then through a temp
 * file so a crash mid-write can't leave it half done.
 */
public final class Config {
	public static final String FILE_NAME = Summiteer.MOD_ID + ".properties";

	// Height bands, in blocks above the world's sea level (63 in vanilla, so 133 and 183).
	public static int highAboveSeaLevel = 70;
	public static int summitAboveSeaLevel = 120;

	// The weather up high, each with its own switch.
	public static boolean wind = true;
	public static boolean cold = true;
	public static boolean whiteouts = true;

	private static final Map<String, String> COMMENTS = new LinkedHashMap<>();
	static {
		COMMENTS.put("highAboveSeaLevel", "Where the treeline is: mountain biomes this many blocks above sea level count as high ground. Peak biomes count at any height.");
		COMMENTS.put("summitAboveSeaLevel", "Where the summits start, in any biome. Raise both for packs with very tall terrain.");
		COMMENTS.put("wind", "Gusts above the treeline that push you (true or false). Crouch to brace, or get under a roof.");
		COMMENTS.put("cold", "Freezing in snowstorms up high and on summits at night (true or false). A fire warms you up.");
		COMMENTS.put("whiteouts", "Thick fog on the summits in a snowstorm (true or false). Only changes what you see.");
	}

	private Config() {
	}

	public static Path path() {
		return Services.PLATFORM.configDir().resolve(FILE_NAME);
	}

	public static void load() {
		Path file = path();
		Properties props = new Properties();
		if (Files.exists(file)) {
			try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				props.load(in);
			} catch (IOException e) {
				Summiteer.LOGGER.warn("Couldn't read {}; using the defaults", file, e);
			}
		}
		highAboveSeaLevel = readInt(props, "highAboveSeaLevel", 70, 1, 2000);
		summitAboveSeaLevel = readInt(props, "summitAboveSeaLevel", 120, 1, 2000);
		if (highAboveSeaLevel >= summitAboveSeaLevel) {
			Summiteer.LOGGER.warn("highAboveSeaLevel must be below summitAboveSeaLevel; using 70 and 120");
			highAboveSeaLevel = 70;
			summitAboveSeaLevel = 120;
		}
		wind = readBool(props, "wind", true);
		cold = readBool(props, "cold", true);
		whiteouts = readBool(props, "whiteouts", true);
		boolean missing = false;
		for (String key : COMMENTS.keySet()) {
			if (!props.containsKey(key)) {
				missing = true;
				props.setProperty(key, current(key));
			}
		}
		if (missing) {
			save(file, props);
		}
	}

	private static String current(String key) {
		return switch (key) {
			case "highAboveSeaLevel" -> Integer.toString(highAboveSeaLevel);
			case "summitAboveSeaLevel" -> Integer.toString(summitAboveSeaLevel);
			case "wind" -> Boolean.toString(wind);
			case "cold" -> Boolean.toString(cold);
			case "whiteouts" -> Boolean.toString(whiteouts);
			default -> throw new IllegalArgumentException(key);
		};
	}

	private static int readInt(Properties props, String key, int fallback, int min, int max) {
		String raw = props.getProperty(key);
		if (raw == null) {
			return fallback;
		}
		try {
			int value = Integer.parseInt(raw.trim());
			if (value < min || value > max) {
				Summiteer.LOGGER.warn("{} must be between {} and {} (got {}); using {}", key, min, max, value, fallback);
				return fallback;
			}
			return value;
		} catch (NumberFormatException e) {
			Summiteer.LOGGER.warn("{} should be a whole number (got \"{}\"); using {}", key, raw, fallback);
			return fallback;
		}
	}

	private static boolean readBool(Properties props, String key, boolean fallback) {
		String raw = props.getProperty(key);
		if (raw == null) {
			return fallback;
		}
		String value = raw.trim().toLowerCase(Locale.ROOT);
		if (value.equals("true") || value.equals("false")) {
			return Boolean.parseBoolean(value);
		}
		Summiteer.LOGGER.warn("{} should be true or false (got \"{}\"); using {}", key, raw, fallback);
		return fallback;
	}

	/** Writes every setting, commented, keeping any keys we don't know, through a temp file. */
	private static void save(Path file, Properties props) {
		try {
			Files.createDirectories(file.getParent());
			Path temp = file.resolveSibling(FILE_NAME + ".tmp");
			try (Writer out = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
				out.write("# Summiteer settings. Changes apply when the game starts.\n");
				for (Map.Entry<String, String> entry : COMMENTS.entrySet()) {
					out.write("\n# " + entry.getValue() + "\n");
					out.write(entry.getKey() + "=" + props.getProperty(entry.getKey()) + "\n");
				}
				boolean unknownHeader = false;
				for (String key : props.stringPropertyNames()) {
					if (!COMMENTS.containsKey(key)) {
						if (!unknownHeader) {
							out.write("\n# Settings this version doesn't know (kept as they were)\n");
							unknownHeader = true;
						}
						out.write(key + "=" + props.getProperty(key) + "\n");
					}
				}
			}
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			Summiteer.LOGGER.warn("Couldn't write {}", file, e);
		}
	}
}
