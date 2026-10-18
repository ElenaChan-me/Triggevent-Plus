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
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/raid/o1n.ts をもとに移植
@CalloutRepo(name = "Deltascape V10", duty = KnownDuty.DeltascapeV10)
public class O1n extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public O1n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.DeltascapeV10);
	}

	@NpcCastCallout(0x23E1)
	private final ModifiableCallout<AbilityCastStart> blaze = ModifiableCallout.durationBasedCall("Blaze", "Stack on {event.target}");

	@NpcCastCallout(0x23DE)
	private final ModifiableCallout<AbilityCastStart> breathWing = ModifiableCallout.durationBasedCall("Breath Wing", "Sides of Boss");

	@NpcCastCallout(0x23E2)
	private final ModifiableCallout<AbilityCastStart> clamp = ModifiableCallout.durationBasedCall("Clamp", "Away from Front");

	// ノックバック無効が効かない
	@NpcCastCallout(0x1ED8)
	private final ModifiableCallout<AbilityCastStart> downburst = ModifiableCallout.durationBasedCall("Downburst", "Knockback");

	@NpcCastCallout(0x23DC)
	private final ModifiableCallout<AbilityCastStart> roar = ModifiableCallout.durationBasedCall("Roar", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> charybdis = ModifiableCallout.durationBasedCall("Charybdis", "HP to 1");

	@HandleEvents
	public void charybdis(EventContext context, AbilityCastStart event) {
		// 回復しないと次の全体攻撃で倒れるので、回復役にだけ知らせる
		if (event.abilityIdMatches(0x23DB) && state.playerJobMatches(job -> job.isHealer() || job == Job.BLU)) {
			context.accept(charybdis.getModified(event));
		}
	}

	@NpcCastCallout(0x23D7)
	private final ModifiableCallout<AbilityCastStart> twinBolt = ModifiableCallout.durationBasedCall("Twin Bolt", "Buster on {event.target}");

	// 本体への自己詠唱で、終わると同時にプレイヤーのマーカーが発動する
	@NpcCastCallout(0x23D9)
	private final ModifiableCallout<AbilityCastStart> levinbolt = ModifiableCallout.durationBasedCall("Levinbolt", "Spread");

}
