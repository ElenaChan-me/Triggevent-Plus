package gg.xp.xivsupport.triggers.cactbot.ew;

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
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.RawXivCombatantInfo;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SequentialTriggerController;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;
import gg.xp.xivsupport.models.XivPlayerCharacter;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/dungeon/another_aloalo_island.ts をもとに移植
@CalloutRepo(name = "Another Aloalo Island", duty = KnownDuty.AnotherAloaloIsland)
public class AnotherAloaloIsland extends AutoChildEventHandler implements FilteredEventHandler {

	// 同時に付与されるデバフなどをまとめて扱うための待ち時間
	private static final int COLLECT_WINDOW_MS = 500;

	private static final long SPRING_CRYSTAL_NAME_ID = 12607;
	private static final long HOMING_PATTERN_NAME_ID = 12507;
	// 横向きのクリスタルは向きが 0、縦向きは -π/2 になる
	private static final double CRYSTAL_HEADING_EPSILON = 0.1;
	// クリスタルは -15, -5, 5, 15 のいずれかに置かれる
	private static final double CRYSTAL_OUTER_EDGE = 10;
	private static final double CRYSTAL_SAME_COLUMN = 1;
	private static final int KETU_BUFF_COUNT = 4;
	private static final int SPRING_CRYSTAL_COUNT = 4;
	// 判明してすぐでは早すぎるので、デバフを把握したころに知らせる
	private static final int SPRING_CRYSTALS_2_DELAY_MS = 5_000;

	private static final int PLANAR_TACTICS_COUNT = 6;
	private static final int PLANAR_TACTICS_STACKS = 2;
	private static final int PLANAR_TACTICS_NUMS = 4;
	private static final int MAX_TACTICS_NUM = 4;

	private static final int TRICK_RELOAD_BULLETS = 8;
	private static final int DART_COUNT = 3;
	private static final int PINWHEEL_DART_COUNT = 2;
	private static final double DARTBOARD_CENTER_X = -200;
	private static final double DARTBOARD_CENTER_Y = 0;

	private static final long HYDROBULLET = 0x8AB8;
	private static final long HYDROFALL = 0x8AB4;
	private static final long FOAMY_FETTERS = 0xECC;
	private static final long BUBBLE_WEAVE = 0xE9F;
	private static final long HYDROFALL_TARGET = 0xEA3;
	private static final long SURGE_VECTOR = 0xE8B;
	private static final long SUBTRACTIVE_SUPPRESSOR_ALPHA = 0xE8C;
	private static final long LOCKED_AND_LOADED = 0x8925;
	private static final long MISLOAD = 0x8926;
	private static final long BULLS_EYE = 0xE9E;
	private static final long TETHER_MARKER = 0x61;

	private enum KetuBuff {
		BUBBLE,
		FETTERS
	}

	private enum Trap {
		STACK,
		SPREAD
	}

	private XivState state;

