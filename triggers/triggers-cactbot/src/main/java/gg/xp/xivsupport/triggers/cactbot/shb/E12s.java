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
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.RawAddCombatantEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/raid/e12s.ts をもとに移植
@CalloutRepo(name = "Edens Promise Eternity Savage", duty = KnownDuty.EdensPromiseEternitySavage)
public class E12s extends AutoChildEventHandler implements FilteredEventHandler {

	// 頭上マーカーはインスタンスごとにずれるので、最初のマーカー（フォームレス・ジャッジメント）を基準に戻す
	private static final long FIRST_HEADMARKER = 0xDA;
	private static final long FORMLESS_MARKER = 0xDA;
	private static final long TITAN_BLUE_MARKER = 0xBB;
	private static final long TITAN_YELLOW_MARKER = 0xB9;
	private static final long TITAN_ORANGE_MARKER = 0xBA;
	private static final long STATUE_STACK_MARKER = 0x3E;
	private static final long FIRST_LASER_MARKER = 0x91;
	private static final long LAST_LASER_MARKER = 0x98;
	private static final int LASER_COUNT = 4;

	private static final long SHIVA_TETHER = 0x8C;
	private static final long TITAN_TETHER = 0x8D;
	private static final long LEVIATHAN_TETHER = 0x8E;
	private static final long IFRIT_TETHER = 0x8F;
	private static final long RAMUH_TETHER = 0x90;
	private static final long GARUDA_TETHER = 0x91;
	private static final long SCULPTURE_TETHER = 0x11;
	private static final long QUICKEN_TETHER = 0x86;

	private static final long CHISELED_SCULPTURE_NAME_ID = 9818;
	private static final long BEASTLY_SCULPTURE_NAME_ID = 9819;
	private static final long SORROWS_HOURGLASS_NAME_ID = 9823;
	private static final long ANGERS_HOURGLASS_NAME_ID = 9824;

	private static final long DARK_FIRE = 0x997;
	private static final long SHADOWEYE = 0x998;
	private static final long DARK_WATER = 0x99D;
	private static final long DARK_BLIZZARD = 0x99E;
	private static final long DARK_AERO = 0x99F;
	private static final long DARK_ERUPTION = 0x99C;
	private static final long FLARE = 0x690;
	private static final long UNHOLY_DARKNESS = 0x996;
	private static final long RETURN = 0x994;
	private static final long RETURN_IV = 0x995;

	// アリーナ中心は (0, -75)
	private static final double CENTER_X = 0;
	private static final double CENTER_Y = -75;
	// 短いファイガ/ブリザガは15秒、長いものは29秒
	private static final Duration BASIC_LONG_THRESHOLD = Duration.ofSeconds(20);
	// アドバンスドでは目とエアロ2回の人にも短いエアロが付くので、最後のエアロだけを見る
	private static final Duration ADVANCED_FINAL_AERO = Duration.ofSeconds(28);
	// アドバンスドの位置取り用の短い頭割り（12秒）は除外する
	private static final Duration SHORT_WATER = Duration.ofSeconds(13);
	private static final Duration TETHER_CLEANUP_DELAY = Duration.ofSeconds(10);
	private static final String UNKNOWN_PLAYER = "???";

	// 北西から時計回り。アドバンスドの南北の分け方に合わせている
	private static final List<ArenaSector> HOURGLASS_DIRS = List.of(
			ArenaSector.NORTHWEST, ArenaSector.NORTH, ArenaSector.NORTHEAST, ArenaSector.EAST,
			ArenaSector.SOUTHEAST, ArenaSector.SOUTH, ArenaSector.SOUTHWEST, ArenaSector.WEST);

	private XivState state;

