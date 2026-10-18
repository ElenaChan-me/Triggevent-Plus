package gg.xp.xivsupport.triggers.dttrial;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.*;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.MapEffectEvent;
import gg.xp.xivsupport.events.actlines.events.SystemLogMessageEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/trial/queen-eternal.ts をもとに移植
@CalloutRepo(name = "The Interphos (Queen Eternal)", duty = KnownDuty.Interphos)
public class Interphos extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public Interphos(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.Interphos);
	}

	private boolean seenFirstCastellation;
	private boolean playerFloating;
	private final List<XivCombatant> gazeTargets = new ArrayList<>();

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		seenFirstCastellation = false;
		playerFloating = false;
		gazeTargets.clear();
	}

	// 1発目の手の詠唱IDで、2発目の向きまで決まる
	@NpcCastCallout(0x8F1E)
	private final ModifiableCallout<AbilityCastStart> legitimateForceStayRight = ModifiableCallout.durationBasedCall("Legitimate Force (Stay Right)", "Right");

	@NpcCastCallout(0x8F1F)
	private final ModifiableCallout<AbilityCastStart> legitimateForceRightLeft = ModifiableCallout.durationBasedCall("Legitimate Force (Right then Left)", "Right then Left");

	@NpcCastCallout(0x8F20)
	private final ModifiableCallout<AbilityCastStart> legitimateForceStayLeft = ModifiableCallout.durationBasedCall("Legitimate Force (Stay Left)", "Left");

	@NpcCastCallout(0x8F21)
	private final ModifiableCallout<AbilityCastStart> legitimateForceLeftRight = ModifiableCallout.durationBasedCall("Legitimate Force (Left then Right)", "Left then Right");

	@NpcCastCallout({0x8EFE, 0x8EFF, 0x8F00})
	private final ModifiableCallout<AbilityCastStart> virtualShift = ModifiableCallout.durationBasedCall("Virtual Shift", "Raidwide");

	@NpcCastCallout(0x8EFC)
	private final ModifiableCallout<AbilityCastStart> aethertithe = ModifiableCallout.durationBasedCall("Aethertithe", "Continuous Raidwide");

	private final ModifiableCallout<MapEffectEvent> aethertitheLeftCone = new ModifiableCallout<>("Aethertithe: Left Cone", "Right");
	private final ModifiableCallout<MapEffectEvent> aethertitheCenterCone = new ModifiableCallout<>("Aethertithe: Center Cone", "Front Corners");
	private final ModifiableCallout<MapEffectEvent> aethertitheRightCone = new ModifiableCallout<>("Aethertithe: Right Cone", "Left");

	// 扇範囲の詠唱は出ず、MapEffect でしか分からない
	@HandleEvents
	public void aethertitheCones(EventContext context, MapEffectEvent event) {
		long flags = event.getFlags();
		if (flags == 0x04000100L) {
			context.accept(aethertitheLeftCone.getModified(event));
		}
		else if (flags == 0x08000100L) {
			context.accept(aethertitheCenterCone.getModified(event));
		}
		else if (flags == 0x10000100L) {
			context.accept(aethertitheRightCone.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> prosecutionOfWar = new ModifiableCallout<>("Prosecution of War", "Buster on {event.target}");

	@HandleEvents
	public void prosecutionOfWarMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x00DA)) {
			context.accept(prosecutionOfWar.getModified(event));
		}
	}

	@PlayerHeadmarker(0x0001)
	private final ModifiableCallout<HeadMarkerEvent> ruthlessRegalia = new ModifiableCallout<>("Ruthless Regalia: Laser Tether on You", "Laser Tether on You");

	@NpcCastCallout(0x8F01)
	private final ModifiableCallout<AbilityCastStart> downburst = ModifiableCallout.durationBasedCall("Downburst", "Knockback");

	@NpcCastCallout(0x8F03)
	private final ModifiableCallout<AbilityCastStart> powerfulGust = ModifiableCallout.durationBasedCall("Powerful Gust", "Knockback");

	// 重力装置

	private final ModifiableCallout<BuffApplied> gravitationalAnomaly = new ModifiableCallout<BuffApplied>("Gravitational Anomaly: Levitating", "Levitating").statusIcon(0xEE6);

	@HandleEvents
	public void gravitationalAnomalyGain(EventContext context, BuffApplied event) {
		// 初回のキャッスルレーション以外はシステムログの方が早いのでそちらを使う
		if (event.buffIdMatches(0xEE6) && event.getTarget().isThePlayer() && !seenFirstCastellation) {
			playerFloating = true;
			context.accept(gravitationalAnomaly.getModified(event));
		}
	}

	private final ModifiableCallout<SystemLogMessageEvent> gravityIncrease = new ModifiableCallout<>("Gravity Increase: Falling", "Falling");
	private final ModifiableCallout<SystemLogMessageEvent> gravityDecrease = new ModifiableCallout<>("Gravity Decrease: Levitating", "Levitating");

	@HandleEvents
	public void gravityLogMessage(EventContext context, SystemLogMessageEvent event) {
		if (!seenFirstCastellation) {
			return;
		}
		if (event.getId() == 0x2940) {
			playerFloating = false;
			context.accept(gravityIncrease.getModified(event));
		}
		else if (event.getId() == 0x2941) {
			playerFloating = true;
			context.accept(gravityDecrease.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> castellationGround = new ModifiableCallout<>("Castellation: Not Levitating", "In Front of Ground Windows");
	private final ModifiableCallout<AbilityUsedEvent> castellationMiddle = new ModifiableCallout<>("Castellation: Levitating", "In Front of Middle Windows");
	private final RepeatSuppressor castellationSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void castellation(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x8F05) && castellationSupp.check(event)) {
			context.accept((playerFloating ? castellationMiddle : castellationGround).getModified(event));
			seenFirstCastellation = true;
		}
	}

	@NpcCastCallout(0x8F19)
	private final ModifiableCallout<AbilityCastStart> brutalCrown = ModifiableCallout.durationBasedCall("Brutal Crown", "Get in Robot Circle");

	private final ModifiableCallout<HeadMarkerEvent> divideAndConquer = new ModifiableCallout<>("Divide and Conquer", "Spread");

	@HandleEvents
	public void divideAndConquerMarker(EventContext context, HeadMarkerEvent event) {
		// ボス側が対象で、プレイヤーは副対象として入ってくる
		if (event.markerIdMatches(0x0209) && event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			context.accept(divideAndConquer.getModified(event));
		}
	}

	// オーソリティ・ゲイズ

	private final ModifiableCallout<BuffApplied> gazeSoon = ModifiableCallout.<BuffApplied>durationBasedCall("Authority's Gaze: On You", "Gaze on You Soon").statusIcon(0xEE7);
	private final ModifiableCallout<BuffApplied> gazeOnYou = new ModifiableCallout<BuffApplied>("Authority's Gaze: Resolve (On You)", "Gaze, Look Away from {other}").statusIcon(0xEE7);
	private final ModifiableCallout<BuffApplied> gazeOnYouAlone = new ModifiableCallout<BuffApplied>("Authority's Gaze: Resolve (On You, No Partner)", "Gaze").statusIcon(0xEE7);
	private final ModifiableCallout<BuffApplied> gazeOnOthers = new ModifiableCallout<>("Authority's Gaze: Resolve (Not on You)", "Look Away from {targets}");

	@HandleEvents
	public void gazeStore(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0xEE7)) {
			gazeTargets.add(event.getTarget());
			if (event.getTarget().isThePlayer()) {
				context.accept(gazeSoon.getModified(event));
			}
		}
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> gazeResolveSq = SqtTemplates.sq(120_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0xEE7),
			(e1, s) -> {
				long msToWait = e1.getEstimatedRemainingDuration().minusSeconds(5).toMillis();
				if (msToWait > 0) {
					s.waitMs(msToWait);
				}
				List<XivCombatant> targets = List.copyOf(gazeTargets);
				gazeTargets.clear();
				if (targets.stream().anyMatch(XivCombatant::isThePlayer)) {
					XivCombatant other = targets.stream().filter(t -> !t.isThePlayer()).findFirst().orElse(null);
					if (other != null) {
						s.setParam("other", other);
						s.updateCall(gazeOnYou, e1);
					}
					else {
						s.updateCall(gazeOnYouAlone, e1);
					}
				}
				else {
					s.setParam("targets", targets);
					s.updateCall(gazeOnOthers, e1);
				}
			});

	// オーソリティ・ホールド（加速度爆弾）

	private final ModifiableCallout<BuffApplied> accelBombSoon = ModifiableCallout.<BuffApplied>durationBasedCall("Authority's Hold: Acceleration Bomb", "Acceleration Bomb Soon").statusIcon(0x1022);
	private final ModifiableCallout<BuffApplied> accelBombStop = new ModifiableCallout<BuffApplied>("Authority's Hold: Stop", "Stop Everything").statusIcon(0x1022);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> accelBombSq = SqtTemplates.sq(120_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0x1022) && ba.getTarget().isThePlayer(),
			(e1, s) -> {
				s.updateCall(accelBombSoon, e1);
				long msToWait = e1.getEstimatedRemainingDuration().minusSeconds(4).toMillis();
				if (msToWait > 0) {
					s.waitMs(msToWait);
				}
				s.updateCall(accelBombStop, e1);
			});

	@NpcCastCallout(0x9A6B)
	private final ModifiableCallout<AbilityCastStart> absoluteAuthority = ModifiableCallout.durationBasedCall("Absolute Authority", "Raidwide");

	@PlayerHeadmarker(0x0147)
	private final ModifiableCallout<HeadMarkerEvent> absoluteAuthorityFlare = new ModifiableCallout<>("Absolute Authority: Flare on You", "Flare on You");

	@PlayerHeadmarker(0x0037)
	private final ModifiableCallout<HeadMarkerEvent> absoluteAuthorityDorito = new ModifiableCallout<>("Absolute Authority: Dorito Stack", "Dorito Stack");

	@NpcCastCallout(0x9C7A)
	private final ModifiableCallout<AbilityCastStart> dynasticDiadem = ModifiableCallout.durationBasedCall("Dynastic Diadem", "In");

	@NpcCastCallout(0x8F24)
	private final ModifiableCallout<AbilityCastStart> royalBanishment = ModifiableCallout.durationBasedCall("Royal Banishment", "Raidwide x5");

}
