package gg.xp.xivsupport.triggers.cactbot.sb;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/raid/o6s.ts をもとに移植
@CalloutRepo(name = "Sigmascape V20 Savage", duty = KnownDuty.O2S)
public class O6s extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long lastKissMarkerId = 0x17;

	private XivState state;

	public O6s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.O2S);
	}

	private boolean lastKissOnPlayer;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		lastKissOnPlayer = false;
	}

	@NpcCastCallout(0x2829)
	private final ModifiableCallout<AbilityCastStart> demonicShear = ModifiableCallout.durationBasedCall("Demonic Shear", "Buster on {event.target}");

	@PlayerHeadmarker(0x01)
	private final ModifiableCallout<HeadMarkerEvent> demonicStone = new ModifiableCallout<>("Demonic Stone", "Demonic Stone on You");

	private final ModifiableCallout<HeadMarkerEvent> lastKissMarker = new ModifiableCallout<>("Last Kiss (Marker)", "Last Kiss on You");

	@HandleEvents
	public void lastKissMarker(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(lastKissMarkerId)) {
			return;
		}
		lastKissOnPlayer = event.getTarget().isThePlayer();
		if (lastKissOnPlayer) {
			context.accept(lastKissMarker.getModified(event));
		}
	}

	private final ModifiableCallout<BuffApplied> lastKissPassed = new ModifiableCallout<BuffApplied>("Last Kiss (Passed)", "Last Kiss on You").statusIcon(0x5BF);

	// マーカーを付けられた本人も一瞬だけデバフが付くので、二重に知らせないようにする
	@HandleEvents
	public void lastKissBuff(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x5BF) && event.getTarget().isThePlayer() && !lastKissOnPlayer) {
			context.accept(lastKissPassed.getModified(event));
		}
	}

}
