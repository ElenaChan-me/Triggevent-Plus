package gg.xp.xivsupport.triggers.cactbot.shb;

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
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/raid/e3s.ts をもとに移植
@CalloutRepo(name = "Edens Gate Inundation Savage", duty = KnownDuty.EdensGateInundationSavage)
public class E3s extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long VENT_TETHER = 0x5A;
	private static final int VENT_TETHER_COUNT = 2;
	// これ以下ならすぐ来るノックバック、その次の区切りまでが1回目の遅いノックバック
	private static final Duration SUNDERING_SHORT_MAX = Duration.ofSeconds(8);
	private static final Duration SUNDERING_FIRST_MAX = Duration.ofSeconds(21);
	// 1回目のタイダルスタックは25秒、2回目は13秒でタンクは参加しない
	private static final Duration SMOTHERING_FIRST_MIN = Duration.ofSeconds(15);
	private static final Duration SMOTHERING_CALL_BEFORE = Duration.ofSeconds(3);

	private XivState state;

	public E3s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.EdensGateInundationSavage);
	}

	private boolean refreshed;
	private final List<XivCombatant> vents = new ArrayList<>();

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		refreshed = false;
		vents.clear();
	}

	@NpcCastCallout(0x3FDC)
	private final ModifiableCallout<AbilityCastStart> tidalRoar = ModifiableCallout.durationBasedCall("Tidal Roar", "Raidwide");

	@NpcCastCallout(0x3FDE)
	private final ModifiableCallout<AbilityCastStart> tidalRage = ModifiableCallout.durationBasedCall("Tidal Rage", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> tidalWaveLook = new ModifiableCallout<>("Tidal Wave", "Look for Wave");
	private final ModifiableCallout<AbilityCastStart> tidalWaveKnockback = new ModifiableCallout<>("Tidal Wave: Knockback", "Knockback");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> tidalWaveSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x3FF1),
			(e1, s) -> {
				s.waitMs(3_000);
				s.updateCall(tidalWaveLook, e1);
				// 詠唱3秒と待機10秒なので、アームズレングスが間に合うタイミングで知らせる
				s.waitMs(5_000);
				s.updateCall(tidalWaveKnockback, e1);
			});

	private final ModifiableCallout<HeadMarkerEvent> ripCurrentOnYou = new ModifiableCallout<>("Rip Current: On You", "Buster on You");
	private final ModifiableCallout<HeadMarkerEvent> ripCurrentTankSwap = new ModifiableCallout<>("Rip Current: Tank Swap", "Tank Swap");
	private final ModifiableCallout<HeadMarkerEvent> ripCurrentHealer = new ModifiableCallout<>("Rip Current: Healer", "Tankbusters");
	private final RepeatSuppressor ripCurrentSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	@HandleEvents
	public void ripCurrent(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(0x17) || !ripCurrentSupp.check(event)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(ripCurrentOnYou.getModified(event));
		}
		else if (state.playerJobMatches(Job::isTank)) {
			context.accept(ripCurrentTankSwap.getModified(event));
		}
		else if (state.playerJobMatches(Job::isHealer)) {
			context.accept(ripCurrentHealer.getModified(event));
		}
	}

	@NpcCastCallout(0x3FEF)
	private final ModifiableCallout<AbilityCastStart> underseaQuakeOutside = ModifiableCallout.durationBasedCall("Undersea Quake (Outside)", "Get Middle");

	@NpcCastCallout(0x3FEE)
	private final ModifiableCallout<AbilityCastStart> underseaQuakeInside = ModifiableCallout.durationBasedCall("Undersea Quake (Inside)", "Sides");

	@PlayerHeadmarker(0x57)
	private final ModifiableCallout<HeadMarkerEvent> flare = new ModifiableCallout<>("Flare", "Flare on You");

	private final ModifiableCallout<AbilityCastStart> drenchingPulse = ModifiableCallout.durationBasedCall("Drenching Pulse", "Stack, Bait Puddles");
	private final ModifiableCallout<AbilityCastStart> drenchingPulsePuddles = new ModifiableCallout<>("Drenching Pulse: Puddles", "Drop Puddles Outside");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> drenchingPulseSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x3FE2),
			(e1, s) -> {
				s.updateCall(drenchingPulse, e1);
				s.waitMs(2_900);
				s.updateCall(drenchingPulsePuddles, e1);
			});

	private final ModifiableCallout<AbilityCastStart> roilingPulse = ModifiableCallout.durationBasedCall("Roiling Pulse", "Stack, Bait Puddles");
	private final ModifiableCallout<AbilityCastStart> roilingPulseTank = new ModifiableCallout<>("Roiling Pulse: Tank", "Flare to Outside Corner");
	private final ModifiableCallout<AbilityCastStart> roilingPulseOther = new ModifiableCallout<>("Roiling Pulse: Non-Tank", "Stack Outside, Avoid Flares");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> roilingPulseSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x3FE4),
			(e1, s) -> {
				s.updateCall(roilingPulse, e1);
				s.waitMs(2_900);
				s.updateCall(state.playerJobMatches(Job::isTank) ? roilingPulseTank : roilingPulseOther, e1);
			});

	@NpcCastCallout(0x3FFE)
	private final ModifiableCallout<AbilityCastStart> stormyHorizon = ModifiableCallout.durationBasedCall("Stormy Horizon", "Panto Puddles x5");

	private final ModifiableCallout<TetherEvent> ventOnYou = new ModifiableCallout<>("Hydrothermal Vent: On You", "Puddle Tether on You");
	private final ModifiableCallout<TetherEvent> ventPop = new ModifiableCallout<>("Hydrothermal Vent: Not Tethered", "Pop Alternating Bubbles");

	@HandleEvents
	public void hydrothermalVent(EventContext context, TetherEvent event) {
		if (!event.tetherIdMatches(VENT_TETHER)) {
			return;
		}
		// 線はプレイヤーとボスの間なので、どちら側に入っているかは問わない
		XivCombatant player = event.getTargetMatching(XivCombatant::isPc);
		if (player == null) {
			return;
		}
		vents.add(player);
		if (player.isThePlayer()) {
			context.accept(ventOnYou.getModified(event));
		}
		if (vents.size() == VENT_TETHER_COUNT
				&& vents.stream().noneMatch(XivCombatant::isThePlayer)
				&& !state.playerJobMatches(Job::isTank)) {
			context.accept(ventPop.getModified(event));
		}
	}

	private final ModifiableCallout<BuffApplied> surgingWaters = new ModifiableCallout<BuffApplied>("Surging Waters", "Initial Knockback on You").autoIcon();

	@HandleEvents
	public void surgingWaters(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x73A) && event.getTarget().isThePlayer()) {
			context.accept(surgingWaters.getModified(event));
		}
	}

	private final ModifiableCallout<BuffApplied> sunderingShort = new ModifiableCallout<BuffApplied>("Sundering Waters: Short", "Knockback on You").autoIcon();
	private final ModifiableCallout<BuffApplied> sunderingFirst = new ModifiableCallout<BuffApplied>("Sundering Waters: Late First", "Late First Knockback").autoIcon();
	private final ModifiableCallout<BuffApplied> sunderingSecond = new ModifiableCallout<BuffApplied>("Sundering Waters: Late Second", "Late Second Knockback").autoIcon();

	@HandleEvents
	public void sunderingWaters(EventContext context, BuffApplied event) {
		if (!event.buffIdMatches(0x73E) || !event.getTarget().isThePlayer()) {
			return;
		}
		Duration duration = event.getInitialDuration();
		ModifiableCallout<BuffApplied> call;
		if (duration.compareTo(SUNDERING_SHORT_MAX) <= 0) {
			call = sunderingShort;
		}
		else if (duration.compareTo(SUNDERING_FIRST_MAX) <= 0) {
			call = sunderingFirst;
		}
		else {
			call = sunderingSecond;
		}
		context.accept(call.getModified(event));
	}

	private final ModifiableCallout<BuffApplied> scouringDefamation = new ModifiableCallout<BuffApplied>("Scouring Waters: Defamation", "Defamation").autoIcon();
	private final ModifiableCallout<BuffApplied> scouringAvoidKnockback = new ModifiableCallout<>("Scouring Waters: Avoid Knockback", "Avoid Knockback, Move to Back");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> scouringOnYouSq = SqtTemplates.sq(30_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0x765) && ba.getTarget().isThePlayer(),
			(e1, s) -> {
				s.updateCall(scouringDefamation, e1);
				s.waitMs(22_000);
				s.updateCall(scouringAvoidKnockback, e1);
			});

	private final ModifiableCallout<BuffApplied> scouringOther = new ModifiableCallout<>("Scouring Waters: Not on You", "Move In, Avoid Defamation");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> scouringOtherSq = SqtTemplates.sq(30_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0x765) && !ba.getTarget().isThePlayer(),
			(e1, s) -> {
				s.waitMs(25_000);
				s.updateCall(scouringOther, e1);
			});

	private final ModifiableCallout<BuffApplied> smotheringWaters = new ModifiableCallout<>("Smothering Waters", "Stack");
	private final RepeatSuppressor smotheringSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> smotheringWatersSq = SqtTemplates.sq(30_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0x73D)
					&& (ba.getInitialDuration().compareTo(SMOTHERING_FIRST_MIN) > 0 || !state.playerJobMatches(Job::isTank))
					&& smotheringSupp.check(ba),
			(e1, s) -> {
				Duration wait = e1.getInitialDuration().minus(SMOTHERING_CALL_BEFORE);
				if (!wait.isNegative()) {
					s.waitDuration(wait);
				}
				s.updateCall(smotheringWaters, e1);
			});

	private final ModifiableCallout<BuffApplied> sweepingWatersGain = new ModifiableCallout<BuffApplied>("Sweeping Waters", "Tank Cone").autoIcon();
	private final ModifiableCallout<BuffApplied> sweepingWaters = new ModifiableCallout<>("Sweeping Waters: Reminder", "Tank Cone");
	private final RepeatSuppressor sweepingSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void sweepingWatersGain(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x73F) && event.getTarget().isThePlayer()) {
			context.accept(sweepingWatersGain.getModified(event));
		}
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> sweepingWatersSq = SqtTemplates.sq(20_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0x73F)
					&& (ba.getTarget().isThePlayer() || state.playerJobMatches(Job::isTank))
					&& sweepingSupp.check(ba),
			(e1, s) -> {
				s.waitMs(13_000);
				s.updateCall(sweepingWaters, e1);
			});

	@HandleEvents
	public void refreshedCast(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x400F)) {
			refreshed = true;
		}
	}

	@NpcCastCallout(0x3FEB)
	private final ModifiableCallout<AbilityCastStart> temporaryCurrentFrontLeft = ModifiableCallout.durationBasedCall("Temporary Current (Front Left)", "Front Left/Back Right");

	@NpcCastCallout(0x3FEA)
	private final ModifiableCallout<AbilityCastStart> temporaryCurrentFrontRight = ModifiableCallout.durationBasedCall("Temporary Current (Front Right)", "Front Right/Back Left");

	private final ModifiableCallout<AbilityCastStart> temporaryCurrentFrontLeft2 = new ModifiableCallout<>("Temporary Current 2 (Front Left)", "Front Left/Back Right");
	private final ModifiableCallout<AbilityCastStart> temporaryCurrentFrontRight2 = new ModifiableCallout<>("Temporary Current 2 (Front Right)", "Front Right/Back Left");

	// リフレッシュ後の2回目は詠唱が1秒しかないので、1回目の詠唱から逆側を予告する
	@AutoFeed
	private final SequentialTrigger<BaseEvent> temporaryCurrent2Sq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x3FEA, 0x3FEB) && refreshed,
			(e1, s) -> {
				s.waitMs(6_200);
				s.updateCall(e1.abilityIdMatches(0x3FEA) ? temporaryCurrentFrontLeft2 : temporaryCurrentFrontRight2, e1);
			});

}
