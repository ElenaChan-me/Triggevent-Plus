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
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/raid/t7.ts をもとに移植
@CalloutRepo(name = "The Second Coil Of Bahamut Turn2", duty = KnownDuty.TheSecondCoilOfBahamutTurn2)
public class T7 extends AutoChildEventHandler implements FilteredEventHandler {

	private static final int SHORT_CALL_MS = 3_000;
	private static final int SHRIEK_REMINDER_DELAY_MS = 7_000;
	private static final int TAIL_SLAP_DELAY_MS = 6_000;

	private XivState state;

	public T7(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheSecondCoilOfBahamutTurn2);
	}

	private boolean canInterrupt() {
		return state.playerJobMatches(job -> job.isTank() || job.isMeleeDps() || job.isPranged());
	}

	private final ModifiableCallout<AbilityCastStart> ramsVoice = ModifiableCallout.durationBasedCall("Ram's Voice", "Silence Ram's Voice");
	private final ModifiableCallout<AbilityCastStart> dragonsVoice = ModifiableCallout.durationBasedCall("Dragon's Voice", "Silence Dragon's Voice");

	@HandleEvents
	public void chimeraVoices(EventContext context, AbilityCastStart event) {
		if (event.getSource().isPc() || !canInterrupt()) {
			return;
		}
		if (event.abilityIdMatches(0x860)) {
			context.accept(ramsVoice.getModified(event));
		}
		else if (event.abilityIdMatches(0x861)) {
			context.accept(dragonsVoice.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> tailSlap = new ModifiableCallout<>("Tail Slap (BLU)", "Tail Slap in 10");

	// 青魔道士でタンクをしている場合だけ次のテイルスラップを予告する
	@AutoFeed
	private final SequentialTrigger<BaseEvent> tailSlapSq = SqtTemplates.sq(TAIL_SLAP_DELAY_MS + 5_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x7A8) && aue.getTarget().isThePlayer() && state.playerJobMatches(job -> job == Job.BLU),
			(e1, s) -> {
				s.waitMs(TAIL_SLAP_DELAY_MS);
				s.updateCall(tailSlap, e1);
			});

	private final ModifiableCallout<BuffApplied> cursedVoice = new ModifiableCallout<BuffApplied>("Cursed Voice", "Voice Soon")
			.statusIcon(0x1C3);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> cursedVoiceSq = SqtTemplates.callWhenDurationIs(BuffApplied.class,
			ba -> ba.buffIdMatches(0x1C3) && ba.getTarget().isThePlayer(),
			cursedVoice, Duration.ofSeconds(3));

	private final ModifiableCallout<BuffApplied> shriekOnYou = new ModifiableCallout<BuffApplied>("Cursed Shriek on You", "Shriek on You", SHORT_CALL_MS)
			.statusIcon(0x1C4);
	private final ModifiableCallout<BuffApplied> shriekOnOther = new ModifiableCallout<BuffApplied>("Cursed Shriek on Other", "Shriek on {event.target}", SHORT_CALL_MS)
			.statusIcon(0x1C4);
	private final ModifiableCallout<BuffApplied> shriekSoon = new ModifiableCallout<>("Cursed Shriek Reminder (on You)", "Shriek Soon", SHORT_CALL_MS);
	private final ModifiableCallout<BuffApplied> dodgeShriek = new ModifiableCallout<>("Cursed Shriek Reminder (on Other)", "Dodge Shriek", SHORT_CALL_MS);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> cursedShriekSq = SqtTemplates.sq(SHRIEK_REMINDER_DELAY_MS + 5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0x1C4),
			(e1, s) -> {
				boolean onYou = e1.getTarget().isThePlayer();
				s.updateCall(onYou ? shriekOnYou : shriekOnOther, e1);
				s.waitMs(SHRIEK_REMINDER_DELAY_MS);
				s.updateCall(onYou ? shriekSoon : dodgeShriek, e1);
			});

	@NpcCastCallout({0x7BB, 0x7B1})
	private final ModifiableCallout<AbilityCastStart> petrifaction = ModifiableCallout.durationBasedCall("Petrifaction", "Look Away");

	@NpcCastCallout(0x7B2)
	private final ModifiableCallout<AbilityCastStart> venomousTail = ModifiableCallout.durationBasedCall("Venomous Tail", "Venomous Tail");

}
