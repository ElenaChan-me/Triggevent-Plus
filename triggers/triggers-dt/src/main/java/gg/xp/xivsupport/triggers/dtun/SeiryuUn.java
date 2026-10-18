package gg.xp.xivsupport.triggers.dtun;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.Job;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.ArenaPos;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/trial/seiryu-un.ts をもとに移植
@CalloutRepo(name = "Seiryu Unreal", duty = KnownDuty.SeiryuUn)
public class SeiryuUn extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;
	// 00A9 は荒魂燃焼以外でも使われるため、荒魂燃焼の詠唱後だけ昇り龍として扱う
	private volatile boolean blazing;
	private volatile boolean withForce;

	public SeiryuUn(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.SeiryuUn);
	}

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		blazing = false;
		withForce = false;
	}

	private boolean playerIsSupport() {
		Job job = state.getPlayerJob();
		return job != null && (job.isTank() || job.isHealer());
	}

	@HandleEvents
	public void aramitamaTracking(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0xABDB)) {
			blazing = true;
		}
	}

	@NpcCastCallout(0xABBA)
	private final ModifiableCallout<AbilityCastStart> fifthElement = ModifiableCallout.durationBasedCall("Fifth Element", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> cursekeeperOnYou = ModifiableCallout.durationBasedCall("Cursekeeper (on You)", "Tank Swap");
	private final ModifiableCallout<AbilityCastStart> cursekeeperOtherTank = ModifiableCallout.durationBasedCall("Cursekeeper (Other Tank)", "Swap, then Buster");
	private final ModifiableCallout<AbilityCastStart> infirmSoul = ModifiableCallout.durationBasedCall("Infirm Soul", "Away from Tanks");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> cursekeeperSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xABC9),
			(e1, s) -> {
				boolean isTank = state.playerJobMatches(Job::isTank);
				if (e1.getTarget().isThePlayer()) {
					s.updateCall(cursekeeperOnYou, e1);
				}
				else if (isTank) {
					s.updateCall(cursekeeperOtherTank, e1);
				}
				if (!isTank) {
					s.waitMs(3_000);
					s.updateCall(infirmSoul, e1);
				}
			});

	private final ModifiableCallout<AbilityCastStart> ascendingStack = ModifiableCallout.durationBasedCall("Serpent Ascending", "Bait Puddles");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> ascendingStackSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xAC02),
			(e1, s) -> {
				s.waitMs(1_000);
				s.updateCall(ascendingStack, e1);
			});

	private final ModifiableCallout<HeadMarkerEvent> ascendingSpreadDpsTowers = new ModifiableCallout<>("Serpent Ascending: Marker on You (Tank/Healer)", "Spread, DPS Get Towers");
	private final ModifiableCallout<HeadMarkerEvent> ascendingSpreadSupportTowers = new ModifiableCallout<>("Serpent Ascending: Marker on You (DPS)", "Spread, Tanks and Healers Get Towers");
	private final ModifiableCallout<HeadMarkerEvent> ascendingTowerSupport = new ModifiableCallout<>("Serpent Ascending: No Marker (Tank/Healer)", "Get Tower, Tank and Healer Towers");
	private final ModifiableCallout<HeadMarkerEvent> ascendingTowerDps = new ModifiableCallout<>("Serpent Ascending: No Marker (DPS)", "Get Tower, DPS Towers");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> ascendingMarkersSq = SqtTemplates.sq(10_000,
			HeadMarkerEvent.class, hme -> blazing && hme.markerIdMatches(0xA9),
			(e1, s) -> {
				boolean support = playerIsSupport();
				List<HeadMarkerEvent> markers = new ArrayList<>();
				markers.add(e1);
				if (e1.getTarget().isThePlayer()) {
					s.updateCall(support ? ascendingSpreadDpsTowers : ascendingSpreadSupportTowers, e1);
				}
				markers.addAll(s.waitEventsQuickSuccession(3, HeadMarkerEvent.class, hme -> hme.markerIdMatches(0xA9)));
				HeadMarkerEvent mine = markers.stream().filter(m -> m.getTarget().isThePlayer()).findFirst().orElse(null);
				if (mine != null) {
					if (mine != e1) {
						s.updateCall(support ? ascendingSpreadDpsTowers : ascendingSpreadSupportTowers, mine);
					}
				}
				else if (markers.size() == 4) {
					s.updateCall(support ? ascendingTowerSupport : ascendingTowerDps, markers.get(markers.size() - 1));
				}
			});

	private final ModifiableCallout<AbilityUsedEvent> handprintEast = new ModifiableCallout<>("Handprint (East Safe)", "Go East");
	private final ModifiableCallout<AbilityUsedEvent> handprintWest = new ModifiableCallout<>("Handprint (West Safe)", "Go West");

	@HandleEvents
	public void handprint(EventContext context, AbilityUsedEvent event) {
		if (!event.isFirstTarget()) {
			return;
		}
		if (event.abilityIdMatches(0xABDC)) {
			context.accept(handprintEast.getModified(event));
		}
		else if (event.abilityIdMatches(0xABDD)) {
			context.accept(handprintWest.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> coursingRiverSafe = ModifiableCallout.durationBasedCall("Coursing River", "Go {safe} Toward Snakes");
	private final ModifiableCallout<AbilityCastStart> coursingRiverUnknown = ModifiableCallout.durationBasedCall("Coursing River (Unknown Side)", "Go Toward Snakes");
	private final ModifiableCallout<AbilityCastStart> coursingRiverForceSafe = ModifiableCallout.durationBasedCall("Coursing River with Force of Nature", "Out of Middle, Knockback {safe}");
	private final ModifiableCallout<AbilityCastStart> coursingRiverForceUnknown = ModifiableCallout.durationBasedCall("Coursing River with Force of Nature (Unknown Side)", "Out of Middle, Knockback Toward Snakes");

	// 蛇が複数体で同時に詠唱しても2回目扱いにしないための抑制
	private final RepeatSuppressor coursingRiverSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> coursingRiverSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xABEE),
			(e1, s) -> {
				if (!coursingRiverSupp.check(e1)) {
					return;
				}
				// 蛇は東西どちらかに湧いて反対側を向くので、向きの逆が安置
				ArenaSector facing;
				var locationInfo = e1.getLocationInfo();
				if (locationInfo == null) {
					s.waitThenRefreshCombatants(100);
					XivCombatant source = state.getLatestCombatantData(e1.getSource());
					facing = ArenaPos.combatantFacing(source);
				}
				else {
					facing = ArenaPos.combatantFacing(locationInfo.getBestHeading());
				}
				ArenaSector safe = facing == ArenaSector.UNKNOWN ? ArenaSector.UNKNOWN : facing.opposite();
				boolean known = safe == ArenaSector.EAST || safe == ArenaSector.WEST;
				s.setParam("safe", safe);
				if (withForce) {
					s.updateCall(known ? coursingRiverForceSafe : coursingRiverForceUnknown, e1);
				}
				else {
					s.updateCall(known ? coursingRiverSafe : coursingRiverUnknown, e1);
				}
				withForce = true;
			});

	private final ModifiableCallout<AbilityCastStart> numaInterrupt = ModifiableCallout.durationBasedCall("Numa-no-shiki Interrupt", "Interrupt {event.source}");

	@HandleEvents
	public void numaInterruptCast(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0xABEB) && state.playerJobMatches(Job::caresAboutInterrupt)) {
			context.accept(numaInterrupt.getModified(event));
		}
	}

	@NpcCastCallout(0xABF8)
	private final ModifiableCallout<AbilityCastStart> onmyoSigil = ModifiableCallout.durationBasedCall("Onmyo Sigil", "Out");

	private final ModifiableCallout<AbilityCastStart> inOut = ModifiableCallout.durationBasedCall("In/Out", "In then Out");
	private final ModifiableCallout<AbilityCastStart> inOutSecond = new ModifiableCallout<>("In/Out: Out", "Out");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> inOutSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xABFC),
			(e1, s) -> {
				s.updateCall(inOut, e1);
				s.waitMs(2_700);
				s.updateCall(inOutSecond, e1);
			});

	private final ModifiableCallout<AbilityCastStart> outIn = ModifiableCallout.durationBasedCall("Out/In", "Out then In");
	private final ModifiableCallout<AbilityCastStart> outInSecond = new ModifiableCallout<>("Out/In: In", "In");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> outInSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xABFA),
			(e1, s) -> {
				s.updateCall(outIn, e1);
				s.waitMs(2_700);
				s.updateCall(outInSecond, e1);
			});

	private final ModifiableCallout<AbilityCastStart> swimLessons = new ModifiableCallout<>("Swim Lessons", "Sprint");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> swimLessonsSq = SqtTemplates.sq(40_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xABC2),
			(e1, s) -> {
				s.waitMs(28_000);
				s.updateCall(swimLessons, e1);
			});

}
