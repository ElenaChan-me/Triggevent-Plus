package gg.xp.xivsupport.triggers.cactbot.sb;

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
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/raid/o9n.ts をもとに移植
@CalloutRepo(name = "Alphascape V10", duty = KnownDuty.AlphascapeV10)
public class O9n extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long stackMarkerId = 0x3E;

	private XivState state;

	public O9n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AlphascapeV10);
	}

	private boolean stackOnPlayer;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		stackOnPlayer = false;
	}

	@NpcCastCallout(0x314F)
	private final ModifiableCallout<AbilityCastStart> chaoticDispersion = ModifiableCallout.durationBasedCall("Chaotic Dispersion", "Buster on {event.target}");

	private final ModifiableCallout<AbilityCastStart> orbsFiend = ModifiableCallout.durationBasedCall("Orbs Fiend", "Orb Tethers");

	@HandleEvents
	public void orbsFiend(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x315C) && state.playerJobMatches(job -> job.isTank() || job == Job.BLU)) {
			context.accept(orbsFiend.getModified(event));
		}
	}

	private final ModifiableCallout<BuffApplied> entropy = new ModifiableCallout<BuffApplied>("Entropy", "Spread").statusIcon(0x640);

	// 火フェーズでは8秒と14秒、中盤では14秒で付くので、残り6秒で呼べばどの場合でも間に合う
	@AutoFeed
	private final SequentialTrigger<BaseEvent> entropySq = SqtTemplates.callWhenDurationIs(
			BuffApplied.class, ba -> ba.buffIdMatches(0x640) && ba.getTarget().isThePlayer(), entropy, Duration.ofSeconds(6));

	// 効果時間に関わらず、ドーナツ頭割りは先に位置取りしておいて問題ない
	@PlayerStatusCallout(0x641)
	private final ModifiableCallout<BuffApplied> dynamicFluid = new ModifiableCallout<BuffApplied>("Dynamic Fluid", "Stack for Donuts").statusIcon(0x641);

	@NpcCastCallout({0x3165, 0x3166, 0x3167, 0x3168})
	private final ModifiableCallout<AbilityCastStart> elementalRaidwide = ModifiableCallout.durationBasedCall("Blaze/Tsunami/Cyclone/Earthquake", "Raidwide");

	@NpcCastCallout(0x3169)
	private final ModifiableCallout<AbilityCastStart> bowelsOfAgony = ModifiableCallout.durationBasedCall("Bowels of Agony", "Big Raidwide");

	// 着弾地点に大きな円範囲が残るので、単なるフレア扱いではなく離れて捨てさせる
	@PlayerHeadmarker(0x57)
	private final ModifiableCallout<HeadMarkerEvent> knockdown = new ModifiableCallout<>("Knockdown", "Drop Flare Away");

	@HandleEvents
	public void stackCollect(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(stackMarkerId) && event.getTarget().isThePlayer()) {
			stackOnPlayer = true;
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> stackOnYou = new ModifiableCallout<>("Stack (On You)", "Stack on You");
	private final ModifiableCallout<HeadMarkerEvent> stackGroups = new ModifiableCallout<>("Stack (Groups)", "Split into Stack Groups");

	// 頭割りマーカーは2つ同時に出るので、揃うのを待ってから判定する
	@AutoFeed
	private final SequentialTrigger<BaseEvent> stackSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(stackMarkerId),
			(e1, s) -> {
				s.waitMs(500);
				s.updateCall(stackOnPlayer ? stackOnYou : stackGroups, e1);
				stackOnPlayer = false;
			});

	@NpcCastCallout({0x3148, 0x316D})
	private final ModifiableCallout<AbilityCastStart> knockback = ModifiableCallout.durationBasedCall("Earthquake/Cyclone Knockback", "Knockback");

}
