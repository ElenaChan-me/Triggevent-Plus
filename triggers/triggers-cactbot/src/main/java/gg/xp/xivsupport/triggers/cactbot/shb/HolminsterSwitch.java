package gg.xp.xivsupport.triggers.cactbot.shb;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/dungeon/holminster_switch.ts をもとに移植
@CalloutRepo(name = "Holminster Switch", duty = KnownDuty.HolminsterSwitch)
public class HolminsterSwitch extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public HolminsterSwitch(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.HolminsterSwitch);
	}

	// 1ボス

	@NpcCastCallout(0x3DC5)
	private final ModifiableCallout<AbilityCastStart> pathOfLight = ModifiableCallout.durationBasedCall("Path of Light", "Raidwide");

	@NpcCastCallout(0x3DC4)
	private final ModifiableCallout<AbilityCastStart> pillory = ModifiableCallout.durationBasedCall("Pillory", "Buster on {event.target}");

	// 2ボス

	@NpcCastCallout(0x3DCF)
	private final ModifiableCallout<AbilityCastStart> tickler = ModifiableCallout.durationBasedCall("The Tickler", "Buster on {event.target}");

	@NpcCastCallout(0x3DD0)
	private final ModifiableCallout<AbilityCastStart> bridle = ModifiableCallout.durationBasedCall("Scold's Bridle", "Raidwide");

	@NpcCastCallout(0x3DD5)
	private final ModifiableCallout<AbilityCastStart> flagellation = ModifiableCallout.durationBasedCall("Fevered Flagellation", "Spread");

	private final ModifiableCallout<HeadMarkerEvent> exorciseStack = new ModifiableCallout<>("Exorcise", "Stack on {event.target}");

	@HandleEvents
	public void exorciseStack(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E)) {
			context.accept(exorciseStack.getModified(event));
		}
	}

	// 3ボス

	@NpcCastCallout(0x3DD8)
	private final ModifiableCallout<AbilityCastStart> scavenger = ModifiableCallout.durationBasedCall("Scavenger", "Raidwide");

	@NpcCastCallout(0x3DD7)
	private final ModifiableCallout<AbilityCastStart> headCrusher = ModifiableCallout.durationBasedCall("Head Crusher", "Buster on {event.target}");

	private final ModifiableCallout<HeadMarkerEvent> chainDown = new ModifiableCallout<>("Chain Down", "Break Chain on {event.target}");

	@HandleEvents
	public void chainDown(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x5C) && !event.getTarget().isThePlayer()) {
			context.accept(chainDown.getModified(event));
		}
	}

	@PlayerHeadmarker(0x8B)
	private final ModifiableCallout<HeadMarkerEvent> taphephobia = new ModifiableCallout<>("Taphephobia", "Spread");

	@NpcCastCallout(0x4350)
	private final ModifiableCallout<AbilityCastStart> intoTheLight = ModifiableCallout.durationBasedCall("Into the Light", "Line Stack");

	@NpcCastCallout(0x3DE7)
	private final ModifiableCallout<AbilityCastStart> leftKnout = ModifiableCallout.durationBasedCall("Left Knout", "Right");

	@NpcCastCallout(0x3DE6)
	private final ModifiableCallout<AbilityCastStart> rightKnout = ModifiableCallout.durationBasedCall("Right Knout", "Left");

}
