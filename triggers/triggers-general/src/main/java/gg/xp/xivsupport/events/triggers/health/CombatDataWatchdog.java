package gg.xp.xivsupport.events.triggers.health;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.ACTLogLineEvent;
import gg.xp.xivsupport.events.state.InCombatChangeEvent;
import gg.xp.xivsupport.gui.i18n.HubSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;

/**
 * 戦闘の情報（詠唱・技）が ACT から届いていないことを知らせる。
 * <p>
 * パッチ直後は FFXIV_ACT_Plugin がゲームの通信を読めず、戦闘中でも詠唱や技の行が出なくなる。
 * そうなるとトリガーが一切動かず、読み上げも表示も出ないまま原因が分からないので、気づけるように知らせる。
 * 判断の手がかりは2つ:
 * <ul>
 *     <li>戦闘が始まってしばらくたっても、詠唱（20）・技（21/22）の行が1つも来ない</li>
 *     <li>FFXIV_ACT_Plugin が通信の読み取りエラー（251 の NetworkMessageReceived Exception など）を続けて出している</li>
 * </ul>
 */
@CalloutRepo(name = "Triggevent Plus: ACT status", duty = KnownDuty.None)
public class CombatDataWatchdog {

	private static final Logger log = LoggerFactory.getLogger(CombatDataWatchdog.class);
	private static final boolean JAPANESE = "ja".equals(HubSettings.get().getLanguage());
	private static final int DISPLAY_SECONDS = 30;

	private final ModifiableCallout<ACTLogLineEvent> noCombatData = new ModifiableCallout<>(
			"No combat data from ACT",
			JAPANESE ? "戦闘の情報が届いていません" : "No combat data from ACT",
			JAPANESE ? "戦闘の情報が届いていません。FFXIV_ACT_Plugin が今のゲームに対応しているか確認してください"
					: "No combat data from ACT. Check that FFXIV_ACT_Plugin supports the current game version",
			ModifiableCallout.expiresIn(DISPLAY_SECONDS));

	private final Monitor monitor = new Monitor();

	@HandleEvents
	public void combatChange(EventContext context, InCombatChangeEvent event) {
		monitor.combatChange(event.isInCombat());
	}

	@HandleEvents
	public void logLine(EventContext context, ACTLogLineEvent event) {
		String reason = monitor.line(event.getLogLine(), timestamp(event));
		if (reason != null) {
			log.warn("Combat data problem: {}", reason);
			context.accept(noCombatData.getModified(event));
		}
	}

	private static Instant timestamp(ACTLogLineEvent event) {
		try {
			ZonedDateTime ts = event.getTimestamp();
			return ts == null ? null : ts.toInstant();
		}
		catch (RuntimeException e) {
			// 時刻の書式が違う行は判定に使わない
			return null;
		}
	}

	/**
	 * 判定だけを行う部分。時刻はログの行の時刻を使うので、ログの再生でも同じように動く。
	 */
	static final class Monitor {
		// 戦闘開始から最初の技までは長くても数秒なので、余裕を見てこれだけ待つ
		static final Duration NO_DATA_THRESHOLD = Duration.ofSeconds(20);
		// 読み取りエラーはパッチ後なら数秒で数十件出る。たまに出る1件では知らせない
		static final int PARSE_ERROR_THRESHOLD = 20;
		// 同じ不具合で何度も鳴らさない
		static final Duration REPEAT_INTERVAL = Duration.ofMinutes(10);

		private boolean inCombat;
		private Instant combatStart;
		private boolean combatDataSeen;
		private int parseErrors;
		private Instant lastWarning;

		synchronized void combatChange(boolean nowInCombat) {
			inCombat = nowInCombat;
			// 開始時刻はログの時刻にそろえたいので、次の行で決める
			combatStart = null;
			combatDataSeen = false;
		}

		/**
		 * @return 知らせるべきならその理由。知らせないなら null
		 */
		synchronized String line(String line, Instant now) {
			int sep = line == null ? -1 : line.indexOf('|');
			if (sep <= 0) {
				return null;
			}
			switch (line.substring(0, sep)) {
				case "20", "21", "22" -> {
					combatDataSeen = true;
					parseErrors = 0;
				}
				case "251" -> {
					if (isParseError(line) && ++parseErrors >= PARSE_ERROR_THRESHOLD) {
						parseErrors = 0;
						return warn(now, "FFXIV_ACT_Plugin reports network parse errors");
					}
				}
				default -> {
				}
			}
			if (inCombat && !combatDataSeen && now != null) {
				if (combatStart == null) {
					combatStart = now;
				}
				else if (Duration.between(combatStart, now).compareTo(NO_DATA_THRESHOLD) >= 0) {
					// 一度判定したら、この戦闘ではもう判定しない
					combatDataSeen = true;
					return warn(now, "No cast/ability lines " + NO_DATA_THRESHOLD.toSeconds() + "s into combat");
				}
			}
			return null;
		}

		private String warn(Instant now, String reason) {
			Instant at = now == null ? Instant.now() : now;
			if (lastWarning != null && Duration.between(lastWarning, at).compareTo(REPEAT_INTERVAL) < 0) {
				return null;
			}
			lastWarning = at;
			return reason;
		}
	}

	static boolean isParseError(String line) {
		return line.contains("NetworkMessageReceived Exception") || line.contains("requires updated RECV signature");
	}
}
