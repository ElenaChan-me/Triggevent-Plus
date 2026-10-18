package gg.xp.xivsupport.triggers.cactbot.dt;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/raid/r6n.ts をもとに移植
@CalloutRepo(name = "Aac Cruiserweight M2", duty = KnownDuty.M6N)
public class R6n extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long PUDDING_PARTY_MARKER = 0x131;
	private static final long COLOR_RIOT_MARKER = 0x156;

	private XivState state;

	public R6n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.M6N);
	}

	@NpcCastCallout(0xA66F)
	private final ModifiableCallout<AbilityCastStart> mousseMural = ModifiableCallout.durationBasedCall("Mousse Mural", "Raidwide");

	private final ModifiableCallout<HeadMarkerEvent> puddingParty = new ModifiableCallout<>("Pudding Party", "Stack on {event.target}");

	// 詠唱は自分対象なので、頭上マーカーから頭割り対象を取る
	@HandleEvents
	public void puddingParty(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(PUDDING_PARTY_MARKER)) {
			context.accept(puddingParty.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> colorRiotOnYou = new ModifiableCallout<>("Color Riot (On You)", "Tank Cleave on You");
	private final ModifiableCallout<HeadMarkerEvent> colorRiotAvoid = new ModifiableCallout<>("Color Riot (Not On You)", "Avoid Tank Cleave");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> colorRiotSq = SqtTemplates.sq(10_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(COLOR_RIOT_MARKER),
			(e1, s) -> {
				HeadMarkerEvent e2 = s.waitEvent(HeadMarkerEvent.class, hm -> hm.markerIdMatches(COLOR_RIOT_MARKER));
				if (e1.getTarget().isThePlayer() || e2.getTarget().isThePlayer()) {
					s.updateCall(colorRiotOnYou, e1);
				}
				else {
					s.updateCall(colorRiotAvoid, e1);
				}
			});

	@NpcCastCallout(value = 0xA675, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> mousseTouchUp = ModifiableCallout.durationBasedCall("Mousse Touch-up", "Spread");

	@NpcCastCallout(value = {0xA65B, 0xA65C}, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> tasteOfFire = ModifiableCallout.durationBasedCall("Taste of Fire", "Healer Groups in Water");

	@NpcCastCallout(value = 0xA65E, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> tasteOfThunder = ModifiableCallout.durationBasedCall("Taste of Thunder", "Spread out of Water");

}
