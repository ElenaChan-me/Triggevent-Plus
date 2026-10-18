package gg.xp.xivsupport.triggers.cactbot.sb;

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
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.models.XivCombatant;

import java.util.HashMap;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/dungeon/fractal_continuum_hard.ts をもとに移植
@CalloutRepo(name = "The Fractal Continuum Hard", duty = KnownDuty.TheFractalContinuumHard)
public class FractalContinuumHard extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long DISCHORD_A = 0x4D;
	private static final long DISCHORD_B = 0x4E;
	// 炎と氷の円は付与から一定時間後に出るので、そのタイミングに合わせる
	private static final long INFINITE_ELEMENTS_DELAY_MS = 8_000;

	private XivState state;

	public FractalContinuumHard(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheFractalContinuumHard);
	}

	private final Map<Long, XivCombatant> dischord = new HashMap<>();

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		dischord.clear();
	}

	@NpcCastCallout({0x2AE5, 0x29A2})
	private final ModifiableCallout<AbilityCastStart> swipe = ModifiableCallout.durationBasedCall("Swipe", "Swipe");

	@NpcCastCallout({0x2AE4, 0x29A1})
	private final ModifiableCallout<AbilityCastStart> swing = ModifiableCallout.durationBasedCall("Swing", "Swing");

	@NpcCastCallout(0x861)
	private final ModifiableCallout<AbilityCastStart> dragonVoice = ModifiableCallout.durationBasedCall("The Dragon's Voice", "Dragon's Voice");

	@NpcCastCallout(0x860)
	private final ModifiableCallout<AbilityCastStart> ramVoice = ModifiableCallout.durationBasedCall("The Ram's Voice", "Ram's Voice");

	@NpcCastCallout(0x27A5)
	private final ModifiableCallout<AbilityCastStart> citadelBusterMotherbit = ModifiableCallout.durationBasedCall("Citadel Buster (Motherbit)", "Get Behind");

	@NpcCastCallout(0x2793)
	private final ModifiableCallout<AbilityCastStart> aetheroplasm = ModifiableCallout.durationBasedCall("Aetheroplasm", "Buster on {event.target}");

	@NpcCastCallout(0x2792)
	private final ModifiableCallout<AbilityCastStart> citadelBusterWarrior = ModifiableCallout.durationBasedCall("Citadel Buster (Ultima Warrior)", "Away from Front");

	@NpcCastCallout(0x2794)
	private final ModifiableCallout<AbilityCastStart> ceruleumVent = ModifiableCallout.durationBasedCall("Ceruleum Vent", "Knockback");

	@PlayerHeadmarker(0x46)
	private final ModifiableCallout<HeadMarkerEvent> ratzon = new ModifiableCallout<>("Ratzon", "Puddle on You");

	@HandleEvents
	public void dischordCollect(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(DISCHORD_A, DISCHORD_B)) {
			dischord.put(event.getMarkerId(), event.getTarget());
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> dischordStack = new ModifiableCallout<>("Dischord", "Stack with {partner}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> dischordSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hme -> hme.markerIdMatches(DISCHORD_A, DISCHORD_B) && hme.getTarget().isThePlayer(),
			(e1, s) -> {
				// 相方のマーカーが届くのを少し待つ
				s.waitMs(500);
				long partnerId = e1.getMarkerId() == DISCHORD_A ? DISCHORD_B : DISCHORD_A;
				XivCombatant partner = dischord.get(partnerId);
				dischord.clear();
				if (partner != null) {
					s.setParam("partner", partner);
					s.updateCall(dischordStack, e1);
				}
			});

	private final ModifiableCallout<HeadMarkerEvent> massAetheroplasm = new ModifiableCallout<>("Mass Aetheroplasm", "Stack on {event.target}");

	@HandleEvents
	public void massAetheroplasm(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E)) {
			context.accept(massAetheroplasm.getModified(event));
		}
	}

	private final ModifiableCallout<BuffApplied> infiniteFire = new ModifiableCallout<BuffApplied>("Infinite Fire", "Stand on Red Circle").statusIcon(0x477);
	private final ModifiableCallout<BuffApplied> infiniteIce = new ModifiableCallout<BuffApplied>("Infinite Ice", "Stand on Blue Circle").statusIcon(0x478);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> infiniteElementsSq = SqtTemplates.sq((int) INFINITE_ELEMENTS_DELAY_MS + 5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0x477, 0x478) && ba.getTarget().isThePlayer(),
			(e1, s) -> {
				s.waitMs(INFINITE_ELEMENTS_DELAY_MS);
				s.updateCall(e1.buffIdMatches(0x477) ? infiniteFire : infiniteIce, e1);
			});

	@NpcCastCallout({0x27AD, 0x27AE})
	private final ModifiableCallout<AbilityCastStart> deathSpin = ModifiableCallout.durationBasedCall("Death Spin", "Out of Melee");

	@NpcCastCallout({0x27AF, 0x27B0})
	private final ModifiableCallout<AbilityCastStart> aetherBend = ModifiableCallout.durationBasedCall("Aether Bend", "In");

	@NpcCastCallout(0x27B5)
	private final ModifiableCallout<AbilityCastStart> allaganGravity = ModifiableCallout.durationBasedCall("Allagan Gravity", "Spread");

	@NpcCastCallout(0x27B2)
	private final ModifiableCallout<AbilityCastStart> demiUltima = ModifiableCallout.durationBasedCall("Demi Ultima", "Raidwide");

	private final ModifiableCallout<HeadMarkerEvent> allaganFlareOnYou = new ModifiableCallout<>("Allagan Flare (On You)", "Away from Group");
	private final ModifiableCallout<HeadMarkerEvent> allaganFlare = new ModifiableCallout<>("Allagan Flare", "Away from {event.target}");

	@HandleEvents
	public void allaganFlare(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x57)) {
			context.accept((event.getTarget().isThePlayer() ? allaganFlareOnYou : allaganFlare).getModified(event));
		}
	}
}
