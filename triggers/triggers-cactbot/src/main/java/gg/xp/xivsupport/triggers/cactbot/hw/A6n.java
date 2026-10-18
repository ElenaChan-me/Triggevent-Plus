package gg.xp.xivsupport.triggers.cactbot.hw;

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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;

import java.time.Duration;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/raid/a6n.ts をもとに移植
@CalloutRepo(name = "Alexander The Cuff Of The Son", duty = KnownDuty.AlexanderTheCuffOfTheSon)
public class A6n extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long ENUMERATION_FIRST_MARKER = 0x40;
	private static final long ENUMERATION_LAST_MARKER = 0x42;
	private static final int ENUMERATION_FIRST_COUNT = 2;

	private XivState state;

	public A6n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AlexanderTheCuffOfTheSon);
	}

	@NpcCastCallout(value = 0x170D, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> minefield = ModifiableCallout.durationBasedCall("Minefield", "Avoid Mines");

	@NpcCastCallout(value = 0x1713, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> supercharge = ModifiableCallout.durationBasedCall("Supercharge", "Dodge Mirage Charge");

	@PlayerStatusCallout(0x3FD)
	private final ModifiableCallout<BuffApplied> lowArithmeticks = ModifiableCallout.<BuffApplied>durationBasedCall("Low Arithmeticks", "Go High").autoIcon();

	@PlayerStatusCallout(0x3FE)
	private final ModifiableCallout<BuffApplied> highArithmeticks = ModifiableCallout.<BuffApplied>durationBasedCall("High Arithmeticks", "Go Low").autoIcon();

	@NpcCastCallout(0x171F)
	private final ModifiableCallout<AbilityCastStart> bioArithmeticks = ModifiableCallout.durationBasedCall("Bio-arithmeticks", "Raidwide");

	private final ModifiableCallout<HeadMarkerEvent> enumeration = new ModifiableCallout<>("Enumeration", "{event.target}: {count}");

	@HandleEvents
	public void enumeration(EventContext context, HeadMarkerEvent event) {
		long id = event.getMarkerId();
		if (id < ENUMERATION_FIRST_MARKER || id > ENUMERATION_LAST_MARKER) {
			return;
		}
		// マーカーIDが人数に対応している（0040 = 2人, 0041 = 3人, 0042 = 4人）
		long count = ENUMERATION_FIRST_COUNT + id - ENUMERATION_FIRST_MARKER;
		context.accept(enumeration.getModified(event, Map.of("count", count)));
	}

	@NpcCastCallout(0x1728)
	private final ModifiableCallout<AbilityCastStart> superCyclone = ModifiableCallout.durationBasedCall("Super Cyclone", "Knockback");

	@NpcCastCallout(0x1722)
	private final ModifiableCallout<AbilityCastStart> ultraFlash = ModifiableCallout.durationBasedCall("Ultra Flash", "Hide Behind Ice");

	@PlayerHeadmarker(0x43)
	private final ModifiableCallout<HeadMarkerEvent> iceMarker = new ModifiableCallout<>("Ice Marker", "Ice: Freeze Tornado");

	@PlayerStatusCallout(0x3FF)
	private final ModifiableCallout<BuffApplied> compressedWater = ModifiableCallout.<BuffApplied>durationBasedCall("Compressed Water", "Water on You").autoIcon();

	private final ModifiableCallout<BuffApplied> compressedWaterExplode = ModifiableCallout.<BuffApplied>durationBasedCall("Compressed Water: Explode Soon", "Drop Water Soon").autoIcon();

	@AutoFeed
	private final SequentialTrigger<BaseEvent> compressedWaterExplodeSq = SqtTemplates.callWhenDurationIs(
			BuffApplied.class, ba -> ba.buffIdMatches(0x3FF) && ba.getTarget().isThePlayer(),
			compressedWaterExplode, Duration.ofSeconds(5));

}
