package gg.xp.xivsupport.triggers.cactbot.ew;

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
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
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
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/dungeon/stigma_dreamscape.ts をもとに移植
@CalloutRepo(name = "The Stigma Dreamscape", duty = KnownDuty.TheStigmaDreamscape)
public class StigmaDreamscape extends AutoChildEventHandler implements FilteredEventHandler {

	// 電磁波の詠唱は約9.7秒あるので、発動の少し前に呼ぶ
	private static final Duration RELEASE_LEAD_TIME = Duration.ofSeconds(4);

	private XivState state;

	public StigmaDreamscape(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheStigmaDreamscape);
	}

	private boolean lastBoss;

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		lastBoss = false;
	}

	// プロトオメガ

	@NpcCastCallout(0x6320)
	private final ModifiableCallout<AbilityCastStart> sideCannonsLeft = ModifiableCallout.durationBasedCall("Side Cannons (Left)", "Left");

	@NpcCastCallout(0x6321)
	private final ModifiableCallout<AbilityCastStart> sideCannonsRight = ModifiableCallout.durationBasedCall("Side Cannons (Right)", "Right");

	@NpcCastCallout(0x6322)
	private final ModifiableCallout<AbilityCastStart> forwardInterceptors = ModifiableCallout.durationBasedCall("Forward Interceptors", "Get Behind");

	@NpcCastCallout(0x6324)
	private final ModifiableCallout<AbilityCastStart> rearInterceptors = ModifiableCallout.durationBasedCall("Rear Interceptors", "Go Front of Boss");

	@PlayerHeadmarker(0x8B)
	private final ModifiableCallout<HeadMarkerEvent> chemicalMissile = new ModifiableCallout<>("Chemical Missile", "Spread");

	private final ModifiableCallout<HeadMarkerEvent> electricSlideOnYou = new ModifiableCallout<>("Electric Slide (On You)", "Stack + Knockback on You");
	private final ModifiableCallout<HeadMarkerEvent> electricSlide = new ModifiableCallout<>("Electric Slide", "Stack + Knockback on {event.target}");

	@HandleEvents
	public void electricSlide(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x121)) {
			context.accept((event.getTarget().isThePlayer() ? electricSlideOnYou : electricSlide).getModified(event));
		}
	}

	private final ModifiableCallout<TetherEvent> guidedMissile = new ModifiableCallout<>("Guided Missile", "Guided Missile on You");

	@HandleEvents
	public void guidedMissile(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x11) && event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			context.accept(guidedMissile.getModified(event));
		}
	}

	@NpcCastCallout(0x632B)
	private final ModifiableCallout<AbilityCastStart> mustardBomb = ModifiableCallout.durationBasedCall("Mustard Bomb", "Buster on {event.target}");

	// アーチラムダ

	@NpcCastCallout(0x63AB)
	private final ModifiableCallout<AbilityCastStart> assaultCannon = ModifiableCallout.durationBasedCall("Assault Cannon", "Get to Wall at Last Dash");

	private final ModifiableCallout<HeadMarkerEvent> sniperCannon = new ModifiableCallout<>("Sniper Cannon", "#{num} Laser on You");

	@HandleEvents
	public void sniperCannon(EventContext context, HeadMarkerEvent event) {
		if (event.getTarget().isThePlayer() && event.markerIdMatches(0x4F, 0x50, 0x51, 0x52)) {
			long num = event.getMarkerId() - 0x4F + 1;
			context.accept(sniperCannon.getModified(event, Map.of("num", num)));
		}
	}

	@NpcCastCallout(0x63B5)
	private final ModifiableCallout<AbilityCastStart> wheel = ModifiableCallout.durationBasedCall("Wheel", "Buster on {event.target}");

	@NpcCastCallout(0x63B4)
	private final ModifiableCallout<AbilityCastStart> atomicFlame = ModifiableCallout.durationBasedCall("Atomic Flame", "Raidwide");

	// スティグマ・フォー

	private final ModifiableCallout<AbilityUsedEvent> touchdownEast = new ModifiableCallout<>("Touchdown (East)", "East");
	private final ModifiableCallout<AbilityUsedEvent> touchdownWest = new ModifiableCallout<>("Touchdown (West)", "West");

	@HandleEvents
	public void lastBossAndTouchdown(EventContext context, AbilityUsedEvent event) {
		// 最終ボスの外にもドラゴンが湧くので、本体のオートアタックを見るまでは何も言わない
		if (event.abilityIdMatches(0x6435)) {
			lastBoss = true;
		}
		else if (event.abilityIdMatches(0x68F9) && event.isFirstTarget() && lastBoss) {
			// 着地直後にフィールドを横切る火炎ブレスを詠唱するので、出現した側を知らせる
			XivCombatant dragon = event.getSource();
			Position pos = dragon.getPos();
			if (pos == null) {
				pos = state.getLatestCombatantData(dragon).getPos();
			}
			if (pos == null) {
				return;
			}
			// フィールドは中心が(0,0)の50x50の正方形
			context.accept((pos.getX() > 0 ? touchdownEast : touchdownWest).getModified(event));
		}
	}

	@NpcCastCallout(value = 0x642D, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> rush = ModifiableCallout.durationBasedCall("Rush", "Avoid Side Dashes");

	private final ModifiableCallout<AbilityCastStart> releaseCircle = ModifiableCallout.durationBasedCall("Electromagnetic Release (Circle)", "Out");
	private final ModifiableCallout<AbilityCastStart> releaseDonut = ModifiableCallout.durationBasedCall("Electromagnetic Release (Donut)", "In");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> releaseSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x6434, 0x6432),
			(e1, s) -> {
				long waitMs = e1.getEstimatedRemainingDuration().minus(RELEASE_LEAD_TIME).toMillis();
				if (waitMs > 0) {
					s.waitMs(waitMs);
				}
				s.updateCall(e1.abilityIdMatches(0x6434) ? releaseCircle : releaseDonut, e1);
			});

	@NpcCastCallout(0x642A)
	private final ModifiableCallout<AbilityCastStart> protoWaveCannonsLeft = ModifiableCallout.durationBasedCall("Proto-wave Cannons (Left)", "Left");

	@NpcCastCallout(0x642B)
	private final ModifiableCallout<AbilityCastStart> protoWaveCannonsRight = ModifiableCallout.durationBasedCall("Proto-wave Cannons (Right)", "Right");

	@PlayerStatusCallout(0x7A6)
	private final ModifiableCallout<BuffApplied> forwardMarch = new ModifiableCallout<BuffApplied>("Forward March", "Mindhack: Forward").autoIcon();

	@PlayerStatusCallout(0x7A7)
	private final ModifiableCallout<BuffApplied> aboutFace = new ModifiableCallout<BuffApplied>("About Face", "Mindhack: Back").autoIcon();

	@PlayerStatusCallout(0x7A8)
	private final ModifiableCallout<BuffApplied> leftFace = new ModifiableCallout<BuffApplied>("Left Face", "Mindhack: Left").autoIcon();

	@PlayerStatusCallout(0x7A9)
	private final ModifiableCallout<BuffApplied> rightFace = new ModifiableCallout<BuffApplied>("Right Face", "Mindhack: Right").autoIcon();

	@NpcCastCallout(0x6436)
	private final ModifiableCallout<AbilityCastStart> atomicRay = ModifiableCallout.durationBasedCall("Atomic Ray", "Raidwide with Bleed");

}
