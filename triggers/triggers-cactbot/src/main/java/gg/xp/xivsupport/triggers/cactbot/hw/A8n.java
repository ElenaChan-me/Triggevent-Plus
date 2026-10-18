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
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/raid/a8n.ts をもとに移植
@CalloutRepo(name = "Alexander The Burden Of The Son", duty = KnownDuty.AlexanderTheBurdenOfTheSon)
public class A8n extends AutoChildEventHandler implements FilteredEventHandler {

	private static final int arithmeticksCallMs = 10_000;

	private XivState state;

	public A8n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AlexanderTheBurdenOfTheSon);
	}

	private boolean bruteTankIsMe;
	private boolean bruteTankOut;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		bruteTankIsMe = false;
		bruteTankOut = false;
	}

	@NpcCastCallout(0x1732)
	private final ModifiableCallout<AbilityCastStart> megabeamOnslaughter = ModifiableCallout.durationBasedCall("Megabeam (Onslaughter)", "Megabeam");

	@NpcCastCallout(0x174F)
	private final ModifiableCallout<AbilityCastStart> megabeamBruteJustice = ModifiableCallout.durationBasedCall("Megabeam (Brute Justice)", "Megabeam");

	private final ModifiableCallout<AbilityUsedEvent> execution = new ModifiableCallout<>("Execution", "Kill Regulators");

	@HandleEvents
	public void executionUsed(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x1632) && event.isFirstTarget()
		    && state.playerJobMatches(job -> job.isDps() || job == Job.BLU)) {
			context.accept(execution.getModified(event));
		}
	}

	@NpcCastCallout(0x1730)
	private final ModifiableCallout<AbilityCastStart> perpetualRay = ModifiableCallout.durationBasedCall("Perpetual Ray", "Buster on {event.target}");

	// 高低どちらも頭上マーカーは 0025 で共通なので、デバフで判別する
	private final ModifiableCallout<BuffApplied> lowArithmeticks = new ModifiableCallout<BuffApplied>("Low Arithmeticks", "Get High", arithmeticksCallMs).statusIcon(0x3FD);
	private final ModifiableCallout<BuffApplied> highArithmeticks = new ModifiableCallout<BuffApplied>("High Arithmeticks", "Get Down", arithmeticksCallMs).statusIcon(0x3FE);
	private final RepeatSuppressor lowArithmeticksSupp = new RepeatSuppressor(Duration.ofSeconds(10));
	private final RepeatSuppressor highArithmeticksSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	@HandleEvents
	public void arithmeticks(EventContext context, BuffApplied event) {
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		if (event.buffIdMatches(0x3FD) && lowArithmeticksSupp.check(event)) {
			context.accept(lowArithmeticks.getModified(event));
		}
		else if (event.buffIdMatches(0x3FE) && highArithmeticksSupp.check(event)) {
			context.accept(highArithmeticks.getModified(event));
		}
	}

	@NpcCastCallout(0x1747)
	private final ModifiableCallout<AbilityCastStart> superCyclone = ModifiableCallout.durationBasedCall("Super Cyclone", "Knockback");

	private final ModifiableCallout<HeadMarkerEvent> enumeration = new ModifiableCallout<>("Enumeration", "{event.target}: {count}");

	@HandleEvents
	public void enumerationMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x40, 0x41, 0x42)) {
			// 0x40 が2人、0x41 が3人、0x42 が4人
			long count = 2 + event.getMarkerId() - 0x40;
			context.accept(enumeration.getModified(event, Map.of("count", count)));
		}
	}

	@NpcCastCallout(0x174E)
	private final ModifiableCallout<AbilityCastStart> doubleRocketPunch = ModifiableCallout.durationBasedCall("Double Rocket Punch", "Buster on {event.target}");

	// 危険な Flarethrower は J-Kick の後かつ Super Jump の前に来るものだけなので、
	// 直前の技とブルートジャスティスのメインタンクを覚えておく
	@HandleEvents
	public void bruteTracker(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x174C)) {
			bruteTankIsMe = event.getTarget().isThePlayer();
		}
		else if (event.abilityIdMatches(0x1750, 0x1756)) {
			bruteTankOut = event.abilityIdMatches(0x1756);
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> longNeedle = new ModifiableCallout<>("Long Needle", "Stack on {event.target}");
	private final ModifiableCallout<HeadMarkerEvent> longNeedleActiveTank = new ModifiableCallout<>("Long Needle (Active Tank)", "Don't Stack, Tank Cleave");

	@HandleEvents
	public void longNeedleMarker(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(0x3E)) {
			return;
		}
		if (bruteTankIsMe && bruteTankOut) {
			context.accept(longNeedleActiveTank.getModified(event));
		}
		else {
			context.accept(longNeedle.getModified(event));
		}
	}

	@NpcCastCallout(0x1751)
	private final ModifiableCallout<AbilityCastStart> apocalypticRay = ModifiableCallout.durationBasedCall("Apocalyptic Ray", "Away from Front");

	private final ModifiableCallout<AbilityCastStart> superJumpOnYou = ModifiableCallout.durationBasedCall("Super Jump (On You)", "Super Jump on You");
	private final ModifiableCallout<AbilityCastStart> superJumpOther = ModifiableCallout.durationBasedCall("Super Jump (Other)", "Super Jump on {event.target}");

	@HandleEvents
	public void superJumpCast(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x1750)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(superJumpOnYou.getModified(event));
		}
		else {
			context.accept(superJumpOther.getModified(event));
		}
	}

	@PlayerHeadmarker(0x08)
	private final ModifiableCallout<HeadMarkerEvent> mirageMarker = new ModifiableCallout<>("Mirage Marker", "Mirage on You");

	@PlayerHeadmarker(0x43)
	private final ModifiableCallout<HeadMarkerEvent> iceMissile = new ModifiableCallout<>("Ice Missile", "Ice Missile on You");

	@NpcCastCallout(value = 0x1749, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> mirageSupercharge = ModifiableCallout.durationBasedCall("Supercharge (Mirage)", "Avoid Mirage Dashes");

}
