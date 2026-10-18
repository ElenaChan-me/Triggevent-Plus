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

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/dungeon/amdapor_keep.ts をもとに移植
@CalloutRepo(name = "Amdapor Keep", duty = KnownDuty.AmdaporKeep)
public class AmdaporKeep extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public AmdaporKeep(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AmdaporKeep);
	}

	@NpcCastCallout(0x415)
	private final ModifiableCallout<AbilityCastStart> liquefyMiddle = ModifiableCallout.durationBasedCall("Liquefy (Middle)", "Sides");

	@NpcCastCallout(0x416)
	private final ModifiableCallout<AbilityCastStart> liquefySides = ModifiableCallout.durationBasedCall("Liquefy (Sides)", "Middle");

	@NpcCastCallout(0x417)
	private final ModifiableCallout<AbilityCastStart> repel = ModifiableCallout.durationBasedCall("Repel", "Knockback");
}
