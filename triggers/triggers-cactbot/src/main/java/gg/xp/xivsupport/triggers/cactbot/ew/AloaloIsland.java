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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.RawAddCombatantEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/dungeon/aloalo_island.ts をもとに移植
@CalloutRepo(name = "Aloalo Island", duty = KnownDuty.AloaloIsland)
public class AloaloIsland extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long ZARATAN_NAME_ID = 12539;
	private static final long ZARATAN_NPC_ID = 16538;
	private static final long OGREBON_NAME_ID = 12540;

	private XivState state;

	public AloaloIsland(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AloaloIsland);
	}

	private enum Dir {
		FRONT, BACK, LEFT, RIGHT
	}

	// ララの回転マーカーがあると、行進や視線の向きが90度回る
	private enum Rotation {
		CW, CCW
	}

	private int quaArmamentsCount;
	private boolean ketuFight;
	private Dir lalaArcaneBlight;
	private Rotation lalaRotate;
	private final List<Integer> reloadFailed = new ArrayList<>();
	private int reloadCount;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		quaArmamentsCount = 0;
		ketuFight = false;
		lalaArcaneBlight = null;
		lalaRotate = null;
		reloadFailed.clear();
		reloadCount = 0;
	}

	private static Dir rotate(Dir dir, Rotation rotation) {
		if (rotation == null) {
			return dir;
		}
		if (rotation == Rotation.CW) {
			return switch (dir) {
				case FRONT -> Dir.RIGHT;
				case BACK -> Dir.LEFT;
				case LEFT -> Dir.FRONT;
				case RIGHT -> Dir.BACK;
			};
		}
		return switch (dir) {
			case FRONT -> Dir.LEFT;
			case BACK -> Dir.RIGHT;
			case LEFT -> Dir.BACK;
			case RIGHT -> Dir.FRONT;
		};
	}

	// 道中

	@NpcCastCallout(0x8869)
	private final ModifiableCallout<AbilityCastStart> soundwave = ModifiableCallout.durationBasedCall("Soundwave", "Raidwide");

	// クァクァ

	@NpcCastCallout(0x8B94)
	private final ModifiableCallout<AbilityCastStart> madeMagic = ModifiableCallout.durationBasedCall("Made Magic", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> armamentsFirst = ModifiableCallout.durationBasedCall("Arcane Armaments (First)", "Away from Orbs");
	private final ModifiableCallout<AbilityCastStart> armamentsSecond = ModifiableCallout.durationBasedCall("Arcane Armaments (Second)", "Under Orbs");
	private final ModifiableCallout<AbilityCastStart> armamentsThird = ModifiableCallout.durationBasedCall("Arcane Armaments (Third)", "Under Donut Far from Axe");

	// 同じ詠唱で3回とも内容が違うので、回数で切り替える
	@HandleEvents
	public void arcaneArmaments(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8B88)) {
			quaArmamentsCount++;
			ModifiableCallout<AbilityCastStart> call = switch (quaArmamentsCount) {
				case 1 -> armamentsFirst;
				case 2 -> armamentsSecond;
				default -> armamentsThird;
			};
			context.accept(call.getModified(event));
		}
	}

	@NpcCastCallout(0x8B8C)
	private final ModifiableCallout<AbilityCastStart> armamentsKnockback = ModifiableCallout.durationBasedCall("Arcane Armaments (Knockback)", "3x Knockback");

	@NpcCastCallout(0x8B90)
	private final ModifiableCallout<AbilityCastStart> armamentsRout = ModifiableCallout.durationBasedCall("Arcane Armaments (Rout)", "4x Charge");

	@NpcCastCallout(0x8B9F)
	private final ModifiableCallout<AbilityCastStart> armamentsWaterSpear = ModifiableCallout.durationBasedCall("Arcane Armaments (Water Spear)", "Away from Spears");

	@NpcCastCallout(0x8BA3)
	private final ModifiableCallout<AbilityCastStart> armamentsPoisonSpear = ModifiableCallout.durationBasedCall("Arcane Armaments (Poison Spear)", "Avoid Spreading Puddles");

	@NpcCastCallout(0x8B95)
	private final ModifiableCallout<AbilityCastStart> violetStorm = ModifiableCallout.durationBasedCall("Violet Storm", "Get Behind");

	@NpcCastCallout(value = 0x8BAE, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> arcaneIntervention = ModifiableCallout.durationBasedCall("Arcane Intervention", "Look Away from Rings");

	// ケトゥドゥケ

	@NpcCastCallout(0x8A9F)
	private final ModifiableCallout<AbilityCastStart> encroachingTwintides = ModifiableCallout.durationBasedCall("Encroaching Twintides", "In, then Out");

	@NpcCastCallout(0x8A9D)
	private final ModifiableCallout<AbilityCastStart> recedingTwintides = ModifiableCallout.durationBasedCall("Receding Twintides", "Out, then In");

	@NpcCastCallout(0x8A84)
	private final ModifiableCallout<AbilityCastStart> flukeTyphoon = ModifiableCallout.durationBasedCall("Fluke Typhoon", "Bubbles Move 2 Tiles");

	@NpcCastCallout(0x8D12)
	private final ModifiableCallout<AbilityCastStart> tidalWave = ModifiableCallout.durationBasedCall("Tidal Wave", "Knockback => Get in Bubble");

	@NpcCastCallout(0x8AA3)
	private final ModifiableCallout<AbilityCastStart> hydroblast = ModifiableCallout.durationBasedCall("Hydroblast", "Buster on {event.target}");

	private final ModifiableCallout<AbilityCastStart> tidalRoar = ModifiableCallout.durationBasedCall("Tidal Roar", "Raidwide");
	private final ModifiableCallout<TetherEvent> waterIII = new ModifiableCallout<>("Water III", "Stretch Tether");
	private final ModifiableCallout<RawAddCombatantEvent> zaratanAdd = new ModifiableCallout<>("Zaratan Add", "Get Behind Non-Bubbled Zaratan");
	private final ModifiableCallout<RawAddCombatantEvent> ogrebonAdd = new ModifiableCallout<>("Ogrebon Add", "Get in Bubble");

	@HandleEvents
	public void tidalRoarCast(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8AA5)) {
			ketuFight = true;
			context.accept(tidalRoar.getModified(event));
		}
	}

	@HandleEvents
	public void waterIIITether(EventContext context, TetherEvent event) {
		// 線ID 1は汎用なので、ケトゥドゥケ戦の雑魚からプレイヤーへの線に絞る
		if (ketuFight && event.tetherIdMatches(0x1) && event.getTarget().isThePlayer() && !event.getSource().isPc()) {
			context.accept(waterIII.getModified(event));
		}
	}

	// 雑魚が出てからギミックの発動まで間があるので、少し待ってから知らせる
	@AutoFeed
	private final SequentialTrigger<BaseEvent> zaratanSq = SqtTemplates.sq(30_000,
			RawAddCombatantEvent.class, add -> ketuFight
					&& add.getEntity().getbNpcNameId() == ZARATAN_NAME_ID
					&& add.getEntity().getbNpcId() == ZARATAN_NPC_ID,
			(e1, s) -> {
				s.waitMs(16_000);
				s.updateCall(zaratanAdd, e1);
				// 複数体が同時に出るので、まとめて1回だけ知らせる
				s.waitMs(5_000);
			});

	@AutoFeed
	private final SequentialTrigger<BaseEvent> ogrebonSq = SqtTemplates.sq(30_000,
			RawAddCombatantEvent.class, add -> ketuFight && add.getEntity().getbNpcNameId() == OGREBON_NAME_ID,
			(e1, s) -> {
				s.waitMs(18_000);
				s.updateCall(ogrebonAdd, e1);
				s.waitMs(5_000);
			});

	// ララ

	@NpcCastCallout(0x887F)
	private final ModifiableCallout<AbilityCastStart> infernoTheorem = ModifiableCallout.durationBasedCall("Inferno Theorem", "Raidwide");

	@NpcCastCallout(0x8880)
	private final ModifiableCallout<AbilityCastStart> floralFigure = ModifiableCallout.durationBasedCall("Floral Figure", "Seed Adds => Donut AoEs");

	@NpcCastCallout(0x8882)
	private final ModifiableCallout<AbilityCastStart> faunalFigure = ModifiableCallout.durationBasedCall("Faunal Figure", "Armadillo Adds => Big AoEs");

	@NpcCastCallout(0x8884)
	private final ModifiableCallout<AbilityCastStart> constructiveFigure = ModifiableCallout.durationBasedCall("Constructive Figure", "Tree Adds => Line AoEs");

	@NpcCastCallout(0x887E)
	private final ModifiableCallout<AbilityCastStart> strategicStrike = ModifiableCallout.durationBasedCall("Strategic Strike", "Buster on {event.target}");

	private final ModifiableCallout<AbilityCastStart> arcaneBlightUnknown = ModifiableCallout.durationBasedCall("Arcane Blight (Unknown)", "Go to Safe Zone");
	private final ModifiableCallout<AbilityCastStart> arcaneBlightFront = ModifiableCallout.durationBasedCall("Arcane Blight (Front)", "Front");
	private final ModifiableCallout<AbilityCastStart> arcaneBlightBack = ModifiableCallout.durationBasedCall("Arcane Blight (Back)", "Back");
	private final ModifiableCallout<AbilityCastStart> arcaneBlightLeft = ModifiableCallout.durationBasedCall("Arcane Blight (Left)", "Left");
	private final ModifiableCallout<AbilityCastStart> arcaneBlightRight = ModifiableCallout.durationBasedCall("Arcane Blight (Right)", "Right");

	private final ModifiableCallout<BuffApplied> analysisFront = new ModifiableCallout<>("Analysis (Front Unseen)", "Face Towards Lala", 8_000);
	private final ModifiableCallout<BuffApplied> analysisBack = new ModifiableCallout<>("Analysis (Back Unseen)", "Look Away from Lala", 8_000);
	private final ModifiableCallout<BuffApplied> analysisLeft = new ModifiableCallout<>("Analysis (Left Unseen)", "Left Flank towards Lala", 8_000);
	private final ModifiableCallout<BuffApplied> analysisRight = new ModifiableCallout<>("Analysis (Right Unseen)", "Right Flank towards Lala", 8_000);
	private final RepeatSuppressor analysisSupp = new RepeatSuppressor(Duration.ofSeconds(12));

	private final ModifiableCallout<BuffApplied> marchFront = new ModifiableCallout<>("Calculated Trajectory (Forward)", "Forward March (1 Square)", 7_000);
	private final ModifiableCallout<BuffApplied> marchBack = new ModifiableCallout<>("Calculated Trajectory (Backward)", "Backward March (1 Square)", 7_000);
	private final ModifiableCallout<BuffApplied> marchLeft = new ModifiableCallout<>("Calculated Trajectory (Left)", "Left March (1 Square)", 7_000);
	private final ModifiableCallout<BuffApplied> marchRight = new ModifiableCallout<>("Calculated Trajectory (Right)", "Right March (1 Square)", 7_000);

	@HandleEvents
	public void lalaRotation(EventContext context, HeadMarkerEvent event) {
		// ララ本体への回転マーカーと、プレイヤーへの回転マーカーの両方がある
		if (event.markerIdMatches(0x1E4, 0x1E5) && !event.getTarget().isPc()) {
			lalaRotate = event.markerIdMatches(0x1E4) ? Rotation.CW : Rotation.CCW;
		}
		else if (event.markerIdMatches(0x1ED, 0x1EE) && event.getTarget().isThePlayer()) {
			lalaRotate = event.markerIdMatches(0x1ED) ? Rotation.CW : Rotation.CCW;
		}
	}

	@HandleEvents
	public void arcaneBlightOpen(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x886F)) {
			lalaArcaneBlight = Dir.RIGHT;
		}
		else if (event.abilityIdMatches(0x8870)) {
			lalaArcaneBlight = Dir.LEFT;
		}
		else if (event.abilityIdMatches(0x8871)) {
			lalaArcaneBlight = Dir.BACK;
		}
		else if (event.abilityIdMatches(0x8872)) {
			lalaArcaneBlight = Dir.FRONT;
		}
	}

	// 回転マーカーが付くのを少し待ってから判定する
	@AutoFeed
	private final SequentialTrigger<BaseEvent> arcaneBlightSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x8873),
			(e1, s) -> {
				s.waitMs(1_000);
				ModifiableCallout<AbilityCastStart> call;
				if (lalaArcaneBlight == null) {
					call = arcaneBlightUnknown;
				}
				else {
					call = switch (rotate(lalaArcaneBlight, lalaRotate)) {
						case FRONT -> arcaneBlightFront;
						case BACK -> arcaneBlightBack;
						case LEFT -> arcaneBlightLeft;
						case RIGHT -> arcaneBlightRight;
					};
				}
				lalaArcaneBlight = null;
				lalaRotate = null;
				s.updateCall(call, e1);
			});

	@AutoFeed
	private final SequentialTrigger<BaseEvent> analysisSq = SqtTemplates.sq(10_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0xE8E, 0xE8F, 0xE90, 0xE91)
					&& ba.getTarget().isThePlayer()
					&& analysisSupp.check(ba),
			(e1, s) -> {
				s.waitMs(500);
				Dir dir;
				if (e1.buffIdMatches(0xE8E)) {
					dir = Dir.FRONT;
				}
				else if (e1.buffIdMatches(0xE8F)) {
					dir = Dir.BACK;
				}
				else if (e1.buffIdMatches(0xE90)) {
					dir = Dir.RIGHT;
				}
				else {
					dir = Dir.LEFT;
				}
				ModifiableCallout<BuffApplied> call = switch (rotate(dir, lalaRotate)) {
					case FRONT -> analysisFront;
					case BACK -> analysisBack;
					case LEFT -> analysisLeft;
					case RIGHT -> analysisRight;
				};
				lalaRotate = null;
				s.updateCall(call, e1);
			});

	@AutoFeed
	private final SequentialTrigger<BaseEvent> calculatedTrajectorySq = SqtTemplates.sq(10_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0xE83, 0xE84, 0xE85, 0xE86) && ba.getTarget().isThePlayer(),
			(e1, s) -> {
				s.waitMs(3_000);
				Dir dir;
				if (e1.buffIdMatches(0xE83)) {
					dir = Dir.FRONT;
				}
				else if (e1.buffIdMatches(0xE84)) {
					dir = Dir.BACK;
				}
				else if (e1.buffIdMatches(0xE85)) {
					dir = Dir.LEFT;
				}
				else {
					dir = Dir.RIGHT;
				}
				ModifiableCallout<BuffApplied> call = switch (rotate(dir, lalaRotate)) {
					case FRONT -> marchFront;
					case BACK -> marchBack;
					case LEFT -> marchLeft;
					case RIGHT -> marchRight;
				};
				lalaRotate = null;
				s.updateCall(call, e1);
			});

	// スタティス

	@NpcCastCallout(0x892F)
	private final ModifiableCallout<AbilityCastStart> pop = ModifiableCallout.durationBasedCall("Pop", "Knockback");

	@NpcCastCallout(0x8929)
	private final ModifiableCallout<AbilityCastStart> aeroIV = ModifiableCallout.durationBasedCall("Aero IV", "Raidwide");

	@NpcCastCallout(0x8928)
	private final ModifiableCallout<AbilityCastStart> shockingAbandon = ModifiableCallout.durationBasedCall("Shocking Abandon", "Buster on {event.target}");

	@NpcCastCallout(0x8946)
	private final ModifiableCallout<AbilityCastStart> fairFlight = ModifiableCallout.durationBasedCall("Fair Flight", "Stand on Cushion");

	private final ModifiableCallout<AbilityCastStart> triggerHappy = ModifiableCallout.durationBasedCall("Trigger Happy", "Safe: {safe}");

	@HandleEvents
	public void staticeCasts(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x892A)) {
			reloadCount = 0;
			reloadFailed.clear();
		}
		else if (event.abilityIdMatches(0x892B)) {
			// 装填に失敗した弾の番号が安全な位置になる
			String safe = reloadFailed.stream().map(String::valueOf).collect(Collectors.joining(", "));
			context.accept(triggerHappy.getModified(event, Map.of("safe", safe)));
		}
	}

	@HandleEvents
	public void staticeReload(EventContext context, AbilityUsedEvent event) {
		if (!event.isFirstTarget()) {
			return;
		}
		if (event.abilityIdMatches(0x8925)) {
			reloadCount++;
		}
		else if (event.abilityIdMatches(0x8926)) {
			reloadCount++;
			reloadFailed.add(reloadCount);
		}
	}

	// ロクロクィ

	@NpcCastCallout(0x87BC)
	private final ModifiableCallout<AbilityCastStart> longLostLight = ModifiableCallout.durationBasedCall("Long-lost Light", "Raidwide");

	@NpcCastCallout(0x87C4)
	private final ModifiableCallout<AbilityCastStart> oLifeFlourish = ModifiableCallout.durationBasedCall("O Life, Flourish", "Glowing Adds Get Larger");

	@NpcCastCallout(0x87BE)
	private final ModifiableCallout<AbilityCastStart> protectiveWill = ModifiableCallout.durationBasedCall("Protective Will", "Buster on {event.target}");

	@NpcCastCallout(0x87C5)
	private final ModifiableCallout<AbilityCastStart> oPetalsUnfurl = ModifiableCallout.durationBasedCall("O Petals, Unfurl", "Away from Tether Ends");

	@NpcCastCallout(0x87BD)
	private final ModifiableCallout<AbilityCastStart> landWave = ModifiableCallout.durationBasedCall("Land Wave", "Get Behind");

	@NpcCastCallout(0x87C7)
	private final ModifiableCallout<AbilityCastStart> oIsleBloom = ModifiableCallout.durationBasedCall("O Isle, Bloom", "Last Bloom => Walk into Safe");

	@NpcCastCallout(0x87CA)
	private final ModifiableCallout<AbilityCastStart> sanctuary = ModifiableCallout.durationBasedCall("Sanctuary", "Out");

	@NpcCastCallout(0x87CB)
	private final ModifiableCallout<AbilityCastStart> stirringOfSpirits = ModifiableCallout.durationBasedCall("Stirring of Spirits", "5x Knockback");

}
