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
import gg.xp.xivsupport.events.actlines.events.RawAddCombatantEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/dungeon/shisui_of_the_violet_tides.ts をもとに移植
@CalloutRepo(name = "Shisui Of The Violet Tides", duty = KnownDuty.ShisuiOfTheVioletTides)
public class ShisuiOfTheVioletTides extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long KAMIKIRI = 6238;
	private static final long NAISHI_NO_KAMI = 6244;

	private XivState state;

	public ShisuiOfTheVioletTides(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.ShisuiOfTheVioletTides);
	}

	private final ModifiableCallout<RawAddCombatantEvent> kamikiriAdd = new ModifiableCallout<>("Kamikiri Add", "Kill {event.entity}");
	private final ModifiableCallout<RawAddCombatantEvent> naishiNoKami = new ModifiableCallout<>("Naishi-no-Kami Adds", "Kill Adds");

	@HandleEvents
	public void addedCombatant(EventContext context, RawAddCombatantEvent event) {
		long nameId = event.getEntity().getbNpcNameId();
		if (nameId == KAMIKIRI) {
			context.accept(kamikiriAdd.getModified(event));
		}
		else if (nameId == NAISHI_NO_KAMI) {
			context.accept(naishiNoKami.getModified(event));
		}
	}

	@PlayerHeadmarker(0x0E)
	private final ModifiableCallout<HeadMarkerEvent> digestiveFluid = new ModifiableCallout<>("Digestive Fluid", "Spread");

	@NpcCastCallout(0x1F7A)
	private final ModifiableCallout<AbilityCastStart> seduce = ModifiableCallout.durationBasedCall("Seduce", "Get in Box");

	@PlayerHeadmarker(0x01)
	private final ModifiableCallout<HeadMarkerEvent> geothermalFlatulence = new ModifiableCallout<>("Geothermal Flatulence", "Chasing AoE on You");

	@NpcCastCallout(0x1F82)
	private final ModifiableCallout<AbilityCastStart> madStare = ModifiableCallout.durationBasedCall("Mad Stare", "Look Away");

}
