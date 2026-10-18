package gg.xp.xivsupport.triggers.cactbot.sb;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/raid/o5s.ts をもとに移植
@CalloutRepo(name = "Sigmascape V10 Savage", duty = KnownDuty.O1S)
public class O5s extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public O5s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.O1S);
	}

	@NpcCastCallout(0x28B1)
	private final ModifiableCallout<AbilityCastStart> doomStrike = ModifiableCallout.durationBasedCall("Doom Strike", "Buster on {event.target}");

	@NpcCastCallout(0x28A4)
	private final ModifiableCallout<AbilityCastStart> headOn = ModifiableCallout.durationBasedCall("Head On", "Out");

	@NpcCastCallout(0x28B2)
	private final ModifiableCallout<AbilityCastStart> diabolicHeadlamp = ModifiableCallout.durationBasedCall("Diabolic Headlamp", "Stack Middle");

	@PlayerHeadmarker(0x01)
	private final ModifiableCallout<HeadMarkerEvent> diabolicLight = new ModifiableCallout<>("Diabolic Light", "Light");

	@PlayerHeadmarker(0x46)
	private final ModifiableCallout<HeadMarkerEvent> diabolicWind = new ModifiableCallout<>("Diabolic Wind", "Wind");

}
