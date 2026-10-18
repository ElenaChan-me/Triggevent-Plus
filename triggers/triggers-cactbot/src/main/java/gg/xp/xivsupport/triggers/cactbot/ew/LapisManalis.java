package gg.xp.xivsupport.triggers.cactbot.ew;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.Job;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.MapEffectEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;

import java.time.Duration;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/dungeon/lapis_manalis.ts をもとに移植
@CalloutRepo(name = "Lapis Manalis", duty = KnownDuty.LapisManalis)
public class LapisManalis extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long WILD_BEAST_FLAGS = 0x00020001;
	// 0x21 から順に、西列の北→南、東列の北→南
	private static final long WILD_BEAST_FIRST_LOCATION = 0x21;
	private static final String[] WILD_BEAST_ROWS = {"North", "Middle-North", "Middle-South", "South"};
	private static final Duration LATE_CALL_LEAD = Duration.ofSeconds(5);
	private static final Duration GLASSY_EYED_LEAD = Duration.ofSeconds(3);

	private XivState state;

	public LapisManalis(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.LapisManalis);
	}

	private boolean canCleanse() {
		return state.playerJobMatches(job -> job.isHealer() || job == Job.BRD);
	}

	// 道中

	@NpcCastCallout(0x8011)
	private final ModifiableCallout<AbilityCastStart> windsOfWinter = ModifiableCallout.durationBasedCall("Winds of Winter", "Raidwide");

	// アルビオン

	private final ModifiableCallout<MapEffectEvent> wildBeasts = new ModifiableCallout<>("Wild Beasts", "Stampede from {dir} ({row} Row)", 8_000);

	@HandleEvents
	public void wildBeasts(EventContext context, MapEffectEvent event) {
		if (!event.flagsMatches(WILD_BEAST_FLAGS)) {
			return;
		}
		long idx = event.getLocation() - WILD_BEAST_FIRST_LOCATION;
		if (idx < 0 || idx >= WILD_BEAST_ROWS.length * 2L) {
			return;
		}
		String dir = idx < WILD_BEAST_ROWS.length ? "West" : "East";
		String row = WILD_BEAST_ROWS[(int) (idx % WILD_BEAST_ROWS.length)];
		context.accept(wildBeasts.getModified(event, Map.of("dir", dir, "row", row)));
	}

	@NpcCastCallout(0x7A85)
	private final ModifiableCallout<AbilityCastStart> albionsEmbrace = ModifiableCallout.durationBasedCall("Albion's Embrace", "Buster on {event.target}");

	@NpcCastCallout(0x802D)
	private final ModifiableCallout<AbilityCastStart> rightSlam = ModifiableCallout.durationBasedCall("Right Slam", "Left");

	@NpcCastCallout(0x802E)
	private final ModifiableCallout<AbilityCastStart> leftSlam = ModifiableCallout.durationBasedCall("Left Slam", "Right");

	@NpcCastCallout(0x7A81)
	private final ModifiableCallout<AbilityCastStart> icebreaker = ModifiableCallout.durationBasedCall("Icebreaker", "Away from Tethered Rock");

	@NpcCastCallout(value = 0x7A83, onYou = true)
	private final ModifiableCallout<AbilityCastStart> icyThroes = ModifiableCallout.durationBasedCall("Icy Throes", "Spread");

	@NpcCastCallout(0x7A84)
	private final ModifiableCallout<AbilityCastStart> roarOfAlbion = ModifiableCallout.durationBasedCall("Roar of Albion", "Hide Behind Boulder");

	// ガラテア・マグナ

	@NpcCastCallout(0x7A91)
	private final ModifiableCallout<AbilityCastStart> waxingCycle = ModifiableCallout.durationBasedCall("Waxing Cycle", "Out then In");

	@NpcCastCallout(0x7F6E)
	private final ModifiableCallout<AbilityCastStart> waningCycle = ModifiableCallout.durationBasedCall("Waning Cycle", "In then Out");

	@NpcCastCallout(0x7A9E)
	private final ModifiableCallout<AbilityCastStart> soulNebula = ModifiableCallout.durationBasedCall("Soul Nebula", "Raidwide");

	@NpcCastCallout(0x7A9A)
	private final ModifiableCallout<AbilityCastStart> soulScythe = ModifiableCallout.durationBasedCall("Soul Scythe", "Get Behind");

	private final ModifiableCallout<BuffApplied> doom = ModifiableCallout.<BuffApplied>durationBasedCall("Doom", "Esuna {event.target}").autoIcon();

	@HandleEvents
	public void doom(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0xD24) && canCleanse()) {
			context.accept(doom.getModified(event));
		}
	}

	private final ModifiableCallout<BuffApplied> glassyEyed = ModifiableCallout.<BuffApplied>durationBasedCall("Glassy-eyed", "Look Away").autoIcon();

	// 全員に付くので、自分の分だけを見て1回にする
	@AutoFeed
	private final SequentialTrigger<BaseEvent> glassyEyedSq = SqtTemplates.callWhenDurationIs(
			BuffApplied.class, ba -> ba.buffIdMatches(0xDB7) && ba.getTarget().isThePlayer(),
			glassyEyed, GLASSY_EYED_LEAD);

	@NpcAbilityUsedCallout(value = 0x7A96, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> tenebrism = new ModifiableCallout<>("Tenebrism", "Get Towers");

	@NpcCastCallout(0x7A9F)
	private final ModifiableCallout<AbilityCastStart> darkHarvest = ModifiableCallout.durationBasedCall("Dark Harvest", "Buster on {event.target}");

	// カイナッツォ

	@NpcCastCallout(0x79A3)
	private final ModifiableCallout<AbilityCastStart> stygianDeluge = ModifiableCallout.durationBasedCall("Stygian Deluge", "Raidwide");

	// 堅守などで防げないので、着弾の直前に呼ぶ
	private final ModifiableCallout<AbilityCastStart> bodySlam = ModifiableCallout.durationBasedCall("Body Slam", "Knockback");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> bodySlamSq = SqtTemplates.callWhenDurationIs(
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x7992),
			bodySlam, LATE_CALL_LEAD);

	@NpcCastCallout(0x7A90)
	private final ModifiableCallout<AbilityCastStart> hydrofall = ModifiableCallout.durationBasedCall("Hydrofall", "Stack on {event.target}");

	private final ModifiableCallout<BuffApplied> neapTide = ModifiableCallout.<BuffApplied>durationBasedCall("Neap Tide", "Spread").autoIcon();

	@AutoFeed
	private final SequentialTrigger<BaseEvent> neapTideSq = SqtTemplates.callWhenDurationIs(
			BuffApplied.class, ba -> ba.buffIdMatches(0xD01) && ba.getTarget().isThePlayer(),
			neapTide, LATE_CALL_LEAD);

	private final ModifiableCallout<BuffApplied> springTide = ModifiableCallout.<BuffApplied>durationBasedCall("Spring Tide", "Stack on {event.target}").autoIcon();

	@AutoFeed
	private final SequentialTrigger<BaseEvent> springTideSq = SqtTemplates.callWhenDurationIs(
			BuffApplied.class, ba -> ba.buffIdMatches(0xD00),
			springTide, LATE_CALL_LEAD);

	@NpcCastCallout(0x7986)
	private final ModifiableCallout<AbilityCastStart> voidcleaver = ModifiableCallout.durationBasedCall("Voidcleaver", "Raidwide");

	@NpcCastCallout(0x798E)
	private final ModifiableCallout<AbilityCastStart> voidTorrent = ModifiableCallout.durationBasedCall("Void Torrent", "Tank Cleave");

}
