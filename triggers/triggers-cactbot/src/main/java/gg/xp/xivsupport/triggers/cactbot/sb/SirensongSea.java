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

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/dungeon/sirensong_sea.ts をもとに移植
@CalloutRepo(name = "The Sirensong Sea", duty = KnownDuty.TheSirensongSea)
public class SirensongSea extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public SirensongSea(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheSirensongSea);
	}

	// ルガト

	private final ModifiableCallout<HeadMarkerEvent> hydroball = new ModifiableCallout<>("Hydroball", "Stack on {event.target}");

	@HandleEvents
	public void hydroball(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E)) {
			context.accept(hydroball.getModified(event));
		}
	}

	@NpcCastCallout(0x1F58)
	private final ModifiableCallout<AbilityCastStart> seaSwallowsAll = ModifiableCallout.durationBasedCall("Sea Swallows All", "Draw In");

	@NpcCastCallout(0x1F59)
	private final ModifiableCallout<AbilityCastStart> overtow = ModifiableCallout.durationBasedCall("Overtow", "Knockback");

	// ガバナー

	@NpcCastCallout(0x1F5E)
	private final ModifiableCallout<AbilityCastStart> shadowflow = ModifiableCallout.durationBasedCall("Shadowflow", "Avoid Shadows");

	@NpcCastCallout(0x1F5C)
	private final ModifiableCallout<AbilityCastStart> bloodburst = ModifiableCallout.durationBasedCall("Bloodburst", "Raidwide");

	@PlayerHeadmarker(0x16)
	private final ModifiableCallout<HeadMarkerEvent> enterNight = new ModifiableCallout<>("Enter Night", "Out");

	@NpcCastCallout(0x2352)
	private final ModifiableCallout<AbilityCastStart> seductiveScream = ModifiableCallout.durationBasedCall("Seductive Scream", "Interrupt {event.source}");

	// ローレライ

	@NpcCastCallout(0x1F65)
	private final ModifiableCallout<AbilityCastStart> morbidAdvance = ModifiableCallout.durationBasedCall("Morbid Advance", "Forward March");

	@NpcCastCallout(0x1F66)
	private final ModifiableCallout<AbilityCastStart> morbidRetreat = ModifiableCallout.durationBasedCall("Morbid Retreat", "Backwards March");

	@NpcCastCallout(0x1F67)
	private final ModifiableCallout<AbilityCastStart> somberMelody = ModifiableCallout.durationBasedCall("Somber Melody", "Raidwide");

}
