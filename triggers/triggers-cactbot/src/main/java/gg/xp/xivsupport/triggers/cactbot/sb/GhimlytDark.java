package gg.xp.xivsupport.triggers.cactbot.sb;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/dungeon/ghimlyt_dark.ts をもとに移植
@CalloutRepo(name = "The Ghimlyt Dark", duty = KnownDuty.TheGhimlytDark)
public class GhimlytDark extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public GhimlytDark(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheGhimlytDark);
	}

	// マーク・III-B・魔導コロッサス

	@NpcCastCallout(0x376E)
	private final ModifiableCallout<AbilityCastStart> jarringBlow = ModifiableCallout.durationBasedCall("Jarring Blow", "Buster on {event.target}");

	@PlayerHeadmarker(0x8B)
	private final ModifiableCallout<HeadMarkerEvent> wildFireBeam = new ModifiableCallout<>("Wild Fire Beam", "Spread");

	@NpcCastCallout(0x3773)
	private final ModifiableCallout<AbilityCastStart> ceruleumVent = ModifiableCallout.durationBasedCall("Ceruleum Vent", "Raidwide");

	private final ModifiableCallout<HeadMarkerEvent> magitekRay = new ModifiableCallout<>("Magitek Ray", "Stack on {event.target}");

	@HandleEvents
	public void magitekRay(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E)) {
			context.accept(magitekRay.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> magitekSlashLeft = new ModifiableCallout<>("Magitek Slash (Clockwise)", "Rotate Left");
	private final ModifiableCallout<HeadMarkerEvent> magitekSlashRight = new ModifiableCallout<>("Magitek Slash (Counterclockwise)", "Rotate Right");
	private final RepeatSuppressor magitekSlashSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void magitekSlash(EventContext context, HeadMarkerEvent event) {
		// 0xA7 がオレンジの時計回り、0xA8 が青の反時計回り
		if (event.markerIdMatches(0xA7, 0xA8) && magitekSlashSupp.check(event)) {
			context.accept((event.markerIdMatches(0xA7) ? magitekSlashLeft : magitekSlashRight).getModified(event));
		}
	}

	// プロメテウス

	@NpcCastCallout(0x3455)
	private final ModifiableCallout<AbilityCastStart> nitrospin = ModifiableCallout.durationBasedCall("Nitrospin", "Raidwide");

	@NpcCastCallout(0x3459)
	private final ModifiableCallout<AbilityCastStart> cermetDrill = ModifiableCallout.durationBasedCall("Cermet Drill", "Buster on {event.target}");

	@NpcCastCallout(value = 0x345C, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> freezingMissile = ModifiableCallout.durationBasedCall("Freezing Missile", "Go Middle");

	// ユリア・クォ・ソラノス、アンニア・クォ・ソラノス

	@NpcCastCallout(0x3727)
	private final ModifiableCallout<AbilityCastStart> artificialPlasma = ModifiableCallout.durationBasedCall("Artificial Plasma", "Raidwide");

	@NpcCastCallout(0x3729)
	private final ModifiableCallout<AbilityCastStart> innocence = ModifiableCallout.durationBasedCall("Innocence", "Buster on {event.target}");

	@NpcCastCallout(0x372A)
	private final ModifiableCallout<AbilityCastStart> deltaTrance = ModifiableCallout.durationBasedCall("Delta Trance", "Buster on {event.target}");

	private final ModifiableCallout<HeadMarkerEvent> heirsbane = new ModifiableCallout<>("Heirsbane", "Buster on {event.target}");

	@HandleEvents
	public void heirsbane(EventContext context, HeadMarkerEvent event) {
		// 同じマーカーがNPCにも付くので、プレイヤーだけに絞る
		if (event.markerIdMatches(0x1) && event.getTarget().isPc()) {
			context.accept(heirsbane.getModified(event));
		}
	}

	@NpcAbilityUsedCallout(value = 0x3710, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> orderToBombard = new ModifiableCallout<>("Order To Bombard", "Knockback");

	@PlayerHeadmarker(0x78)
	private final ModifiableCallout<HeadMarkerEvent> coveringFire = new ModifiableCallout<>("Covering Fire", "Spread");

}
