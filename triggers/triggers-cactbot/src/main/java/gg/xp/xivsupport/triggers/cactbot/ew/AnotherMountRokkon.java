package gg.xp.xivsupport.triggers.cactbot.ew;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.Event;
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
import gg.xp.xivsupport.events.state.RawXivCombatantInfo;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/dungeon/another_mount_rokkon.ts をもとに移植
@CalloutRepo(name = "Another Mount Rokkon", duty = KnownDuty.AnotherMountRokkon)
public class AnotherMountRokkon extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long RAIRIN_NAME_ID = 12430;
	private static final int RAIRIN_COUNT = 4;
	// 4本目の突進が斜めか北寄りかを分ける閾値
	private static final double RAIRIN_DIAGONAL_X = 18;
	private static final int THRALL_COUNT = 4;
	private static final int REISHO_PULSES = 8;
	private static final int TOWER_SETS = 4;
	private static final int MALFORMED_COLORS = 3;
	private static final int SOLDIERS_MARKER_COUNT = 8;
	private static final double MOKO_CENTER_X = -200;
	private static final double MOKO_CENTER_Y = 0;

	private static final long WAILING_SPREAD = 0xDEB;
	private static final long WAILING_STACK = 0xDEC;
	private static final long SPARKS_STACK = 0xE17;
	private static final long SPARKS_SPREAD = 0xE18;
	private static final long VENGEFUL_SPREAD = 0xE1A;
	private static final long VENGEFUL_STACK = 0xE1B;
	private static final long KASUMI_VFX = 0xB9A;
	private static final long ADD_VFX = 0x808;

	private static final long[] ROUSING_ORANGE = {0xE0D, 0xE0E, 0xE0F, 0xE10};
	private static final long[] ROUSING_BLUE = {0xE11, 0xE12, 0xE13, 0xE14};
	private static final long[] MALFORMED_IDS = {0xE0D, 0xE0E, 0xE0F, 0xE11, 0xE12, 0xE13};

	private static final long[] TRIPLE_KASUMI_ABILITIES = {
			0x85B0, 0x85B1, 0x85B2, 0x85B3, 0x85B4, 0x85B5, 0x85B6, 0x85B7,
			0x85BA, 0x85BB, 0x85BC, 0x85BD, 0x85BE, 0x85BF, 0x85C0, 0x85C1,
	};

	// 獅子王戦のフィールド中心は (0, -100)
	private static final ArenaPos thrallArena = new ArenaPos(0, -100, 5, 5);

	private XivState state;

	public AnotherMountRokkon(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AnotherMountRokkon);
	}

	private enum StackType {
		MELEE("Melees Stack", "melees together"),
		ROLE("Role Stacks", "roles together"),
		PARTNER("Partner Stacks", "partners together"),
		UNKNOWN("Stack", "unknown"),
		SPREAD("Spread", "spread");

		private final String label;
		private final String together;

		StackType(String label, String together) {
			this.label = label;
			this.together = together;
		}
	}

	private record StackSpread(boolean stackFirst, StackType type, XivCombatant player1, XivCombatant player2) {
	}

	private enum Kasumi {
		BACK_RED(0x24C, "Back + Out", "Stay + Out"),
		LEFT_RED(0x24D, "Left + Out", "Left + Out"),
		FRONT_RED(0x24E, "Front + Out", "Through + Out"),
		RIGHT_RED(0x24F, "Right + Out", "Right + Out"),
		BACK_BLUE(0x250, "Back + In", "Stay + In"),
		LEFT_BLUE(0x251, "Left + In", "Left + In"),
		FRONT_BLUE(0x252, "Front + In", "Through + In"),
		RIGHT_BLUE(0x253, "Right + In", "Right + In");

		private final long count;
		private final String first;
		private final String followup;

		Kasumi(long count, String first, String followup) {
			this.count = count;
			this.first = first;
			this.followup = followup;
		}

		static @Nullable Kasumi of(long count) {
			for (Kasumi k : values()) {
				if (k.count == count) {
					return k;
				}
			}
			return null;
		}
	}

	private enum ShadowDir {
		BACK(0x248, "Back", "stay", "Stay"),
		LEFT(0x249, "Left", "left", "Left"),
		FRONT(0x24A, "Front", "through", "Through"),
		RIGHT(0x24B, "Right", "right", "Right");

		private final long count;
		private final String tether;
		private final String then;
		private final String move;

		ShadowDir(long count, String tether, String then, String move) {
			this.count = count;
			this.tether = tether;
			this.then = then;
			this.move = move;
		}

		static @Nullable ShadowDir of(long count) {
			for (ShadowDir d : values()) {
				if (d.count == count) {
					return d;
				}
			}
			return null;
		}
	}

	private enum OniClaw {
		NORTH_SOUTH,
		EAST_WEST
	}

	private boolean seenShishuYukiAuto;
	private int smokeaterCount;
	private final List<Position> rairinCollect = new ArrayList<>();
	private final List<BuffApplied> wailingCollect = new ArrayList<>();
	private int wailCount;
	private boolean wailing1Called;
	private final List<AbilityCastStart> devilishThrallCollect = new ArrayList<>();
	private @Nullable Boolean vortexSecondIsIn;
	private @Nullable StackType stackSpreadFirst;
	private @Nullable StackType stackSpreadSecond;
	private int reishoCount;
	private final List<HeadMarkerEvent> ghostHeadmarkers = new ArrayList<>();
	private boolean ghostTower;
	private final List<BuffApplied> sparksCollect = new ArrayList<>();
	private int sparksCount;
	private final XivCombatant[] rousingBlue = new XivCombatant[TOWER_SETS];
	private final XivCombatant[] rousingOrange = new XivCombatant[TOWER_SETS];
	private int rousingTowerCount;
	private final List<BuffApplied> malformedCollect = new ArrayList<>();
	private final List<Long> myMalformedEffects = new ArrayList<>();
	private final List<Kasumi> tripleKasumiCollect = new ArrayList<>();
	private final List<MapEffectEvent> explosionLineCollect = new ArrayList<>();
	private final List<BuffApplied> invocationCollect = new ArrayList<>();
	private boolean invocationCalled;
	private final List<TetherEvent> iaigiriTether = new ArrayList<>();
	private final List<BuffApplied> iaigiriPurple = new ArrayList<>();
	private @Nullable AbilityUsedEvent myAccursedEdge;
	private @Nullable TetherEvent myIaigiriTether;
	private @Nullable OniClaw oniClaw;
	private boolean seenSoldiersOfDeath;

	@HandleEvents(order = -10)
	public void reset(EventContext context, PullStartedEvent event) {
		seenShishuYukiAuto = false;
		smokeaterCount = 0;
		rairinCollect.clear();
		wailingCollect.clear();
		wailCount = 0;
		wailing1Called = false;
		devilishThrallCollect.clear();
		vortexSecondIsIn = null;
		stackSpreadFirst = null;
		stackSpreadSecond = null;
		reishoCount = 0;
		ghostHeadmarkers.clear();
		ghostTower = false;
		sparksCollect.clear();
		sparksCount = 0;
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
		oniClaw = null;
	}

	// ---- 共通処理 ----

	private static int countJob(Job job1, Job job2, Predicate<Job> func) {
		return (func.test(job1) ? 1 : 0) + (func.test(job2) ? 1 : 0);
	}

	private static boolean isMeleeOrTank(Job job) {
		return job.isMeleeDps() || job.isTank();
	}

	// 4人PTで2人に頭割りが付いたとき、どの組み合わせで頭割りするかを決める
	private StackType findStackPartners(@Nullable XivCombatant stack1, @Nullable XivCombatant stack2, boolean preferMelee) {
		if (stack1 == null || stack2 == null) {
			return StackType.UNKNOWN;
		}
		List<XivPlayerCharacter> party = state.getPartyList();
		if (party.size() != 4) {
			return StackType.UNKNOWN;
		}
		Job stackJob1 = null;
		Job stackJob2 = null;
		List<Job> unmarked = new ArrayList<>();
		for (XivPlayerCharacter member : party) {
			if (member.getId() == stack1.getId()) {
				stackJob1 = member.getJob();
			}
			else if (member.getId() == stack2.getId()) {
				stackJob2 = member.getJob();
			}
			else {
				unmarked.add(member.getJob());
			}
		}
		if (stackJob1 == null || stackJob2 == null || unmarked.size() != 2 || unmarked.contains(null)) {
			return StackType.UNKNOWN;
		}
		Job unmarkedJob1 = unmarked.get(0);
		Job unmarkedJob2 = unmarked.get(1);

		if (preferMelee && countJob(stackJob1, stackJob2, AnotherMountRokkon::isMeleeOrTank) == 1
				&& countJob(unmarkedJob1, unmarkedJob2, AnotherMountRokkon::isMeleeOrTank) == 1) {
			return StackType.MELEE;
		}
		int stackSupport = countJob(stackJob1, stackJob2, Job::isSupport);
		int unmarkedSupport = countJob(unmarkedJob1, unmarkedJob2, Job::isSupport);
		if (stackSupport == 1 && unmarkedSupport == 1) {
			return StackType.ROLE;
		}
		if (stackSupport == 2 && unmarkedSupport == 0 || stackSupport == 0 && unmarkedSupport == 2) {
			return StackType.PARTNER;
		}
		return StackType.UNKNOWN;
	}

	private @Nullable StackSpread computeStackSpread(List<BuffApplied> collect, long stackId, long spreadId) {
		List<BuffApplied> stacks = collect.stream().filter(ba -> ba.buffIdMatches(stackId)).toList();
		BuffApplied spread = collect.stream().filter(ba -> ba.buffIdMatches(spreadId)).findFirst().orElse(null);
		if (stacks.size() < 2 || spread == null) {
			return null;
		}
		BuffApplied stack1 = stacks.get(0);
		BuffApplied stack2 = stacks.get(1);
		boolean stackFirst = stack1.getInitialDuration().compareTo(spread.getInitialDuration()) < 0;
		StackType type = findStackPartners(stack1.getTarget(), stack2.getTarget(), true);
		List<XivCombatant> players = List.of(stack1.getTarget(), stack2.getTarget()).stream()
				.sorted(Comparator.comparing(XivCombatant::getName))
				.toList();
		stackSpreadFirst = stackFirst ? type : StackType.SPREAD;
		stackSpreadSecond = stackFirst ? StackType.SPREAD : type;
		return new StackSpread(stackFirst, type, players.get(0), players.get(1));
	}

	private static String firstLabel(StackSpread ss) {
		return ss.stackFirst() ? ss.type().label : StackType.SPREAD.label;
	}

	private static String secondLabel(StackSpread ss) {
		return ss.stackFirst() ? StackType.SPREAD.label : ss.type().label;
	}

	private @Nullable StackType consumeSecondMechanic() {
		StackType mech = stackSpreadSecond;
		stackSpreadFirst = null;
		stackSpreadSecond = null;
		return mech;
	}

	private static Position posOf(XivState state, XivCombatant cbt) {
		return state.getLatestCombatantData(cbt).getPos();
	}

	private static @Nullable XivCombatant tetherPlayer(TetherEvent tether) {
		return tether.getTargetMatching(XivCombatant::isPc);
	}

	private static long tetherShadowId(TetherEvent tether) {
		XivCombatant shadow = tether.getTargetMatching(cbt -> !cbt.isPc());
		return shadow == null ? -1 : shadow.getId();
	}

	private static boolean isMokoVfx(BuffApplied ba) {
		return ba.buffIdMatches(KASUMI_VFX) && Kasumi.of(ba.getRawStacks()) != null;
	}

	private static boolean isShadowVfx(BuffApplied ba) {
		return ba.buffIdMatches(KASUMI_VFX) && ShadowDir.of(ba.getRawStacks()) != null;
	}

	// ---- 1体目の道中 ----

	@NpcCastCallout(0x8656)
	private final ModifiableCallout<AbilityCastStart> disciplesOfLevin = ModifiableCallout.durationBasedCall("Disciples of Levin", "Out");

	private final ModifiableCallout<AbilityCastStart> barrelingSmashOnYou = ModifiableCallout.durationBasedCall("Barreling Smash (On You)", "Charge on You");
	private final ModifiableCallout<AbilityCastStart> barrelingSmash = ModifiableCallout.durationBasedCall("Barreling Smash", "Charge on {event.target}");

	@HandleEvents
	public void barrelingSmashCast(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8653)) {
			context.accept((event.getTarget().isThePlayer() ? barrelingSmashOnYou : barrelingSmash).getModified(event));
		}
	}

	@NpcCastCallout(0x8654)
	private final ModifiableCallout<AbilityCastStart> howl = ModifiableCallout.durationBasedCall("Howl", "Raidwide");

	@NpcCastCallout(0x8655)
	private final ModifiableCallout<AbilityCastStart> masterOfLevin = ModifiableCallout.durationBasedCall("Master of Levin", "Get Under");

	@NpcCastCallout(0x865A)
	private final ModifiableCallout<AbilityCastStart> scytheTail = ModifiableCallout.durationBasedCall("Scythe Tail", "Out");

	@NpcCastCallout(0x8658)
	private final ModifiableCallout<AbilityCastStart> twister = ModifiableCallout.durationBasedCall("Twister", "Stack on {event.target}");

	@NpcCastCallout(0x8659)
	private final ModifiableCallout<AbilityCastStart> crosswind = ModifiableCallout.durationBasedCall("Crosswind", "Knockback");

	// 釣られる前は南を向いているので、左右ではなく東西で伝える
	private final ModifiableCallout<AbilityCastStart> rightSwipeEast = ModifiableCallout.durationBasedCall("Right Swipe (Before Pull)", "East");
	private final ModifiableCallout<AbilityCastStart> rightSwipe = ModifiableCallout.durationBasedCall("Right Swipe", "Left");
	private final ModifiableCallout<AbilityCastStart> leftSwipeWest = ModifiableCallout.durationBasedCall("Left Swipe (Before Pull)", "West");
	private final ModifiableCallout<AbilityCastStart> leftSwipe = ModifiableCallout.durationBasedCall("Left Swipe", "Right");

	@HandleEvents
	public void shishuYukiAuto(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x7A58)) {
			seenShishuYukiAuto = true;
		}
	}

	@HandleEvents
	public void shishuYukiSwipe(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8685)) {
			context.accept((seenShishuYukiAuto ? rightSwipe : rightSwipeEast).getModified(event));
		}
		else if (event.abilityIdMatches(0x8686)) {
			context.accept((seenShishuYukiAuto ? leftSwipe : leftSwipeWest).getModified(event));
		}
	}

	// ---- 獅子王 ----

	@NpcCastCallout(0x841A)
	private final ModifiableCallout<AbilityCastStart> enkyo = ModifiableCallout.durationBasedCall("Enkyo", "Raidwide");

	private final ModifiableCallout<AbilityUsedEvent> smokeater = new ModifiableCallout<>("Smokeater (Count)", "{count}");

	@HandleEvents
	public void onSmokeater(EventContext context, AbilityUsedEvent event) {
		if (!event.isFirstTarget()) {
			return;
		}
		if (event.abilityIdMatches(0x83F9)) {
			smokeaterCount = 1;
		}
		else if (event.abilityIdMatches(0x83FA)) {
			smokeaterCount++;
		}
		else {
			return;
		}
		context.accept(smokeater.getModified(event, Map.of("count", Math.min(smokeaterCount, 3))));
	}

	@NpcCastCallout(0x841B)
	private final ModifiableCallout<AbilityCastStart> splittingCry = ModifiableCallout.durationBasedCall("Splitting Cry", "Buster on {event.target}");

	@NpcAbilityUsedCallout(value = 0x841B, suppressMs = 5_000)
	private final ModifiableCallout<AbilityUsedEvent> splitter = new ModifiableCallout<>("Splitter", "Front/Sides");

	private final ModifiableCallout<RawAddCombatantEvent> noblePursuit = new ModifiableCallout<>("Noble Pursuit", "{safe}");

	@HandleEvents
	public void rairinAdded(EventContext context, RawAddCombatantEvent event) {
		RawXivCombatantInfo info = event.getFullInfo();
		long nameId = info != null ? info.getBnpcNameId() : event.getEntity().getbNpcNameId();
		if (nameId != RAIRIN_NAME_ID) {
			return;
		}
		Position pos = info != null ? info.getPos() : event.getEntity().getPos();
		rairinCollect.add(pos);
		if (rairinCollect.size() != RAIRIN_COUNT) {
			return;
		}
		Position threePos = rairinCollect.get(2);
		Position fourPos = rairinCollect.get(3);
		if (threePos == null || fourPos == null) {
			return;
		}
		// 3本目の突進とは反対側、4本目が斜めなら南、そうでなければ北に避ける
		boolean isThreeEast = threePos.getX() > 0;
		boolean isFourDiagonal = Math.abs(fourPos.getX()) < RAIRIN_DIAGONAL_X;
		ArenaSector safe;
		if (isFourDiagonal) {
			safe = isThreeEast ? ArenaSector.SOUTHWEST : ArenaSector.SOUTHEAST;
		}
		else {
			safe = isThreeEast ? ArenaSector.NORTHWEST : ArenaSector.NORTHEAST;
		}
		context.accept(noblePursuit.getModified(event, Map.of("safe", safe)));
	}

	@HandleEvents(order = -1)
	public void onWailing(EventContext context, Event event) {
		if (event instanceof AbilityCastStart acs && acs.abilityIdMatches(0x8417)) {
			wailCount++;
			wailingCollect.clear();
		}
		else if (event instanceof BuffApplied ba && ba.buffIdMatches(WAILING_SPREAD, WAILING_STACK)) {
			wailingCollect.add(ba);
		}
	}

	private final ModifiableCallout<BuffApplied> unnaturalWailing = new ModifiableCallout<>("Unnatural Wailing", "{first} => {second}");
	private final ModifiableCallout<BuffApplied> unnaturalWailingStacks = new ModifiableCallout<>("Unnatural Wailing (Stack Targets)", "Stacks: {player1}, {player2}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> unnaturalWailingSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(WAILING_SPREAD, WAILING_STACK) && wailCount == 1 && !wailing1Called,
			(e1, s) -> {
				wailing1Called = true;
				s.waitMs(500);
				StackSpread ss = computeStackSpread(wailingCollect, WAILING_STACK, WAILING_SPREAD);
				if (ss == null) {
					return;
				}
				s.setParam("first", firstLabel(ss));
				s.setParam("second", secondLabel(ss));
				s.setParam("player1", ss.player1());
				s.setParam("player2", ss.player2());
				s.call(unnaturalWailing, e1);
				s.call(unnaturalWailingStacks, e1);
			});

	private final ModifiableCallout<AbilityCastStart> vortexOutFirst = ModifiableCallout.durationBasedCall("Eye of the Thunder Vortex", "Out + {first} => In + {second}");
	private final ModifiableCallout<AbilityCastStart> vortexInFirst = ModifiableCallout.durationBasedCall("Vortex of the Thunder Eye", "In + {first} => Out + {second}");
	private final ModifiableCallout<AbilityCastStart> vortexStacks = new ModifiableCallout<>("Thunder Vortex (Stack Targets)", "Stacks: {player1}, {player2}");

	@HandleEvents
	public void vortexCast(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x8413, 0x8415)) {
			return;
		}
		StackSpread ss = computeStackSpread(wailingCollect, WAILING_STACK, WAILING_SPREAD);
		if (ss == null) {
			return;
		}
		boolean inFirst = event.abilityIdMatches(0x8415);
		vortexSecondIsIn = !inFirst;
		ModifiableCallout<AbilityCastStart> call = inFirst ? vortexInFirst : vortexOutFirst;
		context.accept(call.getModified(event, Map.of("first", firstLabel(ss), "second", secondLabel(ss))));
		context.accept(vortexStacks.getModified(event, Map.of("player1", ss.player1(), "player2", ss.player2())));
	}

	private final ModifiableCallout<AbilityUsedEvent> vortexFollowupIn = new ModifiableCallout<>("Thunder Vortex Followup (In)", "In + {mechanic}");
	private final ModifiableCallout<AbilityUsedEvent> vortexFollowupOut = new ModifiableCallout<>("Thunder Vortex Followup (Out)", "Out + {mechanic}");
	private final RepeatSuppressor vortexFollowupSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void vortexFollowup(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x8418, 0x8419) || wailCount == 1 || !vortexFollowupSupp.check(event)) {
			return;
		}
		StackType mech = consumeSecondMechanic();
		Boolean inOut = vortexSecondIsIn;
		if (mech == null || inOut == null) {
			return;
		}
		vortexSecondIsIn = null;
		context.accept((inOut ? vortexFollowupIn : vortexFollowupOut).getModified(event, Map.of("mechanic", mech.label)));
	}

	@NpcCastCallout(0x8412)
	private final ModifiableCallout<AbilityCastStart> thunderVortex = ModifiableCallout.durationBasedCall("Thunder Vortex", "Get Under");

	@HandleEvents(order = -1)
	public void onDevilishThrallCast(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x840B, 0x840C)) {
			devilishThrallCollect.add(event);
		}
	}

	private final ModifiableCallout<AbilityCastStart> devilishThrallDiamond = new ModifiableCallout<>("Devilish Thrall (Diamond)", "{safe} Diamond + {mechanic}", 6_000);
	private final ModifiableCallout<AbilityCastStart> devilishThrallSquare = new ModifiableCallout<>("Devilish Thrall (Square)", "{safe} Square + {mechanic}", 6_000);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> devilishThrallSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x840B, 0x840C),
			(e1, s) -> {
				s.waitMs(500);
				List<AbilityCastStart> casts = new ArrayList<>(devilishThrallCollect);
				devilishThrallCollect.clear();
				if (casts.size() != THRALL_COUNT) {
					return;
				}
				// 内向きの2体は位置と向きが正反対なので、それ以外の外向き2体の間が安置
				List<ArenaSector> outward = new ArrayList<>();
				for (AbilityCastStart cast : casts) {
					XivCombatant cbt = state.getLatestCombatantData(cast.getSource());
					ArenaSector dir = thrallArena.forCombatant(cbt);
					ArenaSector heading = ArenaPos.combatantFacing(cbt);
					if (dir == ArenaSector.UNKNOWN || dir == ArenaSector.CENTER || heading == ArenaSector.UNKNOWN) {
						return;
					}
					if (dir != heading.opposite()) {
						outward.add(dir);
					}
				}
				if (outward.size() != 2) {
					return;
				}
				int diff = outward.get(0).eighthsTo(outward.get(1));
				if (Math.abs(diff) != 2) {
					return;
				}
				ArenaSector safe = outward.get(0).plusEighths(diff / 2);

				StackType mech = stackSpreadFirst != null ? stackSpreadFirst : stackSpreadSecond;
				if (stackSpreadFirst != null) {
					stackSpreadFirst = null;
				}
				else {
					stackSpreadSecond = null;
				}
				s.setParam("safe", safe);
				s.setParam("mechanic", mech == null ? StackType.UNKNOWN.label : mech.label);
				s.updateCall(safe.isCardinal() ? devilishThrallDiamond : devilishThrallSquare, e1);
			});

	private final ModifiableCallout<AbilityUsedEvent> reishoCountCall = new ModifiableCallout<>("Reisho (Count)", "{count}", 1_500);
	private final ModifiableCallout<AbilityUsedEvent> reishoTower = new ModifiableCallout<>("Reisho (Tower)", "Tower");
	private final RepeatSuppressor reishoSupp = new RepeatSuppressor(Duration.ofMillis(500));

	@HandleEvents
	public void reisho(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x840D) || !reishoSupp.check(event)) {
			return;
		}
		reishoCount++;
		if (reishoCount > REISHO_PULSES) {
			return;
		}
		if (ghostTower && reishoCount == REISHO_PULSES) {
			context.accept(reishoTower.getModified(event));
		}
		else {
			context.accept(reishoCountCall.getModified(event, Map.of("count", reishoCount)));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> hauntingThrallSpread = new ModifiableCallout<>("Haunting Thrall (Spread)", "Spread (w/{player})");
	private final ModifiableCallout<HeadMarkerEvent> hauntingThrallTower = new ModifiableCallout<>("Haunting Thrall (Tower)", "Get Tower (w/{player})");

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
		boolean meSpread = spread.stream().anyMatch(XivCombatant::isThePlayer);
		XivCombatant other;
		if (meSpread) {
			other = spread.stream().filter(cbt -> !cbt.isThePlayer()).findFirst().orElse(null);
		}
		else {
			other = state.getPartyList().stream()
					.filter(member -> !member.isThePlayer())
					.filter(member -> spread.stream().noneMatch(sp -> sp.getId() == member.getId()))
					.findFirst()
					.orElse(null);
		}
		ghostTower = !meSpread;
		Object player = other == null ? "Unknown" : other;
		context.accept((meSpread ? hauntingThrallSpread : hauntingThrallTower).getModified(event, Map.of("player", player)));
	}

	// ---- 2体目の道中 ----

	@NpcCastCallout(0x865C)
	private final ModifiableCallout<AbilityCastStart> backwardBlows = ModifiableCallout.durationBasedCall("Backward Blows", "Sides (Stay Sides)");

	@NpcCastCallout(0x865D)
	private final ModifiableCallout<AbilityCastStart> leftwardBlows = ModifiableCallout.durationBasedCall("Leftward Blows", "Right + Behind");

	@NpcCastCallout(0x865E)
	private final ModifiableCallout<AbilityCastStart> rightwardBlows = ModifiableCallout.durationBasedCall("Rightward Blows", "Left + Behind");

	@NpcCastCallout(0x8660)
	private final ModifiableCallout<AbilityCastStart> wrathOfTheTengu = ModifiableCallout.durationBasedCall("Wrath of the Tengu", "Raidwide with Bleed");

	@NpcCastCallout(0x8661)
	private final ModifiableCallout<AbilityCastStart> gazeOfTheTengu = ModifiableCallout.durationBasedCall("Gaze of the Tengu", "Look Away");

	@NpcCastCallout(0x8664)
	private final ModifiableCallout<AbilityCastStart> jujiShuriken = ModifiableCallout.durationBasedCall("Juji Shuriken", "Get Behind");

	@NpcCastCallout(0x8662)
	private final ModifiableCallout<AbilityCastStart> issen = ModifiableCallout.durationBasedCall("Issen", "Buster on {event.target}");

	@NpcCastCallout(0x8663)
	private final ModifiableCallout<AbilityCastStart> huton = ModifiableCallout.durationBasedCall("Huton", "Dodge 4x Shuriken");

	// ---- 剛雷 ----

	@NpcCastCallout(0x8534)
	private final ModifiableCallout<AbilityCastStart> unenlightenment = ModifiableCallout.durationBasedCall("Unenlightenment", "Raidwide with Bleed");

	@NpcCastCallout(0x8509)
	private final ModifiableCallout<AbilityCastStart> brazenBalladPurple = ModifiableCallout.durationBasedCall("Brazen Ballad (Purple)", "Avoid Expanding Lines");

	@NpcCastCallout(0x850A)
	private final ModifiableCallout<AbilityCastStart> brazenBalladBlue = ModifiableCallout.durationBasedCall("Brazen Ballad (Blue)", "Under Rock + Lines");

	@HandleEvents(order = -1)
	public void onSparks(EventContext context, Event event) {
		if (event instanceof AbilityCastStart acs && acs.abilityIdMatches(0x8503)) {
			sparksCount++;
			sparksCollect.clear();
		}
		else if (event instanceof BuffApplied ba && ba.buffIdMatches(SPARKS_STACK, SPARKS_SPREAD)) {
			sparksCollect.add(ba);
		}
	}

	private final ModifiableCallout<BuffApplied> flameAndSulphur = new ModifiableCallout<>("Seal of Scurrying Sparks (Stacks)", "{stack}");
	private final ModifiableCallout<BuffApplied> cloudToGround = new ModifiableCallout<>("Seal of Scurrying Sparks (Stack/Spread)", "{first} => {second}");
	private final ModifiableCallout<BuffApplied> sparksStacks = new ModifiableCallout<>("Seal of Scurrying Sparks (Stack Targets)", "Stacks: {player1}, {player2}");
	private final RepeatSuppressor sparksSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> sparksSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(SPARKS_STACK, SPARKS_SPREAD) && sparksSupp.check(ba),
			(e1, s) -> {
				s.waitMs(500);
				if (sparksCount == 2) {
					StackSpread ss = computeStackSpread(sparksCollect, SPARKS_STACK, SPARKS_SPREAD);
					if (ss == null) {
						return;
					}
					s.setParam("first", firstLabel(ss));
					s.setParam("second", secondLabel(ss));
					s.setParam("player1", ss.player1());
					s.setParam("player2", ss.player2());
					s.call(cloudToGround, e1);
					s.call(sparksStacks, e1);
					return;
				}
				List<BuffApplied> stacks = sparksCollect.stream().filter(ba -> ba.buffIdMatches(SPARKS_STACK)).toList();
				if (stacks.size() < 2) {
					return;
				}
				StackType type = findStackPartners(stacks.get(0).getTarget(), stacks.get(1).getTarget(), true);
				List<XivCombatant> players = List.of(stacks.get(0).getTarget(), stacks.get(1).getTarget()).stream()
						.sorted(Comparator.comparing(XivCombatant::getName))
						.toList();
				s.setParam("player1", players.get(0));
				s.setParam("player2", players.get(1));
				if (type != StackType.UNKNOWN) {
					s.setParam("stack", type.label);
					s.call(flameAndSulphur, e1);
				}
				s.call(sparksStacks, e1);
			});

	private final ModifiableCallout<AbilityUsedEvent> cloudToGroundFollowup = new ModifiableCallout<>("Seal of Scurrying Sparks Followup", "{mechanic}");
	private final RepeatSuppressor cloudToGroundSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void onGreatBallOfFire(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x8505, 0x8506) || !cloudToGroundSupp.check(event)) {
			return;
		}
		StackType mech = consumeSecondMechanic();
		if (mech != null) {
			context.accept(cloudToGroundFollowup.getModified(event, Map.of("mechanic", mech.label)));
		}
	}

	@NpcCastCallout(0x8532)
	private final ModifiableCallout<AbilityCastStart> torchingTorment = ModifiableCallout.durationBasedCall("Torching Torment", "Buster on {event.target}");

	@NpcCastCallout(0x852F)
	private final ModifiableCallout<AbilityCastStart> impurePurgation = ModifiableCallout.durationBasedCall("Impure Purgation (First)", "Protean");

	@NpcCastCallout(value = 0x8531, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> impurePurgationSecond = ModifiableCallout.durationBasedCall("Impure Purgation (Second)", "Move Away");

	private final ModifiableCallout<AbilityCastStart> humbleHammer = ModifiableCallout.durationBasedCall("Humble Hammer", "Shrink Lone Orb");
	private final ModifiableCallout<AbilityCastStart> flintlockBehindTank = new ModifiableCallout<>("Flintlock (Hammer Target)", "Stay Behind Tank");
	private final ModifiableCallout<AbilityCastStart> flintlockBlock = new ModifiableCallout<>("Flintlock (Tank)", "Block Laser on {event.target}");
	private final ModifiableCallout<AbilityCastStart> flintlockAvoid = new ModifiableCallout<>("Flintlock", "Avoid Laser");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> humbleHammerSq = SqtTemplates.sq(30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x8525),
			(e1, s) -> {
				boolean onMe = e1.getTarget().isThePlayer();
				if (onMe) {
					s.call(humbleHammer, e1);
				}
				// 小さくした玉と一緒にこの対象へ射線が来るので、詠唱完了まで待つ
				s.waitDuration(e1.getInitialDuration());
				if (onMe) {
					s.call(flintlockBehindTank, e1);
				}
				else if (state.playerJobMatches(Job::isTank)) {
					s.call(flintlockBlock, e1);
				}
				else {
					s.call(flintlockAvoid, e1);
				}
			});

	@HandleEvents(order = -1)
	public void reincarnationCollect(EventContext context, BuffApplied event) {
		if (rousingTowerCount == 0) {
			for (int i = 0; i < TOWER_SETS; i++) {
				if (event.buffIdMatches(ROUSING_BLUE[i])) {
					rousingBlue[i] = event.getTarget();
				}
				else if (event.buffIdMatches(ROUSING_ORANGE[i])) {
					rousingOrange[i] = event.getTarget();
				}
			}
		}
		else if (event.buffIdMatches(MALFORMED_IDS)) {
			malformedCollect.add(event);
			if (event.getTarget().isThePlayer()) {
				myMalformedEffects.add(event.getBuff().getId());
			}
		}
	}

	private final ModifiableCallout<BaseEvent> blueTower = new ModifiableCallout<>("Rousing Reincarnation (Blue Tower)", "Blue Tower {num}");
	private final ModifiableCallout<BaseEvent> orangeTower = new ModifiableCallout<>("Rousing Reincarnation (Orange Tower)", "Orange Tower {num}");
	private final ModifiableCallout<BaseEvent> towerTether = new ModifiableCallout<>("Rousing Reincarnation (Tether)", "Tether {num}");
	private final ModifiableCallout<BaseEvent> tetherThenBlue = new ModifiableCallout<>("Rousing Reincarnation (Tether, then Blue)", "Tether {num1} => Blue Tower {num2}");
	private final ModifiableCallout<BaseEvent> tetherThenOrange = new ModifiableCallout<>("Rousing Reincarnation (Tether, then Orange)", "Tether {num1} => Orange Tower {num2}");

	private static boolean isMe(@Nullable XivCombatant cbt) {
		return cbt != null && cbt.isThePlayer();
	}

	private void towerResponse(EventContext context, BaseEvent event) {
		int index = rousingTowerCount;
		int towerNum = index + 1;
		rousingTowerCount++;
		if (index >= TOWER_SETS) {
			return;
		}
		if (isMe(rousingBlue[index])) {
			context.accept(blueTower.getModified(event, Map.of("num", towerNum)));
			return;
		}
		if (isMe(rousingOrange[index])) {
			context.accept(orangeTower.getModified(event, Map.of("num", towerNum)));
			return;
		}
		// 元のロジックどおり、次のタワーは towerNum + 1 の位置を参照する
		int nextIndex = towerNum + 1;
		if (towerNum == TOWER_SETS || nextIndex >= TOWER_SETS) {
			context.accept(towerTether.getModified(event, Map.of("num", towerNum)));
			return;
		}
		if (isMe(rousingBlue[nextIndex])) {
			context.accept(tetherThenBlue.getModified(event, Map.of("num1", towerNum, "num2", nextIndex)));
		}
		else if (isMe(rousingOrange[nextIndex])) {
			context.accept(tetherThenOrange.getModified(event, Map.of("num1", towerNum, "num2", nextIndex)));
		}
		else {
			context.accept(towerTether.getModified(event, Map.of("num", towerNum)));
		}
	}

	@HandleEvents
	public void rousingFirstTower(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8518)) {
			towerResponse(context, event);
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> malformedTower2Orange = new ModifiableCallout<>("Malformed Reincarnation (Orange Tower 2)", "Corner Orange Tower 2");
	private final ModifiableCallout<AbilityUsedEvent> malformedTower3Orange = new ModifiableCallout<>("Malformed Reincarnation (Orange Tower 3)", "Placed Orange Tower 3");
	private final ModifiableCallout<AbilityUsedEvent> malformedTower2Blue = new ModifiableCallout<>("Malformed Reincarnation (Blue Tower 2)", "Corner Blue Tower 2");
	private final ModifiableCallout<AbilityUsedEvent> malformedTower3Blue = new ModifiableCallout<>("Malformed Reincarnation (Blue Tower 3)", "Placed Blue Tower 3");
	private final RepeatSuppressor malformedOtherSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void towerBurst(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x851B) || !event.isFirstTarget()) {
			return;
		}
		towerResponse(context, event);
		if (myMalformedEffects.isEmpty() || !malformedOtherSupp.check(event)) {
			return;
		}
		long effectId = myMalformedEffects.remove(0);
		ModifiableCallout<AbilityUsedEvent> call;
		if (effectId == 0xE0E) {
			call = malformedTower2Orange;
		}
		else if (effectId == 0xE0F) {
			call = malformedTower3Orange;
		}
		else if (effectId == 0xE12) {
			call = malformedTower2Blue;
		}
		else if (effectId == 0xE13) {
			call = malformedTower3Blue;
		}
		else {
			return;
		}
		context.accept(call.getModified(event));
	}

	@NpcCastCallout(0x852B)
	private final ModifiableCallout<AbilityCastStart> fightingSpirits = ModifiableCallout.durationBasedCall("Fighting Spirits", "Knockback");

	private final ModifiableCallout<HeadMarkerEvent> limitCut = new ModifiableCallout<>("Fighting Spirits (Limit Cut)", "{num}", 6_000);

	@HandleEvents
	public void limitCutMarker(EventContext context, HeadMarkerEvent event) {
		if (!event.getTarget().isThePlayer() || !event.markerIdMatches(0x0150, 0x0151, 0x0152, 0x0153)) {
			return;
		}
		int num = (int) (event.getMarkerId() - 0x0150) + 1;
		context.accept(limitCut.getModified(event, Map.of("num", num)));
	}

	private final ModifiableCallout<BuffApplied> malformedOrder = new ModifiableCallout<>("Malformed Reincarnation", "{color1} -> {color2} -> {color3} ({mixed})", 10_000);
	private final ModifiableCallout<BuffApplied> malformedPartner = new ModifiableCallout<>("Malformed Reincarnation (Partner)", "(first {color} w/{player})", 10_000);
	private final RepeatSuppressor malformedSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> malformedSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(MALFORMED_IDS) && rousingTowerCount != 0 && malformedSupp.check(ba),
			(e1, s) -> {
				s.waitMs(500);
				int playerCount = 0;
				int mixedCount = 0;
				Map<Long, String> firstColor = new LinkedHashMap<>();
				Map<Long, XivCombatant> players = new LinkedHashMap<>();
				List<String> myColors = new ArrayList<>();
				for (BuffApplied line : malformedCollect) {
					boolean isOrange = line.buffIdMatches(0xE0D, 0xE0E, 0xE0F);
					String color = isOrange ? "Orange" : "Blue";
					XivCombatant target = line.getTarget();
					if (target.isThePlayer()) {
						myColors.add(color);
					}
					String lastColor = firstColor.get(target.getId());
					if (lastColor == null) {
						playerCount++;
						firstColor.put(target.getId(), color);
						players.put(target.getId(), target);
						continue;
					}
					if (!lastColor.equals(color)) {
						mixedCount++;
					}
				}
				if (myColors.size() < MALFORMED_COLORS) {
					return;
				}
				String color1 = myColors.get(0);
				// 死亡者はデバフが付かないので、人数と混色数の一致で判定する
				boolean allMixed = playerCount == mixedCount;
				Object partner = "Unknown";
				for (Map.Entry<Long, String> entry : firstColor.entrySet()) {
					XivCombatant cbt = players.get(entry.getKey());
					if (!cbt.isThePlayer() && entry.getValue().equals(color1)) {
						partner = cbt;
						break;
					}
				}
				s.setParam("color1", color1);
				s.setParam("color2", myColors.get(1));
				s.setParam("color3", myColors.get(2));
				s.setParam("mixed", allMixed ? "full mixed" : "half mixed");
				s.setParam("color", color1);
				s.setParam("player", partner);
				s.call(malformedOrder, e1);
				s.call(malformedPartner, e1);
			});

	private final ModifiableCallout<BuffRemoved> malformedTower1Orange = new ModifiableCallout<>("Malformed Reincarnation (Orange Tower 1)", "Inside Orange Tower 1", 4_000);
	private final ModifiableCallout<BuffRemoved> malformedTower1Blue = new ModifiableCallout<>("Malformed Reincarnation (Blue Tower 1)", "Inside Blue Tower 1", 4_000);

	@HandleEvents
	public void malformedFirstTower(EventContext context, BuffRemoved event) {
		if (!event.buffIdMatches(0xE15, 0xE16) || !event.getTarget().isThePlayer() || myMalformedEffects.isEmpty()) {
			return;
		}
		long effectId = myMalformedEffects.remove(0);
		if (effectId == 0xE0D) {
			context.accept(malformedTower1Orange.getModified(event));
		}
		else if (effectId == 0xE11) {
			context.accept(malformedTower1Blue.getModified(event));
		}
	}

	@NpcAbilityUsedCallout(value = 0x8511, suppressMs = 30_000)
	private final ModifiableCallout<AbilityUsedEvent> fireSpread = new ModifiableCallout<>("Fire Spread", "Move Away");

	// ---- 怨霊モウコ ----

	@NpcCastCallout(0x85E0)
	private final ModifiableCallout<AbilityCastStart> kenkiRelease = ModifiableCallout.durationBasedCall("Kenki Release", "Raidwide");

	private final ModifiableCallout<BuffApplied> kasumiFirstBackRed = new ModifiableCallout<>("Triple Kasumi-giri (Back, Red)", "Back + Out", 10_000);
	private final ModifiableCallout<BuffApplied> kasumiFirstLeftRed = new ModifiableCallout<>("Triple Kasumi-giri (Left, Red)", "Left + Out", 10_000);
	private final ModifiableCallout<BuffApplied> kasumiFirstFrontRed = new ModifiableCallout<>("Triple Kasumi-giri (Front, Red)", "Front + Out", 10_000);
	private final ModifiableCallout<BuffApplied> kasumiFirstRightRed = new ModifiableCallout<>("Triple Kasumi-giri (Right, Red)", "Right + Out", 10_000);
	private final ModifiableCallout<BuffApplied> kasumiFirstBackBlue = new ModifiableCallout<>("Triple Kasumi-giri (Back, Blue)", "Back + In", 10_000);
	private final ModifiableCallout<BuffApplied> kasumiFirstLeftBlue = new ModifiableCallout<>("Triple Kasumi-giri (Left, Blue)", "Left + In", 10_000);
	private final ModifiableCallout<BuffApplied> kasumiFirstFrontBlue = new ModifiableCallout<>("Triple Kasumi-giri (Front, Blue)", "Front + In", 10_000);
	private final ModifiableCallout<BuffApplied> kasumiFirstRightBlue = new ModifiableCallout<>("Triple Kasumi-giri (Right, Blue)", "Right + In", 10_000);
	private final ModifiableCallout<BuffApplied> kasumiSecondPreview = new ModifiableCallout<>("Triple Kasumi-giri (Second Preview)", "({text})");
	private final ModifiableCallout<BuffApplied> kasumiAllPreview = new ModifiableCallout<>("Triple Kasumi-giri (All)", "{text1} => {text2} => {text3}", 10_000);

	private ModifiableCallout<BuffApplied> kasumiFirstCall(Kasumi kasumi) {
		return switch (kasumi) {
			case BACK_RED -> kasumiFirstBackRed;
			case LEFT_RED -> kasumiFirstLeftRed;
			case FRONT_RED -> kasumiFirstFrontRed;
			case RIGHT_RED -> kasumiFirstRightRed;
			case BACK_BLUE -> kasumiFirstBackBlue;
			case LEFT_BLUE -> kasumiFirstLeftBlue;
			case FRONT_BLUE -> kasumiFirstFrontBlue;
			case RIGHT_BLUE -> kasumiFirstRightBlue;
		};
	}

	@HandleEvents
	public void tripleKasumiMarker(EventContext context, BuffApplied event) {
		if (!isMokoVfx(event)) {
			return;
		}
		Kasumi kasumi = Kasumi.of(event.getRawStacks());
		tripleKasumiCollect.add(kasumi);
		switch (tripleKasumiCollect.size()) {
			case 1 -> context.accept(kasumiFirstCall(kasumi).getModified(event));
			case 2 -> context.accept(kasumiSecondPreview.getModified(event, Map.of("text", kasumi.followup)));
			case 3 -> context.accept(kasumiAllPreview.getModified(event, Map.of(
					"text1", tripleKasumiCollect.get(0).first,
					"text2", tripleKasumiCollect.get(1).followup,
					"text3", tripleKasumiCollect.get(2).followup)));
			default -> {
			}
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> kasumiBackRed = new ModifiableCallout<>("Triple Kasumi-giri Followup (Back, Red)", "Stay + Out");
	private final ModifiableCallout<AbilityUsedEvent> kasumiLeftRed = new ModifiableCallout<>("Triple Kasumi-giri Followup (Left, Red)", "Left + Out");
	private final ModifiableCallout<AbilityUsedEvent> kasumiFrontRed = new ModifiableCallout<>("Triple Kasumi-giri Followup (Front, Red)", "Through + Out");
	private final ModifiableCallout<AbilityUsedEvent> kasumiRightRed = new ModifiableCallout<>("Triple Kasumi-giri Followup (Right, Red)", "Right + Out");
	private final ModifiableCallout<AbilityUsedEvent> kasumiBackBlue = new ModifiableCallout<>("Triple Kasumi-giri Followup (Back, Blue)", "Stay + In");
	private final ModifiableCallout<AbilityUsedEvent> kasumiLeftBlue = new ModifiableCallout<>("Triple Kasumi-giri Followup (Left, Blue)", "Left + In");
	private final ModifiableCallout<AbilityUsedEvent> kasumiFrontBlue = new ModifiableCallout<>("Triple Kasumi-giri Followup (Front, Blue)", "Through + In");
	private final ModifiableCallout<AbilityUsedEvent> kasumiRightBlue = new ModifiableCallout<>("Triple Kasumi-giri Followup (Right, Blue)", "Right + In");
	private final RepeatSuppressor kasumiFollowupSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	private ModifiableCallout<AbilityUsedEvent> kasumiFollowupCall(Kasumi kasumi) {
		return switch (kasumi) {
			case BACK_RED -> kasumiBackRed;
			case LEFT_RED -> kasumiLeftRed;
			case FRONT_RED -> kasumiFrontRed;
			case RIGHT_RED -> kasumiRightRed;
			case BACK_BLUE -> kasumiBackBlue;
			case LEFT_BLUE -> kasumiLeftBlue;
			case FRONT_BLUE -> kasumiFrontBlue;
			case RIGHT_BLUE -> kasumiRightBlue;
		};
	}

	@HandleEvents
	public void tripleKasumiFollowup(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(TRIPLE_KASUMI_ABILITIES) || !kasumiFollowupSupp.check(event)) {
			return;
		}
		// 1回目はマーカー時点で読み上げ済みなので捨てる
		if (tripleKasumiCollect.size() == 3) {
			tripleKasumiCollect.remove(0);
		}
		if (tripleKasumiCollect.isEmpty()) {
			return;
		}
		Kasumi kasumi = tripleKasumiCollect.remove(0);
		context.accept(kasumiFollowupCall(kasumi).getModified(event));
	}

	@NpcCastCallout(0x85E3)
	private final ModifiableCallout<AbilityCastStart> lateralSlice = ModifiableCallout.durationBasedCall("Lateral Slice", "Buster on {event.target}");

	@NpcCastCallout(0x85D1)
	private final ModifiableCallout<AbilityCastStart> scarletAuspice = ModifiableCallout.durationBasedCall("Scarlet Auspice", "Sides + Out => Stay Out");

	@NpcCastCallout(0x85D4)
	private final ModifiableCallout<AbilityCastStart> azureAuspice = ModifiableCallout.durationBasedCall("Azure Auspice", "Under => Sides + Out");

	@NpcAbilityUsedCallout(value = 0x85D4, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> azureAuspiceFollowup = new ModifiableCallout<>("Azure Auspice Followup", "Sides + Out");

	private final ModifiableCallout<MapEffectEvent> fireLines = new ModifiableCallout<>("Explosion Lines", "{safe}", 5_000);

	@HandleEvents
	public void fireLineCollect(EventContext context, MapEffectEvent event) {
		long location = event.getLocation();
		if (!event.flagsMatches(0x00100020) || location < 0x2C || location > 0x2F) {
			return;
		}
		explosionLineCollect.add(event);
		if (explosionLineCollect.size() != 2) {
			return;
		}
		boolean isNorth = explosionLineCollect.stream().anyMatch(me -> me.getLocation() == 0x2F);
		boolean isSwOrNe = explosionLineCollect.stream().anyMatch(me -> me.getLocation() == 0x2D);
		ArenaSector safe;
		if (isNorth) {
			safe = isSwOrNe ? ArenaSector.NORTHEAST : ArenaSector.NORTHWEST;
		}
		else {
			safe = isSwOrNe ? ArenaSector.SOUTHWEST : ArenaSector.SOUTHEAST;
		}
		context.accept(fireLines.getModified(event, Map.of("safe", safe)));
	}

	@HandleEvents(order = -1)
	public void onVengefulBuff(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(VENGEFUL_SPREAD, VENGEFUL_STACK)) {
			invocationCollect.add(event);
		}
	}

	private final ModifiableCallout<BuffApplied> invocation = new ModifiableCallout<>("Invocation of Vengeance", "{first} => {second}", 5_000);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> invocationSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(VENGEFUL_SPREAD, VENGEFUL_STACK) && !invocationCalled,
			(e1, s) -> {
				invocationCalled = true;
				s.waitMs(500);
				StackSpread ss = computeStackSpread(invocationCollect, VENGEFUL_STACK, VENGEFUL_SPREAD);
				if (ss == null) {
					return;
				}
				// 呼び出しが多いので、頭割り対象者の読み上げは省く
				s.setParam("first", firstLabel(ss));
				s.setParam("second", secondLabel(ss));
				s.updateCall(invocation, e1);
			});

	private final ModifiableCallout<AbilityUsedEvent> invocationFollowup = new ModifiableCallout<>("Invocation of Vengeance Followup", "{mechanic}");
	private final RepeatSuppressor invocationFollowupSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void onVengefulHit(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x85DC, 0x85DD) || !invocationFollowupSupp.check(event)) {
			return;
		}
		StackType mech = consumeSecondMechanic();
		if (mech != null) {
			context.accept(invocationFollowup.getModified(event, Map.of("mechanic", mech.label)));
		}
	}

	private final RepeatSuppressor iaigiriCleanupSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents(order = -2)
	public void iaigiriCleanup(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x85C2, 0x85C8) && iaigiriCleanupSupp.check(event)) {
			clearIaigiri();
		}
	}

	@HandleEvents(order = -1)
	public void iaigiriCollect(EventContext context, Event event) {
		if (event instanceof TetherEvent tether && tether.tetherIdMatches(0x0011)) {
			iaigiriTether.add(tether);
			if (isMe(tetherPlayer(tether))) {
				myIaigiriTether = tether;
			}
		}
		else if (event instanceof BuffApplied ba && isShadowVfx(ba)) {
			iaigiriPurple.add(ba);
		}
		else if (event instanceof AbilityUsedEvent aue && aue.abilityIdMatches(0x85DA) && aue.getTarget().isThePlayer()) {
			myAccursedEdge = aue;
		}
		else if (event instanceof AbilityCastStart acs && acs.abilityIdMatches(0x8593)) {
			seenSoldiersOfDeath = true;
		}
	}

	private final ModifiableCallout<TetherEvent> fleetingOnYou = new ModifiableCallout<>("Fleeting Iai-giri (On You)", "{dir} Tether on You", 7_000);
	private final ModifiableCallout<TetherEvent> fleetingOnPlayer = new ModifiableCallout<>("Fleeting Iai-giri (On Other)", "{dir} Tether on {player}", 7_000);
	private final ModifiableCallout<TetherEvent> doubleShadowOnYou = new ModifiableCallout<>("Double Shadow Kasumi-giri (Tether)", "{dir} Tether ({partners})", 4_000);
	private final ModifiableCallout<TetherEvent> doubleShadowUnmarked = new ModifiableCallout<>("Double Shadow Kasumi-giri (Unmarked)", "Unmarked ({partners})", 4_000);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> iaigiriTetherSq = SqtTemplates.sq(5_000,
			TetherEvent.class, tether -> tether.tetherIdMatches(0x0011),
			(e1, s) -> {
				s.waitMs(500);
				if (iaigiriTether.size() == 1 && iaigiriPurple.size() == 1) {
					TetherEvent tether = iaigiriTether.get(0);
					ShadowDir dir = ShadowDir.of(iaigiriPurple.get(0).getRawStacks());
					XivCombatant player = tetherPlayer(tether);
					if (dir == null || player == null) {
						return;
					}
					s.setParam("dir", dir.tether);
					s.setParam("player", player);
					s.updateCall(player.isThePlayer() ? fleetingOnYou : fleetingOnPlayer, tether);
					return;
				}
				if (seenSoldiersOfDeath || iaigiriTether.size() != 2 || iaigiriPurple.size() != 2) {
					return;
				}
				StackType type = findStackPartners(tetherPlayer(iaigiriTether.get(0)), tetherPlayer(iaigiriTether.get(1)), true);
				s.setParam("partners", type.together);
				TetherEvent myTether = myIaigiriTether;
				if (myTether == null) {
					s.updateCall(doubleShadowUnmarked, e1);
					return;
				}
				long shadowId = tetherShadowId(myTether);
				BuffApplied myMarker = iaigiriPurple.get(0).getTarget().getId() == shadowId ? iaigiriPurple.get(0) : iaigiriPurple.get(1);
				ShadowDir dir = ShadowDir.of(myMarker.getRawStacks());
				if (dir == null) {
					return;
				}
				s.setParam("dir", dir.tether);
				s.updateCall(doubleShadowOnYou, myTether);
			});

	private final ModifiableCallout<BuffApplied> oniClawNorthSouth = new ModifiableCallout<>("Oni Claw (North/South)", "North/South");
	private final ModifiableCallout<BuffApplied> oniClawEastWest = new ModifiableCallout<>("Oni Claw (East/West)", "East/West");
	private final RepeatSuppressor oniClawSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> oniClawSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(ADD_VFX) && ba.getRawStacks() == 0x257 && oniClawSupp.check(ba),
			(e1, s) -> {
				s.refreshCombatants(100);
				Position pos = posOf(state, e1.getTarget());
				if (pos == null) {
					return;
				}
				double dx = pos.getX() - MOKO_CENTER_X;
				double dy = pos.getY() - MOKO_CENTER_Y;
				// 腕が東西にいるときは南北が安置
				oniClaw = Math.abs(dx) > Math.abs(dy) ? OniClaw.NORTH_SOUTH : OniClaw.EAST_WEST;
				s.updateCall(oniClaw == OniClaw.NORTH_SOUTH ? oniClawNorthSouth : oniClawEastWest, e1);
			});

	private final ModifiableCallout<AbilityCastStart> baitNear = ModifiableCallout.durationBasedCall("Near Edge (Untethered)", "Bait Near (Tether Far)");
	private final ModifiableCallout<AbilityCastStart> baitFar = ModifiableCallout.durationBasedCall("Far Edge (Untethered)", "Bait Far (Tether Near)");
	private final ModifiableCallout<AbilityCastStart> tetherNear = ModifiableCallout.durationBasedCall("Far Edge (Tethered)", "Tether Near (Bait Far)");
	private final ModifiableCallout<AbilityCastStart> tetherFar = ModifiableCallout.durationBasedCall("Near Edge (Tethered)", "Tether Far (Bait Near)");

	@HandleEvents
	public void nearFarEdge(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x85D8, 0x85D9)) {
			return;
		}
		boolean isFarEdge = event.abilityIdMatches(0x85D8);
		ModifiableCallout<AbilityCastStart> call;
		if (myIaigiriTether == null) {
			call = isFarEdge ? baitFar : baitNear;
		}
		else {
			call = isFarEdge ? tetherNear : tetherFar;
		}
		context.accept(call.getModified(event));
	}

	private @Nullable ShadowDir sameSecondMarks() {
		if (iaigiriPurple.size() < 4) {
			return null;
		}
		long third = iaigiriPurple.get(2).getRawStacks();
		long fourth = iaigiriPurple.get(3).getRawStacks();
		return third == fourth ? ShadowDir.of(third) : null;
	}

	private final ModifiableCallout<BuffApplied> secondMark = new ModifiableCallout<>("Double Shadow Kasumi-giri (Second Mark)", "(then {dir})", 5_500);
	private final RepeatSuppressor secondMarkSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> secondMarkSq = SqtTemplates.sq(10_000,
			BuffApplied.class, ba -> {
				if (!isShadowVfx(ba) || seenSoldiersOfDeath || iaigiriPurple.size() <= 2) {
					return false;
				}
				// 2組目が同じ向きなら、立ち位置に関係なく全員に伝えられる
				if (sameSecondMarks() != null) {
					return secondMarkSupp.check(ba);
				}
				TetherEvent myTether = myIaigiriTether;
				return myTether != null && tetherShadowId(myTether) == ba.getTarget().getId() && secondMarkSupp.check(ba);
			},
			(e1, s) -> {
				// 刃の距離の呼び出しと被らないよう遅らせる
				s.waitMs(3_000);
				ShadowDir dir = sameSecondMarks();
				if (dir == null) {
					dir = ShadowDir.of(e1.getRawStacks());
				}
				if (dir == null) {
					return;
				}
				s.setParam("dir", dir.then);
				s.updateCall(secondMark, e1);
			});

	private final ModifiableCallout<AbilityUsedEvent> shadowBack = new ModifiableCallout<>("Shadow Kasumi-giri (Back Tether)", "Back", 2_000);
	private final RepeatSuppressor shadowBackSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void shadowBackTether(EventContext context, AbilityUsedEvent event) {
		TetherEvent myTether = myIaigiriTether;
		if (!event.abilityIdMatches(0x85C9) || myTether == null || tetherShadowId(myTether) != event.getSource().getId()) {
			return;
		}
		if (shadowBackSupp.check(event)) {
			context.accept(shadowBack.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> shadowFollowup = new ModifiableCallout<>("Shadow Kasumi-giri Followup", "{dir}");
	private final RepeatSuppressor shadowFollowupSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	private boolean isOnSameSideAs(AbilityUsedEvent event) {
		AbilityUsedEvent edge = myAccursedEdge;
		Position thisPos = event.getSource().getPos();
		Position myPos = edge == null ? null : edge.getTarget().getPos();
		if (oniClaw == null || thisPos == null || myPos == null) {
			return false;
		}
		if (oniClaw == OniClaw.NORTH_SOUTH) {
			return !(myPos.getY() < MOKO_CENTER_Y && thisPos.getY() > MOKO_CENTER_Y
					|| myPos.getY() > MOKO_CENTER_Y && thisPos.getY() < MOKO_CENTER_Y);
		}
		return !(myPos.getX() < MOKO_CENTER_X && thisPos.getX() > MOKO_CENTER_X
				|| myPos.getX() > MOKO_CENTER_X && thisPos.getX() < MOKO_CENTER_X);
	}

	@HandleEvents
	public void shadowKasumiFollowup(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x85CA)) {
			return;
		}
		TetherEvent myTether = myIaigiriTether;
		long sourceId = event.getSource().getId();
		// 線のない人は、自分と同じ側に来た分身だけを見る
		if (myTether == null ? !isOnSameSideAs(event) : tetherShadowId(myTether) != sourceId) {
			return;
		}
		if (!shadowFollowupSupp.check(event)) {
			return;
		}
		BuffApplied marker = null;
		for (int i = iaigiriPurple.size() - 1; i >= 0; i--) {
			if (iaigiriPurple.get(i).getTarget().getId() == sourceId) {
				marker = iaigiriPurple.get(i);
				break;
			}
		}
		if (marker == null) {
			return;
		}
		ShadowDir dir = ShadowDir.of(marker.getRawStacks());
		if (dir != null) {
			context.accept(shadowFollowup.getModified(event, Map.of("dir", dir.move)));
		}
	}

	private final ModifiableCallout<BuffApplied> soldiersBlueAdd = new ModifiableCallout<>("Soldiers of Death (Blue Add)", "{safe}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> soldiersBlueAddSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(ADD_VFX) && ba.getRawStacks() == 0x5E,
			(e1, s) -> {
				s.refreshCombatants(100);
				Position pos = posOf(state, e1.getTarget());
				if (pos == null) {
					return;
				}
				double x = pos.getX() - MOKO_CENTER_X;
				double y = pos.getY() - MOKO_CENTER_Y;
				// 外周から辺に沿って撃つので、近い方の軸の反対側へ。東西からは長く、南北からは短く撃つ
				boolean isShootingLongFromEastWest = Math.abs(x) > Math.abs(y);
				boolean isEast = x > 0;
				boolean isNorth = y < 0;
				ArenaSector safe;
				if (isShootingLongFromEastWest) {
					if (isNorth) {
						safe = isEast ? ArenaSector.SOUTHEAST : ArenaSector.SOUTHWEST;
					}
					else {
						safe = isEast ? ArenaSector.NORTHEAST : ArenaSector.NORTHWEST;
					}
				}
				else if (isEast) {
					safe = isNorth ? ArenaSector.SOUTHWEST : ArenaSector.NORTHWEST;
				}
				else {
					safe = isNorth ? ArenaSector.SOUTHEAST : ArenaSector.NORTHEAST;
				}
				s.setParam("safe", safe);
				s.updateCall(soldiersBlueAdd, e1);
			});

	private final ModifiableCallout<BuffApplied> soldiersLeftTether = new ModifiableCallout<>("Soldiers of Death (Left Tether)", "Left Tether ({partners})", 5_000);
	private final ModifiableCallout<BuffApplied> soldiersRightTether = new ModifiableCallout<>("Soldiers of Death (Right Tether)", "Right Tether ({partners})", 5_000);
	private final RepeatSuppressor soldiersTetherSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> soldiersTetherSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> isShadowVfx(ba) && iaigiriPurple.size() > 4 && soldiersTetherSupp.check(ba),
			(e1, s) -> {
				if (iaigiriPurple.size() != SOLDIERS_MARKER_COUNT) {
					s.waitMs(500);
				}
				TetherEvent myTether = myIaigiriTether;
				if (myTether == null) {
					return;
				}
				long myShadowId = tetherShadowId(myTether);
				BuffApplied marker = null;
				for (int i = iaigiriPurple.size() - 1; i >= 0; i--) {
					if (iaigiriPurple.get(i).getTarget().getId() == myShadowId) {
						marker = iaigiriPurple.get(i);
						break;
					}
				}
				if (marker == null) {
					return;
				}
				ShadowDir dir = ShadowDir.of(marker.getRawStacks());
				if (dir != ShadowDir.LEFT && dir != ShadowDir.RIGHT) {
					return;
				}
				long markerCount = marker.getRawStacks();
				long markerTargetId = marker.getTarget().getId();
				List<BuffApplied> matching = iaigiriPurple.stream()
						.filter(ba -> ba.getRawStacks() == markerCount && ba.getTarget().getId() != markerTargetId)
						.toList();
				if (matching.size() != 1) {
					return;
				}
				long partnerShadowId = matching.get(0).getTarget().getId();
				TetherEvent partnerTether = iaigiriTether.stream()
						.filter(t -> tetherShadowId(t) == partnerShadowId)
						.findFirst()
						.orElse(null);
				if (partnerTether == null) {
					return;
				}
				// ここではロール同士で組めば常に安全なので、近接優先の設定は使わない
				StackType type = findStackPartners(state.getPlayer(), tetherPlayer(partnerTether), false);
				s.setParam("partners", type.together);
				s.updateCall(dir == ShadowDir.LEFT ? soldiersLeftTether : soldiersRightTether, e1);
			});
}
