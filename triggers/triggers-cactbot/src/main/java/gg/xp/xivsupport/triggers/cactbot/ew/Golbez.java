package gg.xp.xivsupport.triggers.cactbot.ew;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.models.ArenaSector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/trial/golbez.ts をもとに移植
@CalloutRepo(name = "The Voidcast Dais", duty = KnownDuty.TheVoidcastDais)
public class Golbez extends AutoChildEventHandler implements FilteredEventHandler {

	private static final int GALE_SPHERE_CLONES = 4;

	private XivState state;

	public Golbez(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheVoidcastDais);
	}

	private final ModifiableCallout<AbilityUsedEvent> terrastorm = new ModifiableCallout<>("Terrastorm", "Avoid Meteor");

	@HandleEvents
	public void terrastormUsed(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x8463) && event.isFirstTarget()) {
			context.accept(terrastorm.getModified(event));
		}
	}

	@NpcCastCallout(0x846B)
	private final ModifiableCallout<AbilityCastStart> crescentBlade = ModifiableCallout.durationBasedCall("Crescent Blade", "Get Behind");

	@NpcCastCallout(0x84AC)
	private final ModifiableCallout<AbilityCastStart> voidMeteor = ModifiableCallout.durationBasedCall("Void Meteor", "Buster on {event.target}");

	@NpcCastCallout(0x84B2)
	private final ModifiableCallout<AbilityCastStart> bindingCold = ModifiableCallout.durationBasedCall("Binding Cold", "Raidwide + Bleed");

	@NpcCastCallout(0x8471)
	private final ModifiableCallout<AbilityCastStart> blackFang = ModifiableCallout.durationBasedCall("Black Fang", "Big Raidwide");

	@NpcCastCallout(0x8487)
	private final ModifiableCallout<AbilityCastStart> shadowCrescent = ModifiableCallout.durationBasedCall("Shadow Crescent", "Get Behind, then Out");

	private final ModifiableCallout<AbilityUsedEvent> galeSphere = new ModifiableCallout<>("Gale Sphere", "Clones: {dir1} => {dir2} => {dir3} => {dir4}");

	private static ArenaSector galeSphereDir(AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x844F)) {
			return ArenaSector.NORTH;
		}
		else if (event.abilityIdMatches(0x8450)) {
			return ArenaSector.EAST;
		}
		else if (event.abilityIdMatches(0x8451)) {
			return ArenaSector.WEST;
		}
		else {
			return ArenaSector.SOUTH;
		}
	}

	// 分身4体の動きが揃ってから順番をまとめて知らせる
	@AutoFeed
	private final SequentialTrigger<BaseEvent> galeSphereSq = SqtTemplates.sq(30_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x844F, 0x8450, 0x8451, 0x8452) && aue.isFirstTarget(),
			(e1, s) -> {
				List<AbilityUsedEvent> clones = new ArrayList<>();
				clones.add(e1);
				clones.addAll(s.waitEvents(GALE_SPHERE_CLONES - 1, AbilityUsedEvent.class,
						aue -> aue.abilityIdMatches(0x844F, 0x8450, 0x8451, 0x8452) && aue.isFirstTarget()));
				s.updateCall(galeSphere.getModified(clones.get(GALE_SPHERE_CLONES - 1), Map.of(
						"dir1", galeSphereDir(clones.get(0)),
						"dir2", galeSphereDir(clones.get(1)),
						"dir3", galeSphereDir(clones.get(2)),
						"dir4", galeSphereDir(clones.get(3)))));
			});

	@NpcCastCallout(0x8482)
	private final ModifiableCallout<AbilityCastStart> eventideFall = ModifiableCallout.durationBasedCall("Eventide Fall", "Stack");

	@NpcCastCallout(0x8495)
	private final ModifiableCallout<AbilityCastStart> immolatingShade = ModifiableCallout.durationBasedCall("Immolating Shade", "Out, then Stack on {event.target}");

}
