package gg.xp.xivsupport.triggers.cactbot.arr;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.AnyPlayerStatusCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/dungeon/the_wanderers_palace_hard.ts をもとに移植
@CalloutRepo(name = "The Wanderers Palace Hard", duty = KnownDuty.TheWanderersPalaceHard)
public class TheWanderersPalaceHard extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public TheWanderersPalaceHard(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheWanderersPalaceHard);
	}

	@NpcCastCallout(0xC91)
	private final ModifiableCallout<AbilityCastStart> firespit = ModifiableCallout.durationBasedCall("Firespit", "Buster on {event.target}");

	@AnyPlayerStatusCallout(0xD2)
	private final ModifiableCallout<BuffApplied> doom = ModifiableCallout.<BuffApplied>durationBasedCall("Doom", "Heal {event.target} to Full").autoIcon();

	@NpcCastCallout(0xC9E)
	private final ModifiableCallout<AbilityCastStart> soulDouse = ModifiableCallout.durationBasedCall("Soul Douse", "Look Away");
}
