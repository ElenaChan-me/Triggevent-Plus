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
import gg.xp.xivsupport.events.actlines.events.RawAddCombatantEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/trial/emerald_weapon-ex.ts をもとに移植
@CalloutRepo(name = "Castrum Marinum Extreme", duty = KnownDuty.CastrumMarinumExtreme)
public class EmeraldWeaponEx extends AutoChildEventHandler implements FilteredEventHandler {

	private static final double CENTER_X = 100;
	private static final double CENTER_Y = 100;
	private static final long NITROSPHERE_NAME_ID = 9706;
	private static final int ORB_COUNT = 4;
	private static final int DIRECTION_COUNT = 8;
	private static final int WEST_INDEX = 6;
	private static final int NORTHWEST_INDEX = 7;
	private static final int SWORD_COUNT = 4;
	// 中央寄りに落ちる剣の判定範囲
	private static final double SWORD_MIDDLE_RANGE = 10;

	private XivState state;

	public EmeraldWeaponEx(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.CastrumMarinumExtreme);
	}

	private boolean seenMines;
	private final List<Position> orbs = new ArrayList<>();
	private final List<XivCombatant> primusPlayers = new ArrayList<>();
	private final List<ArenaSector> tertius = new ArrayList<>();

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		seenMines = false;
		orbs.clear();
		primusPlayers.clear();
		tertius.clear();
	}

	@NpcCastCallout(0x55B0)
	private final ModifiableCallout<AbilityCastStart> emeraldShot = ModifiableCallout.durationBasedCall("Emerald Shot", "Buster on {event.target}");

	@NpcCastCallout({0x55B1, 0x5B10})
	private final ModifiableCallout<AbilityCastStart> optimizedUltima = ModifiableCallout.durationBasedCall("Optimized Ultima", "Raidwide");

	@NpcCastCallout(0x55AA)
	private final ModifiableCallout<AbilityCastStart> aetheroplasmProduction = ModifiableCallout.durationBasedCall("Aetheroplasm Production", "Get Orbs");

	private final ModifiableCallout<RawAddCombatantEvent> aetheroplasmClockwise = new ModifiableCallout<>("Aetheroplasm: Clockwise", "Rotate Clockwise");
	private final ModifiableCallout<RawAddCombatantEvent> aetheroplasmCounterclockwise = new ModifiableCallout<>("Aetheroplasm: Counterclockwise", "Rotate Counterclockwise");

	@HandleEvents
	public void aetheroplasmRotate(EventContext context, RawAddCombatantEvent event) {
		XivCombatant cbt = event.getEntity();
		if (cbt.getbNpcNameId() != NITROSPHERE_NAME_ID) {
			return;
		}
		Position pos = cbt.getPos();
		if (pos == null) {
			return;
		}
		orbs.add(pos);
		if (orbs.size() != ORB_COUNT) {
			return;
		}
		boolean[] isNitro = new boolean[DIRECTION_COUNT];
		for (Position orb : orbs) {
			double x = orb.x() - CENTER_X;
			double y = orb.y() - CENTER_Y;
			// 北を0として時計回りに8方向へ割り当てる
			int dir = (int) (Math.round(4 - 4 * Math.atan2(x, y) / Math.PI) % DIRECTION_COUNT);
			if (isNitro[dir]) {
				return;
			}
			isNitro[dir] = true;
		}
		// 配置は2パターンしかないので、西側だけ見れば回る向きが決まる
		context.accept((isNitro[WEST_INDEX] == isNitro[NORTHWEST_INDEX] ? aetheroplasmCounterclockwise : aetheroplasmClockwise).getModified(event));
	}

	@NpcCastCallout({0x558F, 0x55D0})
	private final ModifiableCallout<AbilityCastStart> aireTamStorm = ModifiableCallout.durationBasedCall("Aire Tam Storm", "Away from Red Circle");

	private final ModifiableCallout<AbilityCastStart> magitekMagnetism = new ModifiableCallout<>("Magitek Magnetism", "Get Near Same Polarity Mines", 6_000);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> magitekMagnetismSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x5594),
			(e1, s) -> {
				s.waitMs(9_000);
				// 1回目はタンクがフレア処理をしているので鳴らさない
				if (seenMines || !state.playerJobMatches(Job::isTank)) {
					s.updateCall(magitekMagnetism, e1);
				}
				seenMines = true;
			});

	private final ModifiableCallout<AbilityCastStart> divideP1Tank = ModifiableCallout.durationBasedCall("Divide Et Impera (P1): Tank", "Tank Stack");
	private final ModifiableCallout<AbilityCastStart> divideP1Other = ModifiableCallout.durationBasedCall("Divide Et Impera (P1): Non-Tank", "Spread");
	private final ModifiableCallout<AbilityCastStart> divideP2Tank = ModifiableCallout.durationBasedCall("Divide Et Impera (P2): Tank", "Tank Stack");
	private final ModifiableCallout<AbilityCastStart> divideP2Other = ModifiableCallout.durationBasedCall("Divide Et Impera (P2): Non-Tank", "Protean");

	@HandleEvents
	public void divideEtImpera(EventContext context, AbilityCastStart event) {
		boolean tank = state.playerJobMatches(Job::isTank);
		if (event.abilityIdMatches(0x5537)) {
			context.accept((tank ? divideP1Tank : divideP1Other).getModified(event));
		}
		else if (event.abilityIdMatches(0x555B)) {
			context.accept((tank ? divideP2Tank : divideP2Other).getModified(event));
		}
	}

	@PlayerHeadmarker(0x57)
	private final ModifiableCallout<HeadMarkerEvent> magnetismFlare = new ModifiableCallout<>("Magitek Magnetism: Flare", "Flare on You");

	@PlayerHeadmarker(0x17)
	private final ModifiableCallout<HeadMarkerEvent> magnetismBait = new ModifiableCallout<>("Magitek Magnetism: Bait", "Bait Lines Away from Safe Spot");

	@NpcCastCallout({0x55D1, 0x5591})
	private final ModifiableCallout<AbilityCastStart> expire = ModifiableCallout.durationBasedCall("Expire", "Out");

	private final ModifiableCallout<HeadMarkerEvent> primusTerminusEst = new ModifiableCallout<>("Primus Terminus Est", "Go {dir}, Aim Across");

	@HandleEvents
	public void primusTerminusEst(EventContext context, HeadMarkerEvent event) {
		ArenaSector dir;
		if (event.markerIdMatches(0xF9)) {
			dir = ArenaSector.SOUTH;
		}
		else if (event.markerIdMatches(0xFA)) {
			dir = ArenaSector.WEST;
		}
		else if (event.markerIdMatches(0xFB)) {
			dir = ArenaSector.NORTH;
		}
		else if (event.markerIdMatches(0xFC)) {
			dir = ArenaSector.EAST;
		}
		else {
			return;
		}
		primusPlayers.add(event.getTarget());
		if (event.getTarget().isThePlayer()) {
			context.accept(primusTerminusEst.getModified(event, Map.<String, Object>of("dir", dir)));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> primusDodge = new ModifiableCallout<>("Primus Terminus Est: No Marker", "Dodge Arrow Lines");
	private final RepeatSuppressor primusSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> primusDodgeSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(0xF9, 0xFA, 0xFB, 0xFC) && primusSupp.check(hm),
			(e1, s) -> {
				// 全員分のマーカーが付くのを待ってから判定する
				s.waitMs(500);
				if (primusPlayers.stream().noneMatch(XivCombatant::isThePlayer)) {
					s.updateCall(primusDodge, e1);
				}
				primusPlayers.clear();
			});

	private final ModifiableCallout<AbilityCastStart> tertiusTerminusEst = ModifiableCallout.durationBasedCall("Tertius Terminus Est", "Watch for Swords");

	@HandleEvents
	public void tertiusCast(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x55CC)) {
			tertius.clear();
			context.accept(tertiusTerminusEst.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> tertiusMiddleFirst = new ModifiableCallout<>("Tertius Terminus Est: Middle First", "{middle}, then {dir1}/{dir2}", 9_000);
	private final ModifiableCallout<AbilityUsedEvent> tertiusMiddleLast = new ModifiableCallout<>("Tertius Terminus Est: Middle Last", "{dir1}/{dir2}, then {middle}", 9_000);

	// 詠唱開始時の位置は移動直後だと不正確なので、着弾した剣の位置から残り2本を予測する
	@HandleEvents
	public void tertiusSwords(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x55CD) || !event.isFirstTarget()) {
			return;
		}
		XivCombatant source = event.getSource();
		Position pos = source.getPos();
		if (pos == null) {
			pos = state.getLatestCombatantData(source).getPos();
		}
		if (pos == null) {
			return;
		}
		tertius.add(swordSector(pos));
		if (tertius.size() != SWORD_COUNT) {
			return;
		}
		// 剣は四隅に1本ずつと中央に2本落ちるので、まだ来ていない場所が残りの2本になる
		List<ArenaSector> remaining = new ArrayList<>(List.of(
				ArenaSector.NORTHEAST,
				ArenaSector.NORTHWEST,
				ArenaSector.SOUTHEAST,
				ArenaSector.SOUTHWEST,
				ArenaSector.CENTER,
				ArenaSector.CENTER));
		remaining.removeIf(tertius::contains);
		if (remaining.size() < 2) {
			return;
		}
		ArenaSector s0 = tertius.get(0);
		ArenaSector s1 = tertius.get(1);
		ArenaSector s4 = remaining.get(0);
		ArenaSector s5 = remaining.get(1);
		// 2本ずつの組は四隅の2本か中央の2本で、2組目が中央になることはない
		if (s0 != s1) {
			context.accept(tertiusMiddleFirst.getModified(event, Map.<String, Object>of("middle", s4, "dir1", s0, "dir2", s1)));
		}
		else {
			context.accept(tertiusMiddleLast.getModified(event, Map.<String, Object>of("middle", s0, "dir1", s4, "dir2", s5)));
		}
	}

	private static ArenaSector swordSector(Position pos) {
		double x = pos.x() - CENTER_X;
		double y = pos.y() - CENTER_Y;
		if (Math.abs(x) < SWORD_MIDDLE_RANGE && Math.abs(y) < SWORD_MIDDLE_RANGE) {
			return ArenaSector.CENTER;
		}
		if (x < 0) {
			return y < 0 ? ArenaSector.NORTHWEST : ArenaSector.SOUTHWEST;
		}
		return y < 0 ? ArenaSector.NORTHEAST : ArenaSector.SOUTHEAST;
	}

	@NpcCastCallout(0x55D5)
	private final ModifiableCallout<AbilityCastStart> sidescatheLeft = ModifiableCallout.durationBasedCall("Sidescathe (Left)", "Left");

	@NpcCastCallout(0x55D4)
	private final ModifiableCallout<AbilityCastStart> sidescatheRight = ModifiableCallout.durationBasedCall("Sidescathe (Right)", "Right");

	@NpcCastCallout(0x55D6)
	private final ModifiableCallout<AbilityCastStart> emeraldCrusher = ModifiableCallout.durationBasedCall("Emerald Crusher", "Knockback");

	@PlayerHeadmarker(0xFD)
	private final ModifiableCallout<HeadMarkerEvent> secundusPlus = new ModifiableCallout<>("Secundus Terminus Est (Plus)", "Intercard and Out (Plus)");

	@PlayerHeadmarker(0xFE)
	private final ModifiableCallout<HeadMarkerEvent> secundusCross = new ModifiableCallout<>("Secundus Terminus Est (Cross)", "Cardinal and Out (Cross)");

	@NpcCastCallout(0x55BE)
	private final ModifiableCallout<AbilityCastStart> magitekCannon = ModifiableCallout.durationBasedCall("Magitek Cannon", "Middle");

	@NpcCastCallout(0x55C0)
	private final ModifiableCallout<AbilityCastStart> fullRank = ModifiableCallout.durationBasedCall("Full Rank", "Go North, Dodge Soldiers/Divebombs");

}
