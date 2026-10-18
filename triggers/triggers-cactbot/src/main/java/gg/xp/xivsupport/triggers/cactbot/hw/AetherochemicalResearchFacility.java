package gg.xp.xivsupport.triggers.cactbot.hw;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.Job;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/dungeon/aetherochemical_research_facility.ts をもとに移植
@CalloutRepo(name = "The Aetherochemical Research Facility", duty = KnownDuty.TheAetherochemicalResearchFacility)
public class AetherochemicalResearchFacility extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public AetherochemicalResearchFacility(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheAetherochemicalResearchFacility);
	}

	@NpcCastCallout(0x10EB)
	private final ModifiableCallout<AbilityCastStart> petrifaction = ModifiableCallout.durationBasedCall("Petrifaction", "Look Away");

	private final ModifiableCallout<AbilityUsedEvent> inertiaStream = new ModifiableCallout<>("Inertia Stream", "Stack on {event.target}");

	// タンクは本来この頭割りに参加しない
	@HandleEvents
	public void inertiaStream(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x10ED) && event.isFirstTarget() && !event.getSource().isPc()
		    && !state.playerJobMatches(Job::isTank)) {
			context.accept(inertiaStream.getModified(event));
		}
	}

	@NpcCastCallout(0x10FC)
	private final ModifiableCallout<AbilityCastStart> darkOrb = ModifiableCallout.durationBasedCall("Dark Orb", "Buster on {event.target}");

	@NpcCastCallout(0x1109)
	private final ModifiableCallout<AbilityCastStart> shadowFlare = ModifiableCallout.durationBasedCall("Shadow Flare", "Raidwide");

	@NpcCastCallout(0x110A)
	private final ModifiableCallout<AbilityCastStart> annihilation = ModifiableCallout.durationBasedCall("Annihilation", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> universalManipulation = ModifiableCallout.durationBasedCall("Universal Manipulation", "Stand in Dark Portal");

	// 判定が反映されるまで約2秒かかるので、残り6秒の時点で知らせる
	private static final long UNIVERSAL_MANIPULATION_LEAD_MS = 6_000;

	@AutoFeed
	private final SequentialTrigger<BaseEvent> universalManipulationSq = SqtTemplates.sq(30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x1105),
			(e1, s) -> {
				long delay = e1.getInitialDuration().toMillis() - UNIVERSAL_MANIPULATION_LEAD_MS;
				if (delay > 0) {
					s.waitMs(delay);
				}
				s.updateCall(universalManipulation, e1);
			});
}
