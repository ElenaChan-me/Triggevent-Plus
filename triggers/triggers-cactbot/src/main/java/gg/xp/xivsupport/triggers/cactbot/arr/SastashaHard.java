package gg.xp.xivsupport.triggers.cactbot.arr;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.Job;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.state.XivState;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/dungeon/sastasha_hard.ts をもとに移植
@CalloutRepo(name = "Sastasha Hard", duty = KnownDuty.SastashaHard)
public class SastashaHard extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public SastashaHard(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.SastashaHard);
	}

	private final ModifiableCallout<BuffApplied> slime = new ModifiableCallout<BuffApplied>("Slime", "Esuna {event.target}").autoIcon();

	// エスナできるジョブにだけ知らせる
	@HandleEvents
	public void slime(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x239) && state.playerJobMatches(job -> job.isHealer() || job == Job.BRD)) {
			context.accept(slime.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> tailScrewStun = ModifiableCallout.durationBasedCall("Tail Screw (Stun)", "Stun {event.source}");
	private final ModifiableCallout<AbilityCastStart> tailScrew = ModifiableCallout.durationBasedCall("Tail Screw", "Tail Screw on {event.target}");

	@HandleEvents
	public void tailScrew(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0xBF4)) {
			if (state.playerJobMatches(job -> job.isTank() || job.isMeleeDps())) {
				context.accept(tailScrewStun.getModified(event));
			}
			else {
				context.accept(tailScrew.getModified(event));
			}
		}
	}
}
