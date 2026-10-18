package gg.xp.xivsupport.triggers.cactbot.arr;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/dungeon/snowcloak.ts をもとに移植
@CalloutRepo(name = "Snowcloak", duty = KnownDuty.Snowcloak)
public class Snowcloak extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public Snowcloak(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.Snowcloak);
	}

	@NpcCastCallout(0xC1F)
	private final ModifiableCallout<AbilityCastStart> lunarCry = ModifiableCallout.durationBasedCall("Lunar Cry", "Hide behind Ice");
}
