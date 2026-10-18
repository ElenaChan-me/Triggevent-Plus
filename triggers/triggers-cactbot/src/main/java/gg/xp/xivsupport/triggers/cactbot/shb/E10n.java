package gg.xp.xivsupport.triggers.cactbot.shb;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/raid/e10n.ts をもとに移植
@CalloutRepo(name = "Edens Promise Litany", duty = KnownDuty.EdensPromiseLitany)
public class E10n extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public E10n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.EdensPromiseLitany);
	}

	@NpcCastCallout(0x56E5)
	private final ModifiableCallout<AbilityCastStart> deepshadowNova = ModifiableCallout.durationBasedCall("Deepshadow Nova", "Raidwide");

	@NpcCastCallout(0x56B4)
	private final ModifiableCallout<AbilityCastStart> forwardImplosion = ModifiableCallout.durationBasedCall("Forward Implosion", "Get Behind");

	@NpcCastCallout(0x56B7)
	private final ModifiableCallout<AbilityCastStart> backwardImplosion = ModifiableCallout.durationBasedCall("Backward Implosion", "Go Front");

	@NpcCastCallout(0x56B5)
	private final ModifiableCallout<AbilityCastStart> forwardShadowImplosion = ModifiableCallout.durationBasedCall("Forward Shadow Implosion", "Shadow Side");

	@NpcCastCallout(0x56B8)
	private final ModifiableCallout<AbilityCastStart> backwardShadowImplosion = ModifiableCallout.durationBasedCall("Backward Shadow Implosion", "Opposite Shadow");

	@NpcCastCallout(0x56B1)
	private final ModifiableCallout<AbilityCastStart> leftGigaSlash = ModifiableCallout.durationBasedCall("Left Giga Slash", "Right");

	@NpcCastCallout(0x56AE)
	private final ModifiableCallout<AbilityCastStart> rightGigaSlash = ModifiableCallout.durationBasedCall("Right Giga Slash", "Left");

	@NpcCastCallout(0x56AF)
	private final ModifiableCallout<AbilityCastStart> leftShadowSlash = ModifiableCallout.durationBasedCall("Shadow Slash (Left)", "Go Left of Shadows");

	@NpcCastCallout(0x56B2)
	private final ModifiableCallout<AbilityCastStart> rightShadowSlash = ModifiableCallout.durationBasedCall("Shadow Slash (Right)", "Go Right of Shadows");

	@NpcCastCallout(0x5B0B)
	private final ModifiableCallout<AbilityCastStart> shadowsEdge = ModifiableCallout.durationBasedCall("Shadow's Edge", "Tank Cleave");

	private final ModifiableCallout<AbilityCastStart> voidgate = new ModifiableCallout<>("Voidgate", "Get Puddles");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> voidgateSq = SqtTemplates.sq(30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x56DD),
			(e1, s) -> {
				// 詠唱開始から塔まで約17秒あるので、早すぎないよう少し待つ
				s.waitMs(10_000);
				s.updateCall(voidgate, e1);
			});

	@NpcCastCallout(0x56E2)
	private final ModifiableCallout<AbilityCastStart> shadowWarrior = ModifiableCallout.durationBasedCall("Shadow Warrior", "Stand Behind Tethered Dog");

	@NpcCastCallout(value = 0x5B11, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> cloakOfShadows = ModifiableCallout.durationBasedCall("Cloak of Shadows", "Away from Black Lines");

	// ヒットボックス内にいると予兆が出てからでは間に合わない
	@NpcCastCallout(0x56C7)
	private final ModifiableCallout<AbilityCastStart> throneOfShadow = ModifiableCallout.durationBasedCall("Throne of Shadow", "Out");

	@NpcCastCallout(0x56C6)
	private final ModifiableCallout<AbilityCastStart> distantScream = ModifiableCallout.durationBasedCall("Distant Scream", "Knockback");

}
