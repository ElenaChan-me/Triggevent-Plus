package gg.xp.xivsupport.triggers.dtex;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.*;
import gg.xp.xivdata.data.duties.*;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.DescribesCastLocation;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.state.combatstate.ActiveCastRepository;
import gg.xp.xivsupport.events.state.combatstate.CastTracker;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SequentialTriggerController;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.models.ArenaPos;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/trial/enuo-ex.ts をもとに移植
@CalloutRepo(name = "EX7", duty = KnownDuty.DtEx7)
public class DTEx7 extends AutoChildEventHandler implements FilteredEventHandler {

	private static final double CENTER_X = 100;
	private static final double CENTER_Y = 100;
	// 中央判定の許容誤差
	private static final double MIDDLE_TOLERANCE = 1;

	private static final long RETURN_TO_NOTHING_MARKER = 0x02BD;
	private static final long GREAT_RETURN_TO_NOTHING_MARKER = 0x02BE;
	private static final long VOIDAL_TURBULENCE_MARKER = 0x02D1;
	// 前回のフェーズのマーカーを拾わないための有効期限
	private static final Duration CONE_MARKER_MAX_AGE = Duration.ofSeconds(15);

	// ArenaSector は 8 方向しか持たないので 16 方向は文字列で出す
	private static final String[] DIRS_16 = {
			"North", "North-northeast", "Northeast", "East-northeast",
			"East", "East-southeast", "Southeast", "South-southeast",
			"South", "South-southwest", "Southwest", "West-southwest",
			"West", "West-northwest", "Northwest", "North-northwest"
	};

	private XivState state;
	private ActiveCastRepository acr;

	private long returnToNothingMarker;
	private @Nullable HeadMarkerEvent lastConeMarkerOnPlayer;

