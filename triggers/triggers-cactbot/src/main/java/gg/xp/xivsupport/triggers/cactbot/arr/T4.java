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

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/raid/t4.ts をもとに移植
@CalloutRepo(name = "The Binding Coil Of Bahamut Turn4", duty = KnownDuty.TheBindingCoilOfBahamutTurn4)
public class T4 extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public T4(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheBindingCoilOfBahamutTurn4);
	}

	@NpcCastCallout(value = 0x4D4, onYou = true)
	private final ModifiableCallout<AbilityCastStart> gravityThrust = ModifiableCallout.durationBasedCall("Gravity Thrust", "LOS Thrust");

	@NpcCastCallout(value = 0x4D5, onYou = true)
	private final ModifiableCallout<AbilityCastStart> pox = ModifiableCallout.durationBasedCall("Pox", "LOS Pox");
}
