package gg.xp.xivsupport.speech.voicepack;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Triggevent-VoiceGen が書き出したボイスパック（manifest.json・callouts.json・WAV）。
 * <p>
 * 文の照合は {@link CalloutMapping} に任せ、ここでは照合結果に対応する音声ファイルを探す。
 * プレイヤー名は台本に無いので、パックの names フォルダに後から作られた音声があればそれを使う。
 */
public final class VoicePack {

	static final String NAMES_DIR = "names";

	private final Path directory;
	private final String name;
	private final String credit;
	private final Map<String, Path> clips;
	private final CalloutMapping mapping;

	private VoicePack(Path directory, String name, String credit, Map<String, Path> clips, CalloutMapping mapping) {
		this.directory = directory;
		this.name = name;
		this.credit = credit;
		this.clips = clips;
		this.mapping = mapping;
	}

	/**
	 * フォルダの中にあるボイスパック（manifest.json があるフォルダ）を名前順に返す。読めないときは空。
	 */
	public static List<Path> listPacks(Path parent) {
		if (parent == null || !Files.isDirectory(parent)) {
			return List.of();
		}
		try (Stream<Path> dirs = Files.list(parent)) {
			return dirs.filter(d -> Files.isRegularFile(d.resolve("manifest.json"))).sorted().toList();
		}
		catch (IOException | UncheckedIOException e) {
			return List.of();
		}
	}

	public static VoicePack load(Path directory) throws IOException {
		Path manifestPath = directory.resolve("manifest.json");
		if (!Files.isRegularFile(manifestPath)) {
			throw new IOException("Missing manifest.json");
		}
		if (Files.size(manifestPath) > CalloutMapping.MAX_JSON_BYTES) {
			throw new IOException("Too large: manifest.json");
		}
		JsonNode manifest = new ObjectMapper().readTree(manifestPath.toFile());
		if (manifest.path("format").asInt() != CalloutMapping.SUPPORTED_FORMAT) {
			throw new IOException("Unsupported voice pack format");
		}
		CalloutMapping mapping = CalloutMapping.load(directory.resolve("callouts.json"));

		Map<String, Path> clips = new HashMap<>();
		for (Map.Entry<String, JsonNode> e : manifest.path("entries").properties()) {
			String file = e.getValue().path("file").asString("");
			Path path = directory.resolve(file).normalize();
			// manifest の値をそのまま信用せず、パックのフォルダの外を指すものは無視する
			if (!file.isEmpty() && path.startsWith(directory) && Files.isRegularFile(path)) {
				clips.put(e.getKey(), path);
			}
		}
		return new VoicePack(directory, manifest.path("name").asString(directory.getFileName().toString()),
				manifest.path("credit").asString(""), Collections.unmodifiableMap(clips), mapping);
	}

	/**
	 * 読み上げ文を照合する。台本に無い文は null。
	 */
	public CalloutMapping.Resolution resolve(String text) {
		return mapping.resolve(text);
	}

	/**
	 * 鳴らす音声ファイル。1つでも欠けていれば null（その場合は TTS で読む）。
	 */
	public List<Path> clipsFor(CalloutMapping.Resolution resolution) {
		List<Path> out = new ArrayList<>();
		int unknown = 0;
		for (String id : resolution.ids()) {
			Path path;
			if (id == null) {
				path = nameClip(resolution.unknowns().get(unknown++));
			}
			else {
				path = clips.get(id);
			}
			if (path == null) {
				return null;
			}
			out.add(path);
		}
		return out;
	}

	/**
	 * プレイヤー名の音声。Triggevent-VoiceGen と同じ規則のファイル名で names フォルダを探す。
	 */
	Path nameClip(String playerName) {
		Path path = directory.resolve(NAMES_DIR).resolve(nameId(playerName) + ".wav");
		return Files.isRegularFile(path) ? path : null;
	}

	/**
	 * 名前の音声のファイル名（拡張子なし）。名前をそのままファイル名にすると記号や長さで困るので、ハッシュにする。
	 */
	public static String nameId(String playerName) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-1").digest(playerName.trim().getBytes(StandardCharsets.UTF_8));
			return "name_" + HexFormat.of().formatHex(digest).substring(0, 10);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	public boolean hasNameClip(String playerName) {
		return nameClip(playerName) != null;
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
		return mapping.size();
	}
}
