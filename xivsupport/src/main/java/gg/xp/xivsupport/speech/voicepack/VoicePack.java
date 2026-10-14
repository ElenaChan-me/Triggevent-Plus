package gg.xp.xivsupport.speech.voicepack;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Triggevent-VoiceGen が書き出したボイスパック（manifest.json・callouts.json・WAV）。
 * <p>
 * 読み上げは名前や方角を埋め込んだ後の英語の文として届くので、台本のテンプレートを正規表現にして照合し、
 * 鳴らす音声の並びと、日本語にした文を求める。照合の手順は Triggevent-VoiceGen の mapping.resolve と同じ。
 */
public final class VoicePack {

	private static final int SUPPORTED_FORMAT = 1;
	// manifest.json・callouts.json として十分に大きく、誤って巨大なファイルを開いたときは止められる値
	private static final long MAX_JSON_BYTES = 32L * 1024 * 1024;

	private final Path directory;
	private final String name;
	private final String credit;
	private final Map<String, Path> clips;
	private final Map<String, Entry> exact;
	private final List<Template> templates;
	private final Map<String, Part> directions;
	private final Map<String, String> numbers;
	private final Part you;

	private VoicePack(Path directory, String name, String credit, Map<String, Path> clips, Map<String, Entry> exact,
	                  List<Template> templates, Map<String, Part> directions, Map<String, String> numbers, Part you) {
		this.directory = directory;
		this.name = name;
		this.credit = credit;
		this.clips = clips;
		this.exact = exact;
		this.templates = templates;
		this.directions = directions;
		this.numbers = numbers;
		this.you = you;
	}

	public static VoicePack load(Path directory) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		JsonNode manifest = readJson(mapper, directory.resolve("manifest.json"));
		JsonNode mapping = readJson(mapper, directory.resolve("callouts.json"));
		if (manifest.path("format").asInt() != SUPPORTED_FORMAT || mapping.path("format").asInt() != SUPPORTED_FORMAT) {
			throw new IOException("Unsupported voice pack format");
		}

		Map<String, Path> clips = new HashMap<>();
		for (Map.Entry<String, JsonNode> e : manifest.path("entries").properties()) {
			String file = e.getValue().path("file").asString("");
			Path path = directory.resolve(file).normalize();
			// manifest の値をそのまま信用せず、パックのフォルダの外を指すものは無視する
			if (!file.isEmpty() && path.startsWith(directory) && Files.isRegularFile(path)) {
				clips.put(e.getKey(), path);
			}
		}

		Map<String, Entry> exact = new HashMap<>();
		for (String table : List.of("static", "names")) {
			for (Map.Entry<String, JsonNode> e : mapping.path(table).properties()) {
				exact.putIfAbsent(e.getKey(), new Entry(e.getValue().path("id").asString(), e.getValue().path("ja").asString()));
			}
		}

		Map<String, Part> directions = new HashMap<>();
		for (Map.Entry<String, JsonNode> e : mapping.path("directions").properties()) {
			Part part = new Part(e.getKey(), e.getValue().path("ja").asString());
			for (JsonNode word : e.getValue().path("en")) {
				directions.put(word.asString(), part);
			}
		}
		Map<String, String> numbers = new HashMap<>();
		for (Map.Entry<String, JsonNode> e : mapping.path("numbers").properties()) {
			numbers.put(e.getKey(), e.getValue().asString());
		}
		JsonNode youNode = mapping.path("you");
		Part you = new Part(youNode.path("id").asString(), youNode.path("ja").asString());
		String youText = youNode.path("en").asString("YOU");

