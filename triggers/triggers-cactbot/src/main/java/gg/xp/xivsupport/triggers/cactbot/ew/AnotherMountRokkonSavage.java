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
import gg.xp.xivsupport.events.actlines.events.BuffRemoved;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.MapEffectEvent;
import gg.xp.xivsupport.events.actlines.events.RawAddCombatantEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.ArenaPos;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;
import gg.xp.xivsupport.models.XivPlayerCharacter;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/dungeon/another_mount_rokkon-savage.ts をもとに移植
@CalloutRepo(name = "Another Mount Rokkon Savage", duty = KnownDuty.AnotherMountRokkonSavage)
public class AnotherMountRokkonSavage extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long RAIRIN_NAME_ID = 12430;
	private static final long VFX_BUFF = 0xB9A;
	private static final long ADD_VFX_BUFF = 0x808;
	private static final long ONI_CLAW_VFX = 0x257;
	private static final long BLUE_SOLDIER_VFX = 0x5E;
	private static final long KASUMI_FIRST_VFX = 0x24C;
	private static final long KASUMI_LAST_VFX = 0x253;
	private static final long SHADOW_FIRST_VFX = 0x248;
	private static final long SHADOW_LAST_VFX = 0x24B;
	private static final long LIMIT_CUT_FIRST = 0x150;
	private static final long LIMIT_CUT_LAST = 0x153;
	private static final int TOWER_SETS = 4;
	private static final int REISHO_PULSES = 8;
	private static final int SOLDIERS_MARKERS = 8;
	private static final double SHISHIO_CENTER_X = 0;
	private static final double SHISHIO_CENTER_Y = -100;
	private static final double MOKO_CENTER_X = -200;
	private static final double MOKO_CENTER_Y = 0;
	// 4本目の突進が斜めか北に戻るかの境目
	private static final double RAIRIN_DIAGONAL_X = 18;

	private static final String SPREAD = "Spread";
	private static final String UNKNOWN = "?";
	// マーカーの値は 後ろ/左/前/右 の順に並んでいる
	private static final String[] MARKER_DIRS = {"Back", "Left", "Front", "Right"};
	private static final String[] FOLLOWUP_DIRS = {"Stay", "Left", "Through", "Right"};

	private static final long[] TRIPLE_KASUMI_IDS = {
			0x85E4, 0x85E5, 0x85E6, 0x85E7, 0x85E8, 0x85E9, 0x85EA, 0x85EB,
			0x85EE, 0x85EF, 0x85F0, 0x85F1, 0x85F2, 0x85F3, 0x85F4, 0x85F5,
	};

	private enum StackPartners {
		MELEE("Melees Stack", "melees together"),
		ROLE("Role Stacks", "roles together"),
		PARTNER("Partner Stacks", "partners together"),
		UNKNOWN("Stacks", AnotherMountRokkonSavage.UNKNOWN);

		private final String mechanic;
		private final String together;

		StackPartners(String mechanic, String together) {
			this.mechanic = mechanic;
			this.together = together;
		}
	}

	private record StackSpread(String first, String second, XivCombatant player1, XivCombatant player2) {
		Map<String, Object> params() {
			return Map.of("first", first, "second", second, "player1", player1, "player2", player2);
		}
	}

	private record IaiTether(XivCombatant shadow, XivCombatant player) {
	}

	private XivState state;

	private boolean seenShishuYukiAuto;
	private int smokeaterCount;
	private final List<Position> rairinCollect = new ArrayList<>();
	private int wailCount;
	private final List<BuffApplied> wailingCollect = new ArrayList<>();
	private boolean wailing1Called;
	private @Nullable String stackSpreadFirst;
	private @Nullable String stackSpreadSecond;
	private @Nullable String vortexSecond;
	private final List<AbilityCastStart> devilishThrallCollect = new ArrayList<>();
	private int reishoCount;
	private final List<HeadMarkerEvent> ghostHeadmarkers = new ArrayList<>();
	private boolean ghostTower;
	private int sparksCount;
	private final List<BuffApplied> sparksCollect = new ArrayList<>();
	private final XivCombatant[] rousingBlue = new XivCombatant[TOWER_SETS];
	private final XivCombatant[] rousingOrange = new XivCombatant[TOWER_SETS];
	private int rousingTowerCount;
	private final List<BuffApplied> malformedCollect = new ArrayList<>();
	private final Deque<Long> myMalformedEffects = new ArrayDeque<>();
	private final List<Long> tripleKasumiCollect = new ArrayList<>();
	private final List<MapEffectEvent> explosionLineCollect = new ArrayList<>();
	private final List<BuffApplied> invocationCollect = new ArrayList<>();
	private boolean invocationCalled;
	private final List<IaiTether> iaigiriTether = new ArrayList<>();
	private final List<BuffApplied> iaigiriPurple = new ArrayList<>();
	private @Nullable Position myAccursedEdge;
	private @Nullable IaiTether myIaigiriTether;
	private @Nullable Boolean oniClawNorthSouth;
	private boolean seenSoldiersOfDeath;

	public AnotherMountRokkonSavage(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AnotherMountRokkonSavage);
	}

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		seenShishuYukiAuto = false;
		smokeaterCount = 0;
		rairinCollect.clear();
		wailCount = 0;
		wailingCollect.clear();
		wailing1Called = false;
		stackSpreadFirst = null;
		stackSpreadSecond = null;
		vortexSecond = null;
		devilishThrallCollect.clear();
		reishoCount = 0;
		ghostHeadmarkers.clear();
		ghostTower = false;
		sparksCount = 0;
		sparksCollect.clear();
		for (int i = 0; i < TOWER_SETS; i++) {
			rousingBlue[i] = null;
			rousingOrange[i] = null;
		}
		rousingTowerCount = 0;
		malformedCollect.clear();
		myMalformedEffects.clear();
		tripleKasumiCollect.clear();
		explosionLineCollect.clear();
		invocationCollect.clear();
		invocationCalled = false;
		clearIaigiri();
		seenSoldiersOfDeath = false;
	}

	private void clearIaigiri() {
		iaigiriTether.clear();
		iaigiriPurple.clear();
		myAccursedEdge = null;
		myIaigiriTether = null;
		oniClawNorthSouth = null;
	}

	// 共通処理

	// 4人パーティで頭割り2人とそれ以外2人の組み合わせから、どのペアで組むかを判定する
	private StackPartners findStackPartners(@Nullable XivCombatant stack1, @Nullable XivCombatant stack2, boolean preferMelee) {
		List<XivPlayerCharacter> party = state.getPartyList();
		if (stack1 == null || stack2 == null || party.size() != 4) {
			return StackPartners.UNKNOWN;
		}
		List<Job> stackJobs = new ArrayList<>();
		List<Job> unmarkedJobs = new ArrayList<>();
		for (XivPlayerCharacter member : party) {
			if (member.getId() == stack1.getId() || member.getId() == stack2.getId()) {
				stackJobs.add(member.getJob());
			}
			else {
				unmarkedJobs.add(member.getJob());
			}
		}
		if (stackJobs.size() != 2 || unmarkedJobs.size() != 2) {
			return StackPartners.UNKNOWN;
		}
		if (preferMelee && couldStack(stackJobs, unmarkedJobs, job -> job.isMeleeDps() || job.isTank())) {
			return StackPartners.MELEE;
		}
		if (couldStack(stackJobs, unmarkedJobs, Job::isSupport)) {
			return StackPartners.ROLE;
		}
		long stackCount = stackJobs.stream().filter(Job::isSupport).count();
		long unmarkedCount = unmarkedJobs.stream().filter(Job::isSupport).count();
		if (stackCount == 2 && unmarkedCount == 0 || stackCount == 0 && unmarkedCount == 2) {
			return StackPartners.PARTNER;
		}
		return StackPartners.UNKNOWN;
	}

	private static boolean couldStack(List<Job> stackJobs, List<Job> unmarkedJobs, Predicate<Job> condition) {
		return stackJobs.stream().filter(condition).count() == 1 && unmarkedJobs.stream().filter(condition).count() == 1;
	}

	// 頭割りと散開の効果時間を比べて順番を決める。後続のコールのために結果を覚えておく
	private @Nullable StackSpread stackSpread(List<BuffApplied> collect, long stackId, long spreadId) {
		List<BuffApplied> stacks = collect.stream().filter(ba -> ba.buffIdMatches(stackId)).toList();
		BuffApplied spread = collect.stream().filter(ba -> ba.buffIdMatches(spreadId)).findFirst().orElse(null);
		if (stacks.size() < 2 || spread == null) {
			return null;
		}
		BuffApplied stack1 = stacks.get(0);
		BuffApplied stack2 = stacks.get(1);
		boolean stackFirst = stack1.getInitialDuration().compareTo(spread.getInitialDuration()) < 0;
		String stackMech = findStackPartners(stack1.getTarget(), stack2.getTarget(), true).mechanic;
		stackSpreadFirst = stackFirst ? stackMech : SPREAD;
		stackSpreadSecond = stackFirst ? SPREAD : stackMech;
		List<XivCombatant> players = new ArrayList<>(List.of(stack1.getTarget(), stack2.getTarget()));
		players.sort(Comparator.comparing(XivCombatant::getName));
		return new StackSpread(stackSpreadFirst, stackSpreadSecond, players.get(0), players.get(1));
	}

	private @Nullable String takeSecondMechanic() {
		String mech = stackSpreadSecond;
		stackSpreadFirst = null;
		stackSpreadSecond = null;
		return mech;
	}

	// cactbot の 8方向番号と同じく北を0として時計回り
	private static ArenaSector dirFrom(Position pos, double centerX, double centerY) {
		int dir = (int) Math.round(4 - 4 * Math.atan2(pos.x() - centerX, pos.y() - centerY) / Math.PI) % 8;
		return ArenaSector.all.get(dir);
	}

	private @Nullable Position posOf(XivCombatant cbt) {
		return state.getLatestCombatantData(cbt).getPos();
	}

	private static boolean isShadowVfx(BuffApplied ba) {
		return ba.buffIdMatches(VFX_BUFF) && ba.getRawStacks() >= SHADOW_FIRST_VFX && ba.getRawStacks() <= SHADOW_LAST_VFX;
	}

	private static int shadowIndex(BuffApplied ba) {
		return (int) (ba.getRawStacks() - SHADOW_FIRST_VFX);
	}

	// 収集用ハンドラとの実行順に依存しないよう、今回のイベントを含めた一覧を返す
	private List<BuffApplied> purplesWith(BuffApplied ba) {
		if (iaigiriPurple.contains(ba)) {
			return iaigiriPurple;
		}
		List<BuffApplied> out = new ArrayList<>(iaigiriPurple);
		out.add(ba);
		return out;
	}

	private @Nullable BuffApplied lastPurpleOn(XivCombatant shadow) {
		for (int i = iaigiriPurple.size() - 1; i >= 0; i--) {
			BuffApplied ba = iaigiriPurple.get(i);
			if (ba.getTarget().getId() == shadow.getId()) {
				return ba;
			}
		}
		return null;
	}

	// 1つ目の雑魚

	@NpcCastCallout(0x8668)
	private final ModifiableCallout<AbilityCastStart> disciplesOfLevin = ModifiableCallout.durationBasedCall("Disciples of Levin", "Out");

	private final ModifiableCallout<AbilityCastStart> barrelingSmashOnYou = ModifiableCallout.durationBasedCall("Barreling Smash (on You)", "Charge on You");
	private final ModifiableCallout<AbilityCastStart> barrelingSmashOther = ModifiableCallout.durationBasedCall("Barreling Smash (on Other)", "Charge on {event.target}");

	@HandleEvents
	public void barrelingSmash(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8665) && !event.getSource().isPc()) {
			context.accept((event.getTarget().isThePlayer() ? barrelingSmashOnYou : barrelingSmashOther).getModified(event));
		}
	}

	@NpcCastCallout(0x8666)
	private final ModifiableCallout<AbilityCastStart> howl = ModifiableCallout.durationBasedCall("Howl", "Raidwide");

	@NpcCastCallout(0x8667)
	private final ModifiableCallout<AbilityCastStart> masterOfLevin = ModifiableCallout.durationBasedCall("Master of Levin", "Get Under");

	@NpcCastCallout(0x866C)
	private final ModifiableCallout<AbilityCastStart> scytheTail = ModifiableCallout.durationBasedCall("Scythe Tail", "Out");

	@NpcCastCallout(0x866A)
	private final ModifiableCallout<AbilityCastStart> twister = ModifiableCallout.durationBasedCall("Twister", "Stack on {event.target}");

	@NpcCastCallout(0x866B)
	private final ModifiableCallout<AbilityCastStart> crosswind = ModifiableCallout.durationBasedCall("Crosswind", "Knockback");

	// 釣る前のシシュウ・ユキは南向きで薙ぎ払うので、絶対方向でコールする
	private final ModifiableCallout<AbilityCastStart> yukiRightSwipe = ModifiableCallout.durationBasedCall("Right Swipe", "Left");
	private final ModifiableCallout<AbilityCastStart> yukiRightSwipeUnpulled = ModifiableCallout.durationBasedCall("Right Swipe (Before Pull)", "East");
	private final ModifiableCallout<AbilityCastStart> yukiLeftSwipe = ModifiableCallout.durationBasedCall("Left Swipe", "Right");
	private final ModifiableCallout<AbilityCastStart> yukiLeftSwipeUnpulled = ModifiableCallout.durationBasedCall("Left Swipe (Before Pull)", "West");

	@HandleEvents
	public void yukiSwipes(EventContext context, AbilityCastStart event) {
		if (event.getSource().isPc()) {
			return;
		}
		if (event.abilityIdMatches(0x8688)) {
			context.accept((seenShishuYukiAuto ? yukiRightSwipe : yukiRightSwipeUnpulled).getModified(event));
		}
		else if (event.abilityIdMatches(0x8689)) {
			context.accept((seenShishuYukiAuto ? yukiLeftSwipe : yukiLeftSwipeUnpulled).getModified(event));
		}
	}

	@HandleEvents
	public void yukiAuto(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x7A58) && !event.getSource().isPc()) {
			seenShishuYukiAuto = true;
		}
	}

	// 獅子王

	@NpcCastCallout(0x8441)
	private final ModifiableCallout<AbilityCastStart> enkyo = ModifiableCallout.durationBasedCall("Enkyo", "Raidwide");

	private final ModifiableCallout<AbilityUsedEvent> smokeater = new ModifiableCallout<>("Smokeater Count", "{count}");

	@HandleEvents
	public void smokeater(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x8420, 0x8421) || event.getSource().isPc() || !event.isFirstTarget()) {
			return;
		}
		// 8420 が最初の1回目で、8421 は任意の追加分
		if (event.abilityIdMatches(0x8420)) {
			smokeaterCount = 1;
		}
		else {
			smokeaterCount++;
		}
		context.accept(smokeater.getModified(event, Map.of("count", Math.min(smokeaterCount, 3))));
	}

	@NpcCastCallout(0x8442)
	private final ModifiableCallout<AbilityCastStart> splittingCry = ModifiableCallout.durationBasedCall("Splitting Cry", "Buster on {event.target}");

	@NpcAbilityUsedCallout(value = 0x8442, suppressMs = 5_000)
	private final ModifiableCallout<AbilityUsedEvent> splitter = new ModifiableCallout<>("Splitter", "Front or Sides");

	private final ModifiableCallout<RawAddCombatantEvent> noblePursuit = new ModifiableCallout<>("Noble Pursuit", "{safe}");

	@HandleEvents
	public void rairinAdded(EventContext context, RawAddCombatantEvent event) {
		XivCombatant entity = event.getEntity();
		if (entity.getbNpcNameId() != RAIRIN_NAME_ID) {
			return;
		}
		Position pos = event.getFullInfo() != null ? event.getFullInfo().getPos() : entity.getPos();
		if (pos == null) {
			return;
		}
		rairinCollect.add(pos);
		if (rairinCollect.size() != 4) {
			return;
		}
		// 3本目と反対側に逃げ、4本目が斜めなら南、北に戻るなら北
		Position three = rairinCollect.get(2);
		Position four = rairinCollect.get(3);
		boolean threeEast = three.x() > SHISHIO_CENTER_X;
		boolean fourDiagonal = Math.abs(four.x() - SHISHIO_CENTER_X) < RAIRIN_DIAGONAL_X;
		ArenaSector safe;
		if (fourDiagonal) {
			safe = threeEast ? ArenaSector.SOUTHWEST : ArenaSector.SOUTHEAST;
		}
		else {
			safe = threeEast ? ArenaSector.NORTHWEST : ArenaSector.NORTHEAST;
		}
		context.accept(noblePursuit.getModified(event, Map.of("safe", safe)));
	}

	@HandleEvents
	public void wailCount(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x843E) && !event.getSource().isPc()) {
			wailCount++;
			wailingCollect.clear();
		}
	}

	@HandleEvents
	public void wailingCollect(EventContext context, BuffApplied event) {
		// DEB が散開、DEC が頭割り
		if (event.buffIdMatches(0xDEB, 0xDEC)) {
			wailingCollect.add(event);
		}
	}

	private final ModifiableCallout<BuffApplied> unnaturalWailing = new ModifiableCallout<>("Unnatural Wailing", "{first} => {second}", "{first} => {second} ({player1}, {player2})");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> unnaturalWailingSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0xDEB, 0xDEC) && wailCount == 1 && !wailing1Called,
			(e1, s) -> {
				wailing1Called = true;
				s.waitMs(500);
				StackSpread info = stackSpread(wailingCollect, 0xDEC, 0xDEB);
				if (info != null) {
					s.updateCall(unnaturalWailing.getModified(e1, info.params()));
				}
			});

	private final ModifiableCallout<AbilityCastStart> thunderEyeVortex = ModifiableCallout.durationBasedCall("Vortex of the Thunder Eye",
			"{inOut} + {first} => {outIn} + {second}",
			"{inOut} + {first} => {outIn} + {second} ({player1}, {player2})");

	@HandleEvents
	public void thunderEyeVortex(EventContext context, AbilityCastStart event) {
		// 843A が外から、843C が内から
		if (!event.abilityIdMatches(0x843A, 0x843C) || event.getSource().isPc()) {
			return;
		}
		StackSpread info = stackSpread(wailingCollect, 0xDEC, 0xDEB);
		if (info == null) {
			return;
		}
		boolean inFirst = event.abilityIdMatches(0x843C);
		String inOut = inFirst ? "In" : "Out";
		String outIn = inFirst ? "Out" : "In";
		vortexSecond = outIn;
		Map<String, Object> params = new LinkedHashMap<>(info.params());
		params.put("inOut", inOut);
		params.put("outIn", outIn);
		context.accept(thunderEyeVortex.getModified(event, params));
	}

	private final ModifiableCallout<AbilityUsedEvent> thunderEyeVortexFollowup = new ModifiableCallout<>("Vortex of the Thunder Eye Followup", "{inOut} + {mechanic}");
	private final RepeatSuppressor thunderEyeVortexFollowupSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void thunderEyeVortexFollowup(EventContext context, AbilityUsedEvent event) {
		// 843F が Unnatural Ailment、8440 が Unnatural Force
		if (!event.abilityIdMatches(0x843F, 0x8440) || wailCount == 1 || !thunderEyeVortexFollowupSupp.check(event)) {
			return;
		}
		String mech = takeSecondMechanic();
		if (mech == null) {
			return;
		}
		String inOut = vortexSecond;
		vortexSecond = null;
		if (inOut == null) {
			return;
		}
		context.accept(thunderEyeVortexFollowup.getModified(event, Map.of("inOut", inOut, "mechanic", mech)));
	}

	@NpcCastCallout(0x8439)
	private final ModifiableCallout<AbilityCastStart> thunderVortex = ModifiableCallout.durationBasedCall("Thunder Vortex", "Get Under");

	@HandleEvents
	public void devilishThrallCollect(EventContext context, AbilityCastStart event) {
		// 8432 が右薙ぎ、8433 が左薙ぎ
		if (event.abilityIdMatches(0x8432, 0x8433) && !event.getSource().isPc()) {
			devilishThrallCollect.add(event);
		}
	}

	private final ModifiableCallout<AbilityCastStart> devilishThrallDiamond = new ModifiableCallout<>("Devilish Thrall: Cardinal Safe", "{dir} Diamond + {mechanic}", 6_000);
	private final ModifiableCallout<AbilityCastStart> devilishThrallSquare = new ModifiableCallout<>("Devilish Thrall: Intercard Safe", "{dir} Square + {mechanic}", 6_000);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> devilishThrallSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x8432, 0x8433) && !acs.getSource().isPc(),
			(e1, s) -> {
				s.waitMs(400);
				s.waitThenRefreshCombatants(100);
				List<AbilityCastStart> casts = new ArrayList<>(devilishThrallCollect);
				devilishThrallCollect.clear();
				if (casts.size() != 4) {
					return;
				}
				// 内向きの2体は位置と向きが正反対、外向きの2体は位置に対して直角を向いている
				List<Integer> outward = new ArrayList<>();
				for (AbilityCastStart cast : casts) {
					Position pos = posOf(cast.getSource());
					if (pos == null) {
						return;
					}
					ArenaSector dir = dirFrom(pos, SHISHIO_CENTER_X, SHISHIO_CENTER_Y);
					ArenaSector heading = ArenaPos.combatantFacing(pos);
					if (dir != heading.opposite()) {
						outward.add(ArenaSector.all.indexOf(dir));
					}
				}
				if (outward.size() != 2) {
					return;
				}
				outward.sort(null);
				int pos1 = outward.get(0);
				int pos2 = outward.get(1);
				// 0/6 と 1/7 の組は差が6なので、反対回りで平均を取る
				int average = ((pos1 + pos2 + (pos2 - pos1 == 6 ? 8 : 0)) / 2) % 8;
				ArenaSector safe = ArenaSector.all.get(average);

				String mech;
				if (stackSpreadFirst != null) {
					mech = stackSpreadFirst;
					stackSpreadFirst = null;
				}
				else if (stackSpreadSecond != null) {
					mech = stackSpreadSecond;
					stackSpreadSecond = null;
				}
				else {
					mech = UNKNOWN;
				}
				s.setParam("dir", safe);
				s.setParam("mechanic", mech);
				s.updateCall(safe.isCardinal() ? devilishThrallDiamond : devilishThrallSquare, e1);
			});

	private final ModifiableCallout<AbilityUsedEvent> reishoCountCall = new ModifiableCallout<>("Reisho Count", "{count}", 1_500);
	private final ModifiableCallout<AbilityUsedEvent> reishoTower = new ModifiableCallout<>("Reisho: Tower", "Tower");
	private final RepeatSuppressor reishoSupp = new RepeatSuppressor(Duration.ofMillis(500));

	// 8回のパルスのうち、塔担当には最後の1回で塔を踏むよう促す
	@HandleEvents
	public void reisho(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x8434) || !reishoSupp.check(event)) {
			return;
		}
		reishoCount++;
		if (reishoCount > REISHO_PULSES) {
			return;
		}
		context.accept(reishoCountCall.getModified(event, Map.of("count", reishoCount)));
		if (ghostTower && reishoCount == REISHO_PULSES) {
			context.accept(reishoTower.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> hauntingThrallTower = new ModifiableCallout<>("Haunting Thrall: Tower", "Get Tower (with {player})");
	private final ModifiableCallout<HeadMarkerEvent> hauntingThrallSpread = new ModifiableCallout<>("Haunting Thrall: Spread", "Spread (with {player})");

	@HandleEvents
	public void hauntingThrallMarker(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(0x01D8)) {
			return;
		}
		ghostHeadmarkers.add(event);
		if (ghostHeadmarkers.size() != 2) {
			return;
		}
		List<XivCombatant> spread = ghostHeadmarkers.stream().map(HeadMarkerEvent::getTarget).toList();
		if (spread.stream().anyMatch(XivCombatant::isThePlayer)) {
			ghostTower = false;
			Object other = spread.stream().filter(cbt -> !cbt.isThePlayer()).findFirst().map(Object.class::cast).orElse(UNKNOWN);
			context.accept(hauntingThrallSpread.getModified(event, Map.of("player", other)));
		}
		else {
			ghostTower = true;
			Object other = state.getPartyList().stream()
					.filter(member -> !member.isThePlayer() && spread.stream().noneMatch(cbt -> cbt.getId() == member.getId()))
					.findFirst()
					.map(Object.class::cast)
					.orElse(UNKNOWN);
			context.accept(hauntingThrallTower.getModified(event, Map.of("player", other)));
		}
	}

	// 2つ目の雑魚

	@NpcCastCallout(0x866E)
	private final ModifiableCallout<AbilityCastStart> backwardBlows = ModifiableCallout.durationBasedCall("Backward Blows", "Sides (Stay Sides)");

	@NpcCastCallout(0x866F)
	private final ModifiableCallout<AbilityCastStart> leftwardBlows = ModifiableCallout.durationBasedCall("Leftward Blows", "Right + Behind");

	@NpcCastCallout(0x8670)
	private final ModifiableCallout<AbilityCastStart> rightwardBlows = ModifiableCallout.durationBasedCall("Rightward Blows", "Left + Behind");

	@NpcCastCallout(0x8672)
	private final ModifiableCallout<AbilityCastStart> wrathOfTheTengu = ModifiableCallout.durationBasedCall("Wrath of the Tengu", "Raidwide + Bleed");

	@NpcCastCallout(0x8673)
	private final ModifiableCallout<AbilityCastStart> gazeOfTheTengu = ModifiableCallout.durationBasedCall("Gaze of the Tengu", "Look Away");

	@NpcCastCallout(0x8676)
	private final ModifiableCallout<AbilityCastStart> jujiShuriken = ModifiableCallout.durationBasedCall("Juji Shuriken", "Get Behind");

	@NpcCastCallout(0x8674)
	private final ModifiableCallout<AbilityCastStart> issen = ModifiableCallout.durationBasedCall("Issen", "Buster on {event.target}");

	@NpcCastCallout(0x8675)
	private final ModifiableCallout<AbilityCastStart> huton = ModifiableCallout.durationBasedCall("Huton", "Dodge 4x Shuriken");

	// 剛雷

	@NpcCastCallout(0x8534)
	private final ModifiableCallout<AbilityCastStart> unenlightenment = ModifiableCallout.durationBasedCall("Unenlightenment", "Raidwide + Bleed");

	@NpcCastCallout(0x8509)
	private final ModifiableCallout<AbilityCastStart> brazenBalladPurple = ModifiableCallout.durationBasedCall("Brazen Ballad (Purple)", "Avoid Expanding Lines");

	@NpcCastCallout(0x850A)
	private final ModifiableCallout<AbilityCastStart> brazenBalladBlue = ModifiableCallout.durationBasedCall("Brazen Ballad (Blue)", "Under Rock + Lines");

	@HandleEvents
	public void sparksCount(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8503) && !event.getSource().isPc()) {
			sparksCount++;
			sparksCollect.clear();
		}
	}

	@HandleEvents
	public void sparksCollect(EventContext context, BuffApplied event) {
		// E17 が頭割り、E18 が散開
		if (event.buffIdMatches(0xE17, 0xE18)) {
			sparksCollect.add(event);
		}
	}

	private final ModifiableCallout<BuffApplied> flameAndSulphur = new ModifiableCallout<>("Flame and Sulphur: Stacks", "{mechanic}", "{mechanic} ({player1}, {player2})");
	private final ModifiableCallout<BuffApplied> flameAndSulphurUnknown = new ModifiableCallout<>("Flame and Sulphur: Stacks (Unknown Pairs)", "Stacks: {player1}, {player2}");
	private final RepeatSuppressor flameAndSulphurSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> flameAndSulphurSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0xE17, 0xE18) && sparksCount != 2 && flameAndSulphurSupp.check(ba),
			(e1, s) -> {
				s.waitMs(500);
				List<BuffApplied> stacks = sparksCollect.stream().filter(ba -> ba.buffIdMatches(0xE17)).toList();
				if (stacks.size() < 2) {
					return;
				}
				List<XivCombatant> players = new ArrayList<>(List.of(stacks.get(0).getTarget(), stacks.get(1).getTarget()));
				players.sort(Comparator.comparing(XivCombatant::getName));
				StackPartners type = findStackPartners(players.get(0), players.get(1), true);
				s.setParam("player1", players.get(0));
				s.setParam("player2", players.get(1));
				if (type == StackPartners.UNKNOWN) {
					s.updateCall(flameAndSulphurUnknown, e1);
				}
				else {
					s.setParam("mechanic", type.mechanic);
					s.updateCall(flameAndSulphur, e1);
				}
			});

	private final ModifiableCallout<BuffApplied> cloudToGround = new ModifiableCallout<>("Cloud to Ground", "{first} => {second}", "{first} => {second} ({player1}, {player2})");
	private final RepeatSuppressor cloudToGroundSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> cloudToGroundSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0xE17, 0xE18) && sparksCount == 2 && cloudToGroundSupp.check(ba),
			(e1, s) -> {
				s.waitMs(500);
				StackSpread info = stackSpread(sparksCollect, 0xE17, 0xE18);
				if (info != null) {
					s.updateCall(cloudToGround.getModified(e1, info.params()));
				}
			});

	private final ModifiableCallout<AbilityUsedEvent> cloudToGroundFollowup = new ModifiableCallout<>("Cloud to Ground Followup", "{mechanic}");
	private final RepeatSuppressor cloudToGroundFollowupSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void cloudToGroundFollowup(EventContext context, AbilityUsedEvent event) {
		// 8539 が Greater Ball of Fire、853A が Great Ball of Fire
		if (!event.abilityIdMatches(0x8539, 0x853A) || !cloudToGroundFollowupSupp.check(event)) {
			return;
		}
		String mech = takeSecondMechanic();
		if (mech != null) {
			context.accept(cloudToGroundFollowup.getModified(event, Map.of("mechanic", mech)));
		}
	}

	@NpcCastCallout(0x8532)
	private final ModifiableCallout<AbilityCastStart> torchingTorment = ModifiableCallout.durationBasedCall("Torching Torment", "Buster on {event.target}");

	@NpcCastCallout(0x852F)
	private final ModifiableCallout<AbilityCastStart> impurePurgationFirst = ModifiableCallout.durationBasedCall("Impure Purgation (First Hit)", "Protean");

	@NpcCastCallout(value = 0x8553, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> impurePurgationSecond = ModifiableCallout.durationBasedCall("Impure Purgation (Second Hit)", "Move Away");

	@NpcCastCallout(value = 0x854B, onYou = true)
	private final ModifiableCallout<AbilityCastStart> humbleHammer = ModifiableCallout.durationBasedCall("Humble Hammer", "Shrink Lone Orb");

	private final ModifiableCallout<AbilityCastStart> flintlockOnYou = new ModifiableCallout<>("Flintlock (on You)", "Stay Behind Tank");
	private final ModifiableCallout<AbilityCastStart> flintlockTank = new ModifiableCallout<>("Flintlock (Tank)", "Block Laser on {event.target}");
	private final ModifiableCallout<AbilityCastStart> flintlockOther = new ModifiableCallout<>("Flintlock (Other)", "Avoid Laser");

	// レーザーの対象は Humble Hammer の詠唱対象なので、詠唱完了を待ってからコールする
	@AutoFeed
	private final SequentialTrigger<BaseEvent> flintlockSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x854B) && !acs.getSource().isPc(),
			(e1, s) -> {
				s.waitDuration(e1.getInitialDuration());
				if (e1.getTarget().isThePlayer()) {
					s.updateCall(flintlockOnYou, e1);
				}
				else if (state.playerJobMatches(Job::isTank)) {
					s.updateCall(flintlockTank, e1);
				}
				else {
					s.updateCall(flintlockOther, e1);
				}
			});

	@HandleEvents
	public void rousingCollect(EventContext context, BuffApplied event) {
		if (rousingTowerCount != 0) {
			return;
		}
		// E11-E14 が青塔、E0D-E10 が橙塔の1-4番目
		long id = event.getBuff().getId();
		if (id >= 0xE11 && id <= 0xE14) {
			rousingBlue[(int) (id - 0xE11)] = event.getTarget();
		}
		else if (id >= 0xE0D && id <= 0xE10) {
			rousingOrange[(int) (id - 0xE0D)] = event.getTarget();
		}
	}

	private final ModifiableCallout<BaseEvent> rousingBlueTower = new ModifiableCallout<>("Rousing Reincarnation: Blue Tower", "Blue Tower {num}");
	private final ModifiableCallout<BaseEvent> rousingOrangeTower = new ModifiableCallout<>("Rousing Reincarnation: Orange Tower", "Orange Tower {num}");
	private final ModifiableCallout<BaseEvent> rousingTether = new ModifiableCallout<>("Rousing Reincarnation: Tether", "Tether {num}");
	private final ModifiableCallout<BaseEvent> rousingTetherThenBlue = new ModifiableCallout<>("Rousing Reincarnation: Tether then Blue Tower", "Tether {num} => Blue Tower {next}");
	private final ModifiableCallout<BaseEvent> rousingTetherThenOrange = new ModifiableCallout<>("Rousing Reincarnation: Tether then Orange Tower", "Tether {num} => Orange Tower {next}");

	private void towerResponse(EventContext context, BaseEvent event) {
		int index = rousingTowerCount;
		int towerNum = index + 1;
		rousingTowerCount++;
		if (index >= TOWER_SETS) {
			return;
		}
		XivCombatant blue = rousingBlue[index];
		XivCombatant orange = rousingOrange[index];
		if (blue != null && blue.isThePlayer()) {
			context.accept(rousingBlueTower.getModified(event, Map.of("num", towerNum)));
			return;
		}
		if (orange != null && orange.isThePlayer()) {
			context.accept(rousingOrangeTower.getModified(event, Map.of("num", towerNum)));
			return;
		}
		// 次の塔の参照位置は cactbot と同じにしている
		int nextIndex = towerNum + 1;
		if (towerNum == TOWER_SETS || nextIndex >= TOWER_SETS) {
			context.accept(rousingTether.getModified(event, Map.of("num", towerNum)));
			return;
		}
		XivCombatant nextBlue = rousingBlue[nextIndex];
		XivCombatant nextOrange = rousingOrange[nextIndex];
		if (nextBlue != null && nextBlue.isThePlayer()) {
			context.accept(rousingTetherThenBlue.getModified(event, Map.of("num", towerNum, "next", towerNum + 1)));
		}
		else if (nextOrange != null && nextOrange.isThePlayer()) {
			context.accept(rousingTetherThenOrange.getModified(event, Map.of("num", towerNum, "next", towerNum + 1)));
		}
		else {
			context.accept(rousingTether.getModified(event, Map.of("num", towerNum)));
		}
	}

	@HandleEvents
	public void rousingFirstTower(EventContext context, AbilityCastStart event) {
		// Malformed Prayer の詠唱で最初の塔を案内する
		if (event.abilityIdMatches(0x8518) && !event.getSource().isPc()) {
			towerResponse(context, event);
		}
	}

	@HandleEvents
	public void rousingOtherTowers(EventContext context, AbilityUsedEvent event) {
		// 8546 は青塔の爆発。直前の扇範囲ではなく塔の爆発を合図にする
		if (event.abilityIdMatches(0x8546) && !event.getSource().isPc() && event.isFirstTarget()) {
			towerResponse(context, event);
		}
	}

	@NpcCastCallout(0x852B)
	private final ModifiableCallout<AbilityCastStart> fightingSpirits = ModifiableCallout.durationBasedCall("Fighting Spirits", "Knockback");

	private final ModifiableCallout<HeadMarkerEvent> limitCut = new ModifiableCallout<>("Fighting Spirits: Limit Cut", "{num}", 6_000);

	@HandleEvents
	public void limitCut(EventContext context, HeadMarkerEvent event) {
		long id = event.getMarkerId();
		if (id >= LIMIT_CUT_FIRST && id <= LIMIT_CUT_LAST && event.getTarget().isThePlayer()) {
			context.accept(limitCut.getModified(event, Map.of("num", id - LIMIT_CUT_FIRST + 1)));
		}
	}

	private static boolean isMalformedBuff(BuffApplied ba) {
		return ba.buffIdMatches(0xE0D, 0xE0E, 0xE0F, 0xE11, 0xE12, 0xE13);
	}

	@HandleEvents
	public void malformedCollect(EventContext context, BuffApplied event) {
		if (rousingTowerCount != 0 && isMalformedBuff(event)) {
			malformedCollect.add(event);
			if (event.getTarget().isThePlayer()) {
				myMalformedEffects.add(event.getBuff().getId());
			}
		}
	}

	private final ModifiableCallout<BuffApplied> malformedReincarnation = new ModifiableCallout<>("Malformed Reincarnation",
			"{color1} -> {color2} -> {color3} ({mixedType})",
			"{color1} -> {color2} -> {color3} ({mixedType}), first {color1} with {partner}",
			ModifiableCallout.<BuffApplied>expiresIn(10));
	private final RepeatSuppressor malformedSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> malformedReincarnationSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> rousingTowerCount != 0 && isMalformedBuff(ba) && malformedSupp.check(ba),
			(e1, s) -> {
				s.waitMs(500);
				int playerCount = 0;
				int mixedCount = 0;
				Map<Long, String> firstColor = new LinkedHashMap<>();
				Map<Long, XivCombatant> players = new LinkedHashMap<>();
				List<String> myColors = new ArrayList<>();
				for (BuffApplied ba : malformedCollect) {
					String color = ba.buffIdMatches(0xE0D, 0xE0E, 0xE0F) ? "Orange" : "Blue";
					XivCombatant target = ba.getTarget();
					if (target.isThePlayer()) {
						myColors.add(color);
					}
					String lastColor = firstColor.get(target.getId());
					if (lastColor == null) {
						playerCount++;
						firstColor.put(target.getId(), color);
						players.put(target.getId(), target);
					}
					else if (!lastColor.equals(color)) {
						mixedCount++;
					}
				}
				if (myColors.size() < 3) {
					return;
				}
				// 全員が2色を持っていれば全混色、2人だけなら半混色
				String mixedType = playerCount == mixedCount ? "full mixed" : "half mixed";
				Object partner = UNKNOWN;
				for (Map.Entry<Long, String> entry : firstColor.entrySet()) {
					XivCombatant player = players.get(entry.getKey());
					if (!player.isThePlayer() && entry.getValue().equals(myColors.get(0))) {
						partner = player;
						break;
					}
				}
				s.setParam("color1", myColors.get(0));
				s.setParam("color2", myColors.get(1));
				s.setParam("color3", myColors.get(2));
				s.setParam("mixedType", mixedType);
				s.setParam("partner", partner);
				s.updateCall(malformedReincarnation, e1);
			});

	private final ModifiableCallout<BuffRemoved> malformedBlueTower1 = new ModifiableCallout<>("Malformed Reincarnation: Blue Tower 1", "Inside Blue Tower 1", 4_000);
	private final ModifiableCallout<BuffRemoved> malformedOrangeTower1 = new ModifiableCallout<>("Malformed Reincarnation: Orange Tower 1", "Inside Orange Tower 1", 4_000);

	@HandleEvents
	public void malformedFirstTower(EventContext context, BuffRemoved event) {
		// E15 が橙塔設置、E16 が青塔設置
		if (!event.buffIdMatches(0xE15, 0xE16) || !event.getTarget().isThePlayer()) {
			return;
		}
		Long effectId = myMalformedEffects.poll();
		if (effectId == null) {
			return;
		}
		if (effectId == 0xE0D) {
			context.accept(malformedOrangeTower1.getModified(event));
		}
		else if (effectId == 0xE11) {
			context.accept(malformedBlueTower1.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> malformedBlueTower2 = new ModifiableCallout<>("Malformed Reincarnation: Blue Tower 2", "Corner Blue Tower 2", 2_000);
	private final ModifiableCallout<AbilityUsedEvent> malformedOrangeTower2 = new ModifiableCallout<>("Malformed Reincarnation: Orange Tower 2", "Corner Orange Tower 2", 2_000);
	private final ModifiableCallout<AbilityUsedEvent> malformedBlueTower3 = new ModifiableCallout<>("Malformed Reincarnation: Blue Tower 3", "Placed Blue Tower 3", 2_000);
	private final ModifiableCallout<AbilityUsedEvent> malformedOrangeTower3 = new ModifiableCallout<>("Malformed Reincarnation: Orange Tower 3", "Placed Orange Tower 3", 2_000);
	private final RepeatSuppressor malformedTowersSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void malformedOtherTowers(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x8546) || event.getSource().isPc() || myMalformedEffects.isEmpty() || !malformedTowersSupp.check(event)) {
			return;
		}
		long effectId = myMalformedEffects.poll();
		ModifiableCallout<AbilityUsedEvent> call;
		if (effectId == 0xE0E) {
			call = malformedOrangeTower2;
		}
		else if (effectId == 0xE0F) {
			call = malformedOrangeTower3;
		}
		else if (effectId == 0xE12) {
			call = malformedBlueTower2;
		}
		else if (effectId == 0xE13) {
			call = malformedBlueTower3;
		}
		else {
			return;
		}
		context.accept(call.getModified(event));
	}

	@NpcAbilityUsedCallout(value = 0x8541, suppressMs = 30_000)
	private final ModifiableCallout<AbilityUsedEvent> fireSpread = new ModifiableCallout<>("Fire Spread", "Move Away");

	// 木霊

	@NpcCastCallout(0x860C)
	private final ModifiableCallout<AbilityCastStart> kenkiRelease = ModifiableCallout.durationBasedCall("Kenki Release", "Raidwide");

	private static String kasumiFirstText(long raw) {
		int index = (int) (raw - KASUMI_FIRST_VFX);
		// 後半4つが青（内側）、前半4つが赤（外側）
		return MARKER_DIRS[index % 4] + (index >= 4 ? " + In" : " + Out");
	}

	private static String kasumiFollowupText(long raw) {
		int index = (int) (raw - KASUMI_FIRST_VFX);
		return FOLLOWUP_DIRS[index % 4] + (index >= 4 ? " + In" : " + Out");
	}

	private final ModifiableCallout<BuffApplied> tripleKasumi1 = new ModifiableCallout<>("Triple Kasumi-giri: First", "{text}", 10_000);
	private final ModifiableCallout<BuffApplied> tripleKasumi2 = new ModifiableCallout<>("Triple Kasumi-giri: Second", "({text})");
	private final ModifiableCallout<BuffApplied> tripleKasumi3 = new ModifiableCallout<>("Triple Kasumi-giri: All", "{text1} => {text2} => {text3}", 10_000);

	@HandleEvents
	public void tripleKasumi(EventContext context, BuffApplied event) {
		if (!event.buffIdMatches(VFX_BUFF)) {
			return;
		}
		long raw = event.getRawStacks();
		if (raw < KASUMI_FIRST_VFX || raw > KASUMI_LAST_VFX) {
			return;
		}
		tripleKasumiCollect.add(raw);
		switch (tripleKasumiCollect.size()) {
			case 1 -> context.accept(tripleKasumi1.getModified(event, Map.of("text", kasumiFirstText(raw))));
			case 2 -> context.accept(tripleKasumi2.getModified(event, Map.of("text", kasumiFollowupText(raw))));
			case 3 -> context.accept(tripleKasumi3.getModified(event, Map.of(
					"text1", kasumiFirstText(tripleKasumiCollect.get(0)),
					"text2", kasumiFollowupText(tripleKasumiCollect.get(1)),
					"text3", kasumiFollowupText(tripleKasumiCollect.get(2)))));
			default -> {
			}
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> tripleKasumiFollowup = new ModifiableCallout<>("Triple Kasumi-giri: Followup", "{text}");
	private final RepeatSuppressor tripleKasumiFollowupSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void tripleKasumiFollowup(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(TRIPLE_KASUMI_IDS) || event.getSource().isPc() || !tripleKasumiFollowupSupp.check(event)) {
			return;
		}
		// 1つ目は既にコール済みなので飛ばす
		if (tripleKasumiCollect.size() == 3) {
			tripleKasumiCollect.remove(0);
		}
		if (tripleKasumiCollect.isEmpty()) {
			return;
		}
		long raw = tripleKasumiCollect.remove(0);
		context.accept(tripleKasumiFollowup.getModified(event, Map.of("text", kasumiFollowupText(raw))));
	}

	@NpcCastCallout(0x860D)
	private final ModifiableCallout<AbilityCastStart> lateralSlice = ModifiableCallout.durationBasedCall("Lateral Slice", "Buster on {event.target}");

	@NpcCastCallout(0x8600)
	private final ModifiableCallout<AbilityCastStart> scarletAuspice = ModifiableCallout.durationBasedCall("Scarlet Auspice", "Sides + Out => Stay Out");

	@NpcCastCallout(0x8603)
	private final ModifiableCallout<AbilityCastStart> azureAuspice = ModifiableCallout.durationBasedCall("Azure Auspice", "Under => Sides + Out");

	@NpcAbilityUsedCallout(value = 0x8603, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> azureAuspiceFollowup = new ModifiableCallout<>("Azure Auspice Followup", "Sides + Out");

	private final ModifiableCallout<MapEffectEvent> fireLines = new ModifiableCallout<>("Explosion Lines: Safe Spot", "{safe}", 5_000);

	@HandleEvents
	public void fireLines(EventContext context, MapEffectEvent event) {
		// 2C-2F が炎の線。00100020 で光り始める
		long location = event.getLocation();
		if (event.getFlags() != 0x00100020L || location < 0x2C || location > 0x2F) {
			return;
		}
		explosionLineCollect.add(event);
		if (explosionLineCollect.size() != 2) {
			return;
		}
		boolean north = explosionLineCollect.stream().anyMatch(e -> e.getLocation() == 0x2F);
		boolean swOrNe = explosionLineCollect.stream().anyMatch(e -> e.getLocation() == 0x2D);
		ArenaSector safe;
		if (north) {
			safe = swOrNe ? ArenaSector.NORTHEAST : ArenaSector.NORTHWEST;
		}
		else {
			safe = swOrNe ? ArenaSector.SOUTHWEST : ArenaSector.SOUTHEAST;
		}
		context.accept(fireLines.getModified(event, Map.of("safe", safe)));
	}

	@HandleEvents
	public void invocationCollect(EventContext context, BuffApplied event) {
		// E1A が散開、E1B が頭割り
		if (event.buffIdMatches(0xE1A, 0xE1B)) {
			invocationCollect.add(event);
		}
	}

	private final ModifiableCallout<BuffApplied> invocationOfVengeance = new ModifiableCallout<>("Invocation of Vengeance", "{first} => {second}", 5_000);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> invocationOfVengeanceSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0xE1A, 0xE1B) && !invocationCalled,
			(e1, s) -> {
				invocationCalled = true;
				s.waitMs(500);
				// 線や炎のコールと重なるので、頭割り対象者の名前は出さない
				StackSpread info = stackSpread(invocationCollect, 0xE1B, 0xE1A);
				if (info != null) {
					s.updateCall(invocationOfVengeance.getModified(e1, info.params()));
				}
			});

	private final ModifiableCallout<AbilityUsedEvent> invocationOfVengeanceFollowup = new ModifiableCallout<>("Invocation of Vengeance Followup", "{mechanic}");
	private final RepeatSuppressor invocationFollowupSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void invocationFollowup(EventContext context, AbilityUsedEvent event) {
		// 8608 が Vengeful Flame、8609 が Vengeful Pyre
		if (!event.abilityIdMatches(0x8608, 0x8609) || !invocationFollowupSupp.check(event)) {
			return;
		}
		String mech = takeSecondMechanic();
		if (mech != null) {
			context.accept(invocationOfVengeanceFollowup.getModified(event, Map.of("mechanic", mech)));
		}
	}

	private final RepeatSuppressor iaigiriCleanupSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void iaigiriCleanup(EventContext context, AbilityCastStart event) {
		// 85C2 が Fleeting Iai-giri、85C8 が分身の Double Iai-giri
		if (event.abilityIdMatches(0x85C2, 0x85C8) && iaigiriCleanupSupp.check(event)) {
			clearIaigiri();
		}
	}

	@HandleEvents
	public void iaigiriTetherCollect(EventContext context, TetherEvent event) {
		if (!event.tetherIdMatches(0x11)) {
			return;
		}
		XivCombatant shadow = event.getTargetMatching(cbt -> !cbt.isPc());
		XivCombatant player = event.getTargetMatching(XivCombatant::isPc);
		if (shadow == null || player == null) {
			return;
		}
		IaiTether tether = new IaiTether(shadow, player);
		iaigiriTether.add(tether);
		if (player.isThePlayer()) {
			myIaigiriTether = tether;
		}
	}

	@HandleEvents
	public void iaigiriPurpleCollect(EventContext context, BuffApplied event) {
		if (isShadowVfx(event)) {
			iaigiriPurple.add(event);
		}
	}

	@HandleEvents
	public void accursedEdgeCollect(EventContext context, AbilityUsedEvent event) {
		// 複数回当たることもあり得るので最後のものを使う
		if (event.abilityIdMatches(0x8607) && event.getTarget().isThePlayer()) {
			myAccursedEdge = posOf(event.getTarget());
		}
	}

	private final ModifiableCallout<TetherEvent> fleetingIaigiriOnYou = new ModifiableCallout<>("Fleeting Iai-giri (on You)", "{dir} Tether on You", 7_000);
	private final ModifiableCallout<TetherEvent> fleetingIaigiriOther = new ModifiableCallout<>("Fleeting Iai-giri (on Other)", "{dir} Tether on {player}", 7_000);
	private final ModifiableCallout<TetherEvent> doubleShadowTether = new ModifiableCallout<>("Double Shadow Kasumi-giri: Tether", "{dir} Tether ({partners})", 4_000);
	private final ModifiableCallout<TetherEvent> doubleShadowUnmarked = new ModifiableCallout<>("Double Shadow Kasumi-giri: Unmarked", "Unmarked ({partners})", 4_000);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> iaigiriTetherSq = SqtTemplates.sq(5_000,
			TetherEvent.class, te -> te.tetherIdMatches(0x11),
			(e1, s) -> {
				s.waitMs(500);
				if (iaigiriTether.size() == 1 && iaigiriPurple.size() == 1) {
					IaiTether tether = iaigiriTether.get(0);
					s.setParam("dir", MARKER_DIRS[shadowIndex(iaigiriPurple.get(0))]);
					if (tether.player().isThePlayer()) {
						s.updateCall(fleetingIaigiriOnYou, e1);
					}
					else {
						s.setParam("player", tether.player());
						s.updateCall(fleetingIaigiriOther, e1);
					}
				}
				else if (!seenSoldiersOfDeath && iaigiriTether.size() == 2 && iaigiriPurple.size() == 2) {
					StackPartners type = findStackPartners(iaigiriTether.get(0).player(), iaigiriTether.get(1).player(), true);
					s.setParam("partners", type.together);
					IaiTether mine = myIaigiriTether;
					if (mine == null) {
						s.updateCall(doubleShadowUnmarked, e1);
						return;
					}
					BuffApplied marker1 = iaigiriPurple.get(0);
					BuffApplied myMarker = marker1.getTarget().getId() == mine.shadow().getId() ? marker1 : iaigiriPurple.get(1);
					s.setParam("dir", MARKER_DIRS[shadowIndex(myMarker)]);
					s.updateCall(doubleShadowTether, e1);
				}
			});

	private final ModifiableCallout<BuffApplied> oniClawNorthSouthCall = new ModifiableCallout<>("Oni Claw: North/South", "North/South");
	private final ModifiableCallout<BuffApplied> oniClawEastWestCall = new ModifiableCallout<>("Oni Claw: East/West", "East/West");
	private final RepeatSuppressor oniClawSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> oniClawSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(ADD_VFX_BUFF) && ba.getRawStacks() == ONI_CLAW_VFX && oniClawSupp.check(ba),
			(e1, s) -> {
				s.waitThenRefreshCombatants(100);
				Position pos = posOf(e1.getTarget());
				if (pos == null) {
					return;
				}
				// 東西に出た手なら南北が安置
				int dir = (int) Math.round(2 - 2 * Math.atan2(pos.x() - MOKO_CENTER_X, pos.y() - MOKO_CENTER_Y) / Math.PI) % 4;
				boolean northSouth = dir == 1 || dir == 3;
				oniClawNorthSouth = northSouth;
				s.updateCall(northSouth ? oniClawNorthSouthCall : oniClawEastWestCall, e1);
			});

	private final ModifiableCallout<AbilityCastStart> baitNear = ModifiableCallout.durationBasedCall("Near Edge: Bait", "Bait Near (Tether Far)");
	private final ModifiableCallout<AbilityCastStart> baitFar = ModifiableCallout.durationBasedCall("Far Edge: Bait", "Bait Far (Tether Near)");
	private final ModifiableCallout<AbilityCastStart> tetherNear = ModifiableCallout.durationBasedCall("Far Edge: Tether", "Tether Near (Bait Far)");
	private final ModifiableCallout<AbilityCastStart> tetherFar = ModifiableCallout.durationBasedCall("Near Edge: Tether", "Tether Far (Bait Near)");

	@HandleEvents
	public void nearFarEdge(EventContext context, AbilityCastStart event) {
		// 85D8 が Far Edge、85D9 が Near Edge
		if (!event.abilityIdMatches(0x85D8, 0x85D9) || event.getSource().isPc()) {
			return;
		}
		boolean farEdge = event.abilityIdMatches(0x85D8);
		ModifiableCallout<AbilityCastStart> call;
		if (myIaigiriTether == null) {
			call = farEdge ? baitFar : baitNear;
		}
		else {
			call = farEdge ? tetherNear : tetherFar;
		}
		context.accept(call.getModified(event));
	}

	private final ModifiableCallout<BuffApplied> doubleShadowSecondMark = new ModifiableCallout<>("Double Shadow Kasumi-giri: Second Mark", "(then {dir})", 5_500);
	private final RepeatSuppressor secondMarkSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	private static @Nullable Long markerCountAt(List<BuffApplied> purples, int index) {
		return purples.size() > index ? purples.get(index).getRawStacks() : null;
	}

	private boolean secondMarkCondition(BuffApplied ba) {
		if (seenSoldiersOfDeath) {
			return false;
		}
		List<BuffApplied> purples = purplesWith(ba);
		// 最初の組は線と一緒にコール済み
		if (purples.size() <= 2) {
			return false;
		}
		// 2組目の2つが同じなら、位置に関係なく全員に同じ内容を伝えられる
		Long third = markerCountAt(purples, 2);
		Long fourth = markerCountAt(purples, 3);
		if (third != null && third.equals(fourth)) {
			return true;
		}
		IaiTether mine = myIaigiriTether;
		return mine != null && mine.shadow().getId() == ba.getTarget().getId();
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> doubleShadowSecondMarkSq = SqtTemplates.sq(10_000,
			BuffApplied.class, ba -> isShadowVfx(ba) && secondMarkCondition(ba) && secondMarkSupp.check(ba),
			(e1, s) -> {
				// 近い/遠いのコールの方が重要なので被らないように遅らせる
				s.waitMs(3_000);
				Long third = markerCountAt(iaigiriPurple, 2);
				Long fourth = markerCountAt(iaigiriPurple, 3);
				long raw = third != null && third.equals(fourth) && !seenSoldiersOfDeath ? third : e1.getRawStacks();
				s.setParam("dir", FOLLOWUP_DIRS[(int) (raw - SHADOW_FIRST_VFX)].toLowerCase());
				s.updateCall(doubleShadowSecondMark, e1);
			});

	private final ModifiableCallout<AbilityUsedEvent> shadowBackTether = new ModifiableCallout<>("Shadow Kasumi-giri: Back Tether", "Back", 2_000);
	private final RepeatSuppressor shadowBackTetherSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void shadowBackTether(EventContext context, AbilityUsedEvent event) {
		// 分身が飛んできた後に動き忘れないためのリマインド
		IaiTether mine = myIaigiriTether;
		if (event.abilityIdMatches(0x85C9) && mine != null && mine.shadow().getId() == event.getSource().getId() && shadowBackTetherSupp.check(event)) {
			context.accept(shadowBackTether.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> shadowKasumiFollowup = new ModifiableCallout<>("Shadow Kasumi-giri: Followup", "{dir}");
	private final RepeatSuppressor shadowKasumiFollowupSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	// 自分に線が付いている分身か、線が無ければ自分と同じ側にいる分身だけを対象にする
	private boolean isMyShadow(AbilityUsedEvent event) {
		IaiTether mine = myIaigiriTether;
		if (mine != null) {
			return mine.shadow().getId() == event.getSource().getId();
		}
		Boolean northSouth = oniClawNorthSouth;
		Position myPos = myAccursedEdge;
		Position shadowPos = posOf(event.getSource());
		if (northSouth == null || myPos == null || shadowPos == null) {
			return false;
		}
		if (northSouth) {
			return !(myPos.y() < MOKO_CENTER_Y && shadowPos.y() > MOKO_CENTER_Y || myPos.y() > MOKO_CENTER_Y && shadowPos.y() < MOKO_CENTER_Y);
		}
		return !(myPos.x() < MOKO_CENTER_X && shadowPos.x() > MOKO_CENTER_X || myPos.x() > MOKO_CENTER_X && shadowPos.x() < MOKO_CENTER_X);
	}

	@HandleEvents
	public void shadowKasumiFollowup(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x85F9) || !isMyShadow(event) || !shadowKasumiFollowupSupp.check(event)) {
			return;
		}
		BuffApplied marker = lastPurpleOn(event.getSource());
		if (marker != null) {
			context.accept(shadowKasumiFollowup.getModified(event, Map.of("dir", FOLLOWUP_DIRS[shadowIndex(marker)])));
		}
	}

	@HandleEvents
	public void soldiersOfDeath(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8593) && !event.getSource().isPc()) {
			seenSoldiersOfDeath = true;
		}
	}

	private final ModifiableCallout<BuffApplied> soldiersBlueAdd = new ModifiableCallout<>("Soldiers of Death: Blue Add Safe Spot", "{safe}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> soldiersBlueAddSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(ADD_VFX_BUFF) && ba.getRawStacks() == BLUE_SOLDIER_VFX,
			(e1, s) -> {
				s.waitThenRefreshCombatants(100);
				Position pos = posOf(e1.getTarget());
				if (pos == null) {
					return;
				}
				double x = pos.x() - MOKO_CENTER_X;
				double y = pos.y() - MOKO_CENTER_Y;
				// 外周から辺に沿った位置にいるので、辺沿いの方向と逆に逃げる。東西なら遠く、南北なら近くまで撃ってくる
				boolean longFromEastWest = Math.abs(x) > Math.abs(y);
				boolean east = x > 0;
				boolean north = y < 0;
				ArenaSector safe;
				if (longFromEastWest) {
					if (north) {
						safe = east ? ArenaSector.SOUTHEAST : ArenaSector.SOUTHWEST;
					}
					else {
						safe = east ? ArenaSector.NORTHEAST : ArenaSector.NORTHWEST;
					}
				}
				else if (east) {
					safe = north ? ArenaSector.SOUTHWEST : ArenaSector.NORTHWEST;
				}
				else {
					safe = north ? ArenaSector.SOUTHEAST : ArenaSector.NORTHEAST;
				}
				s.setParam("safe", safe);
				s.updateCall(soldiersBlueAdd, e1);
			});

	private final ModifiableCallout<BuffApplied> soldiersShadowTether = new ModifiableCallout<>("Soldiers of Death: Shadow Kasumi-giri Tether", "{dir} Tether ({partners})", 5_000);
	private final RepeatSuppressor soldiersShadowTetherSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> soldiersShadowTetherSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> isShadowVfx(ba) && purplesWith(ba).size() > 4 && soldiersShadowTetherSupp.check(ba),
			(e1, s) -> {
				if (iaigiriPurple.size() < SOLDIERS_MARKERS) {
					s.waitMs(500);
				}
				IaiTether mine = myIaigiriTether;
				if (mine == null) {
					return;
				}
				BuffApplied marker = lastPurpleOn(mine.shadow());
				if (marker == null) {
					return;
				}
				int dirIndex = shadowIndex(marker);
				// 左右のマーカーのときだけ、同じマーカーの相方と入れ替わる可能性がある
				if (dirIndex != 1 && dirIndex != 3) {
					return;
				}
				List<BuffApplied> matching = iaigiriPurple.stream()
						.filter(ba -> ba.getRawStacks() == marker.getRawStacks() && ba.getTarget().getId() != marker.getTarget().getId())
						.toList();
				if (matching.size() != 1) {
					return;
				}
				long partnerShadowId = matching.get(0).getTarget().getId();
				IaiTether partnerTether = iaigiriTether.stream()
						.filter(t -> t.shadow().getId() == partnerShadowId)
						.findFirst()
						.orElse(null);
				if (partnerTether == null) {
					return;
				}
				// ここではロール同士なら常に安全なので、近接優先の設定は使わない
				StackPartners type = findStackPartners(mine.player(), partnerTether.player(), false);
				s.setParam("dir", MARKER_DIRS[dirIndex]);
				s.setParam("partners", type.together);
				s.updateCall(soldiersShadowTether, e1);
			});

}
