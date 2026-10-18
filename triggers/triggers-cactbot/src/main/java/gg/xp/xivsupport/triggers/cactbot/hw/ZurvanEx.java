package gg.xp.xivsupport.triggers.cactbot.hw;

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
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/trial/zurvan-ex.ts をもとに移植
@CalloutRepo(name = "Containment Bay Z1 T9 Extreme", duty = KnownDuty.ContainmentBayZ1T9Extreme)
public class ZurvanEx extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long infiniteFireId = 0x477;
	private static final long infiniteIceId = 0x478;

	private XivState state;

	public ZurvanEx(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.ContainmentBayZ1T9Extreme);
	}

	private XivCombatant waveTarget;
	private boolean coolFlameOnMe;
	private XivCombatant tetherBuddy;
	private Long infiniteElement;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		waveTarget = null;
		coolFlameOnMe = false;
		tetherBuddy = null;
		infiniteElement = null;
	}

	private final ModifiableCallout<HeadMarkerEvent> waveCannonOnYou = new ModifiableCallout<>("Wave Cannon (On You)", "Wave Cannon on You");
	private final ModifiableCallout<HeadMarkerEvent> waveCannonAvoid = new ModifiableCallout<>("Wave Cannon (Other)", "Away from {event.target}, Wave Cannon");

	@HandleEvents
	public void waveCannonMarker(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(0x0E)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(waveCannonOnYou.getModified(event));
		}
		else {
			context.accept(waveCannonAvoid.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> waveCannonStack = ModifiableCallout.durationBasedCall("Wave Cannon Stack", "Stack on {target}");
	private final ModifiableCallout<AbilityCastStart> waveCannonStackUnknown = ModifiableCallout.durationBasedCall("Wave Cannon Stack (Unknown Target)", "Stack");

	private final ModifiableCallout<AbilityCastStart> demonClawOnYou = ModifiableCallout.durationBasedCall("Demon Claw (On You)", "Knockback from Boss on You");

	@HandleEvents
	public void zurvanCasts(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x1C71)) {
			// ノックバックされた人がそのまま頭割りの対象になる
			if (waveTarget == null) {
				waveTarget = event.getTarget();
			}
			if (event.getTarget().isThePlayer()) {
				context.accept(demonClawOnYou.getModified(event));
			}
		}
		else if (event.abilityIdMatches(0x1C72)) {
			XivCombatant target = waveTarget;
			if (target == null) {
				context.accept(waveCannonStackUnknown.getModified(event));
			}
			// 対象者はスタンしているので何も言わない
			else if (!target.isThePlayer()) {
				context.accept(waveCannonStack.getModified(event, Map.of("target", target)));
			}
		}
	}

	@HandleEvents
	public void cleanup(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x1C72)) {
			waveTarget = null;
		}
		else if (event.abilityIdMatches(0x1C56)) {
			coolFlameOnMe = false;
		}
		else if (event.abilityIdMatches(0x1DC7)) {
			tetherBuddy = null;
			infiniteElement = null;
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> flamingHalberd = new ModifiableCallout<>("Flaming Halberd", "Spread");
	// ソロで遊ぶと複数のマーカーが自分に付くので連続で読まない
	private final RepeatSuppressor flamingHalberdSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void flamingHalberdMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x2C) && event.getTarget().isThePlayer() && flamingHalberdSupp.check(event)) {
			context.accept(flamingHalberd.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> coolFlame = new ModifiableCallout<>("Cool Flame", "Spread, Don't Stack");

	@HandleEvents
	public void coolFlameMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x17) && event.getTarget().isThePlayer()) {
			coolFlameOnMe = true;
			context.accept(coolFlame.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> demonicDiveOnYou = new ModifiableCallout<>("Demonic Dive (On You)", "Stack on You");
	private final ModifiableCallout<HeadMarkerEvent> demonicDive = new ModifiableCallout<>("Demonic Dive", "Stack on {event.target}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> demonicDiveSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(0x3E),
			(e1, s) -> {
				// Cool Flame のマーカーと同時に付くので、判定が揃うのを待つ
				s.waitMs(1_000);
				if (coolFlameOnMe) {
					return;
				}
				if (e1.getTarget().isThePlayer()) {
					s.updateCall(demonicDiveOnYou, e1);
				}
				else {
					s.updateCall(demonicDive, e1);
				}
			});

	@NpcCastCallout(0x1C59)
	private final ModifiableCallout<AbilityCastStart> bitingHalberd = ModifiableCallout.durationBasedCall("Biting Halberd", "Get Behind");

	@NpcCastCallout(0x1C5A)
	private final ModifiableCallout<AbilityCastStart> tailEnd = ModifiableCallout.durationBasedCall("Tail End", "Out");

	@NpcCastCallout(0x1C5B)
	private final ModifiableCallout<AbilityCastStart> ciclicle = ModifiableCallout.durationBasedCall("Ciclicle", "In");

	@NpcAbilityUsedCallout(value = 0x1C58, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> iceAndFire = new ModifiableCallout<>("Ice and Fire", "Stay Outside Hitbox");

	@NpcCastCallout(0x1E36)
	private final ModifiableCallout<AbilityCastStart> meracydianFear = ModifiableCallout.durationBasedCall("Meracydian Fear", "Look Away from {event.source}");

	// Tyrfing 自体は範囲ではないが、続くファイガが前方範囲になる
	@NpcCastCallout(0x1C6D)
	private final ModifiableCallout<AbilityCastStart> tyrfing = ModifiableCallout.durationBasedCall("Tyrfing", "Tank Cleave");

	@NpcCastCallout(value = 0x1C5C, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> southernCross = ModifiableCallout.durationBasedCall("Southern Cross", "Bait Ice Puddles");

	@NpcCastCallout(value = 0x1C5D, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> southernCrossMove = ModifiableCallout.durationBasedCall("Southern Cross (Move)", "Move Away");

	private final ModifiableCallout<TetherEvent> infiniteTether = new ModifiableCallout<>("Infinite Tether", "Tethered with {buddy}");

	@HandleEvents
	public void infiniteTethers(EventContext context, TetherEvent event) {
		if (!event.tetherIdMatches(0x05, 0x08) || !event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			return;
		}
		if (tetherBuddy == null) {
			tetherBuddy = event.getTargetMatching(cbt -> !cbt.isThePlayer());
		}
		if (tetherBuddy != null) {
			context.accept(infiniteTether.getModified(event, Map.of("buddy", tetherBuddy)));
		}
	}

	private final ModifiableCallout<BuffApplied> infiniteFire = new ModifiableCallout<BuffApplied>("Infinite Fire", "Fire on You").statusIcon(infiniteFireId);
	private final ModifiableCallout<BuffApplied> infiniteIce = new ModifiableCallout<BuffApplied>("Infinite Ice", "Ice on You").statusIcon(infiniteIceId);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> infiniteDebuffSq = SqtTemplates.sq(10_000,
			BuffApplied.class, ba -> ba.buffIdMatches(infiniteFireId, infiniteIceId) && ba.getTarget().isThePlayer(),
			(e1, s) -> {
				if (infiniteElement == null) {
					infiniteElement = e1.getBuff().getId();
				}
				// 線の相方の読み上げと重ならないように少し待つ
				s.waitMs(2_000);
				Long element = infiniteElement;
				long elementId = element == null ? e1.getBuff().getId() : element;
				if (elementId == infiniteFireId) {
					s.updateCall(infiniteFire, e1);
				}
				else {
					s.updateCall(infiniteIce, e1);
				}
			});

	private final ModifiableCallout<AbilityCastStart> brokenSealFire = ModifiableCallout.durationBasedCall("Broken Seal (Fire)", "Fire Towers with {buddy}");
	private final ModifiableCallout<AbilityCastStart> brokenSealIce = ModifiableCallout.durationBasedCall("Broken Seal (Ice)", "Ice Towers with {buddy}");
	private final ModifiableCallout<AbilityCastStart> brokenSealUnknown = ModifiableCallout.durationBasedCall("Broken Seal (Unknown)", "Towers with {buddy}");

	@HandleEvents
	public void brokenSeal(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x1DC7)) {
			return;
		}
		ModifiableCallout<AbilityCastStart> call;
		if (infiniteElement == null) {
			call = brokenSealUnknown;
		}
		else if (infiniteElement == infiniteFireId) {
			call = brokenSealFire;
		}
		else {
			call = brokenSealIce;
		}
		Object buddy = tetherBuddy == null ? "?" : tetherBuddy;
		context.accept(call.getModified(event, Map.of("buddy", buddy)));
	}

}
