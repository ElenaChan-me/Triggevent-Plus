package gg.xp.xivsupport.triggers.cactbot.sb;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;
import gg.xp.xivsupport.models.XivCombatant;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/raid/o5n.ts をもとに移植
@CalloutRepo(name = "Sigmascape V10", duty = KnownDuty.SigmascapeV10)
public class O5n extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public O5n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.SigmascapeV10);
	}

	@NpcCastCallout(0x28BB)
	private final ModifiableCallout<AbilityCastStart> acidRain = ModifiableCallout.durationBasedCall("Acid Rain", "Raidwide");

	@NpcCastCallout(0x28A3)
	private final ModifiableCallout<AbilityCastStart> doomStrike = ModifiableCallout.durationBasedCall("Doom Strike", "Buster on {event.target}");

	@NpcCastCallout(0x28A4)
	private final ModifiableCallout<AbilityCastStart> headOn = ModifiableCallout.durationBasedCall("Head On", "Out");

	@NpcCastCallout(0x28A6)
	private final ModifiableCallout<AbilityCastStart> diabolicHeadlamp = ModifiableCallout.durationBasedCall("Diabolic Headlamp", "Stack Middle");

	private final ModifiableCallout<TetherEvent> ghostTether = new ModifiableCallout<>("Ghost Tether", "Bait Ghost into Light Circle");

	@HandleEvents
	public void ghostTether(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x01) && event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			context.accept(ghostTether.getModified(event));
		}
	}

	@PlayerHeadmarker(0x01)
	private final ModifiableCallout<HeadMarkerEvent> diabolicLight = new ModifiableCallout<>("Diabolic Light", "Drop Marker Away");

	@PlayerHeadmarker(0x46)
	private final ModifiableCallout<HeadMarkerEvent> diabolicWind = new ModifiableCallout<>("Diabolic Wind", "Spread");

	@PlayerStatusCallout(0x3AA)
	private final ModifiableCallout<BuffApplied> throttle = new ModifiableCallout<BuffApplied>("Throttle", "Touch Ghost").statusIcon(0x3AA);

}
