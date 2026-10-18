package gg.xp.xivsupport.triggers.cactbot.shb;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/trial/ruby_weapon.ts をもとに移植
@CalloutRepo(name = "Cinder Drift", duty = KnownDuty.CinderDrift)
public class RubyWeapon extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public RubyWeapon(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.CinderDrift);
	}

	@NpcCastCallout(0x4AA8)
	private final ModifiableCallout<AbilityCastStart> optimizedUltima = ModifiableCallout.durationBasedCall("Optimized Ultima", "Raidwide");

	@NpcCastCallout(0x4AC7)
	private final ModifiableCallout<AbilityCastStart> stamp = ModifiableCallout.durationBasedCall("Stamp", "Buster on {event.target}");

	@NpcCastCallout(0x4A97)
	private final ModifiableCallout<AbilityCastStart> undermine = ModifiableCallout.durationBasedCall("Undermine", "Away from Lines");

	@NpcCastCallout(0x4A96)
	private final ModifiableCallout<AbilityCastStart> liquefaction = ModifiableCallout.durationBasedCall("Liquefaction", "Get on Lines");

	@NpcCastCallout(0x4AC6)
	private final ModifiableCallout<AbilityCastStart> rubyRay = ModifiableCallout.durationBasedCall("Ruby Ray", "Away from Front");

	private final ModifiableCallout<AbilityCastStart> homingLasersOnYou = ModifiableCallout.durationBasedCall("High-powered Homing Lasers (On You)", "Stack on You");
	private final ModifiableCallout<AbilityCastStart> homingLasers = ModifiableCallout.durationBasedCall("High-powered Homing Lasers", "Stack");
	private final RepeatSuppressor homingLasersSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void homingLasers(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x4AC5)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(homingLasersOnYou.getModified(event));
		}
		else if (homingLasersSupp.check(event)) {
			context.accept(homingLasers.getModified(event));
		}
	}

	@NpcCastCallout(0x4AA0)
	private final ModifiableCallout<AbilityCastStart> dynamics = ModifiableCallout.durationBasedCall("Dynamics", "Get Under");

	@PlayerHeadmarker(0x8B)
	private final ModifiableCallout<HeadMarkerEvent> homingLaser = new ModifiableCallout<>("Homing Laser", "Spread");

	@NpcCastCallout(0x4AA9)
	private final ModifiableCallout<AbilityCastStart> optimizedUltimaEnrage = ModifiableCallout.durationBasedCall("Optimized Ultima (Enrage)", "Enrage");

	@PlayerHeadmarker(0xE0)
	private final ModifiableCallout<HeadMarkerEvent> meteorStream = new ModifiableCallout<>("Meteor Stream", "Spread");

	@PlayerHeadmarker(0x17)
	private final ModifiableCallout<HeadMarkerEvent> bradamante = new ModifiableCallout<>("Bradamante", "Avoid Meteors with Laser");

	@NpcCastCallout(0x4AC8)
	private final ModifiableCallout<AbilityCastStart> outrage = ModifiableCallout.durationBasedCall("Outrage", "Raidwide");

}
