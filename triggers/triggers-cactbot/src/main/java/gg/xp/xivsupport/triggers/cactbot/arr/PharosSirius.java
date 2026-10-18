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
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.AnyPlayerStatusCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/dungeon/pharos_sirius.ts をもとに移植
@CalloutRepo(name = "Pharos Sirius", duty = KnownDuty.PharosSirius)
public class PharosSirius extends AutoChildEventHandler implements FilteredEventHandler {

	// 3スタックになると間もなく爆発する
	private static final long crystalExplodeStacks = 3;

	private XivState state;

	public PharosSirius(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.PharosSirius);
	}

	@NpcCastCallout(0x5CF)
	private final ModifiableCallout<AbilityCastStart> deathlyCadenza = ModifiableCallout.durationBasedCall("Deathly Cadenza", "In");

	@NpcCastCallout(0x5CC)
	private final ModifiableCallout<AbilityCastStart> feralLunge = ModifiableCallout.durationBasedCall("Feral Lunge", "Out");

	private final ModifiableCallout<BuffApplied> corruptedCrystal = new ModifiableCallout<BuffApplied>("Corrupted Crystal", "Spread: Stacks Explode Soon").autoIcon();

	@HandleEvents
	public void corruptedCrystal(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x176) && event.getTarget().isThePlayer() && event.getStacks() == crystalExplodeStacks) {
			context.accept(corruptedCrystal.getModified(event));
		}
	}

	@AnyPlayerStatusCallout(0x172)
	private final ModifiableCallout<BuffApplied> doom = ModifiableCallout.<BuffApplied>durationBasedCall("Doom", "Heal {event.target} to Full").autoIcon();
}
