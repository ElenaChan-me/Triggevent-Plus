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
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/raid/o2s.ts をもとに移植
@CalloutRepo(name = "Deltascape V20 Savage", duty = KnownDuty.O6S)
public class O2s extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long LEVITATION = 0x556;
	private static final long SIX_FULMS_UNDER = 0x237;
	private static final long HUNDRED_GS_MARKER = 0x69;

	private XivState state;

	public O2s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.O6S);
	}

	private int probeCount;
	private boolean levitating;
	private boolean under;
	private final List<XivCombatant> blueCircle = new ArrayList<>();

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		probeCount = 0;
		levitating = false;
		under = false;
		blueCircle.clear();
	}

	@HandleEvents
	public void buffGain(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(LEVITATION) && event.getTarget().isThePlayer()) {
			levitating = true;
		}
	}

	@HandleEvents
	public void buffLoss(EventContext context, BuffRemoved event) {
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		if (event.buffIdMatches(LEVITATION)) {
			levitating = false;
		}
		else if (event.buffIdMatches(SIX_FULMS_UNDER)) {
			under = false;
		}
	}

	private final ModifiableCallout<AbilityCastStart> evilsphereOnYou = ModifiableCallout.durationBasedCall("Evilsphere (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> evilsphereTank = ModifiableCallout.durationBasedCall("Evilsphere (Other Tank)", "Tank Swap");
	private final ModifiableCallout<AbilityCastStart> evilsphere = ModifiableCallout.durationBasedCall("Evilsphere", "Buster on {event.target}");

	@HandleEvents
	public void evilsphere(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x2371)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(evilsphereOnYou.getModified(event));
		}
		else if (state.playerJobMatches(Job::isTank)) {
			context.accept(evilsphereTank.getModified(event));
		}
		else {
			context.accept(evilsphere.getModified(event));
		}
	}

	@HandleEvents
	public void hundredGsCollect(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(HUNDRED_GS_MARKER)) {
			blueCircle.add(event.getTarget());
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> hundredGsOnYou = new ModifiableCallout<>("100 Gs (On You)", "100 Gs on You");
	private final ModifiableCallout<HeadMarkerEvent> hundredGsBeIn = new ModifiableCallout<>("100 Gs", "Be in 100 Gs Circle");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> hundredGsSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hme -> hme.markerIdMatches(HUNDRED_GS_MARKER),
			(e1, s) -> {
				// 複数人に付くので全員分を待ってから判定する
				s.waitMs(300);
				boolean onYou = blueCircle.stream().anyMatch(XivCombatant::isThePlayer);
				blueCircle.clear();
				s.updateCall(onYou ? hundredGsOnYou : hundredGsBeIn, e1);
			});

	@NpcCastCallout(0x235E)
	private final ModifiableCallout<AbilityCastStart> minusHundredGs = ModifiableCallout.durationBasedCall("-100 Gs", "North/South, Look Away");

	@NpcCastCallout(0x236F)
	private final ModifiableCallout<AbilityCastStart> deathsGaze = ModifiableCallout.durationBasedCall("Death's Gaze", "Look Away");

	private final ModifiableCallout<AbilityCastStart> earthquake = ModifiableCallout.durationBasedCall("Earthquake (Levitating)", "Earthquake");
	private final ModifiableCallout<AbilityCastStart> earthquakeLevitate = ModifiableCallout.durationBasedCall("Earthquake", "Earthquake: Levitate");

	@HandleEvents
	public void earthquake(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x2374)) {
			context.accept((levitating ? earthquake : earthquakeLevitate).getModified(event));
		}
	}

	private final ModifiableCallout<BuffApplied> elevatedDps = new ModifiableCallout<>("Elevated (DPS)", "DPS: Levitate");
	private final ModifiableCallout<BuffApplied> elevatedSupport = new ModifiableCallout<>("Elevated (Tank/Healer)", "DPS Up, Tanks/Healers Down");
	private final RepeatSuppressor elevatedSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void elevated(EventContext context, BuffApplied event) {
		if (!event.buffIdMatches(0x54E) || state.playerJobMatches(job -> job == Job.BLU) || !elevatedSupp.check(event)) {
			return;
		}
		if (state.playerJobMatches(Job::isDps)) {
			if (!levitating) {
				context.accept(elevatedDps.getModified(event));
			}
		}
		else {
			context.accept(elevatedSupport.getModified(event));
		}
	}

	@NpcCastCallout(0x2372)
	private final ModifiableCallout<AbilityCastStart> gravitationalWave = ModifiableCallout.durationBasedCall("Gravitational Wave", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> probeTanksHealers = ModifiableCallout.durationBasedCall("Maniacal Probe (Tanks/Healers)", "Maniacal Probe: Tanks and Healers");
	private final ModifiableCallout<AbilityCastStart> probeDps = ModifiableCallout.durationBasedCall("Maniacal Probe (DPS)", "Maniacal Probe: DPS");

	@HandleEvents
	public void maniacalProbe(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x235A)) {
			return;
		}
		probeCount++;
		// 2回目と4回目は DPS が担当する
		boolean dpsProbe = probeCount == 2 || probeCount == 4;
		if (state.playerJobMatches(job -> job == Job.BLU)) {
			return;
		}
		context.accept((dpsProbe ? probeDps : probeTanksHealers).getModified(event));
	}

	@PlayerHeadmarker(0x05)
	private final ModifiableCallout<HeadMarkerEvent> probeOnYou = new ModifiableCallout<>("Maniacal Probe (On You)", "Probe on You");

	private final ModifiableCallout<BuffApplied> unstableGravity = new ModifiableCallout<BuffApplied>("Unstable Gravity", "Elevate").statusIcon(0x550);
	private final ModifiableCallout<BuffApplied> unstableGravityKnock = new ModifiableCallout<BuffApplied>("Unstable Gravity (Delayed)", "Get Knocked to Edge").statusIcon(0x550);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> unstableGravitySq = SqtTemplates.sq(15_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0x550) && ba.getTarget().isThePlayer(),
			(e1, s) -> {
				s.updateCall(unstableGravity, e1);
				s.waitMs(9_000);
				s.updateCall(unstableGravityKnock, e1);
			});

	private final ModifiableCallout<BuffApplied> sixFulmsUnder = new ModifiableCallout<BuffApplied>("6 Fulms Under (Levitating)", "6 Fulms Under").statusIcon(SIX_FULMS_UNDER);
	private final ModifiableCallout<BuffApplied> sixFulmsUnderLevitate = new ModifiableCallout<BuffApplied>("6 Fulms Under", "6 Fulms Under: Levitate").statusIcon(SIX_FULMS_UNDER);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> sixFulmsUnderSq = SqtTemplates.sq(10_000,
			BuffApplied.class, ba -> ba.buffIdMatches(SIX_FULMS_UNDER) && ba.getTarget().isThePlayer() && !under,
			(e1, s) -> {
				s.waitMs(5_000);
				s.updateCall(levitating ? sixFulmsUnder : sixFulmsUnderLevitate, e1);
				under = true;
			});
}
