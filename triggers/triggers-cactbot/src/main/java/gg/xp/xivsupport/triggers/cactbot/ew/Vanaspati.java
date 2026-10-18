package gg.xp.xivsupport.triggers.cactbot.ew;

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
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/dungeon/vanaspati.ts をもとに移植
@CalloutRepo(name = "Vanaspati", duty = KnownDuty.Vanaspati)
public class Vanaspati extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public Vanaspati(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.Vanaspati);
	}

	// ターミナス・スナッチャー

	@NpcCastCallout(0x6238)
	private final ModifiableCallout<AbilityCastStart> noteOfDespair = ModifiableCallout.durationBasedCall("Note of Despair", "Raidwide");

	@NpcCastCallout(0x6231)
	private final ModifiableCallout<AbilityCastStart> mouthOff = ModifiableCallout.durationBasedCall("Mouth Off", "Go to Silent Mouth");

	@NpcCastCallout(0x6235)
	private final ModifiableCallout<AbilityCastStart> lastGasp = ModifiableCallout.durationBasedCall("Last Gasp", "Buster on {event.target}");

	@NpcCastCallout(0x6233)
	private final ModifiableCallout<AbilityCastStart> whatIsRight = ModifiableCallout.durationBasedCall("What Is Right", "Left");

	@NpcCastCallout(0x6234)
	private final ModifiableCallout<AbilityCastStart> whatIsLeft = ModifiableCallout.durationBasedCall("What Is Left", "Right");

	private final ModifiableCallout<AbilityCastStart> wallow = ModifiableCallout.durationBasedCall("Wallow", "Spread");

	// ターミナス・レッカー

	@NpcCastCallout(0x6241)
	private final ModifiableCallout<AbilityCastStart> meaninglessDestruction = ModifiableCallout.durationBasedCall("Meaningless Destruction", "Raidwide");

	@NpcCastCallout(0x6242)
	private final ModifiableCallout<AbilityCastStart> totalWreck = ModifiableCallout.durationBasedCall("Total Wreck", "Buster on {event.target}");

	private final ModifiableCallout<AbilityCastStart> aetherSprayKnockback = ModifiableCallout.durationBasedCall("Aether Spray (Knockback)", "Knockback");

	// 詠唱が長いので、残り5秒で知らせる
	@AutoFeed
	private final SequentialTrigger<BaseEvent> aetherSprayKnockbackSq = SqtTemplates.callWhenDurationIs(
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x623C), aetherSprayKnockback, Duration.ofSeconds(5));

	@NpcCastCallout(0x623B)
	private final ModifiableCallout<AbilityCastStart> aetherSprayBubble = ModifiableCallout.durationBasedCall("Aether Spray (Bubble)", "Get in Bubble");

	@NpcCastCallout(0x6C21)
	private final ModifiableCallout<AbilityCastStart> doubleHexEye = ModifiableCallout.durationBasedCall("Double Hex Eye", "Look Away");

	// スヴァルバーヌ

	@NpcCastCallout(0x6252)
	private final ModifiableCallout<AbilityCastStart> flamesOfDecay = ModifiableCallout.durationBasedCall("Flames of Decay", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> cosmicKiss = ModifiableCallout.durationBasedCall("Cosmic Kiss", "Spread");

	@NpcCastCallout(0x6253)
	private final ModifiableCallout<AbilityCastStart> gnashingOfTeeth = ModifiableCallout.durationBasedCall("Gnashing of Teeth", "Buster on {event.target}");

	@NpcCastCallout(0x6248)
	private final ModifiableCallout<AbilityCastStart> aetherialDisruption = ModifiableCallout.durationBasedCall("Aetherial Disruption", "Go to Opposite Color");

	// 対象者だけに散開を知らせる
	@HandleEvents
	public void spreadOnYou(EventContext context, AbilityCastStart event) {
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		if (event.abilityIdMatches(0x6236)) {
			context.accept(wallow.getModified(event));
		}
		else if (event.abilityIdMatches(0x6250)) {
			context.accept(cosmicKiss.getModified(event));
		}
	}

}
