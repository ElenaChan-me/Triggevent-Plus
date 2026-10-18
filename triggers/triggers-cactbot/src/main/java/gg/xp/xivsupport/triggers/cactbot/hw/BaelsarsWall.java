package gg.xp.xivsupport.triggers.cactbot.hw;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.Job;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/dungeon/baelsars_wall.ts をもとに移植
@CalloutRepo(name = "Baelsars Wall", duty = KnownDuty.BaelsarsWall)
public class BaelsarsWall extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public BaelsarsWall(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.BaelsarsWall);
	}

	// 1ボス目（Magitek Predator）

	@NpcCastCallout(0x1CB2)
	private final ModifiableCallout<AbilityCastStart> magitekClaw = ModifiableCallout.durationBasedCall("Magitek Claw", "Buster on {event.target}");

	@NpcCastCallout(0x1CB3)
	private final ModifiableCallout<AbilityCastStart> magitekRay = ModifiableCallout.durationBasedCall("Magitek Ray", "Away from Front");

	private final ModifiableCallout<AbilityCastStart> needleBurst = ModifiableCallout.durationBasedCall("Needle Burst", "Stun {event.source}");

	@HandleEvents
	public void needleBurst(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x1DC8) && !event.getSource().isPc()
		    && state.playerJobMatches(job -> job.isTank() || job.isMeleeDps() || job == Job.BLU)) {
			context.accept(needleBurst.getModified(event));
		}
	}

	@NpcCastCallout(0x1CBC)
	private final ModifiableCallout<AbilityCastStart> launcher = ModifiableCallout.durationBasedCall("Launcher", "Raidwide");

	// 2ボス目（Armored Weapon）

	@PlayerStatusCallout(0x46C)
	private final ModifiableCallout<BuffApplied> dynamicSensoryJammer = ModifiableCallout.<BuffApplied>durationBasedCall("Dynamic Sensory Jammer", "Stop Everything").autoIcon();

	// 3ボス目（The Griffin）

	@NpcCastCallout(0x1CC3)
	private final ModifiableCallout<AbilityCastStart> griffinBeak = ModifiableCallout.durationBasedCall("Griffin Beak", "Raidwide");

	@NpcCastCallout(0x1CC4)
	private final ModifiableCallout<AbilityCastStart> flashPowder = ModifiableCallout.durationBasedCall("Flash Powder", "Look Away from {event.source}");

	@NpcCastCallout(0x1CC2)
	private final ModifiableCallout<AbilityCastStart> griffinClaw = ModifiableCallout.durationBasedCall("Griffin Claw", "Buster on {event.target}");

	@PlayerHeadmarker(0x1CC4)
	private final ModifiableCallout<HeadMarkerEvent> bigBoot = new ModifiableCallout<>("Big Boot", "Knockback on You");

	private final ModifiableCallout<AbilityCastStart> restraintCollar = ModifiableCallout.durationBasedCall("Restraint Collar", "Break Chain on {event.target}");

	@HandleEvents
	public void restraintCollar(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x1CC8) && !event.getSource().isPc() && !event.getTarget().isThePlayer()) {
			context.accept(restraintCollar.getModified(event));
		}
	}

}
