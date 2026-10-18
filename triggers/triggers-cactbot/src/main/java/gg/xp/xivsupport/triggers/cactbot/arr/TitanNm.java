package gg.xp.xivsupport.triggers.cactbot.arr;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/trial/titan-nm.ts をもとに移植
@CalloutRepo(name = "The Navel", duty = KnownDuty.TheNavel)
public class TitanNm extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public TitanNm(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheNavel);
	}

	@NpcAbilityUsedCallout(value = 0x282, suppressMs = 2_000)
	private final ModifiableCallout<AbilityUsedEvent> tumult = new ModifiableCallout<>("Tumult", "Raidwide");

	private final ModifiableCallout<BuffApplied> gaolOnYou = new ModifiableCallout<BuffApplied>("Gaol on You", "Gaol on You").autoIcon();
	private final ModifiableCallout<BuffApplied> breakGaolOn = new ModifiableCallout<BuffApplied>("Gaol on Others", "Break Gaol on {event.target}").autoIcon();

	@HandleEvents
	public void gaols(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x124)) {
			if (event.getTarget().isThePlayer()) {
				context.accept(gaolOnYou.getModified(event));
			}
			else {
				context.accept(breakGaolOn.getModified(event));
			}
		}
	}
}
