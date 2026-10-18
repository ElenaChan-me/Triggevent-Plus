package gg.xp.xivsupport.triggers.cactbot.arr;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.Job;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.state.XivState;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/trial/titan-hm.ts をもとに移植
@CalloutRepo(name = "The Navel Hard", duty = KnownDuty.TheNavelHard)
public class TitanHm extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public TitanHm(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheNavelHard);
	}

	private final ModifiableCallout<BuffApplied> damageDown = new ModifiableCallout<BuffApplied>("Damage Down", "Cleanse {event.target}").autoIcon();

	// エスナできるジョブにだけ知らせる
	@HandleEvents
	public void damageDown(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x3E) && state.playerJobMatches(job -> job.isHealer() || job == Job.BRD)) {
			context.accept(damageDown.getModified(event));
		}
	}
}
