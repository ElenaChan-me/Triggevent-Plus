package gg.xp.xivsupport.triggers.cactbot.dt;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/raid/r5n.ts をもとに移植
@CalloutRepo(name = "Aac Cruiserweight M1", duty = KnownDuty.M5N)
public class R5n extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long DEEP_CUT_MARKER = 0x1D7;

	private XivState state;

	public R5n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.M5N);
	}

	@NpcCastCallout(0xA6C9)
	private final ModifiableCallout<AbilityCastStart> doTheHustleWest = ModifiableCallout.durationBasedCall("Do the Hustle (West Safe)", "West");

	@NpcCastCallout(0xA6CA)
	private final ModifiableCallout<AbilityCastStart> doTheHustleEast = ModifiableCallout.durationBasedCall("Do the Hustle (East Safe)", "East");

	private final ModifiableCallout<HeadMarkerEvent> deepCutOnYou = new ModifiableCallout<>("Deep Cut (On You)", "Tank Cleave on You");
	private final ModifiableCallout<HeadMarkerEvent> deepCutAvoid = new ModifiableCallout<>("Deep Cut (Not On You)", "Avoid Tank Cleave");

	// 2人分の頭割りマーカーが揃ってから自分が含まれるか判定する
	@AutoFeed
	private final SequentialTrigger<BaseEvent> deepCutSq = SqtTemplates.sq(10_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(DEEP_CUT_MARKER),
			(e1, s) -> {
				HeadMarkerEvent e2 = s.waitEvent(HeadMarkerEvent.class, hm -> hm.markerIdMatches(DEEP_CUT_MARKER));
				if (e1.getTarget().isThePlayer() || e2.getTarget().isThePlayer()) {
					s.updateCall(deepCutOnYou, e1);
				}
				else {
					s.updateCall(deepCutAvoid, e1);
				}
			});

	@NpcCastCallout(0xA6FF)
	private final ModifiableCallout<AbilityCastStart> fullBeat = ModifiableCallout.durationBasedCall("Full Beat", "Stack on {event.target}");

	@NpcCastCallout(0xA6F9)
	private final ModifiableCallout<AbilityCastStart> discoInfernal = ModifiableCallout.durationBasedCall("Disco Infernal", "Raidwide");

	@NpcCastCallout(0xA6C8)
	private final ModifiableCallout<AbilityCastStart> celebrateGoodTimes = ModifiableCallout.durationBasedCall("Celebrate Good Times", "Raidwide");

	@NpcCastCallout(value = {0xA702, 0xA703}, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> eighthBeats = ModifiableCallout.durationBasedCall("Eighth Beats", "Spread");

}
