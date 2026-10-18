package gg.xp.xivsupport.triggers.dtdungeon;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.*;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.RawAddCombatantEvent;
import gg.xp.xivsupport.events.state.RawXivCombatantInfo;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/dungeon/yuweyawata.ts をもとに移植
@CalloutRepo(name = "Yuweyawata Field Station", duty = KnownDuty.Yuweyawata)
public class Yuweyawata extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long rawElectropeNameId = 13622;
	private static final int lostHopeDurationMs = 17_000;

	private XivState state;

	public Yuweyawata(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.Yuweyawata);
	}

	// 1ボス目（Lindblum Zaghnal）

	@NpcCastCallout(0x9EBB)
	private final ModifiableCallout<AbilityCastStart> electricalOverload = ModifiableCallout.durationBasedCall("Electrical Overload", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> caberToss = ModifiableCallout.durationBasedCall("Caber Toss", "Away from Falling Pillar");

	// 詠唱開始直後だと柱の位置がまだ分からないので、終わり際に呼ぶ
	@AutoFeed
	private final SequentialTrigger<BaseEvent> caberTossSq = SqtTemplates.callWhenDurationIs(
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x9EB0),
			caberToss, Duration.ofSeconds(5));

	@NpcCastCallout(value = 0x9EBD, onYou = true)
	private final ModifiableCallout<AbilityCastStart> lightningStorm = ModifiableCallout.durationBasedCall("Lightning Storm", "Spread");

	@NpcCastCallout({0x9EB7, 0xA133})
	private final ModifiableCallout<AbilityCastStart> sparkingFissureSmall = ModifiableCallout.durationBasedCall("Sparking Fissure (Small)", "Raidwide");

	private final ModifiableCallout<RawAddCombatantEvent> rawElectrope = new ModifiableCallout<>("Raw Electrope Spawn", "Kill Adds");

	private final RepeatSuppressor rawElectropeSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void rawElectropeSpawn(EventContext context, RawAddCombatantEvent event) {
		RawXivCombatantInfo info = event.getFullInfo();
		if (info == null || info.getBnpcNameId() != rawElectropeNameId) {
			return;
		}
		if (rawElectropeSupp.check(event)) {
			context.accept(rawElectrope.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> sparkingFissureBig = ModifiableCallout.durationBasedCall("Sparking Fissure (Big)", "Big Raidwide");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> sparkingFissureBigSq = SqtTemplates.callWhenDurationIs(
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xA12A),
			sparkingFissureBig, Duration.ofSeconds(5));

	// 2ボス目（Overseer Kanilokka）

	@NpcCastCallout(0x9ED2)
	private final ModifiableCallout<AbilityCastStart> darkSouls = ModifiableCallout.durationBasedCall("Dark Souls", "Buster on {event.target}");

	@NpcCastCallout(0x9EC0)
	private final ModifiableCallout<AbilityCastStart> freeSpirits = ModifiableCallout.durationBasedCall("Free Spirits", "Raidwide");

	@NpcCastCallout(0x9EC4)
	private final ModifiableCallout<AbilityCastStart> phantomFlood = ModifiableCallout.durationBasedCall("Phantom Flood", "In");

	@NpcCastCallout(value = 0x9EC9, onYou = true)
	private final ModifiableCallout<AbilityCastStart> telltaleTears = ModifiableCallout.durationBasedCall("Telltale Tears", "Spread");

	private final ModifiableCallout<AbilityUsedEvent> lostHope = new ModifiableCallout<>("Lost Hope", "Out", lostHopeDurationMs);

	// 複数ヒットするので最初の1回だけ拾う
	private final RepeatSuppressor lostHopeSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void lostHope(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x9EC5) && lostHopeSupp.check(event)) {
			context.accept(lostHope.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> necrohazard = ModifiableCallout.durationBasedCall("Necrohazard", "Big Raidwide");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> necrohazardSq = SqtTemplates.callWhenDurationIs(
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x9EC6),
			necrohazard, Duration.ofSeconds(4));

	@NpcCastCallout(0x9EC7)
	private final ModifiableCallout<AbilityCastStart> bloodburst = ModifiableCallout.durationBasedCall("Bloodburst", "Raidwide");

	@NpcCastCallout(0x9ECB)
	private final ModifiableCallout<AbilityCastStart> soulDouse = ModifiableCallout.durationBasedCall("Soul Douse", "Stack on {event.target}");

	// 3ボス目（Lunipyati）

	@NpcCastCallout(0x9EA4)
	private final ModifiableCallout<AbilityCastStart> ragingClaw = ModifiableCallout.durationBasedCall("Raging Claw", "Behind");

	@NpcCastCallout(0x9E9B)
	private final ModifiableCallout<AbilityCastStart> leporineLoaf = ModifiableCallout.durationBasedCall("Leporine Loaf", "Raidwide");

	@NpcCastCallout(value = 0x9EA7, onYou = true)
	private final ModifiableCallout<AbilityCastStart> jaggedEdge = ModifiableCallout.durationBasedCall("Jagged Edge", "Spread");

	private final ModifiableCallout<AbilityCastStart> craterCarve = ModifiableCallout.durationBasedCall("Crater Carve", "Out");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> craterCarveSq = SqtTemplates.callWhenDurationIs(
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x9E9D),
			craterCarve, Duration.ofSeconds(4));

	@NpcCastCallout(0x9EA2)
	private final ModifiableCallout<AbilityCastStart> beastlyRoar = ModifiableCallout.durationBasedCall("Beastly Roar", "Away from Boss");

	@NpcCastCallout(0x9EA8)
	private final ModifiableCallout<AbilityCastStart> turaliStoneIV = ModifiableCallout.durationBasedCall("Turali Stone IV", "Stack on {event.target}");

	@NpcCastCallout(0x9EAA)
	private final ModifiableCallout<AbilityCastStart> sonicHowl = ModifiableCallout.durationBasedCall("Sonic Howl", "Raidwide");

	@NpcCastCallout(0x9EAB)
	private final ModifiableCallout<AbilityCastStart> slabber = ModifiableCallout.durationBasedCall("Slabber", "Buster on {event.target}");

}
