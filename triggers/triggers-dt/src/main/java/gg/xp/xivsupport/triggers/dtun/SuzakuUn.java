package gg.xp.xivsupport.triggers.dtun;

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
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/trial/suzaku-un.ts をもとに移植
@CalloutRepo(name = "Suzaku Unreal", duty = KnownDuty.SuzakuUn)
public class SuzakuUn extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public SuzakuUn(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.SuzakuUn);
	}

	@NpcCastCallout(0xA7FC)
	private final ModifiableCallout<AbilityCastStart> screamsOfTheDamned = ModifiableCallout.durationBasedCall("Screams of the Damned", "Raidwide");

	@PlayerHeadmarker(0x8B)
	private final ModifiableCallout<HeadMarkerEvent> rekindle = new ModifiableCallout<>("Rekindle", "Spread");

	@NpcCastCallout(0xA7FB)
	private final ModifiableCallout<AbilityCastStart> cremate = ModifiableCallout.durationBasedCall("Cremate", "Buster on {event.target}");

	@NpcCastCallout(0xA807)
	private final ModifiableCallout<AbilityCastStart> southronStar = ModifiableCallout.durationBasedCall("Southron Star", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> phantomFlurryBuster = ModifiableCallout.durationBasedCall("Phantom Flurry: Buster", "Tankbuster");
	private final ModifiableCallout<AbilityUsedEvent> phantomFlurryBehind = new ModifiableCallout<>("Phantom Flurry: Get Behind", "Behind");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> phantomFlurrySq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xA804),
			(e1, s) -> {
				// 詠唱バーは自身対象なので対象者は出さない
				s.updateCall(phantomFlurryBuster, e1);
				var hit = s.waitEvent(AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0xA804));
				s.updateCall(phantomFlurryBehind, hit);
			});

	private final ModifiableCallout<HeadMarkerEvent> scathingNet = new ModifiableCallout<>("Scathing Net", "Stack on {event.target}");

	@HandleEvents
	public void scathingNetMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0xA1)) {
			context.accept(scathingNet.getModified(event));
		}
	}

	@NpcCastCallout(0xA802)
	private final ModifiableCallout<AbilityCastStart> mesmerizingMelody = ModifiableCallout.durationBasedCall("Mesmerizing Melody", "Out");

	@NpcCastCallout(0xA803)
	private final ModifiableCallout<AbilityCastStart> ruthlessRefrain = ModifiableCallout.durationBasedCall("Ruthless Refrain", "In");

}
