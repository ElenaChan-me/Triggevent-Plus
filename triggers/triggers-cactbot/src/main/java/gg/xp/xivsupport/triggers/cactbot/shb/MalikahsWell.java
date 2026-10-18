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

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/dungeon/malikahs_well.ts をもとに移植
@CalloutRepo(name = "Malikahs Well", duty = KnownDuty.MalikahsWell)
public class MalikahsWell extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long STACK_MARKER = 0x3E;

	private XivState state;

	public MalikahsWell(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.MalikahsWell);
	}

	private final ModifiableCallout<AbilityCastStart> stoneFlailOnYou = ModifiableCallout.durationBasedCall("Stone Flail (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> stoneFlail = ModifiableCallout.durationBasedCall("Stone Flail", "Buster on {event.target}");
	private final ModifiableCallout<AbilityCastStart> effaceOnYou = ModifiableCallout.durationBasedCall("Efface (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> efface = ModifiableCallout.durationBasedCall("Efface", "Buster on {event.target}");
	private final ModifiableCallout<AbilityCastStart> deepDraught = ModifiableCallout.durationBasedCall("Deep Draught", "Interrupt {event.source}");

	@HandleEvents
	public void casts(EventContext context, AbilityCastStart event) {
		boolean onYou = event.getTarget().isThePlayer();
		if (event.abilityIdMatches(0x3CE5)) {
			context.accept((onYou ? stoneFlailOnYou : stoneFlail).getModified(event));
		}
		else if (event.abilityIdMatches(0x3CEB)) {
			context.accept((onYou ? effaceOnYou : efface).getModified(event));
		}
		else if (event.abilityIdMatches(0x4188) && state.playerJobMatches(Job::caresAboutInterrupt)) {
			context.accept(deepDraught.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> headToss = new ModifiableCallout<>("Head Toss", "Stack on {event.target}");

	@HandleEvents
	public void headToss(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(STACK_MARKER)) {
			context.accept(headToss.getModified(event));
		}
	}

	@NpcCastCallout(0x3CE7)
	private final ModifiableCallout<AbilityCastStart> rightRound = ModifiableCallout.durationBasedCall("Right Round", "Melee Knockback");

	@NpcCastCallout(0x3CEC)
	private final ModifiableCallout<AbilityCastStart> highPressure = ModifiableCallout.durationBasedCall("High Pressure", "Knockback");

	@NpcCastCallout(0x3CEF)
	private final ModifiableCallout<AbilityCastStart> swiftSpill = ModifiableCallout.durationBasedCall("Swift Spill", "Get Behind");

	@NpcCastCallout(0x3CF1)
	private final ModifiableCallout<AbilityCastStart> intestinalCrank = ModifiableCallout.durationBasedCall("Intestinal Crank", "Raidwide");

}
