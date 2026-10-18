package gg.xp.xivsupport.triggers.dtdungeon;

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
import gg.xp.xivsupport.events.actlines.events.CastLocationDataEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/dungeon/origenics.ts をもとに移植
@CalloutRepo(name = "Origenics", duty = KnownDuty.Origenics)
public class Origenics extends AutoChildEventHandler implements FilteredEventHandler {

	private static final double DECEIVER_CENTER_X = -172;

	private XivState state;

	public Origenics(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.Origenics);
	}

	private enum HerpeDir {
		CLEAVE_RIGHT("Left"),
		CLEAVE_LEFT("Right"),
		CLEAVE_REAR("Avoid Rear");

		private final String safe;

		HerpeDir(String safe) {
			this.safe = safe;
		}

		static @Nullable HerpeDir forAbility(long id) {
			return switch ((int) id) {
				case 0x8E71 -> CLEAVE_RIGHT;
				case 0x8E72 -> CLEAVE_LEFT;
				case 0x8E73 -> CLEAVE_REAR;
				default -> null;
			};
		}
	}

	private enum Turret {
		FAR_NORTH,
		MIDDLE_NORTH,
		MIDDLE_SOUTH,
		FAR_SOUTH;

		boolean isMiddle() {
			return this == MIDDLE_NORTH || this == MIDDLE_SOUTH;
		}
	}

	private final List<HerpeDir> herpeSweeps = new ArrayList<>();
	private volatile boolean seenFirstDroids;
	private volatile @Nullable ArenaSector nextTurretSide;
	private final List<Turret> turretSafeEast = new ArrayList<>();
	private final List<Turret> turretSafeWest = new ArrayList<>();
	private final List<ArenaSector> psychokinesisSafe = new ArrayList<>();
	private volatile boolean seenFirstCages;
	private volatile boolean seenFirstPsychokineticCharge;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		herpeSweeps.clear();
		seenFirstDroids = false;
		clearTurrets();
		resetPsychokinesis();
		seenFirstCages = false;
		seenFirstPsychokineticCharge = false;
	}

	private void clearTurrets() {
		nextTurretSide = null;
		turretSafeEast.clear();
		turretSafeWest.clear();
	}

	private void resetPsychokinesis() {
		psychokinesisSafe.clear();
		psychokinesisSafe.addAll(List.of(ArenaSector.NORTH, ArenaSector.CENTER, ArenaSector.SOUTH));
	}

	// ヘルペカリス

	@NpcCastCallout(0x8EA7)
	private final ModifiableCallout<AbilityCastStart> stridentShriek = ModifiableCallout.durationBasedCall("Strident Shriek", "Raidwide");

	@NpcCastCallout(0x8E79)
	private final ModifiableCallout<AbilityCastStart> collectiveAgony = ModifiableCallout.durationBasedCall("Collective Agony", "Stack on {event.target}");

	@NpcCastCallout(0x8EA6)
	private final ModifiableCallout<AbilityCastStart> convulsiveCrush = ModifiableCallout.durationBasedCall("Convulsive Crush", "Buster on {event.target}");

	private final ModifiableCallout<AbilityCastStart> poisonHeart = ModifiableCallout.durationBasedCall("Poison Heart", "Spread");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> poisonHeartSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x9421) && acs.getTarget().isThePlayer(),
			(e1, s) -> {
				// 詠唱が長いので、少し遅らせて散開を促す
				s.waitMs(3_000);
				s.updateCall(poisonHeart, e1);
			});

	// 924B/8E64 は初回、8E66/8E65 は続く詠唱
	@NpcCastCallout({0x924B, 0x8E66})
	private final ModifiableCallout<AbilityCastStart> venomspillRight = ModifiableCallout.durationBasedCall("Venomspill (Right)", "Left");

	@NpcCastCallout({0x8E64, 0x8E65})
	private final ModifiableCallout<AbilityCastStart> venomspillLeft = ModifiableCallout.durationBasedCall("Venomspill (Left)", "Right");

	private final ModifiableCallout<AbilityUsedEvent> sweepsEarly = new ModifiableCallout<>("Sweeps: First", "{safe}, Avoid Front", 7_000);
	private final ModifiableCallout<AbilityUsedEvent> sweepsEarlyRear = new ModifiableCallout<>("Sweeps: First (Rear Cleave)", "Front/Sides", 7_000);
	private final ModifiableCallout<AbilityUsedEvent> sweepsFollowup = new ModifiableCallout<>("Sweeps: Followup", "{safe}, Avoid Rear", 4_500);
	private final ModifiableCallout<AbilityUsedEvent> sweepsFollowupLeftRight = new ModifiableCallout<>("Sweeps: Followup (Left then Right)", "Left then Right, Avoid Rear", 4_500);
	private final ModifiableCallout<AbilityUsedEvent> sweepsFollowupRightLeft = new ModifiableCallout<>("Sweeps: Followup (Right then Left)", "Right then Left, Avoid Rear", 4_500);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> sweepsSq = SqtTemplates.sq(15_000,
			AbilityUsedEvent.class, aue -> HerpeDir.forAbility(aue.getAbility().getId()) != null,
			(e1, s) -> {
				HerpeDir first = HerpeDir.forAbility(e1.getAbility().getId());
				if (first == HerpeDir.CLEAVE_REAR) {
					s.updateCall(sweepsEarlyRear, e1);
				}
				else if (first != null) {
					s.setParam("safe", first.safe);
					s.updateCall(sweepsEarly, e1);
				}
				// 最初の攻撃が確定してから次の指示に差し替える
				s.waitMs(7_100);
				List<HerpeDir> sweeps;
				synchronized (herpeSweeps) {
					sweeps = List.copyOf(herpeSweeps);
				}
				if (sweeps.size() >= 3) {
					HerpeDir second = sweeps.get(1);
					HerpeDir third = sweeps.get(2);
					// 後方攻撃が挟まる場合は左右の安置だけ伝えれば自然に動ける
					if (second == HerpeDir.CLEAVE_REAR) {
						s.setParam("safe", third.safe);
						s.updateCall(sweepsFollowup, e1);
					}
					else if (third == HerpeDir.CLEAVE_REAR) {
						s.setParam("safe", second.safe);
						s.updateCall(sweepsFollowup, e1);
					}
					else if (second == HerpeDir.CLEAVE_LEFT) {
						s.updateCall(sweepsFollowupRightLeft, e1);
					}
					else {
						s.updateCall(sweepsFollowupLeftRight, e1);
					}
				}
				// 後続の攻撃で再発火しないよう待つ
				s.waitMs(2_900);
			});

	private final ModifiableCallout<AbilityCastStart> sweepsSequence = new ModifiableCallout<>("Sweeps: Sequence", "{first}, {second}, {third}", 8_000);

	@HandleEvents
	public void sweepsCollect(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8EA7)) {
			// 鳴き声は連続攻撃の後に来るので、ここで次に備えて片付ける
			synchronized (herpeSweeps) {
				herpeSweeps.clear();
			}
			return;
		}
		HerpeDir dir = HerpeDir.forAbility(event.getAbility().getId());
		if (dir == null) {
			return;
		}
		List<HerpeDir> sweeps;
		synchronized (herpeSweeps) {
			herpeSweeps.add(dir);
			if (herpeSweeps.size() != 3) {
				return;
			}
			sweeps = List.copyOf(herpeSweeps);
		}
		context.accept(sweepsSequence.getModified(event, Map.of(
				"first", sweeps.get(0).safe,
				"second", sweeps.get(1).safe,
				"third", sweeps.get(2).safe)));
	}

	// ディシーバー

	@NpcCastCallout(0x8E13)
	private final ModifiableCallout<AbilityCastStart> electrowave = ModifiableCallout.durationBasedCall("Electrowave", "Raidwide");

	// 8E10 は左前と右後ろ、8E11 は右前と左後ろを薙ぎ払う
	private final ModifiableCallout<AbilityCastStart> bionicThrashFrontRight = ModifiableCallout.durationBasedCall("Bionic Thrash (Front Right Safe)", "Back Left / Front Right");
	private final ModifiableCallout<AbilityCastStart> bionicThrashFrontLeft = ModifiableCallout.durationBasedCall("Bionic Thrash (Front Left Safe)", "Back Right / Front Left");
	private final ModifiableCallout<AbilityCastStart> bionicThrashFrontRightDroids = ModifiableCallout.durationBasedCall("Bionic Thrash + Synchroshot (Front Right Safe)", "Back Left / Front Right, Avoid Droid Cleaves");
	private final ModifiableCallout<AbilityCastStart> bionicThrashFrontLeftDroids = ModifiableCallout.durationBasedCall("Bionic Thrash + Synchroshot (Front Left Safe)", "Back Right / Front Left, Avoid Droid Cleaves");

	@HandleEvents
	public void bionicThrash(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x8E10, 0x8E11)) {
			return;
		}
		boolean frontRight = event.abilityIdMatches(0x8E10);
		ModifiableCallout<AbilityCastStart> call;
		if (seenFirstDroids) {
			call = frontRight ? bionicThrashFrontRightDroids : bionicThrashFrontLeftDroids;
		}
		else {
			call = frontRight ? bionicThrashFrontRight : bionicThrashFrontLeft;
		}
		context.accept(call.getModified(event));
	}

	private final ModifiableCallout<AbilityCastStart> synchroshot = ModifiableCallout.durationBasedCall("Synchroshot (First)", "Stand in Line with Flickering Droid");
	private final RepeatSuppressor synchroshotSupp = new RepeatSuppressor(Duration.ofSeconds(2));

	@HandleEvents
	public void synchroshot(EventContext context, AbilityCastStart event) {
		// 8E15 は見せかけの方。2 回目以降はバイオニックスラッシュと合わせて伝える
		if (event.abilityIdMatches(0x8E15) && !seenFirstDroids && synchroshotSupp.check(event)) {
			context.accept(synchroshot.getModified(event));
			seenFirstDroids = true;
		}
	}

	@HandleEvents
	public void fakeTurretCollect(EventContext context, CastLocationDataEvent event) {
		// 本物の砲台は 830D、点滅する偽物が 8E4A を使う。通常の詠唱ログは位置が古いことがあるので 263 行を使う
		if (!event.abilityIdMatches(0x8E4A)) {
			return;
		}
		Position pos = event.getPos();
		if (pos == null) {
			return;
		}
		ArenaSector side = pos.x() < DECEIVER_CENTER_X ? ArenaSector.WEST : ArenaSector.EAST;
		nextTurretSide = side;
		// y は北から -157, -147, -137, -127
		long y = Math.round(pos.y());
		Turret turret;
		if (y < -155) {
			turret = Turret.FAR_NORTH;
		}
		else if (y < -145) {
			turret = Turret.MIDDLE_NORTH;
		}
		else if (y < -135) {
			turret = Turret.MIDDLE_SOUTH;
		}
		else {
			turret = Turret.FAR_SOUTH;
		}
		List<Turret> list = side == ArenaSector.WEST ? turretSafeWest : turretSafeEast;
		synchronized (list) {
			list.add(turret);
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> laserLashMiddle = new ModifiableCallout<>("Laser Lash (Middle Lanes)", "Middle Lanes ({side} Turrets)");
	private final ModifiableCallout<AbilityUsedEvent> laserLashFar = new ModifiableCallout<>("Laser Lash (Far Lanes)", "Far North/South Lanes ({side} Turrets)");
	private final ModifiableCallout<AbilityUsedEvent> laserLashMiddleNorth = new ModifiableCallout<>("Laser Lash (Middle North Lane)", "Middle North Lane ({side} Turrets)");
	private final ModifiableCallout<AbilityUsedEvent> laserLashMiddleSouth = new ModifiableCallout<>("Laser Lash (Middle South Lane)", "Middle South Lane ({side} Turrets)");
	private final ModifiableCallout<AbilityUsedEvent> laserLashUnknown = new ModifiableCallout<>("Laser Lash (Unknown)", "Stand in Line with Flickering Turrets");
	private final RepeatSuppressor laserLashSupp = new RepeatSuppressor(Duration.ofSeconds(2));

	@HandleEvents
	public void laserLash(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x8E4A) || event.getSource().isPc() || !laserLashSupp.check(event)) {
			return;
		}
		ArenaSector side = nextTurretSide;
		if (side == null) {
			context.accept(laserLashUnknown.getModified(event));
			return;
		}
		List<Turret> list = side == ArenaSector.WEST ? turretSafeWest : turretSafeEast;
		List<Turret> safe;
		synchronized (list) {
			safe = List.copyOf(list);
		}
		if (safe.size() != 2) {
			context.accept(laserLashUnknown.getModified(event));
			return;
		}
		Turret safe0 = safe.get(0);
		Turret safe1 = safe.get(1);
		ModifiableCallout<AbilityUsedEvent> call;
		// ボスが中央にいるので、中央寄りの列を優先して伝える
		if (safe0.isMiddle() && safe1.isMiddle()) {
			call = laserLashMiddle;
		}
		else if (safe0.isMiddle() || safe1.isMiddle()) {
			Turret middle = safe0.isMiddle() ? safe0 : safe1;
			call = middle == Turret.MIDDLE_NORTH ? laserLashMiddleNorth : laserLashMiddleSouth;
		}
		else {
			call = laserLashFar;
		}
		context.accept(call.getModified(event, Map.of("side", side)));
	}

	private final ModifiableCallout<AbilityCastStart> surge = ModifiableCallout.durationBasedCall("Surge", "Knockback into Real Turret, then Spread");
	private final ModifiableCallout<AbilityCastStart> surgeFarNorthRight = new ModifiableCallout<>("Surge (Far North Lane, Right)", "Far North Lane: Knockback Right", 6_000);
	private final ModifiableCallout<AbilityCastStart> surgeFarNorthLeft = new ModifiableCallout<>("Surge (Far North Lane, Left)", "Far North Lane: Knockback Left", 6_000);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> surgeSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x8E0F),
			(e1, s) -> {
				s.updateCall(surge, e1);
				s.waitMs(2_500);
				// 偽物の記録から逆算する。最北の列は必ず片方が本物なので、ボスから見た左右で伝える
				boolean eastFake;
				boolean westFake;
				synchronized (turretSafeEast) {
					eastFake = turretSafeEast.contains(Turret.FAR_NORTH);
				}
				synchronized (turretSafeWest) {
					westFake = turretSafeWest.contains(Turret.FAR_NORTH);
				}
				if (!eastFake) {
					s.call(surgeFarNorthRight, e1);
				}
				else if (!westFake) {
					s.call(surgeFarNorthLeft, e1);
				}
			});

	@HandleEvents
	public void turretCleanup(EventContext context, AbilityUsedEvent event) {
		// サージの発動時に片付けないと、最北列のコールに使う情報が先に消えてしまう
		if (event.abilityIdMatches(0x8E0F)) {
			synchronized (turretSafeEast) {
				synchronized (turretSafeWest) {
					clearTurrets();
				}
			}
		}
	}

	// アンブローズ

	@NpcCastCallout(0x8E54)
	private final ModifiableCallout<AbilityCastStart> psychicWave = ModifiableCallout.durationBasedCall("Psychic Wave", "Raidwide");

	// サイコキネティックチャージと組み合わさるものは別 ID
	@NpcCastCallout(0x9941)
	private final ModifiableCallout<AbilityCastStart> overwhelmingCharge = ModifiableCallout.durationBasedCall("Overwhelming Charge", "Behind");

	@NpcCastCallout(0x8E55)
	private final ModifiableCallout<AbilityCastStart> voltaicSlash = ModifiableCallout.durationBasedCall("Voltaic Slash", "Buster on {event.target}");

	private final ModifiableCallout<AbilityCastStart> psychokinesisFirst = ModifiableCallout.durationBasedCall("Psychokinesis (First)", "{safe}");
	private final ModifiableCallout<AbilityCastStart> psychokinesis = ModifiableCallout.durationBasedCall("Psychokinesis", "{safe}, then Spread");

	@HandleEvents
	public void psychokinesis(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x8E4C)) {
			return;
		}
		// 見えない雑魚が 2 本ずつ直線攻撃を出す。東西は関係なく y 座標だけ見る（北から -13, 0, 13）
		Position pos = state.getLatestCombatantData(event.getSource()).getPos();
		if (pos == null) {
			return;
		}
		long y = Math.round(pos.y());
		ArenaSector lane = y < 0 ? ArenaSector.NORTH : (y > 0 ? ArenaSector.SOUTH : ArenaSector.CENTER);
		ArenaSector safe;
		synchronized (psychokinesisSafe) {
			psychokinesisSafe.remove(lane);
			if (psychokinesisSafe.size() != 1) {
				return;
			}
			safe = psychokinesisSafe.get(0);
			resetPsychokinesis();
		}
		context.accept((seenFirstCages ? psychokinesis : psychokinesisFirst).getModified(event, Map.of("safe", safe)));
		seenFirstCages = true;
	}

	@NpcCastCallout(0x8E50)
	private final ModifiableCallout<AbilityCastStart> extrasensoryField = ModifiableCallout.durationBasedCall("Extrasensory Field", "Knockback North/South");

	private final ModifiableCallout<AbilityCastStart> psychokineticChargeFirst = ModifiableCallout.durationBasedCall("Psychokinetic Charge (First)", "Knockback Behind Boss");
	private final ModifiableCallout<AbilityCastStart> psychokineticCharge = ModifiableCallout.durationBasedCall("Psychokinetic Charge", "Knockback Behind Boss, then Spread");

	@HandleEvents
	public void psychokineticCharge(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x988F)) {
			context.accept((seenFirstPsychokineticCharge ? psychokineticCharge : psychokineticChargeFirst).getModified(event));
			seenFirstPsychokineticCharge = true;
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> electrolance = new ModifiableCallout<>("Electrolance", "Avoid Lance Zig-Zag", 8_000);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> electrolanceSq = SqtTemplates.sq(10_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x8E4D),
			(e1, s) -> {
				s.waitMs(5_000);
				s.updateCall(electrolance, e1);
			});

	private final ModifiableCallout<AbilityUsedEvent> electrolanceAssimilation = new ModifiableCallout<>("Electrolance Assimilation", "Sides");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> electrolanceAssimilationSq = SqtTemplates.sq(15_000,
			// 最初の突進（982A）を基準にタイミングを合わせる
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x982A),
			(e1, s) -> {
				s.waitMs(4_500);
				s.updateCall(electrolanceAssimilation, e1);
				s.waitMs(5_500);
			});
}
