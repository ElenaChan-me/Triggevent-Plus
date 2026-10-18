package gg.xp.xivsupport.triggers.cactbot.sb;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/raid/o6n.ts をもとに移植
@CalloutRepo(name = "Sigmascape V20", duty = KnownDuty.SigmascapeV20)
public class O6n extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public O6n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.SigmascapeV20);
	}

	@NpcCastCallout(0x282C)
	private final ModifiableCallout<AbilityCastStart> demonicHowl = ModifiableCallout.durationBasedCall("Demonic Howl", "Raidwide");

	@NpcCastCallout(0x282A)
	private final ModifiableCallout<AbilityCastStart> demonicShear = ModifiableCallout.durationBasedCall("Demonic Shear", "Buster on {event.target}");

	private final ModifiableCallout<TetherEvent> demonicPain = new ModifiableCallout<>("Demonic Pain", "Away from Boss");
	// 距離に応じて線の種類が切り替わるたびに付け直されるため、しばらく抑制する
	private final RepeatSuppressor demonicPainSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	@HandleEvents
	public void demonicPain(EventContext context, TetherEvent event) {
		// 0x01 は十分離れている、0x39 は近すぎる
		if (event.tetherIdMatches(0x01, 0x39) && event.eitherTargetMatches(XivCombatant::isThePlayer) && demonicPainSupp.check(event)) {
			context.accept(demonicPain.getModified(event));
		}
	}

	@PlayerHeadmarker(0x01)
	private final ModifiableCallout<HeadMarkerEvent> demonicStone = new ModifiableCallout<>("Demonic Stone", "Drop Chasing AoE Away");

}
