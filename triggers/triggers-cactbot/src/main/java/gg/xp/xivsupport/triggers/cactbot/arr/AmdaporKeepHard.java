package gg.xp.xivsupport.triggers.cactbot.arr;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.AnyPlayerStatusCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/dungeon/amdapor_keep_hard.ts をもとに移植
@CalloutRepo(name = "Amdapor Keep Hard", duty = KnownDuty.AmdaporKeepHard)
public class AmdaporKeepHard extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public AmdaporKeepHard(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AmdaporKeepHard);
	}

	@NpcCastCallout(0xC65)
	private final ModifiableCallout<AbilityCastStart> entrance = ModifiableCallout.durationBasedCall("Entrance", "Look Away");

	@PlayerHeadmarker(0x0F)
	private final ModifiableCallout<HeadMarkerEvent> boss2Marker = new ModifiableCallout<>("Boss 2 Marker", "Go Behind Statue");

	@NpcCastCallout(0xC63)
	private final ModifiableCallout<AbilityCastStart> invisible = ModifiableCallout.durationBasedCall("Invisible", "Kill Luminescence, Stay Close to Boss");

	// 複数人に同時に付くので1回だけ呼ぶ
	@AnyPlayerStatusCallout(value = {0x29B, 0x260}, suppressMs = 5_000)
	private final ModifiableCallout<BuffApplied> imobilize = new ModifiableCallout<>("Imobilize", "Kill Adds");
}
