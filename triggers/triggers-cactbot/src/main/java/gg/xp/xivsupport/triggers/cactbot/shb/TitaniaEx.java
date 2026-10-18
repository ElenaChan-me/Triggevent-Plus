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
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
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
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/trial/titania-ex.ts をもとに移植
@CalloutRepo(name = "The Dancing Plague Extreme", duty = KnownDuty.TheDancingPlagueExtreme)
public class TitaniaEx extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public TitaniaEx(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheDancingPlagueExtreme);
	}

	private boolean seenMistRune;
	private boolean seenFlameRune;
	private int pummelCount;
	private boolean hasBomb;
	private int thunderCount;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		seenMistRune = false;
		seenFlameRune = false;
		pummelCount = 0;
		hasBomb = false;
		thunderCount = 0;
	}

	@NpcCastCallout(0x3D4B)
	private final ModifiableCallout<AbilityCastStart> brightSabbath = ModifiableCallout.durationBasedCall("Bright Sabbath", "Raidwide");

	@NpcCastCallout(0x3D4C)
	private final ModifiableCallout<AbilityCastStart> phantomOut = ModifiableCallout.durationBasedCall("Phantom Rune (Out)", "Out");

	@NpcCastCallout(0x3D4D)
	private final ModifiableCallout<AbilityCastStart> phantomIn = ModifiableCallout.durationBasedCall("Phantom Rune (In)", "In");

	private final ModifiableCallout<AbilityCastStart> mistRuneRepeat = ModifiableCallout.durationBasedCall("Mist Rune (2nd+)", "In/Out, then Water Positions");
	private final ModifiableCallout<AbilityCastStart> mistRune = ModifiableCallout.durationBasedCall("Mist Rune", "Water Positions");

	private final ModifiableCallout<AbilityCastStart> divinationOnYou = ModifiableCallout.durationBasedCall("Divination (On You)", "Tank Cleave on You");
	private final ModifiableCallout<AbilityCastStart> divination = ModifiableCallout.durationBasedCall("Divination", "Avoid Tank Cleave");

	@NpcCastCallout(0x42D7)
	private final ModifiableCallout<AbilityCastStart> bramble = ModifiableCallout.durationBasedCall("Chain of Brambles", "Wait for Tethers in Center");

	private final ModifiableCallout<AbilityCastStart> faeLightOnYou = ModifiableCallout.durationBasedCall("Fae Light (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> faeLightCleave = ModifiableCallout.durationBasedCall("Fae Light (DPS)", "Avoid Tank Cleave");
	private final ModifiableCallout<AbilityCastStart> faeLight = ModifiableCallout.durationBasedCall("Fae Light", "Buster on {event.target}");

	private final ModifiableCallout<AbilityCastStart> pummel = ModifiableCallout.durationBasedCall("Pummel", "Pummel {num}");

	@HandleEvents
	public void casts(EventContext context, AbilityCastStart event) {
		if (event.getSource().isPc()) {
			return;
		}
		if (event.abilityIdMatches(0x3D45)) {
			context.accept((seenMistRune ? mistRuneRepeat : mistRune).getModified(event));
			seenMistRune = true;
		}
		else if (event.abilityIdMatches(0x3D4A)) {
			context.accept((event.getTarget().isThePlayer() ? divinationOnYou : divination).getModified(event));
		}
		else if (event.abilityIdMatches(0x3D2C)) {
			if (event.getTarget().isThePlayer()) {
				context.accept(faeLightOnYou.getModified(event));
			}
			else if (state.playerJobMatches(Job::isDps)) {
				context.accept(faeLightCleave.getModified(event));
			}
			else {
				context.accept(faeLight.getModified(event));
			}
		}
		else if (event.abilityIdMatches(0x3D37)) {
			pummelCount++;
			if (state.playerJobMatches(job -> job.isTank() || job == Job.BLU)) {
				context.accept(pummel.getModified(event, Map.of("num", pummelCount)));
			}
		}
		else if (event.abilityIdMatches(0x3D32)) {
			thunderCount = 0;
		}
	}

	private final ModifiableCallout<AbilityCastStart> flameRuneRepeat = new ModifiableCallout<>("Flame Rune (2nd+)", "Stack (Maybe Rotate)");
	private final ModifiableCallout<AbilityCastStart> flameRune = new ModifiableCallout<>("Flame Rune", "Stack Positions");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> flameRuneSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x3D47),
			(e1, s) -> {
				// 最初の頭割りまで16.5秒あるので、半分ほど待ってから呼ぶ
				s.waitMs(8_500);
				s.updateCall(seenFlameRune ? flameRuneRepeat : flameRune, e1);
				seenFlameRune = true;
			});

	private final ModifiableCallout<TetherEvent> brambleTether = new ModifiableCallout<>("Chain of Brambles Tether", "Break Tether with {player}");

	private final ModifiableCallout<TetherEvent> thunderTether = new ModifiableCallout<>("Thunder Tether", "Initial Thunder Tether");
	private final RepeatSuppressor thunderTetherSupp = new RepeatSuppressor(Duration.ofSeconds(60));

	@HandleEvents
	public void tethers(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x12)) {
			XivCombatant partner;
			if (event.getTarget().isThePlayer()) {
				partner = event.getSource();
			}
			else if (event.getSource().isThePlayer()) {
				partner = event.getTarget();
			}
			else {
				return;
			}
			context.accept(brambleTether.getModified(event, Map.of("player", partner)));
		}
		else if (event.tetherIdMatches(0x54) && !event.getSource().isPc() && thunderTetherSupp.check(event)) {
			context.accept(thunderTether.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> brambleKnockback = new ModifiableCallout<>("Bramble Knockback", "Diagonal Knockback Soon");

	private final ModifiableCallout<AbilityUsedEvent> frostRuneIn = new ModifiableCallout<>("Frost Rune (In)", "In");
	private final RepeatSuppressor frostRuneInSupp = new RepeatSuppressor(Duration.ofSeconds(60));

	private final ModifiableCallout<AbilityUsedEvent> thunderRune = new ModifiableCallout<>("Thunder Rune", "Thunder {num}");
	private final RepeatSuppressor thunderRuneSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void abilities(EventContext context, AbilityUsedEvent event) {
		if (event.getSource().isPc()) {
			return;
		}
		if (event.abilityIdMatches(0x3D42) && event.isFirstTarget()) {
			context.accept(brambleKnockback.getModified(event));
		}
		else if (event.abilityIdMatches(0x3D2B) && frostRuneInSupp.check(event)) {
			context.accept(frostRuneIn.getModified(event));
		}
		else if (event.abilityIdMatches(0x3D29) && thunderRuneSupp.check(event)) {
			thunderCount++;
			context.accept(thunderRune.getModified(event, Map.of("num", thunderCount)));
		}
		else if (event.abilityIdMatches(0x3D3F)) {
			hasBomb = false;
		}
	}

	private final ModifiableCallout<AbilityCastStart> frostRune = ModifiableCallout.durationBasedCall("Frost Rune", "Get Middle, Shiva Circles");
	private final ModifiableCallout<AbilityCastStart> frostRuneOut = new ModifiableCallout<>("Frost Rune (Out)", "Out");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> frostRuneSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x3D2A),
			(e1, s) -> {
				s.updateCall(frostRune, e1);
				s.waitMs(6_500);
				s.updateCall(frostRuneOut, e1);
			});

	@NpcCastCallout(0x3D2E)
	private final ModifiableCallout<AbilityCastStart> growthRune = ModifiableCallout.durationBasedCall("Growth Rune", "Roots");

	@PlayerHeadmarker(0x8B)
	private final ModifiableCallout<HeadMarkerEvent> uplift = new ModifiableCallout<>("Uplift", "Spread");

	@NpcCastCallout(value = 0x3D36, onYou = true)
	private final ModifiableCallout<AbilityCastStart> hardSwipe = ModifiableCallout.durationBasedCall("Hard Swipe", "Buster on You");

	private final ModifiableCallout<HeadMarkerEvent> peasebomb = new ModifiableCallout<>("Peasebomb", "Spread");

	@HandleEvents
	public void peasebombMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x8D) && event.getTarget().isThePlayer()) {
			hasBomb = true;
			context.accept(peasebomb.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> addsStackOnYou = new ModifiableCallout<>("Adds Stack (On You)", "Stack on You");
	private final ModifiableCallout<HeadMarkerEvent> addsStack = new ModifiableCallout<>("Adds Stack", "Stack on {event.target}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> addsStackSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(0xA1),
			(e1, s) -> {
				// 爆弾マーカーと同時に付くので、そちらを先に処理させる
				s.waitMs(250);
				if (e1.getTarget().isThePlayer()) {
					s.updateCall(addsStackOnYou, e1);
				}
				else if (!hasBomb) {
					s.updateCall(addsStack, e1);
				}
			});

}
