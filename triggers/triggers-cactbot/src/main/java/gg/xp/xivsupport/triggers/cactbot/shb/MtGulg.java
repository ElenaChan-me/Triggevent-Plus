package gg.xp.xivsupport.triggers.cactbot.shb;

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

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/dungeon/mt_gulg.ts をもとに移植
@CalloutRepo(name = "Mt Gulg", duty = KnownDuty.MtGulg)
public class MtGulg extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public MtGulg(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.MtGulg);
	}

	// 道中

	private final ModifiableCallout<AbilityCastStart> punitiveLight = ModifiableCallout.durationBasedCall("Punitive Light", "Interrupt {event.source}");

	@HandleEvents
	public void punitiveLight(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x41AF) && state.playerJobMatches(job -> job.isTank() || job.isMeleeDps() || job.isPranged())) {
			context.accept(punitiveLight.getModified(event));
		}
	}

	@NpcCastCallout(0x41AB)
	private final ModifiableCallout<AbilityCastStart> tailSmash = ModifiableCallout.durationBasedCall("Tail Smash", "Ambition Tail Smash");

	// フォーギヴン・クルエルティー

	@NpcCastCallout(0x3CFB)
	private final ModifiableCallout<AbilityCastStart> rake = ModifiableCallout.durationBasedCall("Rake", "Buster on {event.target}");

	@NpcCastCallout(0x41B2)
	private final ModifiableCallout<AbilityCastStart> lumenInfinitum = ModifiableCallout.durationBasedCall("Lumen Infinitum", "Frontal Laser");

	@NpcCastCallout(0x3CFC)
	private final ModifiableCallout<AbilityCastStart> cycloneWing = ModifiableCallout.durationBasedCall("Cyclone Wing", "Raidwide");

	@NpcCastCallout(value = 0x3D00, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> typhoonWing1 = ModifiableCallout.durationBasedCall("Typhoon Wing 1", "Dodge Wind Cones");

	@NpcCastCallout(value = {0x3D01, 0x3D02}, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> typhoonWing2 = ModifiableCallout.durationBasedCall("Typhoon Wing 2", "Out of Melee");

	// フォーギヴン・ウィムズィー

	@NpcCastCallout(0x3D0B)
	private final ModifiableCallout<AbilityCastStart> sacramentOfPenance = ModifiableCallout.durationBasedCall("Sacrament of Penance", "Raidwide");

	@NpcCastCallout(0x3D09)
	private final ModifiableCallout<AbilityCastStart> catechism = ModifiableCallout.durationBasedCall("Catechism", "Buster on {event.target}");

	@NpcCastCallout(0x3D0F)
	private final ModifiableCallout<AbilityCastStart> judgmentDay = ModifiableCallout.durationBasedCall("Judgment Day", "Get Towers");

	@NpcCastCallout(0x3F7A)
	private final ModifiableCallout<AbilityCastStart> leftPalm = ModifiableCallout.durationBasedCall("Left Palm", "Left");

	@NpcCastCallout(0x3F78)
	private final ModifiableCallout<AbilityCastStart> rightPalm = ModifiableCallout.durationBasedCall("Right Palm", "Right");

	// フォーギヴン・オブセニティー

	@NpcCastCallout(0x3D14)
	private final ModifiableCallout<AbilityCastStart> orisonFortissimo = ModifiableCallout.durationBasedCall("Orison Fortissimo", "Raidwide");

	@NpcCastCallout(0x3D12)
	private final ModifiableCallout<AbilityCastStart> sforzando = ModifiableCallout.durationBasedCall("Sforzando", "Buster on {event.target}");

	@NpcCastCallout(0x3D18)
	private final ModifiableCallout<AbilityCastStart> divineDiminuendo = ModifiableCallout.durationBasedCall("Divine Diminuendo", "Max Melee Range");

	@NpcCastCallout(0x3D1A)
	private final ModifiableCallout<AbilityCastStart> convictionMarcato = ModifiableCallout.durationBasedCall("Conviction Marcato", "Get Behind");

}