	public E12s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.EdensPromiseEternitySavage);
	}

	private enum Phase {
		BASIC,
		INTERMEDIATE,
		ADVANCED,
		TERMINAL
	}

	private enum StatueDir {
		INNER,
		OUTER,
		UNKNOWN
	}

	private record CollectedTether(long id, Instant at) {
	}

	private boolean isDoorBoss;
	private final List<CollectedTether> tethers = new ArrayList<>();
	private List<Long> stockedTethers = List.of();
	private Instant lastPrimalCastAt;
	private int castCount;
	private String junctionSuffix;
	private int junctionCount;
	private final List<XivCombatant> formlessTargets = new ArrayList<>();
	private final List<XivCombatant> weightTargets = new ArrayList<>();
	private boolean seenFirstBombs;
	private final List<XivCombatant> statueStacks = new ArrayList<>();
	private int statueTetherNumber;
	private StatueDir statueDir;
	private int statueLaserCount;

	private Phase phase;
	private final List<XivCombatant> waterStacks = new ArrayList<>();
	private boolean seenInitialSpread;
	private boolean seenInitialStacks;
	private final List<BuffApplied> basicDebuffs = new ArrayList<>();
	private boolean calledBasicDebuff;
	private List<XivCombatant> basicEyes = List.of();
	private final Map<Long, Duration> intermediateDebuffs = new LinkedHashMap<>();
	private final List<Long> intermediateDebuffKeys = new ArrayList<>();
	private final List<BuffApplied> advancedDebuffs = new ArrayList<>();
	private final Map<Long, Integer> sorrows = new HashMap<>();

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		isDoorBoss = false;
		tethers.clear();
		stockedTethers = List.of();
		lastPrimalCastAt = null;
		castCount = 0;
		junctionSuffix = null;
		junctionCount = 0;
		formlessTargets.clear();
		weightTargets.clear();
		seenFirstBombs = false;
		statueStacks.clear();
		statueTetherNumber = 0;
		statueDir = null;
		statueLaserCount = 0;
		phase = null;
		waterStacks.clear();
		seenInitialSpread = false;
		seenInitialStacks = false;
		basicDebuffs.clear();
		calledBasicDebuff = false;
		basicEyes = List.of();
		intermediateDebuffs.clear();
		intermediateDebuffKeys.clear();
		advancedDebuffs.clear();
		sorrows.clear();
	}

	private static long markerId(HeadMarkerEvent event) {
		return FIRST_HEADMARKER + event.getMarkerOffset();
	}

	private boolean playerIsTank() {
		return state.playerJobMatches(Job::isTank);
	}

	private static Object otherPlayer(List<XivCombatant> players) {
		if (players.size() != 2) {
			return UNKNOWN_PLAYER;
		}
		XivCombatant first = players.get(0);
		XivCombatant second = players.get(1);
		if (first.isThePlayer()) {
			return second;
		}
		if (second.isThePlayer()) {
			return first;
		}
		return UNKNOWN_PLAYER;
	}

	private static List<XivCombatant> sortedByName(List<XivCombatant> players) {
		return players.stream().sorted(Comparator.comparing(XivCombatant::getName)).toList();
	}

	private static boolean containsPlayer(List<XivCombatant> players) {
		return players.stream().anyMatch(XivCombatant::isThePlayer);
	}

	// 03 行の時点では XivState 側の情報がまだ反映されていないことがあるので、行の生データを優先する
	private static long addedNameId(RawAddCombatantEvent event) {
		return event.getFullInfo() != null ? event.getFullInfo().getBnpcNameId() : event.getEntity().getbNpcNameId();
	}

	private static Position addedPos(RawAddCombatantEvent event) {
		return event.getFullInfo() != null ? event.getFullInfo().getPos() : event.getEntity().getPos();
	}

	private static int hourglassDir(Position pos) {
		double x = pos.getX() - CENTER_X;
		double y = pos.getY() - CENTER_Y;
		return (int) (Math.round(5 - 4 * Math.atan2(x, y) / Math.PI) % 8);
	}

	// 前半: エデンズ・プロミス

	private final ModifiableCallout<HeadMarkerEvent> formlessBusterSwap = new ModifiableCallout<>("Formless Judgment: On You (Tank)", "Tankbuster + Swap");
	private final ModifiableCallout<HeadMarkerEvent> formlessBusterBlu = new ModifiableCallout<>("Formless Judgment: On You (BLU)", "Buster on You (with {player})");
	private final ModifiableCallout<HeadMarkerEvent> formlessBusterOnYou = new ModifiableCallout<>("Formless Judgment: On You", "Buster on You");

	private final ModifiableCallout<HeadMarkerEvent> titanBlueSingular = new ModifiableCallout<>("Junction Titan: Blue Weight", "Blue Weight");
	private final ModifiableCallout<HeadMarkerEvent> titanBluePartner = new ModifiableCallout<>("Junction Titan: Blue Weight with Partner", "Blue (with {player})");
	private final ModifiableCallout<HeadMarkerEvent> titanYellowSpread = new ModifiableCallout<>("Junction Titan: Yellow", "Yellow Spread");
	private final ModifiableCallout<HeadMarkerEvent> titanOrangeStack = new ModifiableCallout<>("Junction Titan: Orange", "Orange Stack");

	private final ModifiableCallout<HeadMarkerEvent> classicalStackOnYou = new ModifiableCallout<>("Classical Sculpture: Stack on You", "Stack on You (with {player})");
	private final ModifiableCallout<HeadMarkerEvent> classicalStacks = new ModifiableCallout<>("Classical Sculpture: Stacks", "Stacks: {player1}, {player2}");

	@HandleEvents
	public void headMarkers(EventContext context, HeadMarkerEvent event) {
		if (!isDoorBoss) {
			return;
		}
		long id = markerId(event);
		XivCombatant target = event.getTarget();
		boolean onPlayer = target.isThePlayer();

		if (id == FORMLESS_MARKER) {
			formlessTargets.add(target);
			if (formlessTargets.size() != 2 || !containsPlayer(formlessTargets)) {
				return;
			}
			if (playerIsTank()) {
				context.accept(formlessBusterSwap.getModified(event));
			}
			else if (state.playerJobMatches(job -> job == Job.BLU)) {
				// 青魔道士はここを受けることが多いので相方を伝える
				context.accept(formlessBusterBlu.getModified(event, Map.<String, Object>of("player", otherPlayer(formlessTargets))));
			}
			else {
				context.accept(formlessBusterOnYou.getModified(event));
			}
			return;
		}

		if (id == TITAN_BLUE_MARKER) {
			weightTargets.add(target);
			// 2回目以降の青は2人に付くので相方を伝える
			if (seenFirstBombs && weightTargets.size() == 2 && containsPlayer(weightTargets)) {
				context.accept(titanBluePartner.getModified(event, Map.<String, Object>of("player", otherPlayer(weightTargets))));
				return;
			}
			if (onPlayer && !seenFirstBombs) {
				context.accept(titanBlueSingular.getModified(event));
			}
			return;
		}
		if (id == TITAN_YELLOW_MARKER) {
			if (onPlayer) {
				context.accept(titanYellowSpread.getModified(event));
			}
			return;
		}
		if (id == TITAN_ORANGE_MARKER) {
			if (onPlayer) {
				context.accept(titanOrangeStack.getModified(event));
			}
			return;
		}

		if (id == STATUE_STACK_MARKER) {
			statueStacks.add(target);
			if (statueStacks.size() != 2) {
				return;
			}
			if (containsPlayer(statueStacks)) {
				context.accept(classicalStackOnYou.getModified(event, Map.<String, Object>of("player", otherPlayer(statueStacks))));
			}
			else {
				List<XivCombatant> sorted = sortedByName(statueStacks);
				context.accept(classicalStacks.getModified(event, Map.<String, Object>of("player1", sorted.get(0), "player2", sorted.get(1))));
			}
			return;
		}

		if (onPlayer && id >= FIRST_LASER_MARKER && id <= LAST_LASER_MARKER) {
			// 四角1～4、三角1～4の順に連番になっている
			statueTetherNumber = (int) ((id - FIRST_LASER_MARKER) % LASER_COUNT) + 1;
		}
	}

	private final ModifiableCallout<TetherEvent> sculptureInner = new ModifiableCallout<>("Chiseled Sculpture Tether: Inner", "Laser {num} (Inner)");
	private final ModifiableCallout<TetherEvent> sculptureOuter = new ModifiableCallout<>("Chiseled Sculpture Tether: Outer", "Laser {num} (Outer)");
	private final ModifiableCallout<TetherEvent> sculptureUnknown = new ModifiableCallout<>("Chiseled Sculpture Tether: Unknown", "Laser {num}");

	private final ModifiableCallout<TetherEvent> lionTetherOnYou = new ModifiableCallout<>("Beastly Sculpture Tether", "Lion Tether on You");
	private final ModifiableCallout<TetherEvent> lionTetherNE = new ModifiableCallout<>("Beastly Sculpture Tether: NE", "NE Lion Tether");
	private final ModifiableCallout<TetherEvent> lionTetherNW = new ModifiableCallout<>("Beastly Sculpture Tether: NW", "NW Lion Tether");
	private final ModifiableCallout<TetherEvent> lionTetherSE = new ModifiableCallout<>("Beastly Sculpture Tether: SE", "SE Lion Tether");
	private final ModifiableCallout<TetherEvent> lionTetherSW = new ModifiableCallout<>("Beastly Sculpture Tether: SW", "SW Lion Tether");

	@HandleEvents
	public void sculptureTethers(EventContext context, TetherEvent event) {
		if (!event.tetherIdMatches(SCULPTURE_TETHER) || !event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			return;
		}
		XivCombatant other = event.getTargetMatching(cbt -> !cbt.isThePlayer());
		if (other == null) {
			return;
		}
		XivCombatant latest = state.getLatestCombatantData(other);
		long nameId = latest.getbNpcNameId();
		if (nameId == CHISELED_SCULPTURE_NAME_ID) {
			statueDir = sculptureDir(latest);
			if (statueTetherNumber < 1 || statueTetherNumber > LASER_COUNT) {
				return;
			}
			ModifiableCallout<TetherEvent> call = switch (statueDir) {
				case INNER -> sculptureInner;
				case OUTER -> sculptureOuter;
				case UNKNOWN -> sculptureUnknown;
			};
			context.accept(call.getModified(event, Map.<String, Object>of("num", statueTetherNumber)));
		}
		else if (nameId == BEASTLY_SCULPTURE_NAME_ID) {
			Position pos = latest.getPos();
			if (pos == null) {
				context.accept(lionTetherOnYou.getModified(event));
				return;
			}
			boolean north = pos.getY() < CENTER_Y;
			boolean east = pos.getX() > CENTER_X;
			ModifiableCallout<TetherEvent> call;
			if (north) {
				call = east ? lionTetherNE : lionTetherNW;
			}
			else {
				call = east ? lionTetherSE : lionTetherSW;
			}
			context.accept(call.getModified(event));
		}
	}

	// 彫像は2体同時に出るので、中心に近い方を内側とする
	private StatueDir sculptureDir(XivCombatant tethered) {
		List<XivCombatant> statues = state.getCombatantsListCopy().stream()
				.filter(cbt -> cbt.getbNpcNameId() == CHISELED_SCULPTURE_NAME_ID && cbt.getPos() != null)
				.toList();
		if (statues.size() != 2) {
			return StatueDir.UNKNOWN;
		}
		XivCombatant inner = statues.stream()
				.min(Comparator.comparingDouble(cbt -> Math.hypot(cbt.getPos().getX() - CENTER_X, cbt.getPos().getY() - CENTER_Y)))
				.orElseThrow();
		if (inner.getId() == tethered.getId()) {
			return StatueDir.INNER;
		}
		if (statues.stream().anyMatch(cbt -> cbt.getId() == tethered.getId())) {
			return StatueDir.OUTER;
		}
		return StatueDir.UNKNOWN;
	}

	@NpcCastCallout(0x58B4)
	private final ModifiableCallout<AbilityCastStart> palmSE = ModifiableCallout.durationBasedCall("Palm of Temperance (SE)", "SE Knockback");

	@NpcCastCallout(0x58B5)
	private final ModifiableCallout<AbilityCastStart> palmSW = ModifiableCallout.durationBasedCall("Palm of Temperance (SW)", "SW Knockback");

	private final ModifiableCallout<AbilityUsedEvent> baitInner = new ModifiableCallout<>("Statue Laser: Bait Inner", "Bait Inner {num}");
	private final ModifiableCallout<AbilityUsedEvent> baitOuter = new ModifiableCallout<>("Statue Laser: Bait Outer", "Bait Outer {num}");
	private final ModifiableCallout<AbilityUsedEvent> baitUnknown = new ModifiableCallout<>("Statue Laser: Bait", "Bait {num}");
	private final RepeatSuppressor laserSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void statueLasers(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x58B3) || statueLaserCount >= LASER_COUNT || !laserSupp.check(event)) {
			return;
		}
		// 1本目の着弾時に2本目を案内するため、1つ先から数える
		statueLaserCount = (statueLaserCount == 0 ? 1 : statueLaserCount) + 1;
		// レーザーは派手なので、自分の番以外は何も出さない
		if (statueLaserCount == statueTetherNumber) {
			ModifiableCallout<AbilityUsedEvent> call;
			if (statueDir == StatueDir.INNER) {
				call = baitInner;
			}
			else if (statueDir == StatueDir.OUTER) {
				call = baitOuter;
			}
			else {
				call = baitUnknown;
			}
			context.accept(call.getModified(event, Map.<String, Object>of("num", statueLaserCount)));
		}
		if (statueLaserCount >= LASER_COUNT) {
			// 以降のラプチャラス・リーチでレーザー中と誤判定しないように消す
			statueTetherNumber = 0;
			statueDir = null;
		}
	}

	@HandleEvents
	public void weightCleanup(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x58A5)) {
			weightTargets.clear();
			seenFirstBombs = true;
		}
	}

	private final ModifiableCallout<AbilityCastStart> formlessSwap = ModifiableCallout.durationBasedCall("Formless Judgment (Tank)", "Tankbuster + Swap");
	private final ModifiableCallout<AbilityCastStart> formlessBusters = ModifiableCallout.durationBasedCall("Formless Judgment", "Tankbusters");

	@HandleEvents
	public void formlessJudgment(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x58A9)) {
			return;
		}
		// 自分に付いている場合は頭上マーカーの方で案内済み
		if (!containsPlayer(formlessTargets)) {
			context.accept((playerIsTank() ? formlessSwap : formlessBusters).getModified(event));
		}
		formlessTargets.clear();
	}

	private final ModifiableCallout<AbilityCastStart> reachLeft = ModifiableCallout.durationBasedCall("Rapturous Reach Left", "Left");
	private final ModifiableCallout<AbilityCastStart> reachLeftInner = ModifiableCallout.durationBasedCall("Rapturous Reach Left: Bait Inner 1", "Left + Bait Inner 1");
	private final ModifiableCallout<AbilityCastStart> reachLeftOuter = ModifiableCallout.durationBasedCall("Rapturous Reach Left: Bait Outer 1", "Left + Bait Outer 1");
	private final ModifiableCallout<AbilityCastStart> reachLeftUnknown = ModifiableCallout.durationBasedCall("Rapturous Reach Left: Bait 1", "Left + Bait 1");
	private final ModifiableCallout<AbilityCastStart> reachRight = ModifiableCallout.durationBasedCall("Rapturous Reach Right", "Right");
	private final ModifiableCallout<AbilityCastStart> reachRightInner = ModifiableCallout.durationBasedCall("Rapturous Reach Right: Bait Inner 1", "Right + Bait Inner 1");
	private final ModifiableCallout<AbilityCastStart> reachRightOuter = ModifiableCallout.durationBasedCall("Rapturous Reach Right: Bait Outer 1", "Right + Bait Outer 1");
	private final ModifiableCallout<AbilityCastStart> reachRightUnknown = ModifiableCallout.durationBasedCall("Rapturous Reach Right: Bait 1", "Right + Bait 1");

	@HandleEvents
	public void rapturousReach(EventContext context, AbilityCastStart event) {
		boolean left = event.abilityIdMatches(0x58AD);
		if (!left && !event.abilityIdMatches(0x58AE)) {
			return;
		}
		ModifiableCallout<AbilityCastStart> call;
		if (statueTetherNumber != 1) {
			call = left ? reachLeft : reachRight;
		}
		else if (statueDir == StatueDir.INNER) {
			call = left ? reachLeftInner : reachRightInner;
		}
		else if (statueDir == StatueDir.OUTER) {
			call = left ? reachLeftOuter : reachRightOuter;
		}
		else {
			call = left ? reachLeftUnknown : reachRightUnknown;
		}
		context.accept(call.getModified(event));
		isDoorBoss = true;
	}

	@NpcCastCallout(0x58A8)
	private final ModifiableCallout<AbilityCastStart> maleficium = ModifiableCallout.durationBasedCall("Maleficium", "Raidwide");

	private final ModifiableCallout<TetherEvent> junctionShivaSpread = new ModifiableCallout<>("Junction Shiva", "Spread");
	private final ModifiableCallout<TetherEvent> diamondDust = new ModifiableCallout<>("Junction Shiva: Diamond Dust", "Big AOE, Get Middle");
	private final RepeatSuppressor junctionShivaSupp = new RepeatSuppressor(Duration.ofSeconds(2));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> junctionShivaSq = SqtTemplates.sq(30_000,
			TetherEvent.class, te -> te.tetherIdMatches(SHIVA_TETHER) && junctionShivaSupp.check(te),
			(e1, s) -> {
				junctionSuffix = "Spread";
				junctionCount++;
				if (junctionCount == 2) {
					// 早すぎるとリプライザルなどを合わせ損ねるので、10秒前を切ってから出す
					s.waitMs(4_000);
					s.updateCall(diamondDust, e1);
				}
				else {
					s.updateCall(junctionShivaSpread, e1);
				}
			});

	private final ModifiableCallout<TetherEvent> junctionTitanStacks = new ModifiableCallout<>("Junction Titan", "Healer Groups");
	private final ModifiableCallout<TetherEvent> earthenFury = new ModifiableCallout<>("Junction Titan: Earthen Fury", "Big AOE, Bombs Soon");
	private final RepeatSuppressor junctionTitanSupp = new RepeatSuppressor(Duration.ofSeconds(2));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> junctionTitanSq = SqtTemplates.sq(30_000,
			TetherEvent.class, te -> te.tetherIdMatches(TITAN_TETHER) && junctionTitanSupp.check(te),
			(e1, s) -> {
				junctionSuffix = "Healer Stacks";
				junctionCount++;
				if (junctionCount == 3) {
					// シヴァとは大技までの間隔が違う
					s.waitMs(5_000);
					s.updateCall(earthenFury, e1);
				}
				else {
					s.updateCall(junctionTitanStacks, e1);
				}
			});

	@HandleEvents
	public void tetherCollect(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(LEVIATHAN_TETHER, IFRIT_TETHER, RAMUH_TETHER, GARUDA_TETHER)) {
			tethers.add(new CollectedTether(event.getId(), event.getEffectiveHappenedAt()));
		}
	}

	// 前回の詠唱から10秒経った時点でそれまでの線は捨てる
	private List<Long> currentTethers(Instant now) {
		if (lastPrimalCastAt != null) {
			Instant cleanupAt = lastPrimalCastAt.plus(TETHER_CLEANUP_DELAY);
			if (!now.isBefore(cleanupAt)) {
				tethers.removeIf(t -> !t.at().isAfter(cleanupAt));
			}
		}
		return tethers.stream().map(CollectedTether::id).toList();
	}

	private static String primalSafe(long tetherId) {
		if (tetherId == LEVIATHAN_TETHER) {
			return "Middle";
		}
		if (tetherId == IFRIT_TETHER) {
			return "Sides";
		}
		if (tetherId == RAMUH_TETHER) {
			return "Out";
		}
		return "Intercards";
	}

	// 線はすべて2本組（ジャンクションは別扱い）
	private static String tetherText(List<Long> tetherIds) {
		if (tetherIds.size() < 2) {
			return null;
		}
		List<Long> sorted = tetherIds.stream().sorted().toList();
		long first = sorted.get(0);
		long second = sorted.get(1);
		if (first == LEVIATHAN_TETHER) {
			if (second == IFRIT_TETHER) {
				return "Under + Sides";
			}
			if (second == RAMUH_TETHER) {
				return "North/South + Out";
			}
			if (second == GARUDA_TETHER) {
				return "Under + Intercards";
			}
		}
		return primalSafe(first) + " + " + primalSafe(second);
	}

	private final ModifiableCallout<AbilityCastStart> stock = ModifiableCallout.durationBasedCall("Stock", "Stock: {safe}");

	@HandleEvents
	public void stock(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x5892)) {
			return;
		}
		Instant now = event.getEffectiveHappenedAt();
		stockedTethers = currentTethers(now);
		tethers.clear();
		lastPrimalCastAt = now;
		String text = tetherText(stockedTethers);
		if (text != null) {
			context.accept(stock.getModified(event, Map.<String, Object>of("safe", text)));
		}
	}

	private final ModifiableCallout<AbilityCastStart> castRelease = ModifiableCallout.durationBasedCall("Cast/Release", "{safe}");
	private final ModifiableCallout<AbilityCastStart> castReleaseJunction = ModifiableCallout.durationBasedCall("Cast/Release with Junction", "{safe} ({junction})");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> castReleaseSq = SqtTemplates.sq(30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x4E43, 0x5893),
			(e1, s) -> {
				castCount++;
				Instant now = e1.getEffectiveHappenedAt();
				boolean isRelease = e1.abilityIdMatches(0x5893);
				List<Long> source = isRelease ? stockedTethers : currentTethers(now);
				lastPrimalCastAt = now;
				// 4回目（2回目のリリース）は獅子のブレス直前に始まるので、誘導位置から早く離れないよう少し遅らせる
				if (castCount == 4) {
					s.waitMs(1_800);
				}
				// 2回目のキャストは全体頭割りのレーザー付き
				if (castCount == 2) {
					junctionSuffix = "Group Stack";
				}
				String text = tetherText(source);
				String junction = junctionSuffix;
				tethers.clear();
				junctionSuffix = null;
				if (text == null) {
					return;
				}
				if (junction == null) {
					s.updateCall(castRelease.getModified(e1, Map.<String, Object>of("safe", text)));
				}
				else {
					s.updateCall(castReleaseJunction.getModified(e1, Map.<String, Object>of("safe", text, "junction", junction)));
				}
			});

	private final ModifiableCallout<AbilityCastStart> plungingIce = ModifiableCallout.durationBasedCall("Plunging Ice", "Knockback");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> plungingIceSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x589D),
			(e1, s) -> {
				// 9秒詠唱なので少し待ってから出す
				s.waitMs(4_000);
				s.updateCall(plungingIce, e1);
			});

	private final ModifiableCallout<AbilityCastStart> laserEye = ModifiableCallout.durationBasedCall("Laser Eye", "Knockback");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> laserEyeSq = SqtTemplates.sq(20_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x58B8),
			(e1, s) -> {
				// 14.5秒詠唱なので少し待ってから出す
				s.waitMs(9_500);
				s.updateCall(laserEye, e1);
			});

	// 後半: 闇の巫女

	@NpcCastCallout(0x58F0)
	private final ModifiableCallout<AbilityCastStart> shockwavePulsar = ModifiableCallout.durationBasedCall("Shockwave Pulsar", "Raidwide");

	@NpcCastCallout({0x58E0, 0x58E1, 0x58E2, 0x58E3})
	private final ModifiableCallout<AbilityCastStart> relativity = ModifiableCallout.durationBasedCall("Relativity", "Big Raidwide");

	@HandleEvents
	public void relativityPhase(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x58E0)) {
			phase = Phase.BASIC;
		}
		else if (event.abilityIdMatches(0x58E1)) {
			phase = Phase.INTERMEDIATE;
		}
		else if (event.abilityIdMatches(0x58E2)) {
			phase = Phase.ADVANCED;
		}
		else if (event.abilityIdMatches(0x58E3)) {
			phase = Phase.TERMINAL;
		}
	}

	private final ModifiableCallout<AbilityCastStart> danceTank = ModifiableCallout.durationBasedCall("Darkest/Somber Dance (Tank)", "Tanks Out (Party In)");
	private final ModifiableCallout<AbilityCastStart> danceParty = ModifiableCallout.durationBasedCall("Darkest/Somber Dance", "Party In (Tanks Out)");

	@HandleEvents
	public void dance(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x58BE, 0x58BD)) {
			context.accept((playerIsTank() ? danceTank : danceParty).getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> somberSecondTank = new ModifiableCallout<>("Somber Dance: Second Hit (Tank)", "Tanks In (Party Out)");
	private final ModifiableCallout<AbilityUsedEvent> somberSecondParty = new ModifiableCallout<>("Somber Dance: Second Hit", "Party Out (Tanks In)");
	private final RepeatSuppressor somberSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void somberSecondHit(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x58BD) && somberSupp.check(event)) {
			context.accept((playerIsTank() ? somberSecondTank : somberSecondParty).getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> cataclysm = ModifiableCallout.durationBasedCall("Cataclysm", "{safe}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> cataclysmSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x58C2),
			(e1, s) -> {
				// 詠唱開始直後は向きが確定していないので少し待つ
				s.waitThenRefreshCombatants(250);
				Position pos = state.getLatestCombatantData(e1.getSource()).getPos();
				ArenaSector safe = pos == null ? ArenaSector.UNKNOWN : facingCardinal(pos.getHeading()).opposite();
				s.updateCall(cataclysm.getModified(e1, Map.<String, Object>of("safe", safe)));
			});

	// 斜め向きは扱わず、最も近い東西南北に丸める
	private static ArenaSector facingCardinal(double heading) {
		long quarter = Math.round(heading / (Math.PI / 2));
		if (quarter == 0) {
			return ArenaSector.SOUTH;
		}
		if (quarter == 1) {
			return ArenaSector.EAST;
		}
		if (quarter == -1) {
			return ArenaSector.WEST;
		}
		return ArenaSector.NORTH;
	}

	@NpcCastCallout(0x58C3)
	private final ModifiableCallout<AbilityCastStart> shellCrusher = ModifiableCallout.durationBasedCall("Shell Crusher", "Stack");

	private final ModifiableCallout<AbilityUsedEvent> spiritTaker = new ModifiableCallout<>("Spirit Taker", "Spread");
	private final RepeatSuppressor spiritTakerSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void spiritTaker(EventContext context, AbilityUsedEvent event) {
		// スピリットテイカーは必ずシェルクラッシャーの後なので、着弾時点で早めに出す
		if (event.abilityIdMatches(0x58C3) && spiritTakerSupp.check(event)) {
			context.accept(spiritTaker.getModified(event));
		}
	}

	@NpcCastCallout(0x58C7)
	private final ModifiableCallout<AbilityCastStart> blackHalo = ModifiableCallout.durationBasedCall("Black Halo", "Buster on {event.target}");

	private final RepeatSuppressor waterCleanupSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void darkWaterCleanup(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x58CA) && waterCleanupSupp.check(event)) {
			waterStacks.clear();
		}
	}

	@HandleEvents
	public void debuffCollect(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(DARK_WATER)) {
			waterStacks.add(event.getTarget());
		}
		if (phase == Phase.BASIC && event.buffIdMatches(DARK_FIRE, SHADOWEYE, DARK_WATER, DARK_BLIZZARD)) {
			basicDebuffs.add(event);
		}
		if (phase == Phase.ADVANCED && event.buffIdMatches(DARK_FIRE, SHADOWEYE, DARK_AERO)) {
			advancedDebuffs.add(event);
		}
	}

	private final ModifiableCallout<BuffApplied> basicEye = new ModifiableCallout<BuffApplied>("Basic Relativity: Shadoweye", "Eye (with {player})").autoIcon();
	private final ModifiableCallout<BuffApplied> basicWater = new ModifiableCallout<BuffApplied>("Basic Relativity: Dark Water", "Stack (with {player})").autoIcon();
	private final ModifiableCallout<BuffApplied> basicShortFire = new ModifiableCallout<BuffApplied>("Basic Relativity: Short Fire", "Short Fire (with {player})").autoIcon();
	private final ModifiableCallout<BuffApplied> basicLongFire = new ModifiableCallout<BuffApplied>("Basic Relativity: Long Fire", "Long Fire (with {player})").autoIcon();
	private final ModifiableCallout<BuffApplied> basicShortIce = new ModifiableCallout<BuffApplied>("Basic Relativity: Short Ice", "Short Ice (with {player})").autoIcon();
	private final ModifiableCallout<BuffApplied> basicLongIce = new ModifiableCallout<BuffApplied>("Basic Relativity: Long Ice", "Long Ice (with {player})").autoIcon();

	private static List<XivCombatant> targetsOf(List<BuffApplied> buffs, long buffId) {
		return buffs.stream().filter(ba -> ba.buffIdMatches(buffId)).map(BuffApplied::getTarget).toList();
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> basicDebuffSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> phase == Phase.BASIC && !calledBasicDebuff && ba.buffIdMatches(DARK_FIRE, SHADOWEYE, DARK_WATER, DARK_BLIZZARD),
			(e1, s) -> {
				// 全員分のデバフが揃うのを待つ
				s.waitMs(500);
				List<BuffApplied> buffs = List.copyOf(basicDebuffs);
				List<XivCombatant> eyes = targetsOf(buffs, SHADOWEYE);
				basicEyes = eyes;
				List<BuffApplied> longBuffs = buffs.stream().filter(ba -> ba.getInitialDuration().compareTo(BASIC_LONG_THRESHOLD) > 0).toList();
				List<BuffApplied> shortBuffs = buffs.stream().filter(ba -> ba.getInitialDuration().compareTo(BASIC_LONG_THRESHOLD) < 0).toList();
				List<XivCombatant> waters = targetsOf(buffs, DARK_WATER);
				List<XivCombatant> shortFires = targetsOf(shortBuffs, DARK_FIRE);
				List<XivCombatant> longFires = targetsOf(longBuffs, DARK_FIRE);
				List<XivCombatant> shortIces = targetsOf(shortBuffs, DARK_BLIZZARD);
				List<XivCombatant> longIces = targetsOf(longBuffs, DARK_BLIZZARD);

				ModifiableCallout<BuffApplied> call;
				List<XivCombatant> group;
				if (containsPlayer(eyes)) {
					call = basicEye;
					group = eyes;
				}
				else if (containsPlayer(waters)) {
					call = basicWater;
					group = waters;
				}
				else if (containsPlayer(shortFires)) {
					call = basicShortFire;
					group = shortFires;
				}
				else if (containsPlayer(longFires)) {
					call = basicLongFire;
					group = longFires;
				}
				else if (containsPlayer(shortIces)) {
					call = basicShortIce;
					group = shortIces;
				}
				else if (containsPlayer(longIces)) {
					call = basicLongIce;
					group = longIces;
				}
				else {
					return;
				}
				calledBasicDebuff = true;
				BuffApplied mine = buffs.stream().filter(ba -> ba.getTarget().isThePlayer() && ba.buffIdMatches(DARK_FIRE, SHADOWEYE, DARK_WATER, DARK_BLIZZARD))
						.findFirst()
						.orElse(e1);
				s.updateCall(call.getModified(mine, Map.<String, Object>of("player", otherPlayer(group))));
			});

	private final ModifiableCallout<BuffApplied> lookAwayFromPlayers = new ModifiableCallout<>("Basic Relativity: Look Away (No Eye)", "Look Away from {player1} and {player2}");
	private final ModifiableCallout<BuffApplied> lookAwayFromPlayer = new ModifiableCallout<>("Basic Relativity: Look Away (Eye)", "Look Away from {player}");
	private final RepeatSuppressor lookAwaySupp = new RepeatSuppressor(Duration.ofSeconds(3));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> basicLookAwaySq = SqtTemplates.sq(60_000,
			BuffApplied.class, ba -> phase == Phase.BASIC && ba.buffIdMatches(SHADOWEYE) && lookAwaySupp.check(ba),
			(e1, s) -> {
				long waitMs = e1.getEstimatedRemainingDuration().minusSeconds(3).toMillis();
				if (waitMs > 0) {
					s.waitMs(waitMs);
				}
				if (basicEyes.size() != 2) {
					return;
				}
				XivCombatant player1 = basicEyes.get(0);
				XivCombatant player2 = basicEyes.get(1);
				if (!player1.isThePlayer() && !player2.isThePlayer()) {
					s.updateCall(lookAwayFromPlayers.getModified(e1, Map.<String, Object>of("player1", player1, "player2", player2)));
				}
				else {
					s.updateCall(lookAwayFromPlayer.getModified(e1, Map.<String, Object>of("player", player1.isThePlayer() ? player2 : player1)));
				}
			});

	private static String intermediateName(long buffId) {
		if (buffId == FLARE) {
			return "Flare";
		}
		if (buffId == UNHOLY_DARKNESS) {
			return "Stack";
		}
		if (buffId == SHADOWEYE) {
			return "Gaze";
		}
		if (buffId == DARK_ERUPTION) {
			return "Spread";
		}
		if (buffId == DARK_BLIZZARD) {
			return "Ice";
		}
		return "Aero";
	}

	private final ModifiableCallout<BuffApplied> intermediateOrder = new ModifiableCallout<>("Intermediate Relativity: Order", "{effect1} > {effect2} > {effect3}", 20_000);

	@HandleEvents
	public void intermediateCollect(EventContext context, BuffApplied event) {
		if (phase != Phase.INTERMEDIATE || !event.getTarget().isThePlayer()
				|| !event.buffIdMatches(FLARE, UNHOLY_DARKNESS, SHADOWEYE, DARK_ERUPTION, DARK_BLIZZARD, DARK_AERO)) {
			return;
		}
		intermediateDebuffs.put(event.getBuff().getId(), event.getInitialDuration());
		if (intermediateDebuffs.size() != 3) {
			return;
		}
		// 持続時間の長い順＝処理する順
		List<Long> sorted = intermediateDebuffs.entrySet().stream()
				.sorted(Map.Entry.<Long, Duration>comparingByValue().reversed())
				.map(Map.Entry::getKey)
				.toList();
		intermediateDebuffKeys.clear();
		intermediateDebuffKeys.addAll(sorted.subList(1, 3));
		context.accept(intermediateOrder.getModified(event, Map.<String, Object>of(
				"effect1", intermediateName(sorted.get(0)),
				"effect2", intermediateName(sorted.get(1)),
				"effect3", intermediateName(sorted.get(2)))));
	}

	private final ModifiableCallout<BuffApplied> relativityMove = new ModifiableCallout<>("Relativity: Return", "Move!");
	private final ModifiableCallout<BuffApplied> nextFlare = new ModifiableCallout<>("Intermediate Relativity: Next Flare", "Flare");
	private final ModifiableCallout<BuffApplied> nextStack = new ModifiableCallout<>("Intermediate Relativity: Next Stack", "Stack");
	private final ModifiableCallout<BuffApplied> nextGaze = new ModifiableCallout<>("Intermediate Relativity: Next Gaze", "Gaze");
	private final ModifiableCallout<BuffApplied> nextSpread = new ModifiableCallout<>("Intermediate Relativity: Next Spread", "Spread");
	private final ModifiableCallout<BuffApplied> nextIce = new ModifiableCallout<>("Intermediate Relativity: Next Ice", "Ice");
	private final ModifiableCallout<BuffApplied> nextAero = new ModifiableCallout<>("Intermediate Relativity: Next Aero", "Aero");

	private ModifiableCallout<BuffApplied> intermediateNextCall(long buffId) {
		if (buffId == FLARE) {
			return nextFlare;
		}
		if (buffId == UNHOLY_DARKNESS) {
			return nextStack;
		}
		if (buffId == SHADOWEYE) {
			return nextGaze;
		}
		if (buffId == DARK_ERUPTION) {
			return nextSpread;
		}
		if (buffId == DARK_BLIZZARD) {
			return nextIce;
		}
		return nextAero;
	}

	@HandleEvents
	public void relativityReturn(EventContext context, BuffApplied event) {
		if (!event.buffIdMatches(RETURN, RETURN_IV) || !event.getTarget().isThePlayer()) {
			return;
		}
		if (phase != Phase.INTERMEDIATE || intermediateDebuffKeys.isEmpty()) {
			context.accept(relativityMove.getModified(event));
			return;
		}
		context.accept(intermediateNextCall(intermediateDebuffKeys.remove(0)).getModified(event));
	}

	private final ModifiableCallout<BuffApplied> lookOutside = new ModifiableCallout<>("Relativity: Look Outside", "Look Outside");

	// ベーシック以外は最後のリターンで外を向く。ベーシックはリターン設置と同時に目が来るので別扱い
	@AutoFeed
	private final SequentialTrigger<BaseEvent> lookOutsideSq = SqtTemplates.callWhenDurationIs(
			BuffApplied.class, ba -> ba.buffIdMatches(RETURN) && ba.getTarget().isThePlayer() && phase != Phase.BASIC,
			lookOutside, Duration.ofMillis(2_500));

	private final ModifiableCallout<RawAddCombatantEvent> yellowHourglass = new ModifiableCallout<>("Basic Relativity: Yellow Hourglass", "Yellow: {dir}", 10_000);

	@HandleEvents
	public void hourglassSpawn(EventContext context, RawAddCombatantEvent event) {
		long nameId = addedNameId(event);
		if (nameId != ANGERS_HOURGLASS_NAME_ID && nameId != SORROWS_HOURGLASS_NAME_ID) {
			return;
		}
		Position pos = addedPos(event);
		if (pos == null) {
			return;
		}
		int dir = hourglassDir(pos);
		if (nameId == ANGERS_HOURGLASS_NAME_ID) {
			context.accept(yellowHourglass.getModified(event, Map.<String, Object>of("dir", HOURGLASS_DIRS.get(dir))));
		}
		else {
			sorrows.put(event.getEntity().getId(), dir);
		}
	}

	private final ModifiableCallout<TetherEvent> advancedYellow = new ModifiableCallout<>("Advanced Relativity: Yellow Hourglasses", "Yellow: {dir1} / {dir2}", 4_000);
	private final RepeatSuppressor advancedYellowSupp = new RepeatSuppressor(Duration.ofSeconds(3));

	@HandleEvents
	public void advancedYellowTether(EventContext context, TetherEvent event) {
		if (!event.tetherIdMatches(QUICKEN_TETHER) || phase != Phase.ADVANCED) {
			return;
		}
		Integer sorrow1 = sorrows.get(event.getSource().getId());
		if (sorrow1 == null) {
			sorrow1 = sorrows.get(event.getTarget().getId());
		}
		if (sorrow1 == null || !advancedYellowSupp.check(event)) {
			return;
		}
		// 黄色の砂時計は対角に2つ並ぶ
		int sorrow2 = (sorrow1 + 4) % 8;
		int low = Math.min(sorrow1, sorrow2);
		int high = Math.max(sorrow1, sorrow2);
		context.accept(advancedYellow.getModified(event, Map.<String, Object>of("dir1", HOURGLASS_DIRS.get(low), "dir2", HOURGLASS_DIRS.get(high))));
	}

	private final ModifiableCallout<BuffApplied> initialStackGroups = new ModifiableCallout<>("Dark Water (First): Stack Groups", "Stack Groups", 10_000);
	private final ModifiableCallout<BuffApplied> initialStacksOn = new ModifiableCallout<>("Dark Water (First): Stacks", "Stacks ({player1}, {player2})", 10_000);
	private final ModifiableCallout<BuffApplied> initialKnockbackStackGroups = new ModifiableCallout<>("Dark Water (First): Knockback Into Stack Groups", "Knockback Into Stack Groups", 10_000);
	private final ModifiableCallout<BuffApplied> initialKnockbackStacksOn = new ModifiableCallout<>("Dark Water (First): Knockback Into Stacks", "Knockback => Stacks ({player1}, {player2})", 10_000);
	private final RepeatSuppressor initialWaterSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> initialDarkWaterSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(DARK_WATER) && phase == null && initialWaterSupp.check(ba),
			(e1, s) -> {
				// 2人分の付与を待ってから名前を出す
				s.waitMs(200);
				seenInitialStacks = true;
				if (waterStacks.size() == 2) {
					List<XivCombatant> sorted = sortedByName(waterStacks);
					Map<String, Object> params = Map.<String, Object>of("player1", sorted.get(0), "player2", sorted.get(1));
					s.updateCall((seenInitialSpread ? initialKnockbackStacksOn : initialStacksOn).getModified(e1, params));
				}
				else {
					s.updateCall(seenInitialSpread ? initialKnockbackStackGroups : initialStackGroups, e1);
				}
			});

	private final ModifiableCallout<BuffApplied> initialSpread = new ModifiableCallout<>("Dark Eruption (First): Spread", "Spread", 5_000);
	private final ModifiableCallout<BuffApplied> initialKnockbackSpread = new ModifiableCallout<>("Dark Eruption (First): Knockback Into Spread", "Knockback Into Spread", 5_000);
	private final RepeatSuppressor initialEruptionSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> initialDarkEruptionSq = SqtTemplates.sq(60_000,
			BuffApplied.class, ba -> ba.buffIdMatches(DARK_ERUPTION) && phase == null && initialEruptionSupp.check(ba),
			(e1, s) -> {
				long lead = seenInitialSpread ? 6 : 8;
				long waitMs = e1.getEstimatedRemainingDuration().minusSeconds(lead).toMillis();
				if (waitMs > 0) {
					s.waitMs(waitMs);
				}
				seenInitialSpread = true;
				s.updateCall(seenInitialStacks ? initialKnockbackSpread : initialSpread, e1);
			});

	private final ModifiableCallout<BuffApplied> waterStackGroups = new ModifiableCallout<>("Dark Water: Stack Groups", "Stack Groups");
	private final ModifiableCallout<BuffApplied> waterStacksOn = new ModifiableCallout<>("Dark Water: Stacks", "Stacks ({player1}, {player2})");
	private final RepeatSuppressor waterStacksSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> darkWaterStacksSq = SqtTemplates.sq(60_000,
			BuffApplied.class, ba -> ba.buffIdMatches(DARK_WATER) && phase != null
					&& ba.getInitialDuration().compareTo(SHORT_WATER) > 0 && waterStacksSupp.check(ba),
			(e1, s) -> {
				long waitMs = e1.getEstimatedRemainingDuration().minusSeconds(4).toMillis();
				if (waitMs > 0) {
					s.waitMs(waitMs);
				}
				if (waterStacks.size() == 2) {
					List<XivCombatant> sorted = sortedByName(waterStacks);
					s.updateCall(waterStacksOn.getModified(e1, Map.<String, Object>of("player1", sorted.get(0), "player2", sorted.get(1))));
				}
				else {
					s.updateCall(waterStackGroups, e1);
				}
			});

	private final ModifiableCallout<BuffApplied> advancedDoubleAero = new ModifiableCallout<BuffApplied>("Advanced Relativity: Double Aero", "Double Aero (with {player})", 15_000).autoIcon();
	private final ModifiableCallout<BuffApplied> advancedEye = new ModifiableCallout<BuffApplied>("Advanced Relativity: Shadoweye", "Eye (with {player})", 15_000).autoIcon();
	private final ModifiableCallout<BuffApplied> advancedSpread = new ModifiableCallout<BuffApplied>("Advanced Relativity: Spread", "Spread (with {player1}, {player2}, {player3})", 15_000).autoIcon();

	@AutoFeed
	private final SequentialTrigger<BaseEvent> advancedDebuffSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> phase == Phase.ADVANCED && ba.buffIdMatches(DARK_FIRE, SHADOWEYE, DARK_AERO),
			(e1, s) -> {
				s.waitMs(500);
				List<BuffApplied> buffs = List.copyOf(advancedDebuffs);
				advancedDebuffs.clear();
				if (buffs.isEmpty()) {
					return;
				}
				List<BuffApplied> finalAeros = buffs.stream()
						.filter(ba -> ba.buffIdMatches(DARK_AERO) && ba.getInitialDuration().compareTo(ADVANCED_FINAL_AERO) > 0)
						.toList();
				List<XivCombatant> aeros = finalAeros.stream().map(BuffApplied::getTarget).toList();
				if (containsPlayer(aeros)) {
					BuffApplied mine = finalAeros.stream().filter(ba -> ba.getTarget().isThePlayer()).findFirst().orElse(e1);
					s.updateCall(advancedDoubleAero.getModified(mine, Map.<String, Object>of("player", otherPlayer(aeros))));
					return;
				}
				List<XivCombatant> eyes = targetsOf(buffs, SHADOWEYE);
				if (containsPlayer(eyes)) {
					BuffApplied mine = buffs.stream().filter(ba -> ba.buffIdMatches(SHADOWEYE) && ba.getTarget().isThePlayer()).findFirst().orElse(e1);
					s.updateCall(advancedEye.getModified(mine, Map.<String, Object>of("player", otherPlayer(eyes))));
					return;
				}
				List<XivCombatant> spreads = targetsOf(buffs, DARK_FIRE);
				if (containsPlayer(spreads)) {
					List<Object> others = new ArrayList<>(sortedByName(spreads.stream().filter(cbt -> !cbt.isThePlayer()).toList()));
					while (others.size() < 3) {
						others.add(UNKNOWN_PLAYER);
					}
					BuffApplied mine = buffs.stream().filter(ba -> ba.buffIdMatches(DARK_FIRE) && ba.getTarget().isThePlayer()).findFirst().orElse(e1);
					s.updateCall(advancedSpread.getModified(mine, Map.<String, Object>of("player1", others.get(0), "player2", others.get(1), "player3", others.get(2))));
				}
			});

}
