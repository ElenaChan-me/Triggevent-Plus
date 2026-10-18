package gg.xp.xivsupport.triggers.cactbot.hw;

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
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/dungeon/fractal_continuum.ts をもとに移植
@CalloutRepo(name = "The Fractal Continuum", duty = KnownDuty.TheFractalContinuum)
public class FractalContinuum extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public FractalContinuum(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheFractalContinuum);
	}

	@NpcCastCallout(0xF7A)
	private final ModifiableCallout<AbilityCastStart> rapidSever = ModifiableCallout.durationBasedCall("Rapid Sever", "Buster on {event.target}");

	@NpcCastCallout({0xF83, 0xF81})
	private final ModifiableCallout<AbilityCastStart> slashSwipe = ModifiableCallout.durationBasedCall("Slash/Swipe", "Away from Front");

	@NpcCastCallout(0xF82)
	private final ModifiableCallout<AbilityCastStart> smallSwing = ModifiableCallout.durationBasedCall("Small Swing", "Out");

	@NpcCastCallout(0xF87)
	private final ModifiableCallout<AbilityCastStart> bigSwing = ModifiableCallout.durationBasedCall("Big Swing", "Use a Cage");

	private final ModifiableCallout<BuffApplied> aetherochemicalBomb = new ModifiableCallout<BuffApplied>("Aetherochemical Bomb", "Cleanse {event.target}").autoIcon();

	@HandleEvents
	public void bomb(EventContext context, BuffApplied event) {
		// エスナできるのはヒーラーと詩人だけなので、それ以外には通知しない
		if (event.buffIdMatches(0x2D3) && state.playerJobMatches(job -> job.isHealer() || job == Job.BRD)) {
			context.accept(aetherochemicalBomb.getModified(event));
		}
	}
}
