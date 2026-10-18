package gg.xp.xivsupport.triggers.dtdungeon;

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
import gg.xp.xivsupport.events.actlines.events.MapEffectEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/dungeon/worqor-zormor.ts をもとに移植
@CalloutRepo(name = "Worqor Zormor", duty = KnownDuty.WorqorZormor)
public class WorqorZormor extends AutoChildEventHandler implements FilteredEventHandler {

	private static final double ryoqorCenterX = -108;
	private static final double ryoqorCenterY = 119;
	private static final long tileFlags = 0x0002_0001L;

	private XivState state;

	public WorqorZormor(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.WorqorZormor);
	}

	private int fluffleUpCount;
	private final Map<Long, ArenaSector> addCleaveDirs = new HashMap<>();
	private final Set<Long> tetheredAdds = new HashSet<>();
	private boolean seenCrystallineStorm;
	private boolean seenFirstWaterTile;
	private boolean seenFirstFireTile;

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		fluffleUpCount = 0;
		addCleaveDirs.clear();
		tetheredAdds.clear();
		seenCrystallineStorm = false;
		seenFirstWaterTile = false;
		seenFirstFireTile = false;
	}

	// 1ボス目（Ryoqor Terteh）

	@NpcCastCallout(0x8DB8)
	private final ModifiableCallout<AbilityCastStart> frostingFracas = ModifiableCallout.durationBasedCall("Frosting Fracas", "Raidwide");

	@HandleEvents
	public void castTracker(EventContext context, AbilityCastStart event) {
		switch ((int) event.getAbility().getId()) {
			case 0x8DA9 -> fluffleUpCount++;
			case 0x8DBE -> seenCrystallineStorm = true;
			// 東西南北の小さい雑魚は、居る位置と向いている方角の間の 1/4 を薙ぎ払う
			case 0x8DAE -> {
				Position pos = sourcePos(event);
				if (pos != null) {
					ArenaSector side = cardinalOf(pos.getX() - ryoqorCenterX, pos.getY() - ryoqorCenterY);
					ArenaSector facing = cardinalOf(Math.sin(pos.getHeading()), Math.cos(pos.getHeading()));
					ArenaSector cleaved = ArenaSector.tryCombineTwoCardinals(List.of(side, facing));
					addCleaveDirs.put(event.getSource().getId(), cleaved == null ? ArenaSector.UNKNOWN : cleaved);
				}
			}
			// 斜めの大きい雑魚は自分の居る 1/4 を範囲攻撃する
			case 0x8DAF -> {
				Position pos = sourcePos(event);
				if (pos != null) {
					addCleaveDirs.put(event.getSource().getId(), intercardOf(pos.getX() - ryoqorCenterX, pos.getY() - ryoqorCenterY));
				}
			}
		}
	}

	@HandleEvents
	public void coldFeatTether(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x110)) {
			// どちらがボスか判別しなくても、雑魚以外の ID は範囲の記録に無いので両方入れておけば足りる
			for (XivCombatant cbt : event.getTargets()) {
				tetheredAdds.add(cbt.getId());
			}
		}
	}

	private @Nullable Position sourcePos(AbilityCastStart event) {
		Position pos = event.getSource().getPos();
		if (pos == null) {
			pos = state.getLatestCombatantData(event.getSource()).getPos();
		}
		return pos;
	}

	private static ArenaSector cardinalOf(double dx, double dy) {
		if (Math.abs(dx) > Math.abs(dy)) {
			return dx > 0 ? ArenaSector.EAST : ArenaSector.WEST;
		}
		return dy > 0 ? ArenaSector.SOUTH : ArenaSector.NORTH;
	}

	private static ArenaSector intercardOf(double dx, double dy) {
		if (dx > 0) {
			return dy > 0 ? ArenaSector.SOUTHEAST : ArenaSector.NORTHEAST;
		}
		return dy > 0 ? ArenaSector.SOUTHWEST : ArenaSector.NORTHWEST;
	}

	private final ModifiableCallout<AbilityCastStart> coldFeatStartTwo = ModifiableCallout.durationBasedCall("Cold Feat: Start (Two Safe Spots)", "Start {safe1} or {safe2}");
	private final ModifiableCallout<AbilityCastStart> coldFeatStart = ModifiableCallout.durationBasedCall("Cold Feat: Start", "Start {safe}");
	private final ModifiableCallout<AbilityCastStart> coldFeatAvoidStart = ModifiableCallout.durationBasedCall("Cold Feat: Start (Unknown)", "Avoid Untethered Adds");
	private final ModifiableCallout<AbilityCastStart> coldFeatFollowupTwo = new ModifiableCallout<>("Cold Feat: Followup (Two Safe Spots)", "Go {safe1} or {safe2}");
	private final ModifiableCallout<AbilityCastStart> coldFeatFollowup = new ModifiableCallout<>("Cold Feat: Followup", "Go {safe}");
	private final ModifiableCallout<AbilityCastStart> coldFeatAvoidFollowup = new ModifiableCallout<>("Cold Feat: Followup (Unknown)", "Avoid Remaining Adds");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> coldFeatSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x8DAA),
			(e1, s) -> {
				List<ArenaSector> followupSafe = new ArrayList<>();
				List<ArenaSector> coldDirs = tetheredAdds.stream()
						.map(id -> addCleaveDirs.getOrDefault(id, ArenaSector.UNKNOWN))
						.toList();
				if (tetheredAdds.isEmpty()) {
					s.updateCall(coldFeatAvoidStart, e1);
				}
				else if (fluffleUpCount == 1) {
					// 繋がれた雑魚側の斜め 2 か所が先に安置になり、残り 2 か所が後で安置になる
					List<ArenaSector> first = ArenaSector.quadrants.stream().filter(coldDirs::contains).toList();
					List<ArenaSector> second = ArenaSector.quadrants.stream().filter(d -> !coldDirs.contains(d)).toList();
					if (first.size() == 2 && second.size() == 2) {
						followupSafe.addAll(second);
						s.setParam("safe1", first.get(0));
						s.setParam("safe2", first.get(1));
						s.updateCall(coldFeatStartTwo, e1);
					}
					else {
						s.updateCall(coldFeatAvoidStart, e1);
					}
				}
				else if (fluffleUpCount == 2) {
					// 2 回目は安置が必ず北側か南側の 2 か所になる
					if (coldDirs.contains(ArenaSector.NORTHEAST) && coldDirs.contains(ArenaSector.NORTHWEST)) {
						followupSafe.add(ArenaSector.SOUTH);
						s.setParam("safe", ArenaSector.NORTH);
						s.updateCall(coldFeatStart, e1);
					}
					else if (coldDirs.contains(ArenaSector.SOUTHEAST) && coldDirs.contains(ArenaSector.SOUTHWEST)) {
						followupSafe.add(ArenaSector.NORTH);
						s.setParam("safe", ArenaSector.SOUTH);
						s.updateCall(coldFeatStart, e1);
					}
					else {
						s.updateCall(coldFeatAvoidStart, e1);
					}
				}
				else if (fluffleUpCount > 2) {
					// 以降は両方の雑魚が出るので、繋がれた雑魚の位置がそのまま安置とは限らない。
					// 先に来る範囲を全部塗りつぶして残った 1 か所を安置とする。
					Set<ArenaSector> firstUnsafe = new HashSet<>();
					Set<ArenaSector> secondUnsafe = new HashSet<>();
					addCleaveDirs.forEach((id, dir) -> {
						if (tetheredAdds.contains(id)) {
							secondUnsafe.add(dir);
						}
						else {
							firstUnsafe.add(dir);
						}
					});
					List<ArenaSector> firstSafe = ArenaSector.quadrants.stream().filter(d -> !firstUnsafe.contains(d)).toList();
					List<ArenaSector> secondSafe = ArenaSector.quadrants.stream().filter(d -> !secondUnsafe.contains(d)).toList();
					if (firstSafe.size() == 1) {
						if (secondSafe.size() == 1) {
							followupSafe.addAll(secondSafe);
						}
						s.setParam("safe", firstSafe.get(0));
						s.updateCall(coldFeatStart, e1);
					}
					else {
						s.updateCall(coldFeatAvoidStart, e1);
					}
				}
				else {
					s.updateCall(coldFeatAvoidStart, e1);
				}

				s.waitMs(9_500);
				if (followupSafe.size() == 2) {
					s.setParam("safe1", followupSafe.get(0));
					s.setParam("safe2", followupSafe.get(1));
					s.updateCall(coldFeatFollowupTwo, e1);
				}
				else if (followupSafe.size() == 1) {
					s.setParam("safe", followupSafe.get(0));
					s.updateCall(coldFeatFollowup, e1);
				}
				else {
					s.updateCall(coldFeatAvoidFollowup, e1);
				}
				addCleaveDirs.clear();
				tetheredAdds.clear();
			});

	@NpcCastCallout(0x8F69)
	private final ModifiableCallout<AbilityCastStart> sparklingSprinkling = ModifiableCallout.durationBasedCall("Sparkling Sprinkling", "Avoid Lines, then Spread");

	// 2ボス目（Kahderyor）

	@NpcCastCallout(0x8DBA)
	private final ModifiableCallout<AbilityCastStart> windUnbound = ModifiableCallout.durationBasedCall("Wind Unbound", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> windShotHole = ModifiableCallout.durationBasedCall("Wind Shot (Before Crystalline Storm)", "Stack Donuts in Hole");
	private final ModifiableCallout<AbilityCastStart> windShotLines = ModifiableCallout.durationBasedCall("Wind Shot (After Crystalline Storm)", "Stack Donuts in Safe Lines");
	private final ModifiableCallout<AbilityCastStart> earthenShotHole = ModifiableCallout.durationBasedCall("Earthen Shot (Before Crystalline Storm)", "Spread, Away from Puddle");
	private final ModifiableCallout<AbilityCastStart> earthenShotLines = ModifiableCallout.durationBasedCall("Earthen Shot (After Crystalline Storm)", "Spread, Away from Lines");
	private final ModifiableCallout<AbilityCastStart> eyeOfTheFierceOut = ModifiableCallout.durationBasedCall("Eye of the Fierce + Stalagmite Circle", "Out + Look Away");
	private final ModifiableCallout<AbilityCastStart> eyeOfTheFierceIn = ModifiableCallout.durationBasedCall("Eye of the Fierce + Cyclonic Ring", "In + Look Away");

	@HandleEvents
	public void kahderyorCasts(EventContext context, AbilityCastStart event) {
		switch ((int) event.getAbility().getId()) {
			case 0x8DBC -> context.accept((seenCrystallineStorm ? windShotLines : windShotHole).getModified(event));
			case 0x8DBB -> context.accept((seenCrystallineStorm ? earthenShotLines : earthenShotHole).getModified(event));
			case 0x8DC5 -> context.accept(eyeOfTheFierceOut.getModified(event));
			case 0x8DC6 -> context.accept(eyeOfTheFierceIn.getModified(event));
		}
	}

	@NpcCastCallout(0x8DC3)
	private final ModifiableCallout<AbilityCastStart> seedCrystals = ModifiableCallout.durationBasedCall("Seed Crystals", "Spread, then Break Crystals");

	// 3ボス目（Gurfurlur）

	@NpcCastCallout(0x8DAD)
	private final ModifiableCallout<AbilityCastStart> heavingHaymaker = ModifiableCallout.durationBasedCall("Heaving Haymaker", "Raidwide");

	@NpcCastCallout(0x8DE0)
	private final ModifiableCallout<AbilityCastStart> enduringGlory = ModifiableCallout.durationBasedCall("Enduring Glory", "Big Raidwide");

	@NpcCastCallout(0x8DD9)
	private final ModifiableCallout<AbilityCastStart> sledgehammer = ModifiableCallout.durationBasedCall("Sledgehammer", "Stack - Multiple Hits");

	private final ModifiableCallout<AbilityUsedEvent> arcaneStomp = new ModifiableCallout<>("Arcane Stomp", "Absorb All Orbs", 20_000);

	@HandleEvents
	public void arcaneStompUsed(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x8DDF) && event.isFirstTarget()) {
			context.accept(arcaneStomp.getModified(event));
		}
	}

	// 1A が東の炎、1B が西の水、1D が中央の風。19 と 1C は炎と水が入れ替わった配置。
	private final ModifiableCallout<MapEffectEvent> firstWaterTile = new ModifiableCallout<>("First Water Tile", "Knockback from West", 7_000);
	private final ModifiableCallout<MapEffectEvent> firstFireTile = new ModifiableCallout<>("First Fire Tile", "Dodge toward Fire Crystal, then Spread", 9_000);
	private final ModifiableCallout<MapEffectEvent> fireWaterKbEast = new ModifiableCallout<>("Fire/Water Combo: Knockback from East", "Knockback from East to Fire Crystal, then Spread", 9_000);
	private final ModifiableCallout<MapEffectEvent> fireWaterKbWest = new ModifiableCallout<>("Fire/Water Combo: Knockback from West", "Knockback from West to Fire Crystal, then Spread", 9_000);
	private final ModifiableCallout<MapEffectEvent> windTileInitial = new ModifiableCallout<>("Wind Tile: Initial", "Knockback + AoE");
	private final ModifiableCallout<MapEffectEvent> windTileFollowup = new ModifiableCallout<>("Wind Tile: Followup", "Knockback + AoE, Avoid Tornadoes");

	private static boolean isTile(MapEffectEvent event, long... locations) {
		if (event.getFlags() != tileFlags) {
			return false;
		}
		for (long location : locations) {
			if (event.getLocation() == location) {
				return true;
			}
		}
		return false;
	}

	// 初回フラグは遅延後に立てる。同じ MapEffect で組み合わせ側の判定が先に通ってしまうのを防ぐため。
	@AutoFeed
	private final SequentialTrigger<BaseEvent> firstWaterTileSq = SqtTemplates.sq(5_000,
			MapEffectEvent.class, mee -> isTile(mee, 0x1B) && !seenFirstWaterTile,
			(e1, s) -> {
				s.waitMs(2_000);
				seenFirstWaterTile = true;
				s.updateCall(firstWaterTile, e1);
			});

	@AutoFeed
	private final SequentialTrigger<BaseEvent> firstFireTileSq = SqtTemplates.sq(5_000,
			MapEffectEvent.class, mee -> isTile(mee, 0x1A) && !seenFirstFireTile,
			(e1, s) -> {
				s.waitMs(2_000);
				seenFirstFireTile = true;
				s.updateCall(firstFireTile, e1);
			});

	@HandleEvents
	public void fireWaterCombo(EventContext context, MapEffectEvent event) {
		// 19/1C と 1A/1B は必ずペアで来るので片方だけ見る
		if (isTile(event, 0x19, 0x1A) && seenFirstWaterTile && seenFirstFireTile) {
			context.accept((event.getLocation() == 0x19 ? fireWaterKbEast : fireWaterKbWest).getModified(event));
		}
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> windTileSq = SqtTemplates.sq(25_000,
			MapEffectEvent.class, mee -> isTile(mee, 0x1D),
			(e1, s) -> {
				s.waitMs(4_000);
				s.updateCall(windTileInitial, e1);
				s.waitMs(16_400);
				s.updateCall(windTileFollowup, e1);
			});

}