		List<Template> templates = new ArrayList<>();
		for (JsonNode t : mapping.path("templates")) {
			templates.add(Template.parse(t));
		}
		return new VoicePack(directory, manifest.path("name").asString(directory.getFileName().toString()),
				manifest.path("credit").asString(""), Collections.unmodifiableMap(clips), exact,
				Collections.unmodifiableList(templates), directions, numbers, new Part(you.id, you.ja, youText));
	}

	private static JsonNode readJson(ObjectMapper mapper, Path path) throws IOException {
		if (!Files.isRegularFile(path)) {
			throw new IOException("Missing " + path.getFileName());
		}
		if (Files.size(path) > MAX_JSON_BYTES) {
			throw new IOException("Too large: " + path.getFileName());
		}
		return mapper.readTree(path.toFile());
	}

	/**
	 * 読み上げ文を照合する。台本に無い文（利用者が書き換えたコールアウトなど）は null。
	 */
	public Resolution resolve(String text) {
		Entry hit = exact.get(text);
		if (hit != null) {
			return new Resolution(List.of(hit.id), hit.ja);
		}
		for (Template t : templates) {
			Matcher m = t.pattern.matcher(text);
			if (!m.matches()) {
				continue;
			}
			List<String> ids = new ArrayList<>();
			StringBuilder ja = new StringBuilder();
			for (Piece p : t.jaPieces) {
				if (p.slot < 0) {
					ids.add(p.id);
					ja.append(p.text);
					continue;
				}
				Part value = slotValue(t.slots.get(p.slot), m.group("s" + p.slot));
				ids.add(value.id);
				ja.append(value.ja);
			}
			return new Resolution(ids, ja.toString());
		}
		return null;
	}

	private Part slotValue(Slot slot, String value) {
		if (slot.choices != null) {
			Part choice = slot.choices.get(value);
			if (choice != null) {
				return choice;
			}
		}
		if (value.equals(you.en)) {
			return you;
		}
		Part dir = directions.get(value);
		if (dir != null) {
			return dir;
		}
		String number = numbers.get(value);
		if (number != null) {
			return new Part(number, value);
		}
		// プレイヤー名など、収録できない値
		return new Part(null, value);
	}

	/**
	 * 鳴らす音声ファイル。1つでも欠けていれば null（その場合は TTS で読む）。
	 */
	public List<Path> clipsFor(Resolution resolution) {
		List<Path> out = new ArrayList<>();
		for (String id : resolution.ids()) {
			Path path = id == null ? null : clips.get(id);
			if (path == null) {
				return null;
			}
			out.add(path);
		}
		return out;
	}

	public Path getDirectory() {
		return directory;
	}

	public String getName() {
		return name;
	}

	public String getCredit() {
		return credit;
	}

	public int getClipCount() {
		return clips.size();
	}

	public int getCalloutCount() {
		return exact.size() + templates.size();
	}

	public record Resolution(List<String> ids, String japanese) {
	}

	private record Entry(String id, String ja) {
	}

	private record Part(String id, String ja, String en) {
		Part(String id, String ja) {
			this(id, ja, null);
		}
	}

	private record Piece(int slot, String id, String text) {
	}

	private record Slot(Map<String, Part> choices) {
	}

	private record Template(Pattern pattern, List<Slot> slots, List<Piece> jaPieces) {
		static Template parse(JsonNode t) {
			List<Slot> slots = new ArrayList<>();
			List<String> slotRegex = new ArrayList<>();
			for (JsonNode s : t.path("slots")) {
				if ("choice".equals(s.path("kind").asString())) {
					Map<String, Part> choices = new HashMap<>();
					List<String> words = new ArrayList<>();
					for (JsonNode c : s.path("choices")) {
						String en = c.path("en").asString();
						choices.put(en, new Part(c.path("id").asString(), c.path("ja").asString()));
						words.add(en);
					}
					// 長い語を先に試し、短い語が長い語の一部に当たらないようにする
					words.sort((a, b) -> b.length() - a.length());
					slotRegex.add(String.join("|", words.stream().map(Pattern::quote).toList()));
					slots.add(new Slot(choices));
				}
				else {
					slotRegex.add(".+?");
					slots.add(new Slot(null));
				}
			}
			StringBuilder regex = new StringBuilder();
			for (JsonNode p : t.path("enPieces")) {
				if (p.has("lit")) {
					regex.append(Pattern.quote(p.path("lit").asString()));
				}
				else {
					int n = p.path("slot").asInt();
					regex.append("(?<s").append(n).append(">").append(slotRegex.get(n)).append(")");
				}
			}
			List<Piece> jaPieces = new ArrayList<>();
			for (JsonNode p : t.path("jaPieces")) {
				if (p.has("lit")) {
					jaPieces.add(new Piece(-1, p.path("id").asString(), p.path("lit").asString()));
				}
				else {
					jaPieces.add(new Piece(p.path("slot").asInt(), null, null));
				}
			}
			return new Template(Pattern.compile(regex.toString()), slots, jaPieces);
		}
	}
}
