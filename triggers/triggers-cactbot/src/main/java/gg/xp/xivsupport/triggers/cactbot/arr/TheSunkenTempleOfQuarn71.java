package gg.xp.xivsupport.triggers.cactbot.arr;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.state.XivState;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/dungeon/the_sunken_temple_of_quarn71.ts をもとに移植
@CalloutRepo(name = "The Sunken Temple Of Qarn71", duty = KnownDuty.TheSunkenTempleOfQarn71)
public class TheSunkenTempleOfQuarn71 extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public TheSunkenTempleOfQuarn71(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheSunkenTempleOfQarn71);
	}

	private final ModifiableCallout<BuffApplied> doom = ModifiableCallout.<BuffApplied>durationBasedCall("Doom", "Step on Glowing Plate").autoIcon();

	@HandleEvents
	public void doom(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0xD2) && !event.isRefresh() && event.getTarget().isThePlayer()) {
			context.accept(doom.getModified(event));
		}
	}
}
