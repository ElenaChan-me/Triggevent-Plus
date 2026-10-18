package gg.xp.xivsupport.triggers.cactbot.ew;

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

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/raid/p4n.ts をもとに移植
@CalloutRepo(name = "Asphodelos The Fourth Circle", duty = KnownDuty.AsphodelosTheFourthCircle)
public class P4n extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public P4n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AsphodelosTheFourthCircle);
	}

	// 詠唱開始直後では早すぎるものは、少し遅らせて知らせる
	private static SequentialTrigger<BaseEvent> delayedCast(long id, long delayMs, ModifiableCallout<AbilityCastStart> call) {
		return SqtTemplates.sq(30_000, AbilityCastStart.class, acs -> acs.abilityIdMatches(id),
				(e1, s) -> {
					s.waitMs(delayMs);
					s.updateCall(call, e1);
				});
	}

	@NpcCastCallout(0x6A50)
	private final ModifiableCallout<AbilityCastStart> elegantEvisceration = ModifiableCallout.durationBasedCall("Elegant Evisceration", "Tank Cleave on {event.target}");

	@NpcCastCallout(0x6A3F)
	private final ModifiableCallout<AbilityCastStart> levinstrikePinax = ModifiableCallout.durationBasedCall("Levinstrike Pinax", "Go to Corner");

	private final ModifiableCallout<AbilityCastStart> wellPinax = ModifiableCallout.durationBasedCall("Well Pinax", "Middle Knockback");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> wellPinaxSq = delayedCast(0x6A3E, 4_000, wellPinax);

	@NpcCastCallout(0x6A3C)
	private final ModifiableCallout<AbilityCastStart> acidPinax = ModifiableCallout.durationBasedCall("Acid Pinax", "Spread");

	@NpcCastCallout(0x6A3D)
	private final ModifiableCallout<AbilityCastStart> lavaPinax = ModifiableCallout.durationBasedCall("Lava Pinax", "Stack");

	@NpcCastCallout(0x6A51)
	private final ModifiableCallout<AbilityCastStart> decollation = ModifiableCallout.durationBasedCall("Decollation", "Raidwide");

	@NpcCastCallout(0x6A40)
	private final ModifiableCallout<AbilityCastStart> bloodrake = ModifiableCallout.durationBasedCall("Bloodrake", "Raidwide");

	@NpcCastCallout(0x6A4F)
	private final ModifiableCallout<AbilityCastStart> hellSkewer = ModifiableCallout.durationBasedCall("Hell Skewer", "Away from Front");

	private final ModifiableCallout<AbilityCastStart> beloneCoils = ModifiableCallout.durationBasedCall("Belone Coils", "Get Other Role Tower");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> beloneCoilsSq = delayedCast(0x69DD, 2_000, beloneCoils);

	@NpcCastCallout(0x6A4A)
	private final ModifiableCallout<AbilityCastStart> northerlyShiftSlash = ModifiableCallout.durationBasedCall("Northerly Shift (Slash)", "Go North Edge");

	@NpcCastCallout(0x6A4C)
	private final ModifiableCallout<AbilityCastStart> easterlyShiftSlash = ModifiableCallout.durationBasedCall("Easterly Shift (Slash)", "Go East Edge");

	@NpcCastCallout(0x6A4B)
	private final ModifiableCallout<AbilityCastStart> southerlyShiftSlash = ModifiableCallout.durationBasedCall("Southerly Shift (Slash)", "Go South Edge");

	@NpcCastCallout(0x6A4D)
	private final ModifiableCallout<AbilityCastStart> westerlyShiftSlash = ModifiableCallout.durationBasedCall("Westerly Shift (Slash)", "Go West Edge");

	private final ModifiableCallout<AbilityCastStart> northerlyShiftKnockback = ModifiableCallout.durationBasedCall("Northerly Shift (Knockback)", "North Knockback");
	private final ModifiableCallout<AbilityCastStart> easterlyShiftKnockback = ModifiableCallout.durationBasedCall("Easterly Shift (Knockback)", "East Knockback");
	private final ModifiableCallout<AbilityCastStart> southerlyShiftKnockback = ModifiableCallout.durationBasedCall("Southerly Shift (Knockback)", "South Knockback");
	private final ModifiableCallout<AbilityCastStart> westerlyShiftKnockback = ModifiableCallout.durationBasedCall("Westerly Shift (Knockback)", "West Knockback");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> northerlyShiftKnockbackSq = delayedCast(0x6DAE, 2_000, northerlyShiftKnockback);
	@AutoFeed
	private final SequentialTrigger<BaseEvent> easterlyShiftKnockbackSq = delayedCast(0x6DB0, 2_000, easterlyShiftKnockback);
	@AutoFeed
	private final SequentialTrigger<BaseEvent> southerlyShiftKnockbackSq = delayedCast(0x6DAF, 2_000, southerlyShiftKnockback);
	@AutoFeed
	private final SequentialTrigger<BaseEvent> westerlyShiftKnockbackSq = delayedCast(0x6DB1, 2_000, westerlyShiftKnockback);

	@NpcCastCallout(0x69D9)
	private final ModifiableCallout<AbilityCastStart> beloneBursts = ModifiableCallout.durationBasedCall("Belone Bursts", "Pop Other Role Orbs");

}
