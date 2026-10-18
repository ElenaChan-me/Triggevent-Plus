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
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.models.Position;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/trial/shinryu-un.ts をもとに移植
@CalloutRepo(name = "Shinryu Unreal", duty = KnownDuty.ShinryuUn)
public class ShinryuUn extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;
	// 同じ技でもフェーズによって対処が変わるため、ボスの詠唱でフェーズを追う
	private volatile int phase;
	private volatile boolean finalWing;

	public ShinryuUn(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.ShinryuUn);
	}

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		phase = 0;
		finalWing = false;
	}

	@HandleEvents
	public void phaseTracking(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0xC43A)) {
			phase = 1;
		}
		else if (event.abilityIdMatches(0xC443)) {
			phase = 2;
		}
		else if (event.abilityIdMatches(0xC440)) {
			phase = 3;
		}
		else if (event.abilityIdMatches(0xC47B)) {
			phase = 4;
		}
	}

	private final ModifiableCallout<AbilityCastStart> akhMornOnYou = ModifiableCallout.durationBasedCall("Akh Morn (on You)", "Akh Morn on You");
	private final ModifiableCallout<AbilityCastStart> akhMornOnTank = ModifiableCallout.durationBasedCall("Akh Morn (Tank)", "Akh Morn on {event.target}");
	private final ModifiableCallout<AbilityCastStart> akhRhai = ModifiableCallout.durationBasedCall("Akh Morn (Akh Rhai)", "Akh Rhai: Spread and Move");

	@HandleEvents
	public void akhMorn(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0xC44F)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(akhMornOnYou.getModified(event));
		}
		else if (state.playerJobMatches(Job::isTank)) {
			context.accept(akhMornOnTank.getModified(event));
		}
		else {
			context.accept(akhRhai.getModified(event));
		}
	}

	@NpcCastCallout(0xC439)
	private final ModifiableCallout<AbilityCastStart> diamondDust = ModifiableCallout.durationBasedCall("Diamond Dust", "Stack, Don't Move");

	@NpcCastCallout(0xC46D)
	private final ModifiableCallout<AbilityCastStart> dragonfist = ModifiableCallout.durationBasedCall("Dragonfist", "Out of Middle");

	@NpcCastCallout(0xC437)
	private final ModifiableCallout<AbilityCastStart> hellfire = ModifiableCallout.durationBasedCall("Hellfire", "Get in Water");

	private final ModifiableCallout<AbilityCastStart> hypernovaFrozen = ModifiableCallout.durationBasedCall("Hypernova (Phase 3)", "Stop, Get Frozen");
	private final ModifiableCallout<AbilityCastStart> hypernovaWater = ModifiableCallout.durationBasedCall("Hypernova", "Stack in Water");

	@HandleEvents
	public void hypernova(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0xC481, 0xC444)) {
			context.accept((phase == 3 ? hypernovaFrozen : hypernovaWater).getModified(event));
		}
	}

	@NpcCastCallout(0xC438)
	private final ModifiableCallout<AbilityCastStart> judgmentBolt = ModifiableCallout.durationBasedCall("Judgment Bolt", "Out of Water");

	private final ModifiableCallout<AbilityCastStart> levinboltPhase3 = ModifiableCallout.durationBasedCall("Levinbolt (Phase 3)", "Bait Bolt, Keep Moving");
	private final ModifiableCallout<AbilityCastStart> levinboltPhase3Move = new ModifiableCallout<>("Levinbolt (Phase 3): Move Away", "Move Away");
	private final ModifiableCallout<AbilityCastStart> levinbolt = ModifiableCallout.durationBasedCall("Levinbolt", "Spread, No Water");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> levinboltSq = SqtTemplates.sq(15_000,
			// 翼が自身を対象に詠唱するものだけを見る
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xC446, 0xC482, 0xC486) && !acs.getTarget().isPc(),
			(e1, s) -> {
				if (phase == 3) {
					s.updateCall(levinboltPhase3, e1);
					s.waitMs(9_500);
					s.updateCall(levinboltPhase3Move, e1);
				}
				else {
					s.updateCall(levinbolt, e1);
				}
			});

	private final ModifiableCallout<AbilityUsedEvent> icicleWest = new ModifiableCallout<>("Icicle Impact (West)", "Icicle, Lean West");
	private final ModifiableCallout<AbilityUsedEvent> icicleEast = new ModifiableCallout<>("Icicle Impact (East)", "Icicle, Lean East");

	@HandleEvents
	public void icicle(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0xC44B) || !event.isFirstTarget()) {
			return;
		}
		Position pos = event.getSource().getPos();
		if (pos == null) {
			return;
		}
		long x = Math.round(pos.x());
		long y = Math.round(pos.y());
		if (x == -30 && y == -15) {
			context.accept(icicleWest.getModified(event));
		}
		else if (x == -30 && y == -25) {
			context.accept(icicleEast.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> tidalWave = new ModifiableCallout<>("Tidal Wave", "Knockback", 5_000);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> tidalWaveSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xC436),
			(e1, s) -> {
				s.waitMs(3_000);
				s.updateCall(tidalWave, e1);
			});

	private final ModifiableCallout<AbilityCastStart> finalTidalWave = ModifiableCallout.durationBasedCall("Tidal Wave (Enrage)", "No More Heals Needed");

	@HandleEvents
	public void finalTidalWaveCast(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0xC47B) && state.playerJobMatches(Job::isHealer)) {
			context.accept(finalTidalWave.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> tailSlap = new ModifiableCallout<>("Tail Slap", "Tail: Switch Targets");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> tailSlapSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xC43E),
			(e1, s) -> {
				s.waitMs(2_000);
				s.updateCall(tailSlap, e1);
			});

	@NpcCastCallout({0xBFEB, 0xBFEC, 0xC45F})
	private final ModifiableCallout<AbilityCastStart> gyreCharge = ModifiableCallout.durationBasedCall("Gyre Charge", "Avoid Divebomb");

	private final ModifiableCallout<AbilityCastStart> deathSentenceOnYou = ModifiableCallout.durationBasedCall("Death Sentence (on You)", "Death Sentence on You");
	private final ModifiableCallout<AbilityCastStart> deathSentenceOther = ModifiableCallout.durationBasedCall("Death Sentence (Other)", "Death Sentence on {event.target}");

	@HandleEvents
	public void deathSentence(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0xC466)) {
			context.accept((event.getTarget().isThePlayer() ? deathSentenceOnYou : deathSentenceOther).getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> teraSlashOnYou = ModifiableCallout.durationBasedCall("Tera Slash (on You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> teraSlashSwap = ModifiableCallout.durationBasedCall("Tera Slash (Other Tank)", "Tank Swap");
	private final ModifiableCallout<AbilityCastStart> teraSlashOther = ModifiableCallout.durationBasedCall("Tera Slash (Other)", "Buster on {event.target}");

	@HandleEvents
	public void teraSlash(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0xC478)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(teraSlashOnYou.getModified(event));
		}
		else if (state.playerJobMatches(Job::isTank)) {
			context.accept(teraSlashSwap.getModified(event));
		}
		else {
			context.accept(teraSlashOther.getModified(event));
		}
	}

	@NpcCastCallout(0xC475)
	private final ModifiableCallout<AbilityCastStart> wormwail = ModifiableCallout.durationBasedCall("Wormwail", "Get Under");

	@NpcCastCallout(0xC477)
	private final ModifiableCallout<AbilityCastStart> breath = ModifiableCallout.durationBasedCall("Breath", "Out of Front");

	private final ModifiableCallout<AbilityCastStart> finalLeftWing = ModifiableCallout.durationBasedCall("Final Phase: Left Wing", "Kill Left First");
	private final ModifiableCallout<AbilityCastStart> finalRightWing = ModifiableCallout.durationBasedCall("Final Phase: Right Wing", "Kill Right First");

	@HandleEvents
	public void finalWings(EventContext context, AbilityCastStart event) {
		// 先に詠唱を始めた翼だけを通知する
		if (finalWing) {
			return;
		}
		if (event.abilityIdMatches(0xC47D)) {
			finalWing = true;
			context.accept(finalLeftWing.getModified(event));
		}
		else if (event.abilityIdMatches(0xC47E)) {
			finalWing = true;
			context.accept(finalRightWing.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> tethersThenStack = new ModifiableCallout<>("Tethers (Phase 3)", "Break Tethers, then Stack");
	private final ModifiableCallout<HeadMarkerEvent> tethers = new ModifiableCallout<>("Tethers", "Break Tethers");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> tethersSq = SqtTemplates.sq(10_000,
			HeadMarkerEvent.class, hme -> hme.markerIdMatches(0x61) && hme.getTarget().isThePlayer(),
			(e1, s) -> {
				s.waitMs(3_800);
				s.updateCall(phase == 3 ? tethersThenStack : tethers, e1);
			});

	@PlayerHeadmarker(0x7E)
	private final ModifiableCallout<HeadMarkerEvent> tailMarker = new ModifiableCallout<>("Tail Marker", "Tail Marker on You");

	private final ModifiableCallout<HeadMarkerEvent> earthshakerOnYou = new ModifiableCallout<>("Earthshaker (on You)", "Earthshaker on You");
	private final ModifiableCallout<HeadMarkerEvent> avoidEarthshakers = new ModifiableCallout<>("Earthshaker (Not on You)", "Avoid Earthshakers");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> earthshakerSq = SqtTemplates.sq(10_000,
			HeadMarkerEvent.class, hme -> hme.markerIdMatches(0x28),
			(e1, s) -> {
				HeadMarkerEvent e2 = s.waitEvent(HeadMarkerEvent.class, hme -> hme.markerIdMatches(0x28));
				if (e1.getTarget().isThePlayer()) {
					s.updateCall(earthshakerOnYou, e1);
				}
				else if (e2.getTarget().isThePlayer()) {
					s.updateCall(earthshakerOnYou, e2);
				}
				else {
					s.updateCall(avoidEarthshakers, e2);
				}
			});

	@PlayerHeadmarker(0x39)
	private final ModifiableCallout<HeadMarkerEvent> cocoon = new ModifiableCallout<>("Cocoon Marker", "Spread");

}
