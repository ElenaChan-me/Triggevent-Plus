package gg.xp.xivsupport.triggers.cactbot.ew;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.Job;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/dungeon/the_aitiascope.ts をもとに移植
@CalloutRepo(name = "The Aitiascope", duty = KnownDuty.TheAitiascope)
public class TheAitiascope extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public TheAitiascope(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheAitiascope);
	}

	// リウィア

	@NpcCastCallout(0x6448)
	private final ModifiableCallout<AbilityCastStart> frustration = ModifiableCallout.durationBasedCall("Frustration", "Raidwide");

	@NpcCastCallout(0x6442)
	private final ModifiableCallout<AbilityCastStart> aglaeaClimbNeSw = ModifiableCallout.durationBasedCall("Aglaea Climb (NE/SW)", "Back Right / Front Left");

	@NpcCastCallout(0x6443)
	private final ModifiableCallout<AbilityCastStart> aglaeaClimbNwSe = ModifiableCallout.durationBasedCall("Aglaea Climb (NW/SE)", "Back Left / Front Right");

	private final ModifiableCallout<AbilityCastStart> aglaeaBiteOnYou = ModifiableCallout.durationBasedCall("Aglaea Bite (On You)", "Tank Cleave on You");
	private final ModifiableCallout<AbilityCastStart> aglaeaBiteAvoid = ModifiableCallout.durationBasedCall("Aglaea Bite (Not on You)", "Avoid Tank Cleave");

	@HandleEvents
	public void aglaeaBite(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x6449)) {
			context.accept((event.getTarget().isThePlayer() ? aglaeaBiteOnYou : aglaeaBiteAvoid).getModified(event));
		}
	}

	@NpcCastCallout(0x644D)
	private final ModifiableCallout<AbilityCastStart> ignisOdi = ModifiableCallout.durationBasedCall("Ignis Odi", "Stack on {event.target}");

	// リタティン

	@NpcCastCallout(0x6455)
	private final ModifiableCallout<AbilityCastStart> tartareanImpact = ModifiableCallout.durationBasedCall("Tartarean Impact", "Raidwide");

	@NpcCastCallout(0x6450)
	private final ModifiableCallout<AbilityCastStart> shieldSkewer = ModifiableCallout.durationBasedCall("Shield Skewer", "Hide Behind Broken Crystal");

	@NpcCastCallout(0x6452)
	private final ModifiableCallout<AbilityCastStart> shrapnelShell = ModifiableCallout.durationBasedCall("Shrapnel Shell", "Away from Crosshairs");

	@NpcCastCallout(0x6456)
	private final ModifiableCallout<AbilityCastStart> anvilOfTartarus = ModifiableCallout.durationBasedCall("Anvil of Tartarus", "Buster on {event.target}");

	private final ModifiableCallout<AbilityCastStart> lostOpportunity = ModifiableCallout.durationBasedCall("Lost Opportunity (Stun)", "Stun {event.source}");

	@HandleEvents
	public void lostOpportunity(EventContext context, AbilityCastStart event) {
		// スタンを持っているロールにだけ知らせる
		if (event.abilityIdMatches(0x6B1F) && state.playerJobMatches(job -> job.isTank() || job.isMeleeDps() || job == Job.BLU)) {
			context.accept(lostOpportunity.getModified(event));
		}
	}

	// アモン

	@NpcCastCallout(0x6464)
	private final ModifiableCallout<AbilityCastStart> darkForte = ModifiableCallout.durationBasedCall("Dark Forte", "Buster on {event.target}");

	private final ModifiableCallout<AbilityUsedEvent> dreamsOfIce = new ModifiableCallout<>("Dreams of Ice", "Hide Behind Ice");

	@HandleEvents
	public void dreamsOfIce(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x6C6C) && event.isFirstTarget()) {
			context.accept(dreamsOfIce.getModified(event));
		}
	}

	@NpcCastCallout(0x6465)
	private final ModifiableCallout<AbilityCastStart> entracte = ModifiableCallout.durationBasedCall("Entr'acte", "Raidwide");

	@NpcCastCallout(0x6460)
	private final ModifiableCallout<AbilityCastStart> rightFiragaForte = ModifiableCallout.durationBasedCall("Right Firaga Forte", "Right");

	@NpcCastCallout(0x6461)
	private final ModifiableCallout<AbilityCastStart> leftFiragaForte = ModifiableCallout.durationBasedCall("Left Firaga Forte", "Left");

}
