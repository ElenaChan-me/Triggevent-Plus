package gg.xp.xivsupport.triggers.cactbot.hw;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.Job;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/raid/a2s.ts をもとに移植
@CalloutRepo(name = "Alexander The Cuff Of The Father Savage", duty = KnownDuty.AlexanderTheCuffOfTheFatherSavage)
public class A2s extends AutoChildEventHandler implements FilteredEventHandler {

	private static final Duration PREY_SUPPRESS = Duration.ofSeconds(10);

	private XivState state;
	private volatile long lastStunnedSoldierId;

	public A2s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AlexanderTheCuffOfTheFatherSavage);
	}

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		lastStunnedSoldierId = 0;
	}

	private final ModifiableCallout<AbilityUsedEvent> preyHealer = new ModifiableCallout<>("Boomcannon (Healer)", "Keep {event.target} Topped");
	private final ModifiableCallout<AbilityUsedEvent> preyOnYou = new ModifiableCallout<>("Boomcannon (on You)", "Boomcannon on You");

	private final RepeatSuppressor preyHealerSupp = new RepeatSuppressor(PREY_SUPPRESS);
	private final RepeatSuppressor preyOnYouSupp = new RepeatSuppressor(PREY_SUPPRESS);

	@HandleEvents
	public void prey(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x1413) || event.getSource().isPc()) {
			return;
		}
		if (state.playerJobMatches(job -> job.isHealer() || job == Job.BLU) && preyHealerSupp.check(event)) {
			context.accept(preyHealer.getModified(event));
		}
		if (event.getTarget().isThePlayer() && preyOnYouSupp.check(event)) {
			context.accept(preyOnYou.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> bangyzoom = new ModifiableCallout<>("Bangyzoom", "Stun Soldier");

	// ソルジャーは1体ずつ湧くので、同じ個体に対しては最初の1回だけ知らせる
	@HandleEvents
	public void bangyzoom(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0xFD9) || event.getTarget().isPc()) {
			return;
		}
		long targetId = event.getTarget().getId();
		if (targetId != lastStunnedSoldierId) {
			lastStunnedSoldierId = targetId;
			context.accept(bangyzoom.getModified(event));
		}
	}

}
