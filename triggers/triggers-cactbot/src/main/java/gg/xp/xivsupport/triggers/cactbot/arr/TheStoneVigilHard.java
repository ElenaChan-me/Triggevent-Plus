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

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/dungeon/the_stone_vigil_hard.ts をもとに移植
@CalloutRepo(name = "The Stone Vigil Hard", duty = KnownDuty.TheStoneVigilHard)
public class TheStoneVigilHard extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public TheStoneVigilHard(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheStoneVigilHard);
	}

	@NpcCastCallout(0x8F7)
	private final ModifiableCallout<AbilityCastStart> swinge = ModifiableCallout.durationBasedCall("Swinge", "Away from Front");

	@NpcCastCallout(0x8F6)
	private final ModifiableCallout<AbilityCastStart> lionsBreath = ModifiableCallout.durationBasedCall("Lion's Breath", "Away from Front");

	@NpcCastCallout(0x8F5)
	private final ModifiableCallout<AbilityCastStart> rake = ModifiableCallout.durationBasedCall("Rake", "Buster on {event.target}");
}
