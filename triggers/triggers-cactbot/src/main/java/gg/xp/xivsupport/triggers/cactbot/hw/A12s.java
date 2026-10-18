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
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;
import gg.xp.xivsupport.models.XivCombatant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/raid/a12s.ts をもとに移植
@CalloutRepo(name = "Alexander The Soul Of The Creator Savage", duty = KnownDuty.AlexanderTheSoulOfTheCreatorSavage)
public class A12s extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long scourgeMarkerId = 0x1E;
	// 後半の Holy Scourge でも同じマーカーが付くので、最初の2人分だけ扱う
	private static final int blazingScourgeTargets = 2;

	private XivState state;

	public A12s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AlexanderTheSoulOfTheCreatorSavage);
	}

	private final List<XivCombatant> scourge = new ArrayList<>();

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		scourge.clear();
	}

	@NpcCastCallout(0x19E9)
	private final ModifiableCallout<AbilityCastStart> punishingHeat = ModifiableCallout.durationBasedCall("Punishing Heat", "Buster on {event.target}");

	private final ModifiableCallout<HeadMarkerEvent> scourgeOnYou = new ModifiableCallout<>("Blazing Scourge (On You)", "Scourge on You");

	@HandleEvents
	public void scourgeMarker(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(scourgeMarkerId)) {
			return;
		}
		if (scourge.size() <= blazingScourgeTargets && event.getTarget().isThePlayer()) {
			context.accept(scourgeOnYou.getModified(event));
		}
		scourge.add(event.getTarget());
	}

	private final ModifiableCallout<HeadMarkerEvent> scourgeReport = new ModifiableCallout<>("Blazing Scourge (Healer)", "Scourge: {players}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> scourgeReportSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(scourgeMarkerId),
			(e1, s) -> {
				if (!state.playerJobMatches(job -> job.isHealer() || job == Job.BLU)) {
					return;
				}
				s.waitMs(500);
				if (scourge.isEmpty() || scourge.size() > blazingScourgeTargets) {
					return;
				}
				String names = scourge.stream()
						.map(XivCombatant::getName)
						.sorted()
						.collect(Collectors.joining(", "));
				s.setParam("players", names);
				s.updateCall(scourgeReport, e1);
			});

	@NpcCastCallout(0x19EE)
	private final ModifiableCallout<AbilityCastStart> megaHoly = ModifiableCallout.durationBasedCall("Mega Holy", "Raidwide");

	private final ModifiableCallout<HeadMarkerEvent> incineratingHeat = new ModifiableCallout<>("Incinerating Heat", "Stack on {event.target}");

	@HandleEvents
	public void incineratingHeatMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E)) {
			context.accept(incineratingHeat.getModified(event));
		}
	}

	@NpcCastCallout(0x19EB)
	private final ModifiableCallout<AbilityCastStart> laserSacrament = ModifiableCallout.durationBasedCall("Laser Sacrament", "Lasers");

	@NpcCastCallout(0x19ED)
	private final ModifiableCallout<AbilityCastStart> radiantSacrament = ModifiableCallout.durationBasedCall("Radiant Sacrament", "Get Under");

	private final ModifiableCallout<TetherEvent> houseArrest = new ModifiableCallout<>("House Arrest", "Close Tethers ({partner})");
	private final ModifiableCallout<TetherEvent> restrainingOrder = new ModifiableCallout<>("Restraining Order", "Far Tethers ({partner})");

	@HandleEvents
	public void sentenceTethers(EventContext context, TetherEvent event) {
		if (!event.tetherIdMatches(0x1C, 0x1D) || !event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			return;
		}
		XivCombatant partner = event.getTargetMatching(cbt -> !cbt.isThePlayer());
		if (partner == null) {
			return;
		}
		ModifiableCallout<TetherEvent> call = event.tetherIdMatches(0x1C) ? houseArrest : restrainingOrder;
		context.accept(call.getModified(event, Map.of("partner", partner)));
	}

	@PlayerStatusCallout(0x462)
	private final ModifiableCallout<BuffApplied> sharedSentence = new ModifiableCallout<BuffApplied>("Shared Sentence", "Shared Sentence").statusIcon(0x462);

	@PlayerStatusCallout(0x460)
	private final ModifiableCallout<BuffApplied> defamation = new ModifiableCallout<BuffApplied>("Defamation", "Defamation").statusIcon(0x460);

	@PlayerHeadmarker(0x17)
	private final ModifiableCallout<HeadMarkerEvent> judgmentCrystal = new ModifiableCallout<>("Judgment Crystal", "Crystal on You");

	@NpcCastCallout(0x1A0B)
	private final ModifiableCallout<AbilityCastStart> holyScourge = ModifiableCallout.durationBasedCall("Holy Scourge", "Shared Tankbuster");

	private final ModifiableCallout<AbilityCastStart> chasteningHeatOnYou = ModifiableCallout.durationBasedCall("Chastening Heat (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> chasteningHeatSwap = ModifiableCallout.durationBasedCall("Chastening Heat (Other Tank)", "Tank Swap");
	private final ModifiableCallout<AbilityCastStart> chasteningHeat = ModifiableCallout.durationBasedCall("Chastening Heat", "Tankbuster");

	@HandleEvents
	public void chasteningHeatCast(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x1A0D)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(chasteningHeatOnYou.getModified(event));
		}
		else if (state.playerJobMatches(Job::isTank)) {
			context.accept(chasteningHeatSwap.getModified(event));
		}
		else {
			context.accept(chasteningHeat.getModified(event));
		}
	}

	private final ModifiableCallout<TetherEvent> communionTether = new ModifiableCallout<>("Communion", "Puddle Tether on You");

	@HandleEvents
	public void communion(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x36) && event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			context.accept(communionTether.getModified(event));
		}
	}

}
