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
import gg.xp.xivsupport.events.actlines.events.CastLocationDataEvent;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.models.ArenaPos;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;

import java.util.ArrayList;
import java.util.List;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/dungeon/skydeep-cenote.ts をもとに移植
@CalloutRepo(name = "The Skydeep Cenote", duty = KnownDuty.SkydeepCenote)
public class SkydeepCenote extends AutoChildEventHandler implements FilteredEventHandler {

	private static final double FEATHER_RAY_CENTER_X = -105;
	private static final double FEATHER_RAY_CENTER_Y = -160;
	private static final double MAULSKULL_CENTER_X = 100;
	// 泡は 8 間隔の格子に並ぶ。外側の行・列はローリングカレント後の安置に関係しない
	private static final List<Long> BUBBLE_MIDDLE_ROWS_Y = List.of(-156L, -164L);
	private static final List<Long> BUBBLE_MIDDLE_COLS_X = List.of(-117L, -109L, -101L, -93L);
	private static final ArenaPos westBubbles = new ArenaPos(-113, FEATHER_RAY_CENTER_Y, 0, 0);
	private static final ArenaPos eastBubbles = new ArenaPos(-97, FEATHER_RAY_CENTER_Y, 0, 0);

	private XivState state;

	private volatile boolean collectBubbles;
	private volatile boolean seenMirrorManeuver;
	private volatile boolean seenDeepThunder;
	private volatile boolean seenShatter;

