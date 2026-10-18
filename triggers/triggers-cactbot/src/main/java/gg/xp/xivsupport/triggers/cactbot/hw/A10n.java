package gg.xp.xivsupport.triggers.cactbot.hw;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/raid/a10n.ts をもとに移植
@CalloutRepo(name = "Alexander The Breath Of The Creator", duty = KnownDuty.AlexanderTheBreathOfTheCreator)
public class A10n extends AutoChildEventHandler implements FilteredEventHandler {

	// 罠の起動アクションは複数対象で出ることがあるため、まとめて1回にする
	private static final long TRAP_SUPPRESS_MS = 1_000;

	private XivState state;

	public A10n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AlexanderTheBreathOfTheCreator);
	}

	@NpcAbilityUsedCallout(value = 0x1AB1, suppressMs = TRAP_SUPPRESS_MS)
	private final ModifiableCallout<AbilityUsedEvent> frostLaserTrap = new ModifiableCallout<>("Frost Laser Trap", "Frost Lasers");

	@NpcAbilityUsedCallout(value = 0x1AB0, suppressMs = TRAP_SUPPRESS_MS)
	private final ModifiableCallout<AbilityUsedEvent> ceilingWeightTrap = new ModifiableCallout<>("Ceiling Weight Trap", "Ceiling Weight");

	@NpcAbilityUsedCallout(value = 0x1AB8, suppressMs = TRAP_SUPPRESS_MS)
	private final ModifiableCallout<AbilityUsedEvent> singleChargeIn = new ModifiableCallout<>("Single Charge (In)", "In");

	@NpcAbilityUsedCallout(value = 0x1AB9, suppressMs = TRAP_SUPPRESS_MS)
	private final ModifiableCallout<AbilityUsedEvent> singleChargeOut = new ModifiableCallout<>("Single Charge (Out)", "Out");

	@NpcCastCallout(0x1ACF)
	private final ModifiableCallout<AbilityCastStart> gobrushRushgob = ModifiableCallout.durationBasedCall("Gobrush Rushgob", "Buster on {event.target}");

	@PlayerHeadmarker(0x19)
	private final ModifiableCallout<HeadMarkerEvent> criticalWrath = new ModifiableCallout<>("Critical Wrath", "Spread");

	private final ModifiableCallout<HeadMarkerEvent> bombToss = new ModifiableCallout<>("Bomb Toss", "Stack on {event.target}");

	@HandleEvents
	public void bombToss(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E)) {
			context.accept(bombToss.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> gobslashOnYou = new ModifiableCallout<>("Gobslash Slicetops (on You)", "Laser on You, Get Away");
	private final ModifiableCallout<HeadMarkerEvent> gobslashOther = new ModifiableCallout<>("Gobslash Slicetops (not on You)", "Avoid Prey Laser");

	@HandleEvents
	public void gobslashSlicetops(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(0x17)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(gobslashOnYou.getModified(event));
		}
		else {
			context.accept(gobslashOther.getModified(event));
		}
	}

}
