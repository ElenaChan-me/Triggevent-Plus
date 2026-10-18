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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/raid/t12.ts をもとに移植
@CalloutRepo(name = "The Final Coil Of Bahamut Turn3", duty = KnownDuty.TheFinalCoilOfBahamutTurn3)
public class T12 extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public T12(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheFinalCoilOfBahamutTurn3);
	}

	private final ModifiableCallout<AbilityCastStart> revelationOnYou = ModifiableCallout.durationBasedCall("Revelation (on You)", "Revelation on You");
	private final ModifiableCallout<AbilityCastStart> revelationOn = ModifiableCallout.durationBasedCall("Revelation", "Away from {event.target}");

	@HandleEvents
	public void revelation(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0xB87)) {
			if (event.getTarget().isThePlayer()) {
				context.accept(revelationOnYou.getModified(event));
			}
			else {
				context.accept(revelationOn.getModified(event));
			}
		}
	}

	@NpcCastCallout(0xB8C)
	private final ModifiableCallout<AbilityCastStart> blackfire = ModifiableCallout.durationBasedCall("Blackfire", "Blackfire Spread");

	@PlayerHeadmarker(0x20)
	private final ModifiableCallout<HeadMarkerEvent> whitefire = new ModifiableCallout<>("Whitefire", "Whitefire on You");

	@PlayerHeadmarker(0x21)
	private final ModifiableCallout<HeadMarkerEvent> bluefire = new ModifiableCallout<>("Bluefire", "Bluefire Away");

	private final ModifiableCallout<BuffApplied> chainOnYou = ModifiableCallout.<BuffApplied>durationBasedCall("Chain of Purgatory (on You)", "Chain on You").autoIcon();
	private final ModifiableCallout<BuffApplied> chainOn = ModifiableCallout.<BuffApplied>durationBasedCall("Chain of Purgatory", "Chain on {event.target}").autoIcon();

	@HandleEvents
	public void chain(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x24D)) {
			if (event.getTarget().isThePlayer()) {
				context.accept(chainOnYou.getModified(event));
			}
			else {
				context.accept(chainOn.getModified(event));
			}
		}
	}
}
