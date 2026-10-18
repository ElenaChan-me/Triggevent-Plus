package gg.xp.xivsupport.triggers.dtdungeon;

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

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/dungeon/ihuykatumu.ts をもとに移植
@CalloutRepo(name = "Ihuykatumu", duty = KnownDuty.Ihuykatumu)
public class Ihuykatumu extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public Ihuykatumu(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.Ihuykatumu);
	}

	// 1ボス目（Prime Punutiy）

	@NpcCastCallout(0x8E8C)
	private final ModifiableCallout<AbilityCastStart> punutiyPress = ModifiableCallout.durationBasedCall("Punutiy Press", "Raidwide");

	@NpcCastCallout(0x8E99)
	private final ModifiableCallout<AbilityCastStart> decay = ModifiableCallout.durationBasedCall("Decay", "Get under Flytrap");

	@NpcCastCallout(0x8E8E)
	private final ModifiableCallout<AbilityCastStart> inhale = ModifiableCallout.durationBasedCall("Inhale", "Stay out of Inhale");

	@NpcCastCallout(0x8EA2)
	private final ModifiableCallout<AbilityCastStart> shoreShaker = ModifiableCallout.durationBasedCall("Shore Shaker", "Out then In");

	// 2ボス目（Drowsie）

	@NpcCastCallout(0x98DC)
	private final ModifiableCallout<AbilityCastStart> uppercut = ModifiableCallout.durationBasedCall("Uppercut", "Buster on {event.target}");

	private static final int wallopDelayMs = 2_000;

	private final ModifiableCallout<AbilityCastStart> wallopSmall = ModifiableCallout.durationBasedCall("Wallop (Small)", "Dodge Ivy Cleaves (Small)");
	private final ModifiableCallout<AbilityCastStart> wallopLarge = ModifiableCallout.durationBasedCall("Wallop (Large)", "Dodge Ivy Cleaves (Large)");

	// アイビーが複数体同時に詠唱するので、最初の 1 回だけ拾って少し遅らせて出す
	@AutoFeed
	private final SequentialTrigger<BaseEvent> wallopSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x8E7F, 0x8E82),
			(e1, s) -> {
				s.waitMs(wallopDelayMs);
				s.updateCall(e1.abilityIdMatches(0x8E7F) ? wallopSmall : wallopLarge, e1);
			});

	@NpcCastCallout(0x8E7B)
	private final ModifiableCallout<AbilityCastStart> sneeze = ModifiableCallout.durationBasedCall("Sneeze", "Away from Front");

	@PlayerHeadmarker(0x8B)
	private final ModifiableCallout<HeadMarkerEvent> flagrantSpread = new ModifiableCallout<>("Flagrant Spread", "Move Away");

	// 3ボス目（Apollyon）

	@NpcCastCallout({0x8DFB, 0x8E04})
	private final ModifiableCallout<AbilityCastStart> blade = ModifiableCallout.durationBasedCall("Blade", "Buster on {event.target}");

	@NpcCastCallout(0x8DF5)
	private final ModifiableCallout<AbilityCastStart> highWind = ModifiableCallout.durationBasedCall("High Wind", "Raidwide");

	private final ModifiableCallout<AbilityUsedEvent> swarmingLocustFirst = new ModifiableCallout<>("Swarming Locust: First Cleaves", "Away on 3rd Jump");
	private final ModifiableCallout<AbilityUsedEvent> swarmingLocustSecond = new ModifiableCallout<>("Swarming Locust: Second Cleaves", "Away on 3rd Jump");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> swarmingLocustSq = SqtTemplates.sq(15_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x8DF7) && aue.isFirstTarget(),
			(e1, s) -> {
				s.updateCall(swarmingLocustFirst, e1);
				s.waitMs(7_500);
				s.updateCall(swarmingLocustSecond, e1);
			});

	@PlayerHeadmarker(0x6C)
	private final ModifiableCallout<HeadMarkerEvent> thunderIII = new ModifiableCallout<>("Thunder III", "Spread");

	private final ModifiableCallout<AbilityUsedEvent> windSickle = new ModifiableCallout<>("Wind Sickle", "In, then Follow Jump", 8_000);

	@HandleEvents
	public void windSickleUsed(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x8E05) && event.isFirstTarget()) {
			context.accept(windSickle.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> windwhistle = new ModifiableCallout<>("Windwhistle", "Avoid Whirlwind Lines");

	// 竜巻の直線範囲は詠唱なしで3回来るので、技の実行から時間で出す
	@AutoFeed
	private final SequentialTrigger<BaseEvent> windwhistleSq = SqtTemplates.sq(30_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x8E07) && aue.isFirstTarget(),
			(e1, s) -> {
				s.waitMs(7_000);
				s.updateCall(windwhistle, e1);
				s.waitMs(8_000);
				s.updateCall(windwhistle, e1);
				s.waitMs(8_000);
				s.updateCall(windwhistle, e1);
			});

}
