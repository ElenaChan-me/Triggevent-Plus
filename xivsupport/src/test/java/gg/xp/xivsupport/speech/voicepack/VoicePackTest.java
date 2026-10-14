package gg.xp.xivsupport.speech.voicepack;

import org.testng.Assert;
import org.testng.annotations.Test;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class VoicePackTest {

	private static final AudioFormat FORMAT = new AudioFormat(48000f, 16, 1, true, false);

	// テストは並列に動くので、テストごとに別のフォルダを作る
	private static Path createPack() throws IOException {
		Path dir = Files.createTempDirectory("voicepack");
		for (String id : List.of("L_raid", "S_buster", "part_you", "part_dir_n", "S_partners", "C_real", "dot_1871")) {
			writeWav(dir.resolve(id + ".wav"), 4800);
		}
		Files.writeString(dir.resolve("manifest.json"), """
				{"format":1,"name":"test","credit":"VOICEVOX:test","entries":{
				 "L_raid":{"file":"L_raid.wav"},"S_buster":{"file":"S_buster.wav"},"part_you":{"file":"part_you.wav"},
				 "part_dir_n":{"file":"part_dir_n.wav"},"S_partners":{"file":"S_partners.wav"},"C_real":{"file":"C_real.wav"},
				 "dot_1871":{"file":"dot_1871.wav"},"evil":{"file":"../outside.wav"}}}
				""", StandardCharsets.UTF_8);
		Files.writeString(dir.resolve("callouts.json"), """
				{"format":1,
				 "static":{"Raidwide":{"id":"L_raid","ja":"全体攻撃"}},
				 "names":{"ディア":{"id":"dot_1871","ja":"ディア"}},
				 "templates":[
				  {"en":"Buster on {event.target}","enPieces":[{"lit":"Buster on "},{"slot":0}],
				   "jaPieces":[{"slot":0},{"lit":"にタンク強攻撃","id":"S_buster"}],"slots":[{"kind":"player"}]},
				  {"en":"Partners {safe}","enPieces":[{"lit":"Partners "},{"slot":0}],
				   "jaPieces":[{"slot":0},{"lit":"でペア","id":"S_partners"}],"slots":[{"kind":"direction"}]},
				  {"en":"{real ? 'Real' : 'Fake'} Cross","enPieces":[{"slot":0},{"lit":" Cross"}],
				   "jaPieces":[{"slot":0},{"lit":"十字","id":"S_cross"}],
				   "slots":[{"kind":"choice","choices":[{"en":"Real","ja":"本物","id":"C_real"},{"en":"Fake","ja":"偽物","id":"C_fake"}]}]}],
				 "directions":{"part_dir_n":{"ja":"北","en":["North","N"]}},
				 "numbers":{"1":"part_num_1"},
				 "you":{"en":"YOU","ja":"自分","id":"part_you"}}
				""", StandardCharsets.UTF_8);
		return dir;
	}

	private static void delete(Path dir) throws IOException {
		try (Stream<Path> walk = Files.walk(dir)) {
			walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		}
	}

	private static void writeWav(Path path, int frames) throws IOException {
		byte[] data = new byte[frames * 2];
		AudioInputStream in = new AudioInputStream(new ByteArrayInputStream(data), FORMAT, frames);
		AudioSystem.write(in, AudioFileFormat.Type.WAVE, path.toFile());
	}

	@Test
	public void resolvesStaticTemplatesAndNames() throws IOException {
		Path dir = createPack();
		VoicePack pack = VoicePack.load(dir);
		// フォルダの外を指す entry は読み込まない
		Assert.assertEquals(pack.getClipCount(), 7);

		VoicePack.Resolution raid = pack.resolve("Raidwide");
		Assert.assertEquals(raid.ids(), List.of("L_raid"));
		Assert.assertEquals(raid.japanese(), "全体攻撃");
		Assert.assertNotNull(pack.clipsFor(raid));

		VoicePack.Resolution you = pack.resolve("Buster on YOU");
		Assert.assertEquals(you.ids(), List.of("part_you", "S_buster"));
		Assert.assertEquals(you.japanese(), "自分にタンク強攻撃");
		Assert.assertEquals(pack.clipsFor(you).size(), 2);

		// プレイヤー名は収録できないので、日本語の文だけを返して TTS に回す
		VoicePack.Resolution other = pack.resolve("Buster on Lily Tayler");
		Assert.assertNull(pack.clipsFor(other));
		Assert.assertEquals(other.japanese(), "Lily Taylerにタンク強攻撃");

		Assert.assertEquals(pack.resolve("Partners N").ids(), List.of("part_dir_n", "S_partners"));
		Assert.assertEquals(pack.resolve("Real Cross").japanese(), "本物十字");
		// 収録の無いファイル（S_cross）を含むものは TTS に回す
		Assert.assertNull(pack.clipsFor(pack.resolve("Real Cross")));
		Assert.assertEquals(pack.resolve("ディア").ids(), List.of("dot_1871"));
		Assert.assertNull(pack.resolve("Something the user customized"));
		delete(dir);
	}

	@Test
	public void concatenatesClipsWithGap() throws Exception {
		Path dir = createPack();
		VoicePack pack = VoicePack.load(dir);
		byte[] pcm = VoicePackPlayer.concat(pack.clipsFor(pack.resolve("Buster on YOU")));
		// 4800 フレーム × 2 + 間 40ms（1920 フレーム）、1 フレーム 2 バイト
		Assert.assertEquals(pcm.length, (4800 * 2 + 1920) * 2);
		delete(dir);
	}

	@Test
	public void listsPacksInParentFolder() throws IOException {
		Path parent = Files.createTempDirectory("voicepacks");
		Path cute = Files.createDirectories(parent.resolve("mia-cute"));
		Path serious = Files.createDirectories(parent.resolve("mia"));
		Files.createDirectories(parent.resolve("not-a-pack"));
		Files.writeString(cute.resolve("manifest.json"), "{}");
		Files.writeString(serious.resolve("manifest.json"), "{}");
		Assert.assertEquals(VoicePack.listPacks(parent), List.of(serious, cute));
		Assert.assertEquals(VoicePack.listPacks(parent.resolve("missing")), List.of());
		delete(parent);
	}

	@Test(expectedExceptions = IOException.class)
	public void rejectsMissingFiles() throws IOException {
		Path dir = createPack();
		Files.delete(dir.resolve("callouts.json"));
		try {
			VoicePack.load(dir);
		}
		finally {
			delete(dir);
		}
	}
}
