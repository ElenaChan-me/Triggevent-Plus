package gg.xp.xivsupport.triggers.cactbot.shb;

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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/raid/e3n.ts をもとに移植
@CalloutRepo(name = "Edens Gate Inundation", duty = KnownDuty.EdensGateInundation)
public class E3n extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long RIP_CURRENT_MARKER = 0x17;
	private static final long STACK_MARKER = 0x3E;
	private static final long SURGING_WATERS_MARKER = 0xAD;

	private XivState state;

	public E3n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.EdensGateInundation);
	}

	@NpcCastCallout(0x3FC4)
	private final ModifiableCallout<AbilityCastStart> tidalRoar = ModifiableCallout.durationBasedCall("Tidal Roar", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> tidalWaveLook = new ModifiableCallout<>("Tidal Wave", "Look for Wave");
	private final ModifiableCallout<AbilityCastStart> tidalWaveKnockback = new ModifiableCallout<>("Tidal Wave: Knockback", "Knockback");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> tidalWaveSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x3FD2),
			(e1, s) -> {
				s.waitMs(3_000);
				s.updateCall(tidalWaveLook, e1);
				// 詠唱3秒＋10秒後に来るので、アームズレングスが間に合う5秒前に知らせる
				s.waitMs(5_000);
				s.updateCall(tidalWaveKnockback, e1);
			});

	@NpcCastCallout(0x3FD0)
	private final ModifiableCallout<AbilityCastStart> underseaQuakeOutside = ModifiableCallout.durationBasedCall("Undersea Quake (Outside)", "Get Middle");

	@NpcCastCallout(0x3FCF)
	private final ModifiableCallout<AbilityCastStart> underseaQuakeInside = ModifiableCallout.durationBasedCall("Undersea Quake (Inside)", "Sides");

	private final ModifiableCallout<AbilityCastStart> maelstrom = new ModifiableCallout<>("Maelstrom", "Avoid Puddles and Dives");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> maelstromSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x3FD8),
			(e1, s) -> {
				s.waitMs(8_000);
				s.updateCall(maelstrom, e1);
			});

	@NpcCastCallout(0x3FD4)
	private final ModifiableCallout<AbilityCastStart> tsunami = ModifiableCallout.durationBasedCall("Tsunami", "Raidwide");

	@PlayerHeadmarker(0xA9)
	private final ModifiableCallout<HeadMarkerEvent> drenchingPulse = new ModifiableCallout<>("Drenching Pulse", "Spread");

	@PlayerHeadmarker(0x82)
	private final ModifiableCallout<HeadMarkerEvent> splashingWaters = new ModifiableCallout<>("Splashing Waters", "Spread");

	@PlayerHeadmarker(0x99)
	private final ModifiableCallout<HeadMarkerEvent> swirlingWaters = new ModifiableCallout<>("Swirling Waters", "Donut on You");

	private final ModifiableCallout<HeadMarkerEvent> ripCurrentOnYou = new ModifiableCallout<>("Rip Current (On You)", "Buster on You");
	private final ModifiableCallout<HeadMarkerEvent> ripCurrent = new ModifiableCallout<>("Rip Current", "Buster on {event.target}");
	private final ModifiableCallout<HeadMarkerEvent> stack = new ModifiableCallout<>("Crashing Pulse / Smothering Waters", "Stack on {event.target}");
	private final ModifiableCallout<HeadMarkerEvent> surgingWatersOnYou = new ModifiableCallout<>("Surging Waters (On You)", "Knockback on You");
	private final ModifiableCallout<HeadMarkerEvent> surgingWaters = new ModifiableCallout<>("Surging Waters", "Knockback on {event.target}");

	@HandleEvents
	public void headmarkers(EventContext context, HeadMarkerEvent event) {
		boolean onYou = event.getTarget().isThePlayer();
		if (event.markerIdMatches(RIP_CURRENT_MARKER)) {
			context.accept((onYou ? ripCurrentOnYou : ripCurrent).getModified(event));
		}
		else if (event.markerIdMatches(STACK_MARKER)) {
			context.accept(stack.getModified(event));
		}
		else if (event.markerIdMatches(SURGING_WATERS_MARKER)) {
			context.accept((onYou ? surgingWatersOnYou : surgingWaters).getModified(event));
		}
	}

}
