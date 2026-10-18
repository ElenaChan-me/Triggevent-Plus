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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;

import java.util.ArrayList;
import java.util.List;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/trial/shinryu-ex.ts をもとに移植
@CalloutRepo(name = "The Minstrels Ballad Shinryus Domain", duty = KnownDuty.TheMinstrelsBalladShinryusDomain)
public class ShinryuEx extends AutoChildEventHandler implements FilteredEventHandler {

	private static final int shakerCount = 2;
	private static final long icicleX = -30;
	private static final long icicleWestY = -15;
	private static final long icicleEastY = -25;

	private XivState state;

	public ShinryuEx(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheMinstrelsBalladShinryusDomain);
	}

	private int phase;
	private boolean finalWing;
	private final List<XivCombatant> shakerTargets = new ArrayList<>();

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		phase = 0;
		finalWing = false;
		shakerTargets.clear();
	}

	// アース・フューリー、ダークマター、プロトスター、最後のタイダルウェイブで段階を判定する
	@HandleEvents
	public void phaseTracker(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x25DE)) {
			phase = 1;
		}
		else if (event.abilityIdMatches(0x25E7)) {
			phase = 2;
		}
		else if (event.abilityIdMatches(0x25E4)) {
			phase = 3;
		}
		else if (event.abilityIdMatches(0x264E)) {
			phase = 4;
		}
	}

	private final ModifiableCallout<AbilityCastStart> akhMornOnYou = ModifiableCallout.durationBasedCall("Akh Morn (On You)", "Akh Morn on You");
	private final ModifiableCallout<AbilityCastStart> akhMornTank = ModifiableCallout.durationBasedCall("Akh Morn (Tank)", "Akh Morn on {event.target}");
	private final ModifiableCallout<AbilityCastStart> akhRhai = ModifiableCallout.durationBasedCall("Akh Morn (Others)", "Akh Rhai: Spread and Move");

	@HandleEvents
	public void akhMorn(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x25F3)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(akhMornOnYou.getModified(event));
		}
		else if (state.playerJobMatches(Job::isTank)) {
			context.accept(akhMornTank.getModified(event));
		}
		else {
			context.accept(akhRhai.getModified(event));
		}
	}

	@NpcCastCallout(0x25DD)
	private final ModifiableCallout<AbilityCastStart> diamondDust = ModifiableCallout.durationBasedCall("Diamond Dust", "Ice: Stack and Don't Move");

	@NpcCastCallout(0x2611)
	private final ModifiableCallout<AbilityCastStart> dragonfist = ModifiableCallout.durationBasedCall("Dragonfist", "Out of Middle");

	@NpcCastCallout(0x25DB)
	private final ModifiableCallout<AbilityCastStart> hellfire = ModifiableCallout.durationBasedCall("Hellfire", "Get in Water");

	private final ModifiableCallout<AbilityCastStart> hypernovaFrozen = ModifiableCallout.durationBasedCall("Hypernova (Phase 3)", "Stop and Get Frozen");
	private final ModifiableCallout<AbilityCastStart> hypernovaWater = ModifiableCallout.durationBasedCall("Hypernova", "Stack in Water");

	@HandleEvents
	public void hypernova(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x271F, 0x25E8)) {
			context.accept((phase == 3 ? hypernovaFrozen : hypernovaWater).getModified(event));
		}
	}

	@NpcCastCallout(0x25DC)
	private final ModifiableCallout<AbilityCastStart> judgementBolt = ModifiableCallout.durationBasedCall("Judgement Bolt", "Out of Water");

	private final ModifiableCallout<AbilityCastStart> levinboltBait = ModifiableCallout.durationBasedCall("Levinbolt (Phase 3)", "Bait Bolt and Keep Moving");
	private final ModifiableCallout<AbilityCastStart> levinboltSpread = ModifiableCallout.durationBasedCall("Levinbolt", "Spread, No Water");
	private final ModifiableCallout<AbilityCastStart> levinboltMoveAway = new ModifiableCallout<>("Levinbolt (Phase 3, Move Away)", "Move Away");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> levinboltSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x25EA, 0x2720, 0x2725)
					// 右翼が自身に向けて詠唱するものだけを見る
					&& acs.getSource().getId() == acs.getTarget().getId(),
			(e1, s) -> {
				if (phase != 3) {
					s.updateCall(levinboltSpread, e1);
					return;
				}
				s.updateCall(levinboltBait, e1);
				s.waitMs(9_500);
				s.updateCall(levinboltMoveAway, e1);
			});

	private final ModifiableCallout<AbilityUsedEvent> icicleWest = new ModifiableCallout<>("Icicle Impact (West)", "Icicle, Lean West");
	private final ModifiableCallout<AbilityUsedEvent> icicleEast = new ModifiableCallout<>("Icicle Impact (East)", "Icicle, Lean East");

	@HandleEvents
	public void icicle(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x25EF)) {
			return;
		}
		Position pos = event.getSource().getPos();
		if (pos == null || Math.round(pos.x()) != icicleX) {
			return;
		}
		long y = Math.round(pos.y());
		if (y == icicleWestY) {
			context.accept(icicleWest.getModified(event));
		}
		else if (y == icicleEastY) {
			context.accept(icicleEast.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> tidalWave = new ModifiableCallout<>("Tidal Wave", "Knockback", 5_000);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> tidalWaveSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x25DA),
			(e1, s) -> {
				s.waitMs(3_000);
				s.updateCall(tidalWave, e1);
			});

	private final ModifiableCallout<AbilityCastStart> finalTidalWave = ModifiableCallout.durationBasedCall("Tidal Wave (Enrage, Healer)", "No More Heals Needed");

	@HandleEvents
	public void finalTidal(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x264E) && state.playerJobMatches(Job::isHealer)) {
			context.accept(finalTidalWave.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> tailSlap = new ModifiableCallout<>("Tail Slap", "Tail: Switch Targets");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> tailSlapSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x25E2),
			(e1, s) -> {
				s.waitMs(2_000);
				s.updateCall(tailSlap, e1);
			});

	@NpcCastCallout({0x1FA8, 0x1FF4, 0x2603})
	private final ModifiableCallout<AbilityCastStart> gyreCharge = ModifiableCallout.durationBasedCall("Gyre Charge", "Avoid Divebomb");

	private final ModifiableCallout<AbilityCastStart> deathSentenceOnYou = ModifiableCallout.durationBasedCall("Death Sentence (On You)", "Death Sentence on You");
	private final ModifiableCallout<AbilityCastStart> deathSentence = ModifiableCallout.durationBasedCall("Death Sentence", "Death Sentence on {event.target}");

	@HandleEvents
	public void deathSentenceCast(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x260A)) {
			context.accept((event.getTarget().isThePlayer() ? deathSentenceOnYou : deathSentence).getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> teraSlashOnYou = ModifiableCallout.durationBasedCall("Tera Slash (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> teraSlashSwap = ModifiableCallout.durationBasedCall("Tera Slash (Other Tank)", "Tank Swap");
	private final ModifiableCallout<AbilityCastStart> teraSlash = ModifiableCallout.durationBasedCall("Tera Slash", "Buster on {event.target}");

	@HandleEvents
	public void teraSlashCast(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x264B)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(teraSlashOnYou.getModified(event));
		}
		else if (state.playerJobMatches(Job::isTank)) {
			context.accept(teraSlashSwap.getModified(event));
		}
		else {
			context.accept(teraSlash.getModified(event));
		}
	}

	@NpcCastCallout(0x2648)
	private final ModifiableCallout<AbilityCastStart> wormwail = ModifiableCallout.durationBasedCall("Wormwail", "Get Under");

	@NpcCastCallout(0x264A)
	private final ModifiableCallout<AbilityCastStart> breath = ModifiableCallout.durationBasedCall("Breath", "Away from Front");

	private final ModifiableCallout<AbilityCastStart> finalLeftWing = ModifiableCallout.durationBasedCall("Final Wings (Left)", "Kill Left First");
	private final ModifiableCallout<AbilityCastStart> finalRightWing = ModifiableCallout.durationBasedCall("Final Wings (Right)", "Kill Right First");

	@HandleEvents
	public void finalWings(EventContext context, AbilityCastStart event) {
		if (finalWing) {
			return;
		}
		if (event.abilityIdMatches(0x2718)) {
			context.accept(finalLeftWing.getModified(event));
			finalWing = true;
		}
		else if (event.abilityIdMatches(0x2719)) {
			context.accept(finalRightWing.getModified(event));
			finalWing = true;
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> breakTethersStack = new ModifiableCallout<>("Tethers (Phase 3)", "Break Tethers, then Stack");
	private final ModifiableCallout<HeadMarkerEvent> breakTethers = new ModifiableCallout<>("Tethers", "Break Tethers");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> tethersSq = SqtTemplates.sq(10_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(0x61) && hm.getTarget().isThePlayer(),
			(e1, s) -> {
				s.waitMs(3_800);
				s.updateCall(phase == 3 ? breakTethersStack : breakTethers, e1);
			});

	@PlayerHeadmarker(0x7E)
	private final ModifiableCallout<HeadMarkerEvent> tailMarker = new ModifiableCallout<>("Tail Marker", "Tail Marker on You");

	private final ModifiableCallout<HeadMarkerEvent> earthshakerOnYou = new ModifiableCallout<>("Earthshaker (On You)", "Earthshaker on You");
	private final ModifiableCallout<HeadMarkerEvent> avoidEarthshakers = new ModifiableCallout<>("Earthshaker (Others)", "Avoid Earthshakers");

	@HandleEvents
	public void shakers(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(0x28)) {
			return;
		}
		shakerTargets.add(event.getTarget());
		if (shakerTargets.size() != shakerCount) {
			return;
		}
		boolean onYou = shakerTargets.stream().anyMatch(XivCombatant::isThePlayer);
		context.accept((onYou ? earthshakerOnYou : avoidEarthshakers).getModified(event));
		shakerTargets.clear();
	}

	@PlayerHeadmarker(0x39)
	private final ModifiableCallout<HeadMarkerEvent> cocoonMarker = new ModifiableCallout<>("Cocoon Marker", "Spread");

}
