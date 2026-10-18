package gg.xp.xivsupport.triggers.dtdungeon;

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
import gg.xp.xivsupport.events.actlines.events.RawAddCombatantEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.state.combatstate.ActiveCastRepository;
import gg.xp.xivsupport.events.state.combatstate.CastTracker;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SequentialTriggerController;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.ArenaPos;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/dungeon/meso-terminal.ts をもとに移植
@CalloutRepo(name = "The Meso Terminal", duty = KnownDuty.MesoTerminal)
public class MesoTerminal extends AutoChildEventHandler implements FilteredEventHandler {

	private static final double chirurgeonCenterX = 270;
	private static final double chirurgeonCenterY = 12;
	private static final long preservedTerrorNpcId = 18624;

	private XivState state;
	private ActiveCastRepository acr;

	private volatile long playerExecutionerId = -1;
	private volatile boolean seenFirstStorm;
	private volatile boolean seenFirstBombardment;

	public MesoTerminal(XivState state, ActiveCastRepository acr) {
		this.state = state;
		this.acr = acr;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.MesoTerminal);
	}

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		playerExecutionerId = -1;
		seenFirstStorm = false;
		seenFirstBombardment = false;
	}

	private Position castPos(SequentialTriggerController<?> s, AbilityCastStart cast) {
		Position pos = s.waitForCastLocation(cast).getPos();
		if (pos != null) {
			return pos;
		}
		return state.getLatestCombatantData(cast.getSource()).getPos();
	}

	private boolean playerCanCleanse() {
		return state.playerJobMatches(job -> job.isHealer() || job == Job.BRD);
	}

	// 1ボス目（Chirurgeon General）

	@NpcCastCallout(0xAB16)
	private final ModifiableCallout<AbilityCastStart> medicineField = ModifiableCallout.durationBasedCall("Medicine Field", "Raidwide");

	@NpcCastCallout(0xAB1A)
	private final ModifiableCallout<AbilityCastStart> biochemicalFront = ModifiableCallout.durationBasedCall("Biochemical Front", "Behind");

	@NpcCastCallout(0xAB17)
	private final ModifiableCallout<AbilityCastStart> concentratedDose = ModifiableCallout.durationBasedCall("Concentrated Dose", "Tankbuster on {event.target}");

	private final ModifiableCallout<AbilityCastStart> pungentAerosol = ModifiableCallout.durationBasedCall("Pungent Aerosol", "Knockback from {dir}");

	// 詠唱位置は中心から東西南北いずれかに10離れた地点になる
	private final ArenaPos chirurgeonArena = new ArenaPos(chirurgeonCenterX, chirurgeonCenterY, 5, 5);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> pungentAerosolSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xAB1F),
			(e1, s) -> {
				Position pos = castPos(s, e1);
				ArenaSector dir = pos == null ? ArenaSector.UNKNOWN : chirurgeonArena.forPosition(pos);
				if (!ArenaSector.cardinals.contains(dir)) {
					dir = ArenaSector.UNKNOWN;
				}
				s.setParam("dir", dir);
				s.updateCall(pungentAerosol, e1);
			});

	private final ModifiableCallout<AbilityCastStart> sterileSphere = ModifiableCallout.durationBasedCall("Sterile Sphere", "{safe}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> sterileSphereSq = SqtTemplates.sq(10_000,
			// AB1D が小さい方の円で、安置判定にはこちらの位置を使う
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xAB1D),
			(e1, s) -> {
				s.waitMs(1_000);
				List<Position> spots = new ArrayList<>();
				for (CastTracker ct : acr.getActiveCastsById(0xAB1D)) {
					Position pos = castPos(s, ct.getCast());
					if (pos != null) {
						spots.add(pos);
					}
				}
				if (spots.size() < 2) {
					return;
				}
				long x1 = Math.round(spots.get(0).x());
				long y1 = Math.round(spots.get(0).y());
				long x2 = Math.round(spots.get(1).x());
				long y2 = Math.round(spots.get(1).y());
				ArenaSector safe;
				// 2つとも同じ側に並ぶので、その反対側が安置
				if (x1 == x2) {
					safe = x1 < chirurgeonCenterX ? ArenaSector.EAST : ArenaSector.WEST;
				}
				else if (y1 == y2) {
					safe = y1 > chirurgeonCenterY ? ArenaSector.NORTH : ArenaSector.SOUTH;
				}
				else {
					safe = ArenaSector.UNKNOWN;
				}
				s.setParam("safe", safe);
				s.updateCall(sterileSphere, e1);
			});

	private final ModifiableCallout<BuffApplied> poison = new ModifiableCallout<BuffApplied>("Poison", "Cleanse {event.target}").autoIcon();

	@HandleEvents
	public void poison(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x838) && !event.isRefresh() && event.getTarget().isPc() && playerCanCleanse()) {
			context.accept(poison.getModified(event));
		}
	}

	// 2ボス目（Hooded Headsman）

	@HandleEvents
	public void shacklesOfFate(EventContext context, AbilityCastStart event) {
		// 処刑人は複数いて、自分に鎖をつけた個体の技だけ呼ぶ
		if (event.abilityIdMatches(0xAA3D) && event.getTarget().isThePlayer()) {
			playerExecutionerId = event.getSource().getId();
		}
	}

	private boolean fromMyExecutioner(XivCombatant source) {
		return source.getId() == playerExecutionerId;
	}

	@NpcCastCallout(0xAA3B)
	private final ModifiableCallout<AbilityCastStart> headSplittingRoar = ModifiableCallout.durationBasedCall("Head-splitting Roar", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> choppingBlock = ModifiableCallout.durationBasedCall("Chopping Block", "Out");
	private final ModifiableCallout<AbilityCastStart> executionWheel = ModifiableCallout.durationBasedCall("Execution Wheel", "In");
	private final ModifiableCallout<AbilityCastStart> willBreaker = ModifiableCallout.durationBasedCall("Will Breaker", "Interrupt {event.source}");
	private final ModifiableCallout<AbilityCastStart> relentlessTorment = ModifiableCallout.durationBasedCall("Relentless Torment", "Tankbuster on {event.target}");

	@HandleEvents
	public void executionerCasts(EventContext context, AbilityCastStart event) {
		if (!fromMyExecutioner(event.getSource())) {
			return;
		}
		if (event.abilityIdMatches(0xAA4B)) {
			context.accept(choppingBlock.getModified(event));
		}
		else if (event.abilityIdMatches(0xAA4C)) {
			context.accept(executionWheel.getModified(event));
		}
		else if (event.abilityIdMatches(0xAF38)) {
			if (state.playerJobMatches(Job::caresAboutInterrupt)) {
				context.accept(willBreaker.getModified(event));
			}
		}
		else if (event.abilityIdMatches(0xAA45)) {
			context.accept(relentlessTorment.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> flayingFlail = new ModifiableCallout<>("Flaying Flail", "Away from Flails");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> flayingFlailSq = SqtTemplates.sq(5_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0xAA47) && aue.isFirstTarget() && fromMyExecutioner(aue.getSource()),
			(e1, s) -> {
				// 鉄球の見た目が出揃うのを待つ
				s.waitMs(1_000);
				s.updateCall(flayingFlail, e1);
			});

	private final ModifiableCallout<RawAddCombatantEvent> hellmakerAdds = new ModifiableCallout<>("Hellmaker Adds", "Kill Adds");

	private final RepeatSuppressor hellmakerSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void hellmakerAdds(EventContext context, RawAddCombatantEvent event) {
		if (event.getEntity().npcIdMatches(0x48D2) && state.playerJobMatches(Job::isDps) && hellmakerSupp.check(event)) {
			context.accept(hellmakerAdds.getModified(event));
		}
	}

	private final ModifiableCallout<BuffApplied> deathPenalty = new ModifiableCallout<BuffApplied>("Death Penalty", "Cleanse {event.target}").autoIcon();

	@HandleEvents
	public void deathPenalty(EventContext context, BuffApplied event) {
		// 7.4 で死の宣告のIDが 11F2 から 1441 に変わったので両方見る
		if (event.buffIdMatches(0x11F2, 0x1441) && !event.isRefresh() && playerCanCleanse()) {
			context.accept(deathPenalty.getModified(event));
		}
	}

	// 3ボス目（Immortal Remains）

	@NpcCastCallout(0xAB31)
	private final ModifiableCallout<AbilityCastStart> recollection = ModifiableCallout.durationBasedCall("Recollection", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> memoryOfTheStorm = ModifiableCallout.durationBasedCall("Memory of the Storm", "Stack");

	@HandleEvents
	public void memoryOfTheStorm(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0xAB2D)) {
			context.accept(memoryOfTheStorm.getModified(event));
			seenFirstStorm = true;
		}
	}

	@NpcCastCallout(0xAB30)
	private final ModifiableCallout<AbilityCastStart> memoryOfThePyre = ModifiableCallout.durationBasedCall("Memory of the Pyre", "Tankbuster on {event.target}");

	@NpcAbilityUsedCallout(value = 0xAB26, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> turmoilLeft = new ModifiableCallout<>("Turmoil (East Safe)", "Go East");

	@NpcAbilityUsedCallout(value = 0xAB27, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> turmoilRight = new ModifiableCallout<>("Turmoil (West Safe)", "Go West");

	// 北の角のどちらかに雑魚が1体だけ置かれ、その反対側に5体が囲む形で置かれる
	private XivCombatant findNorthTerror() {
		return state.npcsById(preservedTerrorNpcId).stream()
				.map(cbt -> state.getLatestCombatantData(cbt))
				.filter(cbt -> {
					Position pos = cbt.getPos();
					if (pos == null) {
						return false;
					}
					long x = Math.round(Math.abs(pos.x()));
					long y = Math.round(pos.y());
					return (x == 10 && y == -13) || (x == 17 && y == -12);
				})
				.findFirst()
				.orElse(null);
	}

	private final ModifiableCallout<BuffApplied> bombardmentStatic = new ModifiableCallout<>("Bombardment (First)", "Go {safe}, Avoid Small AoE");
	private final ModifiableCallout<BuffApplied> bombardmentStaticUnknown = new ModifiableCallout<>("Bombardment (First, Unknown)", "Avoid Small AoE");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> bombardmentStaticSq = SqtTemplates.sq(10_000,
			// 最初の爆撃だけは前兆の詠唱が無いので、線のバフと直前のメモリーストームで判断する
			BuffApplied.class, ba -> ba.buffIdMatches(0x9F8) && seenFirstStorm && !seenFirstBombardment,
			(e1, s) -> {
				seenFirstBombardment = true;
				s.waitThenRefreshCombatants(1_000);
				XivCombatant terror = findNorthTerror();
				if (terror == null) {
					s.updateCall(bombardmentStaticUnknown, e1);
					return;
				}
				s.setParam("safe", Objects.requireNonNull(terror.getPos()).x() < 0 ? ArenaSector.WEST : ArenaSector.EAST);
				s.updateCall(bombardmentStatic, e1);
			});

	private final ModifiableCallout<AbilityUsedEvent> bombardmentKnockback = new ModifiableCallout<>("Bombardment (Knockback)", "Knockback to {safe}, Avoid AoE");
	private final ModifiableCallout<AbilityUsedEvent> bombardmentKnockbackUnknown = new ModifiableCallout<>("Bombardment (Knockback, Unknown)", "Knockback, Avoid AoE");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> bombardmentKnockbackSq = SqtTemplates.sq(10_000,
			// 0xAB29 は Impression（ノックバック）の発動
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0xAB29) && aue.isFirstTarget() && seenFirstBombardment,
			(e1, s) -> {
				s.waitThenRefreshCombatants(1_000);
				XivCombatant terror = findNorthTerror();
				if (terror == null) {
					s.updateCall(bombardmentKnockbackUnknown, e1);
					return;
				}
				s.setParam("safe", Objects.requireNonNull(terror.getPos()).x() < 0 ? ArenaSector.NORTHWEST : ArenaSector.NORTHEAST);
				s.updateCall(bombardmentKnockback, e1);
			});

	// 北を0として時計回りに8方向へ丸める
	private static int headingTo8DirNum(double heading) {
		return (int) ((Math.round(4 - 4 * heading / Math.PI) % 8 + 8) % 8);
	}

	private final ModifiableCallout<AbilityCastStart> keraunography = ModifiableCallout.durationBasedCall("Keraunography", "{safe}");
	private final ModifiableCallout<AbilityCastStart> keraunographyLeanLeft = ModifiableCallout.durationBasedCall("Keraunography (Lean Left)", "Front and Lean Left, or Southwest");
	private final ModifiableCallout<AbilityCastStart> keraunographyLeanRight = ModifiableCallout.durationBasedCall("Keraunography (Lean Right)", "Front and Lean Right, or Southeast");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> keraunographySq = SqtTemplates.sq(10_000,
			// 雷光は必ず斜め1本と縦横1本の組み合わせになる
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xAB25),
			(e1, s) -> {
				s.waitMs(100);
				List<ArenaSector> diagSafe = List.of();
				Boolean orthoNorth = null;
				Boolean orthoSouth = null;
				for (CastTracker ct : acr.getActiveCastsById(0xAB25)) {
					AbilityCastStart cast = ct.getCast();
					var loc = s.waitForCastLocation(cast);
					int dirNum = headingTo8DirNum(loc.getBestHeading());
					if (dirNum % 2 == 1) {
						// 斜めは常に北側から出るので、実際に見えるのは 3 と 5 だけ
						boolean diagIsPositive = (dirNum + 4) % 4 == 1;
						diagSafe = diagIsPositive
								? List.of(ArenaSector.NORTHWEST, ArenaSector.SOUTHEAST)
								: List.of(ArenaSector.SOUTHWEST, ArenaSector.NORTHEAST);
					}
					else if (dirNum % 4 == 0) {
						orthoNorth = false;
						orthoSouth = false;
					}
					else {
						Position pos = castPos(s, cast);
						boolean isNorth = pos != null && pos.y() < 0;
						orthoNorth = isNorth;
						orthoSouth = !isNorth;
					}
				}
				if (orthoNorth == null) {
					return;
				}
				if (orthoNorth || orthoSouth) {
					// 横が北なら安置は南側の斜めの外、横が南なら前方がまるごと空く
					List<ArenaSector> candidates = orthoNorth
							? List.of(ArenaSector.SOUTHWEST, ArenaSector.SOUTHEAST)
							: List.of(ArenaSector.NORTHWEST, ArenaSector.NORTHEAST);
					ArenaSector safe = diagSafe.stream().filter(candidates::contains).findFirst().orElse(ArenaSector.UNKNOWN);
					s.setParam("safe", safe);
					s.updateCall(keraunography, e1);
				}
				else if (diagSafe.contains(ArenaSector.SOUTHWEST)) {
					s.updateCall(keraunographyLeanLeft, e1);
				}
				else {
					s.updateCall(keraunographyLeanRight, e1);
				}
			});

}
