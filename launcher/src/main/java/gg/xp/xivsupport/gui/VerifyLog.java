package gg.xp.xivsupport.gui;

import gg.xp.reevent.events.Event;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.events.EventDistributor;
import gg.xp.reevent.events.EventHandler;
import gg.xp.reevent.events.EventMaster;
import gg.xp.reevent.events.InitEvent;
import gg.xp.xivsupport.callouts.ModifiedCalloutRepository;
import gg.xp.xivsupport.events.actlines.events.XivStateRecalculatedEvent;
import gg.xp.xivsupport.events.actlines.parsers.FakeACTTimeSource;
import gg.xp.xivsupport.events.delaytest.BaseDelayedEvent;
import gg.xp.xivsupport.events.misc.pulls.Pull;
import gg.xp.xivsupport.events.misc.pulls.PullTracker;
import gg.xp.xivsupport.eventstorage.EventReader;
import gg.xp.xivsupport.gui.overlay.FlyingTextOverlay;
import gg.xp.xivsupport.models.XivZone;
import gg.xp.xivsupport.persistence.InMemoryMapPersistenceProvider;
import gg.xp.xivsupport.replay.ReplayController;
import gg.xp.xivsupport.speech.CalloutEvent;
import gg.xp.xivsupport.speech.voicepack.CalloutMapping;
import gg.xp.xivsupport.speech.voicepack.CalloutTranslator;
import gg.xp.xivsupport.speech.voicepack.VoicePack;
import gg.xp.xivsupport.sys.KnownLogSource;
import gg.xp.xivsupport.sys.PrimaryLogSource;
import gg.xp.xivsupport.sys.XivMain;
import org.picocontainer.MutablePicoContainer;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.awt.Window;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * ACT のログを再生して、どのコールアウトがいつ鳴るかを一覧にする。
 * <p>
 * 新しく移植したトリガーや訳を、実際に戦闘に行かずに確かめるためのもの。
 * 日本語訳とボイスパックの収録の有無、時間がおかしい・近すぎるといった気になる点も合わせて出す。
 * 設定はメモリ上だけで扱うので、普段使っている Triggevent の設定には触らない。
 * <pre>
 * verify-log.cmd Network_xxxx.log [--pack ボイスパックのフォルダ] [--out 出力ファイル] [--all]
 * verify-log.cmd ログのフォルダ [--pack ボイスパックのフォルダ] [--out 出力フォルダ] [--all]
 * </pre>
 * 出力先を指定しないときは、ログと同じフォルダに「ログ名.callouts.md」を作る。
 * フォルダを渡すと中の .log をすべて確かめ、「callouts-report」フォルダにログごとの結果とコンテンツごとのまとめを作る。
 */
public final class VerifyLog {

	// これより短い間隔で続くコールアウトは読み上げが重なりやすい（CalloutVerificationTest と同じ基準）
	private static final long MIN_MS_BETWEEN_CALLS = 1000;
	static final String REPORT_DIR = "callouts-report";
	static final String SUMMARY_FILE = "summary.md";

	private VerifyLog() {
	}

	enum Voice {
		// ボイスパックの収録で鳴る
		PACK,
		// 対応表はあるが収録が揃わないので、日本語の TTS で読む
		TTS_JA,
		// 対応表に無いので英語のまま
		NONE,
		// 読み上げの無いコールアウト
		SILENT
	}

	record Call(int pull, String zone, long ms, String tts, String text, String japanese, Voice voice) {
	}

	record Options(Path log, Path pack, Path out, boolean all) {
		static Options parse(String[] args) {
			Path log = null;
			Path pack = null;
			Path out = null;
			boolean all = false;
			for (int i = 0; i < args.length; i++) {
				switch (args[i]) {
					case "--pack" -> pack = Path.of(value(args, ++i, "--pack"));
					case "--out" -> out = Path.of(value(args, ++i, "--out"));
					case "--all" -> all = true;
					default -> {
						if (args[i].startsWith("--") || log != null) {
							throw new IllegalArgumentException("Unknown argument: " + args[i]);
						}
						log = Path.of(args[i]);
					}
				}
			}
			if (log == null) {
				throw new IllegalArgumentException("Specify an ACT log file.");
			}
			return new Options(log, pack, out, all);
		}

		private static String value(String[] args, int i, String name) {
			if (i >= args.length) {
				throw new IllegalArgumentException(name + " needs a value.");
			}
			return args[i];
		}
	}

