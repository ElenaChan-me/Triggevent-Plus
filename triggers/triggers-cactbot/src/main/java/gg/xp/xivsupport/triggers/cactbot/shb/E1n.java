package gg.xp.xivsupport.triggers.cactbot.shb;

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
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.models.XivCombatant;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/raid/e1n.ts をもとに移植
@CalloutRepo(name = "Edens Gate Resurrection", duty = KnownDuty.EdensGateResurrection)
public class E1n extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long VICE_OF_VANITY_TETHER = 0x11;

	private XivState state;

	public E1n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.EdensGateResurrection);
	}

	@NpcCastCallout(0x3D94)
	private final ModifiableCallout<AbilityCastStart> edensGravity = ModifiableCallout.durationBasedCall("Eden's Gravity", "Raidwide");

	@NpcCastCallout(0x3DA4)
	private final ModifiableCallout<AbilityCastStart> fragorMaximus = ModifiableCallout.durationBasedCall("Fragor Maximus", "Raidwide");

	@NpcCastCallout(0x3D9C)
	private final ModifiableCallout<AbilityCastStart> dimensionalShift = ModifiableCallout.durationBasedCall("Dimensional Shift", "Raidwide");

	@NpcCastCallout(0x3D97)
	private final ModifiableCallout<AbilityCastStart> edensFlare = ModifiableCallout.durationBasedCall("Eden's Flare", "Get Under");

	private final ModifiableCallout<TetherEvent> viceOfVanity = new ModifiableCallout<>("Vice of Vanity", "Tank Laser on You");

	@HandleEvents
	public void viceOfVanity(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(VICE_OF_VANITY_TETHER) && event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			context.accept(viceOfVanity.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> spearOfParadiseOnYou = ModifiableCallout.durationBasedCall("Spear of Paradise (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> spearOfParadise = ModifiableCallout.durationBasedCall("Spear of Paradise", "Buster on {event.target}");

	@HandleEvents
	public void spearOfParadise(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x3DA1)) {
			context.accept((event.getTarget().isThePlayer() ? spearOfParadiseOnYou : spearOfParadise).getModified(event));
		}
	}

	@PlayerHeadmarker(0x1C)
	private final ModifiableCallout<HeadMarkerEvent> viceOfApathy = new ModifiableCallout<>("Vice of Apathy", "Drop Puddle, Run Middle");

	private final ModifiableCallout<AbilityCastStart> pureLight = ModifiableCallout.durationBasedCall("Pure Light", "Get Behind");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> pureLightSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x3DA3),
			(e1, s) -> {
				// 詠唱が10.5秒と長いので、外周で誘導できるよう残り6秒で知らせる
				s.waitMs(4_500);
				s.updateCall(pureLight, e1);
			});

}
