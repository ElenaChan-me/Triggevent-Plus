package gg.xp.xivsupport.triggers.cactbot.shb;

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

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/trial/titan-un.ts をもとに移植
@CalloutRepo(name = "The Navel Unreal", duty = KnownDuty.TheNavelUnreal)
public class TitanUn extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long ROCK_THROW_TETHER = 0x07;

	private XivState state;

	public TitanUn(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheNavelUnreal);
	}

	private final ModifiableCallout<TetherEvent> rockThrowOnYou = new ModifiableCallout<>("Rock Throw (On You)", "Jail on You");
	private final ModifiableCallout<TetherEvent> rockThrow = new ModifiableCallout<>("Rock Throw", "Jails");
	private final RepeatSuppressor rockThrowSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void rockThrow(EventContext context, TetherEvent event) {
		if (!event.tetherIdMatches(ROCK_THROW_TETHER) || !rockThrowSupp.check(event)) {
			return;
		}
		boolean onYou = event.eitherTargetMatches(XivCombatant::isThePlayer);
		context.accept((onYou ? rockThrowOnYou : rockThrow).getModified(event));
	}

	@NpcCastCallout(0x58F9)
	private final ModifiableCallout<AbilityCastStart> upheaval = ModifiableCallout.durationBasedCall("Upheaval", "Knockback");

}
