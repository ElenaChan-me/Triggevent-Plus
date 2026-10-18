package gg.xp.xivsupport.triggers.cactbot.shb;

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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/trial/innocence-ex.ts をもとに移植
@CalloutRepo(name = "The Crown Of The Immaculate Extreme", duty = KnownDuty.TheCrownOfTheImmaculateExtreme)
public class InnocenceEx extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long STARBIRTH = 0x3EEF;
	private static final long SHADOWREAVER = 0x3EEA;
	private static final long CHARGE = 0x3EEE;
	private static final int PILLAR_AIM_BACK = 3;

	private XivState state;

	public InnocenceEx(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheCrownOfTheImmaculateExtreme);
	}

	private int starbirthCount;
	private boolean starbirthActive;
	private int lightPillarCount;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		starbirthCount = 0;
		starbirthActive = false;
		lightPillarCount = 0;
	}

	private final ModifiableCallout<AbilityCastStart> starbirthCorner = ModifiableCallout.durationBasedCall("Starbirth 1", "Starbirth: Corner");
	private final ModifiableCallout<AbilityCastStart> starbirthAvoidCharge = ModifiableCallout.durationBasedCall("Starbirth 2/5", "Starbirth: Avoid + Charge");
	private final ModifiableCallout<AbilityCastStart> starbirthExplode = ModifiableCallout.durationBasedCall("Starbirth 3", "Starbirth: Explode");
	private final ModifiableCallout<AbilityCastStart> starbirthCharge = ModifiableCallout.durationBasedCall("Starbirth 4", "Starbirth: Charge");
	private final ModifiableCallout<AbilityCastStart> starbirthEnrage = ModifiableCallout.durationBasedCall("Starbirth 6", "Starbirth: Enrage");
	private final ModifiableCallout<AbilityCastStart> starbirthSafeCorner = new ModifiableCallout<>("Starbirth 1 (Follow-up)", "Get to Safe Corner");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> starbirthSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(STARBIRTH),
			(e1, s) -> {
				starbirthCount++;
				starbirthActive = true;
				ModifiableCallout<AbilityCastStart> call = switch (starbirthCount) {
					case 1 -> starbirthCorner;
					case 2, 5 -> starbirthAvoidCharge;
					case 3 -> starbirthExplode;
					case 4 -> starbirthCharge;
					case 6 -> starbirthEnrage;
					default -> null;
				};
				if (call != null) {
					s.updateCall(call, e1);
				}
				if (starbirthCount == 1) {
					s.waitMs(6_000);
					s.updateCall(starbirthSafeCorner, e1);
				}
			});

	private final ModifiableCallout<AbilityCastStart> reprobationSwords = new ModifiableCallout<>("Reprobation Swords 2", "Swords");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> reprobationSwordsSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x3EDC),
			(e1, s) -> {
				// 詠唱3秒＋次の剣まで7秒
				s.waitMs(7_000);
				s.updateCall(reprobationSwords, e1);
			});

	@NpcCastCallout(SHADOWREAVER)
	private final ModifiableCallout<AbilityCastStart> shadowreaver = ModifiableCallout.durationBasedCall("Shadowreaver", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> righteousBoltOnYou = ModifiableCallout.durationBasedCall("Righteous Bolt (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> righteousBoltSwap = ModifiableCallout.durationBasedCall("Righteous Bolt (Other Tank)", "Tank Swap");
	private final ModifiableCallout<AbilityCastStart> righteousBolt = ModifiableCallout.durationBasedCall("Righteous Bolt", "Buster on {event.target}");

	private final ModifiableCallout<AbilityCastStart> holySwordOnYou = ModifiableCallout.durationBasedCall("Holy Sword (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> holySword = ModifiableCallout.durationBasedCall("Holy Sword", "Tankbusters");
	private final RepeatSuppressor holySwordSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	private final ModifiableCallout<AbilityCastStart> avoidChargeAndOrbs = ModifiableCallout.durationBasedCall("Charge (During Starbirth)", "Avoid Charge and Orbs");
	private final ModifiableCallout<AbilityCastStart> avoidCharge = ModifiableCallout.durationBasedCall("Charge", "Avoid Charge");

	private final ModifiableCallout<AbilityCastStart> godRay = ModifiableCallout.durationBasedCall("God Ray", "Avoid Swords then Ray");
	private final RepeatSuppressor godRaySupp = new RepeatSuppressor(Duration.ofSeconds(15));

	@HandleEvents
	public void casts(EventContext context, AbilityCastStart event) {
		if (event.getSource().isPc()) {
			return;
		}
		if (event.abilityIdMatches(0x3ECD)) {
			if (event.getTarget().isThePlayer()) {
				context.accept(righteousBoltOnYou.getModified(event));
			}
			else if (state.playerJobMatches(Job::isTank)) {
				context.accept(righteousBoltSwap.getModified(event));
			}
			else {
				context.accept(righteousBolt.getModified(event));
			}
		}
		else if (event.abilityIdMatches(0x3EC9)) {
			if (event.getTarget().isThePlayer()) {
				context.accept(holySwordOnYou.getModified(event));
			}
			else if (holySwordSupp.check(event)) {
				context.accept(holySword.getModified(event));
			}
		}
		else if (event.abilityIdMatches(CHARGE)) {
			context.accept((starbirthActive ? avoidChargeAndOrbs : avoidCharge).getModified(event));
			starbirthActive = false;
		}
		else if (event.abilityIdMatches(SHADOWREAVER)) {
			starbirthActive = false;
		}
		else if (event.abilityIdMatches(0x3EE4, 0x3EE5, 0x3EE6) && godRaySupp.check(event)) {
			context.accept(godRay.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> adds = new ModifiableCallout<>("Venery Adds", "Grab East/West Adds");

	private final ModifiableCallout<AbilityUsedEvent> aimLineAtBackOrb = new ModifiableCallout<>("Light Pillar (On You, 3rd)", "Aim Line at Back Orb");
	private final ModifiableCallout<AbilityUsedEvent> avoidOrbsWithLine = new ModifiableCallout<>("Light Pillar (On You)", "Avoid Orbs with Line");
	private final ModifiableCallout<AbilityUsedEvent> lineStack = new ModifiableCallout<>("Light Pillar", "Line Stack");

	@HandleEvents
	public void abilities(EventContext context, AbilityUsedEvent event) {
		if (event.getSource().isPc()) {
			return;
		}
		if (event.abilityIdMatches(0x42B0) && event.isFirstTarget()) {
			if (state.playerJobMatches(job -> job.isTank() || job == Job.BLU)) {
				context.accept(adds.getModified(event));
			}
		}
		else if (event.abilityIdMatches(0x38FC)) {
			lightPillarCount++;
			if (event.getTarget().isThePlayer()) {
				context.accept((lightPillarCount == PILLAR_AIM_BACK ? aimLineAtBackOrb : avoidOrbsWithLine).getModified(event));
			}
			else {
				context.accept(lineStack.getModified(event));
			}
		}
	}

	private final ModifiableCallout<AbilityCastStart> starbirthExplodeSafeCorner = new ModifiableCallout<>("Starbirth Explode (Follow-up)", "Get to Safe Corner");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> starbirthExplodeSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x3F3E) && lightPillarCount == PILLAR_AIM_BACK,
			(e1, s) -> {
				s.waitMs(6_500);
				s.updateCall(starbirthExplodeSafeCorner, e1);
			});

	@PlayerHeadmarker(0xAC)
	private final ModifiableCallout<HeadMarkerEvent> wingedReprobation = new ModifiableCallout<>("Winged Reprobation Tether", "Tether on You");

	private final ModifiableCallout<HeadMarkerEvent> dropOfLightOrbs = new ModifiableCallout<>("Winged Drop of Light (During Starbirth)", "Circle, Avoid Orbs");
	private final ModifiableCallout<HeadMarkerEvent> dropOfLight = new ModifiableCallout<>("Winged Drop of Light", "Circle on You");

	@HandleEvents
	public void headmarkers(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x8A) && event.getTarget().isThePlayer()) {
			context.accept((starbirthActive ? dropOfLightOrbs : dropOfLight).getModified(event));
		}
	}

	@NpcCastCallout(value = {0x3ED7, 0x3ED3}, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> rotateLeft = ModifiableCallout.durationBasedCall("Soul and Body / Rood (Left)", "Rotate Left");

	@NpcCastCallout(value = {0x3ED9, 0x3ED5}, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> rotateRight = ModifiableCallout.durationBasedCall("Soul and Body / Rood (Right)", "Rotate Right");

}
