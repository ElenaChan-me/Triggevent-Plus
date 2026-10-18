package gg.xp.xivsupport.triggers.cactbot.hw;

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
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.models.XivCombatant;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/raid/a12n.ts をもとに移植
@CalloutRepo(name = "Alexander The Soul Of The Creator", duty = KnownDuty.AlexanderTheSoulOfTheCreator)
public class A12n extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public A12n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AlexanderTheSoulOfTheCreator);
	}

	@NpcCastCallout(0x1AE4)
	private final ModifiableCallout<AbilityCastStart> punishingHeat = ModifiableCallout.durationBasedCall("Punishing Heat", "Buster on {event.target}");

	@PlayerHeadmarker(0x001E)
	private final ModifiableCallout<HeadMarkerEvent> blazingScourge = new ModifiableCallout<>("Blazing Scourge", "Spread");

	@NpcCastCallout(0x1AE7)
	private final ModifiableCallout<AbilityCastStart> megaHoly = ModifiableCallout.durationBasedCall("Mega Holy", "Raidwide");

	// アサルト対象者は頭割りに入ってはいけない
	private boolean assault;

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		assault = false;
	}

	private final ModifiableCallout<HeadMarkerEvent> aggravatedAssault = new ModifiableCallout<>("Aggravated Assault", "Spread, Don't Stack");

	@HandleEvents
	public void aggravatedAssault(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x0010) && event.getTarget().isThePlayer()) {
			assault = true;
			context.accept(aggravatedAssault.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> stackOnYou = new ModifiableCallout<>("Incinerating Heat/Shared Sentence: Stack on You", "Stack on You");
	private final ModifiableCallout<HeadMarkerEvent> stackOn = new ModifiableCallout<>("Incinerating Heat/Shared Sentence: Stack", "Stack on {event.target}");

	// アサルトのマーカーは頭割りマーカーより後に来るため、少し待ってから判定する
	@AutoFeed
	private final SequentialTrigger<BaseEvent> heatAndSolidaritySq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(0x003E),
			(e1, s) -> {
				s.waitMs(500);
				if (!assault) {
					s.updateCall(e1.getTarget().isThePlayer() ? stackOnYou : stackOn, e1);
				}
				assault = false;
			});

	@NpcCastCallout(0x1AE5)
	private final ModifiableCallout<AbilityCastStart> laserSacrament = ModifiableCallout.durationBasedCall("Laser Sacrament", "Lasers");

	private final ModifiableCallout<TetherEvent> communion = new ModifiableCallout<>("Communion", "Puddle Tether on You");

	@HandleEvents
	public void communionTether(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x0036) && event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			context.accept(communion.getModified(event));
		}
	}
}
