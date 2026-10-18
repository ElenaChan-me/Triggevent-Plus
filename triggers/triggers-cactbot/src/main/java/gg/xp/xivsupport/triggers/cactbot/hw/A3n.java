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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/raid/a3n.ts をもとに移植
@CalloutRepo(name = "Alexander The Arm Of The Father", duty = KnownDuty.AlexanderTheArmOfTheFather)
public class A3n extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	// 磁力の相手とマーカーの種類を覚えておき、詠唱時に引き寄せか反発かを判定する
	private final Map<Long, XivCombatant> ferroTether = new ConcurrentHashMap<>();
	private final Map<Long, Long> ferroMarker = new ConcurrentHashMap<>();

	public A3n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AlexanderTheArmOfTheFather);
	}

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		ferroTether.clear();
		ferroMarker.clear();
	}

	@NpcCastCallout(0x12FF)
	private final ModifiableCallout<AbilityCastStart> washAway = ModifiableCallout.durationBasedCall("Wash Away", "Middle");

	@NpcCastCallout(0x12F7)
	private final ModifiableCallout<AbilityCastStart> cascade = ModifiableCallout.durationBasedCall("Cascade", "Raidwide");

	@PlayerHeadmarker(0x1A)
	private final ModifiableCallout<HeadMarkerEvent> sluice = new ModifiableCallout<>("Sluice", "Sluice on You");

	private final ModifiableCallout<AbilityUsedEvent> fluidStrike = new ModifiableCallout<>("Fluid Strike (after Wash Away)", "3x Tank Cleave");

	// 連続する腕の攻撃をそのまま拾うとうるさいので、ウォッシュアウェイを起点にする
	@AutoFeed
	private final SequentialTrigger<BaseEvent> fluidStrikeSq = SqtTemplates.sq(10_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x12FF) && !aue.getSource().isPc(),
			(e1, s) -> {
				s.waitMs(5_000);
				s.updateCall(fluidStrike, e1);
			});

	private final ModifiableCallout<TetherEvent> drainageOnYou = new ModifiableCallout<>("Drainage (on You)", "Drainage on You");
	private final ModifiableCallout<TetherEvent> drainageTank = new ModifiableCallout<>("Drainage (Tank)", "Get Drainage Tether");

	private final RepeatSuppressor drainageTankSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void drainage(EventContext context, TetherEvent event) {
		if (!event.tetherIdMatches(0x05)) {
			return;
		}
		if (event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			context.accept(drainageOnYou.getModified(event));
		}
		if (state.playerJobMatches(Job::isTank) && drainageTankSupp.check(event)) {
			context.accept(drainageTank.getModified(event));
		}
	}

	@HandleEvents
	public void ferrofluidTether(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x26)) {
			ferroTether.put(event.getSource().getId(), event.getTarget());
			ferroTether.put(event.getTarget().getId(), event.getSource());
		}
	}

	@HandleEvents
	public void ferrofluidSigns(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x30, 0x31)) {
			ferroMarker.put(event.getTarget().getId(), event.getMarkerId());
		}
	}

	private final ModifiableCallout<AbilityCastStart> ferrofluidRepel = ModifiableCallout.durationBasedCall("Ferrofluid (Repel)", "Repel: Close to {partner}");
	private final ModifiableCallout<AbilityCastStart> ferrofluidAttract = ModifiableCallout.durationBasedCall("Ferrofluid (Attract)", "Attract: Away from {partner}");
	private final ModifiableCallout<AbilityCastStart> ferrofluidUnknown = ModifiableCallout.durationBasedCall("Ferrofluid (Unknown)", "Ferrofluid");

	@HandleEvents
	public void ferrofluidCall(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x1306) || event.getSource().isPc()) {
			return;
		}
		XivCombatant player = state.getPlayer();
		XivCombatant partner = player == null ? null : ferroTether.get(player.getId());
		Long myMarker = player == null ? null : ferroMarker.get(player.getId());
		Long partnerMarker = partner == null ? null : ferroMarker.get(partner.getId());
		if (partner == null || myMarker == null || partnerMarker == null) {
			context.accept(ferrofluidUnknown.getModified(event));
		}
		else if (myMarker.equals(partnerMarker)) {
			context.accept(ferrofluidRepel.getModified(event, Map.of("partner", partner)));
		}
		else {
			context.accept(ferrofluidAttract.getModified(event, Map.of("partner", partner)));
		}
	}

	@HandleEvents
	public void ferrofluidCleanup(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x1306) && !event.getSource().isPc()) {
			ferroTether.clear();
			ferroMarker.clear();
		}
	}

}
