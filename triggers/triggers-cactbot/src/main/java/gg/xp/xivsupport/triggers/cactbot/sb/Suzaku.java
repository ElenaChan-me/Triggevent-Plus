package gg.xp.xivsupport.triggers.cactbot.sb;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/trial/suzaku.ts をもとに移植
@CalloutRepo(name = "Hells Kier", duty = KnownDuty.HellsKier)
public class Suzaku extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public Suzaku(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.HellsKier);
	}

	@NpcCastCallout(0x3220)
	private final ModifiableCallout<AbilityCastStart> cremate = ModifiableCallout.durationBasedCall("Cremate", "Buster on {event.target}");

	@NpcCastCallout(0x3221)
	private final ModifiableCallout<AbilityCastStart> screamsOfTheDamned = ModifiableCallout.durationBasedCall("Screams of the Damned", "Raidwide");

	@PlayerStatusCallout(0x699)
	private final ModifiableCallout<BuffApplied> primaryTarget = new ModifiableCallout<BuffApplied>("Primary Target", "Lady Tether on You").statusIcon(0x699);

	@NpcCastCallout(0x3234)
	private final ModifiableCallout<AbilityCastStart> southronStar = ModifiableCallout.durationBasedCall("Southron Star", "Raidwide");

	@NpcCastCallout(0x3231)
	private final ModifiableCallout<AbilityCastStart> phantomFlurry = ModifiableCallout.durationBasedCall("Phantom Flurry", "Buster on {event.target}");

	@NpcCastCallout(0x3230)
	private final ModifiableCallout<AbilityCastStart> ruthlessRefrain = ModifiableCallout.durationBasedCall("Ruthless Refrain", "Knockback");

}
