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
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/dungeon/vanguard.ts をもとに移植
@CalloutRepo(name = "Vanguard", duty = KnownDuty.Vanguard)
public class Vanguard extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public Vanguard(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.Vanguard);
	}

	private boolean seenFirstRush;

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		seenFirstRush = false;
	}

	// 1ボス目（Vanguard Commander R8）

	@NpcCastCallout(0x8EDB)
	private final ModifiableCallout<AbilityCastStart> electrowaveR8 = ModifiableCallout.durationBasedCall("Electrowave (Vanguard Commander R8)", "Raidwide");

	// 8ECF/98E5 は刃が内側、8ED0/98E4 は刃が外側
	@NpcCastCallout({0x8ECF, 0x98E5})
	private final ModifiableCallout<AbilityCastStart> enhancedMobilityInside = ModifiableCallout.durationBasedCall("Enhanced Mobility (Blades Inside)", "Go Outside (Corners)");

	@NpcCastCallout({0x8ED0, 0x98E4})
	private final ModifiableCallout<AbilityCastStart> enhancedMobilityOutside = ModifiableCallout.durationBasedCall("Enhanced Mobility (Blades Outside)", "Go Inside");

	private final ModifiableCallout<AbilityCastStart> rushFirst = ModifiableCallout.durationBasedCall("Rush (First)", "Dodge North/South Line Cleaves");
	private final ModifiableCallout<AbilityCastStart> rushGrid = ModifiableCallout.durationBasedCall("Rush (Grid)", "Spread + Dodge Grid Cleaves");

	// 複数の雑魚が同時に詠唱するので 1 回にまとめる
	private final RepeatSuppressor rushSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void rush(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8ED9) && rushSupp.check(event)) {
			context.accept((seenFirstRush ? rushGrid : rushFirst).getModified(event));
			seenFirstRush = true;
		}
	}

	// 2ボス目（Protector）

	@NpcCastCallout(0x9129)
	private final ModifiableCallout<AbilityCastStart> electrowaveProtector = ModifiableCallout.durationBasedCall("Electrowave (Protector)", "Raidwide");

	@NpcCastCallout(0x912A)
	private final ModifiableCallout<AbilityCastStart> rapidThunder = ModifiableCallout.durationBasedCall("Rapid Thunder", "Buster on {event.target}");

	private final ModifiableCallout<BuffApplied> accelerationBomb = ModifiableCallout.<BuffApplied>durationBasedCall("Acceleration Bomb", "Stop Moving").statusIcon(0xEDA);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> accelerationBombSq = SqtTemplates.callWhenDurationIs(
			BuffApplied.class, ba -> ba.buffIdMatches(0xEDA) && ba.getTarget().isThePlayer(), accelerationBomb, Duration.ofSeconds(5));

	@NpcCastCallout(0x91E4)
	private final ModifiableCallout<AbilityCastStart> trackingBolt = ModifiableCallout.durationBasedCall("Tracking Bolt", "Spread");

	@NpcCastCallout(0x91E1)
	private final ModifiableCallout<AbilityCastStart> heavyBlastCannon = ModifiableCallout.durationBasedCall("Heavy Blast Cannon", "Stack on {event.target}");

	// 3ボス目（Zander the Snakeskinner）

	@NpcCastCallout(0x8EF2)
	private final ModifiableCallout<AbilityCastStart> electrothermia = ModifiableCallout.durationBasedCall("Electrothermia", "Raidwide");

	@NpcCastCallout(0x8EF3)
	private final ModifiableCallout<AbilityCastStart> saberRush = ModifiableCallout.durationBasedCall("Saber Rush", "Buster on {event.target}");

	@NpcCastCallout(value = 0x9422, onYou = true)
	private final ModifiableCallout<AbilityCastStart> soulbaneShock = ModifiableCallout.durationBasedCall("Soulbane Shock", "Spread");

	@NpcCastCallout(0x8EF5)
	private final ModifiableCallout<AbilityCastStart> shadeShot = ModifiableCallout.durationBasedCall("Shade Shot", "Buster on {event.target}");

	@NpcCastCallout(0x8EF4)
	private final ModifiableCallout<AbilityCastStart> screech = ModifiableCallout.durationBasedCall("Screech", "Raidwide");

	@NpcCastCallout(0x8EED)
	private final ModifiableCallout<AbilityCastStart> slitherbaneForeguard = ModifiableCallout.durationBasedCall("Slitherbane Foreguard", "Behind");

	@NpcCastCallout(0x8EEE)
	private final ModifiableCallout<AbilityCastStart> slitherbaneRearguard = ModifiableCallout.durationBasedCall("Slitherbane Rearguard", "Front");

}
