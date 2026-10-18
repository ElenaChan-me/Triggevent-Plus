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
import gg.xp.xivsupport.events.actlines.events.DescribesCastLocation;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.MapEffectEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
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

import java.util.ArrayList;
import java.util.List;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/trial/enuo.ts をもとに移植
@CalloutRepo(name = "The Unmaking (Enuo)", duty = KnownDuty.TheUnmaking)
public class TheUnmaking extends AutoChildEventHandler implements FilteredEventHandler {

	private static final double CENTER_X = 100;
	private static final double CENTER_Y = 100;
	// 中央判定の許容誤差
	private static final double MIDDLE_TOLERANCE = 1;

	// ArenaSector は 8 方向しか持たないので 16 方向は文字列で出す
	private static final String[] DIRS_16 = {
			"North", "North-northeast", "Northeast", "East-northeast",
			"East", "East-southeast", "Southeast", "South-southeast",
			"South", "South-southwest", "Southwest", "West-southwest",
			"West", "West-northwest", "Northwest", "North-northwest"
	};

	private XivState state;
	private ActiveCastRepository acr;

	private boolean beaconCalled;

	public TheUnmaking(XivState state, ActiveCastRepository acr) {
		this.state = state;
		this.acr = acr;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheUnmaking);
	}

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		beaconCalled = false;
	}

	private static int xyTo8DirNum(double x, double y) {
		return (int) (Math.round(4 - 4 * Math.atan2(x - CENTER_X, y - CENTER_Y) / Math.PI) % 8);
	}

	private static boolean isMiddle(Position pos) {
		return Math.abs(pos.x() - CENTER_X) < MIDDLE_TOLERANCE && Math.abs(pos.y() - CENTER_Y) < MIDDLE_TOLERANCE;
	}

	private @Nullable Position castPos(SequentialTriggerController<?> s, AbilityCastStart cast) {
		DescribesCastLocation<AbilityCastStart> li = s.waitForCastLocation(cast);
		Position pos = li.getPos();
		if (pos != null) {
			return pos;
		}
		return state.getLatestCombatantData(cast.getSource()).getPos();
	}

	@NpcCastCallout(0xC333)
	private final ModifiableCallout<AbilityCastStart> meteorain = ModifiableCallout.durationBasedCall("Meteorain", "Raidwide");

	@NpcCastCallout(0xC308)
	private final ModifiableCallout<AbilityCastStart> almagest = ModifiableCallout.durationBasedCall("Almagest", "Raidwide");

	@NpcCastCallout(0xC327)
	private final ModifiableCallout<AbilityCastStart> lightlessWorld = ModifiableCallout.durationBasedCall("Lightless World", "Big Raidwide");

	private final ModifiableCallout<AbilityCastStart> naughtGrowsAway = ModifiableCallout.durationBasedCall("Naught Grows: Single", "Away from {dir}");
	private final ModifiableCallout<AbilityCastStart> naughtGrowsAwayOut = ModifiableCallout.durationBasedCall("Naught Grows: One Under Boss", "Away from {dir}, Out");
	private final ModifiableCallout<AbilityCastStart> naughtGrowsOpposite = ModifiableCallout.durationBasedCall("Naught Grows: Opposite", "{safe1}/{safe2} Max Melee");
	private final ModifiableCallout<AbilityCastStart> naughtGrowsOverlap = ModifiableCallout.durationBasedCall("Naught Grows: Overlap", "{safe} Max Melee");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> naughtGrowsSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xC30D, 0xC30E),
			(e1, s) -> {
				// 1 個か 2 個かは決まっていないので、少し待ってから揃っているものを全部見る
				s.waitMs(500);
				List<AbilityCastStart> casts = new ArrayList<>(acr.getActiveCastsById(0xC30D, 0xC30E)
						.stream().map(CastTracker::getCast).toList());
				if (!casts.contains(e1)) {
					casts.add(0, e1);
				}
				Position pos1 = castPos(s, casts.get(0));
				if (pos1 == null) {
					return;
				}
				if (casts.size() < 2) {
					s.setParam("dir", isMiddle(pos1) ? ArenaSector.CENTER : ArenaSector.all.get(xyTo8DirNum(pos1.x(), pos1.y())));
					s.updateCall(naughtGrowsAway, e1);
					return;
				}
				Position pos2 = castPos(s, casts.get(1));
				if (pos2 == null) {
					return;
				}
				if (isMiddle(pos1) || isMiddle(pos2)) {
					Position away = isMiddle(pos1) ? pos2 : pos1;
					s.setParam("dir", ArenaSector.all.get(xyTo8DirNum(away.x(), away.y())));
					s.updateCall(naughtGrowsAwayOut, e1);
					return;
				}
				int dir1 = xyTo8DirNum(pos1.x(), pos1.y());
				int dir2 = xyTo8DirNum(pos2.x(), pos2.y());
				if (dir1 % 4 == dir2 % 4) {
					// 向かい合わせなら、両方から90度ずれた2か所が安置
					s.setParam("safe1", ArenaSector.all.get((dir1 + 2) % 8));
					s.setParam("safe2", ArenaSector.all.get((dir2 + 2) % 8));
					s.updateCall(naughtGrowsOpposite, e1);
					return;
				}
				// 重なっている場合は、隙間が 16 方向の間の位置になる
				int safe8 = (dir1 + 3) % 8 == dir2 ? dir2 + 2 : dir1 + 2;
				int safe16 = (safe8 * 2 + 1) % 16;
				s.setParam("safe", DIRS_16[safe16]);
				s.updateCall(naughtGrowsOverlap, e1);
			});

	private final ModifiableCallout<AbilityCastStart> gazeOfTheVoidCw = ModifiableCallout.durationBasedCall("Gaze of the Void (Clockwise)", "Start {start}, Clockwise to {end}");
	private final ModifiableCallout<AbilityCastStart> gazeOfTheVoidCcw = ModifiableCallout.durationBasedCall("Gaze of the Void (Counter-clockwise)", "Start {start}, Counter-clockwise to {end}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> gazeOfTheVoidSq = SqtTemplates.sq(30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xC31F, 0xC320),
			(e1, s) -> {
				boolean clockwise = e1.abilityIdMatches(0xC31F);
				AbilityCastStart cone = s.findOrWaitForCastWithLocation(acr, acs -> acs.abilityIdMatches(0xC321));
				ArenaSector coneDir = ArenaPos.combatantFacing(s.waitForCastLocation(cone).getBestHeading());
				if (clockwise) {
					s.setParam("start", coneDir.plusEighths(-1));
					s.setParam("end", coneDir.plusEighths(2));
					s.updateCall(gazeOfTheVoidCw, cone);
				}
				else {
					s.setParam("start", coneDir.plusEighths(1));
					s.setParam("end", coneDir.plusEighths(-2));
					s.updateCall(gazeOfTheVoidCcw, cone);
				}
			});

	private final ModifiableCallout<AbilityCastStart> deepFreezeOnYou = ModifiableCallout.durationBasedCall("Deep Freeze: Flare on You", "Tank Flare on You");
	private final ModifiableCallout<AbilityCastStart> deepFreezeNotOnYou = ModifiableCallout.durationBasedCall("Deep Freeze: Flare not on You", "Away from Tank Flares");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> deepFreezeSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xC32E),
			(e1, s) -> {
				// 2 人分の詠唱が揃うのを待つ
				s.waitMs(100);
				boolean onYou = e1.getTarget().isThePlayer()
				                || acr.getActiveCastsById(0xC32E).stream().anyMatch(ct -> ct.getCast().getTarget().isThePlayer());
				s.updateCall(onYou ? deepFreezeOnYou : deepFreezeNotOnYou, e1);
			});

	@NpcCastCallout(0xC32F)
	private final ModifiableCallout<AbilityCastStart> shroudedHoly = ModifiableCallout.durationBasedCall("Shrouded Holy", "Stack on {event.target}");

	private final ModifiableCallout<HeadMarkerEvent> dimensionZero = new ModifiableCallout<>("Dimension Zero", "Stack on {event.target}", 7_700);

	@HandleEvents
	public void headMarkers(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x2CF)) {
			context.accept(dimensionZero.getModified(event));
		}
	}

	private final ModifiableCallout<TetherEvent> naughtHunts = new ModifiableCallout<>("Naught Hunts", "Chasing AoE on You", 13_800);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> naughtHuntsSq = SqtTemplates.sq(10_000,
			TetherEvent.class, te -> te.tetherIdMatches(0x194, 0x195) && te.eitherTargetMatches(XivCombatant::isThePlayer),
			(e1, s) -> {
				s.updateCall(naughtHunts, e1);
				// 同じ対象に複数回線が付くのでまとめる
				s.waitMs(8_000);
			});

	@NpcCastCallout(0xC32A)
	private final ModifiableCallout<AbilityCastStart> meltdown = ModifiableCallout.durationBasedCall("Meltdown", "Bait Puddles then Spread");

	@NpcCastCallout(value = 0xC317, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> silentTorrent = ModifiableCallout.durationBasedCall("Silent Torrent", "Avoid Line Ends");

	@NpcCastCallout(value = 0xC33D, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> loomingEmptiness = ModifiableCallout.durationBasedCall("Looming Emptiness", "Away from Proximity Marker");

	private final ModifiableCallout<MapEffectEvent> beaconSpawn = new ModifiableCallout<>("Light Beacon Spawn", "Kill Light Beacon");

	@HandleEvents
	public void mapEffects(EventContext context, MapEffectEvent event) {
		if (!beaconCalled && event.getFlags() == 0x0002_0001 && event.getLocation() == 3) {
			beaconCalled = true;
			context.accept(beaconSpawn.getModified(event));
		}
	}

}
