package gg.xp.xivsupport.triggers.cactbot.arr;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.SystemLogMessageEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/raid/t8.ts をもとに移植
@CalloutRepo(name = "The Second Coil Of Bahamut Turn3", duty = KnownDuty.TheSecondCoilOfBahamutTurn3)
public class T8 extends AutoChildEventHandler implements FilteredEventHandler {

	// 「地雷が散布された」のシステムログ
	private static final long LANDMINE_LOG_ID = 0x8E6;

	private XivState state;
	// 同じ人が複数回踏んでも1個として数える
	private final Set<Long> landmineTargets = new HashSet<>();

	public T8(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheSecondCoilOfBahamutTurn3);
	}

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		landmineTargets.clear();
	}

	private final ModifiableCallout<HeadMarkerEvent> stack = new ModifiableCallout<>("Stack Marker", "Stack on {event.target}");

	@HandleEvents
	public void stackMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x11)) {
			context.accept(stack.getModified(event));
		}
	}

	private final ModifiableCallout<SystemLogMessageEvent> landmineStart = new ModifiableCallout<>("Landmine Start", "Explode Landmines");
	private final ModifiableCallout<AbilityUsedEvent> landmineExplosion = new ModifiableCallout<>("Landmine Explosion", "{num}", "{num} / 3");

	@HandleEvents
	public void landmineStart(EventContext context, SystemLogMessageEvent event) {
		if (event.getId() == LANDMINE_LOG_ID) {
			landmineTargets.clear();
			context.accept(landmineStart.getModified(event));
		}
	}

	@HandleEvents
	public void landmineExplosion(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x7D1) && !event.getSource().isPc()) {
			if (landmineTargets.add(event.getTarget().getId())) {
				context.accept(landmineExplosion.getModified(event, Map.of("num", landmineTargets.size())));
			}
		}
	}

	private final ModifiableCallout<TetherEvent> homingMissile = new ModifiableCallout<>("Homing Missile Tether", "Missile Tether (on {player})");
	private final RepeatSuppressor homingMissileSupp = new RepeatSuppressor(Duration.ofSeconds(6));

	@HandleEvents
	public void homingMissile(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x05) && homingMissileSupp.check(event)) {
			XivCombatant player = event.getTargetMatching(XivCombatant::isPc);
			if (player != null) {
				context.accept(homingMissile.getModified(event, Map.of("player", player)));
			}
		}
	}

	private final ModifiableCallout<AbilityCastStart> brainjackOnYou = ModifiableCallout.durationBasedCall("Brainjack (on You)", "Brainjack on You");
	private final ModifiableCallout<AbilityCastStart> brainjackOn = ModifiableCallout.durationBasedCall("Brainjack", "Brainjack on {event.target}");
	private final ModifiableCallout<AbilityCastStart> allaganFieldOnYou = ModifiableCallout.durationBasedCall("Allagan Field (on You)", "Allagan Field on You");
	private final ModifiableCallout<AbilityCastStart> allaganFieldOn = ModifiableCallout.durationBasedCall("Allagan Field", "Allagan Field on {event.target}");

	@HandleEvents
	public void avatarCasts(EventContext context, AbilityCastStart event) {
		boolean onYou = event.getTarget().isThePlayer();
		if (event.abilityIdMatches(0x7C3)) {
			context.accept((onYou ? brainjackOnYou : brainjackOn).getModified(event));
		}
		else if (event.abilityIdMatches(0x7C4)) {
			context.accept((onYou ? allaganFieldOnYou : allaganFieldOn).getModified(event));
		}
	}
}
