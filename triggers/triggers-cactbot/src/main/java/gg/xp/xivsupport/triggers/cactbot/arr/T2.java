package gg.xp.xivsupport.triggers.cactbot.arr;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.Job;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.BuffRemoved;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/raid/t2.ts をもとに移植
@CalloutRepo(name = "The Binding Coil Of Bahamut Turn2", duty = KnownDuty.TheBindingCoilOfBahamutTurn2)
public class T2 extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long ALLAGAN_ROT = 0x14D;
	private static final int PASS_ROT_DELAY_MS = 11_000;

	private XivState state;
	private boolean hasRot;

	public T2(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheBindingCoilOfBahamutTurn2);
	}

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		hasRot = false;
	}

	private final ModifiableCallout<AbilityCastStart> highVoltage = ModifiableCallout.durationBasedCall("High Voltage", "Interrupt {event.source}");

	@HandleEvents
	public void highVoltage(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x4C0) && state.playerJobMatches(Job::caresAboutInterrupt)) {
			context.accept(highVoltage.getModified(event));
		}
	}

	@NpcCastCallout(value = 0x4C5, suppressMs = 3_000)
	private final ModifiableCallout<AbilityCastStart> ballast = ModifiableCallout.durationBasedCall("Ballast", "Get Behind");

	private final ModifiableCallout<BuffApplied> rotOnYou = ModifiableCallout.<BuffApplied>durationBasedCall("Allagan Rot (on You)", "Rot on You").autoIcon();
	private final ModifiableCallout<BuffApplied> rotOn = ModifiableCallout.<BuffApplied>durationBasedCall("Allagan Rot", "Rot on {event.target}").autoIcon();

	@HandleEvents
	public void rotApplied(EventContext context, BuffApplied event) {
		if (!event.buffIdMatches(ALLAGAN_ROT)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			hasRot = true;
			context.accept(rotOnYou.getModified(event));
		}
		else {
			context.accept(rotOn.getModified(event));
		}
	}

	@HandleEvents
	public void rotRemoved(EventContext context, BuffRemoved event) {
		if (event.buffIdMatches(ALLAGAN_ROT) && event.getTarget().isThePlayer()) {
			hasRot = false;
		}
	}

	private final ModifiableCallout<BuffApplied> passRot = new ModifiableCallout<BuffApplied>("Pass Rot", "Pass Rot").autoIcon();

	// まだ自分に残っていれば渡しに行く
	@AutoFeed
	private final SequentialTrigger<BaseEvent> passRotSq = SqtTemplates.sq(PASS_ROT_DELAY_MS + 5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(ALLAGAN_ROT) && ba.getTarget().isThePlayer(),
			(e1, s) -> {
				s.waitMs(PASS_ROT_DELAY_MS);
				if (hasRot) {
					s.updateCall(passRot, e1);
				}
			});
}
