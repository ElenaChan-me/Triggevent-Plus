package gg.xp.xivsupport.triggers.cactbot.sb;

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
import gg.xp.xivsupport.events.actlines.events.CastLocationDataEvent;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.models.ArenaPos;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/trial/seiryu-ex.ts をもとに移植
@CalloutRepo(name = "The Wreath Of Snakes Extreme", duty = KnownDuty.TheWreathOfSnakesExtreme)
public class SeiryuEx extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long ASCENDING_MARKER = 0xA9;
	// 散開マーカーは4人に付き、残りの4人が塔を踏む
	private static final int ASCENDING_MARKER_COUNT = 4;
	private static final long SIGIL_FOLLOWUP_MS = 2_700;

	private XivState state;

	public SeiryuEx(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheWreathOfSnakesExtreme);
	}

	private boolean blazing;
	private boolean withForce;
	private final List<XivCombatant> ascendingMarkers = new ArrayList<>();

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		blazing = false;
		withForce = false;
		ascendingMarkers.clear();
	}

	@HandleEvents
	public void aramitama(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x37E4)) {
			blazing = true;
		}
	}

	@NpcCastCallout(0x37C3)
	private final ModifiableCallout<AbilityCastStart> fifthElement = ModifiableCallout.durationBasedCall("Fifth Element", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> cursekeeperOnYou = ModifiableCallout.durationBasedCall("Cursekeeper (On You)", "Tank Swap");
	private final ModifiableCallout<AbilityCastStart> cursekeeperTank = ModifiableCallout.durationBasedCall("Cursekeeper (Other Tank)", "Swap, then Buster");

	@HandleEvents
	public void cursekeeper(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x37D2)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(cursekeeperOnYou.getModified(event));
		}
		else if (state.playerJobMatches(Job::isTank)) {
			context.accept(cursekeeperTank.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> infirmSoul = new ModifiableCallout<>("Infirm Soul", "Away from Tanks");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> infirmSoulSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x37D2) && !state.playerJobMatches(Job::isTank),
			(e1, s) -> {
				s.waitMs(3_000);
				s.updateCall(infirmSoul, e1);
			});

	private final ModifiableCallout<AbilityCastStart> ascendingBait = new ModifiableCallout<>("Serpent Ascending", "Bait Puddles");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> ascendingSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x3C25),
			(e1, s) -> {
				ascendingMarkers.clear();
				s.waitMs(1_000);
				s.updateCall(ascendingBait, e1);
			});

	private final ModifiableCallout<HeadMarkerEvent> ascendingSpreadSupport = new ModifiableCallout<>("Serpent Ascending: Marker (Tank/Healer)", "Spread, DPS Get Towers");
	private final ModifiableCallout<HeadMarkerEvent> ascendingSpreadDps = new ModifiableCallout<>("Serpent Ascending: Marker (DPS)", "Spread, Tanks/Healers Get Towers");
	private final ModifiableCallout<HeadMarkerEvent> ascendingTowerSupport = new ModifiableCallout<>("Serpent Ascending: Tower (Tank/Healer)", "Get Tower, Tank/Healer Towers");
	private final ModifiableCallout<HeadMarkerEvent> ascendingTowerDps = new ModifiableCallout<>("Serpent Ascending: Tower (DPS)", "Get Tower, DPS Towers");

	@HandleEvents
	public void ascendingMarker(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(ASCENDING_MARKER) || !blazing) {
			return;
		}
		ascendingMarkers.add(event.getTarget());
		boolean support = state.playerJobMatches(job -> job.isTank() || job.isHealer());
		if (event.getTarget().isThePlayer()) {
			context.accept((support ? ascendingSpreadSupport : ascendingSpreadDps).getModified(event));
		}
		else if (ascendingMarkers.size() == ASCENDING_MARKER_COUNT
				&& ascendingMarkers.stream().noneMatch(XivCombatant::isThePlayer)) {
			context.accept((support ? ascendingTowerSupport : ascendingTowerDps).getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> handprintEast = new ModifiableCallout<>("Handprint (East)", "Go East");
	private final ModifiableCallout<AbilityUsedEvent> handprintWest = new ModifiableCallout<>("Handprint (West)", "Go West");

	@HandleEvents
	public void handprint(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x37E5)) {
			context.accept(handprintEast.getModified(event));
		}
		else if (event.abilityIdMatches(0x37E6)) {
			context.accept(handprintWest.getModified(event));
		}
	}

	private final ModifiableCallout<CastLocationDataEvent> snakesDir = new ModifiableCallout<>("Force of Nature: Snakes (First)", "Go {safe} Toward Snakes");
	private final ModifiableCallout<CastLocationDataEvent> snakesUnknown = new ModifiableCallout<>("Force of Nature: Snakes (First, Unknown)", "Go Toward Snakes");
	private final ModifiableCallout<CastLocationDataEvent> snakesKnockbackDir = new ModifiableCallout<>("Force of Nature: Snakes (Knockback)", "Out of Middle, Knockback to {safe}");
	private final ModifiableCallout<CastLocationDataEvent> snakesKnockbackUnknown = new ModifiableCallout<>("Force of Nature: Snakes (Knockback, Unknown)", "Out of Middle, Knockback Toward Snakes");

	@HandleEvents
	public void findSnakes(EventContext context, CastLocationDataEvent event) {
		if (!event.abilityIdMatches(0x37F7)) {
			return;
		}
		// 通常の詠唱ログは名前や位置が古いことがあるので、263 行の向きを使う
		Position pos = event.getPos();
		Double heading = pos != null ? Double.valueOf(pos.getHeading()) : event.getHeadingOnly();
		// 蛇は東西どちらかに出現して中央を向くので、向きの反対側が安置
		ArenaSector safe = heading == null ? ArenaSector.UNKNOWN : ArenaPos.combatantFacing(heading).opposite();
		boolean known = safe == ArenaSector.EAST || safe == ArenaSector.WEST;
		ModifiableCallout<CastLocationDataEvent> call;
		if (withForce) {
			call = known ? snakesKnockbackDir : snakesKnockbackUnknown;
		}
		else {
			call = known ? snakesDir : snakesUnknown;
		}
		withForce = true;
		context.accept(call.getModified(event, Map.of("safe", safe)));
	}

	private final ModifiableCallout<AbilityCastStart> numaSilence = ModifiableCallout.durationBasedCall("Numa-no-shiki", "Interrupt {event.source}");

	@HandleEvents
	public void numaSilence(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x37F4) && state.playerJobMatches(Job::caresAboutInterrupt)) {
			context.accept(numaSilence.getModified(event));
		}
	}

	@NpcCastCallout(0x3A01)
	private final ModifiableCallout<AbilityCastStart> sigilSingle = ModifiableCallout.durationBasedCall("Onmyo Sigil (Single)", "Out");

	private final ModifiableCallout<AbilityCastStart> sigilInOut = ModifiableCallout.durationBasedCall("Serpent-Eye Sigil (In/Out)", "In, then Out");
	private final ModifiableCallout<AbilityCastStart> sigilInOutFollowup = new ModifiableCallout<>("Serpent-Eye Sigil (In/Out, Second)", "Out");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> sigilInOutSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x3A05),
			(e1, s) -> {
				s.updateCall(sigilInOut, e1);
				s.waitMs(SIGIL_FOLLOWUP_MS);
				s.updateCall(sigilInOutFollowup, e1);
			});

	private final ModifiableCallout<AbilityCastStart> sigilOutIn = ModifiableCallout.durationBasedCall("Onmyo Sigil (Out/In)", "Out, then In");
	private final ModifiableCallout<AbilityCastStart> sigilOutInFollowup = new ModifiableCallout<>("Onmyo Sigil (Out/In, Second)", "In");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> sigilOutInSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x3A03),
			(e1, s) -> {
				s.updateCall(sigilOutIn, e1);
				s.waitMs(SIGIL_FOLLOWUP_MS);
				s.updateCall(sigilOutInFollowup, e1);
			});

	private final ModifiableCallout<AbilityCastStart> swimLessons = new ModifiableCallout<>("Swim Lessons", "Pop Sprint");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> swimLessonsSq = SqtTemplates.sq(35_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x37CB),
			(e1, s) -> {
				s.waitMs(28_000);
				s.updateCall(swimLessons, e1);
			});
}
