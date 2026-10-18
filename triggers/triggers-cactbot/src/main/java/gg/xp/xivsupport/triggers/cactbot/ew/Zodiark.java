package gg.xp.xivsupport.triggers.cactbot.ew;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/trial/zodiark.ts をもとに移植
@CalloutRepo(name = "The Dark Inside", duty = KnownDuty.TheDarkInside)
public class Zodiark extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public Zodiark(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheDarkInside);
	}

	@NpcCastCallout(0x6B62)
	private final ModifiableCallout<AbilityCastStart> ania = ModifiableCallout.durationBasedCall("Ania", "Buster on {event.target}");

	// 南西・北東どちらも安全だが、全員同じ側に寄った方が楽なので片側だけ伝える
	@NpcCastCallout(0x67D1)
	private final ModifiableCallout<AbilityCastStart> algedonNE = ModifiableCallout.durationBasedCall("Algedon (NE)", "Go Northeast (Knockback)");

	@NpcCastCallout(0x67D2)
	private final ModifiableCallout<AbilityCastStart> algedonNW = ModifiableCallout.durationBasedCall("Algedon (NW)", "Go Northwest (Knockback)");

}
