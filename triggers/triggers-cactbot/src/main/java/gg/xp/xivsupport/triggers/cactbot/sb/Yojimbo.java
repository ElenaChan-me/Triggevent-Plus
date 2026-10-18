package gg.xp.xivsupport.triggers.cactbot.sb;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/trial/yojimbo.ts をもとに移植
@CalloutRepo(name = "Kugane Ohashi", duty = KnownDuty.KuganeOhashi)
public class Yojimbo extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public Yojimbo(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.KuganeOhashi);
	}

	@PlayerHeadmarker(0x57)
	private final ModifiableCallout<HeadMarkerEvent> gigaJump = new ModifiableCallout<>("Giga Jump", "Run Marker Away");

	@PlayerHeadmarker(0x37)
	private final ModifiableCallout<HeadMarkerEvent> dorito = new ModifiableCallout<>("Dorito Marker", "Dorito Stack");

	@PlayerHeadmarker(0x90)
	private final ModifiableCallout<HeadMarkerEvent> gekko = new ModifiableCallout<>("Gekko", "Gekko Marker");

	@PlayerHeadmarker(0x05)
	private final ModifiableCallout<HeadMarkerEvent> enchain = new ModifiableCallout<>("Enchain", "Out");

}
