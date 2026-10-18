package gg.xp.xivsupport.triggers.cactbot.hw;

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
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.models.XivCombatant;

import java.util.ArrayList;
import java.util.List;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/raid/a1s.ts をもとに移植
@CalloutRepo(name = "Alexander The Fist Of The Father Savage", duty = KnownDuty.AlexanderTheFistOfTheFatherSavage)
public class A1s extends AutoChildEventHandler implements FilteredEventHandler {

	// 同時に付く分をまとめるための待ち時間
	private static final long HYDROTHERMAL_COLLECT_MS = 2_000;
	private static final long HYPERCOMPRESSED_COLLECT_MS = 300;
	private static final int PARTY_SIZE = 8;

	private XivState state;

	public A1s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AlexanderTheFistOfTheFatherSavage);
	}

	@PlayerHeadmarker(0x1E)
	private final ModifiableCallout<HeadMarkerEvent> hydrothermalOnYou = new ModifiableCallout<>("Hydrothermal Missile (on You)", "Hydrothermal on You");

	private final ModifiableCallout<HeadMarkerEvent> hydrothermalTargets = new ModifiableCallout<>("Hydrothermal Missile (Targets)", "Hydrothermal on {players}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> hydrothermalSq = SqtTemplates.sq(10_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(0x1E),
			(e1, s) -> {
				List<HeadMarkerEvent> markers = new ArrayList<>();
				markers.add(e1);
				markers.addAll(s.waitEventsUntil(PARTY_SIZE - 1,
						HeadMarkerEvent.class, hm -> hm.markerIdMatches(0x1E),
						BaseEvent.class, e -> e1.getEffectiveTimeSince().toMillis() > HYDROTHERMAL_COLLECT_MS));
				List<XivCombatant> players = markers.stream().map(HeadMarkerEvent::getTarget).toList();
				s.setParam("players", players);
				s.updateCall(hydrothermalTargets, e1);
			});

	@NpcCastCallout(0xE46)
	private final ModifiableCallout<AbilityCastStart> resinBomb = ModifiableCallout.durationBasedCall("Resin Bomb", "Bait Resin Bomb");

	private final ModifiableCallout<AbilityCastStart> hypercompressedOnYou = ModifiableCallout.durationBasedCall("Hypercompressed Plasma (on You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> hypercompressedOther = ModifiableCallout.durationBasedCall("Hypercompressed Plasma (not on You)", "Tankbusters");

	// 2体同時に詠唱することがあるので、少し待ってから自分が対象か判定する
	@AutoFeed
	private final SequentialTrigger<BaseEvent> hypercompressedSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xE4A) && !acs.getSource().isPc(),
			(e1, s) -> {
				List<AbilityCastStart> casts = new ArrayList<>();
				casts.add(e1);
				casts.addAll(s.waitEventsUntil(PARTY_SIZE - 1,
						AbilityCastStart.class, acs -> acs.abilityIdMatches(0xE4A) && !acs.getSource().isPc(),
						BaseEvent.class, e -> e1.getEffectiveTimeSince().toMillis() > HYPERCOMPRESSED_COLLECT_MS));
				AbilityCastStart mine = casts.stream().filter(acs -> acs.getTarget().isThePlayer()).findFirst().orElse(null);
				if (mine != null) {
					s.updateCall(hypercompressedOnYou, mine);
				}
				else {
					s.updateCall(hypercompressedOther, e1);
				}
			});

}
