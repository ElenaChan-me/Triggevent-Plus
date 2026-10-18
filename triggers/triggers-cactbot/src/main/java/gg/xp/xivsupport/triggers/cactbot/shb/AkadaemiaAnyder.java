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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/dungeon/akadaemia_anyder.ts をもとに移植
@CalloutRepo(name = "Akadaemia Anyder", duty = KnownDuty.AkadaemiaAnyder)
public class AkadaemiaAnyder extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public AkadaemiaAnyder(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AkadaemiaAnyder);
	}

	// 1ボス

	@PlayerHeadmarker(0x87)
	private final ModifiableCallout<HeadMarkerEvent> aquaticLance = new ModifiableCallout<>("Aquatic Lance", "Puddle on You");

	@NpcCastCallout(0x3E04)
	private final ModifiableCallout<AbilityCastStart> puncture = ModifiableCallout.durationBasedCall("Puncture", "Buster on {event.target}");

	@NpcCastCallout(0x3E0A)
	private final ModifiableCallout<AbilityCastStart> tidalGuillotine = ModifiableCallout.durationBasedCall("Tidal Guillotine", "Away from Swimming Shark");

	@NpcCastCallout(0x3E0B)
	private final ModifiableCallout<AbilityCastStart> pelagicCleaver = ModifiableCallout.durationBasedCall("Pelagic Cleaver", "Sides of Swimming Shark");

	@NpcCastCallout(0x3E06)
	private final ModifiableCallout<AbilityCastStart> marineMayhem = ModifiableCallout.durationBasedCall("Marine Mayhem", "Raidwide");

	// 2ボス

	@PlayerHeadmarker(0x78)
	private final ModifiableCallout<HeadMarkerEvent> sapShower = new ModifiableCallout<>("Sap Shower", "Spread");

	@NpcCastCallout(0x3E17)
	private final ModifiableCallout<AbilityCastStart> arborStorm = ModifiableCallout.durationBasedCall("Arbor Storm", "Raidwide");

	// 道中

	private final ModifiableCallout<AbilityCastStart> noahionto = ModifiableCallout.durationBasedCall("Noahionto", "Interrupt {event.source}");

	@HandleEvents
	public void noahionto(EventContext context, AbilityCastStart event) {
		// スタンでも沈黙でも止められる
		if (event.abilityIdMatches(0x430C)
				&& state.playerJobMatches(job -> job.isTank() || job.isMeleeDps() || job.caresAboutInterrupt())) {
			context.accept(noahionto.getModified(event));
		}
	}

	// 3ボス

	@NpcCastCallout(0x3E23)
	private final ModifiableCallout<AbilityCastStart> shockbolt = ModifiableCallout.durationBasedCall("Shockbolt", "Buster on {event.target}");

	@NpcCastCallout(0x3E24)
	private final ModifiableCallout<AbilityCastStart> thunderbolt = ModifiableCallout.durationBasedCall("Thunderbolt", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> thunderstorm = new ModifiableCallout<>("Thunderstorm", "Grab Orbs");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> thunderstormSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x3E1A),
			(e1, s) -> {
				// 玉が出てから取りに行かせる
				s.waitMs(4_700);
				s.updateCall(thunderstorm, e1);
			});

}
