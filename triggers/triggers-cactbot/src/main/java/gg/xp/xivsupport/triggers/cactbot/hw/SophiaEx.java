package gg.xp.xivsupport.triggers.cactbot.hw;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/trial/sophia-ex.ts をもとに移植
@CalloutRepo(name = "Containment Bay P1 T6 Extreme", duty = KnownDuty.ContainmentBayP1T6Extreme)
public class SophiaEx extends AutoChildEventHandler implements FilteredEventHandler {

	private static final int SCALE_ENTITY_COUNT = 8;
	private static final int SCALE_WEST_COUNT = 4;

	private XivState state;

	public SophiaEx(XivState state) {
		this.state = state;
		resetQuadrants();
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.ContainmentBayP1T6Extreme);
	}

	private final Map<ArenaSector, Boolean> isQuadrantSafe = new LinkedHashMap<>();
	private boolean seenThunder;
	private boolean clonesActive;
	private @Nullable List<Long> scaleSophias;
	private final List<Long> quasarTethers = new ArrayList<>();
	private boolean sadTethers;

	private void resetQuadrants() {
		isQuadrantSafe.clear();
		isQuadrantSafe.put(ArenaSector.NORTHWEST, true);
		isQuadrantSafe.put(ArenaSector.NORTHEAST, true);
		isQuadrantSafe.put(ArenaSector.SOUTHWEST, true);
		isQuadrantSafe.put(ArenaSector.SOUTHEAST, true);
	}

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		resetQuadrants();
		seenThunder = false;
		clonesActive = false;
		scaleSophias = null;
		quasarTethers.clear();
		sadTethers = false;
	}

	@NpcCastCallout(0x19C4)
	private final ModifiableCallout<AbilityCastStart> tankBuster = ModifiableCallout.durationBasedCall("Tankbuster", "Buster on {event.target}, Swap");

	@NpcCastCallout(0x19B0)
	private final ModifiableCallout<AbilityCastStart> thunder2 = ModifiableCallout.durationBasedCall("Thunder II", "Away from Front");

	@NpcCastCallout(0x19AC)
	private final ModifiableCallout<AbilityCastStart> thunder3 = ModifiableCallout.durationBasedCall("Thunder III", "Get Under");

	@NpcCastCallout(0x19AE)
	private final ModifiableCallout<AbilityCastStart> aero3 = ModifiableCallout.durationBasedCall("Aero III", "Out");

	@NpcCastCallout(0x19B6)
	private final ModifiableCallout<AbilityCastStart> divineSpark = ModifiableCallout.durationBasedCall("Divine Spark", "Look Away from {event.source}");

	@NpcCastCallout(0x19B8)
	private final ModifiableCallout<AbilityCastStart> gnosticRant = ModifiableCallout.durationBasedCall("Gnostic Rant", "Get Behind Lancer");

	private final ModifiableCallout<AbilityCastStart> infusionOnYou = ModifiableCallout.durationBasedCall("Infusion: On You", "Infusion on You");
	private final ModifiableCallout<AbilityCastStart> infusionOther = ModifiableCallout.durationBasedCall("Infusion: On Someone Else", "Infusion on {event.target}");

	@HandleEvents
	public void casts(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x1988)) {
			context.accept((event.getTarget().isThePlayer() ? infusionOnYou : infusionOther).getModified(event));
		}
		else if (event.abilityIdMatches(0x19AB)) {
			clonesActive = true;
		}
	}

	// 分身のサンダーは中央線上の分身にしか使われないので、象限の安全判定には影響しない
	private static @Nullable ArenaSector cloneQuadrant(Position pos) {
		long x = Math.round(pos.x());
		if (x == 0) {
			return null;
		}
		boolean south = pos.y() > 0;
		if (x < 0) {
			return south ? ArenaSector.SOUTHWEST : ArenaSector.NORTHWEST;
		}
		return south ? ArenaSector.SOUTHEAST : ArenaSector.NORTHEAST;
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> thunderSeenSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x19AB),
			(e1, s) -> {
				// 最初のサンダー分の線は除外したいので、少し遅らせてから判定を始める
				s.waitMs(1_000);
				seenThunder = true;
			});

	@HandleEvents
	public void tethers(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x2D)) {
			clonesActive = true;
			if (!seenThunder) {
				return;
			}
			Position pos = state.getLatestCombatantData(event.getSource()).getPos();
			if (pos == null) {
				return;
			}
			ArenaSector quadrant = cloneQuadrant(pos);
			if (quadrant != null) {
				isQuadrantSafe.put(quadrant, false);
			}
		}
		else if (event.tetherIdMatches(0x11) && !clonesActive) {
			quasarTethers.add(event.getSource().getId());
		}
	}

	// バルベロの分離に詠唱がないので、ソフィアから分かれた時点で知らせる
	private final ModifiableCallout<AbilityUsedEvent> lightDew = new ModifiableCallout<>("Light Dew", "Avoid Head Laser");

	@HandleEvents
	public void abilities(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x1983) && clonesActive && event.isFirstTarget()) {
			context.accept(lightDew.getModified(event));
		}
		else if (event.abilityIdMatches(0x19A8, 0x19A9) && scaleSophias == null) {
			setupScaleSophias(event);
		}
		if (event.abilityIdMatches(0x19A9)) {
			quasarTethers.clear();
			sadTethers = false;
		}
	}

	private final ModifiableCallout<AbilityCastStart> executeOne = ModifiableCallout.durationBasedCall("Execute (One Safe Spot)", "{dir1}");
	private final ModifiableCallout<AbilityCastStart> executeTwo = ModifiableCallout.durationBasedCall("Execute (Two Safe Spots)", "{dir1}/{dir2}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> executeSq = SqtTemplates.sq(30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x19AA),
			(e1, s) -> {
				List<ArenaSector> safe = isQuadrantSafe.entrySet().stream()
						.filter(Map.Entry::getValue)
						.map(Map.Entry::getKey)
						.toList();
				if (safe.size() == 1) {
					s.setParam("dir1", safe.get(0));
					s.updateCall(executeOne, e1);
				}
				else if (safe.size() >= 2) {
					s.setParam("dir1", safe.get(0));
					s.setParam("dir2", safe.get(1));
					s.updateCall(executeTwo, e1);
				}
				s.waitEvent(AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x19AA));
				// 処刑の後片付けは少し遅らせる
				s.waitMs(5_000);
				clonesActive = false;
				seenThunder = false;
				resetQuadrants();
			});

	/*
	天秤上の8体は ID が連番で、出現位置と連番のオフセットが固定で対応している。
	どれか1体の位置が分かれば残りも分かる。
	 */
	private void setupScaleSophias(AbilityUsedEvent event) {
		XivCombatant source = event.getSource();
		Position pos = source.getPos();
		if (pos == null) {
			pos = state.getLatestCombatantData(source).getPos();
		}
		if (pos == null) {
			return;
		}
		int yKey = (int) Math.floor(pos.y());
		Integer offset;
		if (pos.x() < 0) {
			offset = switch (yKey) {
				case -4 -> 0;
				case -11 -> 1;
				case 9 -> 2;
				case 3 -> 3;
				default -> null;
			};
		}
		else {
			offset = switch (yKey) {
				case 3 -> 4;
				case 9 -> 5;
				case -4 -> 6;
				case -11 -> 7;
				default -> null;
			};
		}
		if (offset == null) {
			return;
		}
		long seqStart = source.getId() - offset;
		List<Long> ids = new ArrayList<>();
		for (int i = 0; i < SCALE_ENTITY_COUNT; i++) {
			ids.add(seqStart + i);
		}
		scaleSophias = ids;
	}

	// 西側が負、東側が正。線の多い側が安全側になる
	private int findSafeDir() {
		List<Long> scale = scaleSophias;
		if (scale == null) {
			return 0;
		}
		int safeDir = 0;
		for (Long tether : quasarTethers) {
			int idx = scale.indexOf(tether);
			if (idx < 0) {
				continue;
			}
			safeDir += idx < SCALE_WEST_COUNT ? -1 : 1;
		}
		return safeDir;
	}

	private final ModifiableCallout<BaseEvent> eastHardTilt = new ModifiableCallout<>("Quasar: East (Hard Tilt)", "Go East, Hard Tilt", 12_000);
	private final ModifiableCallout<BaseEvent> eastSoftTilt = new ModifiableCallout<>("Quasar: East (Soft Tilt)", "Go East, Soft Tilt", 12_000);
	private final ModifiableCallout<BaseEvent> westHardTilt = new ModifiableCallout<>("Quasar: West (Hard Tilt)", "Go West, Hard Tilt", 12_000);
	private final ModifiableCallout<BaseEvent> westSoftTilt = new ModifiableCallout<>("Quasar: West (Soft Tilt)", "Go West, Soft Tilt", 12_000);

	private @Nullable ModifiableCallout<BaseEvent> tiltCall(int safeDir) {
		return switch (safeDir) {
			case 2 -> eastHardTilt;
			case 1 -> eastSoftTilt;
			case -2 -> westHardTilt;
			case -1 -> westSoftTilt;
			default -> null;
		};
	}

	private final RepeatSuppressor tiltTetherSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> tiltViaTetherSq = SqtTemplates.sq(5_000,
			TetherEvent.class, te -> te.tetherIdMatches(0x11) && !clonesActive && tiltTetherSupp.check(te),
			(e1, s) -> {
				// 線の収集が終わるのを待つ
				s.waitMs(500);
				// 最初のクエーサーを見逃して位置が分からない場合や、線の数が左右同数の場合は詠唱を待つ
				if (scaleSophias == null) {
					sadTethers = true;
					return;
				}
				int safeDir = findSafeDir();
				if (safeDir == 0) {
					sadTethers = true;
					return;
				}
				ModifiableCallout<BaseEvent> call = tiltCall(safeDir);
				if (call != null) {
					s.updateCall(call, e1);
				}
			});

	private final RepeatSuppressor tiltCastSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void tiltViaCast(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x19A9) || !sadTethers || !tiltCastSupp.check(event)) {
			return;
		}
		int safeDir = findSafeDir();
		// 最初のメテオ型クエーサーでは傾かない
		if (quasarTethers.size() == 4 && safeDir != 0) {
			return;
		}
		if (safeDir == 0) {
			// 青いクエーサー（19A9）を詠唱する側が常に危険側
			List<Long> scale = scaleSophias;
			if (scale == null) {
				return;
			}
			int idx = scale.indexOf(event.getSource().getId());
			if (idx < 0) {
				return;
			}
			safeDir = idx < SCALE_WEST_COUNT ? 2 : -2;
		}
		ModifiableCallout<BaseEvent> call = tiltCall(safeDir);
		if (call != null) {
			context.accept(call.getModified(event));
		}
	}

}
