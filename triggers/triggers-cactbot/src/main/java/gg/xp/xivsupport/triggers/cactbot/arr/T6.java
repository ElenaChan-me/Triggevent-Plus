package gg.xp.xivsupport.triggers.cactbot.arr;

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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.state.combatstate.StatusEffectRepository;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.models.XivCombatant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/raid/t6.ts をもとに移植
@CalloutRepo(name = "The Second Coil Of Bahamut Turn1", duty = KnownDuty.TheSecondCoilOfBahamutTurn1)
public class T6 extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long HONEY_GLAZED = 0x1BE;

	private XivState state;
	private StatusEffectRepository buffs;

	public T6(XivState state, StatusEffectRepository buffs) {
		this.state = state;
		this.buffs = buffs;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheSecondCoilOfBahamutTurn1);
	}

	// 茨のテザーで繋がった相手を記録しておき、自分にソーンウィップが来たときに伝える
	private final Map<Long, List<XivCombatant>> thornMap = new HashMap<>();

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		thornMap.clear();
	}

	@HandleEvents
	public void thornTether(EventContext context, TetherEvent event) {
		if (!event.tetherIdMatches(0x12)) {
			return;
		}
		XivCombatant source = event.getSource();
		XivCombatant target = event.getTarget();
		thornMap.computeIfAbsent(source.getId(), k -> new ArrayList<>()).add(target);
		thornMap.computeIfAbsent(target.getId(), k -> new ArrayList<>()).add(source);
	}

	private final ModifiableCallout<AbilityUsedEvent> thornsOnYou = new ModifiableCallout<>("Thorn Whip (No Tethers)", "Thorns on You");
	private final ModifiableCallout<AbilityUsedEvent> thornsOneTether = new ModifiableCallout<>("Thorn Whip (One Tether)", "Thorns with {player}");
	private final ModifiableCallout<AbilityUsedEvent> thornsTwoTethers = new ModifiableCallout<>("Thorn Whip (Two Tethers)", "Thorns with {player1} and {player2}");
	private final ModifiableCallout<AbilityUsedEvent> thornsManyTethers = new ModifiableCallout<>("Thorn Whip (Three or More Tethers)", "Thorns ({num} people)");

	private final ModifiableCallout<AbilityUsedEvent> swarmOnYou = new ModifiableCallout<>("Swarm on You", "Swarm on You");
	private final ModifiableCallout<AbilityUsedEvent> swarmOnOther = new ModifiableCallout<>("Swarm on Other (Healer)", "Swarm on {event.target}");

	@HandleEvents
	public void abilities(EventContext context, AbilityUsedEvent event) {
		if (event.getSource().isPc()) {
			return;
		}
		if (event.abilityIdMatches(0x879) && event.getTarget().isThePlayer()) {
			List<XivCombatant> partners = thornMap.getOrDefault(event.getTarget().getId(), List.of());
			switch (partners.size()) {
				case 0 -> context.accept(thornsOnYou.getModified(event));
				case 1 -> context.accept(thornsOneTether.getModified(event, Map.<String, Object>of("player", partners.get(0))));
				case 2 -> context.accept(thornsTwoTethers.getModified(event, Map.<String, Object>of("player1", partners.get(0), "player2", partners.get(1))));
				default -> context.accept(thornsManyTethers.getModified(event, Map.<String, Object>of("num", partners.size())));
			}
			thornMap.clear();
		}
		else if (event.abilityIdMatches(0x7A0)) {
			if (event.getTarget().isThePlayer()) {
				context.accept(swarmOnYou.getModified(event));
			}
			else if (state.playerJobMatches(job -> job.isHealer() || job == Job.BLU)) {
				context.accept(swarmOnOther.getModified(event));
			}
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> devourGetEaten = new ModifiableCallout<>("Devour (Honey-Glazed)", "Devour: Get Eaten");
	private final ModifiableCallout<HeadMarkerEvent> devourOnYou = new ModifiableCallout<>("Devour on You", "Devour: Jump in New Thorns");
	private final ModifiableCallout<HeadMarkerEvent> devourAvoid = new ModifiableCallout<>("Devour on Other", "Avoid Devour");
	private final ModifiableCallout<HeadMarkerEvent> rottenStenchOnYou = new ModifiableCallout<>("Rotten Stench on You", "Share Laser on You");
	private final ModifiableCallout<HeadMarkerEvent> rottenStenchOnOther = new ModifiableCallout<>("Rotten Stench on Other", "Share Laser on {event.target}");

	@HandleEvents
	public void headmarkers(EventContext context, HeadMarkerEvent event) {
		boolean onYou = event.getTarget().isThePlayer();
		if (event.markerIdMatches(0x0D)) {
			if (buffs.isStatusOnPlayer(HONEY_GLAZED)) {
				context.accept(devourGetEaten.getModified(event));
			}
			else {
				context.accept((onYou ? devourOnYou : devourAvoid).getModified(event));
			}
		}
		else if (event.markerIdMatches(0x0E)) {
			context.accept((onYou ? rottenStenchOnYou : rottenStenchOnOther).getModified(event));
		}
	}

	@NpcCastCallout(0x79D)
	private final ModifiableCallout<AbilityCastStart> blighted = ModifiableCallout.durationBasedCall("Blighted Bouquet", "Stop Everything");

	@NpcCastCallout(0x86C)
	private final ModifiableCallout<AbilityCastStart> swarmStack = ModifiableCallout.durationBasedCall("Swarm Stack", "Stack for Acid");

}
