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
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/dungeon/amaurot.ts をもとに移植
@CalloutRepo(name = "Amaurot", duty = KnownDuty.Amaurot)
public class Amaurot extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long METEOR_MARKER = 0x39;

	private XivState state;

	public Amaurot(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.Amaurot);
	}

	private int meteorCount;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		meteorCount = 0;
	}

	private final ModifiableCallout<HeadMarkerEvent> meteorWest = new ModifiableCallout<>("Meteor 1", "Drop Meteor West");
	private final ModifiableCallout<HeadMarkerEvent> meteorEast = new ModifiableCallout<>("Meteor 2", "Drop Meteor East");
	private final ModifiableCallout<HeadMarkerEvent> meteor = new ModifiableCallout<>("Meteor", "Meteor");

	@HandleEvents
	public void meteor(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(METEOR_MARKER) || !event.getTarget().isThePlayer()) {
			return;
		}
		meteorCount++;
		// 最初の2回は捨てる場所が決まっている
		ModifiableCallout<HeadMarkerEvent> call = switch (meteorCount) {
			case 1 -> meteorWest;
			case 2 -> meteorEast;
			default -> meteor;
		};
		context.accept(call.getModified(event));
	}

	@PlayerHeadmarker(0x8B)
	private final ModifiableCallout<HeadMarkerEvent> spread = new ModifiableCallout<>("Spread Marker", "Spread");

	@NpcCastCallout(0x3CCB)
	private final ModifiableCallout<AbilityCastStart> finalSky = ModifiableCallout.durationBasedCall("Final Sky", "Hide Behind Boulder");

	@NpcCastCallout(0x3CE3)
	private final ModifiableCallout<AbilityCastStart> shadowWreck = ModifiableCallout.durationBasedCall("Shadow Wreck", "Raidwide");

	@NpcCastCallout(0x3CD7)
	private final ModifiableCallout<AbilityCastStart> apokalypsis = ModifiableCallout.durationBasedCall("Apokalypsis", "Get Off");

}
