package gg.xp.xivsupport.triggers.cactbot.shb;

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
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/raid/e6n.ts をもとに移植
@CalloutRepo(name = "Edens Verse Furor", duty = KnownDuty.EdensVerseFuror)
public class E6n extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public E6n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.EdensVerseFuror);
	}

	// 線0x68は他の技でも使われるので、ハンズ・オブ・フレイムの詠唱中だけ見る
	private boolean handsOfFlame;
	private boolean seenSpark;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		handsOfFlame = false;
		seenSpark = false;
	}

	@NpcCastCallout(0x4BD7)
	private final ModifiableCallout<AbilityCastStart> superstorm = ModifiableCallout.durationBasedCall("Superstorm", "Raidwide");

	@NpcCastCallout({0x4BDD, 0x4BDE, 0x4BDF, 0x4BE3, 0x4BE4, 0x4BE5})
	private final ModifiableCallout<AbilityCastStart> ferostorm = ModifiableCallout.durationBasedCall("Ferostorm", "Avoid Green Nails");

	private final ModifiableCallout<HeadMarkerEvent> airBumpOnYou = new ModifiableCallout<>("Air Bump (on You)", "Enumeration on You");
	private final ModifiableCallout<HeadMarkerEvent> airBump = new ModifiableCallout<>("Air Bump", "Enumeration");
	private final RepeatSuppressor airBumpSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void airBump(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(0xD3) || !airBumpSupp.check(event)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(airBumpOnYou.getModified(event));
		}
		else {
			context.accept(airBump.getModified(event));
		}
	}

	@NpcCastCallout(0x4BF1)
	private final ModifiableCallout<AbilityCastStart> infernoHowl = ModifiableCallout.durationBasedCall("Inferno Howl", "Raidwide");

	@HandleEvents
	public void handsOfFlameStart(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x4CFE)) {
			handsOfFlame = true;
		}
	}

	@HandleEvents
	public void handsOfFlameEnd(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x4BE9)) {
			handsOfFlame = false;
		}
	}

	private final ModifiableCallout<TetherEvent> handsOfFlameOnYou = new ModifiableCallout<>("Hands of Flame (on You)", "Charge on You");
	private final ModifiableCallout<TetherEvent> handsOfFlameSwap = new ModifiableCallout<>("Hands of Flame (Other Tank)", "Tank Swap");

	@HandleEvents
	public void handsOfFlameTether(EventContext context, TetherEvent event) {
		if (!handsOfFlame || !event.tetherIdMatches(0x68)) {
			return;
		}
		if (event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			context.accept(handsOfFlameOnYou.getModified(event));
		}
		else if (state.playerJobMatches(Job::isTank)) {
			context.accept(handsOfFlameSwap.getModified(event));
		}
	}

	@NpcCastCallout(0x4BED)
	private final ModifiableCallout<AbilityCastStart> instantIncineration = ModifiableCallout.durationBasedCall("Instant Incineration", "Buster on {event.target}");

	@PlayerHeadmarker(0x16)
	private final ModifiableCallout<HeadMarkerEvent> handsOfHell = new ModifiableCallout<>("Hands of Hell", "Tether Marker on You");

	private final ModifiableCallout<AbilityUsedEvent> strikeSpark = new ModifiableCallout<>("Strike Spark", "Move to Ifrit");

	@HandleEvents
	public void strikeSpark(EventContext context, AbilityUsedEvent event) {
		// 2回目以降の跳躍は気にしなくてよい
		if (event.abilityIdMatches(0x4F98) && !seenSpark) {
			seenSpark = true;
			context.accept(strikeSpark.getModified(event));
		}
	}

	@NpcCastCallout(0x4BE6)
	private final ModifiableCallout<AbilityCastStart> stormOfFury = ModifiableCallout.durationBasedCall("Storm of Fury", "Stack, then Spread");

}
