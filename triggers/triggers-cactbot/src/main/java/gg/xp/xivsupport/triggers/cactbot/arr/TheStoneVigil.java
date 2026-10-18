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

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/dungeon/the_stone_vigil.ts をもとに移植
@CalloutRepo(name = "The Stone Vigil", duty = KnownDuty.TheStoneVigil)
public class TheStoneVigil extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public TheStoneVigil(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheStoneVigil);
	}

	@NpcCastCallout(0x387)
	private final ModifiableCallout<AbilityCastStart> swinge = ModifiableCallout.durationBasedCall("Swinge", "Away from Front");
}
