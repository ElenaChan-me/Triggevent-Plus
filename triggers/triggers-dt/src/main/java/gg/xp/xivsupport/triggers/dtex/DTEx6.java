package gg.xp.xivsupport.triggers.dtex;

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
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/trial/doomtrain-ex.ts をもとに移植
@CalloutRepo(name = "EX6", duty = KnownDuty.DtEx6)
public class DTEx6 extends AutoChildEventHandler implements FilteredEventHandler {

	private static final Logger log = LoggerFactory.getLogger(DTEx6.class);

	private XivState state;

	public DTEx6(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.DtEx6);
	}

	// 車両は南へ50yずつ並んでいて、中心のxはすべて100
	private static final double carCenterX = 100;
	private static final Position car4Center = Position.of2d(100, 250);
	private static final Position car6Center = Position.of2d(100, 350);

	private enum Phase {
		CAR1,
		CAR2,
		ADD,
		CAR3,
		CAR4,
		CAR5,
		CAR6
	}

	private enum StoredMech {
		PARTNERS,
		SPREAD
	}

	private Phase phase = Phase.CAR1;
	private @Nullable StoredMech storedMech;
	private ArenaSector turretDir = ArenaSector.EAST;
	private int car2MechCount;
	private int car6MechCount;
	private int psychokinesisCount;
	private boolean addCleaveOnMe;
	private int hailMoveCount = -1;
	private boolean hailNeedMotion = true;
	private boolean hailActive;

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		phase = Phase.CAR1;
		storedMech = null;
		turretDir = ArenaSector.EAST;
		car2MechCount = 0;
		car6MechCount = 0;
		psychokinesisCount = 0;
		addCleaveOnMe = false;
		hailMoveCount = -1;
		hailNeedMotion = true;
		hailActive = false;
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
		}
		else if (event.abilityIdMatches(0xB246)) {
			phase = Phase.ADD;
		}
		else if (event.abilityIdMatches(0xB28F)) {
			if (phase == Phase.CAR3) {
				phase = Phase.CAR4;
			}
			else if (phase == Phase.CAR4) {
				phase = Phase.CAR5;
			}
			else {
				phase = Phase.CAR6;
			}
		}
	}

	@HandleEvents
	public void phaseTrackerAbilities(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0xB24C)) {
			phase = Phase.CAR3;
		}
	}

	private final ModifiableCallout<AbilityCastStart> overdraughtPartners = ModifiableCallout.durationBasedCall("Dead Man's Overdraught: Partners", "Partners Later");
	private final ModifiableCallout<AbilityCastStart> overdraughtSpread = ModifiableCallout.durationBasedCall("Dead Man's Overdraught: Spread", "Spread Later");

	@HandleEvents
	public void overdraught(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0xB260)) {
			storedMech = StoredMech.PARTNERS;
			context.accept(overdraughtPartners.getModified(event));
		}
		else if (event.abilityIdMatches(0xB25F)) {
			storedMech = StoredMech.SPREAD;
			context.accept(overdraughtSpread.getModified(event));
		}
	}

	@NpcAbilityUsedCallout(value = 0xB261, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> thirdRailMove = new ModifiableCallout<>("Third Rail: Move", "Move");

	// Dead Man's Express (B266) はノックバック、Dead Man's Windpipe (B280) は吸引。どちらも保存済みの散開/ペアが続く
	private final ModifiableCallout<AbilityCastStart> car1ExpressPartners = ModifiableCallout.durationBasedCall("Car 1: Express + Partners", "Knockback then Partners");
	private final ModifiableCallout<AbilityCastStart> car1ExpressSpread = ModifiableCallout.durationBasedCall("Car 1: Express + Spread", "Knockback then Spread");
	private final ModifiableCallout<AbilityCastStart> car1WindpipePartners = ModifiableCallout.durationBasedCall("Car 1: Windpipe + Partners", "Draw In then Partners");
	private final ModifiableCallout<AbilityCastStart> car1WindpipeSpread = ModifiableCallout.durationBasedCall("Car 1: Windpipe + Spread", "Draw In then Spread");

	private final ModifiableCallout<AbilityCastStart> car2ExpressPartners = ModifiableCallout.durationBasedCall("Car 2: Express + Partners", "Line of Sight {turret} then Knockback then Dodge Lasers then Partners");
	private final ModifiableCallout<AbilityCastStart> car2ExpressSpread = ModifiableCallout.durationBasedCall("Car 2: Express + Spread", "Line of Sight {turret} then Knockback then Dodge Lasers then Spread");
	private final ModifiableCallout<AbilityCastStart> car2WindpipePartners = ModifiableCallout.durationBasedCall("Car 2: Windpipe + Partners", "Line of Sight {turret} then Draw In then Away from Front then Partners");
	private final ModifiableCallout<AbilityCastStart> car2WindpipeSpread = ModifiableCallout.durationBasedCall("Car 2: Windpipe + Spread", "Line of Sight {turret} then Draw In then Away from Front then Spread");

	private final ModifiableCallout<AbilityCastStart> car6ExpressPartners = ModifiableCallout.durationBasedCall("Car 6: Express + Partners", "Knockback then Dodge Lasers then Partners");
	private final ModifiableCallout<AbilityCastStart> car6ExpressSpread = ModifiableCallout.durationBasedCall("Car 6: Express + Spread", "Knockback then Dodge Lasers then Spread");
	private final ModifiableCallout<AbilityCastStart> car6WindpipePartners = ModifiableCallout.durationBasedCall("Car 6: Windpipe + Partners", "Draw In then Away from Front then Partners");
	private final ModifiableCallout<AbilityCastStart> car6WindpipeSpread = ModifiableCallout.durationBasedCall("Car 6: Windpipe + Spread", "Draw In then Away from Front then Spread");
	private final ModifiableCallout<AbilityCastStart> car6ExpressPartnersBuster = ModifiableCallout.durationBasedCall("Car 6: Express + Partners + Tankbuster", "Knockback then Dodge Lasers then Partners then Tankbuster");
	private final ModifiableCallout<AbilityCastStart> car6ExpressSpreadBuster = ModifiableCallout.durationBasedCall("Car 6: Express + Spread + Tankbuster", "Knockback then Dodge Lasers then Spread then Tankbuster");
	private final ModifiableCallout<AbilityCastStart> car6WindpipePartnersBuster = ModifiableCallout.durationBasedCall("Car 6: Windpipe + Partners + Tankbuster", "Draw In then Away from Front then Partners then Tankbuster");
	private final ModifiableCallout<AbilityCastStart> car6WindpipeSpreadBuster = ModifiableCallout.durationBasedCall("Car 6: Windpipe + Spread + Tankbuster", "Draw In then Away from Front then Spread then Tankbuster");

	private static ModifiableCallout<AbilityCastStart> pick(boolean express, StoredMech mech,
	                                                        ModifiableCallout<AbilityCastStart> expressPartners,
	                                                        ModifiableCallout<AbilityCastStart> expressSpread,
	                                                        ModifiableCallout<AbilityCastStart> windpipePartners,
	                                                        ModifiableCallout<AbilityCastStart> windpipeSpread) {
		if (express) {
			return mech == StoredMech.PARTNERS ? expressPartners : expressSpread;
		}
		else {
			return mech == StoredMech.PARTNERS ? windpipePartners : windpipeSpread;
		}
	}

	@HandleEvents
	public void expressWindpipe(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0xB266, 0xB280)) {
			return;
		}
		boolean express = event.abilityIdMatches(0xB266);
		StoredMech mech = storedMech;
		if (mech == null) {
			// 途中参加などで Overdraught を見ていない場合は、後半の指示が出せない
			log.warn("Express/Windpipe without stored mechanic: {}", event);
			if (phase == Phase.CAR2) {
				car2MechCount++;
			}
			else if (phase == Phase.CAR6) {
				car6MechCount++;
			}
			return;
		}
		switch (phase) {
			case CAR1 -> context.accept(pick(express, mech, car1ExpressPartners, car1ExpressSpread, car1WindpipePartners, car1WindpipeSpread).getModified(event));
			case CAR2 -> {
				context.accept(pick(express, mech, car2ExpressPartners, car2ExpressSpread, car2WindpipePartners, car2WindpipeSpread).getModified(event, Map.of("turret", turretDir)));
				car2MechCount++;
			}
			case CAR6 -> {
				// 車両6の4回目のギミックだけ、直後にタンク強攻撃が続く
				if (car6MechCount == 3) {
					context.accept(pick(express, mech, car6ExpressPartnersBuster, car6ExpressSpreadBuster, car6WindpipePartnersBuster, car6WindpipeSpreadBuster).getModified(event));
				}
				else {
					context.accept(pick(express, mech, car6ExpressPartners, car6ExpressSpread, car6WindpipePartners, car6WindpipeSpread).getModified(event));
				}
				car6MechCount++;
			}
			default -> {
			}
		}
	}

	private final ModifiableCallout<AbilityCastStart> car2Tankbuster = ModifiableCallout.durationBasedCall("Car 2: Turrets + Tankbusters", "Line of Sight {turret} then Tankbusters");
	private final ModifiableCallout<AbilityCastStart> car6TurretLos = ModifiableCallout.durationBasedCall("Car 6: Turrets (First)", "Line of Sight {turret}");
	private final ModifiableCallout<AbilityCastStart> car6TurretUp = ModifiableCallout.durationBasedCall("Car 6: Turrets (Up)", "Up, Dodge Turrets");
	private final ModifiableCallout<AbilityCastStart> car6TurretDown = ModifiableCallout.durationBasedCall("Car 6: Turrets (Down)", "Down, Dodge Turrets");

	// 複数の砲台がほぼ同時に詠唱するので、最初の1本だけを見る
	private final RepeatSuppressor turretSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void turrets(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0xB271, 0xB272, 0xB273, 0xB276)) {
			return;
		}
		if (!turretSupp.check(event)) {
			return;
		}
		Position pos = event.getSource().getPos();
		if (pos == null) {
			pos = state.getLatestCombatantData(event.getSource()).getPos();
		}
		if (pos != null) {
			turretDir = pos.x() < carCenterX ? ArenaSector.WEST : ArenaSector.EAST;
		}
		if (phase == Phase.CAR2 && car2MechCount == 1) {
			context.accept(car2Tankbuster.getModified(event, Map.of("turret", turretDir)));
			car2MechCount++;
		}
		else if (phase == Phase.CAR6) {
			if (car6MechCount >= 1) {
				context.accept((turretDir == ArenaSector.EAST ? car6TurretUp : car6TurretDown).getModified(event));
			}
			else {
				context.accept(car6TurretLos.getModified(event, Map.of("turret", turretDir)));
			}
			car6MechCount++;
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> addHealerStacks = new ModifiableCallout<>("Add Phase: Healer Stacks", "Healer Stacks", 6_500);
	private final ModifiableCallout<HeadMarkerEvent> addSpread = new ModifiableCallout<>("Add Phase: Spread", "Spread", 6_500);
	private final ModifiableCallout<HeadMarkerEvent> addTankCleave = new ModifiableCallout<>("Add Phase: Tank Cleave on You", "Tank Cleave on You", 6_500);

	private final ModifiableCallout<HeadMarkerEvent> tankbuster = new ModifiableCallout<>("Car 3/4: Tankbuster on You", "Tankbuster");

	private final RepeatSuppressor addMechSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void headmarkers(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x19C)) {
			if (event.getTarget().isThePlayer()) {
				addCleaveOnMe = true;
			}
		}
		else if (event.markerIdMatches(0x27D, 0x27E)) {
			if (!addMechSupp.check(event)) {
				return;
			}
			if (addCleaveOnMe) {
				context.accept(addTankCleave.getModified(event));
			}
			else if (event.markerIdMatches(0x27D)) {
				context.accept(addHealerStacks.getModified(event));
			}
			else {
				context.accept(addSpread.getModified(event));
			}
			addCleaveOnMe = false;
		}
		else if (event.markerIdMatches(0x157)) {
			if ((phase == Phase.CAR3 || phase == Phase.CAR4) && event.getTarget().isThePlayer()) {
				context.accept(tankbuster.getModified(event));
			}
		}
	}

	@NpcCastCallout(0xB250)
	private final ModifiableCallout<AbilityCastStart> derailmentSiege3 = ModifiableCallout.durationBasedCall("Derailment Siege (Car 3)", "Tower x3 then Next Platform");

	@NpcCastCallout(0xB284)
	private final ModifiableCallout<AbilityCastStart> derailmentSiege4 = ModifiableCallout.durationBasedCall("Derailment Siege (Car 4)", "Tower x4 then Next Platform");

	@NpcCastCallout(0xB285)
	private final ModifiableCallout<AbilityCastStart> derailmentSiege5 = ModifiableCallout.durationBasedCall("Derailment Siege (Car 5)", "Tower x5 then Next Platform");

	@NpcCastCallout(0xB286)
	private final ModifiableCallout<AbilityCastStart> derailmentSiege6 = ModifiableCallout.durationBasedCall("Derailment Siege (Car 6)", "Tower x6 then Enrage");

	@NpcCastCallout(0xB27A)
	private final ModifiableCallout<AbilityCastStart> headlight = ModifiableCallout.durationBasedCall("Headlight", "Down then Up");

	@NpcCastCallout(0xB277)
	private final ModifiableCallout<AbilityCastStart> thunderousBreath = ModifiableCallout.durationBasedCall("Thunderous Breath", "Up then Down");

	private final ModifiableCallout<AbilityCastStart> psychokinesisBait = ModifiableCallout.durationBasedCall("Psychokinesis: Spread + Bait", "Spread then Bait Puddles");
	private final ModifiableCallout<AbilityCastStart> psychokinesisBuster = ModifiableCallout.durationBasedCall("Psychokinesis: Spread + Tankbusters", "Spread then Tankbusters");

	@HandleEvents
	public void psychokinesis(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0xB264)) {
			return;
		}
		psychokinesisCount++;
		// 2回目だけ後続がタンク強攻撃になる
		if (psychokinesisCount == 2) {
			context.accept(psychokinesisBuster.getModified(event));
		}
		else {
			context.accept(psychokinesisBait.getModified(event));
		}
	}

	/*
	Hail of Thunder
	B258/B259/B25A で雷雲が何回(2/3/4)移動するかが決まる。1回の移動は90度。
	3回の場合は最初に動き出した向きから時計回りか反時計回りかを判定する。
	 */
	private static final long hailNpcId = 0x4A36;
	// 位置更新の誤差で移動開始と誤判定しないためのしきい値
	private static final double hailMoveThreshold = 0.1;

	private final ModifiableCallout<?> hailFrontSafe = new ModifiableCallout<>("Hail of Thunder: Front Safe", "Front Safe, Stacks", 13_500);
	private final ModifiableCallout<?> hailRightSafe = new ModifiableCallout<>("Hail of Thunder: Right Safe", "Right Safe, Stacks", 13_500);
	private final ModifiableCallout<?> hailBackSafe = new ModifiableCallout<>("Hail of Thunder: Back Safe", "Back Safe, Stacks", 13_500);
	private final ModifiableCallout<?> hailLeftSafe = new ModifiableCallout<>("Hail of Thunder: Left Safe", "Left Safe, Stacks", 13_500);

	@HandleEvents
	public void hailTracker(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0xB258)) {
			hailMoveCount = 2;
		}
		else if (event.abilityIdMatches(0xB259)) {
			hailMoveCount = 3;
		}
		else if (event.abilityIdMatches(0xB25A)) {
			hailMoveCount = 4;
		}
		else if (event.abilityIdMatches(0xB292)) {
			hailNeedMotion = true;
		}
	}

	@HandleEvents
	public void hailEnd(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0xB284, 0xB286)) {
			hailActive = false;
		}
	}

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
				hailNeedMotion = true;
				Position center = phase == Phase.CAR4 ? car4Center : car6Center;
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
						double oldAngle = Math.PI - (lastIdx / 4.0) * (Math.PI * 2);
						double newAngle = Math.atan2(cur.x() - center.x(), cur.y() - center.y());
						// 3回動くので、最初に動いた向きとは逆側に90度ずれた位置で止まる
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
			});

}
