package gg.xp.xivsupport.triggers.cactbot.sb;

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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/raid/o8n.ts をもとに移植
@CalloutRepo(name = "Sigmascape V40", duty = KnownDuty.SigmascapeV40)
public class O8n extends AutoChildEventHandler implements FilteredEventHandler {

	private static final int manaSuppressMs = 1_000;

	private XivState state;

	public O8n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.SigmascapeV40);
	}

	@NpcCastCallout(0x292E)
	private final ModifiableCallout<AbilityCastStart> hyperdrive = ModifiableCallout.durationBasedCall("Hyperdrive", "Buster on {event.target}");

	// グレイブンイメージ

	private final ModifiableCallout<AbilityCastStart> shockwave = new ModifiableCallout<>("Shockwave", "Knockback");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> shockwaveSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x2927),
			(e1, s) -> {
				// 詠唱開始直後に言うと早すぎるため、少し遅らせる
				s.waitMs(5_000);
				s.updateCall(shockwave, e1);
			});

	@NpcCastCallout(0x2929)
	private final ModifiableCallout<AbilityCastStart> gravitationalWave = ModifiableCallout.durationBasedCall("Gravitational Wave", "East");

	@NpcCastCallout(0x292A)
	private final ModifiableCallout<AbilityCastStart> intemperateWill = ModifiableCallout.durationBasedCall("Intemperate Will", "West");

	@NpcCastCallout(0x292B)
	private final ModifiableCallout<AbilityCastStart> aveMaria = ModifiableCallout.durationBasedCall("Ave Maria", "Look At Statue");

	@NpcCastCallout(0x292C)
	private final ModifiableCallout<AbilityCastStart> indolentWill = ModifiableCallout.durationBasedCall("Indolent Will", "Look Away From Statue");

	// ケフカ

	@NpcCastCallout(0x2921)
	private final ModifiableCallout<AbilityCastStart> timelyTeleport = ModifiableCallout.durationBasedCall("Timely Teleport", "Get Outside Destination");

	@NpcCastCallout(0x2924)
	private final ModifiableCallout<AbilityCastStart> aeroAssault = ModifiableCallout.durationBasedCall("Aero Assault", "Knockback");

	@PlayerHeadmarker(0x17)
	private final ModifiableCallout<HeadMarkerEvent> flagrantFireSingle = new ModifiableCallout<>("Flagrant Fire (Single)", "Fire on You");

	private final ModifiableCallout<HeadMarkerEvent> flagrantFireStack = new ModifiableCallout<>("Flagrant Fire (Stack)", "Stack on {event.target}");

	@HandleEvents
	public void flagrantFireStack(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E)) {
			context.accept(flagrantFireStack.getModified(event));
		}
	}

	@NpcCastCallout(value = 0x291D, suppressMs = manaSuppressMs)
	private final ModifiableCallout<AbilityCastStart> trueThunder = ModifiableCallout.durationBasedCall("Thrumming Thunder (True)", "True Thunder");

	@NpcCastCallout(value = 0x291B, suppressMs = manaSuppressMs)
	private final ModifiableCallout<AbilityCastStart> fakeThunder = ModifiableCallout.durationBasedCall("Thrumming Thunder (Fake)", "Fake Thunder");

	@NpcCastCallout(value = 0x2916, suppressMs = manaSuppressMs)
	private final ModifiableCallout<AbilityCastStart> fakeIceDonut = ModifiableCallout.durationBasedCall("Blizzard (Fake Donut)", "Fake Ice: Get Out");

	@NpcCastCallout(value = 0x2919, suppressMs = manaSuppressMs)
	private final ModifiableCallout<AbilityCastStart> trueIceDonut = ModifiableCallout.durationBasedCall("Blizzard (True Donut)", "True Ice: Get In");

	@NpcCastCallout(value = 0x2914, suppressMs = manaSuppressMs)
	private final ModifiableCallout<AbilityCastStart> fakeIceNear = ModifiableCallout.durationBasedCall("Blizzard (Fake Near)", "Fake Ice: Get In");

	@NpcCastCallout(value = 0x2918, suppressMs = manaSuppressMs)
	private final ModifiableCallout<AbilityCastStart> trueIceNear = ModifiableCallout.durationBasedCall("Blizzard (True Near)", "True Ice: Get Out");

	@NpcCastCallout(0x292D)
	private final ModifiableCallout<AbilityCastStart> ultimaUpsurge = ModifiableCallout.durationBasedCall("Ultima Upsurge", "Raidwide");

}
