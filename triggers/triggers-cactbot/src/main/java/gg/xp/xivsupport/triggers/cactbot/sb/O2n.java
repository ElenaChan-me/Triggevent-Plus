package gg.xp.xivsupport.triggers.cactbot.sb;

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
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.BuffRemoved;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/raid/o2n.ts をもとに移植
@CalloutRepo(name = "Deltascape V20", duty = KnownDuty.DeltascapeV20)
public class O2n extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long levitationId = 0x556;
	private static final long sixFulmsUnderId = 0x237;
	// 2回目以降のアンチライトは3回に1回、中央へ寄る必要がある
	private static final int antilightCenterCycle = 3;

	private XivState state;

	public O2n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.DeltascapeV20);
	}

	private boolean levitating;
	private int antiCounter;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		levitating = false;
		antiCounter = 0;
	}

	@HandleEvents
	public void levitationGain(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(levitationId) && event.getTarget().isThePlayer()) {
			levitating = true;
		}
	}

	@HandleEvents
	public void levitationLose(EventContext context, BuffRemoved event) {
		if (event.buffIdMatches(levitationId) && event.getTarget().isThePlayer()) {
			levitating = false;
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> gravManipOnYou = new ModifiableCallout<>("Gravitational Manipulation (On You)", "Stack Marker on You");
	private final ModifiableCallout<HeadMarkerEvent> gravManip = new ModifiableCallout<>("Gravitational Manipulation", "Stack on {event.target}");
	private final ModifiableCallout<HeadMarkerEvent> gravManipFloat = new ModifiableCallout<>("Gravitational Manipulation (Levitate)", "Levitate");

	@HandleEvents
	public void gravManipMarker(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(0x71)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(gravManipOnYou.getModified(event));
		}
		else {
			context.accept(gravManip.getModified(event));
			if (!levitating) {
				context.accept(gravManipFloat.getModified(event));
			}
		}
	}

	@NpcCastCallout(0x250F)
	private final ModifiableCallout<AbilityCastStart> evilsphere = ModifiableCallout.durationBasedCall("Evilsphere", "Buster on {event.target}");

	@NpcCastCallout(0x24FF)
	private final ModifiableCallout<AbilityCastStart> minus100Gs = ModifiableCallout.durationBasedCall("-100 Gs", "-100 Gs: Go North/South");

	@NpcCastCallout(0x250D)
	private final ModifiableCallout<AbilityCastStart> demonEye = ModifiableCallout.durationBasedCall("Demon Eye", "Look Away");

	private final ModifiableCallout<AbilityCastStart> earthquakeLevitate = ModifiableCallout.durationBasedCall("Earthquake (Not Levitating)", "Levitate");
	private final ModifiableCallout<AbilityCastStart> earthquake = ModifiableCallout.durationBasedCall("Earthquake (Levitating)", "Earthquake");

	@HandleEvents
	public void earthquakeCast(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x2512)) {
			context.accept((levitating ? earthquake : earthquakeLevitate).getModified(event));
		}
	}

	@NpcCastCallout(0x2510)
	private final ModifiableCallout<AbilityCastStart> gravitationalWave = ModifiableCallout.durationBasedCall("Gravitational Wave", "Raidwide");

	private final ModifiableCallout<BuffApplied> sixFulmsUnderLevitate = new ModifiableCallout<BuffApplied>("Six Fulms Under (Not Levitating)", "Levitate").statusIcon(sixFulmsUnderId);
	private final ModifiableCallout<BuffApplied> sixFulmsUnder = new ModifiableCallout<BuffApplied>("Six Fulms Under (Levitating)", "6 Fulms Under").statusIcon(sixFulmsUnderId);
	private final RepeatSuppressor sixFulmsUnderSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> sixFulmsUnderSq = SqtTemplates.sq(10_000,
			BuffApplied.class, ba -> ba.buffIdMatches(sixFulmsUnderId) && ba.getTarget().isThePlayer() && sixFulmsUnderSupp.check(ba),
			(e1, s) -> {
				s.waitMs(5_000);
				s.updateCall(levitating ? sixFulmsUnder : sixFulmsUnderLevitate, e1);
			});

	private final ModifiableCallout<AbilityCastStart> antilightFirst = ModifiableCallout.durationBasedCall("Antilight (First, Not Levitating)", "Levitate");
	private final ModifiableCallout<AbilityCastStart> antilightFirstLevitating = ModifiableCallout.durationBasedCall("Antilight (First, Levitating)", "Antilight");
	private final ModifiableCallout<AbilityCastStart> antilightCenter = ModifiableCallout.durationBasedCall("Antilight (Center)", "Go Center, Don't Levitate");
	private final ModifiableCallout<AbilityCastStart> antilight = ModifiableCallout.durationBasedCall("Antilight", "Don't Levitate");

	@HandleEvents
	public void antilightCast(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x2502)) {
			return;
		}
		// 1回目は必ず青なので浮遊が必要。2回目以降は浮遊しなければ安全
		if (antiCounter == 0) {
			context.accept((levitating ? antilightFirstLevitating : antilightFirst).getModified(event));
		}
		else if (antiCounter % antilightCenterCycle == 1) {
			context.accept(antilightCenter.getModified(event));
		}
		else {
			context.accept(antilight.getModified(event));
		}
		antiCounter++;
	}

}
