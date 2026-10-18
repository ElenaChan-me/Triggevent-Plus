package gg.xp.xivsupport.triggers.cactbot.sb;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/dungeon/drowned_city_of_skalla.ts をもとに移植
@CalloutRepo(name = "The Drowned City Of Skalla", duty = KnownDuty.TheDrownedCityOfSkalla)
public class DrownedCityOfSkalla extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public DrownedCityOfSkalla(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheDrownedCityOfSkalla);
	}

	// ケルピー

	@NpcCastCallout(0x2650)
	private final ModifiableCallout<AbilityCastStart> risingSeas = ModifiableCallout.durationBasedCall("Rising Seas", "Raidwide");

	@NpcCastCallout(0x2651)
	private final ModifiableCallout<AbilityCastStart> hydroPull = ModifiableCallout.durationBasedCall("Hydro Pull", "Out");

	@NpcCastCallout(0x2652)
	private final ModifiableCallout<AbilityCastStart> hydroPush = ModifiableCallout.durationBasedCall("Hydro Push", "Knockback");

	@PlayerHeadmarker(0x2B)
	private final ModifiableCallout<HeadMarkerEvent> bloodyPuddle = new ModifiableCallout<>("Bloody Puddle", "Spread");

	// フロドリック・ポイズンタン

	@NpcCastCallout(0x2661)
	private final ModifiableCallout<AbilityCastStart> rustingClaw = ModifiableCallout.durationBasedCall("Rusting Claw", "Tank Cleave");

	@NpcCastCallout(0x2663)
	private final ModifiableCallout<AbilityCastStart> tailDrive = ModifiableCallout.durationBasedCall("Tail Drive", "Tail Cleave");

	@NpcCastCallout(0x2664)
	private final ModifiableCallout<AbilityCastStart> theSpin = ModifiableCallout.durationBasedCall("The Spin", "Out");

	@PlayerHeadmarker(0x79)
	private final ModifiableCallout<HeadMarkerEvent> ringOfChaos = new ModifiableCallout<>("Ring of Chaos", "Ring on You");

	@PlayerHeadmarker(0x7A)
	private final ModifiableCallout<HeadMarkerEvent> crossOfChaos = new ModifiableCallout<>("Cross of Chaos", "Cross on You");

	@PlayerHeadmarker(0x1C)
	private final ModifiableCallout<HeadMarkerEvent> circleOfChaos = new ModifiableCallout<>("Circle of Chaos", "Spread");

	@NpcCastCallout(0x2665)
	private final ModifiableCallout<AbilityCastStart> eyeOfTheFire = ModifiableCallout.durationBasedCall("Eye of the Fire", "Look Away");

	@NpcCastCallout(0x2662)
	private final ModifiableCallout<AbilityCastStart> wordsOfWoe = ModifiableCallout.durationBasedCall("Words of Woe", "Away From Front");

}
