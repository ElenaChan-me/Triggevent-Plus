package gg.xp.xivsupport.triggers.cactbot.sb;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/trial/byakko.ts をもとに移植
@CalloutRepo(name = "The Jade Stoa", duty = KnownDuty.TheJadeStoa)
public class Byakko extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public Byakko(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheJadeStoa);
	}

	@NpcCastCallout(0x2A2F)
	private final ModifiableCallout<AbilityCastStart> stormPulse = ModifiableCallout.durationBasedCall("Storm Pulse", "Raidwide");

	@NpcCastCallout(0x2A2D)
	private final ModifiableCallout<AbilityCastStart> heavenlyStrike = ModifiableCallout.durationBasedCall("Heavenly Strike", "Buster on {event.target}");

	@NpcCastCallout(0x27E2)
	private final ModifiableCallout<AbilityCastStart> highestStakes = ModifiableCallout.durationBasedCall("Highest Stakes", "Stack");

	private final ModifiableCallout<HeadMarkerEvent> whiteHeraldOnYou = new ModifiableCallout<>("White Herald (on You)", "Away from Group");
	private final ModifiableCallout<HeadMarkerEvent> whiteHeraldOther = new ModifiableCallout<>("White Herald (on Other)", "Away from {event.target}");

	@HandleEvents
	public void whiteHerald(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x57)) {
			context.accept((event.getTarget().isThePlayer() ? whiteHeraldOnYou : whiteHeraldOther).getModified(event));
		}
	}

	@PlayerHeadmarker(0x65)
	private final ModifiableCallout<HeadMarkerEvent> bombogenesis = new ModifiableCallout<>("Bombogenesis", "Spread");

}
