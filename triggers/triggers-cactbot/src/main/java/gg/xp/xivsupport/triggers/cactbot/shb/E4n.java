package gg.xp.xivsupport.triggers.cactbot.shb;

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
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/raid/e4n.ts をもとに移植
@CalloutRepo(name = "Edens Gate Sepulture", duty = KnownDuty.EdensGateSepulture)
public class E4n extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public E4n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.EdensGateSepulture);
	}

	@NpcCastCallout(0x40F7)
	private final ModifiableCallout<AbilityCastStart> voiceOfTheLand = ModifiableCallout.durationBasedCall("Voice of the Land", "Raidwide");

	@NpcCastCallout(0x40F8)
	private final ModifiableCallout<AbilityCastStart> earthenFury = ModifiableCallout.durationBasedCall("Earthen Fury", "Raidwide with Bleed");

	private final ModifiableCallout<AbilityCastStart> stonecrusherOnYou = ModifiableCallout.durationBasedCall("Stonecrusher (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> stonecrusher = ModifiableCallout.durationBasedCall("Stonecrusher", "Buster on {event.target}");

	@HandleEvents
	public void stonecrusher(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x40F9)) {
			context.accept((event.getTarget().isThePlayer() ? stonecrusherOnYou : stonecrusher).getModified(event));
		}
	}

	@NpcCastCallout(0x40FA)
	private final ModifiableCallout<AbilityCastStart> massiveLandslide = ModifiableCallout.durationBasedCall("Massive Landslide", "Stand In Front");

	private final ModifiableCallout<AbilityCastStart> seismicWave = new ModifiableCallout<>("Seismic Wave", "Hide Behind Boulder");
	// 岩ごとに詠唱が来るので1回だけ呼ぶ
	private final RepeatSuppressor seismicWaveSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> seismicWaveSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x40F2) && seismicWaveSupp.check(acs),
			(e1, s) -> {
				s.waitMs(6_000);
				s.updateCall(seismicWave, e1);
			});

	@NpcCastCallout(0x40F6)
	private final ModifiableCallout<AbilityCastStart> geocrush = ModifiableCallout.durationBasedCall("Geocrush", "Knockback");

	@NpcCastCallout(0x4102)
	private final ModifiableCallout<AbilityCastStart> faultZone = ModifiableCallout.durationBasedCall("Fault Zone", "Stand On Flank");

}
