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
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/trial/innocence.ts をもとに移植
@CalloutRepo(name = "The Crown Of The Immaculate", duty = KnownDuty.TheCrownOfTheImmaculate)
public class Innocence extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public Innocence(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheCrownOfTheImmaculate);
	}

	@NpcCastCallout(0x3E9A)
	private final ModifiableCallout<AbilityCastStart> realmrazer = ModifiableCallout.durationBasedCall("Realmrazer", "Raidwide");

	@NpcCastCallout(0x3E99)
	private final ModifiableCallout<AbilityCastStart> enthrall = ModifiableCallout.durationBasedCall("Enthrall", "Look Away, Get Towers");

	private final ModifiableCallout<AbilityCastStart> reprobationSwords = new ModifiableCallout<>("Reprobation Swords 2", "Swords");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> reprobationSwordsSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x3EDC),
			(e1, s) -> {
				// 詠唱3秒＋次の剣まで9.5秒
				s.waitMs(9_500);
				s.updateCall(reprobationSwords, e1);
			});

	@NpcCastCallout(0x3EEA)
	private final ModifiableCallout<AbilityCastStart> shadowreaver = ModifiableCallout.durationBasedCall("Shadowreaver", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> righteousBoltOnYou = ModifiableCallout.durationBasedCall("Righteous Bolt (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> righteousBolt = ModifiableCallout.durationBasedCall("Righteous Bolt", "Buster on {event.target}");

	@HandleEvents
	public void righteousBolt(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x3EA3)) {
			context.accept((event.getTarget().isThePlayer() ? righteousBoltOnYou : righteousBolt).getModified(event));
		}
	}

	@NpcCastCallout(0x3EC7)
	private final ModifiableCallout<AbilityCastStart> charge = ModifiableCallout.durationBasedCall("Charge", "Avoid Charge");

	private final ModifiableCallout<AbilityUsedEvent> lightPillar = new ModifiableCallout<>("Light Pillar", "Line Stack");
	private final RepeatSuppressor lightPillarSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void lightPillar(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x38FC) && !event.getSource().isPc() && lightPillarSupp.check(event)) {
			context.accept(lightPillar.getModified(event));
		}
	}

	@PlayerHeadmarker(0x8A)
	private final ModifiableCallout<HeadMarkerEvent> wingedDropOfLight = new ModifiableCallout<>("Winged Drop of Light", "Circle on You");

	@NpcCastCallout(value = 0x3EB1, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> soulAndBody = ModifiableCallout.durationBasedCall("Soul and Body", "Rotate Right");

}
