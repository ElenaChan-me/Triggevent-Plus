package gg.xp.xivsupport.gui;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class VerifyLogTest {

	@Test
	public void parsesOptions() {
		VerifyLog.Options o = VerifyLog.Options.parse(new String[]{"a.log", "--pack", "packs/mia", "--all"});
		Assert.assertEquals(o.log(), Path.of("a.log"));
		Assert.assertEquals(o.pack(), Path.of("packs/mia"));
		Assert.assertNull(o.out());
		Assert.assertTrue(o.all());
		Assert.assertThrows(IllegalArgumentException.class, () -> VerifyLog.Options.parse(new String[]{}));
		Assert.assertThrows(IllegalArgumentException.class, () -> VerifyLog.Options.parse(new String[]{"a.log", "--pack"}));
		Assert.assertThrows(IllegalArgumentException.class, () -> VerifyLog.Options.parse(new String[]{"a.log", "--what"}));
		Assert.assertEquals(VerifyLog.defaultOutput(Path.of("logs", "Network_1.log")), Path.of("logs", "Network_1.callouts.md"));
	}

	@Test
	public void flagsOverlappingAndLateCalls() {
		List<VerifyLog.Call> calls = List.of(
				new VerifyLog.Call(1, "zone", 10_000, "Raidwide", "Raidwide", "全体攻撃", VerifyLog.Voice.PACK),
				new VerifyLog.Call(1, "zone", 10_500, "Spread", "Spread (NOW)", "散開", VerifyLog.Voice.PACK),
				// プルが変わって時刻が戻った場合は重なりとみなさない
				new VerifyLog.Call(2, "zone", 100, "Raidwide", "Raidwide", "全体攻撃", VerifyLog.Voice.NONE));
		List<String> issues = VerifyLog.issues(calls);
		Assert.assertEquals(issues.size(), 2, issues.toString());
		Assert.assertEquals(VerifyLog.time(75_300), "1:15.3");
	}

	@Test
	public void summarizesByZoneAndKeepsGoingOnBrokenLogs() {
		List<VerifyLog.LogResult> results = List.of(
				new VerifyLog.LogResult("a.log", List.of(
						new VerifyLog.Call(1, "P1S", 1000, "Raidwide", "Raidwide", "全体攻撃", VerifyLog.Voice.PACK),
						new VerifyLog.Call(1, "P1S", 1200, "Mystery", "Mystery", "Mystery", VerifyLog.Voice.NONE)), null),
				new VerifyLog.LogResult("b.log", List.of(
						new VerifyLog.Call(1, "P1S", 5000, "Raidwide", "Raidwide", "全体攻撃", VerifyLog.Voice.PACK)), null),
				new VerifyLog.LogResult("broken.log", List.of(), "java.lang.IllegalStateException: bad"));
		String summary = VerifyLog.summarize("logs", null, results);
		Assert.assertTrue(summary.contains("ログ 3 件（読めなかったもの 1 件）"), summary);
		// P1S は2つのログに出て、コールアウト 3 件、うち英語のまま 1 件、重なり 1 件
		Assert.assertTrue(summary.contains("| P1S | 2 | 3 | 2 | 0 | 1 | 1 |"), summary);
		Assert.assertTrue(summary.contains("a.log #1 0:01.2"), summary);
		Assert.assertTrue(summary.contains("- Mystery"), summary);
		Assert.assertTrue(summary.contains("broken.log: java.lang.IllegalStateException"), summary);
	}

	@Test
	public void replaysSampleLog() throws IOException {
		Path log = Files.createTempFile("verifylog", ".log");
		try (InputStream in = VerifyLogTest.class.getResourceAsStream("/asphodelos1.log")) {
			Assert.assertNotNull(in);
			Files.copy(in, log, StandardCopyOption.REPLACE_EXISTING);
		}
		try {
			List<VerifyLog.Call> calls = VerifyLog.replay(log.toFile(), false, null);
			Assert.assertFalse(calls.isEmpty());
			// 対応表で日本語になるコールアウトがあること
			Assert.assertTrue(calls.stream().anyMatch(c -> c.voice() == VerifyLog.Voice.TTS_JA), calls.toString());
			String report = VerifyLog.format("asphodelos1.log", null, calls);
			Assert.assertTrue(report.contains("## プル #"), report);
		}
		finally {
			Files.deleteIfExists(log);
		}
	}
}
