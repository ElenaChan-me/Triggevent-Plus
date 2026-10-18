package gg.xp.xivsupport.triggers.cactbot.hw;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.Job;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivCombatant;
import gg.xp.xivsupport.models.XivPlayerCharacter;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/raid/a3s.ts をもとに移植
@CalloutRepo(name = "Alexander The Arm Of The Father Savage", duty = KnownDuty.AlexanderTheArmOfTheFatherSavage)
public class A3s extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public A3s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AlexanderTheArmOfTheFatherSavage);
	}

	@PlayerHeadmarker(0x001A)
	private final ModifiableCallout<HeadMarkerEvent> sluice = new ModifiableCallout<>("Sluice", "Sluice on You");

	@PlayerHeadmarker(0x0025)
	private final ModifiableCallout<HeadMarkerEvent> digititisTank = new ModifiableCallout<>("Digititis (Tank)", "Tank Debuff");

	@PlayerHeadmarker(0x0022)
	private final ModifiableCallout<HeadMarkerEvent> digititisHealer = new ModifiableCallout<>("Digititis (Healer)", "Healer Debuff");

	@PlayerHeadmarker(0x0024)
	private final ModifiableCallout<HeadMarkerEvent> digititisDamage = new ModifiableCallout<>("Digititis (Damage)", "Damage Debuff");

	@NpcAbilityUsedCallout(value = 0xF09, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> equalConcentration = new ModifiableCallout<>("Equal Concentration", "Burn Higher HP Hand");

	private final ModifiableCallout<TetherEvent> drainageOnYou = new ModifiableCallout<>("Drainage on You", "Drainage on You");
	private final ModifiableCallout<TetherEvent> drainageTank = new ModifiableCallout<>("Drainage (Tank)", "Get Drainage Tether");
	private final RepeatSuppressor drainageTankSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void drainage(EventContext context, TetherEvent event) {
		if (!event.tetherIdMatches(0x0005)) {
			return;
		}
		if (event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			context.accept(drainageOnYou.getModified(event));
		}
		if (state.playerJobMatches(Job::isTank) && drainageTankSupp.check(event)) {
			context.accept(drainageTank.getModified(event));
		}
	}

	// 線 → 頭上マーカー → 詠唱の順に来るので、詠唱開始時点で相方とマーカーが揃っている
	private final Map<Long, XivCombatant> ferroPartners = new HashMap<>();
	private final Map<Long, Long> ferroMarkers = new HashMap<>();

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		ferroPartners.clear();
		ferroMarkers.clear();
	}

	@HandleEvents
	public void ferrofluidTether(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x0026)) {
			ferroPartners.put(event.getSource().getId(), event.getTarget());
			ferroPartners.put(event.getTarget().getId(), event.getSource());
		}
	}

	@HandleEvents
	public void ferrofluidMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x0030, 0x0031)) {
			ferroMarkers.put(event.getTarget().getId(), event.getMarkerId());
		}
	}

	private final ModifiableCallout<AbilityCastStart> ferrofluidRepel = ModifiableCallout.durationBasedCall("Ferrofluid: Repel", "Close to {player}");
	private final ModifiableCallout<AbilityCastStart> ferrofluidAttract = ModifiableCallout.durationBasedCall("Ferrofluid: Attract", "Away from {player}");
	private final ModifiableCallout<AbilityCastStart> ferrofluidUnknown = ModifiableCallout.durationBasedCall("Ferrofluid: Unknown", "Ferrofluid");

	@HandleEvents
	public void ferrofluid(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0xF01)) {
			return;
		}
		XivPlayerCharacter player = state.getPlayer();
		XivCombatant partner = player == null ? null : ferroPartners.get(player.getId());
		Long myMarker = player == null ? null : ferroMarkers.get(player.getId());
		Long partnerMarker = partner == null ? null : ferroMarkers.get(partner.getId());
		if (partner == null || myMarker == null || partnerMarker == null) {
			context.accept(ferrofluidUnknown.getModified(event));
		}
		else if (myMarker.equals(partnerMarker)) {
			context.accept(ferrofluidRepel.getModified(event, Map.of("player", partner)));
		}
		else {
			context.accept(ferrofluidAttract.getModified(event, Map.of("player", partner)));
		}
	}

	@NpcCastCallout(0xEFE)
	private final ModifiableCallout<AbilityCastStart> cascade = ModifiableCallout.durationBasedCall("Cascade", "Raidwide");

	private final ModifiableCallout<AbilityUsedEvent> throttle = new ModifiableCallout<>("Throttle", "Throttle on {event.target}");

	// エスナ可能なジョブにだけ知らせる
	@HandleEvents
	public void throttle(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0xF1A) && !event.getSource().isPc()
		    && state.playerJobMatches(job -> job.isHealer() || job == Job.BRD)) {
			context.accept(throttle.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> fluidClawOnYou = new ModifiableCallout<>("Fluid Claw on You", "Claw on You");
	private final ModifiableCallout<HeadMarkerEvent> fluidClaw = new ModifiableCallout<>("Fluid Claw on Someone Else", "Claw on {event.target}");

	@HandleEvents
	public void fluidClaw(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x0010)) {
			context.accept((event.getTarget().isThePlayer() ? fluidClawOnYou : fluidClaw).getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> embolus = new ModifiableCallout<>("Embolus", "Embolus: Move Boss");

	@HandleEvents
	public void embolus(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0xF1B) && event.isFirstTarget() && !event.getSource().isPc()
		    && state.playerJobMatches(Job::isTank)) {
			context.accept(embolus.getModified(event));
		}
	}
}
