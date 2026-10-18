package gg.xp.xivsupport.triggers.cactbot.hw;

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
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/raid/a5s.ts をもとに移植
@CalloutRepo(name = "Alexander The Fist Of The Son Savage", duty = KnownDuty.AlexanderTheFistOfTheSonSavage)
public class A5s extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public A5s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AlexanderTheFistOfTheSonSavage);
	}

	private int bombCount;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		bombCount = 0;
	}

	private final ModifiableCallout<HeadMarkerEvent> gobcut = new ModifiableCallout<>("Gobcut", "Stack on {event.target}");

	@HandleEvents
	public void gobcutMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E)) {
			context.accept(gobcut.getModified(event));
		}
	}

	private final ModifiableCallout<BuffApplied> concussion = new ModifiableCallout<BuffApplied>("Concussion", "Tank Swap").statusIcon(0x3E4);

	@HandleEvents
	public void concussionBuff(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x3E4) && event.getTarget().isThePlayer()
		    && state.playerJobMatches(job -> job.isTank() || job == Job.BLU)) {
			context.accept(concussion.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> bombsIntoSpikey = new ModifiableCallout<>("Bomb's Away (First)", "Knock Bombs Into Spikey");
	private final ModifiableCallout<AbilityUsedEvent> bombsOppositeSpikey = new ModifiableCallout<>("Bomb's Away (Later)", "Knock Bombs Opposite Spikey");

	@HandleEvents
	public void bombDirection(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x1590) || !event.isFirstTarget()) {
			return;
		}
		bombCount++;
		if (bombCount == 1) {
			context.accept(bombsIntoSpikey.getModified(event));
		}
		else {
			context.accept(bombsOppositeSpikey.getModified(event));
		}
	}

	@PlayerHeadmarker(0x1E)
	private final ModifiableCallout<HeadMarkerEvent> prey = new ModifiableCallout<>("Prey", "Get Away");

	private final ModifiableCallout<HeadMarkerEvent> preyHealer = new ModifiableCallout<>("Prey (Healer)", "Shield {event.target}");

	@HandleEvents
	public void preyHealerMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x1E) && state.playerJobMatches(Job::isHealer)) {
			context.accept(preyHealer.getModified(event));
		}
	}

	@PlayerHeadmarker(0x17)
	private final ModifiableCallout<HeadMarkerEvent> glupgloop = new ModifiableCallout<>("Glupgloop", "Glupgloop on You");

	private final ModifiableCallout<AbilityCastStart> steelScales = ModifiableCallout.durationBasedCall("Steel Scales", "Stun");
	// 複数のコブラが詠唱するので、最初の1回だけ読み上げる
	private final RepeatSuppressor steelScalesSupp = new RepeatSuppressor(Duration.ofSeconds(60));

	@HandleEvents
	public void steelScalesCast(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x16A2)
		    && state.playerJobMatches(job -> job.isTank() || job.isMeleeDps())
		    && steelScalesSupp.check(event)) {
			context.accept(steelScales.getModified(event));
		}
	}

	private final ModifiableCallout<BuffApplied> antiCoagulant = new ModifiableCallout<BuffApplied>("Anti-Coagulant", "Cleanse (Green)", 8_000).statusIcon(0x3EC);
	private final RepeatSuppressor antiCoagulantSupp = new RepeatSuppressor(Duration.ofSeconds(30));

	@HandleEvents
	public void antiCoagulantBuff(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x3EC) && event.getTarget().isThePlayer() && antiCoagulantSupp.check(event)) {
			context.accept(antiCoagulant.getModified(event));
		}
	}

	// 2体の詠唱がほぼ同時に来るので1回にまとめる
	@NpcCastCallout(value = 0x169C, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> oogle = ModifiableCallout.durationBasedCall("Oogle", "Look Away");

}
