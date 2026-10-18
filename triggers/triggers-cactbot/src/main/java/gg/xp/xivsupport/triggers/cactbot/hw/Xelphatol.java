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

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/dungeon/xelphatol.ts をもとに移植
@CalloutRepo(name = "Xelphatol", duty = KnownDuty.Xelphatol)
public class Xelphatol extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public Xelphatol(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.Xelphatol);
	}

	// 1ボス目（Nuzal Hueloc）

	@NpcCastCallout(0x19C8)
	private final ModifiableCallout<AbilityCastStart> longBurst = ModifiableCallout.durationBasedCall("Long Burst", "Raidwide");

	@NpcCastCallout(0x19CC)
	private final ModifiableCallout<AbilityCastStart> hotBlast = ModifiableCallout.durationBasedCall("Hot Blast", "Under");

	// 2ボス目（Dotoli Ciloc）

	@NpcCastCallout(0x19CF)
	private final ModifiableCallout<AbilityCastStart> onHigh = ModifiableCallout.durationBasedCall("On High", "Knockback");

	@PlayerHeadmarker(0x17)
	private final ModifiableCallout<HeadMarkerEvent> darkWings = new ModifiableCallout<>("Dark Wings", "Puddle on You");

	// 3ボス目（Tozol Huatotl）

	@NpcCastCallout(0x19D5)
	private final ModifiableCallout<AbilityCastStart> ixaliAero = ModifiableCallout.durationBasedCall("Ixali Aero (Raidwide)", "Raidwide");

	@PlayerHeadmarker(0x46)
	private final ModifiableCallout<HeadMarkerEvent> bill = new ModifiableCallout<>("Bill", "Spread");

	private final ModifiableCallout<HeadMarkerEvent> hawk = new ModifiableCallout<>("Hawk", "Stack on {event.target}");

	@HandleEvents
	public void hawk(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E)) {
			context.accept(hawk.getModified(event));
		}
	}

}
