package gg.xp.xivsupport.triggers.cactbot.sb;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.Job;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/dungeon/temple_of_the_fist.ts をもとに移植
@CalloutRepo(name = "The Temple Of The Fist", duty = KnownDuty.TheTempleOfTheFist)
public class TempleOfTheFist extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public TempleOfTheFist(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheTempleOfTheFist);
	}

	// スルティとスムリティの2体が同時に詠唱する
	@NpcCastCallout(value = 0x1FD6, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> electricBurst = ModifiableCallout.durationBasedCall("Electric Burst", "Raidwide");

	// アルブダ

	@NpcCastCallout(0x1FD9)
	private final ModifiableCallout<AbilityCastStart> fourfoldShear = ModifiableCallout.durationBasedCall("Fourfold Shear", "Buster on {event.target}");

	@PlayerHeadmarker(0x59)
	private final ModifiableCallout<HeadMarkerEvent> moonseal = new ModifiableCallout<>("Moonseal", "Stand in Blue");

	@PlayerHeadmarker(0x58)
	private final ModifiableCallout<HeadMarkerEvent> sunseal = new ModifiableCallout<>("Sunseal", "Stand in Red");

	@NpcCastCallout(0x1FDC)
	private final ModifiableCallout<AbilityCastStart> portAndStar = ModifiableCallout.durationBasedCall("Port and Star", "Front/Back");

	@NpcCastCallout(0x1FDB)
	private final ModifiableCallout<AbilityCastStart> foreAndAft = ModifiableCallout.durationBasedCall("Fore and Aft", "Sides");

	@NpcCastCallout(0x1FDE)
	private final ModifiableCallout<AbilityCastStart> killerInstinct = ModifiableCallout.durationBasedCall("Killer Instinct", "Watch for Safe Spot");

	// イヴォン・コイアフィスト

	@NpcCastCallout(0x1FE7)
	private final ModifiableCallout<AbilityCastStart> spiritWave = ModifiableCallout.durationBasedCall("Spirit Wave", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> touchOfSlaughter = ModifiableCallout.durationBasedCall("Touch of Slaughter (Healer)", "Heal {event.target} Soon");

	@HandleEvents
	public void touchOfSlaughterCast(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x1FE6) && state.playerJobMatches(job -> job.isHealer() || job == Job.BLU)) {
			context.accept(touchOfSlaughter.getModified(event));
		}
	}

	@NpcAbilityUsedCallout(value = 0x1FE9, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> coeurlHeads = new ModifiableCallout<>("Coeurl Heads", "Avoid Floating Heads");

	@NpcCastCallout(0x1FED)
	private final ModifiableCallout<AbilityCastStart> rhalgrsPiece = ModifiableCallout.durationBasedCall("Rhalgr's Piece", "Away from Marker");

	@NpcCastCallout(0x1FEE)
	private final ModifiableCallout<AbilityCastStart> roseOfDestruction = ModifiableCallout.durationBasedCall("Rose of Destruction", "Stack on {event.target}");

}