	public SkydeepCenote(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.SkydeepCenote);
	}

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		collectBubbles = false;
		seenMirrorManeuver = false;
		seenDeepThunder = false;
		seenShatter = false;
	}

	// フェザーレイ

	@NpcCastCallout(0x8F83)
	private final ModifiableCallout<AbilityCastStart> immersion = ModifiableCallout.durationBasedCall("Immersion", "Raidwide");

	@PlayerHeadmarker(0x0202)
	private final ModifiableCallout<HeadMarkerEvent> nuisanceWave = new ModifiableCallout<>("Nuisance Wave", "Look Away from Party");

	@NpcAbilityUsedCallout(value = 0x8F7C, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> blowingBubbles = new ModifiableCallout<>("Blowing Bubbles", "Avoid Bubbles");

	@NpcAbilityUsedCallout(value = 0x9783, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> troubleBubbles = new ModifiableCallout<>("Trouble Bubbles", "Avoid Bubbles");

	@HandleEvents
	public void bubbleBomb(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x8F7F)) {
			collectBubbles = true;
		}
	}

	private final ModifiableCallout<AbilityCastStart> rollingCurrentSafe = ModifiableCallout.durationBasedCall("Rolling Current (Safe Spot)", "Safe: {safe} (on Hitbox)");
	private final ModifiableCallout<AbilityCastStart> rollingCurrentAvoid = ModifiableCallout.durationBasedCall("Rolling Current (Unknown)", "Avoid Shifting Bubbles");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> rollingCurrentSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x8F80, 0x8F81),
			(e1, s) -> {
				// 8F80 は西から東へ、8F81 は東から西へ泡を押し流す
				boolean fromWest = e1.abilityIdMatches(0x8F80);
				boolean collected = collectBubbles;
				collectBubbles = false;
				if (!collected) {
					s.updateCall(rollingCurrentAvoid, e1);
					return;
				}
				s.waitThenRefreshCombatants(100);
				List<ArenaSector> safe = new ArrayList<>(ArenaSector.quadrants);
				for (XivCombatant cbt : state.getCombatantsListCopy()) {
					if (cbt.isPc()) {
						continue;
					}
					Position pos = cbt.getPos();
					if (pos == null) {
						continue;
					}
					long x = Math.round(pos.x());
					long y = Math.round(pos.y());
					// ボス自身も中央に位置が来るので、格子に一致するものだけを泡とみなす
					if (!BUBBLE_MIDDLE_ROWS_Y.contains(y) || !BUBBLE_MIDDLE_COLS_X.contains(x)) {
						continue;
					}
					boolean west = x < FEATHER_RAY_CENTER_X;
					if (west != fromWest) {
						continue;
					}
					safe.remove((west ? westBubbles : eastBubbles).forPosition(pos));
				}
				if (safe.size() == 1) {
					s.setParam("safe", safe.get(0));
					s.updateCall(rollingCurrentSafe, e1);
				}
				else {
					s.updateCall(rollingCurrentAvoid, e1);
				}
			});

	// ファイアアームズ

	@NpcCastCallout(0x8E60)
	private final ModifiableCallout<AbilityCastStart> dynamicDominance = ModifiableCallout.durationBasedCall("Dynamic Dominance", "Raidwide");

	@NpcCastCallout(0x8E5F)
	private final ModifiableCallout<AbilityCastStart> pummel = ModifiableCallout.durationBasedCall("Pummel", "Buster on {event.target}");

	private final ModifiableCallout<AbilityCastStart> mirrorManeuverFirst = ModifiableCallout.durationBasedCall("Mirror Maneuver (First)", "North, Away from Orb");
	private final ModifiableCallout<AbilityCastStart> mirrorManeuver = ModifiableCallout.durationBasedCall("Mirror Maneuver", "Close to North Orb");

	@HandleEvents
	public void mirrorManeuver(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8E5B)) {
			context.accept((seenMirrorManeuver ? mirrorManeuver : mirrorManeuverFirst).getModified(event));
			seenMirrorManeuver = true;
		}
	}

	@NpcAbilityUsedCallout(value = 0x8E5A, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> artillery = new ModifiableCallout<>("Artillery", "Avoid Exploding Rows/Columns");

	// モールスカル

	@NpcCastCallout(0x8F67)
	private final ModifiableCallout<AbilityCastStart> ashlayer = ModifiableCallout.durationBasedCall("Ashlayer", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> deepThunderFirst = ModifiableCallout.durationBasedCall("Deep Thunder (First)", "Stack (3 Hits)");
	private final ModifiableCallout<AbilityCastStart> deepThunder = ModifiableCallout.durationBasedCall("Deep Thunder", "Stack (5 Hits)");

	@HandleEvents
	public void deepThunder(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8F4F)) {
			context.accept((seenDeepThunder ? deepThunder : deepThunderFirst).getModified(event));
			seenDeepThunder = true;
		}
	}

	private final ModifiableCallout<AbilityCastStart> stonecarverEast = ModifiableCallout.durationBasedCall("Stonecarver (East First)", "Left then Right");
	private final ModifiableCallout<AbilityCastStart> stonecarverWest = ModifiableCallout.durationBasedCall("Stonecarver (West First)", "Right then Left");
	private final ModifiableCallout<AbilityCastStart> stonecarverSkullcrushEast = ModifiableCallout.durationBasedCall("Stonecarver + Skullcrush (East First)", "Knockback Back Left, then Right");
	private final ModifiableCallout<AbilityCastStart> stonecarverSkullcrushWest = ModifiableCallout.durationBasedCall("Stonecarver + Skullcrush (West First)", "Knockback Back Right, then Left");

	@HandleEvents
	public void stonecarver(EventContext context, CastLocationDataEvent event) {
		// 最初の半面攻撃は東西どちらの分身からも来るので、詠唱位置で判定する
		if (!event.abilityIdMatches(0x8F3E, 0x8F58)) {
			return;
		}
		Position pos = event.getPos();
		if (pos == null) {
			return;
		}
		boolean east = pos.x() > MAULSKULL_CENTER_X;
		ModifiableCallout<AbilityCastStart> call;
		if (event.abilityIdMatches(0x8F3E)) {
			call = east ? stonecarverEast : stonecarverWest;
		}
		else {
			call = east ? stonecarverSkullcrushEast : stonecarverSkullcrushWest;
		}
		context.accept(call.getModified(event.originalEvent()));
	}

	@NpcCastCallout(0x8F43)
	private final ModifiableCallout<AbilityCastStart> skullcrush = ModifiableCallout.durationBasedCall("Skullcrush (First)", "Knockback, then Spread");

	private final ModifiableCallout<AbilityUsedEvent> shatterMiddle = new ModifiableCallout<>("Maulwork + Shatter (Middle)", "Avoid AoEs, Sides After", 9_000);
	private final ModifiableCallout<AbilityUsedEvent> shatterMiddleFollowup = new ModifiableCallout<>("Maulwork + Shatter (Middle): Followup", "Sides");
	private final ModifiableCallout<AbilityUsedEvent> shatterSides = new ModifiableCallout<>("Maulwork + Shatter (Sides)", "Avoid AoEs, Middle After", 9_000);
	private final ModifiableCallout<AbilityUsedEvent> shatterSidesFollowup = new ModifiableCallout<>("Maulwork + Shatter (Sides): Followup", "Middle");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> maulworkSq = SqtTemplates.sq(15_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x8F47, 0x8F48, 0x8F49, 0x8F4A),
			(e1, s) -> {
				boolean middle = e1.abilityIdMatches(0x8F47, 0x8F48);
				s.updateCall(middle ? shatterMiddle : shatterSides, e1);
				s.waitMs(10_000);
				s.updateCall(middle ? shatterMiddleFollowup : shatterSidesFollowup, e1);
				seenShatter = true;
			});

	private final ModifiableCallout<AbilityCastStart> colossalImpactNE = ModifiableCallout.durationBasedCall("Colossal Impact (Northeast)", "Knockback to Northeast");
	private final ModifiableCallout<AbilityCastStart> colossalImpactNW = ModifiableCallout.durationBasedCall("Colossal Impact (Northwest)", "Knockback to Northwest");

	@HandleEvents
	public void colossalImpact(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8F60)) {
			context.accept(colossalImpactNE.getModified(event));
		}
		else if (event.abilityIdMatches(0x8F61)) {
			context.accept(colossalImpactNW.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> destructiveHeat = ModifiableCallout.durationBasedCall("Destructive Heat", "Spread After");
	private final ModifiableCallout<AbilityCastStart> buildingHeat = ModifiableCallout.durationBasedCall("Building Heat", "Stack After");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> heatSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x8F65, 0x8F66),
			(e1, s) -> {
				// 最初の単発は別途対処できるので、シャッターと重なる時だけ知らせる
				if (!seenShatter) {
					return;
				}
				s.updateCall(e1.abilityIdMatches(0x8F65) ? destructiveHeat : buildingHeat, e1);
				// 複数人対象の詠唱が同時に来るため、まとめて 1 回にする
				s.waitMs(1_000);
			});
}
