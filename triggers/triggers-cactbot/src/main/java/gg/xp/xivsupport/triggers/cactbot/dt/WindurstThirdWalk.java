package gg.xp.xivsupport.triggers.cactbot.dt;

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
import gg.xp.xivsupport.events.actlines.events.DescribesCastLocation;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.MapEffectEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.state.combatstate.ActiveCastRepository;
import gg.xp.xivsupport.events.state.combatstate.CastTracker;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;
import gg.xp.xivsupport.models.XivPlayerCharacter;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/alliance/windurst-third-walk.ts をもとに移植
@CalloutRepo(name = "Windurst The Third Walk", duty = KnownDuty.WindurstTheThirdWalk)
public class WindurstThirdWalk extends AutoChildEventHandler implements FilteredEventHandler {

	// 同時に詠唱される設置物の位置がそろうまでの待ち時間
	private static final int LOCATION_WAIT_MS = 500;
	// この範囲内に詠唱開始したものを同じセットとみなす
	private static final long SAME_SET_MS = 1_000;
	// 円形範囲は最初の青魔法までに3回、以降は5回ある
	private static final int FIRE_LIMIT_FIRST = 4;
	private static final int FIRE_LIMIT_LATER = 6;
	private static final int STONE_DURATION_MS = 7_000;
	private static final Duration SHOCKWAVE_LEAD = Duration.ofSeconds(5);
	// ヘッドマーカーには詠唱時間がないので、発動の4秒ほど前まで待つ
	private static final int ARROW_DELAY_MS = 6_000;
	// 11秒前に使われるので、残り5秒で知らせる
	private static final int WHEEL_BASTION_DELAY_MS = 6_000;
	private static final int STATE_LINGER_MS = 5_000;
	private static final int POST_INTERMISSION_FIRST_DELAY_MS = 500;
	// 前のセットのコールと重ならないように遅らせる
	private static final int POST_INTERMISSION_LATER_DELAY_MS = 1_600;
	private static final int EXPLOSION_SETS = 3;

	private static final double ALEX_CENTER_X = 0;
	private static final double ALEX_CENTER_Y = 360;
	private static final double ALEX_NORTH_EDGE_Y = 345;
	private static final double HEADING_SPLIT = 1.5;

	private static final double PROMATHIA_CENTER_X = -820;
	private static final double PROMATHIA_PLATFORM_SPLIT_Y = -818;

	private XivState state;
	private final ActiveCastRepository casts;

