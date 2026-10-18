package gg.xp.xivsupport.triggers.dtex;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.*;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.MapEffectEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.state.combatstate.StatusEffectRepository;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/trial/queen-eternal-ex.ts をもとに移植
@CalloutRepo(name = "EX3", duty = KnownDuty.DtEx3)
public class DTEx3 extends AutoChildEventHandler implements FilteredEventHandler {

	private static final Logger log = LoggerFactory.getLogger(DTEx3.class);

	private static final double ARENA_CENTER = 100.0;

	private XivState state;
	private StatusEffectRepository buffs;

	public DTEx3(XivState state, StatusEffectRepository buffs) {
		this.state = state;
		this.buffs = buffs;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.DtEx3);
	}

	private enum Phase {
		P1,
		WIND,
		EARTH,
		ICE,
		P2
	}

	private enum GravitationalEmpireMech {
		TOWER,
		SPREAD,
		CONE
	}

	private enum Element {
		WIND,
		EARTH,
		ICE
	}

	private Phase phase = Phase.P1;
	private GravitationalEmpireMech gravitationalEmpireMech = GravitationalEmpireMech.TOWER;
	private @Nullable Boolean windKnockbackLeft;
	private @Nullable Element radicalShiftCwPlatform;
	private @Nullable Element radicalShiftCcwPlatform;

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		phase = Phase.P1;
		gravitationalEmpireMech = GravitationalEmpireMech.TOWER;
		windKnockbackLeft = null;
		radicalShiftCwPlatform = null;
		radicalShiftCcwPlatform = null;
	}

	@HandleEvents
	public void phaseTrackerCast(EventContext context, AbilityCastStart event) {
		switch ((int) event.getAbility().getId()) {
			case 0xA019 -> phase = Phase.WIND;
			case 0xA01A -> phase = Phase.EARTH;
			case 0xA01B -> phase = Phase.ICE;
			case 0xA01C -> phase = Phase.P1;
		}
	}

	@HandleEvents
	public void phaseTrackerAbility(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0xA04B)) {
			phase = Phase.P2;
		}
	}

	// 全般

	@NpcCastCallout({0x7692, 0xA01C})
	private final ModifiableCallout<AbilityCastStart> worldShatter = ModifiableCallout.durationBasedCall("World Shatter", "Raidwide");

	@NpcCastCallout(0xA00A)
	private final ModifiableCallout<AbilityCastStart> prosecutionOfWar = ModifiableCallout.durationBasedCall("Prosecution of War", "Buster on {event.target}, Swap");

	@NpcCastCallout(0xA04E)
	private final ModifiableCallout<AbilityCastStart> royalDomain = ModifiableCallout.durationBasedCall("Royal Domain", "Raidwide");

	@NpcCastCallout({0xA019, 0xA01A, 0xA01B})
	private final ModifiableCallout<AbilityCastStart> virtualShift = ModifiableCallout.durationBasedCall("Virtual Shift", "Big Raidwide");

	private final ModifiableCallout<AbilityCastStart> legitimateForceRightLeft = ModifiableCallout.durationBasedCall("Legitimate Force (Right First)", "Right then Left");
	private final ModifiableCallout<AbilityCastStart> legitimateForceLeftRight = ModifiableCallout.durationBasedCall("Legitimate Force (Left First)", "Left then Right");

	private final ModifiableCallout<AbilityCastStart> windLegitimateForceRightLeftKbLeft = ModifiableCallout.durationBasedCall("Wind Phase Legitimate Force (Right First, Knockback Left)", "Break Chains, Right then Left, Knockback Left");
	private final ModifiableCallout<AbilityCastStart> windLegitimateForceRightLeftKbRight = ModifiableCallout.durationBasedCall("Wind Phase Legitimate Force (Right First, Knockback Right)", "Break Chains, Right then Left, Knockback Right");
	private final ModifiableCallout<AbilityCastStart> windLegitimateForceRightLeftKbUnknown = ModifiableCallout.durationBasedCall("Wind Phase Legitimate Force (Right First, Unknown Knockback)", "Break Chains, Right then Left");
	private final ModifiableCallout<AbilityCastStart> windLegitimateForceLeftRightKbLeft = ModifiableCallout.durationBasedCall("Wind Phase Legitimate Force (Left First, Knockback Left)", "Break Chains, Left then Right, Knockback Left");
	private final ModifiableCallout<AbilityCastStart> windLegitimateForceLeftRightKbRight = ModifiableCallout.durationBasedCall("Wind Phase Legitimate Force (Left First, Knockback Right)", "Break Chains, Left then Right, Knockback Right");
	private final ModifiableCallout<AbilityCastStart> windLegitimateForceLeftRightKbUnknown = ModifiableCallout.durationBasedCall("Wind Phase Legitimate Force (Left First, Unknown Knockback)", "Break Chains, Left then Right");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> legitimateForceSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xA01E, 0xA020),
			(e1, s) -> {
				// A01E は右から、A020 は左から安置
				boolean rightFirst = e1.abilityIdMatches(0xA01E);
				switch (phase) {
					case P1, EARTH, ICE -> s.updateCall(rightFirst ? legitimateForceRightLeft : legitimateForceLeftRight, e1);
					case WIND -> {
						// ノックバック方向のデバフが付くのを少し待つ
						s.waitMs(500);
						Boolean kbLeft = windKnockbackLeft;
						ModifiableCallout<AbilityCastStart> call;
						if (kbLeft == null) {
							call = rightFirst ? windLegitimateForceRightLeftKbUnknown : windLegitimateForceLeftRightKbUnknown;
						}
						else if (kbLeft) {
							call = rightFirst ? windLegitimateForceRightLeftKbLeft : windLegitimateForceLeftRightKbLeft;
						}
						else {
							call = rightFirst ? windLegitimateForceRightLeftKbRight : windLegitimateForceLeftRightKbRight;
						}
						s.updateCall(call, e1);
					}
					default -> {
					}
				}
			});

	// 風フェーズ前

	private final ModifiableCallout<MapEffectEvent> aethertithe = new ModifiableCallout<>("Aethertithe: Safe Spots", "{safe1}/{safe2}, Healer Groups");

	// 風フェーズ

	@NpcCastCallout(value = 0xA025, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> aeroquell = ModifiableCallout.durationBasedCall("Aeroquell", "Healer Groups");

	@HandleEvents
	public void windKnockbackDebuff(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x105D, 0x105E) && event.getTarget().isThePlayer()) {
			windKnockbackLeft = event.buffIdMatches(0x105D);
		}
	}

	// 風フェーズ後

	@NpcCastCallout(0xA017)
	private final ModifiableCallout<AbilityCastStart> divideAndConquer = ModifiableCallout.durationBasedCall("Divide and Conquer", "Spread");

	// 土フェーズ

	@NpcAbilityUsedCallout(value = 0xA01A, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> earthPhaseUp = new ModifiableCallout<>("Earth Phase: Initial Up", "Up");

	private final ModifiableCallout<AbilityUsedEvent> earthFirstTowers = new ModifiableCallout<>("Earth Phase: First Towers", "Down, Soak Tower");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> earthFirstTowersSq = SqtTemplates.sq(30_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0xA028),
			(e1, s) -> {
				// 着地のタイミングに合わせる
				s.waitMs(14_300);
				s.updateCall(earthFirstTowers, e1);
			});

	private final ModifiableCallout<AbilityCastStart> gravityPillar = ModifiableCallout.durationBasedCall("Gravitational Empire: Pillar on You", "Spread");
	private final ModifiableCallout<TetherEvent> gravityRay = new ModifiableCallout<>("Gravitational Empire: Cone on You", "Cone on You");
	private final ModifiableCallout<AbilityCastStart> gravitationalEmpireTowers = ModifiableCallout.durationBasedCall("Gravitational Empire: Towers", "Down, Soak Tower");

	@HandleEvents
	public void gravityPillarCast(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0xA02C) && event.getTarget().isThePlayer()) {
			gravitationalEmpireMech = GravitationalEmpireMech.SPREAD;
			context.accept(gravityPillar.getModified(event));
		}
	}

	@HandleEvents
	public void gravityRayTether(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x11) && event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			gravitationalEmpireMech = GravitationalEmpireMech.CONE;
			context.accept(gravityRay.getModified(event));
		}
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> gravitationalEmpireTowersSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xA02B),
			(e1, s) -> {
				// 柱・扇の割り当てが先に届くので、それを待ってから判定する
				s.waitMs(500);
				if (gravitationalEmpireMech == GravitationalEmpireMech.TOWER) {
					s.updateCall(gravitationalEmpireTowers, e1);
				}
			});

	private final ModifiableCallout<HeadMarkerEvent> boulder = new ModifiableCallout<>("Earth Phase: Boulder", "Spread");
	private final RepeatSuppressor boulderSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void boulderMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x022F) && boulderSupp.check(event)) {
			context.accept(boulder.getModified(event));
		}
	}

	@NpcCastCallout(0xA033)
	private final ModifiableCallout<AbilityCastStart> weightyBlow = ModifiableCallout.durationBasedCall("Weighty Blow", "Hide Behind Rocks");

	// 土フェーズ後

	private final ModifiableCallout<TetherEvent> coronationLaser = new ModifiableCallout<>("Coronation: Laser", "{laserDir} Laser, {sideDir} Side, with {partner}");
	private final ModifiableCallout<TetherEvent> coronationLaserNoPartner = new ModifiableCallout<>("Coronation: Laser (Partner Unknown)", "{laserDir} Laser, {sideDir} Side");

	private static ArenaSector cardinalOf(Position pos) {
		double dx = pos.x() - ARENA_CENTER;
		double dy = pos.y() - ARENA_CENTER;
		if (Math.abs(dx) > Math.abs(dy)) {
			return dx > 0 ? ArenaSector.EAST : ArenaSector.WEST;
		}
		return dy > 0 ? ArenaSector.SOUTH : ArenaSector.NORTH;
	}

	private @Nullable ArenaSector laserDirFor(TetherEvent tether) {
		XivCombatant laser = tether.getTargetMatching(cbt -> !cbt.isPc());
		if (laser == null) {
			return null;
		}
		Position pos = state.getLatestCombatantData(laser).getPos();
		if (pos == null) {
			return null;
		}
		return cardinalOf(pos);
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> coronationSq = SqtTemplates.sq(30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xA013),
			(e1, s) -> {
				List<TetherEvent> tethers = s.waitEvents(8, TetherEvent.class, te -> te.tetherIdMatches(0x10E, 0x10F));
				s.waitThenRefreshCombatants(100);
				TetherEvent myTether = tethers.stream()
						.filter(te -> te.eitherTargetMatches(XivCombatant::isThePlayer))
						.findFirst()
						.orElse(null);
				if (myTether == null) {
					return;
				}
				ArenaSector laserDir = laserDirFor(myTether);
				if (laserDir == null) {
					log.warn("Coronation: laser position unknown: {}", myTether);
					return;
				}
				// 010E は左側、010F は右側の担当
				int sideOffset = myTether.tetherIdMatches(0x10E) ? -1 : 1;
				s.setParam("laserDir", laserDir);
				s.setParam("sideDir", laserDir.plusQuads(sideOffset));
				XivCombatant partner = tethers.stream()
						.filter(te -> te != myTether && laserDir == laserDirFor(te))
						.map(te -> te.getTargetMatching(XivCombatant::isPc))
						.findFirst()
						.orElse(null);
				if (partner != null) {
					s.setParam("partner", partner);
					s.updateCall(coronationLaser, myTether);
				}
				else {
					s.updateCall(coronationLaserNoPartner, myTether);
				}
			});

	@NpcCastCallout(0xA041)
	private final ModifiableCallout<AbilityCastStart> absoluteAuthority = ModifiableCallout.durationBasedCall("Absolute Authority", "Bait Puddles");

	private final ModifiableCallout<BuffApplied> absoluteAuthoritySpread = ModifiableCallout.<BuffApplied>durationBasedCall("Absolute Authority: Flare on You", "Flare Spread, then Dorito Stack").statusIcon(0x105A);
	private final ModifiableCallout<BuffApplied> absoluteAuthorityStack = ModifiableCallout.durationBasedCall("Absolute Authority: No Flare", "Stack, then Dorito Stack");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> absoluteAuthorityDebuffSq = SqtTemplates.sq(10_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0x105A),
			(e1, s) -> {
				// 全員分のデバフが付与されるのを待つ
				s.waitMs(1_000);
				BuffApplied myBuff = buffs.getBuffs().stream()
						.filter(ba -> ba.buffIdMatches(0x105A) && ba.getTarget().isThePlayer())
						.findFirst()
						.orElse(null);
				if (myBuff != null) {
					s.updateCall(absoluteAuthoritySpread, myBuff);
				}
				else {
					s.updateCall(absoluteAuthorityStack, e1);
				}
			});

	// 氷フェーズ

	private final ModifiableCallout<HeadMarkerEvent> iceMotion = new ModifiableCallout<>("Ice Phase: Motion Marker", "Keep Moving");
	private final RepeatSuppressor iceMotionSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void iceMotionMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x022A) && iceMotionSupp.check(event)) {
			context.accept(iceMotion.getModified(event));
		}
	}

	private final ModifiableCallout<TetherEvent> icicleEast = new ModifiableCallout<>("Ice Phase: Icicle Tether (East)", "Spread East, Stretch Tethers");
	private final ModifiableCallout<TetherEvent> icicleWest = new ModifiableCallout<>("Ice Phase: Icicle Tether (West)", "Spread West, Stretch Tethers");
	private final ModifiableCallout<TetherEvent> icicleUnknown = new ModifiableCallout<>("Ice Phase: Icicle Tether (Unknown)", "Spread, Stretch Tethers");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> icicleTetherSq = SqtTemplates.sq(10_000,
			TetherEvent.class, te -> te.tetherIdMatches(0x39) && te.eitherTargetMatches(XivCombatant::isThePlayer),
			(e1, s) -> {
				s.waitThenRefreshCombatants(100);
				XivCombatant icicle = e1.getTargetMatching(cbt -> !cbt.isPc());
				Position pos = icicle == null ? null : state.getLatestCombatantData(icicle).getPos();
				if (pos == null) {
					s.updateCall(icicleUnknown, e1);
				}
				// 氷柱と反対側に散開して線を伸ばす
				else if (pos.x() < ARENA_CENTER) {
					s.updateCall(icicleEast, e1);
				}
				else {
					s.updateCall(icicleWest, e1);
				}
			});

	// 後半

	private final ModifiableCallout<MapEffectEvent> rotationWind = new ModifiableCallout<>("Radical Shift: Wind Platform", "Wind/Green, then Spread");
	private final ModifiableCallout<MapEffectEvent> rotationEarth = new ModifiableCallout<>("Radical Shift: Earth Platform", "Earth/Yellow, then Spread");
	private final ModifiableCallout<MapEffectEvent> rotationIce = new ModifiableCallout<>("Radical Shift: Ice Platform", "Ice/Blue, then Spread");
	private final ModifiableCallout<MapEffectEvent> rotationUnknown = new ModifiableCallout<>("Radical Shift: Unknown Platform", "Spread");

	@HandleEvents
	public void mapEffects(EventContext context, MapEffectEvent event) {
		long location = event.getLocation();
		if (location == 0x00) {
			// 光ったレーンが危険なので、残り2つを安置としてコールする
			List<ArenaSector> safe = switch ((int) event.getFlags()) {
				case 0x04000100 -> List.of(ArenaSector.CENTER, ArenaSector.EAST);
				case 0x08000100 -> List.of(ArenaSector.WEST, ArenaSector.EAST);
				case 0x10000100 -> List.of(ArenaSector.WEST, ArenaSector.CENTER);
				default -> null;
			};
			if (safe != null) {
				context.accept(aethertithe.getModified(event, Map.<String, Object>of("safe1", safe.get(0), "safe2", safe.get(1))));
			}
		}
		else if (location == 0x09 || location == 0x0A || location == 0x0B) {
			Element element = switch ((int) location) {
				case 0x09 -> Element.WIND;
				case 0x0A -> Element.EARTH;
				default -> Element.ICE;
			};
			if (event.getFlags() == 0x00020001L) {
				radicalShiftCwPlatform = element;
			}
			else if (event.getFlags() == 0x00200010L) {
				radicalShiftCcwPlatform = element;
			}
		}
		else if (location == 0x0C) {
			Element element;
			if (event.getFlags() == 0x08000400L) {
				element = radicalShiftCwPlatform;
			}
			else if (event.getFlags() == 0x01000080L) {
				element = radicalShiftCcwPlatform;
			}
			else {
				return;
			}
			ModifiableCallout<MapEffectEvent> call;
			if (element == null) {
				call = rotationUnknown;
			}
			else {
				call = switch (element) {
					case WIND -> rotationWind;
					case EARTH -> rotationEarth;
					case ICE -> rotationIce;
				};
			}
			context.accept(call.getModified(event));
		}
	}

	@NpcCastCallout(0xA04F)
	private final ModifiableCallout<AbilityCastStart> radicalShift = ModifiableCallout.durationBasedCall("Radical Shift", "Big Raidwide");

	@NpcCastCallout(0xA059)
	private final ModifiableCallout<AbilityCastStart> dyingMemory = ModifiableCallout.durationBasedCall("Dying Memory", "Raidwide");

	@NpcCastCallout(0xA05A)
	private final ModifiableCallout<AbilityCastStart> royalBanishment = ModifiableCallout.durationBasedCall("Royal Banishment", "Raidwide");

	@NpcCastCallout(0xA055)
	private final ModifiableCallout<AbilityCastStart> tyrannysGrasp = ModifiableCallout.durationBasedCall("Tyranny's Grasp", "Back, Tank Towers, then Raidwide");

}
