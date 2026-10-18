package gg.xp.xivsupport.triggers.cactbot.shb;

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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/trial/wol.ts をもとに移植
@CalloutRepo(name = "The Seat Of Sacrifice", duty = KnownDuty.TheSeatOfSacrifice)
public class Wol extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long DELUGE_MARKER = 0x57;
	private static final long STACK_MARKER = 0xA1;

	private enum Imbued {
		NONE,
		BLIZZARD,
		FIRE
	}

	private XivState state;

	public Wol(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheSeatOfSacrifice);
	}

	private Imbued imbued = Imbued.NONE;
	private boolean delugeOnYou;

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		imbued = Imbued.NONE;
		delugeOnYou = false;
	}

	private final ModifiableCallout<AbilityUsedEvent> terrorUnleashed = new ModifiableCallout<>("Terror Unleashed", "Full Heal Everyone");
	private final RepeatSuppressor terrorSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void terrorUnleashed(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x4F27)
				&& state.playerJobMatches(job -> job.isHealer() || job == Job.BLU)
				&& terrorSupp.check(event)) {
			context.accept(terrorUnleashed.getModified(event));
		}
	}

	@NpcCastCallout(0x4F11)
	private final ModifiableCallout<AbilityCastStart> coruscantSaberIn = ModifiableCallout.durationBasedCall("Coruscant Saber (In)", "Get Under");

	@NpcCastCallout(0x4F10)
	private final ModifiableCallout<AbilityCastStart> coruscantSaberOut = ModifiableCallout.durationBasedCall("Coruscant Saber (Out)", "Out");

	@NpcCastCallout(0x4F2D)
	private final ModifiableCallout<AbilityCastStart> absoluteBlizzard = ModifiableCallout.durationBasedCall("Absolute Blizzard III", "Keep Moving");

	@NpcCastCallout(0x4F2E)
	private final ModifiableCallout<AbilityCastStart> absoluteFire = ModifiableCallout.durationBasedCall("Absolute Fire III", "Stop Everything");

	private final ModifiableCallout<AbilityCastStart> imbuedOutMove = ModifiableCallout.durationBasedCall("Imbued Coruscance (Out, Blizzard)", "Out, then Move");
	private final ModifiableCallout<AbilityCastStart> imbuedOutStop = ModifiableCallout.durationBasedCall("Imbued Coruscance (Out, Fire)", "Out, then Stop");
	private final ModifiableCallout<AbilityCastStart> imbuedOut = ModifiableCallout.durationBasedCall("Imbued Coruscance (Out, Unknown)", "Out");
	private final ModifiableCallout<AbilityCastStart> imbuedUnderMove = ModifiableCallout.durationBasedCall("Imbued Coruscance (In, Blizzard)", "Under, then Move");
	private final ModifiableCallout<AbilityCastStart> imbuedUnderStop = ModifiableCallout.durationBasedCall("Imbued Coruscance (In, Fire)", "Under, then Stop");
	private final ModifiableCallout<AbilityCastStart> imbuedUnder = ModifiableCallout.durationBasedCall("Imbued Coruscance (In, Unknown)", "Under");

	@HandleEvents
	public void imbuedCoruscance(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x4F13)) {
			imbued = Imbued.BLIZZARD;
		}
		else if (event.abilityIdMatches(0x4F12)) {
			imbued = Imbued.FIRE;
		}
		else if (event.abilityIdMatches(0x4F4B)) {
			context.accept((switch (imbued) {
				case BLIZZARD -> imbuedOutMove;
				case FIRE -> imbuedOutStop;
				case NONE -> imbuedOut;
			}).getModified(event));
		}
		else if (event.abilityIdMatches(0x4F4C)) {
			context.accept((switch (imbued) {
				case BLIZZARD -> imbuedUnderMove;
				case FIRE -> imbuedUnderStop;
				case NONE -> imbuedUnder;
			}).getModified(event));
		}
	}

	@NpcCastCallout(0x4F42)
	private final ModifiableCallout<AbilityCastStart> swordOfLight = ModifiableCallout.durationBasedCall("Sword of Light", "Out of Triangle");

	private final ModifiableCallout<AbilityCastStart> summonWyrm = new ModifiableCallout<>("Summon Wyrm", "Avoid Wyrm Dash");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> summonWyrmSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x4F41),
			(e1, s) -> {
				s.waitMs(6_000);
				s.updateCall(summonWyrm, e1);
			});

	private final ModifiableCallout<AbilityCastStart> bitterEndOnYou = ModifiableCallout.durationBasedCall("Bitter End: On You", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> bitterEndTankSwap = ModifiableCallout.durationBasedCall("Bitter End: Tank Swap", "Tank Swap");
	private final ModifiableCallout<AbilityCastStart> bitterEndOther = ModifiableCallout.durationBasedCall("Bitter End: On Other", "Buster on {event.target}");

	@HandleEvents
	public void bitterEnd(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x4F28)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(bitterEndOnYou.getModified(event));
		}
		else if (state.playerJobMatches(Job::isTank)) {
			context.accept(bitterEndTankSwap.getModified(event));
		}
		else {
			context.accept(bitterEndOther.getModified(event));
		}
	}

	@NpcCastCallout(0x4F29)
	private final ModifiableCallout<AbilityCastStart> elddragonDive = ModifiableCallout.durationBasedCall("Elddragon Dive", "Raidwide");

	@PlayerHeadmarker(DELUGE_MARKER)
	private final ModifiableCallout<HeadMarkerEvent> delugeOfDeath = new ModifiableCallout<>("Deluge of Death", "Get Out");

	// 火遁の頭割りの時には残らないよう、少ししたら消す
	@AutoFeed
	private final SequentialTrigger<BaseEvent> delugeTracker = SqtTemplates.sq(15_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(DELUGE_MARKER) && hm.getTarget().isThePlayer(),
			(e1, s) -> {
				delugeOnYou = true;
				s.waitMs(10_000);
				delugeOnYou = false;
			});

	private final ModifiableCallout<HeadMarkerEvent> absoluteHolyKatonSan = new ModifiableCallout<>("Absolute Holy/Katon San", "Stack on {event.target}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> absoluteHolyKatonSanSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(STACK_MARKER),
			(e1, s) -> {
				// 大洪水のマーカーと同時に付くことがあるので、少し待ってから判定する
				s.waitMs(500);
				if (!delugeOnYou) {
					s.updateCall(absoluteHolyKatonSan, e1);
				}
			});

	@PlayerHeadmarker(0xEA)
	private final ModifiableCallout<HeadMarkerEvent> radiantBraver = new ModifiableCallout<>("Radiant Braver", "Earthshaker on You");

	@NpcCastCallout(0x515D)
	private final ModifiableCallout<AbilityCastStart> radiantDesperado = ModifiableCallout.durationBasedCall("Radiant Desperado", "Stack Groups");

	@PlayerHeadmarker(0xE9)
	private final ModifiableCallout<HeadMarkerEvent> radiantMeteor = new ModifiableCallout<>("Radiant Meteor", "Go to Corner");

	private final ModifiableCallout<AbilityUsedEvent> suitonSan = new ModifiableCallout<>("Suiton San", "Knockback");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> suitonSanSq = SqtTemplates.sq(10_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x4F38) && aue.isFirstTarget(),
			(e1, s) -> {
				s.waitMs(5_000);
				s.updateCall(suitonSan, e1);
			});

	private final ModifiableCallout<TetherEvent> flareBreath = new ModifiableCallout<>("Spectral Egi Flare Breath", "Point Tether Outside");

	@HandleEvents
	public void flareBreath(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x11) && event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			context.accept(flareBreath.getModified(event));
		}
	}

}
