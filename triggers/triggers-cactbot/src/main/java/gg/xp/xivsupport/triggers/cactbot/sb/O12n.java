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
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivCombatant;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/raid/o12n.ts をもとに移植
@CalloutRepo(name = "Alphascape V40", duty = KnownDuty.AlphascapeV40)
public class O12n extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long GROUND_ZERO_MARKER = 0x8B;

	private XivState state;

	public O12n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AlphascapeV40);
	}

	private @Nullable XivCombatant groundZero;

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		groundZero = null;
	}

	private final ModifiableCallout<AbilityCastStart> solarRayOnYou = ModifiableCallout.durationBasedCall("Solar Ray: On You", "Tank Cleave on You");
	private final ModifiableCallout<AbilityCastStart> solarRayAvoid = ModifiableCallout.durationBasedCall("Solar Ray: Not Targeted", "Avoid Tank Cleave");
	private final RepeatSuppressor solarRaySupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void solarRay(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x330F, 0x3310) && solarRaySupp.check(event)) {
			context.accept((event.getTarget().isThePlayer() ? solarRayOnYou : solarRayAvoid).getModified(event));
		}
	}

	@NpcCastCallout(value = {0x3321, 0x3322}, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> optimizedBladeDance = ModifiableCallout.durationBasedCall("Optimized Blade Dance", "Buster on {event.target}");

	@NpcCastCallout(value = {0x3311, 0x3312}, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> laserShower = ModifiableCallout.durationBasedCall("Laser Shower", "Raidwide");

	@NpcCastCallout(value = {0x331C, 0x331D}, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> cosmoMemory = ModifiableCallout.durationBasedCall("Cosmo Memory", "Big Raidwide");

	private final ModifiableCallout<AbilityUsedEvent> efficientBladework = new ModifiableCallout<>("Efficient Bladework", "Out");

	// サブジェクト・シミュレーションMの後に予兆なしの攻撃が来るので、その着弾から時間で呼ぶ
	@AutoFeed
	private final SequentialTrigger<BaseEvent> efficientBladeworkSq = SqtTemplates.sq(15_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x32F4) && aue.isFirstTarget(),
			(e1, s) -> {
				s.waitMs(8_000);
				s.updateCall(efficientBladework, e1);
			});

	private final ModifiableCallout<BuffApplied> localResonance = new ModifiableCallout<>("Local Resonance", "Move Bosses Apart");
	private final RepeatSuppressor localResonanceSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void localResonance(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x67E) && !event.getTarget().isPc()
				&& state.playerJobMatches(job -> job.isTank() || job == Job.BLU)
				&& localResonanceSupp.check(event)) {
			context.accept(localResonance.getModified(event));
		}
	}

	@PlayerHeadmarker(0x57)
	private final ModifiableCallout<HeadMarkerEvent> optimizedMeteor = new ModifiableCallout<>("Optimized Meteor", "Meteor on You");

	@PlayerHeadmarker(GROUND_ZERO_MARKER)
	private final ModifiableCallout<HeadMarkerEvent> groundZeroOnYou = new ModifiableCallout<>("Ground Zero", "Out");

	private final ModifiableCallout<HeadMarkerEvent> gooInstructions = new ModifiableCallout<>("Ground Zero: Follow-up", "Knockback from F, Away from M");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> groundZeroSq = SqtTemplates.sq(15_000,
			HeadMarkerEvent.class, hme -> hme.markerIdMatches(GROUND_ZERO_MARKER),
			(e1, s) -> {
				groundZero = e1.getTarget();
				s.waitMs(8_000);
				s.updateCall(gooInstructions, e1);
				groundZero = null;
			});

	private final ModifiableCallout<HeadMarkerEvent> stackOnYou = new ModifiableCallout<>("Stack Marker: On You", "Stack on You");
	private final ModifiableCallout<HeadMarkerEvent> stackOnOther = new ModifiableCallout<>("Stack Marker: On Other", "Stack on {event.target}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> stackMarkerSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hme -> hme.markerIdMatches(0x3E),
			(e1, s) -> {
				// グラウンドゼロのマーカーが少し遅れて届くことがあるので待つ
				s.waitMs(300);
				XivCombatant gz = groundZero;
				if (gz != null && gz.isThePlayer()) {
					return;
				}
				s.updateCall(e1.getTarget().isThePlayer() ? stackOnYou : stackOnOther, e1);
			});

	@PlayerHeadmarker(0x60)
	private final ModifiableCallout<HeadMarkerEvent> optimizedFire = new ModifiableCallout<>("Optimized Fire III", "Spread");

	@PlayerStatusCallout(0x67D)
	private final ModifiableCallout<BuffApplied> packetFilterF = new ModifiableCallout<BuffApplied>("Packet Filter F", "Attack Omega-M").autoIcon();

	@PlayerStatusCallout(0x67C)
	private final ModifiableCallout<BuffApplied> packetFilterM = new ModifiableCallout<BuffApplied>("Packet Filter M", "Attack Omega-F").autoIcon();

}
