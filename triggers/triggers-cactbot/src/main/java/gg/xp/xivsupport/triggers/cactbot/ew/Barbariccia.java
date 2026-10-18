package gg.xp.xivsupport.triggers.cactbot.ew;

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
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/trial/barbariccia.ts をもとに移植
@CalloutRepo(name = "Storms Crown", duty = KnownDuty.StormsCrown)
public class Barbariccia extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public Barbariccia(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.StormsCrown);
	}

	@NpcCastCallout(0x75B6)
	private final ModifiableCallout<AbilityCastStart> voidAeroIV = ModifiableCallout.durationBasedCall("Void Aero IV", "Raidwide");

	// 3本の詠唱が同時に始まるが、ドーナツと直線の区別がつくのはこのIDだけ
	@NpcCastCallout(0x75BA)
	private final ModifiableCallout<AbilityCastStart> savageBarberyDonut = ModifiableCallout.durationBasedCall("Savage Barbery (Donut)", "In");

	@NpcCastCallout(0x75C0)
	private final ModifiableCallout<AbilityCastStart> savageBarberyLine = ModifiableCallout.durationBasedCall("Savage Barbery (Line)", "Out and Away");

	@NpcCastCallout(0x75C2)
	private final ModifiableCallout<AbilityCastStart> hairRaidWall = ModifiableCallout.durationBasedCall("Hair Raid (Wall)", "Wall");

	@NpcCastCallout(0x75B7)
	private final ModifiableCallout<AbilityCastStart> voidAeroIII = ModifiableCallout.durationBasedCall("Void Aero III", "Shared Buster on {event.target}");

	private final ModifiableCallout<HeadMarkerEvent> deadlyTwist = new ModifiableCallout<>("Deadly Twist", "Stack on {event.target}");

	@PlayerHeadmarker(0x175)
	private final ModifiableCallout<HeadMarkerEvent> hairSpray = new ModifiableCallout<>("Hair Spray", "Spread");

	private final ModifiableCallout<AbilityUsedEvent> brutalRush = new ModifiableCallout<>("Brutal Rush", "Move Away");

	@PlayerHeadmarker(0x160)
	private final ModifiableCallout<HeadMarkerEvent> boulder = new ModifiableCallout<>("Boulder", "Away from Group");

	@NpcCastCallout(value = 0x73CF, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> boulderBreak = ModifiableCallout.durationBasedCall("Boulder Break", "Tankbusters");

	private final ModifiableCallout<AbilityCastStart> boldBoulder = ModifiableCallout.durationBasedCall("Bold Boulder", "Flare on You");

	private final ModifiableCallout<HeadMarkerEvent> trample = new ModifiableCallout<>("Trample", "Stack on {event.target}");

	private final ModifiableCallout<AbilityCastStart> touchdown = ModifiableCallout.durationBasedCall("Touchdown", "Knockback");
	private final ModifiableCallout<AbilityCastStart> impact = ModifiableCallout.durationBasedCall("Impact", "Knockback");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> touchdownSq = SqtTemplates.callWhenDurationIs(
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x746D), touchdown, Duration.ofSeconds(5));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> impactSq = SqtTemplates.callWhenDurationIs(
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x75D8), impact, Duration.ofSeconds(5));

	@HandleEvents
	public void headMarkers(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E)) {
			context.accept(deadlyTwist.getModified(event));
		}
		else if (event.markerIdMatches(0x64)) {
			context.accept(trample.getModified(event));
		}
	}

	@HandleEvents
	public void boldBoulderCast(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x75D6) && event.getTarget().isThePlayer()) {
			context.accept(boldBoulder.getModified(event));
		}
	}

	// 突進が当たった時点で後続の範囲が確定するので、そこで離れる
	@HandleEvents
	public void brutalRushHit(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x75C6) && event.getTarget().isThePlayer()) {
			context.accept(brutalRush.getModified(event));
		}
	}

}
