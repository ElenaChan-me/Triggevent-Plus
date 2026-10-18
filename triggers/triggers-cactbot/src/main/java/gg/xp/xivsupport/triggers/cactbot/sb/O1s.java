package gg.xp.xivsupport.triggers.cactbot.sb;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/raid/o1s.ts をもとに移植
@CalloutRepo(name = "Deltascape V10 Savage", duty = KnownDuty.O5S)
public class O1s extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public O1s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.O5S);
	}

	@NpcCastCallout(0x1EDD)
	private final ModifiableCallout<AbilityCastStart> blaze = ModifiableCallout.durationBasedCall("Blaze", "Stack");

	@NpcCastCallout(0x1ED6)
	private final ModifiableCallout<AbilityCastStart> breathWing = ModifiableCallout.durationBasedCall("Breath Wing", "Sides of Boss");

	@NpcCastCallout(0x1EDE)
	private final ModifiableCallout<AbilityCastStart> clamp = ModifiableCallout.durationBasedCall("Clamp", "Away from Front");

	@NpcCastCallout(0x1ED8)
	private final ModifiableCallout<AbilityCastStart> downburst = ModifiableCallout.durationBasedCall("Downburst", "Knockback");

	@NpcCastCallout(0x1ED4)
	private final ModifiableCallout<AbilityCastStart> roar = ModifiableCallout.durationBasedCall("Roar", "Raidwide");

	@NpcCastCallout(0x1ED3)
	private final ModifiableCallout<AbilityCastStart> charybdis = ModifiableCallout.durationBasedCall("Charybdis", "HP to 1");

}
