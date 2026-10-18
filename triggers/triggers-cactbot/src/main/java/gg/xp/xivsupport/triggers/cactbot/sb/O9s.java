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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/raid/o9s.ts をもとに移植
@CalloutRepo(name = "Alphascape V10 Savage", duty = KnownDuty.O9S)
public class O9s extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long ENTROPY = 0x640;
	private static final long DYNAMIC_FLUID = 0x641;
	// 3回目の烈火の宣告からは時間切れの総攻撃フェーズ
	private static final int ENRAGE_BLAZE_COUNT = 3;

	private XivState state;

	public O9s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.O9S);
	}

	private enum Phase {
		NONE,
		FIRE,
		WATER,
		WIND,
		EARTH,
		ORB,
		ENRAGE
	}

	private enum Wind {
		NONE,
		HEAD,
		TAIL
	}

	private Phase phase = Phase.NONE;
	private Wind wind = Wind.NONE;
	private boolean primordialCrust;
	private int entropyCount;
	private int blazeCount;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		phase = Phase.NONE;
		wind = Wind.NONE;
		primordialCrust = false;
		entropyCount = 0;
		blazeCount = 0;
	}

	@HandleEvents
	public void phaseTracker(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x3186)) {
			blazeCount++;
			phase = blazeCount >= ENRAGE_BLAZE_COUNT ? Phase.ENRAGE : Phase.FIRE;
		}
		else if (phase == Phase.ENRAGE) {
			return;
		}
		else if (event.abilityIdMatches(0x3187)) {
			phase = Phase.WATER;
		}
		else if (event.abilityIdMatches(0x3188)) {
			phase = Phase.WIND;
		}
		else if (event.abilityIdMatches(0x3189)) {
			phase = Phase.EARTH;
		}
		else if (event.abilityIdMatches(0x318A)) {
			phase = Phase.ORB;
		}
	}

	// 共通

	@NpcCastCallout(0x3170)
	private final ModifiableCallout<AbilityCastStart> chaoticDispersion = ModifiableCallout.durationBasedCall("Chaotic Dispersion", "Buster on {event.target}");

	private final ModifiableCallout<AbilityCastStart> longitudinal = ModifiableCallout.durationBasedCall("Longitudinal Implosion", "Sides, then Front/Back");
	private final ModifiableCallout<AbilityCastStart> longitudinalDie = ModifiableCallout.durationBasedCall("Longitudinal Implosion (Primordial Crust)", "Die on Front/Back, then Sides");
	private final ModifiableCallout<AbilityCastStart> latitudinal = ModifiableCallout.durationBasedCall("Latitudinal Implosion", "Front/Back, then Sides");
	private final ModifiableCallout<AbilityCastStart> latitudinalDie = ModifiableCallout.durationBasedCall("Latitudinal Implosion (Primordial Crust)", "Die on Sides, then Front/Back");

	@HandleEvents
	public void implosion(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x3172)) {
			context.accept((primordialCrust ? longitudinalDie : longitudinal).getModified(event));
		}
		else if (event.abilityIdMatches(0x3173)) {
			context.accept((primordialCrust ? latitudinalDie : latitudinal).getModified(event));
		}
	}

	@NpcCastCallout(0x3171)
	private final ModifiableCallout<AbilityCastStart> damningEdict = ModifiableCallout.durationBasedCall("Damning Edict", "Get Behind");

	private final ModifiableCallout<AbilityCastStart> orbsFiend = ModifiableCallout.durationBasedCall("Fiendish Orbs", "Orb Tethers");

	@HandleEvents
	public void orbsFiend(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x317D) && state.playerJobMatches(job -> job.isTank() || job.isHealer() || job == Job.BLU)) {
			context.accept(orbsFiend.getModified(event));
		}
	}

	// 火

	private final ModifiableCallout<BuffApplied> entropySpread = new ModifiableCallout<BuffApplied>("Entropy: Spread", "Spread").statusIcon(ENTROPY);
	private final ModifiableCallout<BuffApplied> entropySpreadAndStay = new ModifiableCallout<BuffApplied>("Entropy: Spread and Stay (Tank/Healer)", "Spread and Stay").statusIcon(ENTROPY);
	private final ModifiableCallout<BuffApplied> entropyStackAndStayOut = new ModifiableCallout<BuffApplied>("Entropy: Stack and Stay Out (DPS)", "Stack and Stay Out").statusIcon(ENTROPY);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> entropySq = SqtTemplates.sq(60_000,
			BuffApplied.class, ba -> ba.buffIdMatches(ENTROPY) && ba.getTarget().isThePlayer(),
			(e1, s) -> {
				entropyCount++;
				int count = entropyCount;
				boolean support = state.playerJobMatches(job -> job.isTank() || job.isHealer());
				// DPS は2回目で頭割りに集まる時間が要るので早めに伝える
				Duration before = !support && count == 2 ? Duration.ofSeconds(12) : Duration.ofSeconds(5);
				long waitMs = e1.getEstimatedRemainingDuration().minus(before).toMillis();
				if (waitMs > 0) {
					s.waitMs(waitMs);
				}
				if (phase == Phase.ENRAGE || phase == Phase.ORB || count == 1) {
					s.updateCall(entropySpread, e1);
				}
				else if (support) {
					s.updateCall(entropySpreadAndStay, e1);
				}
				else {
					s.updateCall(entropyStackAndStayOut, e1);
				}
				if (phase == Phase.ORB || count == 2) {
					entropyCount = 0;
				}
			});

	private final ModifiableCallout<BuffApplied> entropyHideMiddle = new ModifiableCallout<>("Entropy: Avoid Hit", "Hide Middle");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> entropyAvoidSq = SqtTemplates.sq(30_000,
			BuffApplied.class, ba -> ba.buffIdMatches(ENTROPY) && ba.getTarget().isThePlayer() && phase == Phase.FIRE,
			(e1, s) -> {
				// 24秒と10秒のどちらかが付くので、もう片方の爆発に合わせる
				s.waitMs(e1.getInitialDuration().toSeconds() > 11 ? 5_000 : 19_000);
				s.updateCall(entropyHideMiddle, e1);
			});

	private final ModifiableCallout<AbilityCastStart> fireBigBang = ModifiableCallout.durationBasedCall("Big Bang (Fire)", "Hide Middle");
	private final RepeatSuppressor bigBangSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void fireBigBang(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x3180) && phase == Phase.FIRE && bigBangSupp.check(event)) {
			context.accept(fireBigBang.getModified(event));
		}
	}

	// 水

	private final ModifiableCallout<BuffApplied> dynamicFluid1 = new ModifiableCallout<>("Dynamic Fluid 1", "Stack Donut");
	private final ModifiableCallout<BuffApplied> dynamicFluid2 = new ModifiableCallout<>("Dynamic Fluid 2", "Stack Donut");
	private final ModifiableCallout<BuffApplied> dynamicFluidEnrage = new ModifiableCallout<>("Dynamic Fluid (Enrage)", "Stack Donut");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> dynamicFluidSq = SqtTemplates.sq(20_000,
			BuffApplied.class, ba -> ba.buffIdMatches(DYNAMIC_FLUID) && (phase == Phase.WATER || phase == Phase.ENRAGE),
			(e1, s) -> {
				if (phase == Phase.ENRAGE) {
					s.waitMs(1_000);
					s.updateCall(dynamicFluidEnrage, e1);
					return;
				}
				// タンクヒーラーは10秒、DPS は17秒
				s.waitMs(5_000);
				s.updateCall(dynamicFluid1, e1);
				s.waitMs(7_000);
				s.updateCall(dynamicFluid2, e1);
			});

	private final ModifiableCallout<HeadMarkerEvent> knockDownWater = new ModifiableCallout<>("Knock Down (Water)", "Drop Outside");
	private final ModifiableCallout<HeadMarkerEvent> knockDownWind = new ModifiableCallout<>("Knock Down (Wind)", "Drop Outside, Knockback");

	@HandleEvents
	public void knockDown(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(0x57) || !event.getTarget().isThePlayer()) {
			return;
		}
		if (phase == Phase.WATER) {
			context.accept(knockDownWater.getModified(event));
		}
		else if (phase == Phase.WIND) {
			context.accept(knockDownWind.getModified(event));
		}
	}

	// 風

	@HandleEvents
	public void windTracker(EventContext context, BuffApplied event) {
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		if (event.buffIdMatches(0x642)) {
			wind = Wind.HEAD;
		}
		else if (event.buffIdMatches(0x643)) {
			wind = Wind.TAIL;
		}
	}

	private final ModifiableCallout<AbilityCastStart> cycloneHeadwind = ModifiableCallout.durationBasedCall("Cyclone (Headwind)", "Back to Tornado");
	private final ModifiableCallout<AbilityCastStart> cycloneTailwind = ModifiableCallout.durationBasedCall("Cyclone (Tailwind)", "Face the Tornado");

	@HandleEvents
	public void cyclone(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x318F)) {
			return;
		}
		if (wind == Wind.HEAD) {
			context.accept(cycloneHeadwind.getModified(event));
		}
		else if (wind == Wind.TAIL) {
			context.accept(cycloneTailwind.getModified(event));
		}
		wind = Wind.NONE;
	}

	// 土

	private final ModifiableCallout<BuffApplied> accretionAll = new ModifiableCallout<>("Accretion", "Heal All to Full");
	private final ModifiableCallout<BuffApplied> accretionEarth = new ModifiableCallout<>("Accretion (Earth)", "Heal Tanks/Healers to Full");
	private final RepeatSuppressor accretionSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	@HandleEvents
	public void accretion(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x644)
				&& state.playerJobMatches(job -> job.isHealer() || job == Job.BLU)
				&& accretionSupp.check(event)) {
			context.accept((phase == Phase.EARTH ? accretionEarth : accretionAll).getModified(event));
		}
	}

	private final ModifiableCallout<BuffApplied> primordialCrustCall = new ModifiableCallout<BuffApplied>("Primordial Crust", "Die on Next Mechanic").statusIcon(0x645);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> primordialCrustSq = SqtTemplates.sq(35_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0x645) && ba.getTarget().isThePlayer(),
			(e1, s) -> {
				if (phase != Phase.ORB) {
					primordialCrust = true;
					s.updateCall(primordialCrustCall, e1);
				}
				s.waitMs(30_000);
				primordialCrust = false;
			});

	private final ModifiableCallout<HeadMarkerEvent> earthStack = new ModifiableCallout<>("Earth Stack Marker", "Stack with Partner");
	private final RepeatSuppressor earthStackSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	@HandleEvents
	public void earthStack(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E) && earthStackSupp.check(event)) {
			context.accept(earthStack.getModified(event));
		}
	}

	// 玉

	private final ModifiableCallout<BuffApplied> orbDynamicFluid = new ModifiableCallout<BuffApplied>("Dynamic Fluid (Orb)", "Hit DPS with Water").statusIcon(DYNAMIC_FLUID);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> orbDynamicFluidSq = SqtTemplates.sq(60_000,
			BuffApplied.class, ba -> ba.buffIdMatches(DYNAMIC_FLUID) && ba.getTarget().isThePlayer() && phase == Phase.ORB,
			(e1, s) -> {
				long waitMs = e1.getEstimatedRemainingDuration().minus(Duration.ofSeconds(5)).toMillis();
				if (waitMs > 0) {
					s.waitMs(waitMs);
				}
				s.updateCall(orbDynamicFluid, e1);
			});
}
