package gg.xp.xivsupport.triggers.cactbot.arr;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/trial/shiva-hm.ts をもとに移植
@CalloutRepo(name = "The Akh Afah Amphitheatre Hard", duty = KnownDuty.TheAkhAfahAmphitheatreHard)
public class ShivaHm extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public ShivaHm(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheAkhAfahAmphitheatreHard);
	}

	@PlayerHeadmarker(0x1D)
	private final ModifiableCallout<HeadMarkerEvent> hailstorm = new ModifiableCallout<>("Hailstorm", "Spread");

	@NpcCastCallout(0x9A1)
	private final ModifiableCallout<AbilityCastStart> glacierBash = ModifiableCallout.durationBasedCall("Glacier Bash", "Get Behind");

	@NpcCastCallout(0x999)
	private final ModifiableCallout<AbilityCastStart> permafrost = ModifiableCallout.durationBasedCall("Permafrost", "Stop Moving");

	private final ModifiableCallout<AbilityUsedEvent> iceBoulder = new ModifiableCallout<>("Ice Boulder", "Free {event.target}");

	@HandleEvents
	public void iceBoulder(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x9A3) && !event.getSource().isPc() && !event.getTarget().isThePlayer()) {
			context.accept(iceBoulder.getModified(event));
		}
	}
}
