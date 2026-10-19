package gg.xp.xivsupport.gui.i18n;

import gg.xp.xivsupport.gui.themes.BuiltinTheme;
import gg.xp.xivsupport.persistence.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * XIVToolHub がインストール先に書き出す設定（表示言語・フォント・テーマ・ボイスパック）。
 * ハブを使わずに導入した場合はファイルが無いので、日本語版の既定として日本語で表示する。
 */
public final class HubSettings {
	private static final Logger log = LoggerFactory.getLogger(HubSettings.class);

	public static final String FILE_NAME = "xivtoolhub-settings.json";
	// -Dtriggevent.language=en などで一時的に切り替えられる（不具合の切り分け用）
	private static final String LANGUAGE_PROPERTY = "triggevent.language";
	private static final String DEFAULT_LANGUAGE = "ja";
	private static final long MAX_FILE_BYTES = 64 * 1024;
	private static final int MAX_VOICE_PACKS = 32;

	private static volatile HubSettings instance;

	private final String language;
	private final File fontFile;
	private final BuiltinTheme theme;
	private final List<Path> voicePacks;

	private HubSettings(String language, File fontFile, BuiltinTheme theme, List<Path> voicePacks) {
		this.language = language;
		this.fontFile = fontFile;
		this.theme = theme;
		this.voicePacks = List.copyOf(voicePacks);
	}

	public static HubSettings get() {
		HubSettings current = instance;
		if (current == null) {
			synchronized (HubSettings.class) {
				current = instance;
				if (current == null) {
					current = load();
					instance = current;
				}
			}
		}
		return current;
	}

	/**
	 * 表示言語のコード（en / ja / ko / zh）。
	 */
	public String getLanguage() {
		return language;
	}

	/**
	 * 日本語表示のときだけ使うフォントファイル。指定が無いか、ファイルが見つからない場合は null。
	 */
	public File getFontFile() {
		return fontFile;
	}

	/**
	 * XIVToolHub で選んだテーマ。ハブを使わずに起動した場合は null で、Triggevent 側の設定を使う。
	 */
	public BuiltinTheme getTheme() {
		return theme;
	}

	/**
	 * XIVToolHub でインストールしたボイスパックのフォルダ。manifest.json が無いものは除く。
	 */
	public List<Path> getVoicePacks() {
		return voicePacks;
	}

	private static HubSettings load() {
		String language = null;
		File fontFile = null;
		BuiltinTheme theme = null;
		List<Path> voicePacks = new ArrayList<>();
		try {
			File file = new File(Platform.getInstallDir(), FILE_NAME);
			if (file.isFile() && file.length() <= MAX_FILE_BYTES) {
				JsonNode root = new ObjectMapper().readTree(file);
				language = normalize(textOrNull(root.get("language")));
				theme = BuiltinTheme.fromHubCode(textOrNull(root.get("theme")));
				voicePacks = voicePacks(root.get("voicePacks"));
				String fontPath = textOrNull(root.get("fontPath"));
				if (fontPath != null && !fontPath.isBlank()) {
					File candidate = new File(fontPath);
					if (candidate.isFile()) {
						fontFile = candidate;
					}
					else {
						log.warn("Font file not found: {}", fontPath);
					}
				}
			}
		}
		catch (Throwable t) {
			// 設定が読めなくても起動は続ける
			log.warn("Failed to read XIVToolHub settings", t);
		}

		String override = normalize(System.getProperty(LANGUAGE_PROPERTY));
		if (override != null) {
			language = override;
		}
		if (language == null) {
			language = DEFAULT_LANGUAGE;
		}
		if (!"ja".equals(language)) {
			fontFile = null;
		}
		log.info("Display language: {}, font: {}, theme: {}, voice packs: {}", language, fontFile, theme, voicePacks.size());
		return new HubSettings(language, fontFile, theme, voicePacks);
	}

	static List<Path> voicePacks(JsonNode node) {
		List<Path> packs = new ArrayList<>();
		if (node == null || !node.isArray()) {
			return packs;
		}
		for (JsonNode item : node) {
			if (packs.size() >= MAX_VOICE_PACKS) {
				break;
			}
			String text = textOrNull(item);
			if (text == null || text.isBlank()) {
				continue;
			}
			try {
				Path dir = Path.of(text).toAbsolutePath().normalize();
				// アンインストール後に古い設定が残っていても、存在するパックだけを出す
				if (Files.isRegularFile(dir.resolve("manifest.json")) && !packs.contains(dir)) {
					packs.add(dir);
				}
			}
			catch (InvalidPathException e) {
				log.warn("Invalid voice pack path: {}", text);
			}
		}
		return packs;
	}

	private static String textOrNull(JsonNode node) {
		return node == null || node.isNull() ? null : node.asString();
	}

	static String normalize(String code) {
		if (code == null) {
			return null;
		}
		return switch (code.trim().toLowerCase(Locale.ROOT)) {
			case "ja" -> "ja";
			case "ko" -> "ko";
			case "zh", "zh-cn", "zh-hans" -> "zh";
			case "en" -> "en";
			default -> null;
		};
	}
}
