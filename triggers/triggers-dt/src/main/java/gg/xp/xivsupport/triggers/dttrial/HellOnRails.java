package gg.xp.xivsupport.triggers.dttrial;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.*;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SequentialTriggerConcurrencyMode;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/trial/doomtrain.ts をもとに移植
@CalloutRepo(name = "Hell on Rails (Doomtrain)", duty = KnownDuty.HellOnRails)
public class HellOnRails extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public HellOnRails(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.HellOnRails);
	}

	// 車両は南へ50yずつ並んでいて、中心のxはすべて100
	private static final double carCenterX = 100;
	private static final Position car4Center = Position.of2d(100, 250);
	private static final Position car5Center = Position.of2d(100, 300);

	private enum Phase {
		CAR1,
		CAR2,
		ADD,
		CAR3,
		CAR4,
		CAR5
	}

	private Phase phase = Phase.CAR1;
	private int hailMoveCount = -1;
	private boolean hailNeedMotion = true;
	private boolean hailActive;
	private boolean hailFirstCalled;

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		phase = Phase.CAR1;
		hailMoveCount = -1;
		hailNeedMotion = true;
		hailActive = false;
		hailFirstCalled = false;
	}

	@HandleEvents
	public void phaseTrackerCasts(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0xB237)) {
			if (phase == Phase.CAR1) {
				phase = Phase.CAR2;
			}
			else if (phase == Phase.CAR2) {
				phase = Phase.CAR3;
			}
			else if (phase == Phase.CAR3) {
				phase = Phase.CAR4;
			}
		}
		else if (event.abilityIdMatches(0xB246)) {
			phase = Phase.ADD;
		}
		else if (event.abilityIdMatches(0xB253)) {
			phase = Phase.CAR5;
		}
	}

	@HandleEvents
	public void phaseTrackerAbilities(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0xB24C)) {
			phase = Phase.CAR4;
		}
	}

	@NpcCastCallout(0xB232)
	private final ModifiableCallout<AbilityCastStart> lightningExpress = ModifiableCallout.durationBasedCall("Lightning Express", "Knockback, Avoid Lasers");

	@NpcCastCallout(0xB239)
	private final ModifiableCallout<AbilityCastStart> windpipe = ModifiableCallout.durationBasedCall("Windpipe", "Draw In then Away from Front");

	@NpcCastCallout(0xB237)
	private final ModifiableCallout<AbilityCastStart> unlimitedExpress = ModifiableCallout.durationBasedCall("Unlimited Express", "Raidwide");

	private final ModifiableCallout<HeadMarkerEvent> tankbuster = new ModifiableCallout<>("Tankbuster on You", "Tank Cleave on You");
	private final ModifiableCallout<HeadMarkerEvent> addTankCleave = new ModifiableCallout<>("Add Phase: Tank Cleave on You", "Tank Cleave on You", 7_600);

	@HandleEvents
	public void headmarkers(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x157)) {
			if (event.getTarget().isThePlayer()) {
				context.accept(tankbuster.getModified(event));
			}
		}
		else if (event.markerIdMatches(0x282)) {
			// プレイヤーは副ターゲット側に入ってくることがあるので両方見る
			if (event.eitherTargetMatches(XivCombatant::isThePlayer)) {
				context.accept(addTankCleave.getModified(event));
			}
		}
	}

	private final ModifiableCallout<AbilityCastStart> turretSide = ModifiableCallout.durationBasedCall("Turret Crossing", "Avoid {dir} Turrets");

	// 複数の砲台がほぼ同時に詠唱するので、最初の1本だけを見る
	private final RepeatSuppressor turretSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void turrets(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0xB23D)) {
			return;
		}
		if (!turretSupp.check(event)) {
			return;
		}
		Position pos = event.getSource().getPos();
		if (pos == null) {
			pos = state.getLatestCombatantData(event.getSource()).getPos();
		}
		if (pos == null) {
			return;
		}
		ArenaSector dir = pos.x() < carCenterX ? ArenaSector.WEST : ArenaSector.EAST;
		context.accept(turretSide.getModified(event, Map.of("dir", dir)));
	}

	@NpcCastCallout(0xB250)
	private final ModifiableCallout<AbilityCastStart> derailmentSiege = ModifiableCallout.durationBasedCall("Derailment Siege", "Tower x3 then Next Platform");

	@NpcCastCallout(0xB244)
	private final ModifiableCallout<AbilityCastStart> headlight = ModifiableCallout.durationBasedCall("Headlight", "Down");

	@NpcCastCallout(0xB242)
	private final ModifiableCallout<AbilityCastStart> thunderousBreath = ModifiableCallout.durationBasedCall("Thunderous Breath", "Up");

	@NpcCastCallout(0xB9A9)
	private final ModifiableCallout<AbilityCastStart> batteringArms = ModifiableCallout.durationBasedCall("Battering Arms", "Tower x3");

	private final ModifiableCallout<AbilityUsedEvent> shockwave = new ModifiableCallout<>("Shockwave", "Raidwide");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> shockwaveSq = SqtTemplates.sq(10_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0xB24E),
			(e1, s) -> {
				// 実際の全体攻撃はこの後に来るので少し遅らせる
				s.waitMs(2_700);
				s.updateCall(shockwave, e1);
			});

	/*
	Hail of Thunder
	B258/B259/B25A で雷雲が何回(2/3/4)移動するかが決まる。1回の移動は90度。
	3回の場合は最初に動き出した向きから時計回りか反時計回りかを判定する。
	 */
	private static final long hailNpcId = 0x4A36;
	// 位置更新の誤差で移動開始と誤判定しないためのしきい値
	private static final double hailMoveThreshold = 0.1;

	private final ModifiableCallout<AbilityUsedEvent> hailFirst = new ModifiableCallout<>("Hail of Thunder: First", "South", 8_000);

	private final ModifiableCallout<?> hailFrontSafe = new ModifiableCallout<>("Hail of Thunder: Front Safe", "Front Safe", 13_500);
	private final ModifiableCallout<?> hailRightSafe = new ModifiableCallout<>("Hail of Thunder: Right Safe", "Right Safe", 13_500);
	private final ModifiableCallout<?> hailBackSafe = new ModifiableCallout<>("Hail of Thunder: Back Safe", "Back Safe", 13_500);
	private final ModifiableCallout<?> hailLeftSafe = new ModifiableCallout<>("Hail of Thunder: Left Safe", "Left Safe", 13_500);

	@HandleEvents
	public void hailTracker(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0xB258)) {
			hailMoveCount = 2;
			if (!hailFirstCalled) {
				hailFirstCalled = true;
				context.accept(hailFirst.getModified(event));
			}
		}
		else if (event.abilityIdMatches(0xB259)) {
			hailMoveCount = 3;
		}
		else if (event.abilityIdMatches(0xB25A)) {
			hailMoveCount = 4;
		}
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> hailResetSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xB25B),
			(e1, s) -> {
				// 詠唱直後はまだ前回の移動が残っているので、少し待ってから次の移動を拾う
				s.waitMs(1_000);
				hailNeedMotion = true;
			});

	private static double normalizeDelta(double a) {
		double tau = Math.PI * 2;
		a = (a + Math.PI) % tau;
		if (a < 0) {
			a += tau;
		}
		return a - Math.PI;
	}

	private long hailDurationMs() {
		return switch (hailMoveCount) {
			case 2 -> 7_500;
			case 3 -> 10_500;
			default -> 13_500;
		};
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> hailOfThunderSq = SqtTemplates.sq(180_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xB9A7),
			(e1, s) -> {
				hailActive = true;
				hailMoveCount = 4;
				// 魔法陣展開の後に追加された雷雲を探す。前の車両の雷雲が残っていても混同しないようにする
				Set<Long> existing = state.npcsById(hailNpcId).stream().map(XivCombatant::getId).collect(Collectors.toSet());
				XivCombatant hail = null;
				while (hail == null && hailActive) {
					s.waitThenRefreshCombatants(100);
					List<XivCombatant> candidates = state.npcsById(hailNpcId);
					hail = candidates.stream().filter(cbt -> !existing.contains(cbt.getId())).findFirst().orElse(null);
				}
				if (hail == null) {
					return;
				}
				// 0=北(前), 1=東(右), 2=南(後), 3=西(左)
				int lastIdx = 0;
				Position prev = null;
				while (hailActive) {
					s.waitThenRefreshCombatants(100);
					Position cur = state.getLatestCombatantData(hail).getPos();
					if (cur == null) {
						continue;
					}
					boolean moved = prev != null && prev.distanceFrom2D(cur) > hailMoveThreshold;
					prev = cur;
					if (!moved || !hailNeedMotion) {
						continue;
					}
					hailNeedMotion = false;
					if (hailMoveCount == 2) {
						lastIdx = (lastIdx + 2) % 4;
					}
					else if (hailMoveCount == 3) {
						Position center = phase == Phase.CAR4 ? car4Center : car5Center;
						double oldAngle = Math.PI - (lastIdx / 4.0) * (Math.PI * 2);
						double newAngle = Math.atan2(cur.x() - center.x(), cur.y() - center.y());
						if (normalizeDelta(newAngle - oldAngle) > 0) {
							lastIdx = (lastIdx + 1) % 4;
						}
						else {
							lastIdx = (lastIdx + 3) % 4;
						}
					}
					int safeIdx = (lastIdx + 2) % 4;
					ModifiableCallout<?> call = switch (safeIdx) {
						case 0 -> hailFrontSafe;
						case 1 -> hailRightSafe;
						case 2 -> hailBackSafe;
						default -> hailLeftSafe;
					};
					s.updateCall(call);
					// 移動中に再度判定しないよう、雷雲が止まるまで待つ
					s.waitMs(hailDurationMs());
					s.expireLastCall();
					prev = null;
				}
			}).setConcurrency(SequentialTriggerConcurrencyMode.REPLACE_OLD);

}
