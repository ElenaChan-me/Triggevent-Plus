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
import gg.xp.xivsupport.events.actlines.events.BuffRemoved;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.MapEffectEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/raid/e8s.ts をもとに移植
@CalloutRepo(name = "Edens Verse Refulgence Savage", duty = KnownDuty.EdensVerseRefulgenceSavage)
public class E8s extends AutoChildEventHandler implements FilteredEventHandler {

	private static final double ARENA_CENTER = 100;
	// アイシクルインパクトの初弾が中央の縦線上にあるかどうかの許容幅
	private static final double ICICLE_CENTER_TOLERANCE = 1;
	// 北向きのときの向きは ±π 付近になる
	private static final double FACING_NORTH_MIN_HEADING = 3;
	// 光の暴走で最後の塔を避けるべきスタック数
	private static final int LIGHTSTEEPED_AVOID_STACKS = 4;
	private static final int DIAMOND_FROST_FREEZE_COUNT = 2;
	private static final int AKH_MORN_TARGET_COUNT = 2;

	private static final long MIRROR_BLUE = 0x00020001;
	private static final long MIRROR_GREEN = 0x00200010;
	private static final long MIRROR_RED = 0x02000100;
	// 鏡の位置は 0x03 から北を起点に時計回りで並び、0x0B から同じ並びが繰り返される
	private static final long MIRROR_LOC_FIRST = 0x03;
	private static final long MIRROR_LOC_LAST = 0x12;
	private static final ArenaSector[] MIRROR_LOC_DIRS = {
			ArenaSector.NORTH, ArenaSector.NORTHEAST, ArenaSector.EAST, ArenaSector.SOUTHEAST,
			ArenaSector.SOUTH, ArenaSector.SOUTHWEST, ArenaSector.WEST, ArenaSector.NORTHWEST
	};
	private static final ArenaSector[] MIRROR_THREE_CLOCK = {
			ArenaSector.NORTHWEST, ArenaSector.NORTHEAST, ArenaSector.SOUTHEAST, ArenaSector.SOUTHWEST
	};

	private enum MirrorColor {
		BLUE,
		GREEN,
		RED
	}

	private enum Frost {
		BITING,
		DRIVING
	}

	private enum Kick {
		SCYTHE,
		AXE
	}

	private XivState state;