	public AnotherAloaloIsland(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AnotherAloaloIsland);
	}

	private int ketuSpringCrystalCount;
	private final List<Position> ketuCrystals = new ArrayList<>();
	private int ketuHydroBuffCount;
	private Boolean ketuHydroBuffIsSpreadFirst;
	private boolean ketuHydroBuffIsRoleStacks;
	private KetuBuff ketuBuff;
	private XivCombatant ketuBuffPartner;
	private Boolean ketuTwintidesNextIn;
	private Boolean lalaBossClockwise;
	private Integer lalaBossTimes;
	private Integer lalaUnseenDir;
	private Integer lalaPlayerTimes;
	private Boolean lalaPlayerClockwise;
	private boolean lalaPlanarTacticsDone;
	private boolean lalaSpatialTacticsDone;
	private int staticeBulletCount;
	private Integer staticeTriggerHappy;
	private Integer staticePopTriggerHappyNum;
	private final Deque<Trap> staticeTrapshooting = new ArrayDeque<>();
	private final List<XivCombatant> staticeDartTargets = new ArrayList<>();
	private boolean staticeIsPinwheelingDartboard;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		ketuSpringCrystalCount = 0;
		ketuCrystals.clear();
		ketuHydroBuffCount = 0;
		ketuHydroBuffIsSpreadFirst = null;
		ketuHydroBuffIsRoleStacks = false;
		ketuBuff = null;
		ketuBuffPartner = null;
		ketuTwintidesNextIn = null;
		lalaBossClockwise = null;
		lalaBossTimes = null;
		lalaUnseenDir = null;
		lalaPlayerTimes = null;
		lalaPlayerClockwise = null;
		lalaPlanarTacticsDone = false;
		lalaSpatialTacticsDone = false;
		staticeBulletCount = 0;
		staticeTriggerHappy = null;
		staticePopTriggerHappyNum = null;
		staticeTrapshooting.clear();
		staticeDartTargets.clear();
		staticeIsPinwheelingDartboard = false;
	}

	private boolean canSilence() {
		return state.playerJobMatches(job -> job.caresAboutInterrupt() || job == Job.BLU);
	}

	private boolean canCleanse() {
		return state.playerJobMatches(job -> job.isHealer() || job == Job.BRD || job == Job.BLU);
	}

	private boolean isDps(XivCombatant cbt) {
		return state.getLatestCombatantData(cbt) instanceof XivPlayerCharacter pc && pc.getJob() != null && pc.getJob().isDps();
	}

	private boolean isStandardLightParty() {
		List<XivPlayerCharacter> party = state.getPartyList();
		long supports = party.stream().filter(pc -> pc.getJob() != null && pc.getJob().isSupport()).count();
		long dps = party.stream().filter(pc -> pc.getJob() != null && pc.getJob().isDps()).count();
		return supports == 2 && dps == 2;
	}

	private static boolean containsEntity(List<? extends XivCombatant> list, XivCombatant cbt) {
		return list.stream().anyMatch(other -> other.getId() == cbt.getId());
	}

	private static <Y> List<Y> collectRest(SequentialTriggerController<BaseEvent> s, Y first, int total, Class<Y> eventClass, Predicate<Y> filter) {
		List<Y> out = new ArrayList<>();
		out.add(first);
		if (total > 1) {
			out.addAll(s.groupEvents(total - 1, COLLECT_WINDOW_MS, eventClass, true, List.of(filter)).get(filter));
		}
		return out;
	}

	private static void onTarget(EventContext context, AbilityCastStart event, ModifiableCallout<AbilityCastStart> onYou, ModifiableCallout<AbilityCastStart> onOther) {
		context.accept((event.getTarget().isThePlayer() ? onYou : onOther).getModified(event));
	}

	private static long nameIdOf(RawAddCombatantEvent event) {
		RawXivCombatantInfo info = event.getFullInfo();
		return info != null ? info.getBnpcNameId() : event.getEntity().getbNpcNameId();
	}

	private Position posOf(RawAddCombatantEvent event) {
		RawXivCombatantInfo info = event.getFullInfo();
		if (info != null && info.getPos() != null) {
			return info.getPos();
		}
		return state.getLatestCombatantData(event.getEntity()).getPos();
	}

	// 1体目の雑魚

	private final ModifiableCallout<AbilityCastStart> leadHookOnYou = ModifiableCallout.durationBasedCall("Lead Hook: On You", "3x Tankbuster on You");
	private final ModifiableCallout<AbilityCastStart> leadHookOther = ModifiableCallout.durationBasedCall("Lead Hook: On Other", "3x Tankbuster on {event.target}");
	private final ModifiableCallout<AbilityCastStart> sharpStrikeOnYou = ModifiableCallout.durationBasedCall("Sharp Strike: On You", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> sharpStrikeOther = ModifiableCallout.durationBasedCall("Sharp Strike: On Other", "Buster on {event.target}");
	private final ModifiableCallout<AbilityCastStart> waterIIIOnYou = ModifiableCallout.durationBasedCall("Water III: On You", "Stack on You");
	private final ModifiableCallout<AbilityCastStart> waterIIIOther = ModifiableCallout.durationBasedCall("Water III: On Other", "Stack on {event.target}");
	private final ModifiableCallout<AbilityCastStart> crossAttackOnYou = ModifiableCallout.durationBasedCall("Cross Attack: On You", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> crossAttackOther = ModifiableCallout.durationBasedCall("Cross Attack: On Other", "Buster on {event.target}");

	@HandleEvents
	public void firstTrashTargeted(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8C6E)) {
			onTarget(context, event, leadHookOnYou, leadHookOther);
		}
		else if (event.abilityIdMatches(0x8C63)) {
			onTarget(context, event, sharpStrikeOnYou, sharpStrikeOther);
		}
		else if (event.abilityIdMatches(0x8C64)) {
			onTarget(context, event, waterIIIOnYou, waterIIIOther);
		}
		else if (event.abilityIdMatches(0x8BBB)) {
			onTarget(context, event, crossAttackOnYou, crossAttackOther);
		}
	}

	@NpcCastCallout(0x8BB8)
	private final ModifiableCallout<AbilityCastStart> tailScrew = ModifiableCallout.durationBasedCall("Tail Screw", "Move Away");

	@NpcCastCallout(0x8BB9)
	private final ModifiableCallout<AbilityCastStart> bubbleShower = ModifiableCallout.durationBasedCall("Bubble Shower", "Back, then Front");

	private final ModifiableCallout<AbilityUsedEvent> crabDribble = new ModifiableCallout<>("Crab Dribble", "Go Front");
	private final RepeatSuppressor crabDribbleSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void crabDribble(EventContext context, AbilityUsedEvent event) {
		// クラブドリブルは詠唱が短いので、バブルシャワーの発動を起点にする
		if (event.abilityIdMatches(0x8BB9) && crabDribbleSupp.check(event)) {
			context.accept(crabDribble.getModified(event));
		}
	}

	@NpcCastCallout(0x8BBD)
	private final ModifiableCallout<AbilityCastStart> hydrocannon = ModifiableCallout.durationBasedCall("Hydrocannon", "Get Behind");

	@NpcCastCallout(0x8BBF)
	private final ModifiableCallout<AbilityCastStart> expulsion = ModifiableCallout.durationBasedCall("Expulsion", "Out");

	@NpcCastCallout(0x8BBE)
	private final ModifiableCallout<AbilityCastStart> electricWhorl = ModifiableCallout.durationBasedCall("Electric Whorl", "Get Under");

	@NpcCastCallout(value = 0x8C65, onYou = true)
	private final ModifiableCallout<AbilityCastStart> hydroshot = ModifiableCallout.durationBasedCall("Hydroshot", "Knockback on You");

	// ケトゥドゥケ

	@NpcCastCallout(0x8AD4)
	private final ModifiableCallout<AbilityCastStart> tidalRoar = ModifiableCallout.durationBasedCall("Tidal Roar", "Raidwide with Bleed");

	@NpcCastCallout({0x8AC5, 0x8AAD})
	private final ModifiableCallout<AbilityCastStart> bubbleNet = ModifiableCallout.durationBasedCall("Bubble Net", "Raidwide");

	@HandleEvents
	public void springCrystalsCounter(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8AA8)) {
			// AddCombatant が詠唱より先に来ることがあるので、ここではクリスタルの記録を消さない
			ketuSpringCrystalCount++;
		}
		else if (event.abilityIdMatches(0x8AAB, 0x8AAC)) {
			ketuCrystals.clear();
		}
	}

	private static boolean isSpringCrystal(RawAddCombatantEvent event) {
		return nameIdOf(event) == SPRING_CRYSTAL_NAME_ID;
	}

	private static boolean isHorizontalCrystal(Position pos) {
		return Math.abs(pos.getHeading()) < CRYSTAL_HEADING_EPSILON;
	}

	@HandleEvents
	public void springCrystalCollect(EventContext context, RawAddCombatantEvent event) {
		if (!isSpringCrystal(event)) {
			return;
		}
		Position pos = posOf(event);
		if (pos != null) {
			ketuCrystals.add(pos);
		}
	}

	private final ModifiableCallout<RawAddCombatantEvent> springCrystalsNorthSouth = new ModifiableCallout<>("Spring Crystals 2: North/South Safe", "North/South");
	private final ModifiableCallout<RawAddCombatantEvent> springCrystalsEastWest = new ModifiableCallout<>("Spring Crystals 2: East/West Safe", "East/West");
	private final ModifiableCallout<RawAddCombatantEvent> springCrystalsCorners = new ModifiableCallout<>("Spring Crystals 2: Corners Safe", "Corners");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> springCrystals2Sq = SqtTemplates.sq(10_000,
			// 詠唱より先にクリスタルが出た場合も拾えるよう、1回目のカウント中からも開始する
			RawAddCombatantEvent.class, e -> isSpringCrystal(e) && (ketuSpringCrystalCount == 1 || ketuSpringCrystalCount == 2),
			(e1, s) -> {
				s.waitMs(SPRING_CRYSTALS_2_DELAY_MS);
				if (ketuSpringCrystalCount != 2 || ketuCrystals.size() != SPRING_CRYSTAL_COUNT) {
					return;
				}
				List<Position> horizontal = ketuCrystals.stream().filter(AnotherAloaloIsland::isHorizontalCrystal).toList();
				List<Position> vertical = ketuCrystals.stream().filter(pos -> !isHorizontalCrystal(pos)).toList();
				if (horizontal.size() != 2 || vertical.size() != 2) {
					return;
				}
				if (vertical.stream().anyMatch(pos -> Math.abs(pos.y()) > CRYSTAL_OUTER_EDGE)) {
					s.updateCall(springCrystalsEastWest, e1);
				}
				else if (horizontal.stream().anyMatch(pos -> Math.abs(pos.x()) > CRYSTAL_OUTER_EDGE)) {
					s.updateCall(springCrystalsNorthSouth, e1);
				}
				else {
					s.updateCall(springCrystalsCorners, e1);
				}
			});

	private final ModifiableCallout<BuffApplied> fettersWithPartner = new ModifiableCallout<>("Foamy Fetters (with Partner)", "Fetters (with {player})");
	private final ModifiableCallout<BuffApplied> fettersNoPartner = new ModifiableCallout<>("Foamy Fetters (No Partner)", "Fetters");
	private final ModifiableCallout<BuffApplied> bubbleWithPartner = new ModifiableCallout<>("Bubble Weave (with Partner)", "Bubble (with {player})");
	private final ModifiableCallout<BuffApplied> bubbleNoPartner = new ModifiableCallout<>("Bubble Weave (No Partner)", "Bubble");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> ketuBuffSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(FOAMY_FETTERS, BUBBLE_WEAVE),
			(e1, s) -> {
				List<BuffApplied> buffs = collectRest(s, e1, KETU_BUFF_COUNT, BuffApplied.class, ba -> ba.buffIdMatches(FOAMY_FETTERS, BUBBLE_WEAVE));
				BuffApplied mine = buffs.stream().filter(ba -> ba.getTarget().isThePlayer()).findFirst().orElse(null);
				if (mine == null) {
					return;
				}
				long myBuffId = mine.getBuff().getId();
				ketuBuff = myBuffId == FOAMY_FETTERS ? KetuBuff.FETTERS : KetuBuff.BUBBLE;
				ketuBuffPartner = buffs.stream()
						.filter(ba -> !ba.getTarget().isThePlayer() && ba.buffIdMatches(myBuffId))
						.map(BuffApplied::getTarget)
						.findFirst()
						.orElse(null);
				// フリッパーゲイルと重なる回は後でまとめて知らせる
				if (ketuSpringCrystalCount == 1 || ketuSpringCrystalCount == 4) {
					return;
				}
				boolean fetters = ketuBuff == KetuBuff.FETTERS;
				if (ketuBuffPartner == null) {
					s.updateCall(fetters ? fettersNoPartner : bubbleNoPartner, mine);
				}
				else {
					s.setParam("player", ketuBuffPartner);
					s.updateCall(fetters ? fettersWithPartner : bubbleWithPartner, mine);
				}
			});

	private final ModifiableCallout<AbilityCastStart> bubbleSpread = ModifiableCallout.durationBasedCall("Hydro Buff 1: Bubble + Spread", "Bubble + Spread");
	private final ModifiableCallout<AbilityCastStart> bubbleStacks = ModifiableCallout.durationBasedCall("Hydro Buff 1: Bubble + Stacks", "Bubble + Stacks");
	private final ModifiableCallout<AbilityCastStart> fettersSpread = ModifiableCallout.durationBasedCall("Hydro Buff 1: Fetters + Spread", "Fetters + Spread");
	private final ModifiableCallout<AbilityCastStart> fettersStacks = ModifiableCallout.durationBasedCall("Hydro Buff 1: Fetters + Stacks", "Fetters + Stacks");
	private final ModifiableCallout<AbilityCastStart> bubbleSpreadPartner = ModifiableCallout.durationBasedCall("Hydro Buff 1: Bubble + Spread (Nonstandard Partner)", "Bubble + Spread (with {player})");
	private final ModifiableCallout<AbilityCastStart> bubbleStacksPartner = ModifiableCallout.durationBasedCall("Hydro Buff 1: Bubble + Stacks (Nonstandard Partner)", "Bubble + Stacks (with {player})");
	private final ModifiableCallout<AbilityCastStart> fettersSpreadPartner = ModifiableCallout.durationBasedCall("Hydro Buff 1: Fetters + Spread (Nonstandard Partner)", "Fetters + Spread (with {player})");
	private final ModifiableCallout<AbilityCastStart> fettersStacksPartner = ModifiableCallout.durationBasedCall("Hydro Buff 1: Fetters + Stacks (Nonstandard Partner)", "Fetters + Stacks (with {player})");
	private final ModifiableCallout<AbilityCastStart> bubbleAnything = ModifiableCallout.<AbilityCastStart>durationBasedCall("Hydro Buff 1: Bubble Position", "Diagonal of Vertical / Next to Horizontal")
			.extendedDescription("Assumes you always pick a square in the same quadrant as the crystal. 'Next to' means horizontally east/west of it.");
	private final ModifiableCallout<AbilityCastStart> fettersSpreadSplit = ModifiableCallout.durationBasedCall("Hydro Buff 1: Fetters Spread (Split Layout)", "Diagonal of Vertical");
	private final ModifiableCallout<AbilityCastStart> fettersSpreadColumn = ModifiableCallout.durationBasedCall("Hydro Buff 1: Fetters Spread (Column Layout)", "Next to Vertical");
	private final ModifiableCallout<AbilityCastStart> fettersStackSplit = ModifiableCallout.durationBasedCall("Hydro Buff 1: Fetters Stack (Split Layout)", "On Horizontal");
	private final ModifiableCallout<AbilityCastStart> fettersStackColumn = ModifiableCallout.durationBasedCall("Hydro Buff 1: Fetters Stack (Column Layout)", "Diagonal of Horizontal");

	private final ModifiableCallout<AbilityCastStart> spreadThenStacks = ModifiableCallout.durationBasedCall("Hydro Buff Double: Spread First", "Spread, then Stacks");
	private final ModifiableCallout<AbilityCastStart> stacksThenSpread = ModifiableCallout.durationBasedCall("Hydro Buff Double: Stacks First", "Stacks, then Spread");

	@HandleEvents
	public void hydroBuff(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(HYDROBULLET, HYDROFALL)) {
			return;
		}
		ketuHydroBuffCount++;
		ketuHydroBuffIsSpreadFirst = null;
		ketuHydroBuffIsRoleStacks = false;
		boolean isSpread = event.abilityIdMatches(HYDROBULLET);
		if (ketuHydroBuffCount == 1 || ketuHydroBuffCount == 6) {
			hydroBuffFirst(context, event, isSpread);
		}
		else if (ketuHydroBuffCount == 2 || ketuHydroBuffCount == 5) {
			ketuHydroBuffIsSpreadFirst = isSpread;
			context.accept((isSpread ? spreadThenStacks : stacksThenSpread).getModified(event));
		}
	}

	private void hydroBuffFirst(EventContext context, AbilityCastStart event, boolean isSpread) {
		if (ketuBuff == null) {
			return;
		}
		boolean bubble = ketuBuff == KetuBuff.BUBBLE;
		XivCombatant partner = ketuBuffPartner;
		// 通常のライトパーティ構成でロール同士が組むなら相方の名前は不要
		boolean sameRolePartner = partner != null && isDps(state.getPlayer()) == isDps(partner) && isStandardLightParty();
		if (partner == null || sameRolePartner) {
			ModifiableCallout<AbilityCastStart> call = bubble ? (isSpread ? bubbleSpread : bubbleStacks) : (isSpread ? fettersSpread : fettersStacks);
			context.accept(call.getModified(event));
		}
		else {
			ModifiableCallout<AbilityCastStart> call = bubble ? (isSpread ? bubbleSpreadPartner : bubbleStacksPartner) : (isSpread ? fettersSpreadPartner : fettersStacksPartner);
			context.accept(call.getModified(event, Map.<String, Object>of("player", partner)));
		}

		if (bubble) {
			context.accept(bubbleAnything.getModified(event));
			return;
		}
		// 横向きクリスタルと縦向きクリスタルが同じ列にあるかどうかで立ち位置が変わる
		List<Position> horizontal = ketuCrystals.stream().filter(AnotherAloaloIsland::isHorizontalCrystal).toList();
		List<Position> vertical = ketuCrystals.stream().filter(pos -> !isHorizontalCrystal(pos)).toList();
		if (horizontal.size() != 2 || vertical.size() != 2) {
			return;
		}
		double firstHorizX = horizontal.get(0).x();
		boolean isSplitLayout = vertical.stream().noneMatch(pos -> Math.abs(pos.x() - firstHorizX) < CRYSTAL_SAME_COLUMN);
		if (isSpread) {
			context.accept((isSplitLayout ? fettersSpreadSplit : fettersSpreadColumn).getModified(event));
		}
		else {
			context.accept((isSplitLayout ? fettersStackSplit : fettersStackColumn).getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> hydroFollowupStacks = new ModifiableCallout<>("Hydro Buff Double Followup: Stacks", "Stacks");
	private final ModifiableCallout<AbilityUsedEvent> hydroFollowupRoleStacks = new ModifiableCallout<>("Hydro Buff Double Followup: Role Stacks", "Role Stacks");
	private final ModifiableCallout<AbilityUsedEvent> hydroFollowupSpread = new ModifiableCallout<>("Hydro Buff Double Followup: Spread", "Spread");
	private final RepeatSuppressor hydroFollowupSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	@HandleEvents
	public void hydroBuffFollowup(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x8ABA, 0x8AB7) || !hydroFollowupSupp.check(event)) {
			return;
		}
		boolean wasSpread = event.abilityIdMatches(0x8ABA);
		if (wasSpread && Boolean.TRUE.equals(ketuHydroBuffIsSpreadFirst)) {
			context.accept((ketuHydroBuffIsRoleStacks ? hydroFollowupRoleStacks : hydroFollowupStacks).getModified(event));
		}
		else if (!wasSpread && Boolean.FALSE.equals(ketuHydroBuffIsSpreadFirst)) {
			context.accept(hydroFollowupSpread.getModified(event));
		}
	}

	private final ModifiableCallout<BuffApplied> hydrofallRoleStacks = new ModifiableCallout<>("Hydrofall: Role Stacks", "Role Stacks");
	private final ModifiableCallout<BuffApplied> hydrofallSpreadThenRoleStacks = new ModifiableCallout<>("Hydrofall: Spread, then Role Stacks", "Spread, then Role Stacks");
	private final ModifiableCallout<BuffApplied> hydrofallRoleStacksThenSpread = new ModifiableCallout<>("Hydrofall: Role Stacks, then Spread", "Role Stacks, then Spread");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> hydrofallRoleStackSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(HYDROFALL_TARGET),
			(e1, s) -> {
				List<BuffApplied> stacks = collectRest(s, e1, 2, BuffApplied.class, ba -> ba.buffIdMatches(HYDROFALL_TARGET));
				if (stacks.size() != 2 || !isStandardLightParty()) {
					return;
				}
				// 片方が DPS、片方がサポートに付いた場合（誰かが倒れているなど）はロールで頭割りする
				if (isDps(stacks.get(0).getTarget()) == isDps(stacks.get(1).getTarget())) {
					return;
				}
				ketuHydroBuffIsRoleStacks = true;
				BuffApplied last = stacks.get(stacks.size() - 1);
				if (Boolean.TRUE.equals(ketuHydroBuffIsSpreadFirst)) {
					s.updateCall(hydrofallSpreadThenRoleStacks, last);
				}
				else if (Boolean.FALSE.equals(ketuHydroBuffIsSpreadFirst)) {
					s.updateCall(hydrofallRoleStacksThenSpread, last);
				}
				else {
					s.updateCall(hydrofallRoleStacks, last);
				}
			});

	private final ModifiableCallout<AbilityCastStart> recedingStacks = ModifiableCallout.durationBasedCall("Receding Twintides", "Out, then In + Stacks");
	private final ModifiableCallout<AbilityCastStart> recedingRoleStacks = ModifiableCallout.durationBasedCall("Receding Twintides (Role Stacks)", "Out, then In + Role Stacks");
	private final ModifiableCallout<AbilityCastStart> encroachingStacks = ModifiableCallout.durationBasedCall("Encroaching Twintides", "In, then Out + Stacks");
	private final ModifiableCallout<AbilityCastStart> encroachingRoleStacks = ModifiableCallout.durationBasedCall("Encroaching Twintides (Role Stacks)", "In, then Out + Role Stacks");
	private final ModifiableCallout<AbilityCastStart> angryKnockbackSpread = ModifiableCallout.durationBasedCall("Angry Seas: Spread", "Knockback, then Spread");
	private final ModifiableCallout<AbilityCastStart> angryKnockbackStacks = ModifiableCallout.durationBasedCall("Angry Seas: Stacks", "Knockback, then Stacks");
	private final ModifiableCallout<AbilityCastStart> angryKnockbackRoleStacks = ModifiableCallout.durationBasedCall("Angry Seas: Role Stacks", "Knockback, then Role Stacks");

	@HandleEvents
	public void twintidesAndAngrySeas(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8ACC)) {
			context.accept((ketuHydroBuffIsRoleStacks ? recedingRoleStacks : recedingStacks).getModified(event));
			ketuTwintidesNextIn = true;
		}
		else if (event.abilityIdMatches(0x8ACE)) {
			context.accept((ketuHydroBuffIsRoleStacks ? encroachingRoleStacks : encroachingStacks).getModified(event));
			ketuTwintidesNextIn = false;
		}
		else if (event.abilityIdMatches(0x8AC1)) {
			if (Boolean.TRUE.equals(ketuHydroBuffIsSpreadFirst)) {
				context.accept(angryKnockbackSpread.getModified(event));
			}
			else if (ketuHydroBuffIsRoleStacks) {
				context.accept(angryKnockbackRoleStacks.getModified(event));
			}
			else {
				context.accept(angryKnockbackStacks.getModified(event));
			}
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> twintidesInStacks = new ModifiableCallout<>("Twintides Followup: In + Stacks", "In + Stacks");
	private final ModifiableCallout<AbilityUsedEvent> twintidesInRoleStacks = new ModifiableCallout<>("Twintides Followup: In + Role Stacks", "In + Role Stacks");
	private final ModifiableCallout<AbilityUsedEvent> twintidesOutStacks = new ModifiableCallout<>("Twintides Followup: Out + Stacks", "Out + Stacks");
	private final ModifiableCallout<AbilityUsedEvent> twintidesOutRoleStacks = new ModifiableCallout<>("Twintides Followup: Out + Role Stacks", "Out + Role Stacks");
	private final RepeatSuppressor twintidesFollowupSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void twintidesFollowup(EventContext context, AbilityUsedEvent event) {
		// スフィアシャッターはダブルタイドの直後に来るので、これを合図に次の動きを知らせる
		if (!event.abilityIdMatches(0x8ABC) || !twintidesFollowupSupp.check(event) || ketuTwintidesNextIn == null) {
			return;
		}
		ModifiableCallout<AbilityUsedEvent> call;
		if (ketuTwintidesNextIn) {
			call = ketuHydroBuffIsRoleStacks ? twintidesInRoleStacks : twintidesInStacks;
		}
		else {
			call = ketuHydroBuffIsRoleStacks ? twintidesOutRoleStacks : twintidesOutStacks;
		}
		context.accept(call.getModified(event));
		ketuTwintidesNextIn = null;
	}

	// 2体目の雑魚

	private final ModifiableCallout<AbilityCastStart> ancientAeroIII = ModifiableCallout.durationBasedCall("Ancient Aero III", "Interrupt {event.source}");

	@HandleEvents
	public void ancientAeroIII(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8C4C) && canSilence()) {
			context.accept(ancientAeroIII.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> tornadoOnYou = ModifiableCallout.durationBasedCall("Tornado: On You", "Tornado on You");
	private final ModifiableCallout<AbilityCastStart> tornadoOther = ModifiableCallout.durationBasedCall("Tornado: On Other", "Away from {event.target}");
	private final ModifiableCallout<AbilityCastStart> gravityForceOnYou = ModifiableCallout.durationBasedCall("Gravity Force: On You", "Stack on You");
	private final ModifiableCallout<AbilityCastStart> gravityForceOther = ModifiableCallout.durationBasedCall("Gravity Force: On Other", "Stack on {event.target}");

	@HandleEvents
	public void secondTrashTargeted(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8C4D)) {
			onTarget(context, event, tornadoOnYou, tornadoOther);
		}
		else if (event.abilityIdMatches(0x8BC5)) {
			onTarget(context, event, gravityForceOnYou, gravityForceOther);
		}
	}

	private final ModifiableCallout<BuffApplied> tornadoBind = new ModifiableCallout<BuffApplied>("Tornado: Bind (Cleanser)", "Cleanse {event.target}").statusIcon(0xEC0);

	@HandleEvents
	public void tornadoBind(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0xEC0) && canCleanse()) {
			context.accept(tornadoBind.getModified(event));
		}
	}

	@NpcCastCallout(0x8BC1)
	private final ModifiableCallout<AbilityCastStart> ovation = ModifiableCallout.durationBasedCall("Ovation", "Get Behind");

	@NpcCastCallout(0x8C6F)
	private final ModifiableCallout<AbilityCastStart> isleDrop = ModifiableCallout.durationBasedCall("Isle Drop", "Get Behind + Out");

	@NpcCastCallout(0x8C4E)
	private final ModifiableCallout<AbilityCastStart> ancientQuaga = ModifiableCallout.durationBasedCall("Ancient Quaga", "Raidwide with Bleed");

	@NpcCastCallout(0x8C2F)
	private final ModifiableCallout<AbilityCastStart> ancientQuagaEnrage = ModifiableCallout.durationBasedCall("Ancient Quaga (Enrage)", "Kill Islekeeper");

	// ララ

	@NpcCastCallout(0x88AE)
	private final ModifiableCallout<AbilityCastStart> infernoTheorem = ModifiableCallout.durationBasedCall("Inferno Theorem", "Raidwide");

	@NpcCastCallout(0x88AD)
	private final ModifiableCallout<AbilityCastStart> strategicStrike = ModifiableCallout.durationBasedCall("Strategic Strike", "Tankbuster");

	@HandleEvents
	public void lalaHeadmarkers(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x1E4, 0x1E5) && !event.getTarget().isPc()) {
			lalaBossClockwise = event.markerIdMatches(0x1E4);
		}
		else if (event.markerIdMatches(0x1ED, 0x1EE) && event.getTarget().isThePlayer()) {
			lalaPlayerClockwise = event.markerIdMatches(0x1EE);
		}
	}

	@HandleEvents
	public void angularAddition(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x8889, 0x8D2E) && event.isFirstTarget()) {
			lalaBossTimes = event.abilityIdMatches(0x8889) ? 3 : 5;
		}
	}

	@HandleEvents
	public void lalaPlayerBuffs(EventContext context, BuffApplied event) {
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		// 向きは 0=前, 1=右, 2=後ろ, 3=左
		if (event.buffIdMatches(0xE8E)) {
			lalaUnseenDir = 0;
		}
		else if (event.buffIdMatches(0xE8F)) {
			lalaUnseenDir = 2;
		}
		else if (event.buffIdMatches(0xE90)) {
			lalaUnseenDir = 1;
		}
		else if (event.buffIdMatches(0xE91)) {
			lalaUnseenDir = 3;
		}
		else if (event.buffIdMatches(0xE89)) {
			lalaPlayerTimes = 3;
		}
		else if (event.buffIdMatches(0xECE)) {
			lalaPlayerTimes = 5;
		}
	}

	private final ModifiableCallout<AbilityCastStart> arcaneBlightFront = ModifiableCallout.durationBasedCall("Arcane Blight: Front Safe", "Front");
	private final ModifiableCallout<AbilityCastStart> arcaneBlightRight = ModifiableCallout.durationBasedCall("Arcane Blight: Right Safe", "Right");
	private final ModifiableCallout<AbilityCastStart> arcaneBlightBack = ModifiableCallout.durationBasedCall("Arcane Blight: Back Safe", "Back");
	private final ModifiableCallout<AbilityCastStart> arcaneBlightLeft = ModifiableCallout.durationBasedCall("Arcane Blight: Left Safe", "Left");

	@HandleEvents
	public void arcaneBlight(EventContext context, AbilityCastStart event) {
		int initialDir;
		if (event.abilityIdMatches(0x888B)) {
			initialDir = 2;
		}
		else if (event.abilityIdMatches(0x888C)) {
			initialDir = 0;
		}
		else if (event.abilityIdMatches(0x888D)) {
			initialDir = 1;
		}
		else if (event.abilityIdMatches(0x888E)) {
			initialDir = 3;
		}
		else {
			return;
		}
		Integer times = lalaBossTimes;
		Boolean clockwise = lalaBossClockwise;
		lalaBossTimes = null;
		lalaBossClockwise = null;
		if (times == null || clockwise == null) {
			return;
		}
		int finalDir = (initialDir + (clockwise ? 1 : -1) * times + 8) % 4;
		// 安置が左右に移るときだけ知らせる
		int diff = (finalDir - initialDir + 4) % 4;
		if (diff != 1 && diff != 3) {
			return;
		}
		ModifiableCallout<AbilityCastStart> call = switch (finalDir) {
			case 0 -> arcaneBlightFront;
			case 1 -> arcaneBlightRight;
			case 2 -> arcaneBlightBack;
			default -> arcaneBlightLeft;
		};
		context.accept(call.getModified(event));
	}

	private final ModifiableCallout<AbilityCastStart> targetedLightFront = ModifiableCallout.durationBasedCall("Targeted Light: Front", "Face Towards Lala");
	private final ModifiableCallout<AbilityCastStart> targetedLightBack = ModifiableCallout.durationBasedCall("Targeted Light: Back", "Look Away from Lala");
	private final ModifiableCallout<AbilityCastStart> targetedLightLeft = ModifiableCallout.durationBasedCall("Targeted Light: Left", "Left Flank towards Lala");
	private final ModifiableCallout<AbilityCastStart> targetedLightRight = ModifiableCallout.durationBasedCall("Targeted Light: Right", "Right Flank towards Lala");

	@HandleEvents
	public void targetedLight(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x8CDE)) {
			return;
		}
		Integer initialDir = lalaUnseenDir;
		Boolean clockwise = lalaPlayerClockwise;
		Integer times = lalaPlayerTimes;
		lalaUnseenDir = null;
		lalaPlayerTimes = null;
		if (initialDir == null || clockwise == null || times == null) {
			return;
		}
		// 安置が回転するので、プレイヤーは逆向きに回る
		int finalDir = (initialDir + (clockwise ? -1 : 1) * times + 8) % 4;
		ModifiableCallout<AbilityCastStart> call = switch (finalDir) {
			case 0 -> targetedLightFront;
			case 1 -> targetedLightRight;
			case 2 -> targetedLightBack;
			default -> targetedLightLeft;
		};
		context.accept(call.getModified(event));
	}

	private final ModifiableCallout<BuffApplied> planarOne = new ModifiableCallout<>("Planar Tactics: One", "One", 7_000);
	private final ModifiableCallout<BuffApplied> planarBigTwo = new ModifiableCallout<>("Planar Tactics: Two (Stack with Three)", "Two (stack with three)", 7_000);
	private final ModifiableCallout<BuffApplied> planarSmallTwo = new ModifiableCallout<>("Planar Tactics: Two (Stack with One)", "Two (stack with one)", 7_000);
	private final ModifiableCallout<BuffApplied> planarEitherTwo = new ModifiableCallout<>("Planar Tactics: Either Two", "Either Two (with {player})", 7_000);
	private final ModifiableCallout<BuffApplied> planarThree = new ModifiableCallout<>("Planar Tactics: Three", "Three", 7_000);
	private final ModifiableCallout<BuffApplied> planarSmallTwoOn = new ModifiableCallout<>("Planar Tactics: Twos Stacking with One", "Two with one: {players}", 7_000);
	private final ModifiableCallout<BuffApplied> planarUnknown = new ModifiableCallout<>("Planar Tactics: Number (Fallback)", "{num}", 7_000);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> planarTacticsSq = SqtTemplates.sq(10_000,
			BuffApplied.class, ba -> ba.buffIdMatches(SURGE_VECTOR, SUBTRACTIVE_SUPPRESSOR_ALPHA) && !lalaPlanarTacticsDone,
			(e1, s) -> {
				// サージベクトルは後でも使われるので、最初の1回だけ扱う
				lalaPlanarTacticsDone = true;
				List<BuffApplied> buffs = collectRest(s, e1, PLANAR_TACTICS_COUNT, BuffApplied.class, ba -> ba.buffIdMatches(SURGE_VECTOR, SUBTRACTIVE_SUPPRESSOR_ALPHA));
				List<XivCombatant> stacks = buffs.stream().filter(ba -> ba.buffIdMatches(SURGE_VECTOR)).map(BuffApplied::getTarget).toList();
				List<BuffApplied> nums = buffs.stream().filter(ba -> ba.buffIdMatches(SUBTRACTIVE_SUPPRESSOR_ALPHA)).toList();
				BuffApplied mine = nums.stream().filter(ba -> ba.getTarget().isThePlayer()).findFirst().orElse(null);
				if (mine == null) {
					return;
				}
				long myNumber = mine.getStacks();
				if (myNumber < 1 || myNumber > MAX_TACTICS_NUM) {
					return;
				}
				s.setParam("num", myNumber);
				if (stacks.size() != PLANAR_TACTICS_STACKS || nums.size() != PLANAR_TACTICS_NUMS) {
					s.updateCall(planarUnknown, mine);
					return;
				}
				XivCombatant one = nums.stream().filter(ba -> ba.getStacks() == 1).map(BuffApplied::getTarget).findFirst().orElse(null);
				if (one == null) {
					s.updateCall(planarUnknown, mine);
					return;
				}
				boolean isOneStack = containsEntity(stacks, one);
				List<XivCombatant> twos = nums.stream().filter(ba -> ba.getStacks() == 2).map(BuffApplied::getTarget).toList();
				// 1 と頭割りできる 2 を「小さい 2」と呼ぶ
				List<XivCombatant> smallTwos = twos.stream().filter(two -> containsEntity(stacks, two) != isOneStack).toList();
				if (smallTwos.isEmpty()) {
					s.updateCall(planarUnknown, mine);
					return;
				}
				boolean isPlayerSmallTwo = smallTwos.stream().anyMatch(XivCombatant::isThePlayer);
				if (isPlayerSmallTwo && smallTwos.size() >= 2) {
					XivCombatant other = smallTwos.get(0).isThePlayer() ? smallTwos.get(1) : smallTwos.get(0);
					s.setParam("player", other);
					s.updateCall(planarEitherTwo, mine);
					return;
				}
				ModifiableCallout<BuffApplied> role;
				if (one.isThePlayer()) {
					role = planarOne;
				}
				else if (twos.stream().anyMatch(XivCombatant::isThePlayer)) {
					role = isPlayerSmallTwo ? planarSmallTwo : planarBigTwo;
				}
				else {
					role = planarThree;
				}
				s.updateCall(role, mine);
				if (!isPlayerSmallTwo) {
					s.setParam("players", smallTwos);
					s.call(planarSmallTwoOn, mine);
				}
			});

	private final ModifiableCallout<BuffApplied> leftwardMarch = ModifiableCallout.durationBasedCall("Forward March: Leftward", "Leftward March");
	private final ModifiableCallout<BuffApplied> rightwardMarch = ModifiableCallout.durationBasedCall("Forward March: Rightward", "Rightward March");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> lalaForwardMarchSq = SqtTemplates.sq(120_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0xE83) && ba.getTarget().isThePlayer(),
			(e1, s) -> {
				long waitMs = e1.getEstimatedRemainingDuration().minusSeconds(8).toMillis();
				if (waitMs > 0) {
					s.waitMs(waitMs);
				}
				Boolean clockwise = lalaPlayerClockwise;
				Integer times = lalaPlayerTimes;
				lalaPlayerClockwise = null;
				lalaPlayerTimes = null;
				if (clockwise == null || times == null) {
					return;
				}
				int finalDir = ((clockwise ? 1 : -1) * times + 8) % 4;
				if (finalDir == 1) {
					s.updateCall(leftwardMarch, e1);
				}
				else if (finalDir == 3) {
					s.updateCall(rightwardMarch, e1);
				}
			});

	private final ModifiableCallout<BuffApplied> spatialOne = new ModifiableCallout<>("Spatial Tactics: One", "One (avoid all)");
	private final ModifiableCallout<BuffApplied> spatialTwo = new ModifiableCallout<>("Spatial Tactics: Two", "Two (stay middle)");
	private final ModifiableCallout<BuffApplied> spatialThree = new ModifiableCallout<>("Spatial Tactics: Three", "Three (adjacent to middle)");
	private final ModifiableCallout<BuffApplied> spatialFour = new ModifiableCallout<>("Spatial Tactics: Four", "Four");

	@HandleEvents
	public void spatialTactics(EventContext context, BuffApplied event) {
		if (!event.buffIdMatches(0xE8D) || !event.getTarget().isThePlayer() || lalaSpatialTacticsDone) {
			return;
		}
		lalaSpatialTacticsDone = true;
		ModifiableCallout<BuffApplied> call = switch ((int) event.getStacks()) {
			case 1 -> spatialOne;
			case 2 -> spatialTwo;
			case 3 -> spatialThree;
			case 4 -> spatialFour;
			default -> null;
		};
		if (call != null) {
			context.accept(call.getModified(event));
		}
	}

	// スターチス

	@NpcCastCallout(0x8949)
	private final ModifiableCallout<AbilityCastStart> aeroIV = ModifiableCallout.durationBasedCall("Aero IV", "Raidwide");

	@NpcCastCallout(0x8948)
	private final ModifiableCallout<AbilityCastStart> shockingAbandon = ModifiableCallout.durationBasedCall("Shocking Abandon", "Tankbuster");

	private final ModifiableCallout<AbilityUsedEvent> stackThenSpread = new ModifiableCallout<>("Trick Reload: Stack First", "Stack, then Spread");
	private final ModifiableCallout<AbilityUsedEvent> spreadThenStack = new ModifiableCallout<>("Trick Reload: Spread First", "Spread, then Stack");
	private final ModifiableCallout<AbilityUsedEvent> numSafeLater = new ModifiableCallout<>("Trick Reload: Trigger Happy Safe Spot", "{num} safe later");

	@HandleEvents
	public void trickReload(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(LOCKED_AND_LOADED, MISLOAD) || !event.isFirstTarget()) {
			return;
		}
		// 8発のうち最初と最後がトラップシューティング用で、片方は必ず不発になる
		staticeBulletCount++;
		boolean misload = event.abilityIdMatches(MISLOAD);
		if (staticeBulletCount == 1) {
			staticeTrapshooting.clear();
			if (misload) {
				staticeTrapshooting.add(Trap.STACK);
				staticeTrapshooting.add(Trap.SPREAD);
			}
			else {
				staticeTrapshooting.add(Trap.SPREAD);
				staticeTrapshooting.add(Trap.STACK);
			}
			context.accept((misload ? stackThenSpread : spreadThenStack).getModified(event));
		}
		else if (staticeBulletCount < TRICK_RELOAD_BULLETS && misload) {
			staticeTriggerHappy = staticeBulletCount - 1;
			context.accept(numSafeLater.getModified(event, Map.<String, Object>of("num", staticeTriggerHappy)));
		}
		if (staticeBulletCount == TRICK_RELOAD_BULLETS) {
			staticeBulletCount = 0;
		}
	}

	private final ModifiableCallout<AbilityCastStart> trapshootingSpread = ModifiableCallout.durationBasedCall("Trapshooting: Spread", "Spread");
	private final ModifiableCallout<AbilityCastStart> trapshootingStack = ModifiableCallout.durationBasedCall("Trapshooting: Stack", "Stack");
	private final ModifiableCallout<AbilityCastStart> triggerHappy = ModifiableCallout.durationBasedCall("Trigger Happy", "{num}");

	@HandleEvents
	public void trapshootingAndTriggerHappy(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8D1A, 0x8959)) {
			Trap trap = staticeTrapshooting.poll();
			if (trap != null) {
				context.accept((trap == Trap.STACK ? trapshootingStack : trapshootingSpread).getModified(event));
			}
		}
		else if (event.abilityIdMatches(0x894B)) {
			Integer num = staticeTriggerHappy;
			staticeTriggerHappy = null;
			if (num != null) {
				context.accept(triggerHappy.getModified(event, Map.<String, Object>of("num", num)));
			}
		}
	}

	private final ModifiableCallout<BuffApplied> dartOnYou = new ModifiableCallout<>("Bull's-eye: Dart on You", "Dart on You");
	private final ModifiableCallout<BuffApplied> noDart = new ModifiableCallout<>("Bull's-eye: No Dart", "No Dart");
	private final ModifiableCallout<BuffApplied> dartFlex = new ModifiableCallout<>("Bull's-eye: Unmarked Player", "{player} unmarked");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> bullsEyeSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(BULLS_EYE),
			(e1, s) -> {
				List<BuffApplied> darts = collectRest(s, e1, DART_COUNT, BuffApplied.class, ba -> ba.buffIdMatches(BULLS_EYE));
				// ダーツ＆ウィールのときは色と合わせて別に知らせる
				if (staticeIsPinwheelingDartboard) {
					return;
				}
				List<XivCombatant> targets = darts.stream().map(BuffApplied::getTarget).toList();
				BuffApplied last = darts.get(darts.size() - 1);
				if (targets.stream().noneMatch(XivCombatant::isThePlayer)) {
					s.updateCall(noDart, last);
					return;
				}
				s.updateCall(dartOnYou, last);
				List<XivPlayerCharacter> flexers = state.getPartyList().stream().filter(pc -> !containsEntity(targets, pc)).toList();
				if (flexers.size() == 1) {
					s.setParam("player", flexers.get(0));
					s.call(dartFlex, last);
				}
			});

	private final RepeatSuppressor dartBatchSupp = new RepeatSuppressor(Duration.ofSeconds(2));

	@HandleEvents
	public void dartCollect(EventContext context, BuffApplied event) {
		if (!event.buffIdMatches(BULLS_EYE)) {
			return;
		}
		// 前回の付与から間が空いていれば新しい組とみなす
		if (dartBatchSupp.check(event)) {
			staticeDartTargets.clear();
		}
		staticeDartTargets.add(event.getTarget());
	}

	private final ModifiableCallout<AbilityCastStart> numSafeSoon = ModifiableCallout.durationBasedCall("Surprise Balloon: Trigger Happy Reminder", "{num} safe soon");
	private final ModifiableCallout<AbilityCastStart> popKnockback = ModifiableCallout.durationBasedCall("Pop", "Knockback");
	private final ModifiableCallout<AbilityCastStart> popKnockbackToNum = ModifiableCallout.durationBasedCall("Pop (with Trigger Happy)", "Knockback, then {num}");
	private final RepeatSuppressor popSupp = new RepeatSuppressor(Duration.ofSeconds(20));

	@HandleEvents
	public void surpriseBalloon(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x894D)) {
			// ノックバック防止を両方に合わせるのが難しいので、早めに一度知らせておく
			Integer num = staticeTriggerHappy;
			staticeTriggerHappy = null;
			if (num != null) {
				staticePopTriggerHappyNum = num;
				context.accept(numSafeSoon.getModified(event, Map.<String, Object>of("num", num)));
			}
		}
		else if (event.abilityIdMatches(0x894E) && popSupp.check(event)) {
			Integer num = staticePopTriggerHappyNum;
			staticePopTriggerHappyNum = null;
			if (num == null) {
				context.accept(popKnockback.getModified(event));
			}
			else {
				context.accept(popKnockbackToNum.getModified(event, Map.<String, Object>of("num", num)));
			}
		}
	}

	private final ModifiableCallout<BuffApplied> faceForward = new ModifiableCallout<>("Forward March (Statice)", "Forward March, then {mech}", 5_000);
	private final ModifiableCallout<BuffApplied> faceBackward = new ModifiableCallout<>("About Face (Statice)", "Backward March, then {mech}", 5_000);
	private final ModifiableCallout<BuffApplied> faceLeft = new ModifiableCallout<>("Left Face (Statice)", "Left March, then {mech}", 5_000);
	private final ModifiableCallout<BuffApplied> faceRight = new ModifiableCallout<>("Right Face (Statice)", "Right March, then {mech}", 5_000);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> staticeFaceSq = SqtTemplates.sq(120_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0xDD2, 0xDD3, 0xDD4, 0xDD5) && ba.getTarget().isThePlayer(),
			(e1, s) -> {
				long waitMs = e1.getEstimatedRemainingDuration().minusSeconds(7).toMillis();
				if (waitMs > 0) {
					s.waitMs(waitMs);
				}
				Object mech;
				Integer num = staticeTriggerHappy;
				if (num != null) {
					mech = num;
					staticeTriggerHappy = null;
				}
				else {
					Trap trap = staticeTrapshooting.poll();
					if (trap == null) {
						mech = "?";
					}
					else {
						mech = trap == Trap.STACK ? "Stack" : "Spread";
					}
				}
				s.setParam("mech", mech);
				ModifiableCallout<BuffApplied> call;
				if (e1.buffIdMatches(0xDD2)) {
					call = faceForward;
				}
				else if (e1.buffIdMatches(0xDD3)) {
					call = faceBackward;
				}
				else if (e1.buffIdMatches(0xDD4)) {
					call = faceLeft;
				}
				else {
					call = faceRight;
				}
				s.updateCall(call, e1);
			});

	@PlayerStatusCallout(0x301)
	private final ModifiableCallout<BuffApplied> burningChains = new ModifiableCallout<BuffApplied>("Burning Chains", "Break Chains").autoIcon();

	@HandleEvents
	public void pinwheelingDartboardTracker(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8CBC)) {
			staticeIsPinwheelingDartboard = true;
		}
	}

	private final ModifiableCallout<RawAddCombatantEvent> avoidBlue = new ModifiableCallout<>("Pinwheeling Dartboard: Avoid Blue", "Avoid Blue", 6_000);
	private final ModifiableCallout<RawAddCombatantEvent> avoidRed = new ModifiableCallout<>("Pinwheeling Dartboard: Avoid Red", "Avoid Red", 6_000);
	private final ModifiableCallout<RawAddCombatantEvent> avoidYellow = new ModifiableCallout<>("Pinwheeling Dartboard: Avoid Yellow", "Avoid Yellow", 6_000);
	private final ModifiableCallout<RawAddCombatantEvent> pinwheelDartOnYou = new ModifiableCallout<>("Pinwheeling Dartboard: Dart on You", "Dart (with {player})", 6_000);
	private final ModifiableCallout<RawAddCombatantEvent> pinwheelNoDart = new ModifiableCallout<>("Pinwheeling Dartboard: No Dart", "No Dart", 6_000);

	@HandleEvents
	public void pinwheelingDartboardColor(EventContext context, RawAddCombatantEvent event) {
		if (nameIdOf(event) != HOMING_PATTERN_NAME_ID) {
			return;
		}
		Position pos = posOf(event);
		if (pos != null) {
			double x = pos.x() - DARTBOARD_CENTER_X;
			double y = pos.y() - DARTBOARD_CENTER_Y;
			// 12 分割のうち最初の境界が真北にあり、青・赤・黄の順に4回繰り返す
			// ホーミングパターンは区画の中央に落ちるので半区画ずらす
			int dir12 = (int) (Math.round(6 - 6 * Math.atan2(x, y) / Math.PI + 11.5) % 12);
			ModifiableCallout<RawAddCombatantEvent> color = switch (dir12 % 3) {
				case 0 -> avoidBlue;
				case 1 -> avoidRed;
				default -> avoidYellow;
			};
			context.accept(color.getModified(event));
		}
		List<XivCombatant> darts = List.copyOf(staticeDartTargets);
		if (darts.size() != PINWHEEL_DART_COUNT) {
			return;
		}
		if (darts.stream().noneMatch(XivCombatant::isThePlayer)) {
			context.accept(pinwheelNoDart.getModified(event));
			return;
		}
		XivCombatant other = darts.get(0).isThePlayer() ? darts.get(1) : darts.get(0);
		context.accept(pinwheelDartOnYou.getModified(event, Map.<String, Object>of("player", other)));
	}

	private final ModifiableCallout<HeadMarkerEvent> pinwheelTether = new ModifiableCallout<>("Pinwheeling Dartboard: Tether", "Tether with {player}");
	private final ModifiableCallout<HeadMarkerEvent> pinwheelStack = new ModifiableCallout<>("Pinwheeling Dartboard: Stack", "Stack with {player}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> pinwheelMechSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(TETHER_MARKER) && staticeIsPinwheelingDartboard,
			(e1, s) -> {
				List<HeadMarkerEvent> markers = collectRest(s, e1, 2, HeadMarkerEvent.class, hm -> hm.markerIdMatches(TETHER_MARKER));
				if (markers.size() != 2) {
					return;
				}
				List<XivCombatant> tethers = markers.stream().map(HeadMarkerEvent::getTarget).toList();
				HeadMarkerEvent last = markers.get(markers.size() - 1);
				if (tethers.stream().anyMatch(XivCombatant::isThePlayer)) {
					XivCombatant other = tethers.get(0).isThePlayer() ? tethers.get(1) : tethers.get(0);
					s.setParam("player", other);
					s.updateCall(pinwheelTether, last);
					return;
				}
				XivCombatant other = state.getPartyList().stream()
						.filter(pc -> !pc.isThePlayer() && !containsEntity(tethers, pc))
						.findFirst()
						.orElse(null);
				if (other != null) {
					s.setParam("player", other);
					s.updateCall(pinwheelStack, last);
				}
			});
}
