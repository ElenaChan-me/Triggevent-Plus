package gg.xp.xivsupport.speech.voicepack;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.reevent.scan.LiveOnly;
import gg.xp.xivsupport.gui.i18n.HubSettings;
import gg.xp.xivsupport.persistence.PersistenceProvider;
import gg.xp.xivsupport.persistence.settings.BooleanSetting;
import gg.xp.xivsupport.persistence.settings.IntSetting;
import gg.xp.xivsupport.persistence.settings.StringSetting;
import gg.xp.xivsupport.speech.TtsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serial;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 読み上げ（TTS）をボイスパックの収録音声に差し替える。
 * <p>
 * ACT へ TTS を送る処理より先に読み上げを受け取り、収録があればこの PC で再生する。
 * 名前が入るなどして収録が揃わないものは、日本語にした文を ACT の TTS に回す。台本に無い文は何もしない。
 */
public class VoicePackPlayer {

	private static final Logger log = LoggerFactory.getLogger(VoicePackPlayer.class);
	// ACT へ送る処理（既定の順番 0）より先に受け取る
	private static final int ORDER_BEFORE_ACT = -500;
	// 部品をつなぐときの間。続けて読んだように聞こえる長さ
	private static final int GAP_MS = 40;
	private static final AudioFormat FORMAT = new AudioFormat(48000f, 16, 1, true, false);
	private static final double MIN_GAIN_DB = -40.0;

	private final BooleanSetting enabled;
	private final StringSetting folder;
	private final IntSetting volume;
	private final BooleanSetting japaneseFallback;
	private final List<Path> hubPacks;
	// 読み上げが重ならないよう、1つずつ順番に鳴らす
	private final ExecutorService playback = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "VoicePackPlayback");
		t.setDaemon(true);
		return t;
	});
	private volatile VoicePack pack;
	private volatile String loadError;

	public VoicePackPlayer(PersistenceProvider pers) {
		this(pers, HubSettings.get().getVoicePacks());
	}

	/**
	 * @param hubPacks XIVToolHub でインストールしたパック。自分で選んでいなければ最初のものを使い、読み上げも差し替える
	 */
	VoicePackPlayer(PersistenceProvider pers, List<Path> hubPacks) {
		this.hubPacks = List.copyOf(hubPacks);
		enabled = new BooleanSetting(pers, "voice-pack.enabled", !hubPacks.isEmpty());
		folder = new StringSetting(pers, "voice-pack.folder", hubPacks.isEmpty() ? "" : hubPacks.get(0).toString());
		volume = new IntSetting(pers, "voice-pack.volume", 100, 0, 100);
		japaneseFallback = new BooleanSetting(pers, "voice-pack.japanese-fallback", true);
		folder.addListener(this::reload);
		reload();
	}

	public synchronized void reload() {
		pack = null;
		loadError = null;
		String dir = folder.get();
		if (dir == null || dir.isBlank()) {
			return;
		}
		try {
			pack = VoicePack.load(Path.of(dir).toAbsolutePath().normalize());
			log.info("Voice pack loaded: {} ({} clips, {} callouts)", pack.getName(), pack.getClipCount(), pack.getCalloutCount());
		}
		catch (IOException | RuntimeException e) {
			// 壊れたパックでも Triggevent は動かし続け、読み上げは今までどおり ACT に任せる
			loadError = e.getMessage();
			log.warn("Failed to load voice pack from {}", dir, e);
		}
	}

	@LiveOnly
	@HandleEvents(order = ORDER_BEFORE_ACT)
	public void handleTts(EventContext context, TtsRequest event) {
		VoicePack current = pack;
		if (!enabled.get() || current == null || event.isHandled() || event instanceof JapaneseTtsRequest) {
			return;
		}
		CalloutMapping.Resolution resolution = current.resolve(event.getTtsString());
		if (resolution == null) {
			return;
		}
		List<Path> clips = current.clipsFor(resolution);
		if (clips != null) {
			event.setHandled();
			log.info("Voice pack: '{}' -> {}", event.getTtsString(), resolution.ids());
			play(clips);
		}
		else if (japaneseFallback.get()) {
			event.setHandled();
			log.info("Voice pack (TTS): '{}' -> '{}'", event.getTtsString(), resolution.japanese());
			context.accept(new JapaneseTtsRequest(resolution.japanese()));
		}
	}

	public void play(List<Path> clips) {
		float gainDb = gainDb(volume.get());
		playback.submit(() -> {
			try {
				byte[] pcm = concat(clips);
				playPcm(pcm, gainDb);
			}
			catch (IOException | UnsupportedAudioFileException | LineUnavailableException e) {
				log.error("Error playing voice pack clips {}", clips, e);
			}
		});
	}

	private static float gainDb(int percent) {
		if (percent <= 0) {
			return (float) MIN_GAIN_DB;
		}
		return (float) Math.max(MIN_GAIN_DB, 20 * Math.log10(percent / 100.0));
	}

	static byte[] concat(List<Path> clips) throws IOException, UnsupportedAudioFileException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int gapBytes = (int) (FORMAT.getFrameRate() * GAP_MS / 1000) * FORMAT.getFrameSize();
		for (int i = 0; i < clips.size(); i++) {
			if (i > 0) {
				out.write(new byte[gapBytes]);
			}
			try (AudioInputStream in = AudioSystem.getAudioInputStream(clips.get(i).toFile());
			     AudioInputStream converted = AudioSystem.getAudioInputStream(FORMAT, in)) {
				converted.transferTo(out);
			}
		}
		return out.toByteArray();
	}

	private static void playPcm(byte[] pcm, float gainDb) throws LineUnavailableException {
		try (SourceDataLine line = AudioSystem.getSourceDataLine(FORMAT)) {
			line.open(FORMAT);
			if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
				FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
				gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), gainDb)));
			}
			line.start();
			line.write(pcm, 0, pcm.length);
			line.drain();
		}
	}

	public VoicePack getPack() {
		return pack;
	}

	public String getLoadError() {
		return loadError;
	}

	public BooleanSetting getEnabled() {
		return enabled;
	}

	public StringSetting getFolder() {
		return folder;
	}

	public IntSetting getVolume() {
		return volume;
	}

	public List<Path> getHubPacks() {
		return hubPacks;
	}

	public BooleanSetting getJapaneseFallback() {
		return japaneseFallback;
	}

	/**
	 * ボイスパックで日本語にした読み上げ。ACT の TTS にそのまま読ませるため、このクラスでは再び照合しない。
	 */
	public static final class JapaneseTtsRequest extends TtsRequest {
		@Serial
		private static final long serialVersionUID = 4127893381542093761L;

		public JapaneseTtsRequest(String text) {
			super(text);
		}
	}
}
