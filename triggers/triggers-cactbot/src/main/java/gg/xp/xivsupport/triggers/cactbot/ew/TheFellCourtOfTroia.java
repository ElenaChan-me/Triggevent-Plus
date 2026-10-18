package gg.xp.xivsupport.triggers.cactbot.ew;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/dungeon/the_fell_court_of_troia.ts をもとに移植
@CalloutRepo(name = "The Fell Court Of Troia", duty = KnownDuty.TheFellCourtOfTroia)
public class TheFellCourtOfTroia extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public TheFellCourtOfTroia(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheFellCourtOfTroia);
	}

	// イーヴィルドリーマー

	@NpcCastCallout(value = {0x73BB, 0x73B8}, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> darkVision = ModifiableCallout.durationBasedCall("Dark Vision", "Avoid Eye Lasers");

	private final ModifiableCallout<AbilityCastStart> dreamerVoidGravity = ModifiableCallout.durationBasedCall("Void Gravity (Evil Dreamer)", "Spread");

	@NpcCastCallout(value = 0x73BC, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> uniteMare = ModifiableCallout.durationBasedCall("Unite Mare", "Kill One Eye");

	// ベアトリーチェ

	@NpcCastCallout(0x747D)
	private final ModifiableCallout<AbilityCastStart> deathForeseenSelf = ModifiableCallout.durationBasedCall("Death Foreseen (Boss)", "Look Away");

	@NpcCastCallout(value = 0x7484, suppressMs = 10_000)
	private final ModifiableCallout<AbilityCastStart> deathForeseenOther = ModifiableCallout.durationBasedCall("Death Foreseen (Rings)", "Look Away from Rings");

	@NpcCastCallout(0x7475)
	private final ModifiableCallout<AbilityCastStart> beatificScorn = ModifiableCallout.durationBasedCall("Beatific Scorn", "Away from Exploding Lines");

	@NpcCastCallout(0x7480)
	private final ModifiableCallout<AbilityCastStart> hush = ModifiableCallout.durationBasedCall("Hush", "Buster on {event.target}");

	private final ModifiableCallout<AbilityCastStart> voidNail = ModifiableCallout.durationBasedCall("Void Nail", "Spread");

	@NpcCastCallout(0x747A)
	private final ModifiableCallout<AbilityCastStart> eyeOfTroia = ModifiableCallout.durationBasedCall("Eye of Troia", "Raidwide");

	@NpcCastCallout(0x79E8)
	private final ModifiableCallout<AbilityCastStart> antipressure = ModifiableCallout.durationBasedCall("Antipressure", "Stack on {event.target}");

	// スカルミリョーネ

	@NpcCastCallout(0x7631)
	private final ModifiableCallout<AbilityCastStart> cursedEcho = ModifiableCallout.durationBasedCall("Cursed Echo", "Raidwide + Bleed");

	private final ModifiableCallout<AbilityCastStart> rottenRampage = ModifiableCallout.durationBasedCall("Rotten Rampage", "Spread");

	@NpcCastCallout(0x761C)
	private final ModifiableCallout<AbilityCastStart> vacuumWave = ModifiableCallout.durationBasedCall("Vacuum Wave", "Knockback into Wall");

	@NpcCastCallout(0x7633)
	private final ModifiableCallout<AbilityCastStart> blightedBladework = ModifiableCallout.durationBasedCall("Blighted Bladework", "Get Behind and Out");

	@NpcCastCallout(0x7635)
	private final ModifiableCallout<AbilityCastStart> blightedSweep = ModifiableCallout.durationBasedCall("Blighted Sweep", "Get Behind");

	// 7623 は雑魚フェーズ中、762E はその後
	@NpcCastCallout({0x7623, 0x762E})
	private final ModifiableCallout<AbilityCastStart> voidVortex = ModifiableCallout.durationBasedCall("Void Vortex", "Stack on {event.target}");

	private final ModifiableCallout<AbilityCastStart> scarmiglioneVoidGravity = ModifiableCallout.durationBasedCall("Void Gravity (Scarmiglione)", "Spread");

	@NpcCastCallout(0x7637)
	private final ModifiableCallout<AbilityCastStart> firedamp = ModifiableCallout.durationBasedCall("Firedamp", "Tank Cleave on {event.target}");

	// 対象者だけに散開を知らせる
	@HandleEvents
	public void spreadOnYou(EventContext context, AbilityCastStart event) {
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		ModifiableCallout<AbilityCastStart> call;
		if (event.abilityIdMatches(0x73BA)) {
			call = dreamerVoidGravity;
		}
		else if (event.abilityIdMatches(0x747F)) {
			call = voidNail;
		}
		else if (event.abilityIdMatches(0x7619)) {
			call = rottenRampage;
		}
		else if (event.abilityIdMatches(0x7622)) {
			call = scarmiglioneVoidGravity;
		}
		else {
			return;
		}
		context.accept(call.getModified(event));
	}

}
