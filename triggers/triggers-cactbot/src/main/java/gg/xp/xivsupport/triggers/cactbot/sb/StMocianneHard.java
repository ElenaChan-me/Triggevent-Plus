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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/dungeon/st_mocianne_hard.ts をもとに移植
@CalloutRepo(name = "Saint Mociannes Arboretum Hard", duty = KnownDuty.SaintMociannesArboretumHard)
public class StMocianneHard extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public StMocianneHard(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.SaintMociannesArboretumHard);
	}

	// 道中の視線攻撃
	private final ModifiableCallout<AbilityCastStart> frondFatale = ModifiableCallout.durationBasedCall("Frond Fatale", "Interrupt {event.source}");

	@HandleEvents
	public void frondFataleCast(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x31A4) && state.playerJobMatches(Job::caresAboutInterrupt)) {
			context.accept(frondFatale.getModified(event));
		}
	}

	// ヌルチュー

	@NpcCastCallout(0x2E48)
	private final ModifiableCallout<AbilityCastStart> vineWhip = ModifiableCallout.durationBasedCall("Vine Whip", "Buster on {event.target}");

	@PlayerHeadmarker(0x01)
	private final ModifiableCallout<HeadMarkerEvent> sludgeBomb = new ModifiableCallout<>("Sludge Bomb", "Sludge Puddle on You");

	private final ModifiableCallout<HeadMarkerEvent> faultWarren = new ModifiableCallout<>("Fault Warren", "Stack on {event.target}");
	// 同じ対象に2つ同時にマーカーが付く
	private final RepeatSuppressor faultWarrenSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void faultWarrenMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E) && faultWarrenSupp.check(event)) {
			context.accept(faultWarren.getModified(event));
		}
	}

	@PlayerHeadmarker(0x8D)
	private final ModifiableCallout<HeadMarkerEvent> taproot = new ModifiableCallout<>("Taproot", "Spread");

	@NpcCastCallout(0x2E4F)
	private final ModifiableCallout<AbilityCastStart> devour = ModifiableCallout.durationBasedCall("Devour", "Get Behind Flower");

	// ラハム

	@NpcCastCallout(0x312A)
	private final ModifiableCallout<AbilityCastStart> stone2 = ModifiableCallout.durationBasedCall("Stone II", "Buster on {event.target}");

	@NpcCastCallout(0x312C)
	private final ModifiableCallout<AbilityCastStart> tectonics = ModifiableCallout.durationBasedCall("Tectonics", "Raidwide");

	// 詠唱が長いので、ベルトコンベアが動き出す頃に知らせる
	private final ModifiableCallout<AbilityCastStart> landslip = ModifiableCallout.durationBasedCall("Landslip", "Conveyors, Avoid Golem Lines");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> landslipSq = SqtTemplates.callWhenDurationIs(
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x3132), landslip, Duration.ofSeconds(6));

	@PlayerHeadmarker(0x28)
	private final ModifiableCallout<HeadMarkerEvent> earthShaker = new ModifiableCallout<>("Earth Shaker", "Earthshaker on You");

	@NpcCastCallout(0x312B)
	private final ModifiableCallout<AbilityCastStart> emptyGaze = ModifiableCallout.durationBasedCall("Empty Gaze", "Look Away from {event.source}");

	// トッカプチ

	@NpcCastCallout(0x3135)
	private final ModifiableCallout<AbilityCastStart> mudsling = ModifiableCallout.durationBasedCall("Mudsling", "Buster on {event.target}");

	@PlayerHeadmarker(0x8B)
	private final ModifiableCallout<HeadMarkerEvent> quagmire = new ModifiableCallout<>("Quagmire", "Spread, Stay Off Platforms");

	@NpcCastCallout(0x3137)
	private final ModifiableCallout<AbilityCastStart> mudPie = ModifiableCallout.durationBasedCall("Mud Pie", "Push Mud Pie onto Platform");

	@NpcCastCallout(0x313C)
	private final ModifiableCallout<AbilityCastStart> feculentFlood = ModifiableCallout.durationBasedCall("Feculent Flood", "Push Mud Pie out of Cone");

}
