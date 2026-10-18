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
import gg.xp.xivsupport.events.state.XivState;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/dungeon/haukke_manor_hard.ts をもとに移植
@CalloutRepo(name = "Haukke Manor Hard", duty = KnownDuty.HaukkeManorHard)
public class HaukkeManorHard extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public HaukkeManorHard(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.HaukkeManorHard);
	}

	private final ModifiableCallout<AbilityCastStart> stoneskin = ModifiableCallout.durationBasedCall("Stoneskin", "Interrupt {event.source}");
	private final ModifiableCallout<AbilityCastStart> beguilingMist = ModifiableCallout.durationBasedCall("Beguiling Mist", "Stun {event.source}");

	// 沈黙・スタン手段を持つジョブにだけ知らせる
	@HandleEvents
	public void casts(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x3F0) && state.playerJobMatches(Job::caresAboutInterrupt)) {
			context.accept(stoneskin.getModified(event));
		}
		else if (event.abilityIdMatches(0x6B7) && state.playerJobMatches(job -> job.isTank() || job.isMeleeDps())) {
			context.accept(beguilingMist.getModified(event));
		}
	}
}
