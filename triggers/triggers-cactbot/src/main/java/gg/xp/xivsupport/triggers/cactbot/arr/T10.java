package gg.xp.xivsupport.triggers.cactbot.arr;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/raid/t10.ts をもとに移植
@CalloutRepo(name = "The Final Coil Of Bahamut Turn1", duty = KnownDuty.TheFinalCoilOfBahamutTurn1)
public class T10 extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public T10(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheFinalCoilOfBahamutTurn1);
	}

	@NpcCastCallout(0xB5F)
	private final ModifiableCallout<AbilityCastStart> heatLightning = ModifiableCallout.durationBasedCall("Heat Lightning", "Spread");

	private final ModifiableCallout<HeadMarkerEvent> chargeOnYou = new ModifiableCallout<>("Wild Charge (on You)", "Charge on You");
	private final ModifiableCallout<HeadMarkerEvent> chargeOn = new ModifiableCallout<>("Wild Charge", "Charge on {event.target}");
	private final ModifiableCallout<HeadMarkerEvent> preyOnYou = new ModifiableCallout<>("Prey (on You)", "Prey on You");
	private final ModifiableCallout<HeadMarkerEvent> preyOn = new ModifiableCallout<>("Prey", "Prey on {event.target}");

	@HandleEvents
	public void markers(EventContext context, HeadMarkerEvent event) {
		boolean onYou = event.getTarget().isThePlayer();
		if (event.markerIdMatches(0x1F)) {
			context.accept((onYou ? chargeOnYou : chargeOn).getModified(event));
		}
		else if (event.markerIdMatches(0x1E)) {
			context.accept((onYou ? preyOnYou : preyOn).getModified(event));
		}
	}

	private final ModifiableCallout<TetherEvent> cyclonicOnYou = new ModifiableCallout<>("Cyclonic Tether (on You)", "Cyclonic on You");
	private final ModifiableCallout<TetherEvent> cyclonicOn = new ModifiableCallout<>("Cyclonic Tether", "Cyclonic on {event.target}");

	@HandleEvents
	public void cyclonicTether(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x15)) {
			if (event.getTarget().isThePlayer()) {
				context.accept(cyclonicOnYou.getModified(event));
			}
			else {
				context.accept(cyclonicOn.getModified(event));
			}
		}
	}
}