	public WindurstThirdWalk(XivState state, ActiveCastRepository casts) {
		this.state = state;
		this.casts = casts;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.WindurstTheThirdWalk);
	}

	private boolean leyCollectionActive;
	private boolean seenFirstBlizzard;
	private int fireCount;
	private final Deque<ArenaSector> fireLocations = new ArrayDeque<>();
	private boolean penanceActive;
	private boolean wheelBastionActive;
	private boolean seenPromathiaIntermission;
	private boolean earthboundHeavenActive;
	private int multiExplosionCounter;
	// null は未発動
	private Boolean blessingRightSafe;
	private Boolean lightAspect;
	private boolean tookTrailTower;
	private boolean cometOnPlayer;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		leyCollectionActive = false;
		seenFirstBlizzard = false;
		fireCount = 0;
		fireLocations.clear();
		penanceActive = false;
		wheelBastionActive = false;
		seenPromathiaIntermission = false;
		earthboundHeavenActive = false;
		multiExplosionCounter = 0;
		blessingRightSafe = null;
		lightAspect = null;
		tookTrailTower = false;
		cometOnPlayer = false;
	}

	// 共通

	private final ModifiableCallout<HeadMarkerEvent> superNova = new ModifiableCallout<>("Final Exam / Super Nova", "Stack on {event.target}");

	@HandleEvents
	public void superNova(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x131)) {
			context.accept(superNova.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> tankCleaveOnYou = new ModifiableCallout<>("Vidohunir / Pinning Shot / Divine Bolt (On You)", "Tank Cleave on You");
	private final ModifiableCallout<HeadMarkerEvent> tankCleave = new ModifiableCallout<>("Vidohunir / Pinning Shot / Divine Bolt (Not on You)", "Avoid Tank Cleave");

	@HandleEvents
	public void tankCleave(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x23A, 0x1F2, 0x1D7)) {
			context.accept((event.getTarget().isThePlayer() ? tankCleaveOnYou : tankCleave).getModified(event));
		}
	}

	// シャントット

	@NpcCastCallout({0xC427, 0xC411})
	private final ModifiableCallout<AbilityCastStart> shantottoRaidwide = ModifiableCallout.durationBasedCall("Flare Play / Superior Stone / Banishga", "Raidwide");

	@NpcCastCallout(0xC41E)
	private final ModifiableCallout<AbilityCastStart> empiricalResearch = ModifiableCallout.durationBasedCall("Empirical Research", "Away from Front");

	private final ModifiableCallout<MapEffectEvent> stoneEastClose = new ModifiableCallout<>("Superior Stone Crusher (East Close)", "East, Get Close", STONE_DURATION_MS);
	private final ModifiableCallout<MapEffectEvent> stoneWestClose = new ModifiableCallout<>("Superior Stone Crusher (West Close)", "West, Get Close", STONE_DURATION_MS);
	private final ModifiableCallout<MapEffectEvent> stoneWestFar = new ModifiableCallout<>("Superior Stone Crusher (West Far)", "West, Get Far", STONE_DURATION_MS);
	private final ModifiableCallout<MapEffectEvent> stoneEastFar = new ModifiableCallout<>("Superior Stone Crusher (East Far)", "East, Get Far", STONE_DURATION_MS);

	@HandleEvents
	public void superiorStone(EventContext context, MapEffectEvent event) {
		// 閉じる直前の予兆の場所で安置が決まる
		if (!event.flagsMatches(0x00800040)) {
			return;
		}
		long loc = event.getLocation();
		ModifiableCallout<MapEffectEvent> call;
		if (loc == 0x3E) {
			call = stoneEastClose;
		}
		else if (loc == 0x40) {
			call = stoneWestClose;
		}
		else if (loc == 0x41) {
			call = stoneWestFar;
		}
		else if (loc == 0x42) {
			call = stoneEastFar;
		}
		else {
			return;
		}
		context.accept(call.getModified(event));
	}

	@HandleEvents
	public void leyCollectionStart(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0xC418)) {
			leyCollectionActive = true;
		}
		else if (event.abilityIdMatches(0xC419, 0xC41A)) {
			fireCount++;
		}
	}

	private static ArenaSector cardinalFrom(Position from, Position to) {
		double dx = to.getX() - from.getX();
		double dy = to.getY() - from.getY();
		if (Math.abs(dx) > Math.abs(dy)) {
			return dx > 0 ? ArenaSector.EAST : ArenaSector.WEST;
		}
		return dy > 0 ? ArenaSector.SOUTH : ArenaSector.NORTH;
	}

	// 線の始点から見た終点の方角が、次にファイアが来る場所になる
	@HandleEvents
	public void leyLineTether(EventContext context, TetherEvent event) {
		if (!leyCollectionActive || !event.tetherIdMatches(0x17F, 0x180)) {
			return;
		}
		Position from = state.getLatestCombatantData(event.getSource()).getPos();
		Position to = state.getLatestCombatantData(event.getTarget()).getPos();
		if (from == null || to == null) {
			fireLocations.add(ArenaSector.UNKNOWN);
			return;
		}
		fireLocations.add(cardinalFrom(from, to));
	}

	@NpcCastCallout(0xC419)
	private final ModifiableCallout<AbilityCastStart> circumscribedFireInitial = ModifiableCallout.durationBasedCall("Circumscribed Fire (Initial)", "Get Under");

	private final ModifiableCallout<AbilityUsedEvent> circumscribedFireNext = new ModifiableCallout<>("Circumscribed Fire (Next)", "Move {dir}, Get Under");
	private final ModifiableCallout<AbilityUsedEvent> circumscribedFireUnknown = new ModifiableCallout<>("Circumscribed Fire (Next, Unknown)", "Move to Next Circle");
	private final ModifiableCallout<AbilityUsedEvent> localizedBlizzard = new ModifiableCallout<>("Localized Blizzard", "Out");
	private final RepeatSuppressor fireSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	private int fireLimit() {
		return seenFirstBlizzard ? FIRE_LIMIT_LATER : FIRE_LIMIT_FIRST;
	}

	// ブリザドの詠唱は短すぎるので、ファイアの回数から判断する
	@AutoFeed
	private final SequentialTrigger<BaseEvent> circumscribedFireSq = SqtTemplates.sq(5_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0xC419, 0xC41A) && fireSupp.check(aue),
			(e1, s) -> {
				if (fireCount < fireLimit()) {
					ArenaSector next = fireLocations.poll();
					if (next == null || next == ArenaSector.UNKNOWN) {
						s.updateCall(circumscribedFireUnknown, e1);
					}
					else {
						s.updateCall(circumscribedFireNext.getModified(e1, Map.<String, Object>of("dir", next)));
					}
				}
				else if (e1.abilityIdMatches(0xC41A) && fireCount == fireLimit()) {
					leyCollectionActive = false;
					seenFirstBlizzard = true;
					fireCount = 0;
					fireLocations.clear();
					s.waitMs(LOCATION_WAIT_MS);
					s.updateCall(localizedBlizzard, e1);
				}
			});

	@PlayerHeadmarker(0x22E)
	private final ModifiableCallout<HeadMarkerEvent> thunderAndError = new ModifiableCallout<>("Thunder and Error", "Spread");

	@NpcCastCallout(value = 0xC40A, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> largeSpecimen = ModifiableCallout.durationBasedCall("Large Specimen", "Avoid Proximity AoEs");

	private final ModifiableCallout<HeadMarkerEvent> stardustSpecimen = new ModifiableCallout<>("Stardust Specimen", "Stack on {event.target}");

	@HandleEvents
	public void stardustSpecimen(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x13E)) {
			context.accept(stardustSpecimen.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> shockwave = ModifiableCallout.durationBasedCall("Shockwave", "Raidwide");

	// 詠唱が長いので、発動の5秒前まで待つ
	@AutoFeed
	private final SequentialTrigger<BaseEvent> shockwaveSq = SqtTemplates.sq(30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xC40B),
			(e1, s) -> {
				Duration wait = e1.getInitialDuration().minus(SHOCKWAVE_LEAD);
				if (!wait.isNegative()) {
					s.waitDuration(wait);
				}
				s.updateCall(shockwave, e1);
			});

	@PlayerStatusCallout(0x1516)
	private final ModifiableCallout<BuffApplied> easterlyWinds = new ModifiableCallout<>("Easterly Winds", "Knockback West into Wall");

	@PlayerStatusCallout(0x1517)
	private final ModifiableCallout<BuffApplied> westerlyWinds = new ModifiableCallout<>("Westerly Winds", "Knockback East into Wall");

	// 道中

	@NpcCastCallout({0xC3AC, 0xC3B7, 0xC3B4})
	private final ModifiableCallout<AbilityCastStart> trashRaidwide = ModifiableCallout.durationBasedCall("Fulmination Khalkeos / Bellowing Grunt / Disregard", "Raidwide");

	@NpcCastCallout(0xC3B3)
	private final ModifiableCallout<AbilityCastStart> leftShadowSlash = ModifiableCallout.durationBasedCall("Left Shadow Slash", "Right");

	@NpcCastCallout(0xC3B2)
	private final ModifiableCallout<AbilityCastStart> rightShadowSlash = ModifiableCallout.durationBasedCall("Right Shadow Slash", "Left");

	@NpcCastCallout(0xC3B6)
	private final ModifiableCallout<AbilityCastStart> petrifaction = ModifiableCallout.durationBasedCall("Petrifaction", "Look Away");

	// アレキサンダー

	@PlayerHeadmarker(0xD7)
	private final ModifiableCallout<HeadMarkerEvent> banishga = new ModifiableCallout<>("Banishga (Spread)", "Spread");

	private final ModifiableCallout<HeadMarkerEvent> divineArrowCw = new ModifiableCallout<>("Divine Arrow (Clockwise)", "Get Behind => Clockwise");
	private final ModifiableCallout<HeadMarkerEvent> divineArrowCcw = new ModifiableCallout<>("Divine Arrow (Counterclockwise)", "Get Behind => Counterclockwise");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> divineArrowSq = SqtTemplates.sq(10_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(0x2B3, 0x2B4, 0x2B5, 0x2B6, 0x2B7, 0x2B8, 0x2B9, 0x2BA),
			(e1, s) -> {
				s.waitMs(ARROW_DELAY_MS);
				boolean cw = e1.markerIdMatches(0x2B3, 0x2B4, 0x2B5, 0x2B6);
				s.updateCall(cw ? divineArrowCw : divineArrowCcw, e1);
			});

	private final ModifiableCallout<AbilityCastStart> divineSpearPinwheelCw = ModifiableCallout.durationBasedCall("Divine Spear (Pinwheel Clockwise)", "Pinwheel: Lean Clockwise, Close");
	private final ModifiableCallout<AbilityCastStart> divineSpearPinwheelCcw = ModifiableCallout.durationBasedCall("Divine Spear (Pinwheel Counterclockwise)", "Pinwheel: Lean Counterclockwise, Close");
	private final ModifiableCallout<AbilityCastStart> divineSpearSafe = ModifiableCallout.durationBasedCall("Divine Spear (Arrow)", "Go {dir}, Close");
	private final ModifiableCallout<AbilityCastStart> divineSpearUnknown = ModifiableCallout.durationBasedCall("Divine Spear (Unknown)", "Avoid Spear Triangles");
	private final RepeatSuppressor divineSpearSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	private List<DescribesCastLocation<AbilityCastStart>> sameSetLocations(AbilityCastStart e1, long id) {
		List<DescribesCastLocation<AbilityCastStart>> out = new ArrayList<>();
		for (CastTracker ct : casts.getAll()) {
			AbilityCastStart cast = ct.getCast();
			if (!cast.abilityIdMatches(id) || cast.getLocationInfo() == null) {
				continue;
			}
			long delta = Math.abs(Duration.between(e1.getEffectiveHappenedAt(), cast.getEffectiveHappenedAt()).toMillis());
			if (delta <= SAME_SET_MS) {
				out.add(cast.getLocationInfo());
			}
		}
		return out;
	}

	// 槍の設置者は直角の位置にいて、潰す側を向いている。中央に0体なら風車型、2体なら矢印型
	@AutoFeed
	private final SequentialTrigger<BaseEvent> divineSpearSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xC3DF) && divineSpearSupp.check(acs),
			(e1, s) -> {
				s.waitMs(LOCATION_WAIT_MS);
				List<DescribesCastLocation<AbilityCastStart>> spears = sameSetLocations(e1, 0xC3DF);
				if (spears.size() < 4) {
					s.updateCall(divineSpearUnknown, e1);
					return;
				}
				List<Double> centerHeadings = new ArrayList<>();
				Double northHeading = null;
				for (DescribesCastLocation<AbilityCastStart> spear : spears) {
					long x = Math.round(spear.getPos().getX());
					long y = Math.round(spear.getPos().getY());
					if (x == ALEX_CENTER_X && y == ALEX_CENTER_Y) {
						centerHeadings.add(spear.getBestHeading());
					}
					else if (x == ALEX_CENTER_X && y < ALEX_NORTH_EDGE_Y) {
						northHeading = spear.getBestHeading();
					}
				}
				if (centerHeadings.isEmpty()) {
					if (northHeading == null) {
						s.updateCall(divineSpearUnknown, e1);
					}
					else {
						// 北の設置者が南東向きなら、十字から時計回り側へ寄る
						s.updateCall(northHeading > 0 ? divineSpearPinwheelCw : divineSpearPinwheelCcw, e1);
					}
					return;
				}
				if (centerHeadings.size() != 2) {
					s.updateCall(divineSpearUnknown, e1);
					return;
				}
				double h1 = centerHeadings.get(0);
				long headingSum = Math.round(h1 + centerHeadings.get(1));
				ArenaSector safe;
				if (headingSum == 0) {
					safe = Math.abs(h1) < HEADING_SPLIT ? ArenaSector.NORTH : ArenaSector.SOUTH;
				}
				else {
					// 二体とも東向きなら西が安置
					safe = headingSum < 0 ? ArenaSector.EAST : ArenaSector.WEST;
				}
				s.updateCall(divineSpearSafe.getModified(e1, Map.<String, Object>of("dir", safe)));
			});

	@NpcCastCallout(0xC3E0)
	private final ModifiableCallout<AbilityCastStart> impartialRulingLeft = ModifiableCallout.durationBasedCall("Impartial Ruling (Left)", "Right, then Left");

	@NpcCastCallout(0xC3E1)
	private final ModifiableCallout<AbilityCastStart> impartialRulingRight = ModifiableCallout.durationBasedCall("Impartial Ruling (Right)", "Left, then Right");

	@NpcCastCallout(value = 0xC5FE, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> karmicShielding = ModifiableCallout.durationBasedCall("Karmic Shielding", "Attack Only Unshielded Gordius");

	@NpcCastCallout(0xC3E9)
	private final ModifiableCallout<AbilityCastStart> divineJudgment = ModifiableCallout.durationBasedCall("Divine Judgment", "Raidwide");

	// アワルン

	@PlayerHeadmarker(0x8B)
	private final ModifiableCallout<HeadMarkerEvent> auroralWind = new ModifiableCallout<>("Auroral Wind", "Spread");

	@NpcCastCallout(0xC535)
	private final ModifiableCallout<AbilityCastStart> impactStream = ModifiableCallout.durationBasedCall("Impact Stream", "Raidwide");

	// プロマシア

	@NpcCastCallout(0xC48D)
	private final ModifiableCallout<AbilityCastStart> emptySalvation = ModifiableCallout.durationBasedCall("Empty Salvation", "Raidwide");

	@HandleEvents
	public void wheelBastionCast(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0xC491, 0xC492)) {
			wheelBastionActive = true;
		}
		else if (event.abilityIdMatches(0xC48E)) {
			earthboundHeavenActive = false;
		}
		else if (event.abilityIdMatches(0xC49D)) {
			earthboundHeavenActive = true;
		}
	}

	@HandleEvents
	public void wheelBastionUsed(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0xC493, 0xC494)) {
			wheelBastionActive = false;
		}
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> penanceSq = SqtTemplates.sq(30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xC49C),
			(e1, s) -> {
				penanceActive = true;
				s.waitEvent(AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0xC49C));
				s.waitMs(STATE_LINGER_MS);
				penanceActive = false;
			});

	private final ModifiableCallout<AbilityCastStart> blessingLeft = ModifiableCallout.durationBasedCall("Malevolent Blessing (Left Safe)", "Left");
	private final ModifiableCallout<AbilityCastStart> blessingRight = ModifiableCallout.durationBasedCall("Malevolent Blessing (Right Safe)", "Right");

	// 車輪・砦と同時のときはそちらのコールにまとめる
	@AutoFeed
	private final SequentialTrigger<BaseEvent> blessingSq = SqtTemplates.sq(30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xC496, 0xC497),
			(e1, s) -> {
				boolean rightSafe = e1.abilityIdMatches(0xC497);
				blessingRightSafe = rightSafe;
				if (!wheelBastionActive) {
					s.updateCall(rightSafe ? blessingRight : blessingLeft, e1);
				}
				s.waitEvent(AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0xC496, 0xC497));
				s.waitMs(STATE_LINGER_MS);
				blessingRightSafe = null;
			});

	private final ModifiableCallout<AbilityUsedEvent> wheelLasers = new ModifiableCallout<>("Wheel of Impregnability (Lasers)", "Out of Melee, Get Behind => Avoid Lasers");
	private final ModifiableCallout<AbilityUsedEvent> wheelLeft = new ModifiableCallout<>("Wheel of Impregnability (Left)", "Out of Melee, Left");
	private final ModifiableCallout<AbilityUsedEvent> wheelRight = new ModifiableCallout<>("Wheel of Impregnability (Right)", "Out of Melee, Right");
	private final ModifiableCallout<AbilityUsedEvent> wheel = new ModifiableCallout<>("Wheel of Impregnability", "Out of Melee");
	private final ModifiableCallout<AbilityUsedEvent> bastionLasers = new ModifiableCallout<>("Bastion of Twilight (Lasers)", "Get Behind, Get Under => Avoid Lasers");
	private final ModifiableCallout<AbilityUsedEvent> bastionLeft = new ModifiableCallout<>("Bastion of Twilight (Left)", "Get Under, Left");
	private final ModifiableCallout<AbilityUsedEvent> bastionRight = new ModifiableCallout<>("Bastion of Twilight (Right)", "Get Under, Right");
	private final ModifiableCallout<AbilityUsedEvent> bastion = new ModifiableCallout<>("Bastion of Twilight", "Get Behind, Get Under");
	private final RepeatSuppressor wheelBastionSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> wheelBastionSq = SqtTemplates.sq(15_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0xC491, 0xC492) && wheelBastionSupp.check(aue),
			(e1, s) -> {
				boolean isWheel = e1.abilityIdMatches(0xC491);
				s.waitMs(WHEEL_BASTION_DELAY_MS);
				Boolean rightSafe = blessingRightSafe;
				ModifiableCallout<AbilityUsedEvent> call;
				if (penanceActive) {
					call = isWheel ? wheelLasers : bastionLasers;
				}
				else if (rightSafe != null) {
					if (isWheel) {
						call = rightSafe ? wheelRight : wheelLeft;
					}
					else {
						call = rightSafe ? bastionRight : bastionLeft;
					}
				}
				else {
					call = isWheel ? wheel : bastion;
				}
				s.updateCall(call, e1);
			});

	private final ModifiableCallout<HeadMarkerEvent> cometOnYou = new ModifiableCallout<>("Comet / Dark Nova (On You)", "Tank Cleave on You");
	private final ModifiableCallout<HeadMarkerEvent> cometNotOnYou = new ModifiableCallout<>("Comet / Dark Nova (Not on You)", "Avoid Tank Cleaves");
	private final RepeatSuppressor cometSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void cometCollect(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x158) && event.getTarget().isThePlayer()) {
			cometOnPlayer = true;
		}
	}

	// 複数のタンクに付くので、全員分そろってから判断する
	@AutoFeed
	private final SequentialTrigger<BaseEvent> cometSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(0x158) && cometSupp.check(hm),
			(e1, s) -> {
				s.waitMs(LOCATION_WAIT_MS);
				s.updateCall(cometOnPlayer ? cometOnYou : cometNotOnYou, e1);
				cometOnPlayer = false;
			});

	@PlayerHeadmarker(0x1D2)
	private final ModifiableCallout<HeadMarkerEvent> meteor = new ModifiableCallout<>("Meteor", "Spread");

	@NpcCastCallout(value = 0xC49E, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> infernalDeliveranceTowers = ModifiableCallout.durationBasedCall("Infernal Deliverance (Towers)", "Get Towers");

	private final ModifiableCallout<AbilityUsedEvent> infernalDeliverancePuddles = new ModifiableCallout<>("Infernal Deliverance (Puddles)", "Move Away");
	private final RepeatSuppressor infernalPuddleSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void infernalDeliverancePuddles(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0xC49F) && infernalPuddleSupp.check(event)) {
			context.accept(infernalDeliverancePuddles.getModified(event));
		}
	}

	// 南、北西、北東の3つの足場がある
	private static int getPlatform(Position pos) {
		if (pos == null) {
			return -1;
		}
		if (pos.getY() > PROMATHIA_PLATFORM_SPLIT_Y) {
			return 0;
		}
		if (pos.getX() < PROMATHIA_CENTER_X) {
			return 1;
		}
		if (pos.getX() > PROMATHIA_CENTER_X) {
			return 2;
		}
		return -1;
	}

	private boolean onPlayerPlatform(XivCombatant caster) {
		XivPlayerCharacter player = state.getPlayer();
		if (player == null) {
			return false;
		}
		int playerPlatform = getPlatform(player.getPos());
		return playerPlatform != -1 && playerPlatform == getPlatform(state.getLatestCombatantData(caster).getPos());
	}

	private final ModifiableCallout<AbilityCastStart> windsCw = ModifiableCallout.durationBasedCall("Winds of Promyvion (Clockwise)", "Clockwise");
	private final ModifiableCallout<AbilityCastStart> windsCcw = ModifiableCallout.durationBasedCall("Winds of Promyvion (Counterclockwise)", "Counterclockwise");
	private final ModifiableCallout<AbilityCastStart> auroralDrape = ModifiableCallout.durationBasedCall("Auroral Drape", "Go to Safe Corner");
	private final ModifiableCallout<AbilityCastStart> emptyBeleaguer = ModifiableCallout.durationBasedCall("Empty Beleaguer", "Dodge 2-1");
	private final RepeatSuppressor windsSupp = new RepeatSuppressor(Duration.ofSeconds(5));
	private final RepeatSuppressor drapeSupp = new RepeatSuppressor(Duration.ofSeconds(5));
	private final RepeatSuppressor beleaguerSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	// 中間フェーズは自分のいる足場の雑魚だけを言う
	@HandleEvents
	public void intermissionAdds(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0xC4B0, 0xC4B1)) {
			if (onPlayerPlatform(event.getSource()) && windsSupp.check(event)) {
				context.accept((event.abilityIdMatches(0xC4B0) ? windsCw : windsCcw).getModified(event));
			}
		}
		else if (event.abilityIdMatches(0xC4B3)) {
			if (onPlayerPlatform(event.getSource()) && drapeSupp.check(event)) {
				context.accept(auroralDrape.getModified(event));
			}
		}
		else if (event.abilityIdMatches(0xC4AF)) {
			if (onPlayerPlatform(event.getSource()) && beleaguerSupp.check(event)) {
				context.accept(emptyBeleaguer.getModified(event));
			}
		}
	}

	private final ModifiableCallout<AbilityCastStart> deadlyRebirth = ModifiableCallout.durationBasedCall("Deadly Rebirth", "Raidwide, Stun");

	@HandleEvents
	public void deadlyRebirth(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0xC4AB)) {
			seenPromathiaIntermission = true;
			context.accept(deadlyRebirth.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> explosionSafe = new ModifiableCallout<>("Eternity Explosion", "{dir1} / {dir2}");
	private final ModifiableCallout<AbilityCastStart> explosionUnknown = new ModifiableCallout<>("Eternity Explosion (Unknown)", "Avoid Puddle Explosions");
	private final ModifiableCallout<AbilityCastStart> earthboundWest = new ModifiableCallout<>("Earthbound Explosion (West)", "West");
	private final ModifiableCallout<AbilityCastStart> earthboundSoutheast = new ModifiableCallout<>("Earthbound Explosion (Southeast)", "Southeast");
	private final ModifiableCallout<AbilityCastStart> earthboundNortheast = new ModifiableCallout<>("Earthbound Explosion (Northeast)", "Northeast");
	private final RepeatSuppressor explosionSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	// 六角形の頂点にある水たまりの位置（丸めた値）
	private static ArenaSector puddleDir(Position pos) {
		long x = Math.round(pos.getX());
		long y = Math.round(pos.getY());
		if (x == -820 && y == -805) {
			return ArenaSector.SOUTH;
		}
		if (x == -833 && y == -813) {
			return ArenaSector.SOUTHWEST;
		}
		if (x == -807 && y == -813) {
			return ArenaSector.SOUTHEAST;
		}
		if (x == -833 && y == -828) {
			return ArenaSector.NORTHWEST;
		}
		if (x == -807 && y == -828) {
			return ArenaSector.NORTHEAST;
		}
		if (x == -820 && y == -835) {
			return ArenaSector.NORTH;
		}
		return ArenaSector.UNKNOWN;
	}

	private static List<ArenaSector> evenExplosionSafe(List<ArenaSector> explosions) {
		if (explosions.contains(ArenaSector.UNKNOWN)) {
			return List.of();
		}
		if (explosions.size() == 4) {
			Set<ArenaSector> safe = EnumSet.of(ArenaSector.NORTH, ArenaSector.NORTHEAST, ArenaSector.SOUTHEAST, ArenaSector.SOUTH, ArenaSector.SOUTHWEST, ArenaSector.NORTHWEST);
			explosions.forEach(safe::remove);
			return new ArrayList<>(safe);
		}
		if (explosions.contains(ArenaSector.NORTH) || explosions.contains(ArenaSector.SOUTH)) {
			return List.of(ArenaSector.EAST, ArenaSector.WEST);
		}
		if (explosions.contains(ArenaSector.EAST) || explosions.contains(ArenaSector.WEST)) {
			return List.of(ArenaSector.NORTH, ArenaSector.SOUTH);
		}
		if (explosions.contains(ArenaSector.NORTHEAST) || explosions.contains(ArenaSector.SOUTHWEST)) {
			return List.of(ArenaSector.NORTHWEST, ArenaSector.SOUTHEAST);
		}
		if (explosions.contains(ArenaSector.SOUTHEAST) || explosions.contains(ArenaSector.NORTHWEST)) {
			return List.of(ArenaSector.SOUTHWEST, ArenaSector.NORTHEAST);
		}
		return List.of();
	}

	// 偶数個の爆発は通常の安置を、大地の天は奇数個なので空いている側を言う
	@AutoFeed
	private final SequentialTrigger<BaseEvent> explosionSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xC490) && explosionSupp.check(acs),
			(e1, s) -> {
				boolean earthbound = earthboundHeavenActive;
				boolean postIntermission = seenPromathiaIntermission && !earthbound;
				if (postIntermission && multiExplosionCounter != 0) {
					s.waitMs(POST_INTERMISSION_LATER_DELAY_MS);
				}
				else {
					s.waitMs(POST_INTERMISSION_FIRST_DELAY_MS);
				}
				if (postIntermission) {
					multiExplosionCounter = (multiExplosionCounter + 1) % EXPLOSION_SETS;
				}
				List<ArenaSector> explosions = new ArrayList<>();
				for (DescribesCastLocation<AbilityCastStart> loc : sameSetLocations(e1, 0xC490)) {
					explosions.add(puddleDir(loc.getPos()));
				}
				if (earthbound) {
					// 北と南なら残りは東なので西、北東と南西なら残りは北西なので南東
					if (explosions.contains(ArenaSector.NORTH) && explosions.contains(ArenaSector.SOUTH)) {
						s.updateCall(earthboundWest, e1);
					}
					else if (explosions.contains(ArenaSector.NORTHEAST) && explosions.contains(ArenaSector.SOUTHWEST)) {
						s.updateCall(earthboundSoutheast, e1);
					}
					else if (explosions.contains(ArenaSector.NORTHWEST) && explosions.contains(ArenaSector.SOUTHEAST)) {
						s.updateCall(earthboundNortheast, e1);
					}
					else {
						s.updateCall(explosionUnknown, e1);
					}
					return;
				}
				List<ArenaSector> safe = evenExplosionSafe(explosions);
				if (safe.size() != 2) {
					s.updateCall(explosionUnknown, e1);
					return;
				}
				s.updateCall(explosionSafe.getModified(e1, Map.<String, Object>of("dir1", safe.get(0), "dir2", safe.get(1))));
			});

	// 神龍

	@NpcCastCallout(0xBFD1)
	private final ModifiableCallout<AbilityCastStart> cosmicBreath = ModifiableCallout.durationBasedCall("Cosmic Breath", "Go Lower Platform");

	@NpcCastCallout(0xBFD4)
	private final ModifiableCallout<AbilityCastStart> cosmicTail = ModifiableCallout.durationBasedCall("Cosmic Tail", "Go Upper Platform");

	private final ModifiableCallout<BuffApplied> waningLight = new ModifiableCallout<>("Waning Light", "Light Safe Later");
	private final ModifiableCallout<BuffApplied> waxingDark = new ModifiableCallout<>("Waxing Dark", "Dark Safe Later");

	@HandleEvents
	public void aspect(EventContext context, BuffApplied event) {
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		if (event.buffIdMatches(0x14E8)) {
			lightAspect = true;
			context.accept(waningLight.getModified(event));
		}
		else if (event.buffIdMatches(0x14E9)) {
			lightAspect = false;
			context.accept(waxingDark.getModified(event));
		}
		else if (event.buffIdMatches(0x4F7)) {
			tookTrailTower = true;
		}
	}

	private final ModifiableCallout<AbilityCastStart> twilightNebulaLight = ModifiableCallout.durationBasedCall("Twilight Nebula (Light)", "Up on Light Platform");
	private final ModifiableCallout<AbilityCastStart> twilightNebulaDark = ModifiableCallout.durationBasedCall("Twilight Nebula (Dark)", "Down on Dark Platform");
	private final ModifiableCallout<AbilityCastStart> twilightNebulaUnknown = ModifiableCallout.durationBasedCall("Twilight Nebula (Unknown)", "Match Platform and Debuff Color");

	@HandleEvents
	public void twilightNebula(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0xBFD9)) {
			return;
		}
		Boolean light = lightAspect;
		ModifiableCallout<AbilityCastStart> call;
		if (light == null) {
			call = twilightNebulaUnknown;
		}
		else {
			call = light ? twilightNebulaLight : twilightNebulaDark;
		}
		context.accept(call.getModified(event));
	}

	@PlayerHeadmarker(0x2A8)
	private final ModifiableCallout<HeadMarkerEvent> vortexLookAway = new ModifiableCallout<>("Cataclysmic Vortex (Look Away)", "Look Away");

	@PlayerHeadmarker(0x2A9)
	private final ModifiableCallout<HeadMarkerEvent> vortexLookTowards = new ModifiableCallout<>("Cataclysmic Vortex (Look Towards)", "Look Towards");

	@PlayerHeadmarker(0x2AA)
	private final ModifiableCallout<HeadMarkerEvent> vortexStillness = new ModifiableCallout<>("Cataclysmic Vortex (Stillness)", "Stop Everything");

	@PlayerHeadmarker(0x2AB)
	private final ModifiableCallout<HeadMarkerEvent> vortexMotion = new ModifiableCallout<>("Cataclysmic Vortex (Motion)", "Keep Moving");

	@NpcCastCallout(0xBFEA)
	private final ModifiableCallout<AbilityCastStart> atomicTail = ModifiableCallout.durationBasedCall("Atomic Tail", "Go Upper Platform");

	// ホロウキング

	private final ModifiableCallout<AbilityCastStart> celestialTrail = ModifiableCallout.durationBasedCall("Celestial Trail", "Get Towers");
	private final RepeatSuppressor celestialTrailSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	// 一度塔を踏んだ人は再び踏めないので呼ばない
	@HandleEvents
	public void celestialTrail(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0xBFF4) && !tookTrailTower && celestialTrailSupp.check(event)) {
			context.accept(celestialTrail.getModified(event));
		}
	}

	@NpcCastCallout(0xC01B)
	private final ModifiableCallout<AbilityCastStart> emptyProclamation = ModifiableCallout.durationBasedCall("Empty Proclamation", "Raidwide");

	// どちらも斜め成分があるので、北西か北東で言う
	@NpcCastCallout(0xC000)
	private final ModifiableCallout<AbilityCastStart> leftSwordscross = ModifiableCallout.durationBasedCall("Left Swordscross", "Northwest");

	@NpcCastCallout(0xBFFF)
	private final ModifiableCallout<AbilityCastStart> rightSwordscross = ModifiableCallout.durationBasedCall("Right Swordscross", "Northeast");

	@NpcCastCallout(0xC005)
	private final ModifiableCallout<AbilityCastStart> twinBlazeLeft = ModifiableCallout.durationBasedCall("Twin Blaze (Left)", "In, Left");

	@NpcCastCallout(0xC006)
	private final ModifiableCallout<AbilityCastStart> twinBlazeRight = ModifiableCallout.durationBasedCall("Twin Blaze (Right)", "In, Right");

	@NpcCastCallout(0xC009)
	private final ModifiableCallout<AbilityCastStart> cataclysmicBlade = ModifiableCallout.durationBasedCall("Cataclysmic Blade", "Avoid Sword Cones");

}