	public DTEx7(XivState state, ActiveCastRepository acr) {
		this.state = state;
		this.acr = acr;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.DtEx7);
	}

	@HandleEvents
	public void headMarkers(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(RETURN_TO_NOTHING_MARKER, GREAT_RETURN_TO_NOTHING_MARKER)) {
			returnToNothingMarker = event.getMarkerId();
		}
		else if (event.markerIdMatches(VOIDAL_TURBULENCE_MARKER) && event.getTarget().isThePlayer()) {
			lastConeMarkerOnPlayer = event;
		}
	}

	private static int xyTo8DirNum(double x, double y) {
		return (int) (Math.round(4 - 4 * Math.atan2(x - CENTER_X, y - CENTER_Y) / Math.PI) % 8);
	}

	private static int xyTo16DirNum(double x, double y) {
		return (int) (Math.round(8 - 8 * Math.atan2(x - CENTER_X, y - CENTER_Y) / Math.PI) % 16);
	}

	private static ArenaSector sectorFor(Position pos) {
		if (Math.abs(pos.x() - CENTER_X) < MIDDLE_TOLERANCE && Math.abs(pos.y() - CENTER_Y) < MIDDLE_TOLERANCE) {
			return ArenaSector.CENTER;
		}
		return ArenaSector.all.get(xyTo8DirNum(pos.x(), pos.y()));
	}

	private Position castPos(SequentialTriggerController<?> s, AbilityCastStart cast) {
		DescribesCastLocation<AbilityCastStart> li = s.waitForCastLocation(cast);
		Position pos = li.getPos();
		if (pos != null) {
			return pos;
		}
		return state.getLatestCombatantData(cast.getSource()).getPos();
	}

	@NpcCastCallout(0xC381)
	private final ModifiableCallout<AbilityCastStart> meteorain = ModifiableCallout.durationBasedCall("Meteorain", "Raidwide");

	@NpcCastCallout(0xC334)
	private final ModifiableCallout<AbilityCastStart> almagest = ModifiableCallout.durationBasedCall("Almagest", "Raidwide");

	@NpcCastCallout(0xC36D)
	private final ModifiableCallout<AbilityCastStart> lightlessWorld = ModifiableCallout.durationBasedCall("Lightless World", "Big Raidwide");

	private final ModifiableCallout<AbilityCastStart> naughtGrowsAwayHealer = ModifiableCallout.durationBasedCall("Naught Grows: Away (Healer Stacks)", "Away from {dir}, Healer Stacks");
	private final ModifiableCallout<AbilityCastStart> naughtGrowsAwayStack = ModifiableCallout.durationBasedCall("Naught Grows: Away (Stack)", "Away from {dir}, Stack");
	private final ModifiableCallout<AbilityCastStart> naughtGrowsAway = ModifiableCallout.durationBasedCall("Naught Grows: Away", "Away from {dir}");
	private final ModifiableCallout<AbilityCastStart> naughtGrowsUnderHealer = ModifiableCallout.durationBasedCall("Naught Grows: Donut (Healer Stacks)", "{dir}, Healer Stacks");
	private final ModifiableCallout<AbilityCastStart> naughtGrowsUnderStack = ModifiableCallout.durationBasedCall("Naught Grows: Donut (Stack)", "{dir}, Stack");
	private final ModifiableCallout<AbilityCastStart> naughtGrowsUnder = ModifiableCallout.durationBasedCall("Naught Grows: Donut", "{dir}");
	private final ModifiableCallout<AbilityCastStart> naughtGrowsUnderBossHealer = ModifiableCallout.durationBasedCall("Naught Grows: Under Boss (Healer Stacks)", "Under Boss, Away from {dir}, Healer Stacks");
	private final ModifiableCallout<AbilityCastStart> naughtGrowsUnderBossStack = ModifiableCallout.durationBasedCall("Naught Grows: Under Boss (Stack)", "Under Boss, Away from {dir}, Stack");
	private final ModifiableCallout<AbilityCastStart> naughtGrowsUnderBoss = ModifiableCallout.durationBasedCall("Naught Grows: Under Boss", "Under Boss, Away from {dir}");
	private final ModifiableCallout<AbilityCastStart> naughtGrowsUnderPortalHealer = ModifiableCallout.durationBasedCall("Naught Grows: Under Void (Healer Stacks)", "{dir}, Away from Boss, Healer Stacks");
	private final ModifiableCallout<AbilityCastStart> naughtGrowsUnderPortalStack = ModifiableCallout.durationBasedCall("Naught Grows: Under Void (Stack)", "{dir}, Away from Boss, Stack");
	private final ModifiableCallout<AbilityCastStart> naughtGrowsUnderPortal = ModifiableCallout.durationBasedCall("Naught Grows: Under Void", "{dir}, Away from Boss");

	private ModifiableCallout<AbilityCastStart> byReturnToNothing(
			ModifiableCallout<AbilityCastStart> healer,
			ModifiableCallout<AbilityCastStart> stack,
			ModifiableCallout<AbilityCastStart> unknown) {
		if (returnToNothingMarker == RETURN_TO_NOTHING_MARKER) {
			return healer;
		}
		if (returnToNothingMarker == GREAT_RETURN_TO_NOTHING_MARKER) {
			return stack;
		}
		return unknown;
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> naughtGrowsSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xC339, 0xC33A, 0xC33B, 0xC33C),
			(e1, s) -> {
				// 1 個か 2 個かは決まっていないので、少し待ってから揃っているものを全部見る
				s.waitMs(500);
				List<AbilityCastStart> casts = new ArrayList<>(acr.getActiveCastsById(0xC339, 0xC33A, 0xC33B, 0xC33C)
						.stream().map(CastTracker::getCast).toList());
				if (!casts.contains(e1)) {
					casts.add(0, e1);
				}
				AbilityCastStart cast1 = casts.get(0);
				ArenaSector pos1 = sectorFor(castPos(s, cast1));
				boolean aoe1 = cast1.abilityIdMatches(0xC339, 0xC33B);
				if (casts.size() < 2) {
					s.setParam("dir", pos1);
					if (aoe1) {
						s.updateCall(byReturnToNothing(naughtGrowsAwayHealer, naughtGrowsAwayStack, naughtGrowsAway), e1);
					}
					else {
						s.updateCall(byReturnToNothing(naughtGrowsUnderHealer, naughtGrowsUnderStack, naughtGrowsUnder), e1);
					}
					return;
				}
				AbilityCastStart cast2 = casts.get(1);
				ArenaSector pos2 = sectorFor(castPos(s, cast2));
				boolean aoe2 = cast2.abilityIdMatches(0xC339, 0xC33B);
				boolean middleIsAoe = pos2 == ArenaSector.CENTER ? aoe2 : aoe1;
				ArenaSector voidDir = pos1 == ArenaSector.CENTER ? pos2 : pos1;
				s.setParam("dir", voidDir);
				if (!middleIsAoe) {
					s.updateCall(byReturnToNothing(naughtGrowsUnderBossHealer, naughtGrowsUnderBossStack, naughtGrowsUnderBoss), e1);
				}
				else {
					s.updateCall(byReturnToNothing(naughtGrowsUnderPortalHealer, naughtGrowsUnderPortalStack, naughtGrowsUnderPortal), e1);
				}
			});

	private final ModifiableCallout<AbilityCastStart> meltdown = ModifiableCallout.durationBasedCall("Meltdown", "Bait Puddles, Stop Moving, Spread");
	private final ModifiableCallout<AbilityUsedEvent> meltdownChains = new ModifiableCallout<>("Meltdown: Chains of Condemnation", "Stop Moving, Spread", 5_000);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> meltdownSq = SqtTemplates.sq(30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xC378),
			(e1, s) -> {
				s.updateCall(meltdown, e1);
				var used = s.waitEvent(AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0xC378));
				s.updateCall(meltdownChains, used);
			});

	@NpcCastCallout(0xC370)
	private final ModifiableCallout<AbilityCastStart> airyEmptiness = ModifiableCallout.durationBasedCall("Airy Emptiness", "Partners");

	@NpcCastCallout(0xC371)
	private final ModifiableCallout<AbilityCastStart> denseEmptiness = ModifiableCallout.durationBasedCall("Dense Emptiness", "Healer Stacks");

	private final ModifiableCallout<AbilityCastStart> gazeOfTheVoidCw = ModifiableCallout.durationBasedCall("Gaze of the Void (Clockwise)", "Start {start}, Clockwise to {end}");
	private final ModifiableCallout<AbilityCastStart> gazeOfTheVoidCcw = ModifiableCallout.durationBasedCall("Gaze of the Void (Counter-clockwise)", "Start {start}, Counter-clockwise to {end}");
	private final ModifiableCallout<TetherEvent> gazeOfTheVoidTankOrbs = new ModifiableCallout<>("Gaze of the Void: Tank Orbs", "Tank Orbs {tankDir}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> gazeOfTheVoidSq = SqtTemplates.sq(30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xC353, 0xC354),
			(e1, s) -> {
				boolean clockwise = e1.abilityIdMatches(0xC353);
				AbilityCastStart cone = s.findOrWaitForCastWithLocation(acr, acs -> acs.abilityIdMatches(0xC355));
				ArenaSector coneDir = ArenaPos.combatantFacing(s.waitForCastLocation(cone).getBestHeading());
				// 円錐の隣の玉もタンク担当になるので、回転方向側にずらした位置がタンク玉の間になる
				int firstTankOrb;
				if (clockwise) {
					s.setParam("start", coneDir.plusEighths(-1));
					s.setParam("end", coneDir.plusEighths(2));
					firstTankOrb = coneDir.ordinal();
					s.updateCall(gazeOfTheVoidCw, cone);
				}
				else {
					s.setParam("start", coneDir.plusEighths(1));
					s.setParam("end", coneDir.plusEighths(-2));
					firstTankOrb = (coneDir.ordinal() + 7) % 8;
					s.updateCall(gazeOfTheVoidCcw, cone);
				}
				List<TetherEvent> tethers = s.waitEvents(4, TetherEvent.class, te -> te.tetherIdMatches(0x196));
				s.setParam("tankDir", DIRS_16[firstTankOrb * 2 + 1]);
				s.updateCall(gazeOfTheVoidTankOrbs, tethers.get(tethers.size() - 1));
			});

	private final ModifiableCallout<AbilityCastStart> silentTorrent = ModifiableCallout.durationBasedCall("Silent Torrent", "{safe} Close");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> silentTorrentSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xC34B),
			(e1, s) -> {
				// 後でメルトダウンの床を誘導するため、近い方の安置だけを出す
				Position pos = castPos(s, e1);
				int dangerDir = xyTo16DirNum(pos.x(), pos.y());
				s.setParam("safe", DIRS_16[(dangerDir + 9) % 16]);
				s.updateCall(silentTorrent, e1);
			});

	private final ModifiableCallout<AbilityCastStart> deepFreezeOnYou = ModifiableCallout.durationBasedCall("Deep Freeze: Flare on You", "Flare on You, Keep Moving");
	private final ModifiableCallout<AbilityCastStart> deepFreezeNotOnYou = ModifiableCallout.durationBasedCall("Deep Freeze: Flare not on You", "Away from Flares, Keep Moving");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> deepFreezeSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xC37C),
			(e1, s) -> {
				// 2 人分の詠唱が揃うのを待つ
				s.waitMs(100);
				boolean onYou = e1.getTarget().isThePlayer()
				                || acr.getActiveCastsById(0xC37C).stream().anyMatch(ct -> ct.getCast().getTarget().isThePlayer());
				s.updateCall(onYou ? deepFreezeOnYou : deepFreezeNotOnYou, e1);
			});

	@NpcCastCallout(0xC33E)
	private final ModifiableCallout<AbilityCastStart> loomingEmptiness = ModifiableCallout.durationBasedCall("Looming Emptiness", "Knockback");

	private final ModifiableCallout<AbilityCastStart> voidalTurbulenceCone = ModifiableCallout.durationBasedCall("Voidal Turbulence: Cone on You", "Cone on You");
	private final ModifiableCallout<AbilityCastStart> voidalTurbulenceTower = ModifiableCallout.durationBasedCall("Voidal Turbulence: Tower", "Soak Tower");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> voidalTurbulenceSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xC374),
			(e1, s) -> {
				// 最後のマーカーは詠唱開始とほぼ同時なので、ラグを考えて少し待つ
				s.waitMs(100);
				HeadMarkerEvent marker = lastConeMarkerOnPlayer;
				lastConeMarkerOnPlayer = null;
				boolean cone = marker != null && marker.getEffectiveTimeSince().compareTo(CONE_MARKER_MAX_AGE) < 0;
				s.updateCall(cone ? voidalTurbulenceCone : voidalTurbulenceTower, e1);
			});

	private final ModifiableCallout<AbilityUsedEvent> curseOfTheFlesh = new ModifiableCallout<>("Curse of the Flesh", "Cleanse Debuff");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> curseOfTheFleshSq = SqtTemplates.sq(5_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0xC369) && aue.getTarget().isThePlayer(),
			(e1, s) -> s.updateCall(curseOfTheFlesh, e1));

	private final ModifiableCallout<AbilityCastStart> drainTouch = ModifiableCallout.durationBasedCall("Drain Touch", "Interrupt {event.source}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> drainTouchSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xC362),
			(e1, s) -> {
				if (state.playerJobMatches(Job::isTank)) {
					s.updateCall(drainTouch, e1);
				}
				// 複数体が同時に詠唱するのでまとめる
				s.waitMs(1_000);
			});

	@NpcCastCallout(value = 0xC365, onYou = true)
	private final ModifiableCallout<AbilityCastStart> weightOfNothing = ModifiableCallout.durationBasedCall("Weight of Nothing", "Tank Cleave on You");

	@NpcCastCallout(value = 0xC366, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> demonEye = ModifiableCallout.durationBasedCall("Demon Eye", "Look Middle");

	private final ModifiableCallout<AbilityCastStart> passageOfNaughtIntercards = ModifiableCallout.durationBasedCall("Passage of Naught: Intercards", "Intercards");
	private final ModifiableCallout<AbilityCastStart> passageOfNaughtCardinals = ModifiableCallout.durationBasedCall("Passage of Naught: Cardinals", "Cardinals");
	private final ModifiableCallout<AbilityCastStart> passageOfNaughtMiddle = ModifiableCallout.durationBasedCall("Passage of Naught: Middle", "Middle");
	private final ModifiableCallout<AbilityCastStart> passageOfNaughtMaxMelee = ModifiableCallout.durationBasedCall("Passage of Naught: Max Melee", "{safe1}/{safe2} Max Melee");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> passageOfNaughtSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xC341, 0xC342, 0xC343),
			(e1, s) -> {
				// 太い線は細い線 2 本分として数え、合計 4 本分で揃ったとみなす
				List<AbilityCastStart> lines = new ArrayList<>();
				lines.add(e1);
				int counter = e1.abilityIdMatches(0xC341) ? 2 : 1;
				while (counter < 4) {
					var next = s.waitEvent(AbilityCastStart.class, acs -> acs.abilityIdMatches(0xC341, 0xC342, 0xC343));
					lines.add(next);
					counter += next.abilityIdMatches(0xC341) ? 2 : 1;
				}
				AbilityCastStart last = lines.get(lines.size() - 1);
				if (lines.size() == 2) {
					double heading = s.waitForCastLocation(lines.get(0)).getBestHeading();
					if (ArenaPos.combatantFacing(heading).isCardinal()) {
						s.updateCall(passageOfNaughtIntercards, last);
					}
					else {
						s.updateCall(passageOfNaughtCardinals, last);
					}
					return;
				}
				if (lines.size() >= 4) {
					s.updateCall(passageOfNaughtMiddle, last);
					return;
				}
				// 太い線に垂直な最大近接距離が安置になる
				AbilityCastStart big = lines.stream().filter(acs -> acs.abilityIdMatches(0xC341)).findFirst().orElse(e1);
				Position bigPos = castPos(s, big);
				int bigDir = xyTo8DirNum(bigPos.x(), bigPos.y());
				int safe1 = (bigDir + 2) % 8;
				int safe2 = (bigDir + 6) % 8;
				s.setParam("safe1", ArenaSector.all.get(Math.min(safe1, safe2)));
				s.setParam("safe2", ArenaSector.all.get(Math.max(safe1, safe2)));
				s.updateCall(passageOfNaughtMaxMelee, last);
			});

	@NpcCastCallout(0xC37D)
	private final ModifiableCallout<AbilityCastStart> shroudedHoly = ModifiableCallout.durationBasedCall("Shrouded Holy", "Healer Stacks");

	@NpcCastCallout(0xC37F)
	private final ModifiableCallout<AbilityCastStart> dimensionZero = ModifiableCallout.durationBasedCall("Dimension Zero", "Stack");

	private final ModifiableCallout<TetherEvent> naughtHunts = new ModifiableCallout<>("Naught Hunts", "Chasing AoE on You", 13_800);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> naughtHuntsSq = SqtTemplates.sq(10_000,
			TetherEvent.class, te -> te.tetherIdMatches(0x194, 0x195) && te.eitherTargetMatches(XivCombatant::isThePlayer),
			(e1, s) -> {
				s.updateCall(naughtHunts, e1);
				// 同じ対象に複数回線が付くのでまとめる
				s.waitMs(8_000);
			});

}
