package gg.xp.xivsupport.triggers.cactbot.shb;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.Job;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/dungeon/dohn_mheg.ts をもとに移植
@CalloutRepo(name = "Dohn Mheg", duty = KnownDuty.DohnMheg)
public class DohnMheg extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public DohnMheg(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.DohnMheg);
	}

	private boolean canStun() {
		return state.playerJobMatches(job -> job.isTank() || job.isMeleeDps());
	}

	// 道中

	private final ModifiableCallout<AbilityCastStart> wateringWheel = ModifiableCallout.durationBasedCall("Watering Wheel", "Interrupt {event.source}");
	private final ModifiableCallout<AbilityCastStart> straightPunch = ModifiableCallout.durationBasedCall("Straight Punch", "Stun {event.source}");
	private final ModifiableCallout<AbilityCastStart> proboscis = ModifiableCallout.durationBasedCall("Proboscis", "Stun {event.source}");
	private final ModifiableCallout<AbilityCastStart> torpedo = ModifiableCallout.durationBasedCall("Torpedo", "Stun {event.source}");

	@HandleEvents
	public void trashCasts(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x3DAA)) {
			if (state.playerJobMatches(Job::caresAboutInterrupt)) {
				context.accept(wateringWheel.getModified(event));
			}
			return;
		}
		if (!canStun()) {
			return;
		}
		switch ((int) event.getAbility().getId()) {
			case 0x3DAB -> context.accept(straightPunch.getModified(event));
			case 0x3DAF -> context.accept(proboscis.getModified(event));
			case 0x3DB5 -> context.accept(torpedo.getModified(event));
		}
	}

	// 1ボス

	@NpcCastCallout(0x2299)
	private final ModifiableCallout<AbilityCastStart> candyCane = ModifiableCallout.durationBasedCall("Candy Cane", "Buster on {event.target}");

	@NpcCastCallout(0x1E8E)
	private final ModifiableCallout<AbilityCastStart> landsblood = ModifiableCallout.durationBasedCall("Landsblood", "Raidwide");

	private final ModifiableCallout<HeadMarkerEvent> leapStack = new ModifiableCallout<>("Leap Stack", "Stack on {event.target}");

	@HandleEvents
	public void leapStack(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E)) {
			context.accept(leapStack.getModified(event));
		}
	}

	// 2ボス

	@NpcCastCallout(0x22D3)
	private final ModifiableCallout<AbilityCastStart> timber = ModifiableCallout.durationBasedCall("Timber", "Raidwide");

	// 3ボス

	@NpcCastCallout(0x35A4)
	private final ModifiableCallout<AbilityCastStart> cripplingBlow = ModifiableCallout.durationBasedCall("Crippling Blow", "Buster on {event.target}");

	@NpcCastCallout(0x34F0)
	private final ModifiableCallout<AbilityCastStart> impChoir = ModifiableCallout.durationBasedCall("Imp Choir", "Look Away");

	@NpcCastCallout(0x34EF)
	private final ModifiableCallout<AbilityCastStart> toadChoir = ModifiableCallout.durationBasedCall("Toad Choir", "Away from Front");

	@NpcCastCallout(0x358C)
	private final ModifiableCallout<AbilityCastStart> virtuosicCapriccio = ModifiableCallout.durationBasedCall("Virtuosic Capriccio", "Raidwide");

}
