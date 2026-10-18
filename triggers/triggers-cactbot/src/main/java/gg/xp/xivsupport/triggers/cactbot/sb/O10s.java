package gg.xp.xivsupport.triggers.cactbot.sb;

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
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/raid/o10s.ts をもとに移植
@CalloutRepo(name = "Alphascape V20 Savage", duty = KnownDuty.O10S)
public class O10s extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public O10s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.O10S);
	}

	// 1回目の回転が横か縦か。null は回転の組の外
	private Boolean lastSpinWasHorizontal;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		lastSpinWasHorizontal = null;
	}

	@NpcCastCallout(0x31AA)
	private final ModifiableCallout<AbilityCastStart> tailEnd = ModifiableCallout.durationBasedCall("Tail End", "Buster on {event.target}");

	private final ModifiableCallout<HeadMarkerEvent> fireMarkerOnYou = new ModifiableCallout<>("Fire Marker (On You)", "Fire Marker on You");
	private final ModifiableCallout<HeadMarkerEvent> fireMarker = new ModifiableCallout<>("Fire Marker (Other)", "Fire Marker on {event.target}");

	@HandleEvents
	public void fireMarker(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(0x17)) {
			return;
		}
		context.accept((event.getTarget().isThePlayer() ? fireMarkerOnYou : fireMarker).getModified(event));
	}

	@PlayerHeadmarker(0x8F)
	private final ModifiableCallout<HeadMarkerEvent> deathFromBelow = new ModifiableCallout<>("Death From Below", "Death From Below");

	@PlayerHeadmarker(0x8E)
	private final ModifiableCallout<HeadMarkerEvent> deathFromAbove = new ModifiableCallout<>("Death From Above", "Death From Above");

	// 回転の組み合わせ
	// 31AC + 31AE = 31B2 (横 + 横 = 外)
	// 31AC + 31B0 = 31B4 (横 + 縦 = 内)
	// 31AD + 31AE = 31B3 (縦 + 横 = 斜め)
	// 31AD + 31B0 = 31B5 (縦 + 縦 = 十字)
	private final ModifiableCallout<AbilityUsedEvent> horizontalSpin1 = new ModifiableCallout<>("Horizontal Spin (First)", "Next Spin: In or Out");
	private final ModifiableCallout<AbilityUsedEvent> verticalSpin1 = new ModifiableCallout<>("Vertical Spin (First)", "Next Spin: Cardinals or Corners");
	private final ModifiableCallout<AbilityUsedEvent> spinOut = new ModifiableCallout<>("Horizontal Spin (Second, Out)", "Out");
	private final ModifiableCallout<AbilityUsedEvent> spinCardinals = new ModifiableCallout<>("Horizontal Spin (Second, Cardinals)", "Cardinals");
	private final ModifiableCallout<AbilityUsedEvent> spinIn = new ModifiableCallout<>("Vertical Spin (Second, In)", "In");
	private final ModifiableCallout<AbilityUsedEvent> spinCorners = new ModifiableCallout<>("Vertical Spin (Second, Corners)", "Corners");

	@HandleEvents
	public void spins(EventContext context, AbilityUsedEvent event) {
		// 誰にも当たらなくても行が出るので、最初の対象分だけ扱う
		if (!event.isFirstTarget()) {
			return;
		}
		if (event.abilityIdMatches(0x31B2, 0x31B3, 0x31B4, 0x31B5)) {
			lastSpinWasHorizontal = null;
		}
		else if (event.abilityIdMatches(0x31AC)) {
			lastSpinWasHorizontal = true;
			context.accept(horizontalSpin1.getModified(event));
		}
		else if (event.abilityIdMatches(0x31AD)) {
			lastSpinWasHorizontal = false;
			context.accept(verticalSpin1.getModified(event));
		}
		else if (event.abilityIdMatches(0x31AE) && lastSpinWasHorizontal != null) {
			context.accept((lastSpinWasHorizontal ? spinOut : spinCardinals).getModified(event));
		}
		else if (event.abilityIdMatches(0x31B0) && lastSpinWasHorizontal != null) {
			context.accept((lastSpinWasHorizontal ? spinIn : spinCorners).getModified(event));
		}
	}

}
