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

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/dungeon/halatali_hard.ts をもとに移植
@CalloutRepo(name = "Halatali Hard", duty = KnownDuty.HalataliHard)
public class HalataliHard extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public HalataliHard(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.HalataliHard);
	}

	@NpcCastCallout(0x833)
	private final ModifiableCallout<AbilityCastStart> demonEye = ModifiableCallout.durationBasedCall("Demon Eye", "Use the Orb");

	@NpcCastCallout(0x84F)
	private final ModifiableCallout<AbilityCastStart> standstill = ModifiableCallout.durationBasedCall("Standstill", "Use the Nail");
}
