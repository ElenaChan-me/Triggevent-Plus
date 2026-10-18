package gg.xp.xivsupport.triggers.cactbot.sb;

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

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/trial/susano.ts をもとに移植
@CalloutRepo(name = "The Pool Of Tribute", duty = KnownDuty.ThePoolOfTribute)
public class Susano extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public Susano(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.ThePoolOfTribute);
	}

	private final ModifiableCallout<HeadMarkerEvent> brightstorm = new ModifiableCallout<>("Brightstorm", "Stack on {event.target}");

	@HandleEvents
	public void brightstorm(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x003E)) {
			context.accept(brightstorm.getModified(event));
		}
	}

	@PlayerHeadmarker(0x0017)
	private final ModifiableCallout<HeadMarkerEvent> seasplitter = new ModifiableCallout<>("Seasplitter", "Knockback on You");

	@NpcCastCallout(0x2026)
	private final ModifiableCallout<AbilityCastStart> ukehi = ModifiableCallout.durationBasedCall("Ukehi", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> stormsplitterOnYou = ModifiableCallout.durationBasedCall("Stormsplitter (on You)", "Tank Cleave on You");
	private final ModifiableCallout<AbilityCastStart> stormsplitterOther = ModifiableCallout.durationBasedCall("Stormsplitter (not on You)", "Avoid Tank Cleave");

	@HandleEvents
	public void stormsplitter(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x2023)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(stormsplitterOnYou.getModified(event));
		}
		else {
			context.accept(stormsplitterOther.getModified(event));
		}
	}
}
