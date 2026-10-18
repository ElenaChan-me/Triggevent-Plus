package gg.xp.xivsupport.triggers.cactbot.sb;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/trial/seiryu.ts をもとに移植
@CalloutRepo(name = "The Wreath Of Snakes", duty = KnownDuty.TheWreathOfSnakes)
public class Seiryu extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public Seiryu(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheWreathOfSnakes);
	}

	@NpcCastCallout(0x37FE)
	private final ModifiableCallout<AbilityCastStart> fifthElement = ModifiableCallout.durationBasedCall("Fifth Element", "Raidwide");

	@NpcCastCallout(0x3A08)
	private final ModifiableCallout<AbilityCastStart> serpentEyeSigil = ModifiableCallout.durationBasedCall("Serpent-Eye Sigil", "In");

	@NpcCastCallout(0x3A07)
	private final ModifiableCallout<AbilityCastStart> onmyoSigil = ModifiableCallout.durationBasedCall("Onmyo Sigil", "Out");

	@NpcCastCallout(0x37FD)
	private final ModifiableCallout<AbilityCastStart> infirmSoul = ModifiableCallout.durationBasedCall("Infirm Soul", "Buster on {event.target}");

	@NpcAbilityUsedCallout(value = 0x3C25, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> serpentAscending = new ModifiableCallout<>("Serpent Ascending", "Get Towers");

	@PlayerHeadmarker(0xA9)
	private final ModifiableCallout<HeadMarkerEvent> serpentDescending = new ModifiableCallout<>("Serpent Descending", "Spread");

	@NpcAbilityUsedCallout(value = 0x37E5, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> handprintEast = new ModifiableCallout<>("Handprint (East)", "Go East");

	@NpcAbilityUsedCallout(value = 0x37E6, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> handprintWest = new ModifiableCallout<>("Handprint (West)", "Go West");

}
