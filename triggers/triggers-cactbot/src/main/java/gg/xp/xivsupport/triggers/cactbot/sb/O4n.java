package gg.xp.xivsupport.triggers.cactbot.sb;

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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/raid/o4n.ts をもとに移植
@CalloutRepo(name = "Deltascape V40", duty = KnownDuty.DeltascapeV40)
public class O4n extends AutoChildEventHandler implements FilteredEventHandler {

	// 詠唱開始から実際に位置取りが必要になるまでの間隔
	private static final long BATTLE_DELAY_MS = 6_000;
	private static final int BATTLE_TIMEOUT_MS = 10_000;
	private static final long STANDARD_FIRE_SUPPRESS_MS = 5_000;

	private XivState state;

	public O4n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.DeltascapeV40);
	}

	private int battleCount;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		battleCount = 0;
	}

	private final ModifiableCallout<AbilityCastStart> doom = ModifiableCallout.durationBasedCall("Doom", "Cleanse Doom Soon");

	@HandleEvents
	public void doomCast(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x24B7) && state.playerJobMatches(job -> job.isHealer() || job == Job.BRD)) {
			context.accept(doom.getModified(event));
		}
	}

	@NpcCastCallout(0x24BD)
	private final ModifiableCallout<AbilityCastStart> standardThunder = ModifiableCallout.durationBasedCall("Standard Thunder", "Tank Cleave");

	@NpcCastCallout(value = 0x24BA, suppressMs = STANDARD_FIRE_SUPPRESS_MS)
	private final ModifiableCallout<AbilityCastStart> standardFire = ModifiableCallout.durationBasedCall("Standard Fire", "Spread");

	@NpcCastCallout(0x24C0)
	private final ModifiableCallout<AbilityCastStart> empoweredBlizzard = ModifiableCallout.durationBasedCall("Empowered Blizzard", "Keep Moving");

	@NpcCastCallout(0x24BF)
	private final ModifiableCallout<AbilityCastStart> empoweredFire = ModifiableCallout.durationBasedCall("Empowered Fire", "Stop Everything");

	@NpcCastCallout(0x24C1)
	private final ModifiableCallout<AbilityCastStart> empoweredThunder = ModifiableCallout.durationBasedCall("Empowered Thunder", "Out");

	private final ModifiableCallout<AbilityCastStart> decisiveBattle = new ModifiableCallout<>("Decisive Battle", "Stand in the Gap");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> decisiveBattleSq = SqtTemplates.sq(BATTLE_TIMEOUT_MS,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x2408),
			(e1, s) -> {
				battleCount++;
				// 1回目は位置取りの意味がないので知らせない
				if (battleCount <= 1) {
					return;
				}
				s.waitMs(BATTLE_DELAY_MS);
				s.updateCall(decisiveBattle, e1);
			});

	private final ModifiableCallout<AbilityCastStart> zombieBreath = new ModifiableCallout<>("Zombie Breath", "Behind Head, Avoid Zombie Breath");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> zombieBreathSq = SqtTemplates.sq(BATTLE_TIMEOUT_MS,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x240A),
			(e1, s) -> {
				s.waitMs(BATTLE_DELAY_MS);
				s.updateCall(zombieBreath, e1);
			});

	@NpcCastCallout(0x24C8)
	private final ModifiableCallout<AbilityCastStart> blackHole = ModifiableCallout.durationBasedCall("Black Hole", "Avoid Black Holes");

	@NpcCastCallout(0x24B8)
	private final ModifiableCallout<AbilityCastStart> vacuumWave = ModifiableCallout.durationBasedCall("Vacuum Wave", "Knockback");

	@PlayerHeadmarker(0x57)
	private final ModifiableCallout<HeadMarkerEvent> flare = new ModifiableCallout<>("Flare", "Flare on You");

	private final ModifiableCallout<HeadMarkerEvent> holy = new ModifiableCallout<>("Holy", "Stack on {event.target}");

	@HandleEvents
	public void holy(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E)) {
			context.accept(holy.getModified(event));
		}
	}

	@NpcCastCallout(0x24C6)
	private final ModifiableCallout<AbilityCastStart> meteor = ModifiableCallout.durationBasedCall("Meteor", "Big Raidwide");

}
