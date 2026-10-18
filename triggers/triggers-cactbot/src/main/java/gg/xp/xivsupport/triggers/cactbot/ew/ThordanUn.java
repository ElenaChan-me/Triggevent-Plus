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
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.ArenaPos;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.XivCombatant;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/trial/thordan-un.ts をもとに移植
@CalloutRepo(name = "The Singularity Reactor Unreal", duty = KnownDuty.TheSingularityReactorUnreal)
public class ThordanUn extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public ThordanUn(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheSingularityReactorUnreal);
	}

	private int phase = 1;
	private @Nullable XivCombatant mainTank;
	private boolean seenThrust;
	private int defCounter = 1;
	private @Nullable XivCombatant swordKnight;
	private @Nullable XivCombatant swordTarget;
	private @Nullable XivCombatant shieldKnight;
	private @Nullable XivCombatant shieldTarget;

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		phase = 1;
		mainTank = null;
		seenThrust = false;
		defCounter = 1;
		clearBladedance();
	}

	private void clearBladedance() {
		swordKnight = null;
		swordTarget = null;
		shieldKnight = null;
		shieldTarget = null;
	}

	private static boolean sameEntity(@Nullable XivCombatant a, @Nullable XivCombatant b) {
		return a != null && b != null && a.getId() == b.getId();
	}

	@HandleEvents
	public void phaseTracker(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x89BA)) {
			phase = 2;
		}
		else if (event.abilityIdMatches(0x89CB) && event.isFirstTarget()) {
			phase++;
		}
		else if (event.abilityIdMatches(0x89BC)) {
			// ヘヴンリーヒールを受けるメインタンクを把握しておく
			mainTank = event.getTarget();
		}
	}

	@PlayerHeadmarker(0x18)
	private final ModifiableCallout<HeadMarkerEvent> lightningStorm = new ModifiableCallout<>("Lightning Storm", "Spread");

	private final ModifiableCallout<HeadMarkerEvent> dragonsRage = new ModifiableCallout<>("Dragon's Rage", "Stack on {event.target}");

	@NpcCastCallout(0x89C4)
	private final ModifiableCallout<AbilityCastStart> ancientQuaga = ModifiableCallout.durationBasedCall("Ancient Quaga", "Raidwide");

	@NpcCastCallout(0x89C6)
	private final ModifiableCallout<AbilityCastStart> heavenlyHeel = ModifiableCallout.durationBasedCall("Heavenly Heel", "Buster on {event.target}");

	private final ModifiableCallout<TetherEvent> holyChains = new ModifiableCallout<>("Holy Chains", "Break Chains with {partner}");

	@HandleEvents
	public void holyChainsTether(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x9) && event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			XivCombatant partner = event.getTargetMatching(cbt -> !cbt.isThePlayer());
			context.accept(holyChains.getModified(event, Map.<String, Object>of("partner", partner == null ? "?" : partner)));
		}
	}

	@NpcCastCallout(value = 0x89DC, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> conviction = ModifiableCallout.durationBasedCall("Conviction", "Towers");

	private final ModifiableCallout<AbilityCastStart> dragonsGazeSingle = ModifiableCallout.durationBasedCall("Dragon's Gaze (Thordan Only)", "Look Away from Thordan");
	private final ModifiableCallout<AbilityCastStart> dragonsGazeDouble = ModifiableCallout.durationBasedCall("Dragon's Gaze (Thordan and Eye)", "Look Away from Thordan and Eye");

	@HandleEvents
	public void dragonsGaze(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x89C8)) {
			context.accept((phase == 1 ? dragonsGazeSingle : dragonsGazeDouble).getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> tripleSpiralThrust = new ModifiableCallout<>("Triple Spiral Thrust", "{safe1}/{safe2} Safe");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> tripleSpiralThrustSq = SqtTemplates.sq(5_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x89B7) && phase == 2 && !seenThrust,
			(e1, s) -> {
				// 騎士3体の出現を待ち、向いている方向とその反対側を危険として除外する
				List<AbilityUsedEvent> knights = new ArrayList<>();
				knights.add(e1);
				knights.addAll(s.waitEvents(2, AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x89B7)));
				seenThrust = true;
				List<ArenaSector> safe = new ArrayList<>(ArenaSector.all);
				for (AbilityUsedEvent knight : knights) {
					ArenaSector dir = ArenaPos.combatantFacing(knight.getSource());
					if (dir == ArenaSector.UNKNOWN) {
						dir = ArenaPos.combatantFacing(state.getLatestCombatantData(knight.getSource()));
					}
					safe.remove(dir);
					safe.remove(dir.opposite());
				}
				if (safe.size() != 2) {
					return;
				}
				s.setParam("safe1", safe.get(0));
				s.setParam("safe2", safe.get(1));
				s.updateCall(tripleSpiralThrust, e1);
			});

	private final ModifiableCallout<BuffApplied> swordOfTheHeavens = new ModifiableCallout<>("Sword of the Heavens", "Attack {event.target}");

	@HandleEvents
	public void knightBuffs(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x3B0)) {
			swordKnight = event.getTarget();
			context.accept(swordOfTheHeavens.getModified(event));
		}
		else if (event.buffIdMatches(0x3B1)) {
			shieldKnight = event.getTarget();
		}
	}

	@NpcCastCallout(value = 0x89D4, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> holiestOfHoly = ModifiableCallout.durationBasedCall("Holiest of Holy", "Raidwide");

	@HandleEvents
	public void holyBladedanceCollect(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x89D5)) {
			if (sameEntity(swordKnight, event.getSource())) {
				swordTarget = event.getTarget();
			}
			if (sameEntity(shieldKnight, event.getSource())) {
				shieldTarget = event.getTarget();
			}
		}
	}

	private final ModifiableCallout<AbilityCastStart> holyBladedanceUnknown = ModifiableCallout.durationBasedCall("Holy Bladedance (Unknown Targets)", "Heavy Busters");
	private final ModifiableCallout<AbilityCastStart> holyBladedanceSingle = ModifiableCallout.durationBasedCall("Holy Bladedance (Same Target)", "2x Buster on {target}");
	private final ModifiableCallout<AbilityCastStart> holyBladedanceDouble = ModifiableCallout.durationBasedCall("Holy Bladedance (Split)", "Sword Buster on {sword}, Shield on {shield}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> holyBladedanceSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x89D5),
			(e1, s) -> {
				// 2体分の詠唱が出揃うのを待つ
				s.waitMs(500);
				if (swordTarget == null || shieldTarget == null) {
					s.updateCall(holyBladedanceUnknown, e1);
				}
				else if (sameEntity(swordTarget, shieldTarget)) {
					s.setParam("target", swordTarget);
					s.updateCall(holyBladedanceSingle, e1);
				}
				else {
					s.setParam("sword", swordTarget);
					s.setParam("shield", shieldTarget);
					s.updateCall(holyBladedanceDouble, e1);
				}
				clearBladedance();
			});

	private final ModifiableCallout<HeadMarkerEvent> skywardLeap = new ModifiableCallout<>("Skyward Leap", "Defamation on You");
	private final ModifiableCallout<HeadMarkerEvent> skywardLeapCounted = new ModifiableCallout<>("Skyward Leap (Intermission)", "Defamation #{number} on You");

	@HandleEvents
	public void headMarkers(EventContext context, HeadMarkerEvent event) {
		boolean onYou = event.getTarget().isThePlayer();
		if (event.markerIdMatches(0x3E)) {
			context.accept(dragonsRage.getModified(event));
		}
		else if (event.markerIdMatches(0x10)) {
			furySpear(context, event, onYou);
		}
		else if (event.markerIdMatches(0x0E) && onYou) {
			if (phase == 2) {
				context.accept(skywardLeapCounted.getModified(event, Map.<String, Object>of("number", defCounter)));
			}
			else {
				context.accept(skywardLeap.getModified(event));
			}
			defCounter++;
		}
	}

	private final ModifiableCallout<TetherEvent> spiralPierce = new ModifiableCallout<>("Spiral Pierce", "Line AoE on You");
	private final RepeatSuppressor spiralPierceSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void spiralPierceTether(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x5) && event.getTarget().isThePlayer() && spiralPierceSupp.check(event)) {
			context.accept(spiralPierce.getModified(event));
		}
	}

	@PlayerHeadmarker(0x1D)
	private final ModifiableCallout<HeadMarkerEvent> hiemalStorm = new ModifiableCallout<>("Hiemal Storm", "Ice Puddle on You");

	@PlayerHeadmarker(0x0B)
	private final ModifiableCallout<HeadMarkerEvent> cometPuddles = new ModifiableCallout<>("Comet", "4x Meteor Puddles on You");

	private final ModifiableCallout<HeadMarkerEvent> furySpearOnYou = new ModifiableCallout<>("Fury Spear: On You", "Wild Charge on You");
	private final ModifiableCallout<HeadMarkerEvent> furySpearMainTank = new ModifiableCallout<>("Fury Spear: Main Tank", "Wild Charge, Stay Out");
	private final ModifiableCallout<HeadMarkerEvent> furySpearIntercept = new ModifiableCallout<>("Fury Spear: Intercept", "Intercept {event.target}");

	private void furySpear(EventContext context, HeadMarkerEvent event, boolean onYou) {
		if (onYou) {
			context.accept(furySpearOnYou.getModified(event));
		}
		// ヘヴンリーヒールを直後に受けるメインタンクが重なると被ダメージ上昇で即死する
		else if (sameEntity(mainTank, state.getPlayer())) {
			context.accept(furySpearMainTank.getModified(event));
		}
		else {
			context.accept(furySpearIntercept.getModified(event));
		}
	}

	@NpcCastCallout(0x89F0)
	private final ModifiableCallout<AbilityCastStart> pureOfSoul = ModifiableCallout.durationBasedCall("Pure of Soul", "Raidwide");

}
