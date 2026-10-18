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
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/trial/titania.ts をもとに移植
@CalloutRepo(name = "The Dancing Plague", duty = KnownDuty.TheDancingPlague)
public class Titania extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public Titania(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheDancingPlague);
	}

	@NpcCastCallout(0x3D5C)
	private final ModifiableCallout<AbilityCastStart> brightSabbath = ModifiableCallout.durationBasedCall("Bright Sabbath", "Raidwide");

	@NpcCastCallout(0x3D5D)
	private final ModifiableCallout<AbilityCastStart> phantomOut = ModifiableCallout.durationBasedCall("Phantom Rune (Out)", "Out");

	@NpcCastCallout(0x3D5E)
	private final ModifiableCallout<AbilityCastStart> phantomIn = ModifiableCallout.durationBasedCall("Phantom Rune (In)", "In");

	@NpcCastCallout(0x3D45)
	private final ModifiableCallout<AbilityCastStart> mist = ModifiableCallout.durationBasedCall("Mist Rune", "Water Positions");

	private final ModifiableCallout<AbilityCastStart> flame = new ModifiableCallout<>("Flame Rune", "Stack in Puddles");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> flameSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x3D47),
			(e1, s) -> {
				s.waitMs(6_000);
				s.updateCall(flame, e1);
			});

	@NpcCastCallout(0x3D5B)
	private final ModifiableCallout<AbilityCastStart> divination = ModifiableCallout.durationBasedCall("Divination Rune", "Tank Cleave");

	private final ModifiableCallout<AbilityCastStart> frostRune1 = new ModifiableCallout<>("Frost Rune 1", "Get Middle, Shiva Circles");
	private final ModifiableCallout<AbilityCastStart> frostRune2 = new ModifiableCallout<>("Frost Rune 2", "Out");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> frostRuneSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x3D2A),
			(e1, s) -> {
				s.updateCall(frostRune1, e1);
				s.waitMs(6_500);
				s.updateCall(frostRune2, e1);
			});

	private final ModifiableCallout<AbilityUsedEvent> frostRune3 = new ModifiableCallout<>("Frost Rune 3", "In");
	private final RepeatSuppressor frostRune3Supp = new RepeatSuppressor(Duration.ofSeconds(60));

	@HandleEvents
	public void frostRune3(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x3D4E) && frostRune3Supp.check(event)) {
			context.accept(frostRune3.getModified(event));
		}
	}

	@NpcCastCallout(0x3D2E)
	private final ModifiableCallout<AbilityCastStart> growthRune = ModifiableCallout.durationBasedCall("Growth Rune", "Avoid Roots");

	@PlayerHeadmarker(0x8B)
	private final ModifiableCallout<HeadMarkerEvent> uplift = new ModifiableCallout<>("Uplift", "Spread");

	@PlayerHeadmarker(0xBD)
	private final ModifiableCallout<HeadMarkerEvent> peasebomb = new ModifiableCallout<>("Peasebomb", "Spread");

	private final ModifiableCallout<HeadMarkerEvent> pucksBreathOnYou = new ModifiableCallout<>("Puck's Breath (On You)", "Stack on You");
	private final ModifiableCallout<HeadMarkerEvent> pucksBreath = new ModifiableCallout<>("Puck's Breath", "Stack on {event.target}");

	@HandleEvents
	public void pucksBreath(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0xA1)) {
			context.accept((event.getTarget().isThePlayer() ? pucksBreathOnYou : pucksBreath).getModified(event));
		}
	}

	@NpcCastCallout(0x3D59)
	private final ModifiableCallout<AbilityCastStart> pucksRebuke = ModifiableCallout.durationBasedCall("Puck's Rebuke", "Knockback");

	private final ModifiableCallout<AbilityUsedEvent> addPhaseTank = new ModifiableCallout<>("Mini Add Phase (Tank)", "Group Adds East on Mustardseed");
	private final ModifiableCallout<AbilityUsedEvent> addPhase = new ModifiableCallout<>("Mini Add Phase", "Kill Mustardseed East");
	private final RepeatSuppressor addPhaseSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void addPhase(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x3D31) || !addPhaseSupp.check(event)) {
			return;
		}
		boolean tank = state.playerJobMatches(job -> job.isTank() || job == Job.BLU);
		context.accept((tank ? addPhaseTank : addPhase).getModified(event));
	}

}
