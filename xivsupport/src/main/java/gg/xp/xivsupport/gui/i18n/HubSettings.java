package gg.xp.xivsupport.gui.i18n;

import gg.xp.xivsupport.persistence.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.Locale;

/**
 * XIVToolHub がインストール先に書き出す設定（表示言語・フォント）。
 * ハブを使わずに導入した場合はファイルが無いので、日本語版の既定として日本語で表示する。
 */
public final class HubSettings {
	private static final Logger log = LoggerFactory.getLogger(HubSettings.class);

	public static final String FILE_NAME = "xivtoolhub-settings.json";
	// -Dtriggevent.language=en などで一時的に切り替えられる（不具合の切り分け用）
	private static final String LANGUAGE_PROPERTY = "triggevent.language";
	private static final String DEFAULT_LANGUAGE = "ja";
	private static final long MAX_FILE_BYTES = 64 * 1024;

	private static volatile HubSettings instance;

	private final String language;
	private final File fontFile;

	private HubSettings(String language, File fontFile) {
		this.language = language;
		this.fontFile = fontFile;
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

	private static HubSettings load() {
		String language = null;
		File fontFile = null;
		try {
			File file = new File(Platform.getInstallDir(), FILE_NAME);
			if (file.isFile() && file.length() <= MAX_FILE_BYTES) {
				JsonNode root = new ObjectMapper().readTree(file);
				language = normalize(textOrNull(root.get("language")));
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
		log.info("Display language: {}, font: {}", language, fontFile);
		return new HubSettings(language, fontFile);
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
