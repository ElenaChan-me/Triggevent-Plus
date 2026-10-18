package gg.xp.xivsupport.triggers.cactbot.arr;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/dungeon/the_tam_tara_depocraft_hard.ts をもとに移植
@CalloutRepo(name = "The Tam Tara Deepcroft Hard", duty = KnownDuty.TheTamTaraDeepcroftHard)
public class TheTamTaraDepocraftHard extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public TheTamTaraDepocraftHard(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheTamTaraDeepcroftHard);
	}

	// 対象ごとにイベントが来るので1回にまとめる
	@NpcAbilityUsedCallout(value = 0x956, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> inhumanity = new ModifiableCallout<>("Inhumanity", "Stack (ignore adds)");
}
