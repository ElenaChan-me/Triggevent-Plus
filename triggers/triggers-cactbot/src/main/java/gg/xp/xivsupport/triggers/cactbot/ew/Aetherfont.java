package gg.xp.xivsupport.triggers.cactbot.ew;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/dungeon/aetherfont.ts をもとに移植
@CalloutRepo(name = "The Aetherfont", duty = KnownDuty.TheAetherfont)
public class Aetherfont extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public Aetherfont(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheAetherfont);
	}

	// リュングバクル

	@NpcCastCallout(0x823A)
	private final ModifiableCallout<AbilityCastStart> upsweep = ModifiableCallout.durationBasedCall("Upsweep", "Raidwide");

	@NpcCastCallout(0x8237)
	private final ModifiableCallout<AbilityCastStart> bodyslam = ModifiableCallout.durationBasedCall("Body Slam", "Raidwide");

	@NpcCastCallout(0x8240)
	private final ModifiableCallout<AbilityCastStart> lyngbakrTidalBreath = ModifiableCallout.durationBasedCall("Tidal Breath (Lyngbakr)", "Get Behind");

	// 散開マーカーは詠唱が終わってから出るので、詠唱後もしばらく残す
	@NpcCastCallout(0x823D)
	private final ModifiableCallout<AbilityCastStart> floodstide = new ModifiableCallout<>("Floodstide", "Spread", 8_000);

	@NpcCastCallout(0x823F)
	private final ModifiableCallout<AbilityCastStart> tidalspout = ModifiableCallout.durationBasedCall("Tidalspout", "Stack on {event.target}");

	@NpcCastCallout(0x8241)
	private final ModifiableCallout<AbilityCastStart> sonicBloop = ModifiableCallout.durationBasedCall("Sonic Bloop", "Buster on {event.target}");

	// アルカス

	@NpcCastCallout(0x872D)
	private final ModifiableCallout<AbilityCastStart> battleCryInitial = ModifiableCallout.durationBasedCall("Battle Cry (Initial)", "Raidwide");

	@NpcCastCallout(0x8254)
	private final ModifiableCallout<AbilityCastStart> battleCry = ModifiableCallout.durationBasedCall("Battle Cry", "Raidwide, Get Middle");

	@NpcCastCallout(0x8258)
	private final ModifiableCallout<AbilityCastStart> ripperClaw = ModifiableCallout.durationBasedCall("Ripper Claw", "Buster on {event.target}");

	@NpcCastCallout(0x8798)
	private final ModifiableCallout<AbilityCastStart> lightningClaw = ModifiableCallout.durationBasedCall("Lightning Claw", "Stack on {event.target}");

	private final ModifiableCallout<AbilityUsedEvent> electrify = new ModifiableCallout<>("Electrify", "Out");
	private final RepeatSuppressor electrifySupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void electrify(EventContext context, AbilityUsedEvent event) {
		// ライトニングクロー本体の着弾の後に中央範囲が来る
		if (event.abilityIdMatches(0x8256) && electrifySupp.check(event)) {
			context.accept(electrify.getModified(event));
		}
	}

	// オクトマンモス

	@NpcCastCallout(0x824C)
	private final ModifiableCallout<AbilityCastStart> tidalRoar = ModifiableCallout.durationBasedCall("Tidal Roar", "Raidwide");

	@NpcCastCallout(0x8248)
	private final ModifiableCallout<AbilityCastStart> salineSpit = ModifiableCallout.durationBasedCall("Saline Spit", "Stand Between Platforms");

	@NpcCastCallout(value = 0x8684, onYou = true)
	private final ModifiableCallout<AbilityCastStart> waterDrop = ModifiableCallout.durationBasedCall("Water Drop", "Spread");

	@NpcCastCallout(0x824A)
	private final ModifiableCallout<AbilityCastStart> octomammothTidalBreath = ModifiableCallout.durationBasedCall("Tidal Breath (Octomammoth)", "Get Behind");

	@NpcCastCallout(0x86F7)
	private final ModifiableCallout<AbilityCastStart> breathstroke = ModifiableCallout.durationBasedCall("Breathstroke", "Get Behind (Avoid Tentacles)");

}
