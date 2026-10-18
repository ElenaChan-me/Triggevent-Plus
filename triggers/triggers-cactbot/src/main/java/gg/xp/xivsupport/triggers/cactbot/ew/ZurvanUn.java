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
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.models.XivCombatant;

import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/trial/zurvan-un.ts をもとに移植
@CalloutRepo(name = "Containment Bay Z1 T9 Unreal", duty = KnownDuty.ContainmentBayZ1T9Unreal)
public class ZurvanUn extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long INFINITE_FIRE = 0x477;
	private static final long INFINITE_ICE = 0x478;
	private static final String UNKNOWN = "Unknown";

	private XivState state;

	public ZurvanUn(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.ContainmentBayZ1T9Unreal);
	}

	// 波動砲の頭割り対象はデモンクローの対象と同じ
	private XivCombatant waveTarget;
	private boolean coolFlameOnPlayer;
	private XivCombatant tetherBuddy;
	private String infiniteElement;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		waveTarget = null;
		coolFlameOnPlayer = false;
		tetherBuddy = null;
		infiniteElement = null;
	}

	private final ModifiableCallout<HeadMarkerEvent> waveCannonOnYou = new ModifiableCallout<>("Wave Cannon (on You)", "Wave Cannon on You");
	private final ModifiableCallout<HeadMarkerEvent> waveCannonAvoid = new ModifiableCallout<>("Wave Cannon (Avoid)", "Away from {event.target}, Wave Cannon");

	@HandleEvents
	public void waveCannonMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0xE)) {
			context.accept((event.getTarget().isThePlayer() ? waveCannonOnYou : waveCannonAvoid).getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> waveCannonStack = ModifiableCallout.durationBasedCall("Wave Cannon (Stack)", "Stack");
	private final ModifiableCallout<AbilityCastStart> waveCannonStackOn = ModifiableCallout.durationBasedCall("Wave Cannon (Stack on Player)", "Stack on {player}");

	@HandleEvents
	public void waveCannonStack(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x857B)) {
			return;
		}
		// 対象はスタンしているので呼ばない
		if (waveTarget != null && waveTarget.isThePlayer()) {
			return;
		}
		if (waveTarget == null) {
			context.accept(waveCannonStack.getModified(event));
		}
		else {
			context.accept(waveCannonStackOn.getModified(event, Map.of("player", waveTarget)));
		}
	}

	private final ModifiableCallout<AbilityCastStart> demonClaw = ModifiableCallout.durationBasedCall("Demon Claw", "Knockback from Boss on You");

	@HandleEvents
	public void demonClaw(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x857A)) {
			return;
		}
		if (waveTarget == null) {
			waveTarget = event.getTarget();
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(demonClaw.getModified(event));
		}
	}

	@PlayerHeadmarker(0x2C)
	private final ModifiableCallout<HeadMarkerEvent> flamingHalberd = new ModifiableCallout<>("Flaming Halberd", "Spread");

	private final ModifiableCallout<HeadMarkerEvent> demonicDiveOnYou = new ModifiableCallout<>("Demonic Dive (on You)", "Stack on You");
	private final ModifiableCallout<HeadMarkerEvent> demonicDiveOn = new ModifiableCallout<>("Demonic Dive", "Stack on {event.target}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> demonicDiveSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hme -> hme.markerIdMatches(0x3E),
			(e1, s) -> {
				// クールフレイムのマーカーが後から付くことがあるので少し待つ
				s.waitMs(1_000);
				if (coolFlameOnPlayer) {
					return;
				}
				s.updateCall(e1.getTarget().isThePlayer() ? demonicDiveOnYou : demonicDiveOn, e1);
			});

	private final ModifiableCallout<HeadMarkerEvent> coolFlame = new ModifiableCallout<>("Cool Flame", "Spread, Don't Stack");

	@HandleEvents
	public void coolFlame(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x17) && event.getTarget().isThePlayer()) {
			coolFlameOnPlayer = true;
			context.accept(coolFlame.getModified(event));
		}
	}

	@HandleEvents
	public void abilityCleanup(EventContext context, AbilityUsedEvent event) {
		if (!event.isFirstTarget()) {
			return;
		}
		if (event.abilityIdMatches(0x855F)) {
			coolFlameOnPlayer = false;
		}
		else if (event.abilityIdMatches(0x857B)) {
			waveTarget = null;
		}
		else if (event.abilityIdMatches(0x857D)) {
			tetherBuddy = null;
			infiniteElement = null;
		}
	}

	@NpcCastCallout(0x8562)
	private final ModifiableCallout<AbilityCastStart> bitingHalberd = ModifiableCallout.durationBasedCall("Biting Halberd", "Get Behind");

	@NpcCastCallout(0x8563)
	private final ModifiableCallout<AbilityCastStart> tailEnd = ModifiableCallout.durationBasedCall("Tail End", "Out");

	@NpcCastCallout(0x8564)
	private final ModifiableCallout<AbilityCastStart> ciclicle = ModifiableCallout.durationBasedCall("Ciclicle", "In");

	@NpcAbilityUsedCallout(value = 0x8561, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> iceAndFire = new ModifiableCallout<>("Ice and Fire", "Stay Outside Hitbox");

	@NpcCastCallout(0x856F)
	private final ModifiableCallout<AbilityCastStart> meracydianFear = ModifiableCallout.durationBasedCall("Meracydian Fear", "Look Away from {event.source}");

	// ティルフィング自体は単体だが、続くファイガが範囲になる
	@NpcCastCallout(0x8576)
	private final ModifiableCallout<AbilityCastStart> tyrfing = ModifiableCallout.durationBasedCall("Tyrfing", "Tank Cleave");

	@NpcCastCallout(value = 0x8565, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> southernCrossBait = ModifiableCallout.durationBasedCall("Southern Cross (Bait)", "Bait Ice Puddles");

	@NpcCastCallout(value = 0x8566, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> southernCrossMove = ModifiableCallout.durationBasedCall("Southern Cross (Move)", "Move Away");

	private final ModifiableCallout<TetherEvent> infiniteTether = new ModifiableCallout<>("Infinite Fire/Ice Tether", "Tethered with {buddy}");

	@HandleEvents
	public void infiniteTether(EventContext context, TetherEvent event) {
		if (!event.tetherIdMatches(0x5, 0x8) || !event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			return;
		}
		if (tetherBuddy == null) {
			tetherBuddy = event.getSource().isThePlayer() ? event.getTarget() : event.getSource();
		}
		context.accept(infiniteTether.getModified(event, Map.of("buddy", tetherBuddy)));
	}

	private final ModifiableCallout<BuffApplied> infiniteDebuff = new ModifiableCallout<BuffApplied>("Infinite Fire/Ice", "{element} on You").autoIcon();

	@AutoFeed
	private final SequentialTrigger<BaseEvent> infiniteDebuffSq = SqtTemplates.sq(10_000,
			BuffApplied.class, ba -> ba.buffIdMatches(INFINITE_FIRE, INFINITE_ICE) && ba.getTarget().isThePlayer(),
			(e1, s) -> {
				if (infiniteElement == null) {
					infiniteElement = e1.buffIdMatches(INFINITE_FIRE) ? "Fire" : "Ice";
				}
				// 線の相方のコールと重ならないようにずらす
				s.waitMs(2_000);
				s.setParam("element", infiniteElement == null ? UNKNOWN : infiniteElement);
				s.updateCall(infiniteDebuff, e1);
			});

	private final ModifiableCallout<AbilityCastStart> brokenSeal = ModifiableCallout.durationBasedCall("Broken Seal", "{element} Towers with {buddy}");

	@HandleEvents
	public void brokenSeal(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x857D)) {
			context.accept(brokenSeal.getModified(event, Map.of(
					"element", infiniteElement == null ? UNKNOWN : infiniteElement,
					"buddy", tetherBuddy == null ? UNKNOWN : tetherBuddy)));
		}
	}

}
