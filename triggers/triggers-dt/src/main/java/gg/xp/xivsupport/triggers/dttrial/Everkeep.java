package gg.xp.xivsupport.triggers.dttrial;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.*;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SequentialTriggerController;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/trial/zoraal-ja.ts をもとに移植
@CalloutRepo(name = "Everkeep (Zoraal Ja)", duty = KnownDuty.Everkeep)
public class Everkeep extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public Everkeep(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.Everkeep);
	}

	// 一度シンクロナスを見た後は、ピット・オブ・ヴォロクの剣がすべて鏡面側に出る
	private boolean seenFirstSync;
	private @Nullable Boolean halfCircuitLeftSafe;

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		seenFirstSync = false;
		halfCircuitLeftSafe = null;
	}

	// 前半

	@NpcCastCallout(0x934B)
	private final ModifiableCallout<AbilityCastStart> soulOverflow = ModifiableCallout.durationBasedCall("Soul Overflow", "Raidwide");

	@NpcCastCallout(0x934C)
	private final ModifiableCallout<AbilityCastStart> calamitysEdge = ModifiableCallout.durationBasedCall("Calamity's Edge", "Raidwide");

	@NpcCastCallout(0x9353)
	private final ModifiableCallout<AbilityCastStart> patricidalPique = ModifiableCallout.durationBasedCall("Patricidal Pique", "Buster on {event.target}");

	private final ModifiableCallout<AbilityCastStart> doubleEdgedFrontBack = ModifiableCallout.durationBasedCall("Double-edged Swords (Front First)", "Front then Back");
	private final ModifiableCallout<AbilityCastStart> doubleEdgedBackFront = ModifiableCallout.durationBasedCall("Double-edged Swords (Back First)", "Back then Front");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> doubleEdgedSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x9352),
			(e1, s) -> {
				// 見えないアクター2体が同じIDで詠唱するので、先に来た方の向きで順番を決める
				double heading = s.waitForCastLocation(e1).getBestHeading();
				boolean facingNorth = Math.abs(heading) > Math.PI * 3 / 4;
				s.updateCall(facingNorth ? doubleEdgedBackFront : doubleEdgedFrontBack, e1);
				// 2体目の詠唱で二重にコールしないよう、しばらく待ってから終了する
				s.waitMs(4_000);
			});

	@NpcCastCallout(value = 0x934D, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> shadowBurst = ModifiableCallout.durationBasedCall("Shadow Burst", "Away from Adds");

	private final ModifiableCallout<AbilityCastStart> vorpalTrailFirst = ModifiableCallout.durationBasedCall("Vorpal Trail: Contracting", "Dodge Contracting Swords, then Front/Back");
	private final ModifiableCallout<AbilityCastStart> vorpalTrailSecond = new ModifiableCallout<>("Vorpal Trail: Expanding", "Dodge Expanding Swords");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> vorpalTrailSq = SqtTemplates.sq(20_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x934E),
			(e1, s) -> {
				s.updateCall(vorpalTrailFirst, e1);
				// 戻ってくる剣は詠唱から一定時間後に動き出す
				s.waitMs(11_500);
				s.updateCall(vorpalTrailSecond, e1);
			});

	// 後半

	@NpcCastCallout(0x9370)
	private final ModifiableCallout<AbilityCastStart> soulOverflowTransition = ModifiableCallout.durationBasedCall("Soul Overflow (Phase Transition)", "Big Raidwide");

	@NpcCastCallout(0x9354)
	private final ModifiableCallout<AbilityCastStart> dawnOfAnAge = ModifiableCallout.durationBasedCall("Dawn of an Age", "Raidwide");

	@NpcCastCallout(0x9356)
	private final ModifiableCallout<AbilityCastStart> actualize = ModifiableCallout.durationBasedCall("Actualize", "Raidwide");

	@NpcCastCallout(0x9366)
	private final ModifiableCallout<AbilityCastStart> smitingCircuitOutside = ModifiableCallout.durationBasedCall("Smiting Circuit (Outside)", "In");

	@NpcCastCallout(0x9367)
	private final ModifiableCallout<AbilityCastStart> smitingCircuitInside = ModifiableCallout.durationBasedCall("Smiting Circuit (Inside)", "Out");

	private final ModifiableCallout<AbilityCastStart> bitterReapingOnYou = ModifiableCallout.durationBasedCall("Bitter Reaping: Buster on You", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> bitterReaping = ModifiableCallout.durationBasedCall("Bitter Reaping: Buster on Others", "Buster on {targets}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> bitterReapingSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x937A),
			(e1, s) -> {
				List<AbilityCastStart> casts = collectCasts(s, e1, 0x937A, 200);
				List<XivCombatant> targets = casts.stream().map(AbilityCastStart::getTarget).toList();
				if (targets.stream().anyMatch(XivCombatant::isThePlayer)) {
					s.updateCall(bitterReapingOnYou, e1);
				}
				else {
					s.setParam("targets", targets);
					s.updateCall(bitterReaping, e1);
				}
			});

	@HandleEvents
	public void syncTracker(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x9359)) {
			seenFirstSync = true;
		}
	}

	private final ModifiableCallout<AbilityCastStart> chasmOfVollok = ModifiableCallout.durationBasedCall("Chasm of Vollok: Safe Corner", "{safe}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> chasmOfVollokSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x9358),
			(e1, s) -> {
				List<AbilityCastStart> swords = collectCasts(s, e1, 0x9358, 200);
				List<Tile> unsafe = swords.stream()
						.map(sword -> findClosestTile(sword.getSource().getPos(), seenFirstSync, false))
						.filter(t -> t != null)
						.map(MappedTile::tile)
						.toList();
				List<Tile> safeCorners = CORNER_TILES.stream().filter(t -> !unsafe.contains(t)).toList();
				// 安置の角が1つに絞れないときはコールしない
				if (safeCorners.size() != 1) {
					return;
				}
				s.setParam("safe", TILE_DIRECTIONS.get(safeCorners.get(0)));
				s.updateCall(chasmOfVollok, e1);
			});

	private final ModifiableCallout<AbilityCastStart> forgedTrack = ModifiableCallout.durationBasedCall("Forged Track: Safe Inner Tile", "Inner {safe}");
	private final ModifiableCallout<AbilityCastStart> forgedTrackUnknown = ModifiableCallout.durationBasedCall("Forged Track: Unknown", "Avoid Lines");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> forgedTrackSq = SqtTemplates.sq(20_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x9361),
			(e1, s) -> {
				// 連続で来る場合にコールが重ならないよう、少し待ってからまとめて判定する
				List<AbilityCastStart> swords = collectCasts(s, e1, 0x9361, 2_000);
				List<Tile> possibleSafe = new ArrayList<>(INSIDE_TILES);
				for (AbilityCastStart sword : swords) {
					MappedTile mapped = findClosestTile(sword.getSource().getPos(), true, true);
					if (mapped == null || mapped.from() == null) {
						continue;
					}
					List<Tile> cleaved = FORGED_TRACK_CLEAVES.get(mapped.from()).getOrDefault(mapped.tile(), List.of());
					possibleSafe.removeAll(cleaved);
				}
				// 1つの足場からなら安置は2つ、2つの足場からなら1つになるはず
				if (possibleSafe.isEmpty() || possibleSafe.size() > 2) {
					s.updateCall(forgedTrackUnknown, e1);
					return;
				}
				s.setParam("safe", TILE_DIRECTIONS.get(possibleSafe.get(0)));
				s.updateCall(forgedTrack, e1);
			});

	// 8AEF がプレイヤー対象の頭割り
	@NpcAbilityUsedCallout(0x8AEF)
	private final ModifiableCallout<AbilityUsedEvent> dutysEdge = new ModifiableCallout<>("Duty's Edge", "Stack on {event.target}");

	@NpcCastCallout(0x9368)
	private final ModifiableCallout<AbilityCastStart> halfFullRightSword = ModifiableCallout.durationBasedCall("Half Full (Left Safe)", "Left");

	@NpcCastCallout(0x9369)
	private final ModifiableCallout<AbilityCastStart> halfFullLeftSword = ModifiableCallout.durationBasedCall("Half Full (Right Safe)", "Right");

	@HandleEvents
	public void halfCircuitSide(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x936B, 0x936C)) {
			// 936B は右手の剣なので左が安置
			halfCircuitLeftSafe = event.abilityIdMatches(0x936B);
		}
	}

	private final ModifiableCallout<AbilityCastStart> halfCircuitInLeft = ModifiableCallout.durationBasedCall("Half Circuit: In + Left", "In and Boss's Left");
	private final ModifiableCallout<AbilityCastStart> halfCircuitInRight = ModifiableCallout.durationBasedCall("Half Circuit: In + Right", "In and Boss's Right");
	private final ModifiableCallout<AbilityCastStart> halfCircuitOutLeft = ModifiableCallout.durationBasedCall("Half Circuit: Out + Left", "Out and Boss's Left");
	private final ModifiableCallout<AbilityCastStart> halfCircuitOutRight = ModifiableCallout.durationBasedCall("Half Circuit: Out + Right", "Out and Boss's Right");
	private final ModifiableCallout<AbilityCastStart> halfCircuitIn = ModifiableCallout.durationBasedCall("Half Circuit: In (Side Unknown)", "In");
	private final ModifiableCallout<AbilityCastStart> halfCircuitOut = ModifiableCallout.durationBasedCall("Half Circuit: Out (Side Unknown)", "Out");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> halfCircuitSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x936E, 0x936F),
			(e1, s) -> {
				// 左右の詠唱とほぼ同時に来るので、先にそちらを拾わせる
				s.waitMs(300);
				// 936E は剣が外向きなので内側が安置
				boolean in = e1.abilityIdMatches(0x936E);
				Boolean leftSafe = halfCircuitLeftSafe;
				ModifiableCallout<AbilityCastStart> call;
				if (leftSafe == null) {
					call = in ? halfCircuitIn : halfCircuitOut;
				}
				else if (leftSafe) {
					call = in ? halfCircuitInLeft : halfCircuitOutLeft;
				}
				else {
					call = in ? halfCircuitInRight : halfCircuitOutRight;
				}
				s.updateCall(call, e1);
			});

	// 同じIDの詠唱が短時間にまとめて来るので、指定時間内のものを集める
	private static List<AbilityCastStart> collectCasts(SequentialTriggerController<BaseEvent> s, AbilityCastStart first, long abilityId, long windowMs) {
		List<AbilityCastStart> out = new ArrayList<>();
		out.add(first);
		out.addAll(s.waitEventsUntil(16,
				AbilityCastStart.class, acs -> acs.abilityIdMatches(abilityId) && first.getEffectiveTimeSince().toMillis() <= windowMs,
				BaseEvent.class, e -> first.getEffectiveTimeSince().toMillis() > windowMs));
		return out;
	}

	// 足場のタイル判定

	private enum Tile {
		NORTH_CORNER,
		NORTHWEST_NORTH,
		NORTHEAST_NORTH,
		NORTHWEST_WEST,
		INSIDE_NORTH,
		NORTHEAST_EAST,
		WEST_CORNER,
		INSIDE_WEST,
		INSIDE_EAST,
		EAST_CORNER,
		SOUTHWEST_WEST,
		INSIDE_SOUTH,
		SOUTHEAST_EAST,
		SOUTHWEST_SOUTH,
		SOUTHEAST_SOUTH,
		SOUTH_CORNER
	}

	/**
	 * @param tile 本体の足場に換算したタイル
	 * @param from 鏡面側の足場から換算した場合はその方角、本体の足場なら null
	 */
	private record MappedTile(Tile tile, @Nullable ArenaSector from) {
	}

	// 詠唱開始時の座標は小数のぶれがあるので、丸めた座標で引く
	private static final List<Integer> TILE_COORDS = List.of(89, 93, 96, 100, 104, 107, 111);

	private static final Map<Integer, Map<Integer, Tile>> TILE_MAP = Map.of(
			89, Map.of(100, Tile.NORTH_CORNER),
			93, Map.of(96, Tile.NORTHWEST_NORTH, 104, Tile.NORTHEAST_NORTH),
			96, Map.of(93, Tile.NORTHWEST_WEST, 100, Tile.INSIDE_NORTH, 107, Tile.NORTHEAST_EAST),
			100, Map.of(89, Tile.WEST_CORNER, 96, Tile.INSIDE_WEST, 104, Tile.INSIDE_EAST, 111, Tile.EAST_CORNER),
			104, Map.of(93, Tile.SOUTHWEST_WEST, 100, Tile.INSIDE_SOUTH, 107, Tile.SOUTHEAST_EAST),
			107, Map.of(96, Tile.SOUTHWEST_SOUTH, 104, Tile.SOUTHEAST_SOUTH),
			111, Map.of(100, Tile.SOUTH_CORNER)
	);

	private static final List<Tile> CORNER_TILES = List.of(Tile.NORTH_CORNER, Tile.EAST_CORNER, Tile.SOUTH_CORNER, Tile.WEST_CORNER);

	// 並び順が優先順位になる（複数安置なら先頭をコール）
	private static final List<Tile> INSIDE_TILES = List.of(Tile.INSIDE_NORTH, Tile.INSIDE_SOUTH, Tile.INSIDE_EAST, Tile.INSIDE_WEST);

	private static final Map<Tile, ArenaSector> TILE_DIRECTIONS = Map.of(
			Tile.NORTH_CORNER, ArenaSector.NORTH,
			Tile.EAST_CORNER, ArenaSector.EAST,
			Tile.SOUTH_CORNER, ArenaSector.SOUTH,
			Tile.WEST_CORNER, ArenaSector.WEST,
			Tile.INSIDE_NORTH, ArenaSector.NORTH,
			Tile.INSIDE_EAST, ArenaSector.EAST,
			Tile.INSIDE_SOUTH, ArenaSector.SOUTH,
			Tile.INSIDE_WEST, ArenaSector.WEST
	);

	// エッジトラックの軌道は固定で、北西・南東が大きなX、北東・南西が小さなX。
	// 内側4マスを斬れるのは鏡面側の角の剣だけなので、それだけ見ればよい。
	private static final Map<ArenaSector, Map<Tile, List<Tile>>> FORGED_TRACK_CLEAVES = Map.of(
			ArenaSector.NORTHEAST, Map.of(
					Tile.NORTH_CORNER, List.of(Tile.INSIDE_NORTH, Tile.INSIDE_WEST),
					Tile.EAST_CORNER, List.of(Tile.INSIDE_EAST, Tile.INSIDE_SOUTH)),
			ArenaSector.SOUTHEAST, Map.of(
					Tile.EAST_CORNER, List.of(Tile.INSIDE_SOUTH, Tile.INSIDE_WEST),
					Tile.SOUTH_CORNER, List.of(Tile.INSIDE_NORTH, Tile.INSIDE_EAST)),
			ArenaSector.SOUTHWEST, Map.of(
					Tile.SOUTH_CORNER, List.of(Tile.INSIDE_SOUTH, Tile.INSIDE_EAST),
					Tile.WEST_CORNER, List.of(Tile.INSIDE_WEST, Tile.INSIDE_NORTH)),
			ArenaSector.NORTHWEST, Map.of(
					Tile.WEST_CORNER, List.of(Tile.INSIDE_NORTH, Tile.INSIDE_EAST),
					Tile.NORTH_CORNER, List.of(Tile.INSIDE_WEST, Tile.INSIDE_SOUTH))
	);

	// タイルは5x5で、鏡面側の足場は本体から斜めに3タイル分ずれている
	private static final double MIRROR_OFFSET = 21.21;
	// エッジトラックの剣は足場の奥の端に置かれるので、少し大きくずらす
	private static final double MIRROR_OFFSET_FORGED_TRACK = 22.98;
	private static final double ARENA_CENTER = 100.0;

	private static @Nullable MappedTile findClosestTile(@Nullable Position pos, boolean isMirror, boolean isForgedTrack) {
		if (pos == null) {
			return null;
		}
		double x = pos.x();
		double y = pos.y();
		ArenaSector from = null;
		if (isMirror) {
			double adjust = isForgedTrack ? MIRROR_OFFSET_FORGED_TRACK : MIRROR_OFFSET;
			if (x < ARENA_CENTER && y < ARENA_CENTER) {
				x += adjust;
				y += adjust;
				from = ArenaSector.NORTHWEST;
			}
			else if (x < ARENA_CENTER) {
				x += adjust;
				y -= adjust;
				from = ArenaSector.SOUTHWEST;
			}
			else if (y < ARENA_CENTER) {
				x -= adjust;
				y += adjust;
				from = ArenaSector.NORTHEAST;
			}
			else {
				x -= adjust;
				y -= adjust;
				from = ArenaSector.SOUTHEAST;
			}
		}
		int closestX = closestCoord(x);
		int closestY = closestCoord(y);
		Map<Integer, Tile> row = TILE_MAP.get(closestY);
		if (row == null) {
			return null;
		}
		Tile tile = row.get(closestX);
		if (tile == null) {
			return null;
		}
		return new MappedTile(tile, from);
	}

	private static int closestCoord(double value) {
		int closest = TILE_COORDS.get(0);
		for (int coord : TILE_COORDS) {
			if (Math.abs(coord - value) < Math.abs(closest - value)) {
				closest = coord;
			}
		}
		return closest;
	}

}
