package gg.xp.xivsupport.triggers.cactbot.arr;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/dungeon/hullbreaker_isle.ts をもとに移植
@CalloutRepo(name = "Hullbreaker Isle", duty = KnownDuty.HullbreakerIsle)
public class HullbreakerIsle extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public HullbreakerIsle(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.HullbreakerIsle);
	}

	@NpcCastCallout(0x89E)
	private final ModifiableCallout<AbilityCastStart> stoolPelt = ModifiableCallout.durationBasedCall("Stool Pelt", "Raidwide");

	@NpcAbilityUsedCallout(value = 0x89F, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> chestThump = new ModifiableCallout<>("Chest Thump", "Shake Banana Tree");
}
