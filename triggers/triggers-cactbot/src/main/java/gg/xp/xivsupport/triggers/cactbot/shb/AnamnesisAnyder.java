package gg.xp.xivsupport.triggers.cactbot.shb;

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

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/dungeon/anamnesis_anyder.ts をもとに移植
@CalloutRepo(name = "Anamnesis Anyder", duty = KnownDuty.AnamnesisAnyder)
public class AnamnesisAnyder extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public AnamnesisAnyder(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AnamnesisAnyder);
	}

	// 1ボス

	@NpcCastCallout({0x4B69, 0x4B72})
	private final ModifiableCallout<AbilityCastStart> fetidFang = ModifiableCallout.durationBasedCall("Fetid Fang", "Buster on {event.target}");

	private final ModifiableCallout<AbilityCastStart> scrutiny = new ModifiableCallout<>("Scrutiny", "Avoid Arrow", 7_000);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> scrutinySq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x4E25),
			(e1, s) -> {
				// 矢印の向きが見えてから呼ぶ
				s.waitMs(3_000);
				s.updateCall(scrutiny, e1);
			});

	@NpcCastCallout(0x4B6A)
	private final ModifiableCallout<AbilityCastStart> inscrutability = ModifiableCallout.durationBasedCall("Inscrutability", "Raidwide");

	@NpcCastCallout({0x4E26, 0x4E27})
	private final ModifiableCallout<AbilityCastStart> luminousRay = ModifiableCallout.durationBasedCall("Luminous Ray", "Away from Front");

	// 2ボス

	@NpcCastCallout(0x4B58)
	private final ModifiableCallout<AbilityCastStart> finalVerse = ModifiableCallout.durationBasedCall("The Final Verse", "Raidwide");

	@NpcCastCallout(0x4B55)
	private final ModifiableCallout<AbilityCastStart> minaSwing = ModifiableCallout.durationBasedCall("2,000-Mina Swing", "Out");

	@NpcCastCallout(0x4B57)
	private final ModifiableCallout<AbilityCastStart> eyeOfTheCyclone = ModifiableCallout.durationBasedCall("Eye of the Cyclone", "In");

	@NpcCastCallout(0x4B54)
	private final ModifiableCallout<AbilityCastStart> minaSwipe = ModifiableCallout.durationBasedCall("2,000-Mina Swipe", "Away from Front");

	@NpcCastCallout(0x4B56)
	private final ModifiableCallout<AbilityCastStart> ragingGlower = ModifiableCallout.durationBasedCall("Raging Glower", "Away from Front");

	private final ModifiableCallout<HeadMarkerEvent> stackMarker = new ModifiableCallout<>("Open Hearth Flying Fount", "Stack on {event.target}");

	@HandleEvents
	public void stackMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E)) {
			context.accept(stackMarker.getModified(event));
		}
	}

	// 3ボス

	@NpcCastCallout(0x4B8C)
	private final ModifiableCallout<AbilityCastStart> bonebreaker = ModifiableCallout.durationBasedCall("Bonebreaker", "Buster on {event.target}");

	@NpcCastCallout(value = 0x4B7E, onYou = true)
	private final ModifiableCallout<AbilityCastStart> fallingWater = ModifiableCallout.durationBasedCall("Falling Water", "Spread");

	@NpcCastCallout(0x4B84)
	private final ModifiableCallout<AbilityCastStart> depthGrip = ModifiableCallout.durationBasedCall("Depth Grip", "Avoid Hands");

}
