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
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SequentialTriggerController;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;
import gg.xp.xivsupport.models.XivPlayerCharacter;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/dungeon/another_aloalo_island-savage.ts をもとに移植
@CalloutRepo(name = "Another Aloalo Island Savage", duty = KnownDuty.AnotherAloaloIslandSavage)
public class AnotherAloaloIslandSavage extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long springCrystalNameId = 12607;
	private static final long homingPatternNameId = 12507;
	// 横向きの水晶は向きが0、縦向きは-π/2
	private static final double crystalHeadingEpsilon = 0.1;
	// 水晶の座標は -15, -5, 5, 15 のいずれかなので、10より外側なら外周の列
	private static final double crystalOuterEdge = 10;
	private static final double sameColumnTolerance = 1;
	private static final double staticeCenterX = -200;
	private static final double staticeCenterY = 0;
	// 同時に付与されるデバフなどをまとめて扱うための猶予
	private static final Duration collectWindow = Duration.ofMillis(500);
	private static final long forwardMarchLeadMs = 8_000;
	private static final long staticeFaceLeadMs = 7_000;

	private XivState state;

	public AnotherAloaloIslandSavage(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AnotherAloaloIslandSavage);
	}

	private enum Mech {
		STACK,
		SPREAD
	}

	private int ketuSpringCrystalCount;
	private final List<XivCombatant> ketuCrystalAdd = new ArrayList<>();
	private int ketuHydroBuffCount;
	private @Nullable Boolean ketuHydroBuffIsSpreadFirst;
	private boolean ketuHydroBuffIsRoleStacks;
	private @Nullable Boolean ketuBuffIsBubble;
	private @Nullable XivCombatant ketuBuffPartner;
	// true なら次の Twintides 後は内側
	private @Nullable Boolean ketuTwintidesNextIn;
	private @Nullable Boolean lalaBossClockwise;
	private @Nullable Integer lalaBossTimes;
	// 0=前 1=右 2=後 3=左
	private @Nullable Integer lalaUnseen;
	private @Nullable Integer lalaPlayerTimes;
	private @Nullable Boolean lalaPlayerClockwise;
	private final List<BuffApplied> lalaSubAlpha = new ArrayList<>();
	private boolean lalaPlanarTacticsDone;
	private boolean lalaSpatialTacticsDone;
	private final List<AbilityUsedEvent> staticeBullet = new ArrayList<>();
	private @Nullable Integer staticeTriggerHappy;
	private @Nullable Integer staticePopTriggerHappyNum;
	private final Deque<Mech> staticeTrapshooting = new ArrayDeque<>();
	private boolean staticeIsPinwheelingDartboard;
	private final List<XivCombatant> staticePinwheelDarts = new ArrayList<>();
	private final Map<Long, BaseEvent> lastCalls = new HashMap<>();

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		ketuSpringCrystalCount = 0;
		ketuCrystalAdd.clear();
		ketuHydroBuffCount = 0;
		ketuHydroBuffIsSpreadFirst = null;
		ketuHydroBuffIsRoleStacks = false;
		ketuBuffIsBubble = null;
		ketuBuffPartner = null;
		ketuTwintidesNextIn = null;
		lalaBossClockwise = null;
		lalaBossTimes = null;
		lalaUnseen = null;
		lalaPlayerTimes = null;
		lalaPlayerClockwise = null;
		lalaSubAlpha.clear();
		lalaPlanarTacticsDone = false;
		lalaSpatialTacticsDone = false;
		staticeBullet.clear();
		staticeTriggerHappy = null;
		staticePopTriggerHappyNum = null;
		staticeTrapshooting.clear();
		staticeIsPinwheelingDartboard = false;
		staticePinwheelDarts.clear();
		lastCalls.clear();
	}

	// 対象ごとに出るアビリティは、一定時間内の2回目以降を無視する
	private boolean suppressed(long key, BaseEvent event, long ms) {
		BaseEvent last = lastCalls.get(key);
		if (last != null && Duration.between(last.getEffectiveHappenedAt(), event.getEffectiveHappenedAt()).toMillis() < ms) {
			return true;
		}
		lastCalls.put(key, event);
		return false;
	}

	// 最初のイベントから少しの間に届いた同種のイベントをまとめる
	private static <Y extends BaseEvent> List<Y> collectNear(SequentialTriggerController<BaseEvent> s, Y first, int limit, Class<Y> eventClass, Predicate<Y> filter) {
		List<Y> out = new ArrayList<>();
		out.add(first);
		if (limit > 1) {
			out.addAll(s.waitEventsUntil(limit - 1, eventClass, filter, BaseEvent.class,
					e -> Duration.between(first.getEffectiveHappenedAt(), e.getEffectiveHappenedAt()).compareTo(collectWindow) > 0));
		}
		return out;
	}

	private static boolean isDps(@Nullable XivCombatant cbt) {
		return cbt instanceof XivPlayerCharacter pc && pc.getJob().isDps();
	}

	// ロール混合の対処はタンクヒラ2人・DPS2人の通常編成でのみ判断できる
	private boolean isStandardLightParty() {
		List<XivPlayerCharacter> party = state.getPartyList();
		long supports = party.stream().filter(pc -> pc.getJob().isSupport()).count();
		long dps = party.stream().filter(pc -> pc.getJob().isDps()).count();
		return supports == 2 && dps == 2;
	}

	private static boolean isHorizontalCrystal(XivCombatant cbt) {
		Position pos = cbt.getPos();
		return pos != null && Math.abs(pos.getHeading()) < crystalHeadingEpsilon;
	}

	private static @Nullable XivCombatant other(List<XivCombatant> pair, XivCombatant me) {
		if (pair.size() < 2) {
			return null;
		}
		return pair.get(0).equals(me) ? pair.get(1) : pair.get(0);
	}

	// ---------------- 1つ目の雑魚 ----------------

	private final ModifiableCallout<AbilityCastStart> leadHookOnYou = ModifiableCallout.durationBasedCall("Lead Hook (on You)", "3x Tankbuster on You");
	private final ModifiableCallout<AbilityCastStart> leadHookOther = ModifiableCallout.durationBasedCall("Lead Hook (on Other)", "3x Buster on {event.target}");

	@HandleEvents
	public void leadHook(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8BC7)) {
			context.accept((event.getTarget().isThePlayer() ? leadHookOnYou : leadHookOther).getModified(event));
		}
	}

	@NpcCastCallout(0x8BC8)
	private final ModifiableCallout<AbilityCastStart> sharpStrike = ModifiableCallout.durationBasedCall("Sharp Strike", "Buster on {event.target}");

	@NpcCastCallout(0x8BC9)
	private final ModifiableCallout<AbilityCastStart> tailScrew = ModifiableCallout.durationBasedCall("Tail Screw", "Move Away");

	@NpcCastCallout(0x8BCC)
	private final ModifiableCallout<AbilityCastStart> waterIII = ModifiableCallout.durationBasedCall("Water III", "Stack on {event.target}");

	@NpcCastCallout(0x8BCA)
	private final ModifiableCallout<AbilityCastStart> bubbleShower = ModifiableCallout.durationBasedCall("Bubble Shower", "Back then Front");

	// カニ泡は詠唱が短いので、バブルシャワーの発動を合図にする
	private final ModifiableCallout<AbilityUsedEvent> crabDribble = new ModifiableCallout<>("Crab Dribble", "Front");

	@HandleEvents
	public void crabDribble(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x8BCA) && !suppressed(0x8BCA, event, 5_000)) {
			context.accept(crabDribble.getModified(event));
		}
	}

	@NpcCastCallout(0x8C4B)
	private final ModifiableCallout<AbilityCastStart> hydrocannon = ModifiableCallout.durationBasedCall("Hydrocannon", "Get Behind");

	@NpcCastCallout(0x8BCE)
	private final ModifiableCallout<AbilityCastStart> expulsion = ModifiableCallout.durationBasedCall("Expulsion", "Out");

	@NpcCastCallout(0x8BCD)
	private final ModifiableCallout<AbilityCastStart> electricWhorl = ModifiableCallout.durationBasedCall("Electric Whorl", "Get Under");

	@NpcCastCallout(value = 0x8BD1, onYou = true)
	private final ModifiableCallout<AbilityCastStart> hydroshot = ModifiableCallout.durationBasedCall("Hydroshot", "Knockback on You");

	@NpcCastCallout(0x8C4F)
	private final ModifiableCallout<AbilityCastStart> crossAttack = ModifiableCallout.durationBasedCall("Cross Attack", "Buster on {event.target}");

	// ---------------- ケトゥドゥケ ----------------

	@NpcCastCallout(0x8AD4)
	private final ModifiableCallout<AbilityCastStart> tidalRoar = ModifiableCallout.durationBasedCall("Tidal Roar", "Raidwide with Bleed");

	@NpcCastCallout({0x8AC5, 0x8AAD})
	private final ModifiableCallout<AbilityCastStart> bubbleNet = ModifiableCallout.durationBasedCall("Bubble Net", "Raidwide");

	@HandleEvents
	public void springCrystalTracker(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8AA8)) {
			ketuSpringCrystalCount++;
		}
		else if (event.abilityIdMatches(0x8ADB, 0x8ADC)) {
			ketuCrystalAdd.clear();
		}
	}

	@HandleEvents
	public void springCrystalCollect(EventContext context, RawAddCombatantEvent event) {
		if (event.getEntity().getbNpcNameId() == springCrystalNameId) {
			ketuCrystalAdd.add(event.getEntity());
		}
	}

	private final ModifiableCallout<BuffApplied> fetters = new ModifiableCallout<BuffApplied>("Foamy Fetters", "Fetters (with {partner})").statusIcon(0xECC);
	private final ModifiableCallout<BuffApplied> bubble = new ModifiableCallout<BuffApplied>("Bubble Weave", "Bubble (with {partner})").statusIcon(0xE9F);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> fettersBubbleSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0xECC, 0xE9F),
			(e1, s) -> {
				List<BuffApplied> buffs = collectNear(s, e1, 4, BuffApplied.class, ba -> ba.buffIdMatches(0xECC, 0xE9F));
				BuffApplied mine = buffs.stream().filter(ba -> ba.getTarget().isThePlayer()).findFirst().orElse(null);
				if (mine == null) {
					return;
				}
				boolean isBubble = mine.buffIdMatches(0xE9F);
				ketuBuffIsBubble = isBubble;
				ketuBuffPartner = buffs.stream()
						.filter(ba -> !ba.getTarget().isThePlayer() && ba.buffIdMatches(mine.getBuff().getId()))
						.map(BuffApplied::getTarget)
						.findFirst()
						.orElse(null);
				// Fluke Gale と重なる回は後で呼ぶので、ここでは呼ばない
				if (ketuSpringCrystalCount == 1 || ketuSpringCrystalCount == 4) {
					return;
				}
				s.setParam("partner", ketuBuffPartner);
				s.updateCall(isBubble ? bubble : fetters, mine);
			});

	private final ModifiableCallout<AbilityCastStart> bubbleSpread = new ModifiableCallout<>("Hydro Buff: Bubble + Spread", "Bubble + Spread", 10_000);
	private final ModifiableCallout<AbilityCastStart> bubbleStacks = new ModifiableCallout<>("Hydro Buff: Bubble + Stacks", "Bubble + Stacks", 10_000);
	private final ModifiableCallout<AbilityCastStart> fettersSpread = new ModifiableCallout<>("Hydro Buff: Fetters + Spread", "Fetters + Spread", 10_000);
	private final ModifiableCallout<AbilityCastStart> fettersStacks = new ModifiableCallout<>("Hydro Buff: Fetters + Stacks", "Fetters + Stacks", 10_000);
	private final ModifiableCallout<AbilityCastStart> bubbleSpreadPartner = new ModifiableCallout<>("Hydro Buff: Bubble + Spread (Flex Partner)", "Bubble + Spread (with {partner})", 10_000);
	private final ModifiableCallout<AbilityCastStart> bubbleStacksPartner = new ModifiableCallout<>("Hydro Buff: Bubble + Stacks (Flex Partner)", "Bubble + Stacks (with {partner})", 10_000);
	private final ModifiableCallout<AbilityCastStart> fettersSpreadPartner = new ModifiableCallout<>("Hydro Buff: Fetters + Spread (Flex Partner)", "Fetters + Spread (with {partner})", 10_000);
	private final ModifiableCallout<AbilityCastStart> fettersStacksPartner = new ModifiableCallout<>("Hydro Buff: Fetters + Stacks (Flex Partner)", "Fetters + Stacks (with {partner})", 10_000);

	private final ModifiableCallout<AbilityCastStart> bubbleAnything = new ModifiableCallout<>("Hydro Buff: Bubble Position", "Diagonal of Vertical / Next to Horizontal", 10_000);
	private final ModifiableCallout<AbilityCastStart> fettersSpreadSplit = new ModifiableCallout<>("Hydro Buff: Fetters Spread (Split Layout)", "Diagonal of Vertical", 10_000);
	private final ModifiableCallout<AbilityCastStart> fettersSpreadColumn = new ModifiableCallout<>("Hydro Buff: Fetters Spread (Column Layout)", "Next to Vertical", 10_000);
	private final ModifiableCallout<AbilityCastStart> fettersStackSplit = new ModifiableCallout<>("Hydro Buff: Fetters Stack (Split Layout)", "On Horizontal", 10_000);
	private final ModifiableCallout<AbilityCastStart> fettersStackColumn = new ModifiableCallout<>("Hydro Buff: Fetters Stack (Column Layout)", "Diagonal of Horizontal", 10_000);

	private final ModifiableCallout<AbilityCastStart> spreadThenStacks = ModifiableCallout.durationBasedCall("Hydro Buff Double: Spread First", "Spread => Stacks");
	private final ModifiableCallout<AbilityCastStart> stacksThenSpread = ModifiableCallout.durationBasedCall("Hydro Buff Double: Stacks First", "Stacks => Spread");

	@HandleEvents
	public void hydroBuff(EventContext context, AbilityCastStart event) {
		// 8AB8 = Hydrobullet（散開）、8AB4 = Hydrofall（頭割り）
		if (!event.abilityIdMatches(0x8AB8, 0x8AB4)) {
			return;
		}
		ketuHydroBuffCount++;
		ketuHydroBuffIsSpreadFirst = null;
		ketuHydroBuffIsRoleStacks = false;
		boolean isSpread = event.abilityIdMatches(0x8AB8);

		if (ketuHydroBuffCount == 1 || ketuHydroBuffCount == 6) {
			hydroBuffSingle(context, event, isSpread);
		}
		else if (ketuHydroBuffCount == 2 || ketuHydroBuffCount == 5) {
			ketuHydroBuffIsSpreadFirst = isSpread;
			context.accept((isSpread ? spreadThenStacks : stacksThenSpread).getModified(event));
		}
	}

	private void hydroBuffSingle(EventContext context, AbilityCastStart event, boolean isSpread) {
		Boolean isBubble = ketuBuffIsBubble;
		if (isBubble == null) {
			return;
		}
		XivCombatant partner = ketuBuffPartner;
		boolean partnerSameRole = partner != null
				&& isDps(state.getPlayer()) == isDps(partner)
				&& isStandardLightParty();
		// 通常編成なら同じロール同士で同じデバフになるので相方は言わない
		ModifiableCallout<AbilityCastStart> mechCall;
		if (partnerSameRole || partner == null) {
			mechCall = isBubble
					? (isSpread ? bubbleSpread : bubbleStacks)
					: (isSpread ? fettersSpread : fettersStacks);
			context.accept(mechCall.getModified(event));
		}
		else {
			mechCall = isBubble
					? (isSpread ? bubbleSpreadPartner : bubbleStacksPartner)
					: (isSpread ? fettersSpreadPartner : fettersStacksPartner);
			context.accept(mechCall.getModified(event, Map.of("partner", partner)));
		}

		if (isBubble) {
			context.accept(bubbleAnything.getModified(event));
			return;
		}

		// 各水晶が別の列にある「分散」配置と、同じ列に縦横が並ぶ「列」配置の2種類がある
		List<XivCombatant> horizontal = ketuCrystalAdd.stream().filter(AnotherAloaloIslandSavage::isHorizontalCrystal).toList();
		List<XivCombatant> vertical = ketuCrystalAdd.stream().filter(cbt -> cbt.getPos() != null && !isHorizontalCrystal(cbt)).toList();
		if (horizontal.size() != 2 || vertical.size() != 2) {
			return;
		}
		double firstHorizX = horizontal.get(0).getPos().getX();
		boolean isSplitLayout = vertical.stream().noneMatch(cbt -> Math.abs(cbt.getPos().getX() - firstHorizX) < sameColumnTolerance);
		ModifiableCallout<AbilityCastStart> posCall;
		if (isSpread) {
			posCall = isSplitLayout ? fettersSpreadSplit : fettersSpreadColumn;
		}
		else {
			posCall = isSplitLayout ? fettersStackSplit : fettersStackColumn;
		}
		context.accept(posCall.getModified(event));
	}

	private final ModifiableCallout<AbilityUsedEvent> doubleFollowupSpread = new ModifiableCallout<>("Hydro Buff Double Followup: Spread", "Spread");
	private final ModifiableCallout<AbilityUsedEvent> doubleFollowupStacks = new ModifiableCallout<>("Hydro Buff Double Followup: Stacks", "Stacks");
	private final ModifiableCallout<AbilityUsedEvent> doubleFollowupRoleStacks = new ModifiableCallout<>("Hydro Buff Double Followup: Role Stacks", "Role Stacks");

	@HandleEvents
	public void hydroBuffDoubleFollowup(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x8ADF, 0x8ADE) || suppressed(0x8ADF, event, 10_000)) {
			return;
		}
		boolean wasSpread = event.abilityIdMatches(0x8ADF);
		if (wasSpread && Boolean.TRUE.equals(ketuHydroBuffIsSpreadFirst)) {
			context.accept((ketuHydroBuffIsRoleStacks ? doubleFollowupRoleStacks : doubleFollowupStacks).getModified(event));
		}
		else if (!wasSpread && Boolean.FALSE.equals(ketuHydroBuffIsSpreadFirst)) {
			context.accept(doubleFollowupSpread.getModified(event));
		}
	}

	private final ModifiableCallout<BuffApplied> roleStacks = new ModifiableCallout<>("Hydrofall: Role Stacks", "Role Stacks");
	private final ModifiableCallout<BuffApplied> spreadThenRoleStacks = new ModifiableCallout<>("Hydrofall: Spread then Role Stacks", "Spread => Role Stacks");
	private final ModifiableCallout<BuffApplied> roleStacksThenSpread = new ModifiableCallout<>("Hydrofall: Role Stacks then Spread", "Role Stacks => Spread");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> hydrofallRoleStackSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0xEA3),
			(e1, s) -> {
				List<BuffApplied> stacks = collectNear(s, e1, 2, BuffApplied.class, ba -> ba.buffIdMatches(0xEA3));
				if (stacks.size() != 2 || !isStandardLightParty()) {
					return;
				}
				// 片方がDPS、片方がサポートに付いた場合（誰かが倒れているときなど）はロール別に頭割りする
				if (isDps(stacks.get(0).getTarget()) == isDps(stacks.get(1).getTarget())) {
					return;
				}
				ketuHydroBuffIsRoleStacks = true;
				ModifiableCallout<BuffApplied> call;
				if (Boolean.TRUE.equals(ketuHydroBuffIsSpreadFirst)) {
					call = spreadThenRoleStacks;
				}
				else if (Boolean.FALSE.equals(ketuHydroBuffIsSpreadFirst)) {
					call = roleStacksThenSpread;
				}
				else {
					call = roleStacks;
				}
				s.updateCall(call, e1);
			});

	private final ModifiableCallout<AbilityCastStart> recedingTwintides = ModifiableCallout.durationBasedCall("Receding Twintides", "Out => In + Stacks");
	private final ModifiableCallout<AbilityCastStart> recedingTwintidesRole = ModifiableCallout.durationBasedCall("Receding Twintides (Role Stacks)", "Out => In + Role Stacks");
	private final ModifiableCallout<AbilityCastStart> encroachingTwintides = ModifiableCallout.durationBasedCall("Encroaching Twintides", "In => Out + Stacks");
	private final ModifiableCallout<AbilityCastStart> encroachingTwintidesRole = ModifiableCallout.durationBasedCall("Encroaching Twintides (Role Stacks)", "In => Out + Role Stacks");

	@HandleEvents
	public void twintides(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8AE7)) {
			ketuTwintidesNextIn = true;
			context.accept((ketuHydroBuffIsRoleStacks ? recedingTwintidesRole : recedingTwintides).getModified(event));
		}
		else if (event.abilityIdMatches(0x8AE9)) {
			ketuTwintidesNextIn = false;
			context.accept((ketuHydroBuffIsRoleStacks ? encroachingTwintidesRole : encroachingTwintides).getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> twintidesInStacks = new ModifiableCallout<>("Twintides Followup: In + Stacks", "In + Stacks");
	private final ModifiableCallout<AbilityUsedEvent> twintidesInRoleStacks = new ModifiableCallout<>("Twintides Followup: In + Role Stacks", "In + Role Stacks");
	private final ModifiableCallout<AbilityUsedEvent> twintidesOutStacks = new ModifiableCallout<>("Twintides Followup: Out + Stacks", "Out + Stacks");
	private final ModifiableCallout<AbilityUsedEvent> twintidesOutRoleStacks = new ModifiableCallout<>("Twintides Followup: Out + Role Stacks", "Out + Role Stacks");

	@HandleEvents
	public void twintidesFollowup(EventContext context, AbilityUsedEvent event) {
		// 8AE0 = Sphere Shatter。Twintides の直後に来るのでこれを合図にする
		if (!event.abilityIdMatches(0x8AE0) || suppressed(0x8AE0, event, 5_000)) {
			return;
		}
		Boolean nextIn = ketuTwintidesNextIn;
		ketuTwintidesNextIn = null;
		if (nextIn == null) {
			return;
		}
		ModifiableCallout<AbilityUsedEvent> call;
		if (nextIn) {
			call = ketuHydroBuffIsRoleStacks ? twintidesInRoleStacks : twintidesInStacks;
		}
		else {
			call = ketuHydroBuffIsRoleStacks ? twintidesOutRoleStacks : twintidesOutStacks;
		}
		context.accept(call.getModified(event));
	}

	private final ModifiableCallout<RawAddCombatantEvent> springCrystalsNorthSouth = new ModifiableCallout<>("Spring Crystals 2: North/South Safe", "North/South");
	private final ModifiableCallout<RawAddCombatantEvent> springCrystalsEastWest = new ModifiableCallout<>("Spring Crystals 2: East/West Safe", "East/West");
	private final ModifiableCallout<RawAddCombatantEvent> springCrystalsCorners = new ModifiableCallout<>("Spring Crystals 2: Corners Safe", "Corners");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> springCrystals2Sq = SqtTemplates.sq(15_000,
			RawAddCombatantEvent.class, ace -> ace.getEntity().getbNpcNameId() == springCrystalNameId && ketuSpringCrystalCount == 2,
			(e1, s) -> {
				List<RawAddCombatantEvent> adds = collectNear(s, e1, 4, RawAddCombatantEvent.class, ace -> ace.getEntity().getbNpcNameId() == springCrystalNameId);
				if (adds.size() != 4) {
					return;
				}
				List<XivCombatant> crystals = adds.stream().map(RawAddCombatantEvent::getEntity).filter(cbt -> cbt.getPos() != null).toList();
				List<XivCombatant> horizontal = crystals.stream().filter(AnotherAloaloIslandSavage::isHorizontalCrystal).toList();
				List<XivCombatant> vertical = crystals.stream().filter(cbt -> !isHorizontalCrystal(cbt)).toList();
				if (horizontal.size() != 2 || vertical.size() != 2) {
					return;
				}
				// 自分のデバフが分かるまで役に立たないので、少し遅らせて呼ぶ
				s.waitMs(5_000);
				ModifiableCallout<RawAddCombatantEvent> call;
				if (vertical.stream().anyMatch(cbt -> Math.abs(cbt.getPos().getY()) > crystalOuterEdge)) {
					call = springCrystalsEastWest;
				}
				else if (horizontal.stream().anyMatch(cbt -> Math.abs(cbt.getPos().getX()) > crystalOuterEdge)) {
					call = springCrystalsNorthSouth;
				}
				else {
					call = springCrystalsCorners;
				}
				s.updateCall(call, adds.get(adds.size() - 1));
			});

	private final ModifiableCallout<AbilityCastStart> angrySeasSpread = ModifiableCallout.durationBasedCall("Angry Seas (Spread)", "Knockback => Spread");
	private final ModifiableCallout<AbilityCastStart> angrySeasStacks = ModifiableCallout.durationBasedCall("Angry Seas (Stacks)", "Knockback => Stacks");
	private final ModifiableCallout<AbilityCastStart> angrySeasRoleStacks = ModifiableCallout.durationBasedCall("Angry Seas (Role Stacks)", "Knockback => Role Stacks");

	@HandleEvents
	public void angrySeas(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x8AE1)) {
			return;
		}
		ModifiableCallout<AbilityCastStart> call;
		if (Boolean.TRUE.equals(ketuHydroBuffIsSpreadFirst)) {
			call = angrySeasSpread;
		}
		else if (ketuHydroBuffIsRoleStacks) {
			call = angrySeasRoleStacks;
		}
		else {
			call = angrySeasStacks;
		}
		context.accept(call.getModified(event));
	}

	// ---------------- 2つ目の雑魚 ----------------

	private final ModifiableCallout<AbilityCastStart> ancientAeroIII = ModifiableCallout.durationBasedCall("Ancient Aero III", "Interrupt");

	@HandleEvents
	public void ancientAeroIII(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8BD2) && state.playerJobMatches(job -> job.isTank() || job.isPranged())) {
			context.accept(ancientAeroIII.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> tornadoOnYou = ModifiableCallout.durationBasedCall("Tornado (on You)", "Tornado on You");
	private final ModifiableCallout<AbilityCastStart> tornadoOther = ModifiableCallout.durationBasedCall("Tornado (on Other)", "Away from {event.target}");

	@HandleEvents
	public void tornado(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8BD3)) {
			context.accept((event.getTarget().isThePlayer() ? tornadoOnYou : tornadoOther).getModified(event));
		}
	}

	private final ModifiableCallout<BuffApplied> tornadoBind = new ModifiableCallout<BuffApplied>("Tornado Bind", "Cleanse {event.target}").statusIcon(0xEC0);

	@HandleEvents
	public void tornadoBind(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0xEC0) && state.playerJobMatches(job -> job.isHealer() || job == Job.BRD)) {
			context.accept(tornadoBind.getModified(event));
		}
	}

	@NpcCastCallout(0x8BD4)
	private final ModifiableCallout<AbilityCastStart> ovation = ModifiableCallout.durationBasedCall("Ovation", "Get Behind");

	@NpcCastCallout(0x8C3A)
	private final ModifiableCallout<AbilityCastStart> gravityForce = ModifiableCallout.durationBasedCall("Gravity Force", "Stack on {event.target}");

	@NpcCastCallout(0x8C3C)
	private final ModifiableCallout<AbilityCastStart> isleDrop = ModifiableCallout.durationBasedCall("Isle Drop", "Get Behind + Out");

	@NpcCastCallout(0x8C39)
	private final ModifiableCallout<AbilityCastStart> ancientQuaga = ModifiableCallout.durationBasedCall("Ancient Quaga", "Raidwide with Bleed");

	// ---------------- ララ ----------------

	@NpcCastCallout(0x8C05)
	private final ModifiableCallout<AbilityCastStart> infernoTheorem = ModifiableCallout.durationBasedCall("Inferno Theorem", "Raidwide");

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
		if (event.abilityIdMatches(0x8BE0, 0x8D2F) && event.isFirstTarget()) {
			lalaBossTimes = event.abilityIdMatches(0x8BE0) ? 3 : 5;
		}
	}

	@HandleEvents
	public void lalaPlayerBuffs(EventContext context, BuffApplied event) {
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		if (event.buffIdMatches(0xE8E)) {
			lalaUnseen = 0;
		}
		else if (event.buffIdMatches(0xE8F)) {
			lalaUnseen = 2;
		}
		else if (event.buffIdMatches(0xE90)) {
			lalaUnseen = 1;
		}
		else if (event.buffIdMatches(0xE91)) {
			lalaUnseen = 3;
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
		if (event.abilityIdMatches(0x8BE2)) {
			initialDir = 2;
		}
		else if (event.abilityIdMatches(0x8BE3)) {
			initialDir = 0;
		}
		else if (event.abilityIdMatches(0x8BE4)) {
			initialDir = 1;
		}
		else if (event.abilityIdMatches(0x8BE5)) {
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
		int rotationFactor = clockwise ? 1 : -1;
		int finalDir = (initialDir + rotationFactor * times + 8) % 4;
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
	private final ModifiableCallout<AbilityCastStart> targetedLightRight = ModifiableCallout.durationBasedCall("Targeted Light: Right", "Right Flank towards Lala");
	private final ModifiableCallout<AbilityCastStart> targetedLightBack = ModifiableCallout.durationBasedCall("Targeted Light: Back", "Look Away from Lala");
	private final ModifiableCallout<AbilityCastStart> targetedLightLeft = ModifiableCallout.durationBasedCall("Targeted Light: Left", "Left Flank towards Lala");

	@HandleEvents
	public void targetedLight(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x8CE0)) {
			return;
		}
		Integer initialDir = lalaUnseen;
		Boolean clockwise = lalaPlayerClockwise;
		Integer times = lalaPlayerTimes;
		lalaUnseen = null;
		lalaPlayerTimes = null;
		if (initialDir == null || clockwise == null || times == null) {
			return;
		}
		// 安置が回転するので、プレイヤーは逆向きに回る
		int rotationFactor = clockwise ? -1 : 1;
		int finalDir = (initialDir + rotationFactor * times + 8) % 4;
		ModifiableCallout<AbilityCastStart> call = switch (finalDir) {
			case 0 -> targetedLightFront;
			case 1 -> targetedLightRight;
			case 2 -> targetedLightBack;
			default -> targetedLightLeft;
		};
		context.accept(call.getModified(event));
	}

	@NpcCastCallout(0x8C04)
	private final ModifiableCallout<AbilityCastStart> strategicStrike = ModifiableCallout.durationBasedCall("Strategic Strike", "Buster on {event.target}");

	private final ModifiableCallout<BuffApplied> planarOne = new ModifiableCallout<>("Planar Tactics: One", "One", 7_000);
	private final ModifiableCallout<BuffApplied> planarBigTwo = new ModifiableCallout<>("Planar Tactics: Two (with Three)", "Two (stack with three)", 7_000);
	private final ModifiableCallout<BuffApplied> planarSmallTwo = new ModifiableCallout<>("Planar Tactics: Two (with One)", "Two (stack with one)", 7_000);
	private final ModifiableCallout<BuffApplied> planarEitherTwo = new ModifiableCallout<>("Planar Tactics: Either Two", "Either Two (with {player})", 7_000);
	private final ModifiableCallout<BuffApplied> planarThree = new ModifiableCallout<>("Planar Tactics: Three", "Three", 7_000);
	private final ModifiableCallout<BuffApplied> planarSmallTwoOn = new ModifiableCallout<>("Planar Tactics: Twos with One", "(Two with one: {players})", 7_000);
	private final ModifiableCallout<BuffApplied> planarUnknown = new ModifiableCallout<>("Planar Tactics: Number Only", "{num}", 7_000);

	@HandleEvents
	public void planarTactics(EventContext context, BuffApplied event) {
		// E8B = Surge Vector、E8C = Subtractive Suppressor Alpha。Surge Vector は後でも使われるので最初の1回だけ
		if (lalaPlanarTacticsDone || !event.buffIdMatches(0xE8C, 0xE8B)) {
			return;
		}
		lalaSubAlpha.add(event);
		if (lalaSubAlpha.size() != 6) {
			return;
		}
		lalaPlanarTacticsDone = true;

		List<XivCombatant> stacks = lalaSubAlpha.stream().filter(ba -> ba.buffIdMatches(0xE8B)).map(BuffApplied::getTarget).toList();
		List<BuffApplied> nums = lalaSubAlpha.stream().filter(ba -> ba.buffIdMatches(0xE8C)).toList();
		BuffApplied myNum = nums.stream().filter(ba -> ba.getTarget().isThePlayer()).findFirst().orElse(null);
		if (myNum == null) {
			return;
		}
		long myNumber = myNum.getStacks();
		if (myNumber < 1 || myNumber > 4) {
			return;
		}
		var defaultCall = planarUnknown.getModified(myNum, Map.of("num", myNumber));
		if (stacks.size() != 2 || nums.size() != 4) {
			context.accept(defaultCall);
			return;
		}
		XivCombatant one = nums.stream().filter(ba -> ba.getStacks() == 1).map(BuffApplied::getTarget).findFirst().orElse(null);
		if (one == null) {
			context.accept(defaultCall);
			return;
		}
		boolean isOneStack = stacks.contains(one);
		List<XivCombatant> twos = nums.stream().filter(ba -> ba.getStacks() == 2).map(BuffApplied::getTarget).toList();
		// 1と組めるのは、1と頭割りの有無が逆の2
		List<XivCombatant> smallTwos = twos.stream().filter(two -> stacks.contains(two) != isOneStack).toList();
		if (smallTwos.isEmpty()) {
			context.accept(defaultCall);
			return;
		}
		XivCombatant me = myNum.getTarget();
		boolean isPlayerSmallTwo = smallTwos.contains(me);

		// どちらの2も1と組める場合は相談が必要
		if (isPlayerSmallTwo && smallTwos.size() >= 2) {
			context.accept(planarEitherTwo.getModified(myNum, Map.of("player", other(smallTwos, me))));
			return;
		}

		ModifiableCallout<BuffApplied> roleCall;
		if (one.equals(me)) {
			roleCall = planarOne;
		}
		else if (twos.contains(me)) {
			roleCall = isPlayerSmallTwo ? planarSmallTwo : planarBigTwo;
		}
		else {
			roleCall = planarThree;
		}
		context.accept(roleCall.getModified(myNum));
		if (!isPlayerSmallTwo) {
			String names = smallTwos.stream().map(XivCombatant::getName).collect(Collectors.joining(", "));
			context.accept(planarSmallTwoOn.getModified(myNum, Map.of("players", names)));
		}
	}

	private final ModifiableCallout<BuffApplied> leftwardMarch = new ModifiableCallout<BuffApplied>("Forward March: Leftward", "Leftward March", 4_000).statusIcon(0xE83);
	private final ModifiableCallout<BuffApplied> rightwardMarch = new ModifiableCallout<BuffApplied>("Forward March: Rightward", "Rightward March", 4_000).statusIcon(0xE83);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> lalaForwardMarchSq = SqtTemplates.sq(60_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0xE83) && ba.getTarget().isThePlayer(),
			(e1, s) -> {
				long delay = e1.getInitialDuration().toMillis() - forwardMarchLeadMs;
				if (delay > 0) {
					s.waitMs(delay);
				}
				Boolean clockwise = lalaPlayerClockwise;
				Integer times = lalaPlayerTimes;
				lalaPlayerClockwise = null;
				lalaPlayerTimes = null;
				if (clockwise == null || times == null) {
					return;
				}
				int rotationFactor = clockwise ? 1 : -1;
				int finalDir = (rotationFactor * times + 8) % 4;
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
		// E8D は Subtractive Suppressor Beta のデバフ
		if (lalaSpatialTacticsDone || !event.buffIdMatches(0xE8D) || !event.getTarget().isThePlayer()) {
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

	// ---------------- スタティス ----------------

	@NpcCastCallout(0x8966)
	private final ModifiableCallout<AbilityCastStart> aeroIV = ModifiableCallout.durationBasedCall("Aero IV", "Raidwide");

	private final ModifiableCallout<AbilityUsedEvent> trickReloadStackFirst = new ModifiableCallout<>("Trick Reload: Stack First", "Stack => Spread");
	private final ModifiableCallout<AbilityUsedEvent> trickReloadSpreadFirst = new ModifiableCallout<>("Trick Reload: Spread First", "Spread => Stack");
	private final ModifiableCallout<AbilityUsedEvent> trickReloadSafeLater = new ModifiableCallout<>("Trick Reload: Safe Number", "({num} safe later)");

	@HandleEvents
	public void trickReload(EventContext context, AbilityUsedEvent event) {
		// 8925 は Locked and Loaded（装填成功）、8926 は Misload（装填失敗）
		if (!event.abilityIdMatches(0x8925, 0x8926) || !event.isFirstTarget()) {
			return;
		}
		staticeBullet.add(event);
		int count = staticeBullet.size();
		boolean isMisload = event.abilityIdMatches(0x8926);
		// 8発のうち最初と最後が Trapshooting 用、間の6発が Trigger Happy 用
		if (count == 1) {
			staticeTrapshooting.clear();
			if (isMisload) {
				staticeTrapshooting.add(Mech.STACK);
				staticeTrapshooting.add(Mech.SPREAD);
			}
			else {
				staticeTrapshooting.add(Mech.SPREAD);
				staticeTrapshooting.add(Mech.STACK);
			}
			context.accept((isMisload ? trickReloadStackFirst : trickReloadSpreadFirst).getModified(event));
		}
		else if (count <= 7 && isMisload) {
			staticeTriggerHappy = count - 1;
			context.accept(trickReloadSafeLater.getModified(event, Map.of("num", staticeTriggerHappy)));
		}
		if (count == 8) {
			staticeBullet.clear();
		}
	}

	private final ModifiableCallout<AbilityCastStart> trapshootingSpread = ModifiableCallout.durationBasedCall("Trapshooting: Spread", "Spread");
	private final ModifiableCallout<AbilityCastStart> trapshootingStack = ModifiableCallout.durationBasedCall("Trapshooting: Stack", "Stack");

	@HandleEvents
	public void trapshooting(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x8D1C, 0x8976)) {
			return;
		}
		Mech mech = staticeTrapshooting.poll();
		if (mech != null) {
			context.accept((mech == Mech.STACK ? trapshootingStack : trapshootingSpread).getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> triggerHappy = ModifiableCallout.durationBasedCall("Trigger Happy", "{num}");

	@HandleEvents
	public void triggerHappy(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x8968)) {
			return;
		}
		Integer num = staticeTriggerHappy;
		staticeTriggerHappy = null;
		if (num != null) {
			context.accept(triggerHappy.getModified(event, Map.of("num", num)));
		}
	}

	private final ModifiableCallout<BuffApplied> dartOnYou = new ModifiableCallout<BuffApplied>("Bull's-eye: Dart on You", "Dart on You").statusIcon(0xE9E);
	private final ModifiableCallout<BuffApplied> dartOnYouFlex = new ModifiableCallout<BuffApplied>("Bull's-eye: Dart on You (Flex)", "Dart on You ({player} unmarked)").statusIcon(0xE9E);
	private final ModifiableCallout<BuffApplied> noDart = new ModifiableCallout<>("Bull's-eye: No Dart", "No Dart");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> bullsEyeSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0xE9E) && !staticeIsPinwheelingDartboard,
			(e1, s) -> {
				List<BuffApplied> darts = collectNear(s, e1, 3, BuffApplied.class, ba -> ba.buffIdMatches(0xE9E));
				List<XivCombatant> dartTargets = darts.stream().map(BuffApplied::getTarget).toList();
				BuffApplied mine = darts.stream().filter(ba -> ba.getTarget().isThePlayer()).findFirst().orElse(null);
				if (mine == null) {
					s.updateCall(noDart, e1);
					return;
				}
				List<XivPlayerCharacter> flexers = state.getPartyList().stream().filter(pc -> !dartTargets.contains(pc)).toList();
				if (flexers.size() == 1) {
					s.setParam("player", flexers.get(0));
					s.updateCall(dartOnYouFlex, mine);
				}
				else {
					s.updateCall(dartOnYou, mine);
				}
			});

	private final ModifiableCallout<AbilityCastStart> surpriseBalloon = ModifiableCallout.durationBasedCall("Surprise Balloon", "({num} safe soon)");

	@HandleEvents
	public void surpriseBalloon(EventContext context, AbilityCastStart event) {
		// ノックバック2回を両方とも無効化する猶予が短いので、早めに知らせておく
		if (!event.abilityIdMatches(0x8927)) {
			return;
		}
		Integer num = staticeTriggerHappy;
		staticeTriggerHappy = null;
		if (num != null) {
			staticePopTriggerHappyNum = num;
			context.accept(surpriseBalloon.getModified(event, Map.of("num", num)));
		}
	}

	private final ModifiableCallout<AbilityCastStart> pop = ModifiableCallout.durationBasedCall("Pop", "Knockback");
	private final ModifiableCallout<AbilityCastStart> popToNum = ModifiableCallout.durationBasedCall("Pop (Safe Number)", "Knockback => {num}");

	@HandleEvents
	public void pop(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x896B) || suppressed(0x896B, event, 20_000)) {
			return;
		}
		Integer num = staticePopTriggerHappyNum;
		staticePopTriggerHappyNum = null;
		if (num == null) {
			context.accept(pop.getModified(event));
		}
		else {
			context.accept(popToNum.getModified(event, Map.of("num", num)));
		}
	}

	private final ModifiableCallout<BuffApplied> faceForward = new ModifiableCallout<>("Statice Face: Forward", "Forward March => {mech}", 5_000);
	private final ModifiableCallout<BuffApplied> faceBackward = new ModifiableCallout<>("Statice Face: Backward", "Backward March => {mech}", 5_000);
	private final ModifiableCallout<BuffApplied> faceLeft = new ModifiableCallout<>("Statice Face: Left", "Left March => {mech}", 5_000);
	private final ModifiableCallout<BuffApplied> faceRight = new ModifiableCallout<>("Statice Face: Right", "Right March => {mech}", 5_000);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> staticeFaceSq = SqtTemplates.sq(60_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0xDD2, 0xDD3, 0xDD4, 0xDD5) && ba.getTarget().isThePlayer(),
			(e1, s) -> {
				long delay = e1.getInitialDuration().toMillis() - staticeFaceLeadMs;
				if (delay > 0) {
					s.waitMs(delay);
				}
				Object mech;
				Integer num = staticeTriggerHappy;
				if (num != null) {
					mech = num;
					staticeTriggerHappy = null;
				}
				else {
					Mech next = staticeTrapshooting.poll();
					if (next == null) {
						mech = "Unknown";
					}
					else {
						mech = next == Mech.STACK ? "Stack" : "Spread";
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
	private final ModifiableCallout<BuffApplied> burningChains = new ModifiableCallout<BuffApplied>("Burning Chains", "Break Chains").statusIcon(0x301);

	@NpcCastCallout(0x8965)
	private final ModifiableCallout<AbilityCastStart> shockingAbandon = ModifiableCallout.durationBasedCall("Shocking Abandon", "Buster on {event.target}");

	@HandleEvents
	public void pinwheelingTracker(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8CBF)) {
			staticeIsPinwheelingDartboard = true;
			staticePinwheelDarts.clear();
		}
	}

	@HandleEvents
	public void pinwheelingDarts(EventContext context, BuffApplied event) {
		if (staticeIsPinwheelingDartboard && event.buffIdMatches(0xE9E)) {
			staticePinwheelDarts.add(event.getTarget());
		}
	}

	private final ModifiableCallout<RawAddCombatantEvent> pinwheelDartWith = new ModifiableCallout<>("Pinwheeling Dartboard: Dart on You", "Dart (with {player})", 6_000);
	private final ModifiableCallout<RawAddCombatantEvent> pinwheelNoDart = new ModifiableCallout<>("Pinwheeling Dartboard: No Dart", "No Dart", 6_000);
	private final ModifiableCallout<RawAddCombatantEvent> avoidBlue = new ModifiableCallout<>("Pinwheeling Dartboard: Avoid Blue", "Avoid Blue", 6_000);
	private final ModifiableCallout<RawAddCombatantEvent> avoidRed = new ModifiableCallout<>("Pinwheeling Dartboard: Avoid Red", "Avoid Red", 6_000);
	private final ModifiableCallout<RawAddCombatantEvent> avoidYellow = new ModifiableCallout<>("Pinwheeling Dartboard: Avoid Yellow", "Avoid Yellow", 6_000);

	@HandleEvents
	public void pinwheelingColor(EventContext context, RawAddCombatantEvent event) {
		XivCombatant cbt = event.getEntity();
		if (cbt.getbNpcNameId() != homingPatternNameId) {
			return;
		}
		Position pos = cbt.getPos();
		if (pos != null) {
			double x = pos.getX() - staticeCenterX;
			double y = pos.getY() - staticeCenterY;
			// 北を境目に12等分され、青・赤・黄の順に4回繰り返す。Homing Pattern は区画の中央に落ちる
			int dir12 = (int) (Math.round(6 - 6 * Math.atan2(x, y) / Math.PI + 11.5) % 12);
			ModifiableCallout<RawAddCombatantEvent> colorCall = switch (dir12 % 3) {
				case 0 -> avoidBlue;
				case 1 -> avoidRed;
				default -> avoidYellow;
			};
			context.accept(colorCall.getModified(event));
		}

		if (staticePinwheelDarts.size() != 2) {
			return;
		}
		XivCombatant me = state.getPlayer();
		if (!staticePinwheelDarts.contains(me)) {
			context.accept(pinwheelNoDart.getModified(event));
			return;
		}
		context.accept(pinwheelDartWith.getModified(event, Map.of("player", other(staticePinwheelDarts, me))));
	}

	private final ModifiableCallout<HeadMarkerEvent> dartboardTether = new ModifiableCallout<>("Pinwheeling Dartboard: Tether", "Tether with {player}");
	private final ModifiableCallout<HeadMarkerEvent> dartboardStack = new ModifiableCallout<>("Pinwheeling Dartboard: Stack", "Stack with {player}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> dartboardMechSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(0x61) && staticeIsPinwheelingDartboard,
			(e1, s) -> {
				List<HeadMarkerEvent> markers = collectNear(s, e1, 2, HeadMarkerEvent.class, hm -> hm.markerIdMatches(0x61));
				if (markers.size() != 2) {
					return;
				}
				List<XivCombatant> tethers = markers.stream().map(HeadMarkerEvent::getTarget).toList();
				XivCombatant me = state.getPlayer();
				if (tethers.contains(me)) {
					s.setParam("player", other(tethers, me));
					s.updateCall(dartboardTether, e1);
					return;
				}
				List<XivCombatant> nonTethers = state.getPartyList().stream()
						.filter(pc -> !tethers.contains(pc))
						.map(pc -> (XivCombatant) pc)
						.toList();
				XivCombatant partner = other(nonTethers, me);
				if (partner != null) {
					s.setParam("player", partner);
					s.updateCall(dartboardStack, e1);
				}
			});

}
