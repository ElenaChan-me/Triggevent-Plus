package gg.xp.xivsupport.triggers.cactbot.shb;

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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.SystemLogMessageEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/alliance/the_copied_factory.ts をもとに移植
@CalloutRepo(name = "The Copied Factory", duty = KnownDuty.TheCopiedFactory)
public class TheCopiedFactory extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long LOG_RIGHT_ARM = 0x253D;
	private static final long LOG_FLAMETHROWERS = 0x253B;
	private static final long LOG_LEFT_ARM = 0x253C;
	private static final long LOG_ELECTRIC_FLOOR = 0x2538;
	private static final long LOG_CONVEYER_BELTS = 0x2539;
	private static final long LOG_OIL = 0x253A;

	private XivState state;

	public TheCopiedFactory(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheCopiedFactory);
	}

	// 飛行ユニットとエンゲルスで同じマーカーを使うので、直近に詠唱したボスで判別する
	private boolean engelsActive;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		engelsActive = false;
	}

	// 多関節型：司令機

	@NpcCastCallout(0x48CF)
	private final ModifiableCallout<AbilityCastStart> forcefulImpact = ModifiableCallout.durationBasedCall("Forceful Impact", "Raidwide");

	@NpcCastCallout(0x48B5)
	private final ModifiableCallout<AbilityCastStart> energyAssault = ModifiableCallout.durationBasedCall("Energy Assault", "Get Behind");

	@NpcCastCallout(value = 0x48FA, suppressMs = 15_000)
	private final ModifiableCallout<AbilityCastStart> highCaliberLaser = ModifiableCallout.durationBasedCall("High-Caliber Laser", "Look for Lasers");

	@NpcCastCallout(0x48CE)
	private final ModifiableCallout<AbilityCastStart> clangingBlow = ModifiableCallout.durationBasedCall("Clanging Blow", "Buster on {event.target}");

	@NpcCastCallout(0x48C8)
	private final ModifiableCallout<AbilityCastStart> centrifugalSpin = ModifiableCallout.durationBasedCall("Centrifugal Spin", "Sides");

	@NpcCastCallout(0x48CA)
	private final ModifiableCallout<AbilityCastStart> sidestrikingSpin = ModifiableCallout.durationBasedCall("Sidestriking Spin", "Front/Back");

	@NpcCastCallout(0x48C3)
	private final ModifiableCallout<AbilityCastStart> shockwave = ModifiableCallout.durationBasedCall("Shockwave", "Knockback");

	// ホッブス

	@NpcCastCallout(0x4805)
	private final ModifiableCallout<AbilityCastStart> laserResistanceTest = ModifiableCallout.durationBasedCall("Laser-Resistance Test", "Raidwide");

	private final ModifiableCallout<SystemLogMessageEvent> rightArm = new ModifiableCallout<>("Right Arm", "Dodge Moving Circle");
	private final ModifiableCallout<SystemLogMessageEvent> flamethrowers = new ModifiableCallout<>("Flamethrowers", "Look Behind for Flamethrowers");
	private final ModifiableCallout<SystemLogMessageEvent> leftArmOut = new ModifiableCallout<>("Left Arm (Out)", "Out", 6_000);
	private final ModifiableCallout<SystemLogMessageEvent> leftArmWalls = new ModifiableCallout<>("Left Arm (Falling Walls)", "Dodge Falling Walls");
	private final ModifiableCallout<SystemLogMessageEvent> leftArmTethers = new ModifiableCallout<>("Left Arm (Tethers)", "Spread Tethers");
	private final ModifiableCallout<SystemLogMessageEvent> electricFloor = new ModifiableCallout<>("Electric Floor", "Dodge Electric Floor", 10_000);
	private final ModifiableCallout<SystemLogMessageEvent> conveyerBelts = new ModifiableCallout<>("Conveyer Belts", "Conveyor Belts");
	private final ModifiableCallout<SystemLogMessageEvent> oilVats = new ModifiableCallout<>("Oil Vats", "Oil Vats", 3_000);
	private final RepeatSuppressor electricFloorSupp = new RepeatSuppressor(Duration.ofSeconds(15));
	private final RepeatSuppressor oilSupp = new RepeatSuppressor(Duration.ofSeconds(15));

	@HandleEvents
	public void hobbesLogs(EventContext context, SystemLogMessageEvent event) {
		long id = event.getId();
		if (id == LOG_RIGHT_ARM) {
			context.accept(rightArm.getModified(event));
		}
		else if (id == LOG_FLAMETHROWERS) {
			context.accept(flamethrowers.getModified(event));
		}
		else if (id == LOG_ELECTRIC_FLOOR && electricFloorSupp.check(event)) {
			context.accept(electricFloor.getModified(event));
		}
		else if (id == LOG_CONVEYER_BELTS) {
			context.accept(conveyerBelts.getModified(event));
		}
	}

	// 左腕は円範囲の後に壁倒し、線と続く
	@AutoFeed
	private final SequentialTrigger<BaseEvent> leftArmSq = SqtTemplates.sq(15_000,
			SystemLogMessageEvent.class, slm -> slm.getId() == LOG_LEFT_ARM,
			(e1, s) -> {
				s.updateCall(leftArmOut, e1);
				s.waitMs(8_000);
				s.updateCall(leftArmWalls, e1);
				s.waitMs(2_000);
				s.updateCall(leftArmTethers, e1);
			});

	// オイルは2回沸くので2回とも知らせる
	@AutoFeed
	private final SequentialTrigger<BaseEvent> oilSq = SqtTemplates.sq(10_000,
			SystemLogMessageEvent.class, slm -> slm.getId() == LOG_OIL && oilSupp.check(slm),
			(e1, s) -> {
				s.updateCall(oilVats, e1);
				s.waitMs(6_000);
				s.updateCall(oilVats, e1);
			});

	@PlayerHeadmarker(0xC4)
	private final ModifiableCallout<HeadMarkerEvent> shortMissile = new ModifiableCallout<>("Short-Range Missile", "Spread");

	@NpcCastCallout(0x4807)
	private final ModifiableCallout<AbilityCastStart> laserSight = ModifiableCallout.durationBasedCall("Laser Sight", "Stack");

	// ゴリアテタンク

	private final ModifiableCallout<TetherEvent> exploderOnYou = new ModifiableCallout<>("Medium Exploder (on You)", "Exploder on You");

	@HandleEvents
	public void exploderTether(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x11) && event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			context.accept(exploderOnYou.getModified(event));
		}
	}

	// 飛行ユニット

	@NpcCastCallout(0x4941)
	private final ModifiableCallout<AbilityCastStart> bombingManeuver = ModifiableCallout.durationBasedCall("360 Bombing Maneuver", "Raidwide");

	private final ModifiableCallout<HeadMarkerEvent> ballisticImpact = new ModifiableCallout<>("Ballistic Impact (Flight Unit)", "Spread");
	private final ModifiableCallout<HeadMarkerEvent> incendiaryBombing = new ModifiableCallout<>("Incendiary Bombing", "Puddle on You");

	@HandleEvents
	public void sharedMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x17) && event.getTarget().isThePlayer()) {
			context.accept((engelsActive ? incendiaryBombing : ballisticImpact).getModified(event));
		}
	}

	// エンゲルス

	@HandleEvents
	public void bossTracker(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x4941)) {
			engelsActive = false;
		}
		else if (event.abilityIdMatches(0x4726, 0x4727, 0x472A, 0x472E, 0x473C, 0x4746, 0x474E, 0x4755, 0x48A8)) {
			engelsActive = true;
		}
	}

	@NpcCastCallout(0x4727)
	private final ModifiableCallout<AbilityCastStart> marxSmashRight = ModifiableCallout.durationBasedCall("Marx Smash (Right)", "Go Right");

	@NpcCastCallout(0x4726)
	private final ModifiableCallout<AbilityCastStart> marxSmashLeft = ModifiableCallout.durationBasedCall("Marx Smash (Left)", "Go Left");

	@NpcCastCallout(0x472E)
	private final ModifiableCallout<AbilityCastStart> marxSmashForward = ModifiableCallout.durationBasedCall("Marx Smash (Forward)", "Front and Center");

	@NpcCastCallout(0x472A)
	private final ModifiableCallout<AbilityCastStart> marxSmashBack = ModifiableCallout.durationBasedCall("Marx Smash (Back)", "Back and Sides");

	@NpcCastCallout(0x4746)
	private final ModifiableCallout<AbilityCastStart> marxCrush = ModifiableCallout.durationBasedCall("Marx Crush", "Kill Claws");

	@PlayerHeadmarker(0xC6)
	private final ModifiableCallout<HeadMarkerEvent> precisionGuidedMissile = new ModifiableCallout<>("Precision Guided Missile", "Buster on You");

	@NpcCastCallout(0x4755)
	private final ModifiableCallout<AbilityCastStart> diffuseLaser = ModifiableCallout.durationBasedCall("Diffuse Laser", "Raidwide");

	// 本来の予兆はレーザーサイトだが、エネルギーバラージの方が早いのでこちらで知らせる
	private final ModifiableCallout<AbilityCastStart> energyBarrage = ModifiableCallout.durationBasedCall("Energy Barrage", "Sides, Near Front");
	private final ModifiableCallout<AbilityCastStart> energyBarrageTowers = new ModifiableCallout<>("Energy Barrage (Towers)", "Get Towers");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> energyBarrageSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x473C),
			(e1, s) -> {
				s.updateCall(energyBarrage, e1);
				s.waitMs(8_000);
				s.updateCall(energyBarrageTowers, e1);
			});

	@PlayerHeadmarker(0xC5)
	private final ModifiableCallout<HeadMarkerEvent> guidedMissile = new ModifiableCallout<>("Guided Missile", "Out, Dodge Homing AoE");

	private final ModifiableCallout<AbilityUsedEvent> reverseJointedGoliaths = new ModifiableCallout<>("Reverse-Jointed Goliaths", "Adds, Ignore Small", 4_000);

	@NpcCastCallout(0x474E)
	private final ModifiableCallout<AbilityCastStart> incendiarySaturationBombing = ModifiableCallout.durationBasedCall("Incendiary Saturation Bombing", "Front and Center");

	private final ModifiableCallout<AbilityCastStart> marxThrust = new ModifiableCallout<>("Marx Thrust", "Look for Wall Saws");

	// 9S専用機（多脚戦車）

	@NpcCastCallout(0x48F5)
	private final ModifiableCallout<AbilityCastStart> neutralization = ModifiableCallout.durationBasedCall("Neutralization", "Buster on {event.target}");

	@NpcCastCallout(0x48F6)
	private final ModifiableCallout<AbilityCastStart> laserSaturation = ModifiableCallout.durationBasedCall("Laser Saturation", "Raidwide");

	@NpcCastCallout(0x4A74)
	private final ModifiableCallout<AbilityCastStart> laserTurret = ModifiableCallout.durationBasedCall("Laser Turret", "Away from Front");

	@PlayerHeadmarker(0x8B)
	private final ModifiableCallout<HeadMarkerEvent> ballistic9s = new ModifiableCallout<>("Ballistic Impact (9S)", "Spread");

	@PlayerHeadmarker(0xA4)
	private final ModifiableCallout<HeadMarkerEvent> goliathLaserTurret = new ModifiableCallout<>("Goliath Laser Turret", "Laser Buster on You");

	@NpcCastCallout(0x48DF)
	private final ModifiableCallout<AbilityCastStart> foreHindCannons = ModifiableCallout.durationBasedCall("Fore-Hind Cannons", "Sides");

	@NpcCastCallout(0x48DE)
	private final ModifiableCallout<AbilityCastStart> dualFlankCannons = ModifiableCallout.durationBasedCall("Dual-Flank Cannons", "Front/Back");

	private final ModifiableCallout<AbilityCastStart> engageMarxSupport = new ModifiableCallout<>("Engage Marx Support", "Dodge Overhead Saws");

	// 雑魚の出現を待つと最初のボスにも反応してしまうので、技の発動で知らせる
	private final ModifiableCallout<AbilityUsedEvent> serviceModels = new ModifiableCallout<>("Serial-Jointed Service Models", "Kill Adds");

	@NpcCastCallout(0x48E5)
	private final ModifiableCallout<AbilityCastStart> goliathTankSupport = ModifiableCallout.durationBasedCall("Engage Goliath Tank Support", "Kill Adds");

	@NpcCastCallout(0x48E7)
	private final ModifiableCallout<AbilityCastStart> hackGoliathTank = ModifiableCallout.durationBasedCall("Hack Goliath Tank", "Go Behind Untethered Tank");

	@NpcCastCallout(value = 0x48F3, suppressMs = 2_000)
	private final ModifiableCallout<AbilityCastStart> shrapnelImpact = ModifiableCallout.durationBasedCall("Shrapnel Impact", "Stack");

	private final ModifiableCallout<AbilityCastStart> bubble = new ModifiableCallout<>("Bubble", "Get in the Bubble");

	@HandleEvents
	public void abilitiesUsed(EventContext context, AbilityUsedEvent event) {
		if (!event.isFirstTarget()) {
			return;
		}
		if (event.abilityIdMatches(0x473F)) {
			context.accept(reverseJointedGoliaths.getModified(event));
		}
		else if (event.abilityIdMatches(0x48EA)) {
			context.accept(serviceModels.getModified(event));
		}
	}

	// 詠唱開始からしばらく経ってから知らせた方がよい技
	private static SequentialTrigger<BaseEvent> delayedCast(long abilityId, long delayMs, ModifiableCallout<AbilityCastStart> call) {
		return SqtTemplates.sq((int) delayMs + 5_000,
				AbilityCastStart.class, acs -> acs.abilityIdMatches(abilityId),
				(e1, s) -> {
					s.waitMs(delayMs);
					s.updateCall(call, e1);
				});
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> marxThrustSq = delayedCast(0x48A8, 9_000, marxThrust);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> engageMarxSupportSq = delayedCast(0x48D3, 4_000, engageMarxSupport);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> bubbleSq = delayedCast(0x48EB, 5_000, bubble);

}
