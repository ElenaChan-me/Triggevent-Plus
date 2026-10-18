package gg.xp.xivsupport.triggers.cactbot.sb;

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
import gg.xp.xivsupport.models.XivCombatant;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/dungeon/ala_mhigo.ts をもとに移植
@CalloutRepo(name = "Ala Mhigo", duty = KnownDuty.AlaMhigo)
public class AlaMhigo extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public AlaMhigo(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AlaMhigo);
	}

	@NpcCastCallout(0x204D)
	private final ModifiableCallout<AbilityCastStart> electromagneticField = ModifiableCallout.durationBasedCall("Electromagnetic Field", "Raidwide");

	@NpcCastCallout(0x204F)
	private final ModifiableCallout<AbilityCastStart> manaBurst = ModifiableCallout.durationBasedCall("Mana Burst", "Raidwide");

	@NpcCastCallout(0x205D)
	private final ModifiableCallout<AbilityCastStart> demimagicks = ModifiableCallout.durationBasedCall("Demimagicks", "Spread");

	@NpcCastCallout({0x2066, 0x2587})
	private final ModifiableCallout<AbilityCastStart> storm = ModifiableCallout.durationBasedCall("Art of the Storm", "Out of Blue Circle");

	@NpcCastCallout({0x2065, 0x2586})
	private final ModifiableCallout<AbilityCastStart> swell = ModifiableCallout.durationBasedCall("Art of the Swell", "Knockback");

	@NpcCastCallout({0x2068, 0x2588})
	private final ModifiableCallout<AbilityCastStart> sword = ModifiableCallout.durationBasedCall("Art of the Sword", "Proteans");

	private final ModifiableCallout<TetherEvent> lightlessSpark = new ModifiableCallout<>("Lightless Spark", "Face Tether Out");

	@HandleEvents
	public void lightlessSparkTether(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x29) && event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			context.accept(lightlessSpark.getModified(event));
		}
	}

	@NpcCastCallout(0x206D)
	private final ModifiableCallout<AbilityCastStart> concentrativity = ModifiableCallout.durationBasedCall("Concentrativity", "Raidwide");

}
