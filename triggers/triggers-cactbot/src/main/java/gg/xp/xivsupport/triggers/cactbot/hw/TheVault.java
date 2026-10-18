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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/dungeon/the_vault.ts をもとに移植
@CalloutRepo(name = "The Vault", duty = KnownDuty.TheVault)
public class TheVault extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public TheVault(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheVault);
	}

	private boolean playerCanStun() {
		return state.playerJobMatches(job -> job.isTank() || job.isMeleeDps() || job == Job.BLU);
	}

	@NpcCastCallout(0x101E)
	private final ModifiableCallout<AbilityCastStart> holiestOfHoly = ModifiableCallout.durationBasedCall("Holiest of Holy", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> holyShieldBash = ModifiableCallout.durationBasedCall("Holy Shield Bash", "Heal and Shield {event.target}");

	@HandleEvents
	public void holyShieldBash(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x101F) && !event.getSource().isPc() && state.playerJobMatches(Job::isHealer)) {
			context.accept(holyShieldBash.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> executionOnYou = new ModifiableCallout<>("Execution (on You)", "Away from Group");
	private final ModifiableCallout<HeadMarkerEvent> executionOther = new ModifiableCallout<>("Execution (not on You)", "Away from {event.target}");

	@HandleEvents
	public void execution(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(0x20)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(executionOnYou.getModified(event));
		}
		else {
			context.accept(executionOther.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> blackNebula = ModifiableCallout.durationBasedCall("Black Nebula", "Stun {event.source}");

	@HandleEvents
	public void blackNebula(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x1042) && !event.getSource().isPc() && playerCanStun()) {
			context.accept(blackNebula.getModified(event));
		}
	}

	@NpcCastCallout(0x1027)
	private final ModifiableCallout<AbilityCastStart> faithUnmoving = ModifiableCallout.durationBasedCall("Faith Unmoving", "Knockback");

	private final ModifiableCallout<TetherEvent> dimensionalTorsion = new ModifiableCallout<>("Dimensional Torsion", "Away from Rifts");

	private final RepeatSuppressor torsionSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void dimensionalTorsion(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x01) && event.eitherTargetMatches(XivCombatant::isThePlayer) && torsionSupp.check(event)) {
			context.accept(dimensionalTorsion.getModified(event));
		}
	}

	@NpcCastCallout(0x1035)
	private final ModifiableCallout<AbilityCastStart> altarPyre = ModifiableCallout.durationBasedCall("Altar Pyre", "Raidwide");

	@PlayerHeadmarker(0x61)
	private final ModifiableCallout<HeadMarkerEvent> holyChains = new ModifiableCallout<>("Holy Chains", "Break Chain");

}
