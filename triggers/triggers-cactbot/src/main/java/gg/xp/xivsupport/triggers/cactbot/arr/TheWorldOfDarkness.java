package gg.xp.xivsupport.triggers.cactbot.arr;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
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
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/alliance/the_world_of_darkness.ts をもとに移植
@CalloutRepo(name = "The World Of Darkness", duty = KnownDuty.TheWorldOfDarkness)
public class TheWorldOfDarkness extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long SULLEN = 0x27C;
	private static final long IREFUL = 0x27D;

	private XivState state;
	private boolean sullenDebuff;
	private boolean irefulDebuff;

	public TheWorldOfDarkness(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheWorldOfDarkness);
	}

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		sullenDebuff = false;
		irefulDebuff = false;
	}

	@HandleEvents
	public void debuffGained(EventContext context, BuffApplied event) {
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		if (event.buffIdMatches(SULLEN)) {
			sullenDebuff = true;
		}
		else if (event.buffIdMatches(IREFUL)) {
			irefulDebuff = true;
		}
	}

	@HandleEvents
	public void debuffLost(EventContext context, BuffRemoved event) {
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		if (event.buffIdMatches(SULLEN)) {
			sullenDebuff = false;
		}
		else if (event.buffIdMatches(IREFUL)) {
			irefulDebuff = false;
		}
	}

	private final ModifiableCallout<AbilityCastStart> doubleVisionRed = ModifiableCallout.durationBasedCall("Double Vision (Sullen)", "Get Behind (Red)");
	private final ModifiableCallout<AbilityCastStart> doubleVisionWhite = ModifiableCallout.durationBasedCall("Double Vision (Ireful)", "Get in Front (White)");

	// 今付いているデバフと逆の色に立って入れ替える
	@HandleEvents
	public void doubleVision(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0xCC8)) {
			if (sullenDebuff) {
				context.accept(doubleVisionRed.getModified(event));
			}
			else if (irefulDebuff) {
				context.accept(doubleVisionWhite.getModified(event));
			}
		}
	}

	@NpcCastCallout(value = {0xCD1, 0xDAB}, suppressMs = 100)
	private final ModifiableCallout<AbilityCastStart> mortalGaze = ModifiableCallout.durationBasedCall("Mortal Gaze", "Look Away");

	@PlayerStatusCallout(0xD2)
	private final ModifiableCallout<BuffApplied> doom = ModifiableCallout.<BuffApplied>durationBasedCall("Doom", "Run to Cleanse Circle").autoIcon();

	private final ModifiableCallout<HeadMarkerEvent> level100Flare = new ModifiableCallout<>("Level 100 Flare Marker", "Away from {event.target}");
	private final ModifiableCallout<HeadMarkerEvent> level150Death = new ModifiableCallout<>("Level 150 Death Marker", "Away from {event.target}");

	@HandleEvents
	public void markers(EventContext context, HeadMarkerEvent event) {
		if (event.getTarget().isThePlayer()) {
			return;
		}
		if (event.markerIdMatches(0x2C)) {
			context.accept(level100Flare.getModified(event));
		}
		else if (event.markerIdMatches(0x2D)) {
			context.accept(level150Death.getModified(event));
		}
	}
}
