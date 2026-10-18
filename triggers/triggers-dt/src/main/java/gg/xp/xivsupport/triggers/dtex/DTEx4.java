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
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.DescribesCastLocation;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.MapEffectEvent;
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
import gg.xp.xivsupport.models.XivPlayerCharacter;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/trial/zelenia-ex.ts をもとに移植
@CalloutRepo(name = "EX4", duty = KnownDuty.DtEx4)
public class DTEx4 extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public DTEx4(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.DtEx4);
	}

	private enum Phase {
		PHASE1,
		ADDS,
		PHASE2,
		BLOOM1,
		BLOOM2,
		BLOOM3,
		BLOOM4,
		BLOOM5,
		BLOOM6
	}

	private enum TileState {
		UNKNOWN,
		RED,
		GREY
	}

	// 床タイルは内周 NNE(0x04) から時計回りに 8 枚、外周 NNE(0x0C) から同様に 8 枚
	private static final int TILE_FIRST = 0x04;
	private static final int TILE_LAST = 0x13;
	private static final int TILE_INNER_WNW = 0x0A;
	private static final int TILE_OUTER_ENE = 0x0D;
	private static final int TILE_OUTER_SSE = 0x0F;
	private static final int TILE_OUTER_SSW = 0x10;

	private static final long TILE_FLAG_RED = 0x01000040L;
	private static final long TILE_FLAG_GREY = 0x00020001L;
	private static final long TILE_FLAG_GREY_TO_RED = 0x00800040L;

	private static final long ESCELON_NEAR = 0x2F6;
	private static final long ESCELON_FAR = 0x2F7;

	private Phase phase = Phase.PHASE1;
	private final TileState[] tiles = new TileState[TILE_LAST - TILE_FIRST + 1];
	private boolean bloom4RosesNorth;
	private boolean bloom6Rose;

	{
		Arrays.fill(tiles, TileState.UNKNOWN);
	}

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		phase = Phase.PHASE1;
		Arrays.fill(tiles, TileState.UNKNOWN);
		bloom4RosesNorth = false;
		bloom6Rose = false;
	}

	private TileState tile(int location) {
		return tiles[location - TILE_FIRST];
	}

	private static boolean isTileEvent(MapEffectEvent mee) {
		return mee.getLocation() >= TILE_FIRST && mee.getLocation() <= TILE_LAST
		       && mee.flagsMatches(TILE_FLAG_RED, TILE_FLAG_GREY, TILE_FLAG_GREY_TO_RED);
	}

	@HandleEvents
	public void tileTracker(EventContext context, MapEffectEvent event) {
		if (!isTileEvent(event)) {
			return;
		}
		TileState newState = event.flagsMatches(TILE_FLAG_GREY) ? TileState.GREY : TileState.RED;
		tiles[(int) event.getLocation() - TILE_FIRST] = newState;
	}

	private static final Map<Long, Phase> phaseCasts = Map.of(
			0xA8B5L, Phase.ADDS,
			0xA8CDL, Phase.PHASE2,
			0xA8B9L, Phase.BLOOM1,
			0xAA14L, Phase.BLOOM2,
			0xAA15L, Phase.BLOOM3,
			0xAA16L, Phase.BLOOM4,
			0xAA17L, Phase.BLOOM5,
			0xAA18L, Phase.BLOOM6
	);

	@HandleEvents
	public void phaseTracker(EventContext context, AbilityCastStart event) {
		Phase newPhase = phaseCasts.get(event.getAbility().getId());
		if (newPhase != null) {
			phase = newPhase;
		}
	}

	private static boolean isDps(XivCombatant cbt) {
		return cbt instanceof XivPlayerCharacter pc && pc.getJob().isDps();
	}

	// ロール（DPS かどうか）が同じ側に薔薇マーカーが付くので、対象者本人でなくても判定できる
	private boolean roseIsOnMyRole(HeadMarkerEvent event) {
		return isDps(event.getTarget()) == state.playerJobMatches(Job::isDps);
	}

	@NpcCastCallout(0xA89E)
	private final ModifiableCallout<AbilityCastStart> thornedCatharsis = ModifiableCallout.durationBasedCall("Thorned Catharsis", "Raidwide");

	@NpcCastCallout(0xA8D5)
	private final ModifiableCallout<AbilityCastStart> stockBreak = ModifiableCallout.durationBasedCall("Stock Break", "Stack - Multiple Hits");

	@NpcCastCallout(0xA8B5)
	private final ModifiableCallout<AbilityCastStart> blessedBarricade = ModifiableCallout.durationBasedCall("Blessed Barricade", "Supports West, DPS East");

	// 詠唱完了から全体攻撃までさらに時間がある
	@NpcCastCallout(0xA8CD)
	private final ModifiableCallout<AbilityCastStart> perfumedQuietus = ModifiableCallout.durationBasedCallWithOffset("Perfumed Quietus", "Big Raidwide", Duration.ofMillis(3_900));

	@NpcCastCallout(value = 0xA8C3, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> bloom4Thorns = ModifiableCallout.durationBasedCall("Encircling Thorns (Bloom 4)", "Stack for Thorns, Break Tethers, Stack in Red Tiles");

	@PlayerHeadmarker(0x17)
	private final ModifiableCallout<HeadMarkerEvent> spearpointPush = new ModifiableCallout<>("Spearpoint Push: Tether on You", "Point Sword Cleave Out");

	private final ModifiableCallout<AbilityCastStart> specterTank = ModifiableCallout.durationBasedCall("Specter of the Lost: Tank", "Tank Tethers");
	private final ModifiableCallout<AbilityCastStart> specterNonTank = ModifiableCallout.durationBasedCall("Specter of the Lost: Non-Tank", "Avoid Tethers");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> specterOfTheLostSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xA89F),
			(e1, s) -> s.updateCall(state.playerJobMatches(Job::isTank) ? specterTank : specterNonTank, e1));

	private final ModifiableCallout<BuffApplied> escelonNearSwapFirstThird = new ModifiableCallout<>("Escelons' Fall: Near First, Swap After 1st and 3rd", "Near Bait First, Swap After First and Third", 19_000);
	private final ModifiableCallout<BuffApplied> escelonFarSwapFirstThird = new ModifiableCallout<>("Escelons' Fall: Far First, Swap After 1st and 3rd", "Far Bait First, Swap After First and Third", 19_000);
	private final ModifiableCallout<BuffApplied> escelonNearSwapSecond = new ModifiableCallout<>("Escelons' Fall: Near First, Swap After 2nd", "Near Bait First, Swap After Second", 19_000);
	private final ModifiableCallout<BuffApplied> escelonFarSwapSecond = new ModifiableCallout<>("Escelons' Fall: Far First, Swap After 2nd", "Far Bait First, Swap After Second", 19_000);

	private static boolean isEscelonBait(BuffApplied ba) {
		return ba.buffIdMatches(0xB9A) && (ba.getRawStacks() == ESCELON_NEAR || ba.getRawStacks() == ESCELON_FAR);
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> escelonsFallSq = SqtTemplates.sq(35_000,
			BuffApplied.class, DTEx4::isEscelonBait,
			(e1, s) -> {
				BuffApplied e2 = s.waitEvent(BuffApplied.class, DTEx4::isEscelonBait);
				boolean nearFirst = e1.getRawStacks() == ESCELON_NEAR;
				boolean sameTwice = e1.getRawStacks() == e2.getRawStacks();
				ModifiableCallout<BuffApplied> call;
				if (sameTwice) {
					call = nearFirst ? escelonNearSwapFirstThird : escelonFarSwapFirstThird;
				}
				else {
					call = nearFirst ? escelonNearSwapSecond : escelonFarSwapSecond;
				}
				s.updateCall(call, e2);
				// 同じ回の残りのバフで再発火しないようにする
				s.waitMs(20_000);
			});

	private final ModifiableCallout<HeadMarkerEvent> shockSpread = new ModifiableCallout<>("Shock: Spread", "Spread");
	private final ModifiableCallout<HeadMarkerEvent> shockSpreadMove = new ModifiableCallout<>("Shock: Spread Move Reminder", "Move Away");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> shockSpreadSq = SqtTemplates.sq(10_000,
			HeadMarkerEvent.class, hme -> hme.markerIdMatches(0x245) && hme.getTarget().isThePlayer(),
			(e1, s) -> {
				s.updateCall(shockSpread, e1);
				s.waitMs(7_000);
				s.updateCall(shockSpreadMove, e1);
			});

	private final ModifiableCallout<AbilityCastStart> powerBreakWest = ModifiableCallout.durationBasedCall("Power Break: West Safe", "West");
	private final ModifiableCallout<AbilityCastStart> powerBreakEast = ModifiableCallout.durationBasedCall("Power Break: East Safe", "East");

	private double bestHeadingFor(AbilityCastStart event) {
		DescribesCastLocation<AbilityCastStart> li = event.getLocationInfo();
		if (li != null) {
			return li.getBestHeading();
		}
		else {
			return state.getLatestCombatantData(event.getSource()).getPos().heading();
		}
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> powerBreakSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xA8B0, 0xA8B1),
			(e1, s) -> {
				if (e1.getLocationInfo() == null) {
					s.waitThenRefreshCombatants(100);
				}
				// A8B0 は幻影から見て右側を薙ぐ。北側にいる幻影は南を向いているので東西が反転する
				boolean onNorthSide = ArenaPos.combatantFacing(bestHeadingFor(e1)) == ArenaSector.SOUTH;
				boolean cleavingEast = e1.abilityIdMatches(0xA8B0);
				if (onNorthSide) {
					cleavingEast = !cleavingEast;
				}
				s.updateCall(cleavingEast ? powerBreakWest : powerBreakEast, e1);
			});

	private final ModifiableCallout<HeadMarkerEvent> shockTower = new ModifiableCallout<>("Shock: Donut on You (Phase 1)", "Donut on You, Get Tower");
	private final ModifiableCallout<HeadMarkerEvent> bloom4Stack = new ModifiableCallout<>("Alexandrian Banish III (Bloom 4)", "Support/DPS Stacks");
	private final ModifiableCallout<HeadMarkerEvent> bloom1Clockwise = new ModifiableCallout<>("Bloom 1: Clockwise", "Start Southeast, Rotate Clockwise");
	private final ModifiableCallout<HeadMarkerEvent> bloom1CounterClockwise = new ModifiableCallout<>("Bloom 1: Counter-clockwise", "Start North, Rotate Counter-clockwise");
	private final ModifiableCallout<HeadMarkerEvent> bloom3Rose = new ModifiableCallout<>("Bloom 3: Rose on You", "Rose Marker on You");
	private final ModifiableCallout<HeadMarkerEvent> bloom3Tower = new ModifiableCallout<>("Bloom 3: Tower", "Soak Tower");
	private final ModifiableCallout<HeadMarkerEvent> bloom4Rose = new ModifiableCallout<>("Bloom 4: Rose on You", "Rose Marker on You, Spread {dir}");
	private final ModifiableCallout<HeadMarkerEvent> bloom4Spread = new ModifiableCallout<>("Bloom 4: Spread on You", "Spread Marker on You, Spread {dir}");
	private final ModifiableCallout<HeadMarkerEvent> bloom6RoseMarker = new ModifiableCallout<>("Bloom 6: Rose on You", "Rose Marker on You");
	private final ModifiableCallout<HeadMarkerEvent> bloom6TowerLater = new ModifiableCallout<>("Bloom 6: Tower Later", "Tower Soaks Later");

	private final RepeatSuppressor bloom4StackSupp = new RepeatSuppressor(Duration.ofSeconds(5));
	private final RepeatSuppressor bloom1Supp = new RepeatSuppressor(Duration.ofSeconds(5));
	private final RepeatSuppressor roseSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	@HandleEvents
	public void headmarkers(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x244)) {
			if (phase == Phase.PHASE1 && event.getTarget().isThePlayer()) {
				context.accept(shockTower.getModified(event));
			}
		}
		else if (event.markerIdMatches(0x255)) {
			if (bloom4StackSupp.check(event)) {
				context.accept(bloom4Stack.getModified(event));
			}
		}
		else if (event.markerIdMatches(0xA7, 0xA8)) {
			if (bloom1Supp.check(event)) {
				context.accept((event.markerIdMatches(0xA7) ? bloom1Clockwise : bloom1CounterClockwise).getModified(event));
			}
		}
		else if (event.markerIdMatches(0x250)) {
			if (phase != Phase.BLOOM3 && phase != Phase.BLOOM4 && phase != Phase.BLOOM6) {
				return;
			}
			if (!roseSupp.check(event)) {
				return;
			}
			boolean youAreRose = roseIsOnMyRole(event);
			switch (phase) {
				case BLOOM3 -> context.accept((youAreRose ? bloom3Rose : bloom3Tower).getModified(event));
				case BLOOM4 -> {
					// 薔薇組は薔薇側、散開組はその反対側
					boolean north = youAreRose == bloom4RosesNorth;
					ArenaSector dir = north ? ArenaSector.NORTH : ArenaSector.SOUTH;
					context.accept((youAreRose ? bloom4Rose : bloom4Spread).getModified(event, Map.<String, Object>of("dir", dir)));
				}
				case BLOOM6 -> {
					bloom6Rose = youAreRose;
					context.accept((youAreRose ? bloom6RoseMarker : bloom6TowerLater).getModified(event));
				}
				default -> {
				}
			}
		}
	}

	private final ModifiableCallout<MapEffectEvent> bloom4RosesNorthCall = new ModifiableCallout<>("Bloom 4: Roses North", "Roses North, Spreads South");
	private final ModifiableCallout<MapEffectEvent> bloom4RosesSouthCall = new ModifiableCallout<>("Bloom 4: Roses South", "Roses South, Spreads North");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> bloom4RosesSq = SqtTemplates.sq(110_000,
			MapEffectEvent.class, mee -> phase == Phase.BLOOM4 && isTileEvent(mee),
			(e1, s) -> {
				// 床の更新は複数行に分かれて届くので少し待ってから見る
				s.waitMs(500);
				bloom4RosesNorth = tile(TILE_OUTER_SSE) == TileState.RED || tile(TILE_OUTER_SSW) == TileState.RED;
				s.updateCall(bloom4RosesNorth ? bloom4RosesNorthCall : bloom4RosesSouthCall, e1);
				// 同じフェーズ中の床更新で再発火させない
				s.waitMs(100_000);
			});

	private final ModifiableCallout<AbilityCastStart> bloom2InWest = ModifiableCallout.durationBasedCall("Bloom 2: In West First", "In WSW, Out WNW, Out WSW");
	private final ModifiableCallout<AbilityCastStart> bloom2InEast = ModifiableCallout.durationBasedCall("Bloom 2: In East First", "In ESE, Out ESE, Out ENE");
	private final ModifiableCallout<AbilityCastStart> bloom2OutWest = ModifiableCallout.durationBasedCall("Bloom 2: Out West First", "Out WSW, In WNW, In WSW");
	private final ModifiableCallout<AbilityCastStart> bloom2OutEast = ModifiableCallout.durationBasedCall("Bloom 2: Out East First", "Out ESE, In ESE, In ENE");

	private final ModifiableCallout<AbilityCastStart> bloom5InFirst = ModifiableCallout.durationBasedCall("Bloom 5: In First", "In, Clockwise, then Out");
	private final ModifiableCallout<AbilityCastStart> bloom5OutFirst = ModifiableCallout.durationBasedCall("Bloom 5: Out First", "Out, Clockwise, then In");

	private final ModifiableCallout<AbilityCastStart> bloom6RoseIn = ModifiableCallout.durationBasedCall("Bloom 6: Place Rose In", "Place Rose In, Dodge Cleaves");
	private final ModifiableCallout<AbilityCastStart> bloom6RoseOut = ModifiableCallout.durationBasedCall("Bloom 6: Place Rose Out", "Place Rose Out, Dodge Cleaves");
	private final ModifiableCallout<AbilityCastStart> bloom6TowerCall = ModifiableCallout.durationBasedCall("Bloom 6: Tower", "Dodge Cleaves, Soak Tower");

	private final RepeatSuppressor bloomMovementSupp = new RepeatSuppressor(Duration.ofSeconds(30));
	private final RepeatSuppressor bloom6Supp = new RepeatSuppressor(Duration.ofSeconds(30));

	@HandleEvents
	public void bloomCasts(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0xA9BA, 0xA9BB)) {
			if (phase != Phase.BLOOM2 && phase != Phase.BLOOM5) {
				return;
			}
			if (!bloomMovementSupp.check(event)) {
				return;
			}
			boolean inSafeFirst = event.abilityIdMatches(0xA9BB);
			if (phase == Phase.BLOOM2) {
				// 床パターンは 2 通りしかなく、雷鳴剣の軌道は固定
				boolean innerWestSafe = tile(TILE_INNER_WNW) == TileState.RED;
				ModifiableCallout<AbilityCastStart> call;
				if (inSafeFirst) {
					call = innerWestSafe ? bloom2InWest : bloom2InEast;
				}
				else {
					call = innerWestSafe ? bloom2OutEast : bloom2OutWest;
				}
				context.accept(call.getModified(event));
			}
			else {
				context.accept((inSafeFirst ? bloom5InFirst : bloom5OutFirst).getModified(event));
			}
		}
		else if (event.abilityIdMatches(0xA8DF, 0xA8E1)) {
			if (phase != Phase.BLOOM6 || !bloom6Supp.check(event)) {
				return;
			}
			if (bloom6Rose) {
				boolean cleavingEneFirst = event.abilityIdMatches(0xA8E1);
				boolean towerEneOut = tile(TILE_OUTER_ENE) == TileState.RED;
				boolean placeIn = cleavingEneFirst == towerEneOut;
				context.accept((placeIn ? bloom6RoseIn : bloom6RoseOut).getModified(event));
			}
			else {
				context.accept(bloom6TowerCall.getModified(event));
			}
		}
	}

}
