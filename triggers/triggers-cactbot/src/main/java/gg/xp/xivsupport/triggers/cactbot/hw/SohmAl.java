package gg.xp.xivsupport.triggers.cactbot.hw;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/dungeon/sohm_al.ts をもとに移植
@CalloutRepo(name = "Sohm Al", duty = KnownDuty.SohmAl)
public class SohmAl extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public SohmAl(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.SohmAl);
	}

	private final ModifiableCallout<HeadMarkerEvent> myathStack = new ModifiableCallout<>("Myath: Stack", "Stack on {event.target}");

	@HandleEvents
	public void stackMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x003E)) {
			context.accept(myathStack.getModified(event));
		}
	}

	@PlayerHeadmarker(0x008C)
	private final ModifiableCallout<HeadMarkerEvent> myathSpread = new ModifiableCallout<>("Myath: Spread", "Spread");

	@PlayerHeadmarker(0x0007)
	private final ModifiableCallout<HeadMarkerEvent> tiomanMeteor = new ModifiableCallout<>("Tioman: Meteor", "Meteor on You");
}
