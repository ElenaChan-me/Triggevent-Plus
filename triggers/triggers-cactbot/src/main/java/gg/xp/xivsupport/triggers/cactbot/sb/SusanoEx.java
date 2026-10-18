package gg.xp.xivsupport.triggers.cactbot.sb;

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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/trial/susano-ex.ts をもとに移植
@CalloutRepo(name = "The Pool Of Tribute Extreme", duty = KnownDuty.ThePoolOfTributeExtreme)
public class SusanoEx extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long churningId = 0x4F6;

	private XivState state;

	public SusanoEx(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.ThePoolOfTributeExtreme);
	}

	// 誰か一人でもダイスが付いているか。途中で死なれるとずれるが、たいていは問題ない
	private boolean churning;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		churning = false;
	}

	@HandleEvents
	public void churningGain(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(churningId)) {
			churning = true;
		}
	}

	@HandleEvents
	public void churningLose(EventContext context, BuffRemoved event) {
		if (event.buffIdMatches(churningId)) {
			churning = false;
		}
	}

	private final ModifiableCallout<AbilityCastStart> stormsplitterOnYou = ModifiableCallout.durationBasedCall("Stormsplitter (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> stormsplitterSwap = ModifiableCallout.durationBasedCall("Stormsplitter (Other Tank)", "Tank Swap");
	private final ModifiableCallout<AbilityCastStart> stormsplitter = ModifiableCallout.durationBasedCall("Stormsplitter", "Buster on {event.target}");

	@HandleEvents
	public void stormsplitterCast(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x2033)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(stormsplitterOnYou.getModified(event));
		}
		else if (state.playerJobMatches(Job::isTank)) {
			context.accept(stormsplitterSwap.getModified(event));
		}
		else {
			context.accept(stormsplitter.getModified(event));
		}
	}

	// 赤いノックバックマーカー
	private final ModifiableCallout<HeadMarkerEvent> knockbackWithDice = new ModifiableCallout<>("Knockback Marker (Dice)", "Knockback with Dice, Stop");
	private final ModifiableCallout<HeadMarkerEvent> knockback = new ModifiableCallout<>("Knockback Marker", "Knockback on You");

	@HandleEvents
	public void knockbackMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x17) && event.getTarget().isThePlayer()) {
			context.accept((churning ? knockbackWithDice : knockback).getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> brightstorm = new ModifiableCallout<>("Brightstorm", "Stack on {event.target}");

	@HandleEvents
	public void brightstormMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E)) {
			context.accept(brightstorm.getModified(event));
		}
	}

	@PlayerHeadmarker(0x6E)
	private final ModifiableCallout<HeadMarkerEvent> levinbolt = new ModifiableCallout<>("Levinbolt", "Levinbolt on You");

	// タンクから線が見えにくいので、スタン対象を知らせる
	private final ModifiableCallout<HeadMarkerEvent> levinboltStun = new ModifiableCallout<>("Levinbolt Stun (Tank)", "Stun {event.target}");

	@HandleEvents
	public void levinboltStunMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x6F) && state.playerJobMatches(Job::isTank)) {
			context.accept(levinboltStun.getModified(event));
		}
	}

	private final ModifiableCallout<BuffApplied> churningStop = ModifiableCallout.<BuffApplied>durationBasedCall("Churning", "Stop Everything").statusIcon(churningId);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> churningSq = SqtTemplates.callWhenDurationIs(
			BuffApplied.class, ba -> ba.buffIdMatches(churningId) && ba.getTarget().isThePlayer(), churningStop, Duration.ofSeconds(3));

}
