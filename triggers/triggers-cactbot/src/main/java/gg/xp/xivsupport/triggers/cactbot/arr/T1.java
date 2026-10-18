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
import gg.xp.xivsupport.events.actlines.events.SystemLogMessageEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/raid/t1.ts をもとに移植
@CalloutRepo(name = "The Binding Coil Of Bahamut Turn1", duty = KnownDuty.TheBindingCoilOfBahamutTurn1)
public class T1 extends AutoChildEventHandler implements FilteredEventHandler {

	// 戦闘開始時の「アラガンメガストラクチャーが封鎖された」システムログ
	private static final long SEALED_OFF_LOG_ID = 0x7DC;
	private static final long MEGASTRUCTURE_PLACE_ID = 0x379;
	private static final int FIRST_SLIME_DELAY_MS = 35_000;
	private static final int HOOD_SWING_DELAY_MS = 8_000;

	private XivState state;

	public T1(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheBindingCoilOfBahamutTurn1);
	}

	private final ModifiableCallout<AbilityCastStart> highVoltage = ModifiableCallout.durationBasedCall("High Voltage", "Interrupt {event.source}");

	@HandleEvents
	public void highVoltage(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x5A7) && state.playerJobMatches(Job::caresAboutInterrupt)) {
			context.accept(highVoltage.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> regorge = new ModifiableCallout<>("Regorge", "Spit on You");

	@HandleEvents
	public void regorge(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x4BA) && !event.getSource().isPc() && event.getTarget().isThePlayer()) {
			context.accept(regorge.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> hoodSwing = new ModifiableCallout<>("Hood Swing", "Hood Swing in 10");

	// 次のフッドスイングまでの間隔に合わせて遅らせて呼ぶ
	@AutoFeed
	private final SequentialTrigger<BaseEvent> hoodSwingSq = SqtTemplates.sq(HOOD_SWING_DELAY_MS + 5_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x4B8) && !aue.getSource().isPc() && aue.getTarget().isThePlayer(),
			(e1, s) -> {
				s.waitMs(HOOD_SWING_DELAY_MS);
				s.updateCall(hoodSwing, e1);
			});

	private final ModifiableCallout<SystemLogMessageEvent> slimeSoon = new ModifiableCallout<>("Slime Timer (First)", "Slime Soon");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> firstSlimeSq = SqtTemplates.sq(FIRST_SLIME_DELAY_MS + 5_000,
			SystemLogMessageEvent.class, slm -> slm.getId() == SEALED_OFF_LOG_ID && slm.getParam1() == MEGASTRUCTURE_PLACE_ID,
			(e1, s) -> {
				s.waitMs(FIRST_SLIME_DELAY_MS);
				s.updateCall(slimeSoon, e1);
			});
}
