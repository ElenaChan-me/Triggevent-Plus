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
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;
import gg.xp.xivsupport.models.XivCombatant;

import java.util.ArrayList;
import java.util.List;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/dungeon/gubal_library_hard.ts をもとに移植
@CalloutRepo(name = "The Great Gubal Library Hard", duty = KnownDuty.TheGreatGubalLibraryHard)
public class GubalLibraryHard extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public GubalLibraryHard(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheGreatGubalLibraryHard);
	}

	@NpcCastCallout(0x1945)
	private final ModifiableCallout<AbilityCastStart> bibliocide = ModifiableCallout.durationBasedCall("Bibliocide", "Raidwide");

	private final ModifiableCallout<HeadMarkerEvent> ferrofluidClose = new ModifiableCallout<>("Ferrofluid: Same Markers", "Close to Boss");
	private final ModifiableCallout<HeadMarkerEvent> ferrofluidAway = new ModifiableCallout<>("Ferrofluid: Different Markers", "Away from Boss");

	// 自分とボスの印が揃ったら、同じなら反発、違えば引き寄せになる
	private final List<Long> ferrofluidMarkers = new ArrayList<>();

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		ferrofluidMarkers.clear();
	}

	@HandleEvents
	public void ferrofluid(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(0x0030, 0x0031)) {
			return;
		}
		XivCombatant target = event.getTarget();
		if (!target.isThePlayer() && target.isPc()) {
			return;
		}
		ferrofluidMarkers.add(event.getMarkerId());
		if (ferrofluidMarkers.size() >= 2) {
			boolean same = ferrofluidMarkers.get(0).equals(ferrofluidMarkers.get(1));
			ferrofluidMarkers.clear();
			context.accept((same ? ferrofluidClose : ferrofluidAway).getModified(event));
		}
	}

	private final ModifiableCallout<TetherEvent> slosh = new ModifiableCallout<>("Slosh", "Away from Boss");

	@HandleEvents
	public void sloshTether(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x0039) && event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			context.accept(slosh.getModified(event));
		}
	}

	@PlayerStatusCallout(0x46F)
	private final ModifiableCallout<BuffApplied> sunseal = new ModifiableCallout<BuffApplied>("Sunseal", "Stand in Red").statusIcon(0x46F);

	@PlayerStatusCallout(0x470)
	private final ModifiableCallout<BuffApplied> moonseal = new ModifiableCallout<BuffApplied>("Moonseal", "Stand in Blue").statusIcon(0x470);

	private final ModifiableCallout<AbilityCastStart> condensedLibraInterrupt = ModifiableCallout.durationBasedCall("Condensed Libra (Interrupt)", "Interrupt {event.source}");
	private final ModifiableCallout<AbilityCastStart> condensedLibraStun = ModifiableCallout.durationBasedCall("Condensed Libra (Stun)", "Stun {event.source}");

	// 止めないとタンクに被ダメージ上昇が付く
	@HandleEvents
	public void condensedLibra(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x198D)) {
			return;
		}
		if (state.playerJobMatches(job -> job.isTank() || job.isPranged())) {
			context.accept(condensedLibraInterrupt.getModified(event));
		}
		else if (state.playerJobMatches(Job::isMeleeDps)) {
			context.accept(condensedLibraStun.getModified(event));
		}
	}

	@NpcCastCallout(0x1956)
	private final ModifiableCallout<AbilityCastStart> propertiesOfQuakes = ModifiableCallout.durationBasedCall("Properties of Quakes", "Stand in Light Circle");

	@NpcCastCallout(0x1957)
	private final ModifiableCallout<AbilityCastStart> propertiesOfTornadoes = ModifiableCallout.durationBasedCall("Properties of Tornadoes", "Stand in Dark Circle");

	@NpcCastCallout(0x1959)
	private final ModifiableCallout<AbilityCastStart> propertiesOfImps = ModifiableCallout.durationBasedCall("Properties of Imps", "Cleanse in Green Circle");

	@NpcCastCallout(0x195A)
	private final ModifiableCallout<AbilityCastStart> propertiesOfThunder = ModifiableCallout.durationBasedCall("Properties of Thunder III", "Spread");

	@NpcCastCallout(0x1955)
	private final ModifiableCallout<AbilityCastStart> propertiesOfDarkness = ModifiableCallout.durationBasedCall("Properties of Darkness II", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> eclipticMeteor = ModifiableCallout.durationBasedCall("Ecliptic Meteor", "Hide Behind Boulder");

	// 詠唱が長いので、岩陰に隠れる猶予が約10秒残るところまで待つ
	private static final long ECLIPTIC_METEOR_DELAY_MS = 14_000;

	@AutoFeed
	private final SequentialTrigger<BaseEvent> eclipticMeteorSq = SqtTemplates.sq(30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x195D),
			(e1, s) -> {
				s.waitMs(ECLIPTIC_METEOR_DELAY_MS);
				s.updateCall(eclipticMeteor, e1);
			});
}
