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

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/dungeon/the_lost_city_of_amdapor.ts をもとに移植
@CalloutRepo(name = "The Lost City Of Amdapor", duty = KnownDuty.TheLostCityOfAmdapor)
public class TheLostCityOfAmdapor extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public TheLostCityOfAmdapor(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheLostCityOfAmdapor);
	}

	@NpcAbilityUsedCallout(value = 0x736, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> devour = new ModifiableCallout<>("Devour", "Kill Adds");

	@NpcCastCallout(value = 0x762, onYou = true)
	private final ModifiableCallout<AbilityCastStart> graviball = ModifiableCallout.durationBasedCall("Graviball", "Drop Puddle Outside");

	@NpcCastCallout(0x766)
	private final ModifiableCallout<AbilityCastStart> ultimateTerror = ModifiableCallout.durationBasedCall("Ultimate Terror", "In");
}
