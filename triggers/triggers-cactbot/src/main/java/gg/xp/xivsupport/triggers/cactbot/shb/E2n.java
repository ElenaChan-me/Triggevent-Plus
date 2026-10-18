package gg.xp.xivsupport.triggers.cactbot.shb;

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

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/raid/e2n.ts をもとに移植
@CalloutRepo(name = "Edens Gate Descent", duty = KnownDuty.EdensGateDescent)
public class E2n extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long DARK_FIRE_MARKER = 0x4C;
	private static final long STACK_MARKER = 0x3E;
	private static final long EYE_MARKER = 0xB3;
	private static final long DELAYED_FIRE_MARKER = 0xB5;
	private static final long DELAYED_STACK_MARKER = 0xB4;
	private static final long DELAYED_EYE_MARKER = 0xB7;
	private static final long COUNTDOWN_MARKER = 0xB8;
	// 3回目のダークファイガは頭割りと同時に来る
	private static final int FIRE_WITH_STACK = 3;
	private static final Duration SPELL_CLEANUP = Duration.ofSeconds(10);

	private enum Spell {
		FIRE,
		STACK,
		EYE
	}

	private XivState state;

	public E2n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.EdensGateDescent);
	}

	private int fireCount;
	private HeadMarkerEvent lastCountdown;
	private final Map<XivCombatant, Spell> spells = new HashMap<>();

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		fireCount = 0;
		lastCountdown = null;
		spells.clear();
	}

	private final ModifiableCallout<AbilityCastStart> shadowflameOnYou = ModifiableCallout.durationBasedCall("Shadowflame (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> shadowflame = ModifiableCallout.durationBasedCall("Shadowflame", "Tankbusters");
	private final RepeatSuppressor shadowflameSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void casts(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x3E4D)) {
			if (event.getTarget().isThePlayer()) {
				context.accept(shadowflameOnYou.getModified(event));
			}
			else if (shadowflameSupp.check(event)) {
				context.accept(shadowflame.getModified(event));
			}
		}
		else if (event.abilityIdMatches(0x3E42)) {
			fireCount++;
		}
	}

	@NpcCastCallout(0x3E6D)
	private final ModifiableCallout<AbilityCastStart> entropy = ModifiableCallout.durationBasedCall("Entropy", "Raidwide");

	@NpcCastCallout(0x3E3C)
	private final ModifiableCallout<AbilityCastStart> doomvoidSlicer = ModifiableCallout.durationBasedCall("Doomvoid Slicer", "Get Under");

	@NpcCastCallout(0x3E46)
	private final ModifiableCallout<AbilityCastStart> emptyHate = ModifiableCallout.durationBasedCall("Empty Hate", "Knockback");

	private final ModifiableCallout<HeadMarkerEvent> darkFire = new ModifiableCallout<>("Dark Fire", "Spread");
	private final ModifiableCallout<HeadMarkerEvent> unholyDarkness = new ModifiableCallout<>("Unholy Darkness", "Stack on {event.target}");
	private final ModifiableCallout<HeadMarkerEvent> shadoweyeOnYou = new ModifiableCallout<>("Shadoweye (On You)", "Eye on You");
	private final ModifiableCallout<HeadMarkerEvent> shadoweye = new ModifiableCallout<>("Shadoweye", "Look Away from {event.target}");

	private final ModifiableCallout<HeadMarkerEvent> delayedFire = new ModifiableCallout<>("Delayed Dark Fire", "Delayed Fire");
	private final ModifiableCallout<HeadMarkerEvent> delayedStack = new ModifiableCallout<>("Delayed Unholy Darkness", "Delayed Stack");
	private final ModifiableCallout<HeadMarkerEvent> delayedEye = new ModifiableCallout<>("Delayed Shadoweye", "Delayed Shadoweye");

	@HandleEvents
	public void spellMarkers(EventContext context, HeadMarkerEvent event) {
		boolean onYou = event.getTarget().isThePlayer();
		if (event.markerIdMatches(DARK_FIRE_MARKER)) {
			if (onYou) {
				context.accept(darkFire.getModified(event));
			}
		}
		else if (event.markerIdMatches(STACK_MARKER)) {
			context.accept(unholyDarkness.getModified(event));
		}
		else if (event.markerIdMatches(EYE_MARKER)) {
			context.accept((onYou ? shadoweyeOnYou : shadoweye).getModified(event));
		}
		else if (event.markerIdMatches(DELAYED_FIRE_MARKER)) {
			collect(event, Spell.FIRE);
			if (onYou) {
				context.accept(delayedFire.getModified(event));
			}
		}
		else if (event.markerIdMatches(DELAYED_STACK_MARKER)) {
			collect(event, Spell.STACK);
			if (onYou) {
				context.accept(delayedStack.getModified(event));
			}
		}
		else if (event.markerIdMatches(DELAYED_EYE_MARKER)) {
			collect(event, Spell.EYE);
			if (onYou) {
				context.accept(delayedEye.getModified(event));
			}
		}
	}

	private void collect(HeadMarkerEvent event, Spell spell) {
		// カウントダウンから時間が経っていれば前の周回の遅延魔法なので捨てる
		if (lastCountdown != null && lastCountdown.getEffectiveTimeSince().compareTo(SPELL_CLEANUP) > 0) {
			spells.clear();
			lastCountdown = null;
		}
		spells.put(event.getTarget(), spell);
	}

	private final ModifiableCallout<HeadMarkerEvent> countdownFire = new ModifiableCallout<>("Countdown: Dark Fire", "Spread");
	private final ModifiableCallout<HeadMarkerEvent> countdownFireWithStack = new ModifiableCallout<>("Countdown: Dark Fire (With Stack)", "Spread, Don't Stack");
	private final ModifiableCallout<HeadMarkerEvent> countdownStack = new ModifiableCallout<>("Countdown: Unholy Darkness", "Stack on {event.target}");

	@HandleEvents
	public void countdown(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(COUNTDOWN_MARKER)) {
			return;
		}
		lastCountdown = event;
		Spell spell = spells.get(event.getTarget());
		Spell mySpell = spells.get(state.getPlayer());
		if (spell == Spell.FIRE && event.getTarget().isThePlayer()) {
			context.accept((fireCount == FIRE_WITH_STACK ? countdownFireWithStack : countdownFire).getModified(event));
		}
		else if (spell == Spell.STACK) {
			// 頭割りと重なるファイガ持ちは頭割りに入ってはいけない
			if (mySpell == Spell.FIRE && fireCount == FIRE_WITH_STACK) {
				return;
			}
			context.accept(countdownStack.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> countdownEyeOnYou = new ModifiableCallout<>("Countdown: Shadoweye (On You)", "Eye on You");
	private final ModifiableCallout<HeadMarkerEvent> countdownEye = new ModifiableCallout<>("Countdown: Shadoweye", "Look Away from {event.target}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> countdownEyeOnYouSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(COUNTDOWN_MARKER)
					&& hm.getTarget().isThePlayer()
					&& spells.get(hm.getTarget()) == Spell.EYE,
			(e1, s) -> {
				s.waitMs(2_000);
				s.updateCall(countdownEyeOnYou, e1);
			});

	@AutoFeed
	private final SequentialTrigger<BaseEvent> countdownEyeSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(COUNTDOWN_MARKER)
					&& !hm.getTarget().isThePlayer()
					&& spells.get(hm.getTarget()) == Spell.EYE,
			(e1, s) -> {
				s.waitMs(2_000);
				s.updateCall(countdownEye, e1);
			});

}
