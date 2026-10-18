package gg.xp.xivsupport.triggers.cactbot.ew;

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
import gg.xp.xivsupport.events.actlines.events.BuffRemoved;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/alliance/euphrosyne.ts をもとに移植
@CalloutRepo(name = "Euphrosyne", duty = KnownDuty.Euphrosyne)
public class Euphrosyne extends AutoChildEventHandler implements FilteredEventHandler {

	// 複数人に同時に付く対象を集めきるまでの待ち時間
	private static final int COLLECT_MS = 300;
	// 強制移動の効果時間がこれ以下になった時点の受け渡しだけを対象にする
	private static final Duration MARCH_WINDOW = Duration.ofSeconds(10);
	// ハイドロスタシスの詠唱者を集めて位置を確認するまでの時間
	private static final int HYDROSTASIS_COLLECT_MS = 1_500;
	private static final double HYDROSTASIS_CENTER_X = 50;
	private static final double HYDROSTASIS_CENTER_Y = -741;
	// アイスダート・ウェイニングクロウの記録が有効な時間
	private static final Duration ICE_DART_WINDOW = Duration.ofSeconds(10);
	private static final Duration WAXING_CLAW_WINDOW = Duration.ofSeconds(10);

	private XivState state;

	public Euphrosyne(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.Euphrosyne);
	}

	private boolean busterOnPlayer;
	private BuffApplied marchBuff;
	private BuffApplied spinnerBuff;
	private final List<Long> tetrapagos = new ArrayList<>();
	private AbilityCastStart iceDartOnPlayer;
	private AbilityCastStart waxingClaw;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		busterOnPlayer = false;
		marchBuff = null;
		spinnerBuff = null;
		tetrapagos.clear();
		iceDartOnPlayer = null;
		waxingClaw = null;
	}

	// ノフィカ

	@NpcCastCallout({0x7C24, 0x7C1D, 0x7C1E, 0x7C19})
	private final ModifiableCallout<AbilityCastStart> nophicaRaidwide = ModifiableCallout.durationBasedCall("Abundance / Matron's Harvest / Landwaker", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> springFlowers = ModifiableCallout.durationBasedCall("Spring Flowers", "Out");
	private final ModifiableCallout<AbilityCastStart> springFlowersForward = ModifiableCallout.durationBasedCall("Spring Flowers (Forward March)", "Forward March, Out");
	private final ModifiableCallout<AbilityCastStart> springFlowersBackward = ModifiableCallout.durationBasedCall("Spring Flowers (Backward March)", "Backward March, Out");
	private final ModifiableCallout<AbilityCastStart> springFlowersLeft = ModifiableCallout.durationBasedCall("Spring Flowers (Left March)", "Left March, Out");
	private final ModifiableCallout<AbilityCastStart> springFlowersRight = ModifiableCallout.durationBasedCall("Spring Flowers (Right March)", "Right March, Out");
	private final ModifiableCallout<AbilityCastStart> summerShade = ModifiableCallout.durationBasedCall("Summer Shade", "In");
	private final ModifiableCallout<AbilityCastStart> summerShadeForward = ModifiableCallout.durationBasedCall("Summer Shade (Forward March)", "Forward March, In");
	private final ModifiableCallout<AbilityCastStart> summerShadeBackward = ModifiableCallout.durationBasedCall("Summer Shade (Backward March)", "Backward March, In");
	private final ModifiableCallout<AbilityCastStart> summerShadeLeft = ModifiableCallout.durationBasedCall("Summer Shade (Left March)", "Left March, In");
	private final ModifiableCallout<AbilityCastStart> summerShadeRight = ModifiableCallout.durationBasedCall("Summer Shade (Right March)", "Right March, In");

	@HandleEvents
	public void floralHaze(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0xDD2, 0xDD3, 0xDD4, 0xDD5) && event.getTarget().isThePlayer()) {
			marchBuff = event;
		}
	}

	@HandleEvents
	public void floralHazeRemoved(EventContext context, BuffRemoved event) {
		if (event.buffIdMatches(0xDD2, 0xDD3, 0xDD4, 0xDD5) && event.getTarget().isThePlayer()) {
			marchBuff = null;
		}
	}

	@HandleEvents
	public void givingLand(EventContext context, AbilityCastStart event) {
		boolean out;
		if (event.abilityIdMatches(0x801A)) {
			out = true;
		}
		else if (event.abilityIdMatches(0x8018)) {
			out = false;
		}
		else {
			return;
		}
		// 次の花粉は18秒か35秒後に付くので、間近に迫った強制移動だけを組み合わせる
		BuffApplied march = marchBuff;
		long buffId = -1;
		if (march != null && march.getEstimatedRemainingDuration().compareTo(MARCH_WINDOW) <= 0) {
			buffId = march.getBuff().getId();
		}
		ModifiableCallout<AbilityCastStart> call;
		if (buffId == 0xDD2) {
			call = out ? springFlowersForward : summerShadeForward;
		}
		else if (buffId == 0xDD3) {
			call = out ? springFlowersBackward : summerShadeBackward;
		}
		else if (buffId == 0xDD4) {
			call = out ? springFlowersLeft : summerShadeLeft;
		}
		else if (buffId == 0xDD5) {
			call = out ? springFlowersRight : summerShadeRight;
		}
		else {
			call = out ? springFlowers : summerShade;
		}
		context.accept(call.getModified(event));
	}

	@NpcCastCallout(0x7C16)
	private final ModifiableCallout<AbilityCastStart> furrow = ModifiableCallout.durationBasedCall("Furrow", "Stack on {event.target}");

	@HandleEvents
	public void busterCollect(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x7C23, 0x7D78) && event.getTarget().isThePlayer()) {
			busterOnPlayer = true;
		}
	}

	@HandleEvents
	public void lunarKissCollect(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x19C) && event.getTarget().isThePlayer()) {
			busterOnPlayer = true;
		}
	}

	@NpcCastCallout(value = 0x7C23, onYou = true)
	private final ModifiableCallout<AbilityCastStart> heavensEarthOnYou = ModifiableCallout.durationBasedCall("Heavens' Earth (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> heavensEarth = ModifiableCallout.durationBasedCall("Heavens' Earth (Not on You)", "Tankbusters");
	private final RepeatSuppressor heavensEarthSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> heavensEarthSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x7C23) && heavensEarthSupp.check(acs),
			(e1, s) -> {
				s.waitMs(COLLECT_MS);
				if (!busterOnPlayer) {
					s.updateCall(heavensEarth, e1);
				}
				busterOnPlayer = false;
			});

	// 道中

	@NpcCastCallout(value = 0x7D3B, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> roaringRumble = ModifiableCallout.durationBasedCall("Roaring Rumble", "Raidwide");

	@NpcCastCallout(value = 0x7D39, onYou = true)
	private final ModifiableCallout<AbilityCastStart> sweepingGouge = ModifiableCallout.durationBasedCall("Sweeping Gouge", "Buster on You");

	@NpcCastCallout(0x7D37)
	private final ModifiableCallout<AbilityCastStart> localizedMaelstrom = ModifiableCallout.durationBasedCall("Localized Maelstrom", "Shared Buster on {event.target}");

	@NpcCastCallout(0x7D38)
	private final ModifiableCallout<AbilityCastStart> trounce = ModifiableCallout.durationBasedCall("Trounce", "Away from Front");

	// 同時に詠唱されることが多いので、自分が対象でも一度だけ言う
	@NpcCastCallout(value = 0x7D3C, onYou = true, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> rapidSever = ModifiableCallout.durationBasedCall("Rapid Sever", "Buster on You");

	// ニメーヤ・アルシク

	private final ModifiableCallout<BuffApplied> spinnerLookAwaySoon = new ModifiableCallout<>("Spinner's Wheel: Look Away (Early)", "Look Away Soon");
	private final ModifiableCallout<BuffApplied> spinnerLookTowardsSoon = new ModifiableCallout<>("Spinner's Wheel: Look Towards (Early)", "Look Towards Soon");
	private final ModifiableCallout<BuffApplied> spinnerPyreticSoon = new ModifiableCallout<>("Spinner's Wheel: Pyretic (Early)", "Pyretic Soon");
	private final ModifiableCallout<BuffApplied> spinnerFreezeSoon = new ModifiableCallout<>("Spinner's Wheel: Freeze (Early)", "Keep Moving Soon");
	private final RepeatSuppressor spinnerInitialSupp = new RepeatSuppressor(Duration.ofSeconds(20));

	private final ModifiableCallout<BuffApplied> spinnerLookAway = new ModifiableCallout<>("Spinner's Wheel: Look Away", "Look Away from Nymeia");
	private final ModifiableCallout<BuffApplied> spinnerLookTowards = new ModifiableCallout<>("Spinner's Wheel: Look Towards", "Look Towards Nymeia");
	private final ModifiableCallout<BuffApplied> spinnerPyretic = new ModifiableCallout<>("Spinner's Wheel: Pyretic", "Stop Everything");
	private final ModifiableCallout<BuffApplied> spinnerFreeze = new ModifiableCallout<>("Spinner's Wheel: Freeze", "Keep Moving");
	private final RepeatSuppressor spinnerSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	private static boolean isSpinnerBuff(BuffApplied ba) {
		return ba.buffIdMatches(0xD39, 0xD3A, 0xD3B, 0xD3C) && ba.getTarget().isThePlayer();
	}

	private ModifiableCallout<BuffApplied> spinnerCall(BuffApplied ba, boolean early) {
		long id = ba.getBuff().getId();
		if (id == 0xD39) {
			return early ? spinnerLookAwaySoon : spinnerLookAway;
		}
		else if (id == 0xD3A) {
			return early ? spinnerLookTowardsSoon : spinnerLookTowards;
		}
		else if (id == 0xD3B) {
			return early ? spinnerPyreticSoon : spinnerPyretic;
		}
		else {
			return early ? spinnerFreezeSoon : spinnerFreeze;
		}
	}

	@HandleEvents
	public void spinnerInitial(EventContext context, BuffApplied event) {
		// 時と潮の詠唱で付け直されるので、最初の一回だけ予告する
		if (isSpinnerBuff(event) && spinnerInitialSupp.check(event)) {
			context.accept(spinnerCall(event, true).getModified(event));
		}
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> spinnerSq = SqtTemplates.sq(30_000,
			BuffApplied.class, ba -> isSpinnerBuff(ba) && spinnerSupp.check(ba),
			(e1, s) -> {
				spinnerBuff = e1;
				s.waitDuration(e1.getInitialDuration().minusSeconds(2));
				if (spinnerBuff == e1) {
					spinnerBuff = null;
					s.updateCall(spinnerCall(e1, false), e1);
				}
			});

	// 線で加速されたときは早めにコールし、通常の時間でのコールは取り消す
	@AutoFeed
	private final SequentialTrigger<BaseEvent> spinnerTetherSq = SqtTemplates.sq(15_000,
			TetherEvent.class, te -> te.tetherIdMatches(0xDF) && te.getSource().isThePlayer(),
			(e1, s) -> {
				s.waitMs(11_500);
				BuffApplied buff = spinnerBuff;
				if (buff != null) {
					spinnerBuff = null;
					s.updateCall(spinnerCall(buff, false), buff);
				}
			});

	@NpcCastCallout(0x7A47)
	private final ModifiableCallout<AbilityCastStart> axioma = ModifiableCallout.durationBasedCall("Axioma", "Raidwide");

	@NpcCastCallout(0x7A44)
	private final ModifiableCallout<AbilityCastStart> hydroptosis = ModifiableCallout.durationBasedCall("Hydroptosis", "Spread, Avoid Purple");

	@NpcCastCallout(0x7A42)
	private final ModifiableCallout<AbilityCastStart> inexorablePull = ModifiableCallout.durationBasedCall("Inexorable Pull", "Stand in Purple Fissure");

	@NpcCastCallout(0x7A48)
	private final ModifiableCallout<AbilityCastStart> petrai = ModifiableCallout.durationBasedCall("Petrai", "Shared Buster on {event.target}");

	private final ModifiableCallout<AbilityCastStart> hydrostasis = new ModifiableCallout<>("Hydrostasis", "Knockback {dir1} => {dir2} => {dir3}", 18_000);
	private final RepeatSuppressor hydrostasisSupp = new RepeatSuppressor(Duration.ofSeconds(20));

	private static boolean isHydrostasis(AbilityCastStart acs) {
		return acs.abilityIdMatches(0x7A3B, 0x7A3C, 0x7A3D, 0x7A3E);
	}

	private ArenaSector hydrostasisDir(AbilityCastStart acs) {
		Position pos = state.getLatestCombatantData(acs.getSource()).getPos();
		if (pos == null) {
			return ArenaSector.UNKNOWN;
		}
		if (pos.getY() < HYDROSTASIS_CENTER_Y) {
			return ArenaSector.NORTH;
		}
		return pos.getX() < HYDROSTASIS_CENTER_X ? ArenaSector.SOUTHWEST : ArenaSector.SOUTHEAST;
	}

	// 初回は3つ同時、2回目以降は2つ詠唱した後に残りの1つが遅れて来る
	@AutoFeed
	private final SequentialTrigger<BaseEvent> hydrostasisSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> isHydrostasis(acs) && hydrostasisSupp.check(acs),
			(e1, s) -> {
				List<AbilityCastStart> casts = new ArrayList<>();
				casts.add(e1);
				casts.addAll(s.waitEventsUntil(3,
						AbilityCastStart.class, Euphrosyne::isHydrostasis,
						BaseEvent.class, e -> e1.getEffectiveTimeSince().toMillis() > HYDROSTASIS_COLLECT_MS));
				s.refreshCombatants(100);
				// 詠唱IDの順に発動する
				casts.sort(Comparator.comparingLong(acs -> acs.getAbility().getId()));
				List<ArenaSector> dirs = new ArrayList<>();
				for (AbilityCastStart cast : casts) {
					dirs.add(hydrostasisDir(cast));
				}
				if (dirs.size() < 2) {
					return;
				}
				if (dirs.size() == 2) {
					// 遅れて来る1つは残った方角で、最初に発動する
					Set<ArenaSector> remaining = EnumSet.of(ArenaSector.NORTH, ArenaSector.SOUTHWEST, ArenaSector.SOUTHEAST);
					dirs.forEach(remaining::remove);
					if (remaining.size() != 1) {
						return;
					}
					dirs.add(0, remaining.iterator().next());
				}
				if (dirs.get(0) == dirs.get(1) || dirs.get(1) == dirs.get(2)) {
					return;
				}
				s.updateCall(hydrostasis.getModified(e1, Map.<String, Object>of("dir1", dirs.get(0), "dir2", dirs.get(1), "dir3", dirs.get(2))));
			});

	// ハルオーネ

	@NpcCastCallout(0x7D79)
	private final ModifiableCallout<AbilityCastStart> rainOfSpears = ModifiableCallout.durationBasedCall("Rain of Spears", "Raidwide");

	private static String tetraName(long id) {
		if (id == 0x7D46) {
			return "Out";
		}
		else if (id == 0x7D47) {
			return "In";
		}
		else if (id == 0x7D48) {
			return "Left";
		}
		else if (id == 0x7D49) {
			return "Right";
		}
		return "Unknown";
	}

	private final ModifiableCallout<AbilityCastStart> tetrapagosFirstOut = new ModifiableCallout<>("Tetrapagos: First (Out)", "Out", 7_000);
	private final ModifiableCallout<AbilityCastStart> tetrapagosFirstIn = new ModifiableCallout<>("Tetrapagos: First (In)", "In", 7_000);
	private final ModifiableCallout<AbilityCastStart> tetrapagosFirstLeft = new ModifiableCallout<>("Tetrapagos: First (Left)", "Left", 7_000);
	private final ModifiableCallout<AbilityCastStart> tetrapagosFirstRight = new ModifiableCallout<>("Tetrapagos: First (Right)", "Right", 7_000);
	private final ModifiableCallout<AbilityCastStart> tetrapagosSummary = new ModifiableCallout<>("Tetrapagos: Summary", "{dir1} > {dir2} > {dir3} > {dir4}", 7_500);
	private final ModifiableCallout<AbilityUsedEvent> tetrapagosNextOut = new ModifiableCallout<>("Tetrapagos: Next (Out)", "Out", 1_500);
	private final ModifiableCallout<AbilityUsedEvent> tetrapagosNextIn = new ModifiableCallout<>("Tetrapagos: Next (In)", "In", 1_500);
	private final ModifiableCallout<AbilityUsedEvent> tetrapagosNextLeft = new ModifiableCallout<>("Tetrapagos: Next (Left)", "Left", 1_500);
	private final ModifiableCallout<AbilityUsedEvent> tetrapagosNextRight = new ModifiableCallout<>("Tetrapagos: Next (Right)", "Right", 1_500);
	private final RepeatSuppressor tetrapagosFirstSupp = new RepeatSuppressor(Duration.ofSeconds(20));

	@HandleEvents
	public void tetrapagosCast(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x7D45, 0x7D59)) {
			tetrapagos.clear();
			return;
		}
		if (!event.abilityIdMatches(0x7D46, 0x7D47, 0x7D48, 0x7D49)) {
			return;
		}
		long id = event.getAbility().getId();
		tetrapagos.add(id);
		if (tetrapagosFirstSupp.check(event)) {
			ModifiableCallout<AbilityCastStart> call;
			if (id == 0x7D46) {
				call = tetrapagosFirstOut;
			}
			else if (id == 0x7D47) {
				call = tetrapagosFirstIn;
			}
			else if (id == 0x7D48) {
				call = tetrapagosFirstLeft;
			}
			else {
				call = tetrapagosFirstRight;
			}
			context.accept(call.getModified(event));
		}
		if (tetrapagos.size() == 4) {
			context.accept(tetrapagosSummary.getModified(event, Map.<String, Object>of(
					"dir1", tetraName(tetrapagos.get(0)),
					"dir2", tetraName(tetrapagos.get(1)),
					"dir3", tetraName(tetrapagos.get(2)),
					"dir4", tetraName(tetrapagos.get(3)))));
		}
	}

	@HandleEvents
	public void tetrapagosUsed(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x7D4A, 0x7D4B, 0x7D4C, 0x7D4D)) {
			return;
		}
		// 最初の1つは詠唱時にコール済みなので捨てる
		if (tetrapagos.size() == 4) {
			tetrapagos.remove(0);
		}
		if (tetrapagos.isEmpty()) {
			return;
		}
		long id = tetrapagos.remove(0);
		ModifiableCallout<AbilityUsedEvent> call;
		if (id == 0x7D46) {
			call = tetrapagosNextOut;
		}
		else if (id == 0x7D47) {
			call = tetrapagosNextIn;
		}
		else if (id == 0x7D48) {
			call = tetrapagosNextLeft;
		}
		else if (id == 0x7D49) {
			call = tetrapagosNextRight;
		}
		else {
			return;
		}
		context.accept(call.getModified(event));
	}

	@NpcCastCallout(0x80AD)
	private final ModifiableCallout<AbilityCastStart> doomSpear = ModifiableCallout.durationBasedCall("Doom Spear", "Get Towers");

	@NpcCastCallout(value = 0x7D78, onYou = true)
	private final ModifiableCallout<AbilityCastStart> spearsThreeOnYou = ModifiableCallout.durationBasedCall("Spears Three (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> spearsThree = ModifiableCallout.durationBasedCall("Spears Three (Not on You)", "Tankbusters");
	private final RepeatSuppressor spearsThreeSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> spearsThreeSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x7D78) && spearsThreeSupp.check(acs),
			(e1, s) -> {
				s.waitMs(COLLECT_MS);
				if (!busterOnPlayer) {
					s.updateCall(spearsThree, e1);
				}
				busterOnPlayer = false;
			});

	private final ModifiableCallout<HeadMarkerEvent> thousandfoldBack = new ModifiableCallout<>("Thousandfold Thrust (Back)", "Back");
	private final ModifiableCallout<HeadMarkerEvent> thousandfoldFront = new ModifiableCallout<>("Thousandfold Thrust (Front)", "Front");
	private final ModifiableCallout<HeadMarkerEvent> thousandfoldLeft = new ModifiableCallout<>("Thousandfold Thrust (Left)", "Left");
	private final ModifiableCallout<HeadMarkerEvent> thousandfoldRight = new ModifiableCallout<>("Thousandfold Thrust (Right)", "Right");

	@HandleEvents
	public void thousandfoldThrust(EventContext context, HeadMarkerEvent event) {
		// ボス自身に付くマーカーで向きが決まる
		if (event.getTarget().isThePlayer()) {
			return;
		}
		ModifiableCallout<HeadMarkerEvent> call;
		if (event.markerIdMatches(0x182)) {
			call = thousandfoldBack;
		}
		else if (event.markerIdMatches(0x183)) {
			call = thousandfoldFront;
		}
		else if (event.markerIdMatches(0x184)) {
			call = thousandfoldLeft;
		}
		else if (event.markerIdMatches(0x185)) {
			call = thousandfoldRight;
		}
		else {
			return;
		}
		context.accept(call.getModified(event));
	}

	@NpcCastCallout(0x7D63)
	private final ModifiableCallout<AbilityCastStart> wrathOfHalone = ModifiableCallout.durationBasedCall("Wrath of Halone", "Out, Avoid Ring");

	@NpcCastCallout(value = 0x7D66, onYou = true)
	private final ModifiableCallout<AbilityCastStart> iceDart = ModifiableCallout.durationBasedCall("Ice Dart", "Spread");

	@HandleEvents
	public void iceDartCollect(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x7D66) && event.getTarget().isThePlayer()) {
			iceDartOnPlayer = event;
		}
	}

	private final ModifiableCallout<AbilityCastStart> iceRondel = ModifiableCallout.durationBasedCall("Ice Rondel", "Stack on {event.target}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> iceRondelSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x7D67),
			(e1, s) -> {
				s.waitMs(500);
				// アイスダートの対象者は頭割りに入らない
				AbilityCastStart dart = iceDartOnPlayer;
				if (dart != null && dart.getEffectiveTimeSince().compareTo(ICE_DART_WINDOW) < 0) {
					return;
				}
				s.updateCall(iceRondel, e1);
			});

	// メネフィナ

	@NpcCastCallout({0x7BFA, 0x7BFB})
	private final ModifiableCallout<AbilityCastStart> blueMoon = ModifiableCallout.durationBasedCall("Blue Moon", "Raidwide");

	private final ModifiableCallout<AbilityUsedEvent> lovesLightSingle = new ModifiableCallout<>("Love's Light (Single Moon)", "Sides of Moon");
	private final ModifiableCallout<AbilityUsedEvent> lovesLightQuadruple = new ModifiableCallout<>("Love's Light (Quadruple Moon)", "Go to Dark Moon");

	@HandleEvents
	public void lovesLight(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x7BB8, 0x7BC2)) {
			context.accept(lovesLightSingle.getModified(event));
		}
		else if (event.abilityIdMatches(0x7BB9, 0x7BC3)) {
			context.accept(lovesLightQuadruple.getModified(event));
		}
	}

	@NpcCastCallout({0x7BCC, 0x7BD0})
	private final ModifiableCallout<AbilityCastStart> midnightFrostBack = ModifiableCallout.durationBasedCall("Midnight Frost (Back Cleave)", "Go Front");

	@NpcCastCallout({0x7BCB, 0x7BCF})
	private final ModifiableCallout<AbilityCastStart> midnightFrostFront = ModifiableCallout.durationBasedCall("Midnight Frost (Front Cleave)", "Get Behind");

	@HandleEvents
	public void waxingClaw(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x7BE0, 0x7BE1)) {
			waxingClaw = event;
		}
	}

	private final ModifiableCallout<AbilityCastStart> midnightFrostFrontLeft = ModifiableCallout.durationBasedCall("Midnight Frost + Waxing Claw (Front Left)", "Front Left");
	private final ModifiableCallout<AbilityCastStart> midnightFrostFrontRight = ModifiableCallout.durationBasedCall("Midnight Frost + Waxing Claw (Front Right)", "Front Right");
	private final ModifiableCallout<AbilityCastStart> midnightFrostBackLeft = ModifiableCallout.durationBasedCall("Midnight Frost + Waxing Claw (Back Left)", "Back Left");
	private final ModifiableCallout<AbilityCastStart> midnightFrostBackRight = ModifiableCallout.durationBasedCall("Midnight Frost + Waxing Claw (Back Right)", "Back Right");

	// 犬が付いているときの冷波は対象プレイヤー基準の向きになるので、前後左右で言う
	@AutoFeed
	private final SequentialTrigger<BaseEvent> midnightFrostAttachedSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x7BD7, 0x7BD8, 0x7BD9, 0x7BDA),
			(e1, s) -> {
				// ウェイニングクロウと同時に詠唱されるので少し待つ
				s.waitMs(COLLECT_MS);
				AbilityCastStart claw = waxingClaw;
				if (claw == null || claw.getEffectiveTimeSince().compareTo(WAXING_CLAW_WINDOW) > 0) {
					return;
				}
				boolean frontCleave = e1.abilityIdMatches(0x7BD7, 0x7BD8);
				boolean rightClaw = claw.abilityIdMatches(0x7BE0);
				ModifiableCallout<AbilityCastStart> call;
				if (frontCleave) {
					call = rightClaw ? midnightFrostBackLeft : midnightFrostBackRight;
				}
				else {
					call = rightClaw ? midnightFrostFrontLeft : midnightFrostFrontRight;
				}
				s.updateCall(call, e1);
			});

	private final ModifiableCallout<HeadMarkerEvent> lunarKissOnYou = new ModifiableCallout<>("Lunar Kiss (On You)", "Tank Laser on You");
	private final ModifiableCallout<HeadMarkerEvent> lunarKiss = new ModifiableCallout<>("Lunar Kiss (Not on You)", "Avoid Tank Laser");
	private final RepeatSuppressor lunarKissSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void lunarKissOnYou(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x19C) && event.getTarget().isThePlayer()) {
			context.accept(lunarKissOnYou.getModified(event));
		}
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> lunarKissSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(0x19C) && lunarKissSupp.check(hm),
			(e1, s) -> {
				s.waitMs(COLLECT_MS);
				if (!busterOnPlayer) {
					s.updateCall(lunarKiss, e1);
				}
				busterOnPlayer = false;
			});

	@NpcCastCallout({0x7BC6, 0x7BE8, 0x7BE9, 0x7F0E, 0x7F0F, 0x7BDB, 0x7BDC})
	private final ModifiableCallout<AbilityCastStart> winterHalo = ModifiableCallout.durationBasedCall("Winter Halo", "In");

	@NpcCastCallout(value = 0x7BF4, onYou = true)
	private final ModifiableCallout<AbilityCastStart> keenMoonbeam = ModifiableCallout.durationBasedCall("Keen Moonbeam", "Spread");

	@NpcCastCallout(0x80FA)
	private final ModifiableCallout<AbilityCastStart> moonsetRays = ModifiableCallout.durationBasedCall("Moonset Rays", "Stack on {event.target}");

}
