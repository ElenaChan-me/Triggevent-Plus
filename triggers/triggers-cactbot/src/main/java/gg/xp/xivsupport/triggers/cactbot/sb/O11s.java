package gg.xp.xivsupport.triggers.cactbot.sb;

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
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import org.jetbrains.annotations.Nullable;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/raid/o11s.ts をもとに移植
@CalloutRepo(name = "Alphascape V30 Savage", duty = KnownDuty.O11S)
public class O11s extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long STARBOARD_1 = 0x3262;
	private static final long STARBOARD_2 = 0x3263;
	private static final long LARBOARD_1 = 0x3264;
	private static final long LARBOARD_2 = 0x3265;
	private static final long STARBOARD_SURGE = 0x3266;
	private static final long LARBOARD_SURGE = 0x3268;
	private static final int CANNON_RESET_MS = 15_000;
	private static final int SURGE_SECOND_DELAY_MS = 4_000;

	private XivState state;

	public O11s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.O11S);
	}

	// null は1発目をまだ見ていない状態
	private @Nullable Boolean lastWasStarboard;

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		lastWasStarboard = null;
	}

	@NpcCastCallout(0x326D)
	private final ModifiableCallout<AbilityCastStart> mustardBomb = ModifiableCallout.durationBasedCall("Mustard Bomb", "Buster on {event.target}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> cannonCleanupSq = SqtTemplates.sq(CANNON_RESET_MS + 5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(STARBOARD_1, LARBOARD_1),
			(e1, s) -> {
				s.waitMs(CANNON_RESET_MS);
				lastWasStarboard = null;
			});

	private final ModifiableCallout<AbilityCastStart> starboardCannon1 = ModifiableCallout.durationBasedCall("Starboard Cannon 1", "Left");
	private final ModifiableCallout<AbilityCastStart> larboardCannon1 = ModifiableCallout.durationBasedCall("Larboard Cannon 1", "Right");
	private final ModifiableCallout<AbilityCastStart> starboardCannon2Move = ModifiableCallout.durationBasedCall("Starboard Cannon 2: Move", "Move (Left)");
	private final ModifiableCallout<AbilityCastStart> starboardCannon2Stay = ModifiableCallout.durationBasedCall("Starboard Cannon 2: Stay", "Stay (Left)");
	private final ModifiableCallout<AbilityCastStart> larboardCannon2Stay = ModifiableCallout.durationBasedCall("Larboard Cannon 2: Stay", "Stay (Right)");
	private final ModifiableCallout<AbilityCastStart> larboardCannon2Move = ModifiableCallout.durationBasedCall("Larboard Cannon 2: Move", "Move (Right)");

	@HandleEvents
	public void cannons(EventContext context, AbilityCastStart event) {
		boolean starboard = event.abilityIdMatches(STARBOARD_1, STARBOARD_2);
		if (!starboard && !event.abilityIdMatches(LARBOARD_1, LARBOARD_2)) {
			return;
		}
		// 1発目のログが抜けても2発目で左右だけは伝えられるよう、2発目も1発目として扱えるようにしておく
		Boolean last = lastWasStarboard;
		if (last == null) {
			context.accept((starboard ? starboardCannon1 : larboardCannon1).getModified(event));
			lastWasStarboard = starboard;
		}
		else if (event.abilityIdMatches(STARBOARD_2)) {
			context.accept((last ? starboardCannon2Move : starboardCannon2Stay).getModified(event));
		}
		else if (event.abilityIdMatches(LARBOARD_2)) {
			context.accept((last ? larboardCannon2Stay : larboardCannon2Move).getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> starboardSurge1 = ModifiableCallout.durationBasedCall("Starboard Surge", "Left (then opposite)");
	private final ModifiableCallout<AbilityCastStart> starboardSurge2 = new ModifiableCallout<>("Starboard Surge: Second", "Opposite (Left)");
	private final ModifiableCallout<AbilityCastStart> larboardSurge1 = ModifiableCallout.durationBasedCall("Larboard Surge", "Right (then opposite)");
	private final ModifiableCallout<AbilityCastStart> larboardSurge2 = new ModifiableCallout<>("Larboard Surge: Second", "Opposite (Right)");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> surgeSq = SqtTemplates.sq(SURGE_SECOND_DELAY_MS + 5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(STARBOARD_SURGE, LARBOARD_SURGE),
			(e1, s) -> {
				boolean starboard = e1.abilityIdMatches(STARBOARD_SURGE);
				s.updateCall(starboard ? starboardSurge1 : larboardSurge1, e1);
				s.waitMs(SURGE_SECOND_DELAY_MS);
				s.updateCall(starboard ? starboardSurge2 : larboardSurge2, e1);
			});

}
