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
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.RawAddCombatantEvent;
import gg.xp.xivsupport.events.state.RawXivCombatantInfo;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/trial/tsukuyomi.ts をもとに移植
@CalloutRepo(name = "Castrum Fluminis", duty = KnownDuty.CastrumFluminis)
public class Tsukuyomi extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long MIDNIGHT_HAZE_NAME_ID = 7230;
	// スタックが溜まりきる前に反対色へ移って打ち消させる
	private static final long MOON_DEBUFF_WARN_STACKS = 4;

	private XivState state;

	public Tsukuyomi(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.CastrumFluminis);
	}

	@NpcCastCallout(0x2BE3)
	private final ModifiableCallout<AbilityCastStart> tormentUntoDeath = ModifiableCallout.durationBasedCall("Torment Unto Death", "Tank Cleave");

	@NpcCastCallout(0x2BE2)
	private final ModifiableCallout<AbilityCastStart> reprimand = ModifiableCallout.durationBasedCall("Reprimand", "Raidwide");

	private final ModifiableCallout<RawAddCombatantEvent> midnightHaze = new ModifiableCallout<>("Midnight Haze", "Kill Adds");
	private final RepeatSuppressor midnightHazeSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void addedCombatant(EventContext context, RawAddCombatantEvent event) {
		RawXivCombatantInfo info = event.getFullInfo();
		long nameId = info != null ? info.getBnpcNameId() : event.getEntity().getbNpcNameId();
		if (nameId == MIDNIGHT_HAZE_NAME_ID && midnightHazeSupp.check(event)) {
			context.accept(midnightHaze.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> leadOfTheUnderworldOnYou = ModifiableCallout.durationBasedCall("Lead of the Underworld (On You)", "Line Stack on You");
	private final ModifiableCallout<AbilityCastStart> leadOfTheUnderworld = ModifiableCallout.durationBasedCall("Lead of the Underworld", "Line Stack on {event.target}");

	@HandleEvents
	public void leadOfTheUnderworld(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x2BE6)) {
			context.accept((event.getTarget().isThePlayer() ? leadOfTheUnderworldOnYou : leadOfTheUnderworld).getModified(event));
		}
	}

	@NpcCastCallout(0x2CB0)
	private final ModifiableCallout<AbilityCastStart> nightbloom = ModifiableCallout.durationBasedCall("Nightbloom", "Raidwide");

	private final ModifiableCallout<HeadMarkerEvent> lunacy = new ModifiableCallout<>("Lunacy", "Stack on {event.target}");

	@HandleEvents
	public void lunacy(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E)) {
			context.accept(lunacy.getModified(event));
		}
	}

	private final ModifiableCallout<BuffApplied> moonlit = new ModifiableCallout<BuffApplied>("Moonlit", "Move to Black").statusIcon(0x602);
	private final ModifiableCallout<BuffApplied> moonshadowed = new ModifiableCallout<BuffApplied>("Moonshadowed", "Move to White").statusIcon(0x603);

	@HandleEvents
	public void moonDebuff(EventContext context, BuffApplied event) {
		if (!event.getTarget().isThePlayer() || event.getStacks() < MOON_DEBUFF_WARN_STACKS) {
			return;
		}
		if (event.buffIdMatches(0x602)) {
			context.accept(moonlit.getModified(event));
		}
		else if (event.buffIdMatches(0x603)) {
			context.accept(moonshadowed.getModified(event));
		}
	}

	// ダンス・オブ・ザ・デッドには詠唱バーがないため、約5秒前に出る無名アクションで知らせる
	@NpcAbilityUsedCallout(value = 0x2BFD, suppressMs = 5_000)
	private final ModifiableCallout<AbilityUsedEvent> danceOfTheDead = new ModifiableCallout<>("Dance of the Dead", "Raidwide");

}