	public static void main(String[] args) {
		Options options;
		try {
			options = Options.parse(args);
		}
		catch (IllegalArgumentException e) {
			System.err.println(e.getMessage());
			System.err.println("Usage: verify-log <ACT log file or folder> [--pack <voice pack folder>] [--out <report file or folder>] [--all]");
			System.exit(2);
			return;
		}
		if (Files.isDirectory(options.log())) {
			try {
				VoicePack pack = options.pack() == null ? null : VoicePack.load(options.pack().toAbsolutePath().normalize());
				Path outDir = options.out() == null ? options.log().resolve(REPORT_DIR) : options.out();
				Path summary = verifyFolder(options.log(), outDir, options.all(), pack);
				System.out.println("Summary -> " + summary.toAbsolutePath());
				System.exit(0);
			}
			catch (IOException | RuntimeException e) {
				e.printStackTrace();
				System.exit(1);
			}
			return;
		}
		if (!Files.isRegularFile(options.log())) {
			System.err.println("Log file not found: " + options.log());
			System.exit(2);
			return;
		}
		try {
			VoicePack pack = options.pack() == null ? null : VoicePack.load(options.pack().toAbsolutePath().normalize());
			List<Call> calls = replay(options.log().toFile(), options.all(), pack);
			String report = format(options.log().getFileName().toString(), pack, calls);
			// コンソールの文字コードによっては日本語が化けるので、画面には出さずファイルに書く
			Path out = options.out() == null ? defaultOutput(options.log()) : options.out();
			Files.writeString(out, report, StandardCharsets.UTF_8);
			System.out.println(calls.size() + " callouts -> " + out.toAbsolutePath());
			System.exit(0);
		}
		catch (IOException | RuntimeException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * フォルダ内のログを1つずつ確かめる。壊れたログがあっても残りは続ける。
	 */
	static Path verifyFolder(Path folder, Path outDir, boolean enableAll, VoicePack pack) throws IOException {
		List<Path> logs;
		try (Stream<Path> files = Files.list(folder)) {
			logs = files.filter(f -> Files.isRegularFile(f) && f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".log"))
					.sorted()
					.toList();
		}
		Files.createDirectories(outDir);
		List<LogResult> results = new ArrayList<>();
		for (int i = 0; i < logs.size(); i++) {
			Path log = logs.get(i);
			String name = log.getFileName().toString();
			System.out.printf("[%d/%d] %s%n", i + 1, logs.size(), name);
			try {
				List<Call> calls = replay(log.toFile(), enableAll, pack);
				Files.writeString(outDir.resolve(defaultOutput(log).getFileName()), format(name, pack, calls), StandardCharsets.UTF_8);
				results.add(new LogResult(name, calls, null));
			}
			catch (RuntimeException e) {
				results.add(new LogResult(name, List.of(), e.toString()));
			}
			finally {
				// 再生のたびに作られるオーバーレイの窓が溜まらないようにする
				for (Window w : Window.getWindows()) {
					w.dispose();
				}
			}
		}
		Path summary = outDir.resolve(SUMMARY_FILE);
		Files.writeString(summary, summarize(folder.getFileName() == null ? folder.toString() : folder.getFileName().toString(), pack, results), StandardCharsets.UTF_8);
		return summary;
	}

	record LogResult(String log, List<Call> calls, String error) {
	}

	/**
	 * コンテンツごとのまとめ。どのログで何が起きたかを追えるよう、気になる点にはログ名を付ける。
	 */
	static String summarize(String folderName, VoicePack pack, List<LogResult> results) {
		StringBuilder sb = new StringBuilder();
		sb.append("# コールアウト検証のまとめ: ").append(folderName).append('\n');
		sb.append("ボイスパック: ").append(pack == null ? "なし（日本語の対応表だけで判定）" : pack.getName()).append("\n\n");
		long failed = results.stream().filter(r -> r.error() != null).count();
		sb.append("ログ %d 件（読めなかったもの %d 件）\n\n".formatted(results.size(), failed));

		Map<String, List<Call>> byZone = new TreeMap<>();
		Map<String, Set<String>> logsByZone = new TreeMap<>();
		Map<String, List<String>> issuesByZone = new TreeMap<>();
		for (LogResult r : results) {
			Map<String, List<Call>> zones = new LinkedHashMap<>();
			for (Call c : r.calls()) {
				String zone = c.zone().isBlank() ? "(不明)" : c.zone();
				zones.computeIfAbsent(zone, z -> new ArrayList<>()).add(c);
			}
			zones.forEach((zone, calls) -> {
				byZone.computeIfAbsent(zone, z -> new ArrayList<>()).addAll(calls);
				logsByZone.computeIfAbsent(zone, z -> new TreeSet<>()).add(r.log());
				issues(calls).forEach(i -> issuesByZone.computeIfAbsent(zone, z -> new ArrayList<>()).add(r.log() + " " + i));
			});
		}

		sb.append("| コンテンツ | ログ | コールアウト | 収録 | 日本語TTS | 英語のまま | 気になる点 |\n|---|---|---|---|---|---|---|\n");
		byZone.forEach((zone, calls) -> sb.append("| ").append(cell(zone))
				.append(" | ").append(logsByZone.get(zone).size())
				.append(" | ").append(calls.size())
				.append(" | ").append(count(calls, Voice.PACK))
				.append(" | ").append(count(calls, Voice.TTS_JA))
				.append(" | ").append(count(calls, Voice.NONE))
				.append(" | ").append(issuesByZone.getOrDefault(zone, List.of()).size())
				.append(" |\n"));

		byZone.forEach((zone, calls) -> {
			List<String> issues = issuesByZone.getOrDefault(zone, List.of());
			List<String> english = calls.stream().filter(c -> c.voice() == Voice.NONE).map(Call::tts).distinct().toList();
			if (issues.isEmpty() && english.isEmpty()) {
				return;
			}
			sb.append("\n## ").append(zone).append("\n\n");
			issues.forEach(i -> sb.append("- ").append(i).append('\n'));
			if (!english.isEmpty()) {
				sb.append("\n対応表に無い読み上げ:\n\n");
				english.forEach(t -> sb.append("- ").append(t).append('\n'));
			}
		});

		if (failed > 0) {
			sb.append("\n## 読めなかったログ\n\n");
			results.stream().filter(r -> r.error() != null).forEach(r -> sb.append("- ").append(r.log()).append(": ").append(r.error()).append('\n'));
		}
		return sb.toString();
	}

	private static long count(List<Call> calls, Voice voice) {
		return calls.stream().filter(c -> c.voice() == voice).count();
	}

	static Path defaultOutput(Path log) {
		String name = log.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return log.resolveSibling((dot > 0 ? name.substring(0, dot) : name) + ".callouts.md");
	}

	static List<Call> replay(File logFile, boolean enableAll, VoicePack pack) {
		MutablePicoContainer pico = XivMain.testingMasterInit();
		ReplayController replay = new ReplayController(pico.getComponent(EventMaster.class), EventReader.readActLogFile(logFile), false);
		pico.addComponent(replay);
		pico.addComponent(FakeACTTimeSource.class);
		FakeACTTimeSource timeSource = pico.getComponent(FakeACTTimeSource.class);
		pico.getComponent(PrimaryLogSource.class).setLogSource(KnownLogSource.ACT_LOG_FILE);
		CalloutTranslator translator = new CalloutTranslator(new InMemoryMapPersistenceProvider(), true);
		List<Call> calls = new ArrayList<>();

		EventDistributor dist = pico.getComponent(EventDistributor.class);
		// 再生中の遅延イベントは実際の時計ではなくログの時刻で動かす（CalloutVerificationTest と同じ）
		dist.registerHandler(Event.class, new EventHandler<>() {
			@Override
			public void handle(EventContext ctx, Event e) {
				if (e instanceof BaseDelayedEvent bde) {
					if (bde.getParent() != null) {
						bde.setHappenedAt(bde.getParent().getHappenedAt());
					}
					bde.setTimeSource(timeSource);
				}
				else if (e instanceof XivStateRecalculatedEvent ev) {
					ev.setTimeSource(timeSource);
					ev.setHappenedAt(timeSource.now());
				}
			}

			@Override
			public int getOrder() {
				return -5000;
			}
		});
		dist.registerHandler(CalloutEvent.class, (ctx, e) -> {
			String tts = e.getCallText();
			String text = e.getVisualText();
			if ((tts == null || tts.isEmpty()) && (text == null || text.isBlank())) {
				return;
			}
			Pull pull = pico.getComponent(PullTracker.class).getCurrentPull();
			if (pull == null || pull.getCombatStart() == null) {
				return;
			}
			long ms = Duration.between(pull.getCombatStart().getHappenedAt(), timeSource.now()).toMillis();
			XivZone zone = pull.getZone();
			calls.add(new Call(pull.getPullNum(), zone == null ? "" : zone.getName(), ms,
					tts == null ? "" : tts, text == null ? "" : text,
					translator.translateVisual(text == null || text.isBlank() ? tts : text), voice(pack, translator, tts)));
		});

		dist.acceptEvent(new InitEvent());
		// 文字のコールアウトはオーバーレイが有効なときしか作られない
		pico.getComponent(FlyingTextOverlay.class).getEnabled().set(true);
		if (enableAll) {
			pico.getComponent(ModifiedCalloutRepository.class).getAllCallouts().stream()
					.flatMap(group -> group.getCallouts().stream())
					.forEach(c -> c.getEnable().set(true));
		}
		try {
			replay.advanceBy(Integer.MAX_VALUE);
			pico.getComponent(EventMaster.class).getQueue().waitDrain();
		}
		finally {
			// フォルダごと確かめるときに、ログごとの処理スレッドが動き続けないようにする
			pico.getComponent(EventMaster.class).stop();
		}
		return calls;
	}

	static Voice voice(VoicePack pack, CalloutTranslator translator, String tts) {
		if (tts == null || tts.isBlank()) {
			return Voice.SILENT;
		}
		if (pack != null) {
			CalloutMapping.Resolution resolution = pack.resolve(tts);
			if (resolution != null) {
				return pack.clipsFor(resolution) != null ? Voice.PACK : Voice.TTS_JA;
			}
			return Voice.NONE;
		}
		return translator.translateSpeech(tts) != null ? Voice.TTS_JA : Voice.NONE;
	}

	static List<String> issues(List<Call> calls) {
		List<String> issues = new ArrayList<>();
		Call last = null;
		for (Call call : calls) {
			if (call.text().endsWith("(NOW)")) {
				issues.add("%s: 表示が (NOW) で終わっています（遅れて出ている可能性）".formatted(label(call)));
			}
			if (call.tts().contains("Error") || call.text().contains("Error")) {
				issues.add("%s: コールアウトの中でエラーが出ています".formatted(label(call)));
			}
			// プルが変わると時刻が 0 に戻るので、同じプルの中だけを比べる
			if (last != null && last.pull() == call.pull() && call.ms() - last.ms() < MIN_MS_BETWEEN_CALLS && !call.tts().isBlank() && !last.tts().isBlank()) {
				issues.add("%s: 直前の「%s」と %dms しか離れておらず、読み上げが重なります".formatted(label(call), last.tts(), call.ms() - last.ms()));
			}
			if (!call.tts().isBlank()) {
				last = call;
			}
		}
		return issues;
	}

	private static String label(Call call) {
		return "#%d %s 「%s」".formatted(call.pull(), time(call.ms()), call.tts().isBlank() ? call.text() : call.tts());
	}

	static String time(long ms) {
		long seconds = Math.max(0, ms) / 1000;
		return String.format(Locale.ROOT, "%d:%02d.%d", seconds / 60, seconds % 60, Math.max(0, ms) % 1000 / 100);
	}

	static String format(String logName, VoicePack pack, List<Call> calls) {
		StringBuilder sb = new StringBuilder();
		sb.append("# コールアウト検証: ").append(logName).append('\n');
		sb.append("ボイスパック: ").append(pack == null ? "なし（日本語の対応表だけで判定）" : pack.getName()).append("\n\n");
		long spoken = calls.stream().filter(c -> c.voice() != Voice.SILENT).count();
		long packed = calls.stream().filter(c -> c.voice() == Voice.PACK).count();
		long ttsJa = calls.stream().filter(c -> c.voice() == Voice.TTS_JA).count();
		long english = calls.stream().filter(c -> c.voice() == Voice.NONE).count();
		sb.append("コールアウト %d 件（読み上げあり %d 件: 収録 %d / 日本語TTS %d / 英語のまま %d）\n\n"
				.formatted(calls.size(), spoken, packed, ttsJa, english));
		int currentPull = -1;
		for (Call call : calls) {
			if (call.pull() != currentPull) {
				currentPull = call.pull();
				sb.append("## プル #").append(currentPull).append(' ').append(call.zone()).append("\n\n");
				sb.append("| 時間 | 読み上げ | 表示 | 日本語 | 音声 |\n|---|---|---|---|---|\n");
			}
			sb.append("| ").append(time(call.ms()))
					.append(" | ").append(cell(call.tts()))
					.append(" | ").append(cell(call.text()))
					.append(" | ").append(cell(call.japanese()))
					.append(" | ").append(voiceLabel(call.voice()))
					.append(" |\n");
		}
		List<String> issues = issues(calls);
		sb.append("\n## 気になる点 (").append(issues.size()).append(")\n\n");
		if (issues.isEmpty()) {
			sb.append("なし\n");
		}
		issues.forEach(i -> sb.append("- ").append(i).append('\n'));
		List<String> untranslated = calls.stream()
				.filter(c -> c.voice() == Voice.NONE)
				.map(Call::tts)
				.distinct()
				.toList();
		if (!untranslated.isEmpty()) {
			sb.append("\n## 対応表に無い読み上げ (").append(untranslated.size()).append(")\n\n");
			untranslated.forEach(t -> sb.append("- ").append(t).append('\n'));
		}
		return sb.toString();
	}

	private static String voiceLabel(Voice voice) {
		return switch (voice) {
			case PACK -> "収録";
			case TTS_JA -> "日本語TTS";
			case NONE -> "英語";
			case SILENT -> "-";
		};
	}

	private static String cell(String text) {
		// 表が崩れないよう、改行と区切り文字を置き換える
		return text == null ? "" : text.replace("|", "\\|").replace("\r", "").replace("\n", " / ");
	}
}
