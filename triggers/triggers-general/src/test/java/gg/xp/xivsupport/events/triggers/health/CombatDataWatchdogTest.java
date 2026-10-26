package gg.xp.xivsupport.events.triggers.health;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Instant;

public class CombatDataWatchdogTest {

	private static final Instant START = Instant.parse("2026-10-20T11:23:00Z");

	private static Instant at(int second) {
		return START.plusSeconds(second);
	}

	private static int feed(CombatDataWatchdog.Monitor m, String type, int from, int to) {
		int warnings = 0;
		for (int s = from; s < to; s++) {
			if (m.line(type + "|ts|Change|1234", at(s)) != null) {
				warnings++;
			}
		}
		return warnings;
	}

	@Test
	public void warnsWhenCombatHasNoCastsOrAbilities() {
		CombatDataWatchdog.Monitor m = new CombatDataWatchdog.Monitor();
		m.combatChange(true);
		// 戦闘中でも、メモリから読む位置情報（261）などは来続ける
		Assert.assertEquals(feed(m, "261", 0, 25), 1);
		// 同じ戦闘ではもう鳴らさない
		Assert.assertEquals(feed(m, "261", 25, 60), 0);
		// 次の戦闘でも、間を置かずに鳴らし続けない
		m.combatChange(false);
		m.combatChange(true);
		Assert.assertEquals(feed(m, "261", 60, 100), 0);
	}

	@Test
	public void staysQuietWhenAbilitiesArrive() {
		CombatDataWatchdog.Monitor m = new CombatDataWatchdog.Monitor();
		m.combatChange(true);
		feed(m, "261", 0, 3);
		Assert.assertNull(m.line("21|ts|40000001|Boss|1234|Attack", at(3)));
		Assert.assertEquals(feed(m, "261", 4, 60), 0);
		// 戦闘外は判定しない
		m.combatChange(false);
		Assert.assertEquals(feed(m, "261", 60, 100), 0);
	}

	@Test
	public void warnsOnRepeatedParseErrors() {
		CombatDataWatchdog.Monitor m = new CombatDataWatchdog.Monitor();
		String error = "251|ts|NetworkMessageReceived Exception.  Message: 00000040|x";
		for (int i = 0; i < CombatDataWatchdog.Monitor.PARSE_ERROR_THRESHOLD - 1; i++) {
			Assert.assertNull(m.line(error, at(1)));
		}
		Assert.assertNotNull(m.line(error, at(1)));
		Assert.assertTrue(CombatDataWatchdog.isParseError("251|x|DeucalionClient: Named Pipe connected, but requires updated RECV signature.|y"));
		Assert.assertFalse(CombatDataWatchdog.isParseError("251|x|Found Signature [ZoneID] at offset [02AB0D70]|y"));
	}

	@Test
	public void ignoresMalformedLines() {
		CombatDataWatchdog.Monitor m = new CombatDataWatchdog.Monitor();
		m.combatChange(true);
		Assert.assertNull(m.line(null, at(0)));
		Assert.assertNull(m.line("", at(0)));
		Assert.assertNull(m.line("261|ts|x", null));
	}
}
