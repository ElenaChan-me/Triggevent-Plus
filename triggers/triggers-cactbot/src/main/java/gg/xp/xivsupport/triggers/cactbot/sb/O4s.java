package gg.xp.xivsupport.triggers.cactbot.sb;

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
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.BuffRemoved;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/raid/o4s.ts をもとに移植
@CalloutRepo(name = "Deltascape V40 Savage", duty = KnownDuty.O8S)
public class O4s extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public O4s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.O8S);
	}

	private String phase;
	private boolean neoPhase;
	private Integer dieOnLaser;
	private Integer omegaLaserCount;
	private boolean omegaProbablyDiedOnLaser;
	private boolean beyondDeath;
	private boolean whiteWound;
	private boolean blackWound;
	private boolean allaganField;
	private XivCombatant waterHealer;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		phase = null;
		neoPhase = false;
		dieOnLaser = null;
		omegaLaserCount = null;
		omegaProbablyDiedOnLaser = false;
		beyondDeath = false;
		whiteWound = false;
		blackWound = false;
		allaganField = false;
		waterHealer = null;
	}

	private boolean shouldDieOnLaser() {
		if (!beyondDeath) {
			return false;
		}
		// 1本目で死ぬと2本目の時点では死の超越が更新されないため、2本目では指示しない
		if (Integer.valueOf(2).equals(omegaLaserCount) && omegaProbablyDiedOnLaser) {
			return false;
		}
		if (!"omega".equals(phase)) {
			return true;
		}
		if (dieOnLaser == null || omegaLaserCount == null) {
			return false;
		}
		return omegaLaserCount >= dieOnLaser;
	}

	// 前半

	@NpcCastCallout(0x23F5)
	private final ModifiableCallout<AbilityCastStart> fire3 = ModifiableCallout.durationBasedCall("Fire III", "Fire III");

	@NpcCastCallout(0x23F7)
	private final ModifiableCallout<AbilityCastStart> blizzard3 = ModifiableCallout.durationBasedCall("Blizzard III", "Blizzard III");

	private final ModifiableCallout<AbilityCastStart> thunder3Buster = ModifiableCallout.durationBasedCall("Thunder III (Tank/Healer)", "Thunder III: Tankbuster");
	private final ModifiableCallout<AbilityCastStart> thunder3 = ModifiableCallout.durationBasedCall("Thunder III (Others)", "Thunder III");

	@HandleEvents
	public void thunder3Cast(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x23F9)) {
			if (state.playerJobMatches(job -> job.isTank() || job.isHealer())) {
				context.accept(thunder3Buster.getModified(event));
			}
			else {
				context.accept(thunder3.getModified(event));
			}
		}
	}

	@NpcCastCallout(0x23FB)
	private final ModifiableCallout<AbilityCastStart> ultimateFire3 = ModifiableCallout.durationBasedCall("Fire III (Dualcast)", "Stop Moving");

	@NpcCastCallout(0x23FC)
	private final ModifiableCallout<AbilityCastStart> ultimateBlizzard3 = ModifiableCallout.durationBasedCall("Blizzard III (Dualcast)", "Keep Moving");

	@NpcCastCallout(0x23FD)
	private final ModifiableCallout<AbilityCastStart> ultimateThunder3 = ModifiableCallout.durationBasedCall("Thunder III (Dualcast)", "Out");

	private final ModifiableCallout<AbilityCastStart> flareOnYou = ModifiableCallout.durationBasedCall("Flare on You", "Flare on You");
	private final ModifiableCallout<AbilityCastStart> lightAndDarknessStack = ModifiableCallout.durationBasedCall("Light and Darkness: Stack", "Stack");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> flareSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x2401),
			(e1, s) -> {
				List<AbilityCastStart> casts = new ArrayList<>();
				casts.add(e1);
				casts.addAll(s.waitEventsQuickSuccession(2, AbilityCastStart.class, acs -> acs.abilityIdMatches(0x2401)));
				AbilityCastStart mine = casts.stream().filter(acs -> acs.getTarget().isThePlayer()).findFirst().orElse(null);
				if (mine != null) {
					s.updateCall(flareOnYou, mine);
				}
				// 前半のエクスデスのフレアは対象者にしか通知しない
				else if (neoPhase) {
					s.updateCall(lightAndDarknessStack, e1);
				}
			});

	// 後半

	@HandleEvents
	public void neoTracker(EventContext context, AbilityCastStart event) {
		// ネオエクスデスのみが使う技を見た時点で後半とみなす
		if (event.abilityIdMatches(0x2417, 0x241E, 0x241C, 0x240E, 0x240F, 0x2411, 0x2412, 0x2416, 0x242B, 0x242C, 0x242D, 0x2426)) {
			neoPhase = true;
		}
		if (event.abilityIdMatches(0x242B)) {
			phase = "alpha";
			dieOnLaser = 1;
		}
		else if (event.abilityIdMatches(0x242C)) {
			phase = "delta";
			waterHealer = null;
		}
		else if (event.abilityIdMatches(0x242D)) {
			phase = "omega";
			waterHealer = null;
			omegaLaserCount = 1;
		}
	}

	@HandleEvents
	public void buffGain(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x3FF)) {
			waterHealer = event.getTarget();
		}
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		if (event.buffIdMatches(0x564)) {
			whiteWound = true;
		}
		else if (event.buffIdMatches(0x565)) {
			blackWound = true;
		}
		else if (event.buffIdMatches(0x566)) {
			beyondDeath = true;
		}
		else if (event.buffIdMatches(0x1C6)) {
			allaganField = true;
		}
	}

	@HandleEvents
	public void buffLoss(EventContext context, BuffRemoved event) {
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		if (event.buffIdMatches(0x564)) {
			whiteWound = false;
		}
		else if (event.buffIdMatches(0x565)) {
			blackWound = false;
		}
		else if (event.buffIdMatches(0x566)) {
			beyondDeath = false;
		}
		else if (event.buffIdMatches(0x1C6)) {
			allaganField = false;
		}
	}

	private final ModifiableCallout<AbilityCastStart> floodInsideOut = ModifiableCallout.durationBasedCall("Flood of Naught: Inside (Normal)", "Out");
	private final ModifiableCallout<AbilityCastStart> floodInsideDie = ModifiableCallout.durationBasedCall("Flood of Naught: Inside (Die)", "Die on Inside");
	private final ModifiableCallout<AbilityCastStart> floodOutsideIn = ModifiableCallout.durationBasedCall("Flood of Naught: Outside (Normal)", "In");
	private final ModifiableCallout<AbilityCastStart> floodOutsideDie = ModifiableCallout.durationBasedCall("Flood of Naught: Outside (Die)", "Die on Outside");

	private final ModifiableCallout<AbilityCastStart> purpleBlueLeftPurple = ModifiableCallout.durationBasedCall("Flood of Naught: Purple/Blue (Black Wound)", "Left on Purple");
	private final ModifiableCallout<AbilityCastStart> purpleBlueRightBlue = ModifiableCallout.durationBasedCall("Flood of Naught: Purple/Blue (White Wound)", "Right on Blue");
	private final ModifiableCallout<AbilityCastStart> purpleBlueDieRightBlue = ModifiableCallout.durationBasedCall("Flood of Naught: Purple/Blue (Die, Black Wound)", "Die on Right Blue");
	private final ModifiableCallout<AbilityCastStart> purpleBlueDieLeftPurple = ModifiableCallout.durationBasedCall("Flood of Naught: Purple/Blue (Die, White Wound)", "Die on Left Purple");

	private final ModifiableCallout<AbilityCastStart> bluePurpleRightPurple = ModifiableCallout.durationBasedCall("Flood of Naught: Blue/Purple (Black Wound)", "Right on Purple");
	private final ModifiableCallout<AbilityCastStart> bluePurpleLeftBlue = ModifiableCallout.durationBasedCall("Flood of Naught: Blue/Purple (White Wound)", "Left on Blue");
	private final ModifiableCallout<AbilityCastStart> bluePurpleDieLeftBlue = ModifiableCallout.durationBasedCall("Flood of Naught: Blue/Purple (Die, Black Wound)", "Die on Left Blue");
	private final ModifiableCallout<AbilityCastStart> bluePurpleDieRightPurple = ModifiableCallout.durationBasedCall("Flood of Naught: Blue/Purple (Die, White Wound)", "Die on Right Purple");

	private final ModifiableCallout<AbilityCastStart> colorSides = ModifiableCallout.durationBasedCall("Flood of Naught: Colors (No Wound)", "Color Sides");
	private final ModifiableCallout<AbilityCastStart> colorSidesDie = ModifiableCallout.durationBasedCall("Flood of Naught: Colors (Die, No Wound)", "Die on Color Sides");

	@HandleEvents
	public void floodOfNaught(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x240E, 0x240F, 0x2411, 0x2412)) {
			return;
		}
		boolean die = shouldDieOnLaser();
		ModifiableCallout<AbilityCastStart> call;
		switch ((int) event.getAbility().getId()) {
			case 0x240E -> call = die ? floodInsideDie : floodInsideOut;
			case 0x240F -> call = die ? floodOutsideDie : floodOutsideIn;
			case 0x2411 -> {
				if (blackWound) {
					call = die ? purpleBlueDieRightBlue : purpleBlueLeftPurple;
				}
				else if (whiteWound) {
					call = die ? purpleBlueDieLeftPurple : purpleBlueRightBlue;
				}
				else {
					call = die ? colorSidesDie : colorSides;
				}
			}
			default -> {
				if (blackWound) {
					call = die ? bluePurpleDieLeftBlue : bluePurpleRightPurple;
				}
				else if (whiteWound) {
					call = die ? bluePurpleDieRightPurple : bluePurpleLeftBlue;
				}
				else {
					call = die ? colorSidesDie : colorSides;
				}
			}
		}
		context.accept(call.getModified(event));

		// 死の超越は2本目のレーザーが出た後まで消えないので、ここで死んだ可能性を記録しておく
		if ("omega".equals(phase)) {
			if (die) {
				omegaProbablyDiedOnLaser = true;
			}
			omegaLaserCount = (omegaLaserCount == null ? 1 : omegaLaserCount) + 1;
		}
	}

	private final ModifiableCallout<AbilityCastStart> chargeBehindOtherTank = ModifiableCallout.durationBasedCall("Flood of Naught: Charge (Tank, Allagan Field)", "Charge: Behind Other Tank");
	private final ModifiableCallout<AbilityCastStart> chargeVeryBack = ModifiableCallout.durationBasedCall("Flood of Naught: Charge (Allagan Field)", "Charge: Very Back");
	private final ModifiableCallout<AbilityCastStart> chargeInFront = ModifiableCallout.durationBasedCall("Flood of Naught: Charge (Tank)", "Charge: In Front");
	private final ModifiableCallout<AbilityCastStart> chargeBehindTanks = ModifiableCallout.durationBasedCall("Flood of Naught: Charge", "Charge: Behind Tanks");

	@HandleEvents
	public void charge(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x2416)) {
			return;
		}
		boolean tank = state.playerJobMatches(Job::isTank);
		if (allaganField) {
			context.accept((tank ? chargeBehindOtherTank : chargeVeryBack).getModified(event));
		}
		else {
			context.accept((tank ? chargeInFront : chargeBehindTanks).getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> doubleAttackTank = ModifiableCallout.durationBasedCall("Double Attack (Tank)", "Double Attack");
	private final ModifiableCallout<AbilityCastStart> doubleAttack = ModifiableCallout.durationBasedCall("Double Attack", "Double Attack: Out");

	@HandleEvents
	public void doubleAttackCast(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x241C)) {
			context.accept((state.playerJobMatches(Job::isTank) ? doubleAttackTank : doubleAttack).getModified(event));
		}
	}

	@NpcCastCallout(0x242B)
	private final ModifiableCallout<AbilityCastStart> grandCrossAlpha = ModifiableCallout.durationBasedCall("Grand Cross Alpha", "Go Middle");

	private final ModifiableCallout<AbilityCastStart> grandCrossDeltaTank = ModifiableCallout.durationBasedCall("Grand Cross Delta (Tank)", "Front of Boss");
	private final ModifiableCallout<AbilityCastStart> grandCrossDeltaHealer = ModifiableCallout.durationBasedCall("Grand Cross Delta (Healer)", "Sides of Boss");
	private final ModifiableCallout<AbilityCastStart> grandCrossDeltaDps = ModifiableCallout.durationBasedCall("Grand Cross Delta (DPS)", "Inside Boss");

	@HandleEvents
	public void grandCrossDelta(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x242C)) {
			return;
		}
		if (state.playerJobMatches(Job::isTank)) {
			context.accept(grandCrossDeltaTank.getModified(event));
		}
		else if (state.playerJobMatches(Job::isHealer)) {
			context.accept(grandCrossDeltaHealer.getModified(event));
		}
		else {
			context.accept(grandCrossDeltaDps.getModified(event));
		}
	}

	@NpcCastCallout(0x242D)
	private final ModifiableCallout<AbilityCastStart> grandCrossOmega = ModifiableCallout.durationBasedCall("Grand Cross Omega", "Go Middle");

	private final ModifiableCallout<BuffApplied> forkedLightning = new ModifiableCallout<BuffApplied>("Forked Lightning", "Spread").statusIcon(0x24B);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> forkedLightningSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0x24B) && ba.getTarget().isThePlayer(),
			(e1, s) -> {
				s.waitMs(1_000);
				s.updateCall(forkedLightning, e1);
			});

	private final ModifiableCallout<BuffApplied> accelerationBombDelta = new ModifiableCallout<BuffApplied>("Acceleration Bomb (Delta, Initial)", "Acceleration Bomb").statusIcon(0x568);
	private final ModifiableCallout<BuffApplied> accelerationBombOmega = ModifiableCallout.<BuffApplied>durationBasedCall("Acceleration Bomb (Omega)", "Look Away and Stop").statusIcon(0x568);
	private final ModifiableCallout<BuffApplied> accelerationBomb = ModifiableCallout.<BuffApplied>durationBasedCall("Acceleration Bomb", "Stop").statusIcon(0x568);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> accelerationBombSq = SqtTemplates.sq(60_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0x568) && ba.getTarget().isThePlayer(),
			(e1, s) -> {
				if ("delta".equals(phase)) {
					s.updateCall(accelerationBombDelta, e1);
				}
				long waitMs = e1.getEstimatedRemainingDuration().minus(Duration.ofSeconds(4)).toMillis();
				if (waitMs > 0) {
					s.waitMs(waitMs);
				}
				s.updateCall("omega".equals(phase) ? accelerationBombOmega : accelerationBomb, e1);
			});

	private final ModifiableCallout<BuffApplied> omegaShriek = ModifiableCallout.<BuffApplied>durationBasedCall("Omega Shriek", "Shriek: Middle, Look Away").statusIcon(0x1C4);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> omegaShriekSq = SqtTemplates.sq(60_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0x1C4) && ba.getTarget().isThePlayer() && "omega".equals(phase),
			(e1, s) -> {
				long waitMs = e1.getEstimatedRemainingDuration().minus(Duration.ofSeconds(5)).toMillis();
				if (waitMs > 0) {
					s.waitMs(waitMs);
				}
				s.updateCall(omegaShriek, e1);
			});

	private final ModifiableCallout<BuffApplied> waterDelta = new ModifiableCallout<BuffApplied>("Water on You (Delta)", "Water on You").statusIcon(0x3FF);
	private final ModifiableCallout<BuffApplied> waterOmega = new ModifiableCallout<BuffApplied>("Water on You (Omega)", "Water: Stack Under Boss").statusIcon(0x3FF);

	@HandleEvents
	public void waterOnMe(EventContext context, BuffApplied event) {
		if (!event.buffIdMatches(0x3FF) || !event.getTarget().isThePlayer()) {
			return;
		}
		// デルタでは陣取りの方針が固定されないので、場所までは指示しない
		if ("delta".equals(phase)) {
			context.accept(waterDelta.getModified(event));
		}
		else if ("omega".equals(phase)) {
			context.accept(waterOmega.getModified(event));
		}
	}

	private final ModifiableCallout<BuffApplied> beyondDeathTankHealer = new ModifiableCallout<BuffApplied>("Beyond Death (Delta, Tank, Water Known)", "Stack on {healer}").statusIcon(0x566);
	private final ModifiableCallout<BuffApplied> beyondDeathTankWater = new ModifiableCallout<BuffApplied>("Beyond Death (Delta, Tank)", "Stack on Water").statusIcon(0x566);
	private final ModifiableCallout<BuffApplied> beyondDeathDelta = new ModifiableCallout<BuffApplied>("Beyond Death (Delta)", "Beyond Death").statusIcon(0x566);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> beyondDeathDeltaSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0x566) && ba.getTarget().isThePlayer() && "delta".equals(phase),
			(e1, s) -> {
				if (!state.playerJobMatches(Job::isTank)) {
					s.updateCall(beyondDeathDelta, e1);
					return;
				}
				// 水属性のデバフが付くのを少し待つ
				s.waitMs(500);
				if (waterHealer != null) {
					s.setParam("healer", waterHealer);
					s.updateCall(beyondDeathTankHealer, e1);
				}
				else {
					s.updateCall(beyondDeathTankWater, e1);
				}
			});

	private final ModifiableCallout<BuffApplied> offBalanceHealer = new ModifiableCallout<BuffApplied>("Off Balance (Omega, Water Known)", "Stack Under Boss on {healer}").statusIcon(0x569);
	private final ModifiableCallout<BuffApplied> offBalanceWater = new ModifiableCallout<BuffApplied>("Off Balance (Omega)", "Stack on Water").statusIcon(0x569);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> offBalanceSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0x569) && ba.getTarget().isThePlayer() && "omega".equals(phase),
			(e1, s) -> {
				s.waitMs(500);
				if (waterHealer != null) {
					s.setParam("healer", waterHealer);
					s.updateCall(offBalanceHealer, e1);
				}
				else {
					s.updateCall(offBalanceWater, e1);
				}
			});

	@PlayerHeadmarker(0x28)
	private final ModifiableCallout<HeadMarkerEvent> earthshaker = new ModifiableCallout<>("Earthshaker", "Earthshaker on You");

	@NpcCastCallout(0x241E)
	private final ModifiableCallout<AbilityCastStart> deltaAttack = ModifiableCallout.durationBasedCall("Delta Attack", "Stack");

	@NpcCastCallout(0x2417)
	private final ModifiableCallout<AbilityCastStart> almagest = ModifiableCallout.durationBasedCall("Almagest", "Raidwide");

}
