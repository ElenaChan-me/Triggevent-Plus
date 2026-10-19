package gg.xp.xivsupport.speech.voicepack;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.reevent.scan.LiveOnly;
import gg.xp.xivsupport.gui.i18n.HubSettings;
import gg.xp.xivsupport.persistence.PersistenceProvider;
import gg.xp.xivsupport.persistence.settings.BooleanSetting;
import gg.xp.xivsupport.speech.TtsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 表示言語が日本語のとき、コールアウトの画面表示と読み上げを日本語にする。
 * <p>
 * 対応表は Triggevent-VoiceGen が台本から作ったものを同梱している（ボイスパックが無くても使える）。
 * 読み上げはボイスパックの処理の後に受け取るので、ボイスパックで鳴らしたものには手を出さない。
 */
public class CalloutTranslator {

	private static final Logger log = LoggerFactory.getLogger(CalloutTranslator.class);
	private static final String RESOURCE = "/i18n/callouts-ja.json";
	// ボイスパック（-500）の後、ACT へ送る処理（0）の前
	private static final int ORDER_AFTER_VOICE_PACK = -400;
	// 画面表示は描画のたびに問い合わせるので、照合結果を覚えておく
	private static final int CACHE_SIZE = 1024;
	// durationBasedCall が付ける残り時間（例: "Raidwide (4.7)"）は訳さずに残す
	private static final Pattern COUNTDOWN = Pattern.compile("^(.*?)( \\(\\d+(?:\\.\\d+)?\\))$", Pattern.DOTALL);

	private final BooleanSetting displayEnabled;
	private final BooleanSetting speechEnabled;
	private final boolean japanese;
	private volatile CalloutMapping mapping;
	private volatile boolean loadFailed;
	private final Map<String, String> cache = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
			return size() > CACHE_SIZE;
		}
	};

	public CalloutTranslator(PersistenceProvider pers) {
		this(pers, "ja".equals(HubSettings.get().getLanguage()));
	}

	CalloutTranslator(PersistenceProvider pers, boolean japanese) {
		displayEnabled = new BooleanSetting(pers, "callout-translation.display", true);
		speechEnabled = new BooleanSetting(pers, "callout-translation.speech", true);
		this.japanese = japanese;
		displayEnabled.addListener(this::clearCache);
	}

	private CalloutMapping mapping() {
		CalloutMapping current = mapping;
		if (current != null || loadFailed) {
			return current;
		}
		synchronized (this) {
			if (mapping == null && !loadFailed) {
				try (InputStream in = CalloutTranslator.class.getResourceAsStream(RESOURCE)) {
					if (in == null) {
						throw new IOException("Missing resource " + RESOURCE);
					}
					mapping = CalloutMapping.load(in);
					log.info("Japanese callout mapping loaded ({} callouts)", mapping.size());
				}
				catch (IOException | RuntimeException e) {
					// 訳せなくても英語のまま表示・読み上げを続ける
					loadFailed = true;
					log.error("Failed to load Japanese callout mapping", e);
				}
			}
			return mapping;
		}
	}

	private synchronized void clearCache() {
		cache.clear();
	}

	/**
	 * 画面に出す文を日本語にする。訳が無いときや設定で無効なときは元の文を返す。
	 */
	public String translateVisual(String text) {
		if (text == null || text.isBlank() || !japanese || !displayEnabled.get()) {
			return text;
		}
		synchronized (this) {
			String cached = cache.get(text);
			if (cached != null) {
				return cached;
			}
		}
		String out = translateLines(text);
		synchronized (this) {
			cache.put(text, out);
		}
		return out;
	}

	private String translateLines(String text) {
		// 複数行のコールアウトは行ごとに訳す
		String[] lines = text.split("\n", -1);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < lines.length; i++) {
			if (i > 0) {
				sb.append('\n');
			}
			sb.append(translateLine(lines[i]));
		}
		return sb.toString();
	}

	private String translateLine(String line) {
		CalloutMapping current = mapping();
		if (current == null || line.isBlank()) {
			return line;
		}
		String core = line;
		String suffix = "";
		Matcher m = COUNTDOWN.matcher(line);
		if (m.matches()) {
			core = m.group(1);
			suffix = m.group(2);
		}
		CalloutMapping.Resolution r = current.resolve(core);
		return r == null ? line : r.japanese() + suffix;
	}

	/**
	 * 読み上げる文の日本語。台本に無い文は null。
	 */
	public String translateSpeech(String text) {
		CalloutMapping current = mapping();
		if (current == null || text == null) {
			return null;
		}
		CalloutMapping.Resolution r = current.resolve(text);
		return r == null ? null : r.japanese();
	}

	@LiveOnly
	@HandleEvents(order = ORDER_AFTER_VOICE_PACK)
	public void handleTts(EventContext context, TtsRequest event) {
		if (!japanese || !speechEnabled.get() || event.isHandled() || event instanceof VoicePackPlayer.JapaneseTtsRequest) {
			return;
		}
		String ja = translateSpeech(event.getTtsString());
		if (ja != null) {
			event.setHandled();
			context.accept(new VoicePackPlayer.JapaneseTtsRequest(ja));
		}
	}

	public boolean isJapanese() {
		return japanese;
	}

	public BooleanSetting getDisplayEnabled() {
		return displayEnabled;
	}

	public BooleanSetting getSpeechEnabled() {
		return speechEnabled;
	}
}
