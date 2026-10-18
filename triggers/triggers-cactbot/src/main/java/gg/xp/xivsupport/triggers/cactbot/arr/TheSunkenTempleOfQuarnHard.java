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

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/dungeon/the_sunken_temple_of_quarn_hard.ts をもとに移植
@CalloutRepo(name = "The Sunken Temple Of Qarn Hard", duty = KnownDuty.TheSunkenTempleOfQarnHard)
public class TheSunkenTempleOfQuarnHard extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public TheSunkenTempleOfQuarnHard(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheSunkenTempleOfQarnHard);
	}

	@NpcCastCallout(0xC26)
	private final ModifiableCallout<AbilityCastStart> lightOfAnathema = ModifiableCallout.durationBasedCall("Light of Anathema", "Away from Front");
}
