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
import gg.xp.xivsupport.events.actlines.events.BuffRemoved;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/raid/e2s.ts をもとに移植
@CalloutRepo(name = "Edens Gate Descent Savage", duty = KnownDuty.EdensGateDescentSavage)
public class E2s extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long SPELL_IN_WAITING = 0x710;
	private static final long STACK_MARKER = 0x3E;
	private static final long FIRE_MARKER = 0x4C;
	private static final long EYE_MARKER = 0xB3;
	private static final long FLARE_MARKER = 0x57;
	private static final long WIND_MARKER = 0x1E;
	private static final long COUNTDOWN_MARKER = 0xB8;

	private enum Spell {
		STACK,
		FIRE,
		EYE,
		FLARE,
		WIND
	}

	private XivState state;

	public E2s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.EdensGateDescentSavage);
	}

	private boolean waiting;
	private boolean hellWind;
	private final Map<XivCombatant, Spell> spells = new HashMap<>();

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		waiting = false;
		hellWind = false;
		spells.clear();
	}

	@HandleEvents
	public void spellInWaitingGain(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(SPELL_IN_WAITING) && !event.getTarget().isPc()) {
			waiting = true;
			// 前回の遅延魔法の記録が残っていると判定を誤るので、新しい周回の頭で消す
			spells.clear();
		}
	}

	@HandleEvents
	public void spellInWaitingLose(EventContext context, BuffRemoved event) {
		if (event.buffIdMatches(SPELL_IN_WAITING) && !event.getTarget().isPc()) {
			waiting = false;
		}
	}

	@NpcCastCallout(0x3E6F)
	private final ModifiableCallout<AbilityCastStart> entropy = ModifiableCallout.durationBasedCall("Entropy", "Raidwide");

	@NpcCastCallout(0x3E71)
	private final ModifiableCallout<AbilityCastStart> quietus = ModifiableCallout.durationBasedCall("Quietus", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> shadowflameOnYou = ModifiableCallout.durationBasedCall("Shadowflame: On You", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> shadowflame = ModifiableCallout.durationBasedCall("Shadowflame", "Tankbusters");
	private final RepeatSuppressor shadowflameSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void shadowflame(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x3E61, 0x3E62) && event.getTarget().isThePlayer()) {
			context.accept(shadowflameOnYou.getModified(event));
		}
		else if (event.abilityIdMatches(0x3E61) && shadowflameSupp.check(event)) {
			context.accept(shadowflame.getModified(event));
		}
	}

	@NpcCastCallout(0x3E63)
	private final ModifiableCallout<AbilityCastStart> doomvoidCleaver = ModifiableCallout.durationBasedCall("Doomvoid Cleaver", "Protean");

	@NpcCastCallout(0x3E4F)
	private final ModifiableCallout<AbilityCastStart> doomvoidGuillotine = ModifiableCallout.durationBasedCall("Doomvoid Guillotine", "Sides");

	@NpcCastCallout(0x3E50)
	private final ModifiableCallout<AbilityCastStart> doomvoidSlicer = ModifiableCallout.durationBasedCall("Doomvoid Slicer", "Get Under");

	@NpcCastCallout(0x3E59)
	private final ModifiableCallout<AbilityCastStart> emptyHate = ModifiableCallout.durationBasedCall("Empty Hate", "Knockback");

	@NpcCastCallout(0x3E6B)
	private final ModifiableCallout<AbilityCastStart> emptyRage = ModifiableCallout.durationBasedCall("Empty Rage", "Away from Hand");

	@NpcCastCallout(0x4659)
	private final ModifiableCallout<AbilityCastStart> cycleOfRetribution = ModifiableCallout.durationBasedCall("Cycle of Retribution", "In, Protean, Sides");

	@NpcCastCallout(0x40B9)
	private final ModifiableCallout<AbilityCastStart> cycleOfChaos = ModifiableCallout.durationBasedCall("Cycle of Chaos", "Sides, In, Protean");

	// 遅延なしの魔法

	private final ModifiableCallout<HeadMarkerEvent> unholyDarkness = new ModifiableCallout<>("Unholy Darkness", "Stack on {event.target}");
	private final ModifiableCallout<HeadMarkerEvent> darkFire = new ModifiableCallout<>("Dark Fire", "Spread");
	private final ModifiableCallout<HeadMarkerEvent> shadoweyeOnYou = new ModifiableCallout<>("Shadoweye: On You", "Eye on You");
	private final ModifiableCallout<HeadMarkerEvent> shadoweyeOther = new ModifiableCallout<>("Shadoweye: On Other", "Look Away from {event.target}");
	private final ModifiableCallout<HeadMarkerEvent> flare = new ModifiableCallout<>("Flare", "Flare");
	private final ModifiableCallout<HeadMarkerEvent> hellWindCall = new ModifiableCallout<>("Hell Wind", "Hell Wind: Get Out");

	// 遅延魔法（スペル・イン・ウェイティング中）

	private final ModifiableCallout<HeadMarkerEvent> delayedStack = new ModifiableCallout<>("Delayed Unholy Darkness", "Delayed Stack");
	private final ModifiableCallout<HeadMarkerEvent> delayedFire = new ModifiableCallout<>("Delayed Dark Fire", "Delayed Fire");
	private final ModifiableCallout<HeadMarkerEvent> delayedEye = new ModifiableCallout<>("Delayed Shadoweye", "Delayed Shadoweye");
	private final ModifiableCallout<HeadMarkerEvent> delayedFlare = new ModifiableCallout<>("Delayed Flare", "Delayed Flare");
	private final ModifiableCallout<HeadMarkerEvent> delayedWind = new ModifiableCallout<>("Delayed Hell Wind", "Delayed Hell Wind");

	@HandleEvents
	public void spellMarkers(EventContext context, HeadMarkerEvent event) {
		Spell spell;
		if (event.markerIdMatches(STACK_MARKER)) {
			spell = Spell.STACK;
		}
		else if (event.markerIdMatches(FIRE_MARKER)) {
			spell = Spell.FIRE;
		}
		else if (event.markerIdMatches(EYE_MARKER)) {
			spell = Spell.EYE;
		}
		else if (event.markerIdMatches(FLARE_MARKER)) {
			spell = Spell.FLARE;
		}
		else if (event.markerIdMatches(WIND_MARKER)) {
			spell = Spell.WIND;
		}
		else {
			return;
		}
		boolean onYou = event.getTarget().isThePlayer();
		if (waiting) {
			spells.put(event.getTarget(), spell);
			if (onYou) {
				ModifiableCallout<HeadMarkerEvent> call = switch (spell) {
					case STACK -> delayedStack;
					case FIRE -> delayedFire;
					case EYE -> delayedEye;
					case FLARE -> delayedFlare;
					case WIND -> delayedWind;
				};
				context.accept(call.getModified(event));
			}
			return;
		}
		switch (spell) {
			case STACK -> context.accept(unholyDarkness.getModified(event));
			case FIRE -> {
				if (onYou) {
					context.accept(darkFire.getModified(event));
				}
			}
			case EYE -> context.accept((onYou ? shadoweyeOnYou : shadoweyeOther).getModified(event));
			case FLARE -> {
				if (onYou) {
					context.accept(flare.getModified(event));
				}
			}
			case WIND -> {
				if (onYou) {
					context.accept(hellWindCall.getModified(event));
				}
			}
		}
	}

	// 遅延なしのヘルウィンドは頭割りと同時に来るので、しばらく頭割りのコールを抑える
	@AutoFeed
	private final SequentialTrigger<BaseEvent> hellWindTracker = SqtTemplates.sq(20_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(WIND_MARKER) && !waiting && hm.getTarget().isThePlayer(),
			(e1, s) -> {
				hellWind = true;
				s.waitMs(15_000);
				hellWind = false;
			});

	// カウントダウンマーカーで遅延魔法が発動する

	private final ModifiableCallout<HeadMarkerEvent> countdownStack = new ModifiableCallout<>("Countdown: Unholy Darkness", "Stack on {event.target}");
	private final ModifiableCallout<HeadMarkerEvent> countdownFire = new ModifiableCallout<>("Countdown: Dark Fire", "Spread");
	private final ModifiableCallout<HeadMarkerEvent> countdownFlare = new ModifiableCallout<>("Countdown: Flare", "Flare");
	private final ModifiableCallout<HeadMarkerEvent> countdownFlareHealer = new ModifiableCallout<>("Countdown: Flare (Healer)", "Flare AoEs");
	private final ModifiableCallout<HeadMarkerEvent> countdownWind = new ModifiableCallout<>("Countdown: Hell Wind", "Hell Wind: Wait for Heals");
	private final ModifiableCallout<HeadMarkerEvent> countdownWindHealer = new ModifiableCallout<>("Countdown: Hell Wind (Healer)", "Heal Hell Wind Targets");
	private final RepeatSuppressor flareHealerSupp = new RepeatSuppressor(Duration.ofSeconds(10));
	private final RepeatSuppressor windHealerSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	@HandleEvents
	public void countdownMarker(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(COUNTDOWN_MARKER)) {
			return;
		}
		Spell spell = spells.get(event.getTarget());
		if (spell == null) {
			return;
		}
		boolean onYou = event.getTarget().isThePlayer();
		boolean healer = state.playerJobMatches(Job::isHealer);
		Spell mySpell = spells.get(state.getPlayer());
		switch (spell) {
			case STACK -> {
				if (!hellWind) {
					context.accept(countdownStack.getModified(event));
				}
			}
			case FIRE -> {
				if (onYou) {
					context.accept(countdownFire.getModified(event));
				}
			}
			case FLARE -> {
				if (onYou) {
					context.accept(countdownFlare.getModified(event));
				}
				else if (healer && mySpell != Spell.FLARE && flareHealerSupp.check(event)) {
					context.accept(countdownFlareHealer.getModified(event));
				}
			}
			case WIND -> {
				if (healer) {
					if (windHealerSupp.check(event)) {
						context.accept(countdownWindHealer.getModified(event));
					}
				}
				else if (onYou) {
					context.accept(countdownWind.getModified(event));
				}
			}
			case EYE -> {
				// 視線は少し遅らせてコールするので別のトリガーで扱う
			}
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> countdownEyeOnYou = new ModifiableCallout<>("Countdown: Shadoweye on You", "Eye on You");
	private final ModifiableCallout<HeadMarkerEvent> countdownEyeOther = new ModifiableCallout<>("Countdown: Shadoweye on Other", "Look Away from {event.target}");
	private final RepeatSuppressor eyeOnYouSupp = new RepeatSuppressor(Duration.ofSeconds(10));
	private final RepeatSuppressor eyeOtherSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> countdownEyeOnYouSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(COUNTDOWN_MARKER)
					&& hm.getTarget().isThePlayer()
					&& spells.get(hm.getTarget()) == Spell.EYE
					&& eyeOnYouSupp.check(hm),
			(e1, s) -> {
				s.waitMs(2_000);
				s.updateCall(countdownEyeOnYou, e1);
			});

	@AutoFeed
	private final SequentialTrigger<BaseEvent> countdownEyeOtherSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(COUNTDOWN_MARKER)
					&& spells.get(hm.getTarget()) == Spell.EYE
					&& spells.get(state.getPlayer()) != Spell.EYE
					&& eyeOtherSupp.check(hm),
			(e1, s) -> {
				// 視線対象は固まっている前提なので、最初の1人だけコールする
				s.waitMs(2_000);
				s.updateCall(countdownEyeOther, e1);
			});

}
