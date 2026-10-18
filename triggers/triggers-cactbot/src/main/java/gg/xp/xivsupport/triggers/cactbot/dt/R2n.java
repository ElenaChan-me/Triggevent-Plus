package gg.xp.xivsupport.triggers.cactbot.dt;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/raid/r2n.ts をもとに移植
@CalloutRepo(name = "Aac Light Heavyweight M2", duty = KnownDuty.M2N)
public class R2n extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public R2n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.M2N);
	}

	@NpcCastCallout(0x9164)
	private final ModifiableCallout<AbilityCastStart> callMeHoney = ModifiableCallout.durationBasedCall("Call Me Honey", "Raidwide");

	@NpcCastCallout({0x9B39, 0x9B3B})
	private final ModifiableCallout<AbilityCastStart> honeyBeeline = ModifiableCallout.durationBasedCall("Honey Beeline", "Sides");

	@NpcCastCallout({0x9B3A, 0x9B3C})
	private final ModifiableCallout<AbilityCastStart> temptingTwist = ModifiableCallout.durationBasedCall("Tempting Twist", "Under");

	@NpcCastCallout(0x9167)
	private final ModifiableCallout<AbilityCastStart> honeyedBreeze = ModifiableCallout.durationBasedCall("Honeyed Breeze", "Buster on {event.target}");

	@NpcCastCallout(0x9170)
	private final ModifiableCallout<AbilityCastStart> dropOfVenom = ModifiableCallout.durationBasedCall("Drop of Venom", "Stack on {event.target}");

	@NpcCastCallout(0x9173)
	private final ModifiableCallout<AbilityCastStart> blowKiss = ModifiableCallout.durationBasedCall("Blow Kiss", "Away from Front");

	@NpcCastCallout(value = 0x917A, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> heartsore = ModifiableCallout.durationBasedCall("Heartsore", "Spread");

	@NpcCastCallout(0x9AC1)
	private final ModifiableCallout<AbilityCastStart> loveseeker = ModifiableCallout.durationBasedCall("Loveseeker", "Out");

	@NpcCastCallout(0x917B)
	private final ModifiableCallout<AbilityCastStart> honeyBFinale = ModifiableCallout.durationBasedCall("Honey B. Finale", "Raidwide");

	@NpcCastCallout(value = 0x9B8D, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> heartsick = ModifiableCallout.durationBasedCall("Heartsick", "Stacks");

}
