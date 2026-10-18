package gg.xp.xivsupport.triggers.cactbot.arr;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/dungeon/the_sunken_temple_of_quarn.ts をもとに移植
@CalloutRepo(name = "The Sunken Temple Of Qarn", duty = KnownDuty.TheSunkenTempleOfQarn)
public class TheSunkenTempleOfQuarn extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public TheSunkenTempleOfQuarn(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheSunkenTempleOfQarn);
	}

	@PlayerStatusCallout(0xD2)
	private final ModifiableCallout<BuffApplied> doom = ModifiableCallout.<BuffApplied>durationBasedCall("Doom", "Step on Glowing Plate").autoIcon();
}