	public E8s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.EdensVerseRefulgenceSavage);
	}

	private boolean canCleanse() {
		return state.playerJobMatches(job -> job.isHealer() || job == Job.BRD || job == Job.BLU);
	}

	private boolean canStun() {
		return state.playerJobMatches(job -> job.isTank() || job.isMeleeDps() || job == Job.BLU);
	}

	private boolean canSilence() {
		return state.playerJobMatches(job -> job.caresAboutInterrupt() || job == Job.BLU);
	}

	private boolean isTankOrBlu() {
		return state.playerJobMatches(job -> job.isTank() || job == Job.BLU);
	}

	private int mirrorMirrorCount;
	private final Map<ArenaSector, MirrorColor> mirrorMap = new EnumMap<>(ArenaSector.class);
	private Frost firstFrost;
	private Kick firstKick;
	private final List<XivCombatant> diamondFrostFreezeTargets = new ArrayList<>();
	private boolean calledIcicleImpact;
	private int playerLightsteeped;
	private int pathOfLightCounter;
	private boolean finalTowerCalled;
	private Boolean asunderEast;
	private int asunderCount;
	private int wyrmsLament;
	private Integer wyrmclawNumber;
	private Integer wyrmfangNumber;
	private int wyrmsLamentMirrorCount;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		mirrorMirrorCount = 0;
		mirrorMap.clear();
		firstFrost = null;
		firstKick = null;
		diamondFrostFreezeTargets.clear();
		calledIcicleImpact = false;
		playerLightsteeped = 0;
		pathOfLightCounter = 0;
		finalTowerCalled = false;
		asunderEast = null;
		asunderCount = 0;
		wyrmsLament = 0;
		wyrmclawNumber = null;
		wyrmfangNumber = null;
		wyrmsLamentMirrorCount = 0;
	}

	@NpcCastCallout(0x4DCC)
	private final ModifiableCallout<AbilityCastStart> absoluteZero = ModifiableCallout.durationBasedCall("Absolute Zero", "Raidwide");

	// 鏡の鏡

	private final ModifiableCallout<AbilityCastStart> mirrorMirrorScytheNext = ModifiableCallout.durationBasedCall("Mirror, Mirror 2 (Scythe Kick Next)", "Under Boss, then Under Mirrors Soon");
	private final ModifiableCallout<AbilityCastStart> mirrorMirrorAxeNext = ModifiableCallout.durationBasedCall("Mirror, Mirror 2 (Axe Kick Next)", "Out, then Middle Soon");

	@HandleEvents
	public void mirrorMirror(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x4D5A)) {
			return;
		}
		mirrorMirrorCount++;
		mirrorMap.clear();
		if (mirrorMirrorCount == 2) {
			if (firstKick == Kick.AXE) {
				context.accept(mirrorMirrorScytheNext.getModified(event));
			}
			else if (firstKick == Kick.SCYTHE) {
				context.accept(mirrorMirrorAxeNext.getModified(event));
			}
		}
	}

	@HandleEvents
	public void mirrorCollect(EventContext context, MapEffectEvent event) {
		MirrorColor color;
		if (event.flagsMatches(MIRROR_BLUE)) {
			color = MirrorColor.BLUE;
		}
		else if (event.flagsMatches(MIRROR_GREEN)) {
			color = MirrorColor.GREEN;
		}
		else if (event.flagsMatches(MIRROR_RED)) {
			color = MirrorColor.RED;
		}
		else {
			return;
		}
		long loc = event.getLocation();
		if (loc < MIRROR_LOC_FIRST || loc > MIRROR_LOC_LAST) {
			return;
		}
		mirrorMap.put(MIRROR_LOC_DIRS[(int) ((loc - MIRROR_LOC_FIRST) % MIRROR_LOC_DIRS.length)], color);
	}

	private final ModifiableCallout<AbilityCastStart> bitingFrostRedWest = ModifiableCallout.durationBasedCall("Biting Frost (First Mirror, Red West)", "Behind, then Southwest");
	private final ModifiableCallout<AbilityCastStart> bitingFrostRedEast = ModifiableCallout.durationBasedCall("Biting Frost (First Mirror, Red East)", "Behind, then Southeast");
	private final ModifiableCallout<AbilityCastStart> bitingFrost = ModifiableCallout.durationBasedCall("Biting Frost", "Get Behind");
	private final ModifiableCallout<AbilityCastStart> drivingFrostRedEast = ModifiableCallout.durationBasedCall("Driving Frost (First Mirror, Red East)", "Front, then Northwest");
	private final ModifiableCallout<AbilityCastStart> drivingFrostRedWest = ModifiableCallout.durationBasedCall("Driving Frost (First Mirror, Red West)", "Front, then Northeast");
	private final ModifiableCallout<AbilityCastStart> drivingFrost = ModifiableCallout.durationBasedCall("Driving Frost", "Go Front");

	@HandleEvents
	public void frost(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x4D66)) {
			if (firstFrost == null) {
				firstFrost = Frost.BITING;
				if (mirrorMap.get(ArenaSector.WEST) == MirrorColor.RED) {
					context.accept(bitingFrostRedWest.getModified(event));
					return;
				}
				if (mirrorMap.get(ArenaSector.EAST) == MirrorColor.RED) {
					context.accept(bitingFrostRedEast.getModified(event));
					return;
				}
			}
			context.accept(bitingFrost.getModified(event));
		}
		else if (event.abilityIdMatches(0x4D67)) {
			if (firstFrost == null) {
				firstFrost = Frost.DRIVING;
				if (mirrorMap.get(ArenaSector.EAST) == MirrorColor.RED) {
					context.accept(drivingFrostRedEast.getModified(event));
					return;
				}
				if (mirrorMap.get(ArenaSector.WEST) == MirrorColor.RED) {
					context.accept(drivingFrostRedWest.getModified(event));
					return;
				}
			}
			context.accept(drivingFrost.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> reflectedFrost = new ModifiableCallout<>("Reflected Frost", "Swap Sides");
	private final RepeatSuppressor reflectedFrostSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void reflectedFrost(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x4DB7, 0x4DB8) && reflectedFrostSupp.check(event)) {
			context.accept(reflectedFrost.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> bitingFrostNext = new ModifiableCallout<>("Second Frost Reminder: Biting Next (Tank)", "Biting Next, Face Outward");
	private final ModifiableCallout<AbilityCastStart> drivingFrostNext = new ModifiableCallout<>("Second Frost Reminder: Driving Next (Tank)", "Driving Next, Face Inward");
	private final RepeatSuppressor forgetfulTankSupp = new RepeatSuppressor(Duration.ofSeconds(80));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> forgetfulTankSq = SqtTemplates.sq(50_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x4D66, 0x4D67) && isTankOrBlu() && forgetfulTankSupp.check(acs),
			(e1, s) -> {
				s.waitMs(43_000);
				s.updateCall(firstFrost == Frost.DRIVING ? bitingFrostNext : drivingFrostNext, e1);
			});

	@NpcCastCallout(0x4D6C)
	private final ModifiableCallout<AbilityCastStart> diamondFrost = ModifiableCallout.durationBasedCall("Diamond Frost", "Raidwide");

	private final ModifiableCallout<BuffApplied> diamondFrostCleanse = new ModifiableCallout<BuffApplied>("Diamond Frost: Freezing (Cleanser)", "Cleanse {players}").statusIcon(0x8CB);

	@HandleEvents
	public void diamondFrostFreezing(EventContext context, BuffApplied event) {
		// アイスリット・ドラゴンソングの付与は無視する
		if (!event.buffIdMatches(0x8CB) || mirrorMirrorCount != 1) {
			return;
		}
		diamondFrostFreezeTargets.add(event.getTarget());
		if (diamondFrostFreezeTargets.size() == DIAMOND_FROST_FREEZE_COUNT && canCleanse()) {
			context.accept(diamondFrostCleanse.getModified(event, Map.<String, Object>of("players", List.copyOf(diamondFrostFreezeTargets))));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> frigidNeedleStar = new ModifiableCallout<>("Diamond Frost: Star on You", "Star on You");

	@HandleEvents
	public void frigidNeedleStar(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x60) && mirrorMirrorCount == 1 && event.getTarget().isThePlayer()) {
			context.accept(frigidNeedleStar.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> icicleImpactNorthSouth = new ModifiableCallout<>("Icicle Impact: North/South", "North/South", 6_000);
	private final ModifiableCallout<AbilityCastStart> icicleImpactEastWest = new ModifiableCallout<>("Icicle Impact: East/West", "East/West", 6_000);
	private final ModifiableCallout<AbilityUsedEvent> icicleImpactNorthSouthBackup = new ModifiableCallout<>("Icicle Impact: North/South (Backup)", "North/South");
	private final ModifiableCallout<AbilityUsedEvent> icicleImpactEastWestBackup = new ModifiableCallout<>("Icicle Impact: East/West (Backup)", "East/West");

	private static boolean isOnCenterLine(Position pos) {
		return Math.abs(pos.x() - ARENA_CENTER) <= ICICLE_CENTER_TOLERANCE;
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> icicleImpactSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x4DA0) && !calledIcicleImpact,
			(e1, s) -> {
				Position pos = s.waitForCastLocation(e1).getPos();
				if (pos == null || calledIcicleImpact) {
					return;
				}
				calledIcicleImpact = true;
				s.updateCall(isOnCenterLine(pos) ? icicleImpactNorthSouth : icicleImpactEastWest, e1);
			});

	@HandleEvents
	public void icicleImpactBackup(EventContext context, AbilityUsedEvent event) {
		// 詠唱位置のデータが来なかったときのために、初弾の着弾で遅れてでも知らせる
		if (!event.abilityIdMatches(0x4DA0) || calledIcicleImpact) {
			return;
		}
		Position pos = state.getLatestCombatantData(event.getSource()).getPos();
		if (pos == null) {
			return;
		}
		calledIcicleImpact = true;
		context.accept((isOnCenterLine(pos) ? icicleImpactNorthSouthBackup : icicleImpactEastWestBackup).getModified(event));
	}

	private final ModifiableCallout<AbilityCastStart> doubleSlapOnYou = ModifiableCallout.durationBasedCall("Double Slap: On You", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> doubleSlapTank = ModifiableCallout.durationBasedCall("Double Slap: Tank Swap", "Tank Swap");
	private final ModifiableCallout<AbilityCastStart> doubleSlapOther = ModifiableCallout.durationBasedCall("Double Slap: On Other", "Buster on {event.target}");

	@HandleEvents
	public void doubleSlap(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x4D65)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(doubleSlapOnYou.getModified(event));
		}
		else if (state.playerJobMatches(Job::isTank)) {
			context.accept(doubleSlapTank.getModified(event));
		}
		else {
			context.accept(doubleSlapOther.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> axeKickFirst = ModifiableCallout.durationBasedCall("Axe Kick", "Out");
	private final ModifiableCallout<AbilityCastStart> axeKickMirror = ModifiableCallout.durationBasedCall("Axe Kick (Mirror)", "Out, then Middle");
	private final ModifiableCallout<AbilityCastStart> scytheKickFirst = ModifiableCallout.durationBasedCall("Scythe Kick", "Get Under");
	private final ModifiableCallout<AbilityCastStart> scytheKickCards = ModifiableCallout.durationBasedCall("Scythe Kick (Cardinal Mirrors)", "Under, then Under Cardinal Mirrors");
	private final ModifiableCallout<AbilityCastStart> scytheKickIntercards = ModifiableCallout.durationBasedCall("Scythe Kick (Intercard Mirrors)", "Under, then Under Intercard Mirrors");
	private final ModifiableCallout<AbilityCastStart> scytheKickUnknown = ModifiableCallout.durationBasedCall("Scythe Kick (Unknown Mirrors)", "Under Boss, then Under Mirrors");

	@HandleEvents
	public void kicks(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x4D6D)) {
			if (firstKick != null) {
				context.accept(axeKickMirror.getModified(event));
			}
			else {
				firstKick = Kick.AXE;
				context.accept(axeKickFirst.getModified(event));
			}
		}
		else if (event.abilityIdMatches(0x4D6E)) {
			if (firstKick == null) {
				firstKick = Kick.SCYTHE;
				context.accept(scytheKickFirst.getModified(event));
			}
			else if (mirrorMap.get(ArenaSector.NORTH) == MirrorColor.GREEN) {
				context.accept(scytheKickCards.getModified(event));
			}
			else if (mirrorMap.get(ArenaSector.NORTHEAST) == MirrorColor.GREEN) {
				context.accept(scytheKickIntercards.getModified(event));
			}
			else {
				context.accept(scytheKickUnknown.getModified(event));
			}
		}
	}

	// 光の暴走

	@NpcCastCallout(0x4D73)
	private final ModifiableCallout<AbilityCastStart> lightRampant = ModifiableCallout.durationBasedCall("Light Rampant", "Raidwide");

	@HandleEvents
	public void lightsteepedGain(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x8D1) && event.getTarget().isThePlayer()) {
			playerLightsteeped = (int) event.getStacks();
		}
	}

	@HandleEvents
	public void lightsteepedLose(EventContext context, BuffRemoved event) {
		if (event.buffIdMatches(0x8D1) && event.getTarget().isThePlayer()) {
			playerLightsteeped = 0;
		}
	}


	@PlayerStatusCallout(0x8CD)
	private final ModifiableCallout<BuffApplied> refulgentChain = new ModifiableCallout<BuffApplied>("Refulgent Chain", "Chain on You").autoIcon();

	private final ModifiableCallout<TetherEvent> holyLight = new ModifiableCallout<>("Holy Light", "Orb on You");

	@HandleEvents
	public void holyLight(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x2) && event.getTarget().isThePlayer()) {
			context.accept(holyLight.getModified(event));
		}
	}

	@HandleEvents
	public void pathOfLightCounter(EventContext context, AbilityUsedEvent event) {
		// 0x4D63 はボス自身を対象にした光の導き
		if (event.abilityIdMatches(0x4D63)) {
			pathOfLightCounter++;
		}
	}

	private final ModifiableCallout<BuffApplied> getFinalTower = new ModifiableCallout<>("Light Rampant: Get Final Tower", "Get Final Tower");
	private final ModifiableCallout<BuffApplied> avoidFinalTower = new ModifiableCallout<>("Light Rampant: Avoid Final Tower", "Avoid Final Tower");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> finalTowerSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0x8D1) && pathOfLightCounter == 2 && !finalTowerCalled,
			(e1, s) -> {
				finalTowerCalled = true;
				// 最後の光の導きのスタックが揃うのを待つ
				s.waitMs(500);
				s.updateCall(playerLightsteeped >= LIGHTSTEEPED_AVOID_STACKS ? avoidFinalTower : getFinalTower, e1);
			});

	@NpcCastCallout(0x4D80)
	private final ModifiableCallout<AbilityCastStart> banish3 = ModifiableCallout.durationBasedCall("Banish III", "Stack");

	@NpcCastCallout(0x4D81)
	private final ModifiableCallout<AbilityCastStart> banish3Divided = ModifiableCallout.durationBasedCall("Banish III Divided", "Spread");

	// ハート・アサンダー

	@HandleEvents
	public void heartAsunder(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x4DAC) || asunderEast != null || !event.getTarget().isThePlayer()) {
			return;
		}
		// 左右の足場は x=70 付近と x=130 付近にある
		Position pos = state.getLatestCombatantData(event.getSource()).getPos();
		if (pos != null) {
			asunderEast = pos.x() > ARENA_CENTER;
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> aqueousAetherStun = new ModifiableCallout<>("Aqueous Aether", "Stun Aqueous Aether");
	private final RepeatSuppressor asunderSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void aqueousAether(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x4DAC) || !asunderSupp.check(event)) {
			return;
		}
		asunderCount++;
		// 1回目と3回目のハート・アサンダーで両側に水のエーテルが出る
		if ((asunderCount == 1 || asunderCount == 3) && canStun()) {
			context.accept(aqueousAetherStun.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> earthenAetherStoneskin = ModifiableCallout.durationBasedCall("Earthen Aether: Stoneskin", "Interrupt {event.source}");

	@HandleEvents
	public void earthenAetherStoneskin(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x4D85) || asunderEast == null || !canSilence()) {
			return;
		}
		Position pos = state.getLatestCombatantData(event.getSource()).getPos();
		if (pos == null) {
			return;
		}
		// 自分の足場側の土のエーテルだけを対象にする
		if ((pos.x() > ARENA_CENTER) == asunderEast) {
			context.accept(earthenAetherStoneskin.getModified(event));
		}
	}

	// アク・モーン

	private final ModifiableCallout<AbilityCastStart> akhMornOnYou = ModifiableCallout.durationBasedCall("Akh Morn: On You", "Akh Morn on You");
	private final ModifiableCallout<AbilityCastStart> akhMornOthers = ModifiableCallout.durationBasedCall("Akh Morn: On Others", "Akh Morn on {players}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> akhMornSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x4D98, 0x4D79),
			(e1, s) -> {
				if (e1.getTarget().isThePlayer()) {
					s.updateCall(akhMornOnYou, e1);
				}
				List<AbilityCastStart> casts = new ArrayList<>();
				casts.add(e1);
				while (casts.size() < AKH_MORN_TARGET_COUNT) {
					AbilityCastStart next = s.waitEvent(AbilityCastStart.class, acs -> acs.abilityIdMatches(0x4D98, 0x4D79));
					casts.add(next);
					if (next.getTarget().isThePlayer()) {
						s.updateCall(akhMornOnYou, next);
					}
				}
				boolean onPlayer = casts.stream().anyMatch(cast -> cast.getTarget().isThePlayer());
				if (!onPlayer) {
					s.setParam("players", casts.stream().map(AbilityCastStart::getTarget).toList());
					s.updateCall(akhMornOthers, casts.get(casts.size() - 1));
				}
			});

	@NpcCastCallout(value = 0x4D7B, onYou = true)
	private final ModifiableCallout<AbilityCastStart> mornAfahOnYou = ModifiableCallout.durationBasedCall("Morn Afah: On You", "Morn Afah on You");

	private final ModifiableCallout<AbilityCastStart> mornAfahOther = ModifiableCallout.durationBasedCall("Morn Afah: On Other", "Morn Afah on {event.target}");

	@HandleEvents
	public void mornAfah(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x4D7B) && !event.getTarget().isThePlayer()) {
			context.accept(mornAfahOther.getModified(event));
		}
	}

	// 鏡の鏡3

	private final ModifiableCallout<AbilityCastStart> mirrorThreeFast = new ModifiableCallout<>("Mirror, Mirror 3: Directions (Fast)", "{dir1}, then {dir2}, then {dir3} (fast)", 10_000);
	private final ModifiableCallout<AbilityCastStart> mirrorThreeSlow = new ModifiableCallout<>("Mirror, Mirror 3: Directions (Slow)", "{dir1}, then {dir2}, then {dir3} (slow)", 10_000);
	private final ModifiableCallout<AbilityCastStart> mirrorThreeFallbackLeft = ModifiableCallout.durationBasedCall("Mirror, Mirror 3: Left (No Data)", "Left");
	private final ModifiableCallout<AbilityCastStart> mirrorThreeFallbackRight = ModifiableCallout.durationBasedCall("Mirror, Mirror 3: Right (No Data)", "Right");
	private final ModifiableCallout<AbilityUsedEvent> mirrorThreeDir2 = new ModifiableCallout<>("Mirror, Mirror 3: Second Direction", "{dir2}");
	private final ModifiableCallout<AbilityUsedEvent> mirrorThreeDir3 = new ModifiableCallout<>("Mirror, Mirror 3: Third Direction", "{dir3}");
	private final RepeatSuppressor mirrorThreeSupp = new RepeatSuppressor(Duration.ofSeconds(15));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> mirrorThreeSq = SqtTemplates.sq(30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x4D75, 0x4D76) && wyrmsLament == 0 && mirrorThreeSupp.check(acs),
			(e1, s) -> {
				boolean isLeftCleave = e1.abilityIdMatches(0x4D75);
				// 北を向いて瞬間移動したあと、詠唱開始と同時に振り向くので少し待つ
				s.waitThenRefreshCombatants(250);
				Position shivaPos = state.getLatestCombatantData(e1.getSource()).getPos();
				if (shivaPos == null) {
					s.updateCall(isLeftCleave ? mirrorThreeFallbackRight : mirrorThreeFallbackLeft, e1);
					return;
				}
				boolean isFacingNorth = Math.abs(shivaPos.getHeading()) > FACING_NORTH_MIN_HEADING;
				// 青い鏡が西なら時計回りに安置が移る
				boolean isClockwise = mirrorMap.get(ArenaSector.WEST) == MirrorColor.BLUE;
				boolean isFirstSafeWest = isFacingNorth != isLeftCleave;
				boolean isFirstSafeNorth = isClockwise != isLeftCleave;
				ArenaSector dir1;
				if (isFirstSafeNorth) {
					dir1 = isFirstSafeWest ? ArenaSector.NORTHWEST : ArenaSector.NORTHEAST;
				}
				else {
					dir1 = isFirstSafeWest ? ArenaSector.SOUTHWEST : ArenaSector.SOUTHEAST;
				}
				int rot = isClockwise ? 1 : -1;
				int len = MIRROR_THREE_CLOCK.length;
				int idx1 = List.of(MIRROR_THREE_CLOCK).indexOf(dir1);
				int idx2 = (idx1 + rot + len) % len;
				int idx3 = (idx2 + rot + len) % len;
				ArenaSector dir2 = MIRROR_THREE_CLOCK[idx2];
				ArenaSector dir3 = MIRROR_THREE_CLOCK[idx3];
				boolean isFast = isClockwise
						? (dir1 == ArenaSector.NORTHWEST || dir1 == ArenaSector.SOUTHEAST)
						: (dir1 == ArenaSector.NORTHEAST || dir1 == ArenaSector.SOUTHWEST);
				s.setParam("dir1", dir1);
				s.setParam("dir2", dir2);
				s.setParam("dir3", dir3);
				s.updateCall(isFast ? mirrorThreeFast : mirrorThreeSlow, e1);
				AbilityUsedEvent second = s.waitEvent(AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x4D90, 0x4D91));
				s.updateCall(mirrorThreeDir2, second);
				AbilityUsedEvent third = s.waitEvent(AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x4DBB, 0x4DBC));
				s.updateCall(mirrorThreeDir3, third);
			});

	private final ModifiableCallout<AbilityCastStart> hallowedWingsKnockback = new ModifiableCallout<AbilityCastStart>("Hallowed Wings: Uptime Knockback", "Knockback", 1_400)
			.disabledByDefault()
			.extendedDescription("Calls the Mirror, Mirror 4 double knockback right after the boss turns, leaving under 1.4s to react (uptime strategy).");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> hallowedWingsKnockbackSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x4D77),
			(e1, s) -> {
				// アームズレングスが間に合うぎりぎりで知らせる
				s.waitMs(8_600);
				s.updateCall(hallowedWingsKnockback, e1);
			});

	// 竜の嘆き

	@NpcCastCallout(0x4D7C)
	private final ModifiableCallout<AbilityCastStart> wyrmsLamentCast = ModifiableCallout.durationBasedCall("Wyrm's Lament", "Raidwide");

	@HandleEvents
	public void wyrmsLamentCounter(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x4D7C)) {
			wyrmsLament++;
		}
	}

	private final ModifiableCallout<AbilityCastStart> wyrmsLamentMirrorLeft = ModifiableCallout.durationBasedCall("Wyrm's Lament Mirror: Left", "Left");
	private final ModifiableCallout<AbilityCastStart> wyrmsLamentMirrorRight = ModifiableCallout.durationBasedCall("Wyrm's Lament Mirror: Right", "Right");

	@HandleEvents
	public void wyrmsLamentMirror(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x4D75, 0x4D76) && wyrmsLament == 1) {
			context.accept((event.abilityIdMatches(0x4D75) ? wyrmsLamentMirrorRight : wyrmsLamentMirrorLeft).getModified(event));
		}
	}

	private static Integer wyrmNumber(BuffApplied event, boolean firstLament, int[] firstDurations, int[] secondDurations) {
		int seconds = (int) Math.ceil(event.getInitialDuration().toMillis() / 1000.0);
		int[] durations = firstLament ? firstDurations : secondDurations;
		for (int i = 0; i < durations.length; i++) {
			if (durations[i] == seconds) {
				return i + 1;
			}
		}
		return null;
	}

	// 付与時間から自分の処理順を割り出す
	private static final int[] WYRMCLAW_FIRST = {14, 22, 30, 38};
	private static final int[] WYRMCLAW_SECOND = {22, 38};
	private static final int[] WYRMFANG_FIRST = {20, 28, 36, 44};
	private static final int[] WYRMFANG_SECOND = {28, 44};

	private final ModifiableCallout<BuffApplied> wyrmclaw = ModifiableCallout.<BuffApplied>durationBasedCall("Wyrmclaw", "Red {num}").autoIcon();
	private final ModifiableCallout<BuffApplied> wyrmfang = ModifiableCallout.<BuffApplied>durationBasedCall("Wyrmfang", "Blue {num}").autoIcon();

	@HandleEvents
	public void wyrmBuffs(EventContext context, BuffApplied event) {
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		if (event.buffIdMatches(0x8D2)) {
			wyrmclawNumber = wyrmNumber(event, wyrmsLament == 1, WYRMCLAW_FIRST, WYRMCLAW_SECOND);
			context.accept(wyrmclaw.getModified(event, Map.<String, Object>of("num", wyrmclawNumber == null ? "?" : wyrmclawNumber)));
		}
		else if (event.buffIdMatches(0x8D3)) {
			wyrmfangNumber = wyrmNumber(event, wyrmsLament == 1, WYRMFANG_FIRST, WYRMFANG_SECOND);
			context.accept(wyrmfang.getModified(event, Map.<String, Object>of("num", wyrmfangNumber == null ? "?" : wyrmfangNumber)));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> popHead = new ModifiableCallout<>("Wyrm's Lament: Pop Head", "Pop Head {num}");
	private final ModifiableCallout<AbilityUsedEvent> getPuddle = new ModifiableCallout<>("Wyrm's Lament: Get Puddle", "Get Puddle {num}");

	@HandleEvents
	public void wyrmsLamentReminder(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x4D75, 0x4D76) || !event.isFirstTarget() || wyrmsLament != 1) {
			return;
		}
		wyrmsLamentMirrorCount++;
		if (wyrmclawNumber != null && wyrmclawNumber == wyrmsLamentMirrorCount + 1) {
			context.accept(popHead.getModified(event, Map.<String, Object>of("num", wyrmclawNumber)));
		}
		else if (wyrmfangNumber != null && wyrmfangNumber == wyrmsLamentMirrorCount) {
			context.accept(getPuddle.getModified(event, Map.<String, Object>of("num", wyrmfangNumber)));
		}
	}

	// その他

	private final ModifiableCallout<AbilityUsedEvent> drachenArmor = new ModifiableCallout<>("Drachen Armor", "Move Away");
	private final ModifiableCallout<AbilityUsedEvent> reflectedDrachenArmor = new ModifiableCallout<>("Reflected Drachen Armor", "Move Away");

	@HandleEvents
	public void drachenArmor(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x4DD2)) {
			context.accept(drachenArmor.getModified(event));
		}
		else if (event.abilityIdMatches(0x4DC2)) {
			context.accept(reflectedDrachenArmor.getModified(event));
		}
	}

	@NpcCastCallout(0x4D82)
	private final ModifiableCallout<AbilityCastStart> holy = ModifiableCallout.durationBasedCall("Holy", "Out");

	@NpcCastCallout(0x4D83)
	private final ModifiableCallout<AbilityCastStart> holyDivided = ModifiableCallout.durationBasedCall("Holy Divided", "In");

	@NpcCastCallout(0x4D68)
	private final ModifiableCallout<AbilityCastStart> twinStillness = ModifiableCallout.durationBasedCall("Twin Stillness", "Back, then Front");

	@NpcCastCallout(0x4D69)
	private final ModifiableCallout<AbilityCastStart> twinSilence = ModifiableCallout.durationBasedCall("Twin Silence", "Front, then Back");

	@NpcCastCallout(0x4D6F)
	private final ModifiableCallout<AbilityCastStart> spitefulDance = ModifiableCallout.durationBasedCall("Spiteful Dance", "Out, then In");

	@NpcCastCallout(0x4D70)
	private final ModifiableCallout<AbilityCastStart> embitteredDance = ModifiableCallout.durationBasedCall("Embittered Dance", "In, then Out");

	private final ModifiableCallout<AbilityUsedEvent> icelitCleanseDps = new ModifiableCallout<>("Icelit Dragonsong: Cleanse (Cleanser)", "Cleanse DPS Only");
	private final ModifiableCallout<AbilityUsedEvent> icelitExuviation = new ModifiableCallout<>("Icelit Dragonsong: Cleanse (BLU)", "Exuviation");
	private final RepeatSuppressor icelitSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void icelitDragonsongCleanse(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x4D7D) || !canCleanse() || !icelitSupp.check(event)) {
			return;
		}
		boolean blu = state.playerJobMatches(job -> job == Job.BLU);
		context.accept((blu ? icelitExuviation : icelitCleanseDps).getModified(event));
	}

	private final ModifiableCallout<AbilityCastStart> banish = ModifiableCallout.durationBasedCall("Banish (Tank)", "Tank Stack in Tower");
	private final ModifiableCallout<AbilityCastStart> banishDivided = ModifiableCallout.durationBasedCall("Banish Divided (Tank)", "Tank Spread in Tower");

	@HandleEvents
	public void banishTowers(EventContext context, AbilityCastStart event) {
		if (!state.playerJobMatches(Job::isTank)) {
			return;
		}
		if (event.abilityIdMatches(0x4D7E)) {
			context.accept(banish.getModified(event));
		}
		else if (event.abilityIdMatches(0x4D7F)) {
			context.accept(banishDivided.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> houseOfLightGetTower = ModifiableCallout.durationBasedCall("The House of Light: Get Final Tower", "Protean, then Get Final Tower");
	private final ModifiableCallout<AbilityCastStart> houseOfLightAvoidTower = ModifiableCallout.durationBasedCall("The House of Light: Avoid Final Tower", "Protean, then Avoid Final Tower");

	@HandleEvents
	public void houseOfLight(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x4D64)) {
			context.accept((playerLightsteeped >= LIGHTSTEEPED_AVOID_STACKS ? houseOfLightAvoidTower : houseOfLightGetTower).getModified(event));
		}
	}

}
