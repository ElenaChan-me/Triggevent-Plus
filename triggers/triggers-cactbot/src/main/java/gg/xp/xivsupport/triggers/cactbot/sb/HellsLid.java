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

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/dungeon/hells_lid.ts をもとに移植
@CalloutRepo(name = "Hells Lid", duty = KnownDuty.HellsLid)
public class HellsLid extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public HellsLid(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.HellsLid);
	}

	// オオタケ丸

	@NpcCastCallout(0x27BE)
	private final ModifiableCallout<AbilityCastStart> swing = ModifiableCallout.durationBasedCall("Swing", "Out");

	private final ModifiableCallout<HeadMarkerEvent> targetedLeapOnYou = new ModifiableCallout<>("Targeted Leap (On You)", "Boss Chasing: Get Away");
	private final ModifiableCallout<HeadMarkerEvent> targetedLeap = new ModifiableCallout<>("Targeted Leap", "Avoid Boss");

	@HandleEvents
	public void targetedLeap(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x01)) {
			context.accept((event.getTarget().isThePlayer() ? targetedLeapOnYou : targetedLeap).getModified(event));
		}
	}

	// カマイタチ

	@NpcCastCallout(0x27C8)
	private final ModifiableCallout<AbilityCastStart> circlingWinds = ModifiableCallout.durationBasedCall("Circling Winds", "In");

	@NpcCastCallout(0x27C9)
	private final ModifiableCallout<AbilityCastStart> rollingWinds = ModifiableCallout.durationBasedCall("Rolling Winds", "Sides");

	// 玄武

	@NpcCastCallout(0x27D4)
	private final ModifiableCallout<AbilityCastStart> sinisterTide = ModifiableCallout.durationBasedCall("Sinister Tide", "Avoid Water Orb");

	@PlayerHeadmarker(0x2B)
	private final ModifiableCallout<HeadMarkerEvent> hellOfWaste = new ModifiableCallout<>("Hell of Waste", "Spread");

	@NpcCastCallout(0x27D3)
	private final ModifiableCallout<AbilityCastStart> hellOfWaves = ModifiableCallout.durationBasedCall("Hell of Waves", "Knockback");

}
