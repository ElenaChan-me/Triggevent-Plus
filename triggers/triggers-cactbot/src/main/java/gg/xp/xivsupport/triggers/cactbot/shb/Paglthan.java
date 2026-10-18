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
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.BuffRemoved;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/dungeon/paglthan.ts をもとに移植
@CalloutRepo(name = "Paglthan", duty = KnownDuty.Paglthan)
public class Paglthan extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long LIGHTNING_ROD = 0xA0E;
	// 中央安置は 0x5B4A と 0x5B4B が4本ずつ、外周安置は 0x5B4A が5本
	private static final int LUNAR_FLARE_OUTSIDE = 5;
	private static final int LUNAR_FLARE_MIDDLE = 8;

	private XivState state;

	public Paglthan(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.Paglthan);
	}

	private int lunarFlares;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		lunarFlares = 0;
	}

	// アムフルク

	@NpcCastCallout(0x5C4E)
	private final ModifiableCallout<AbilityCastStart> criticalRip = ModifiableCallout.durationBasedCall("Critical Rip", "Buster on {event.target}");

	@NpcCastCallout(0x5C4D)
	private final ModifiableCallout<AbilityCastStart> electricBurst = ModifiableCallout.durationBasedCall("Electric Burst", "Raidwide");

	private final ModifiableCallout<BuffApplied> lightningRodGain = new ModifiableCallout<BuffApplied>("Lightning Rod (Gain)", "Go to a Lightning Rod").statusIcon(LIGHTNING_ROD);
	private final ModifiableCallout<BuffRemoved> lightningRodLose = new ModifiableCallout<>("Lightning Rod (Lose)", "Go Middle");

	@HandleEvents
	public void lightningRodGain(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(LIGHTNING_ROD) && event.getTarget().isThePlayer()) {
			context.accept(lightningRodGain.getModified(event));
		}
	}

	@HandleEvents
	public void lightningRodLose(EventContext context, BuffRemoved event) {
		if (event.buffIdMatches(LIGHTNING_ROD) && event.getTarget().isThePlayer()) {
			context.accept(lightningRodLose.getModified(event));
		}
	}

	// 道中

	@NpcCastCallout(0x5C97)
	private final ModifiableCallout<AbilityCastStart> ballistic = ModifiableCallout.durationBasedCall("Ballistic", "Knockback");

	@NpcCastCallout(0x5C9E)
	private final ModifiableCallout<AbilityCastStart> defensiveReaction = ModifiableCallout.durationBasedCall("Defensive Reaction", "Raidwide");

	// ルナバハムート

	@NpcCastCallout(0x5B47)
	private final ModifiableCallout<AbilityCastStart> twistedScream = ModifiableCallout.durationBasedCall("Twisted Scream", "Raidwide");

	private final ModifiableCallout<HeadMarkerEvent> akhMornOnYou = new ModifiableCallout<>("Akh Morn (On You)", "Stack on You");
	private final ModifiableCallout<HeadMarkerEvent> akhMorn = new ModifiableCallout<>("Akh Morn", "Stack on {event.target}");

	@HandleEvents
	public void akhMorn(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x5D)) {
			context.accept((event.getTarget().isThePlayer() ? akhMornOnYou : akhMorn).getModified(event));
		}
	}

	@PlayerHeadmarker(0x17)
	private final ModifiableCallout<HeadMarkerEvent> megaFlareSpread = new ModifiableCallout<>("Megaflare (Spread)", "Spread");

	private final ModifiableCallout<AbilityUsedEvent> megaFlareMove = new ModifiableCallout<>("Megaflare (Move)", "Away from Circles");

	@HandleEvents
	public void megaFlareMove(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x5B4D) && event.getTarget().isThePlayer()) {
			context.accept(megaFlareMove.getModified(event));
		}
	}

	@PlayerHeadmarker(0x104)
	private final ModifiableCallout<HeadMarkerEvent> kanRhaiMarker = new ModifiableCallout<>("Kan Rhai (Marker)", "Kan Rhai on You");

	private final ModifiableCallout<AbilityUsedEvent> kanRhaiMove = new ModifiableCallout<>("Kan Rhai (Move)", "Away from Crosses");
	private final RepeatSuppressor kanRhaiSupp = new RepeatSuppressor(Duration.ofSeconds(2));

	@HandleEvents
	public void kanRhaiMove(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x5B4F) && kanRhaiSupp.check(event)) {
			context.accept(kanRhaiMove.getModified(event));
		}
	}

	@HandleEvents
	public void lunarFlareReset(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x5B49)) {
			lunarFlares = 0;
		}
	}

	@HandleEvents
	public void lunarFlareCollect(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x5B4A, 0x5B4B)) {
			lunarFlares++;
		}
	}

	private final ModifiableCallout<AbilityCastStart> lunarFlareOutside = new ModifiableCallout<>("Lunar Flare (Outside)", "Get Outside Between Circles");
	private final ModifiableCallout<AbilityCastStart> lunarFlareMiddle = new ModifiableCallout<>("Lunar Flare (Middle)", "Go Middle");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> lunarFlareSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x5B4A, 0x5B4B),
			(e1, s) -> {
				// 同時に出る詠唱が揃うまで待ってから本数で判定する
				s.waitMs(500);
				if (lunarFlares == LUNAR_FLARE_OUTSIDE) {
					s.updateCall(lunarFlareOutside, e1);
				}
				else if (lunarFlares == LUNAR_FLARE_MIDDLE) {
					s.updateCall(lunarFlareMiddle, e1);
				}
				s.waitMs(1_000);
			});

	@NpcCastCallout(0x5B58)
	private final ModifiableCallout<AbilityCastStart> flatten = ModifiableCallout.durationBasedCall("Flatten", "Buster on {event.target}");

	@NpcCastCallout(0x5B57)
	private final ModifiableCallout<AbilityCastStart> gigaFlare = ModifiableCallout.durationBasedCall("Gigaflare", "Raidwide");

}
