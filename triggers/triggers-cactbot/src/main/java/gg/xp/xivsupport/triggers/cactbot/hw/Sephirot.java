package gg.xp.xivsupport.triggers.cactbot.hw;

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

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/trial/sephirot.ts をもとに移植
@CalloutRepo(name = "Containment Bay S1 T7", duty = KnownDuty.ContainmentBayS1T7)
public class Sephirot extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public Sephirot(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.ContainmentBayS1T7);
	}

	private final ModifiableCallout<HeadMarkerEvent> fiendishRage = new ModifiableCallout<>("Fiendish Rage", "Stack on {event.target}");

	@HandleEvents
	public void stackMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x0048)) {
			context.accept(fiendishRage.getModified(event));
		}
	}

	@PlayerHeadmarker(0x0046)
	private final ModifiableCallout<HeadMarkerEvent> ratzon = new ModifiableCallout<>("Ratzon", "Spread");

	@PlayerHeadmarker(0x0028)
	private final ModifiableCallout<HeadMarkerEvent> earthShaker = new ModifiableCallout<>("Earth Shaker", "Earthshaker on You");

	@NpcCastCallout(0x16DD)
	private final ModifiableCallout<AbilityCastStart> ain = ModifiableCallout.durationBasedCall("Ain", "Get Behind");

	// 詠唱位置が安定しないため、ノックバックの方向までは出さない
	@NpcCastCallout(0x16EA)
	private final ModifiableCallout<AbilityCastStart> pillarOfMercy = ModifiableCallout.durationBasedCall("Pillar of Mercy", "Knockback");

	@NpcCastCallout(0x16EC)
	private final ModifiableCallout<AbilityCastStart> revelation = ModifiableCallout.durationBasedCall("Storm of Words: Revelation", "Kill Storm of Words");

	@NpcCastCallout(0x16EB)
	private final ModifiableCallout<AbilityCastStart> malkuth = ModifiableCallout.durationBasedCall("Malkuth", "Knockback");
}
