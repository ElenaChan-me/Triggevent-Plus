package gg.xp.xivsupport.triggers.cactbot.arr;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.state.XivState;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/dungeon/aurum_vale73.ts をもとに移植
@CalloutRepo(name = "The Aurum Vale73", duty = KnownDuty.TheAurumVale73)
public class AurumVale73 extends AutoChildEventHandler implements FilteredEventHandler {

	// 1スタック目は呼ばず、重なり始めたら果実で解除させる
	private static final long minStacks = 2;
	private static final long maxStacks = 9;

	private XivState state;

	public AurumVale73(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheAurumVale73);
	}

	private final ModifiableCallout<BuffApplied> goldLungsBurrs = new ModifiableCallout<BuffApplied>("Gold Lungs/Burrs", "Eat Fruit").autoIcon();

	@HandleEvents
	public void goldLungsBurrs(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x12E, 0x12F)
				&& event.getTarget().isThePlayer()
				&& event.getStacks() >= minStacks
				&& event.getStacks() <= maxStacks) {
			context.accept(goldLungsBurrs.getModified(event));
		}
	}
}
