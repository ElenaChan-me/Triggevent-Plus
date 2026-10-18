package gg.xp.xivsupport.triggers.cactbot.hw;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/trial/bismarck-ex.ts をもとに移植
@CalloutRepo(name = "The Limitless Blue Extreme", duty = KnownDuty.TheLimitlessBlueExtreme)
public class BismarckEx extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public BismarckEx(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheLimitlessBlueExtreme);
	}

	@NpcCastCallout(0xFAF)
	private final ModifiableCallout<AbilityCastStart> sharpGust = ModifiableCallout.durationBasedCall("Sharp Gust", "Knockback");
}
