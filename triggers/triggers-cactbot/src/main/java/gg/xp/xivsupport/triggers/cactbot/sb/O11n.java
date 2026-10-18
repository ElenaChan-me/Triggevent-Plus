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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/raid/o11n.ts をもとに移植
@CalloutRepo(name = "Alphascape V30", duty = KnownDuty.AlphascapeV30)
public class O11n extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long starboard1 = 0x3281;
	private static final long starboard2 = 0x3282;
	private static final long larboard1 = 0x3283;
	private static final long larboard2 = 0x3284;

	private XivState state;

	public O11n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AlphascapeV30);
	}

	private Boolean lastWasStarboard;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		lastWasStarboard = null;
	}

	@NpcCastCallout(0x3286)
	private final ModifiableCallout<AbilityCastStart> atomicRay = ModifiableCallout.durationBasedCall("Atomic Ray", "Raidwide");

	@NpcCastCallout(0x3287)
	private final ModifiableCallout<AbilityCastStart> mustardBomb = ModifiableCallout.durationBasedCall("Mustard Bomb", "Buster on {event.target}");

	private final ModifiableCallout<AbilityCastStart> starboardFirst = ModifiableCallout.durationBasedCall("Starboard Cannon (First)", "Left");
	private final ModifiableCallout<AbilityCastStart> larboardFirst = ModifiableCallout.durationBasedCall("Larboard Cannon (First)", "Right");
	private final ModifiableCallout<AbilityCastStart> starboardMove = ModifiableCallout.durationBasedCall("Starboard Cannon (Second, Move)", "Move (Left)");
	private final ModifiableCallout<AbilityCastStart> starboardStay = ModifiableCallout.durationBasedCall("Starboard Cannon (Second, Stay)", "Stay (Left)");
	private final ModifiableCallout<AbilityCastStart> larboardStay = ModifiableCallout.durationBasedCall("Larboard Cannon (Second, Stay)", "Stay (Right)");
	private final ModifiableCallout<AbilityCastStart> larboardMove = ModifiableCallout.durationBasedCall("Larboard Cannon (Second, Move)", "Move (Right)");

	// 1発目のログが欠けても2発目で左右だけは言えるよう、1発目の判定には2発目のIDも含める
	@HandleEvents
	public void cannon(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(starboard1, starboard2, larboard1, larboard2)) {
			return;
		}
		boolean starboard = event.abilityIdMatches(starboard1, starboard2);
		if (lastWasStarboard == null) {
			context.accept((starboard ? starboardFirst : larboardFirst).getModified(event));
			lastWasStarboard = starboard;
		}
		else if (event.abilityIdMatches(starboard2)) {
			context.accept((lastWasStarboard ? starboardMove : starboardStay).getModified(event));
		}
		else if (event.abilityIdMatches(larboard2)) {
			context.accept((lastWasStarboard ? larboardStay : larboardMove).getModified(event));
		}
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> cannonCleanupSq = SqtTemplates.sq(20_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(starboard1, larboard1),
			(e1, s) -> {
				s.waitMs(15_000);
				lastWasStarboard = null;
			});

	@PlayerHeadmarker(0x65)
	private final ModifiableCallout<HeadMarkerEvent> ballisticMissile = new ModifiableCallout<>("Ballistic Missile", "Drop Fire Outside");

	private final ModifiableCallout<HeadMarkerEvent> electricSlide = new ModifiableCallout<>("Electric Slide", "Stack on {event.target}");

	@HandleEvents
	public void electricSlide(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E)) {
			context.accept(electricSlide.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> deltaAttack = new ModifiableCallout<>("Delta Attack", "Use Duty Action on Conductive Focus");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> deltaAttackSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x327B),
			(e1, s) -> {
				s.waitMs(3_000);
				s.updateCall(deltaAttack, e1);
			});

}
