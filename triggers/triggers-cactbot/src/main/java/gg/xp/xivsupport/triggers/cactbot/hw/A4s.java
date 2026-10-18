package gg.xp.xivsupport.triggers.cactbot.hw;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.BuffRemoved;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/raid/a4s.ts をもとに移植
@CalloutRepo(name = "Alexander The Burden Of The Father Savage", duty = KnownDuty.AlexanderTheBurdenOfTheFatherSavage)
public class A4s extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public A4s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AlexanderTheBurdenOfTheFatherSavage);
	}

	@PlayerHeadmarker(0x00AE)
	private final ModifiableCallout<HeadMarkerEvent> discoid = new ModifiableCallout<>("Discoid", "Orbs on You");

	private final ModifiableCallout<BuffRemoved> stunLeg = new ModifiableCallout<>("Stun Leg", "Stun {event.target}");

	@HandleEvents
	public void stunResistanceLost(EventContext context, BuffRemoved event) {
		// スタン耐性が切れた直後なら再びスタンが通る
		if (event.buffIdMatches(0x27) && state.playerJobMatches(job -> job.isTank() || job.isMeleeDps())) {
			context.accept(stunLeg.getModified(event));
		}
	}

	@NpcCastCallout(0x13E7)
	private final ModifiableCallout<AbilityCastStart> mortalRevolution = ModifiableCallout.durationBasedCall("Mortal Revolution", "Raidwide");

	@NpcCastCallout(0xF5E)
	private final ModifiableCallout<AbilityCastStart> carnage = ModifiableCallout.durationBasedCall("Carnage", "Laser Tethers, Spread");

	@NpcCastCallout(value = 0xF64, onYou = true)
	private final ModifiableCallout<AbilityCastStart> judgmentNisiA = ModifiableCallout.durationBasedCall("Judgment Nisi A", "Nisi A on You");

	@NpcCastCallout(value = 0xF65, onYou = true)
	private final ModifiableCallout<AbilityCastStart> judgmentNisiB = ModifiableCallout.durationBasedCall("Judgment Nisi B", "Nisi B on You");
}
