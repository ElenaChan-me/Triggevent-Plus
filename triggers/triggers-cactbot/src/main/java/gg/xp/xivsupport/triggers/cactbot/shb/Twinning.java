package gg.xp.xivsupport.triggers.cactbot.shb;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/dungeon/twinning.ts をもとに移植
@CalloutRepo(name = "The Twinning", duty = KnownDuty.TheTwinning)
public class Twinning extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public Twinning(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheTwinning);
	}

	private boolean canStunOrSilence() {
		return state.playerJobMatches(job -> job.isTank() || job.isMeleeDps() || job.isPranged());
	}

	private boolean canSilence() {
		return state.playerJobMatches(job -> job.caresAboutInterrupt());
	}

	// 雑魚戦

	private final ModifiableCallout<AbilityCastStart> mainHead = ModifiableCallout.durationBasedCall("Main Head", "Stun {event.source}");
	private final ModifiableCallout<AbilityCastStart> berserk = ModifiableCallout.durationBasedCall("Berserk", "Interrupt {event.source}");
	private final ModifiableCallout<AbilityCastStart> tonzeSwing = ModifiableCallout.durationBasedCall("128-Tonze Swing", "Interrupt {event.source}");

	@HandleEvents
	public void interrupts(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x3DBC) && canStunOrSilence()) {
			context.accept(mainHead.getModified(event));
		}
		else if (event.abilityIdMatches(0x3DC0) && canStunOrSilence()) {
			context.accept(berserk.getModified(event));
		}
		else if (event.abilityIdMatches(0x3DBA) && canSilence()) {
			context.accept(tonzeSwing.getModified(event));
		}
	}

	// アルファ・ザグナル

	private final ModifiableCallout<HeadMarkerEvent> impactPounce = new ModifiableCallout<>("Impact / Pounce", "Spread, Avoid Cages");
	private final RepeatSuppressor impactPounceSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	@HandleEvents
	public void impactPounce(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x32, 0x33, 0x34, 0x35, 0x5A) && impactPounceSupp.check(event)) {
			context.accept(impactPounce.getModified(event));
		}
	}

	@NpcCastCallout(0x3D64)
	private final ModifiableCallout<AbilityCastStart> beastlyRoar = ModifiableCallout.durationBasedCall("Beastly Roar", "Raidwide");

	@NpcCastCallout(0x3D65)
	private final ModifiableCallout<AbilityCastStart> augurium = ModifiableCallout.durationBasedCall("Augurium", "Tank Cleave");

	private final ModifiableCallout<HeadMarkerEvent> chargeEradicatedOnYou = new ModifiableCallout<>("Charge Eradicated (On You)", "Stack on You");
	private final ModifiableCallout<HeadMarkerEvent> chargeEradicated = new ModifiableCallout<>("Charge Eradicated", "Stack on {event.target}");

	@HandleEvents
	public void chargeEradicated(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x5D)) {
			context.accept((event.getTarget().isThePlayer() ? chargeEradicatedOnYou : chargeEradicated).getModified(event));
		}
	}

	// ミトリダテス

	@NpcCastCallout(0x3DED)
	private final ModifiableCallout<AbilityCastStart> thunderBeam = ModifiableCallout.durationBasedCall("Thunder Beam", "Buster on {event.target}");

	private final ModifiableCallout<AbilityCastStart> allaganThunder = ModifiableCallout.durationBasedCall("Allagan Thunder", "Spread");

	@HandleEvents
	public void allaganThunder(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x3DEF) && event.getTarget().isThePlayer()) {
			context.accept(allaganThunder.getModified(event));
		}
	}

	// タイクーン

	@NpcCastCallout(value = 0x3DF8, suppressMs = 15_000)
	private final ModifiableCallout<AbilityCastStart> magitekCrossray = ModifiableCallout.durationBasedCall("Magitek Crossray", "Cardinal Lasers");

	@NpcCastCallout(value = 0x3DF2, suppressMs = 15_000)
	private final ModifiableCallout<AbilityCastStart> defensiveArray = ModifiableCallout.durationBasedCall("Defensive Array", "Outer Lasers");

	@NpcCastCallout(0x3DFB)
	private final ModifiableCallout<AbilityCastStart> railCannon = ModifiableCallout.durationBasedCall("Rail Cannon", "Buster on {event.target}");

	@NpcCastCallout(0x3E0C)
	private final ModifiableCallout<AbilityCastStart> magicrystal = ModifiableCallout.durationBasedCall("Magicrystal", "Spread");

	@NpcCastCallout(0x3DFC)
	private final ModifiableCallout<AbilityCastStart> discharger = ModifiableCallout.durationBasedCall("Discharger", "Raidwide");

}
