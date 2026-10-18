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
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/raid/o10n.ts をもとに移植
@CalloutRepo(name = "Alphascape V20", duty = KnownDuty.AlphascapeV20)
public class O10n extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long horizontalSpin1 = 0x31C7;
	private static final long verticalSpin1 = 0x31C8;
	private static final long horizontalSpin2 = 0x31C9;
	private static final long verticalSpin2 = 0x31CB;

	private XivState state;

	public O10n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AlphascapeV20);
	}

	private Boolean lastSpinWasHorizontal;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		lastSpinWasHorizontal = null;
	}

	// 回転は2回の組み合わせで決まる
	// 横+横 = 外、横+縦 = 中、縦+縦 = 十字（角が安置）
	private final ModifiableCallout<AbilityUsedEvent> horizontalFirst = new ModifiableCallout<>("Horizontal Spin (First)", "Next Spin: In or Out");
	private final ModifiableCallout<AbilityUsedEvent> verticalFirst = new ModifiableCallout<>("Vertical Spin (First)", "Next Spin: Corners");
	private final ModifiableCallout<AbilityUsedEvent> horizontalOut = new ModifiableCallout<>("Horizontal Spin (Second, Out)", "Out");
	private final ModifiableCallout<AbilityUsedEvent> horizontalCardinals = new ModifiableCallout<>("Horizontal Spin (Second, Cardinals)", "Go To Cardinals");
	private final ModifiableCallout<AbilityUsedEvent> verticalIn = new ModifiableCallout<>("Vertical Spin (Second, In)", "In");
	private final ModifiableCallout<AbilityUsedEvent> verticalCorners = new ModifiableCallout<>("Vertical Spin (Second, Corners)", "Go To Corners");

	@HandleEvents
	public void spin(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(horizontalSpin1, verticalSpin1, horizontalSpin2, verticalSpin2) || !event.isFirstTarget()) {
			return;
		}
		if (event.abilityIdMatches(horizontalSpin1)) {
			lastSpinWasHorizontal = true;
			context.accept(horizontalFirst.getModified(event));
		}
		else if (event.abilityIdMatches(verticalSpin1)) {
			lastSpinWasHorizontal = false;
			context.accept(verticalFirst.getModified(event));
		}
		else if (lastSpinWasHorizontal == null) {
			return;
		}
		else if (event.abilityIdMatches(horizontalSpin2)) {
			context.accept((lastSpinWasHorizontal ? horizontalOut : horizontalCardinals).getModified(event));
		}
		else {
			context.accept((lastSpinWasHorizontal ? verticalIn : verticalCorners).getModified(event));
		}
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> spinCleanupSq = SqtTemplates.sq(15_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(horizontalSpin1, verticalSpin1) && aue.isFirstTarget(),
			(e1, s) -> {
				s.waitMs(10_000);
				lastSpinWasHorizontal = null;
			});

	@PlayerHeadmarker(0x28)
	private final ModifiableCallout<HeadMarkerEvent> earthShaker = new ModifiableCallout<>("Earth Shaker", "Earthshaker on You");

	private final ModifiableCallout<HeadMarkerEvent> akhMorn = new ModifiableCallout<>("Akh Morn", "Stack on {event.target}");

	@HandleEvents
	public void akhMorn(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E)) {
			context.accept(akhMorn.getModified(event));
		}
	}

	@PlayerHeadmarker(0xA0)
	private final ModifiableCallout<HeadMarkerEvent> thunderstorm = new ModifiableCallout<>("Thunderstorm", "Spread");

	@PlayerHeadmarker(0x43)
	private final ModifiableCallout<HeadMarkerEvent> dryIce = new ModifiableCallout<>("Dry Ice", "Drop Ice Outside");

	@NpcCastCallout(0x31C5)
	private final ModifiableCallout<AbilityCastStart> tailEnd = ModifiableCallout.durationBasedCall("Tail End", "Buster on {event.target}");

	@NpcCastCallout(0x33EF)
	private final ModifiableCallout<AbilityCastStart> rimeWreath = ModifiableCallout.durationBasedCall("Rime Wreath", "Raidwide");

	// 詠唱完了の約2秒後からダメージが入り始める。DPSのみ対象だが予兆がないので全員に知らせる
	@NpcAbilityUsedCallout(value = 0x3622, suppressMs = 5_000)
	private final ModifiableCallout<AbilityUsedEvent> akhRhai = new ModifiableCallout<>("Akh Rhai", "Move Away");

}
