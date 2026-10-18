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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.models.XivCombatant;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/raid/e9n.ts をもとに移植
@CalloutRepo(name = "Edens Promise Umbra", duty = KnownDuty.EdensPromiseUmbra)
public class E9n extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public E9n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.EdensPromiseUmbra);
	}

	@NpcCastCallout(0x55ED)
	private final ModifiableCallout<AbilityCastStart> groundRazingParticleBeam = ModifiableCallout.durationBasedCall("Ground-Razing Particle Beam", "Raidwide");

	@NpcCastCallout(0x5223)
	private final ModifiableCallout<AbilityCastStart> artOfDarknessRight = ModifiableCallout.durationBasedCall("The Art of Darkness (Right Cleave)", "Left");

	@NpcCastCallout(0x5224)
	private final ModifiableCallout<AbilityCastStart> artOfDarknessLeft = ModifiableCallout.durationBasedCall("The Art of Darkness (Left Cleave)", "Right");

	@NpcCastCallout(0x5AFF)
	private final ModifiableCallout<AbilityCastStart> wideAngleParticleBeam = ModifiableCallout.durationBasedCall("Wide-Angle Particle Beam", "Away from Front");

	@NpcCastCallout(0x55EB)
	private final ModifiableCallout<AbilityCastStart> zeroFormParticleBeam = ModifiableCallout.durationBasedCall("Zero-Form Particle Beam", "Buster on {event.target}");

	@NpcCastCallout(0x4FC6)
	private final ModifiableCallout<AbilityCastStart> emptyPlane = ModifiableCallout.durationBasedCall("Empty Plane", "Raidwide");

	@NpcCastCallout(0x4FA2)
	private final ModifiableCallout<AbilityCastStart> obscureWoods = ModifiableCallout.durationBasedCall("Obscure Woods", "Raidwide");

	@NpcCastCallout(0x55DE)
	private final ModifiableCallout<AbilityCastStart> wasteAway = ModifiableCallout.durationBasedCall("Waste Away", "Knockback");

	@PlayerHeadmarker(0x0C)
	private final ModifiableCallout<HeadMarkerEvent> stygianTether = new ModifiableCallout<>("Stygian Tether", "Wait Near Bramble");

	private final ModifiableCallout<TetherEvent> stygianBreak = new ModifiableCallout<>("Stygian Tether (Break)", "Break Chains");

	@HandleEvents
	public void stygianBreak(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x12) && event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			context.accept(stygianBreak.getModified(event));
		}
	}

	@NpcCastCallout(value = {0x55DF, 0x55E0, 0x55E1}, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> wideAnglePhaser = ModifiableCallout.durationBasedCall("Wide-Angle Phaser", "Go Sides at Tethered Wall");

	@NpcCastCallout(0x55E2)
	private final ModifiableCallout<AbilityCastStart> rejuvenatingBalm = ModifiableCallout.durationBasedCall("Rejuvenating Balm", "Away from Tethered Walls");

	@NpcCastCallout(0x5155)
	private final ModifiableCallout<AbilityCastStart> delugeOfDarkness = ModifiableCallout.durationBasedCall("Deluge of Darkness", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> particleConcentration = new ModifiableCallout<>("Particle Concentration", "Get Towers", 4_000);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> particleConcentrationSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x55E8),
			(e1, s) -> {
				// 塔が出揃うまで待つ
				s.waitMs(4_000);
				s.updateCall(particleConcentration, e1);
			});

	private final ModifiableCallout<AbilityCastStart> hyperchargedCondensation = new ModifiableCallout<>("Hypercharged Condensation", "Kill Adds", 5_000);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> hyperchargedCondensationSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x532E),
			(e1, s) -> {
				// 雑魚が出てから呼ぶ
				s.waitMs(2_000);
				s.updateCall(hyperchargedCondensation, e1);
			});

	@NpcCastCallout(0x55DC)
	private final ModifiableCallout<AbilityCastStart> antiAirParticleBeam = ModifiableCallout.durationBasedCall("Anti-Air Particle Beam", "Out");

}
