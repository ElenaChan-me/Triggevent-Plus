package gg.xp.xivsupport.triggers.cactbot.arr;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/trial/titan-ex.ts をもとに移植
@CalloutRepo(name = "The Navel Extreme", duty = KnownDuty.TheNavelExtreme)
public class TitanEx extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public TitanEx(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheNavelExtreme);
	}

	private final ModifiableCallout<TetherEvent> jailOnYou = new ModifiableCallout<>("Rock Throw (on You)", "Jail on You");
	private final ModifiableCallout<TetherEvent> jails = new ModifiableCallout<>("Rock Throw", "Jails");
	private final RepeatSuppressor rockThrowSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void rockThrow(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x07) && rockThrowSupp.check(event)) {
			if (event.eitherTargetMatches(XivCombatant::isThePlayer)) {
				context.accept(jailOnYou.getModified(event));
			}
			else {
				context.accept(jails.getModified(event));
			}
		}
	}

	@NpcCastCallout(0x5BA)
	private final ModifiableCallout<AbilityCastStart> upheaval = ModifiableCallout.durationBasedCall("Upheaval", "Knockback");
}
