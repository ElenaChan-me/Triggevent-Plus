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

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/dungeon/cutters_cry72.ts をもとに移植
@CalloutRepo(name = "Cutters Cry72", duty = KnownDuty.CuttersCry72)
public class CuttersCry72 extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public CuttersCry72(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.CuttersCry72);
	}

	@NpcCastCallout(0x450)
	private final ModifiableCallout<AbilityCastStart> ramVoice = ModifiableCallout.durationBasedCall("The Ram's Voice", "Out of Melee");

	@NpcCastCallout(0x5A2)
	private final ModifiableCallout<AbilityCastStart> dragonVoice = ModifiableCallout.durationBasedCall("The Dragon's Voice", "In");
}
