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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/trial/emerald_weapon.ts をもとに移植
@CalloutRepo(name = "Castrum Marinum", duty = KnownDuty.CastrumMarinum)
public class EmeraldWeapon extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public EmeraldWeapon(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.CastrumMarinum);
	}

	private boolean seenMines;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		seenMines = false;
	}

	@NpcCastCallout(0x5554)
	private final ModifiableCallout<AbilityCastStart> emeraldShot = ModifiableCallout.durationBasedCall("Emerald Shot", "Buster on {event.target}");

	@NpcCastCallout({0x5555, 0x5556, 0x5B0F})
	private final ModifiableCallout<AbilityCastStart> optimizedUltima = ModifiableCallout.durationBasedCall("Optimized Ultima", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> magitekMagnetism = new ModifiableCallout<>("Magitek Magnetism", "Get Near Tethered Mines", 6_000);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> magitekMagnetismSq = SqtTemplates.sq(20_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x5B05, 0x5B06),
			(e1, s) -> {
				// 1回目の機雷はタンクが処理しなくてよい
				boolean call = seenMines || !state.playerJobMatches(Job::isTank);
				seenMines = true;
				if (!call) {
					return;
				}
				s.waitMs(9_000);
				s.updateCall(magitekMagnetism, e1);
			});

	@NpcCastCallout({0x553F, 0x5540})
	private final ModifiableCallout<AbilityCastStart> sidescatheLeft = ModifiableCallout.durationBasedCall("Sidescathe (Left Safe)", "Left");

	@NpcCastCallout({0x5541, 0x5542})
	private final ModifiableCallout<AbilityCastStart> sidescatheRight = ModifiableCallout.durationBasedCall("Sidescathe (Right Safe)", "Right");

	@NpcCastCallout({0x553C, 0x553D})
	private final ModifiableCallout<AbilityCastStart> emeraldCrusher = ModifiableCallout.durationBasedCall("Emerald Crusher", "Knockback");

	private final ModifiableCallout<HeadMarkerEvent> divideEtImpera = new ModifiableCallout<>("Divide Et Impera (Tankbuster)", "Buster on {event.target}");

	@HandleEvents
	public void divideEtImpera(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0xDA)) {
			context.accept(divideEtImpera.getModified(event));
		}
	}

	@PlayerHeadmarker(0xF5)
	private final ModifiableCallout<HeadMarkerEvent> primusTerminusEst = new ModifiableCallout<>("Primus Terminus Est", "Knockback Arrow on You");

	@PlayerHeadmarker(0xFE)
	private final ModifiableCallout<HeadMarkerEvent> secundusTerminusEstX = new ModifiableCallout<>("Secundus Terminus Est (X)", "Go Cardinal with Sword");

	@PlayerHeadmarker(0xFD)
	private final ModifiableCallout<HeadMarkerEvent> secundusTerminusEstPlus = new ModifiableCallout<>("Secundus Terminus Est (Plus)", "Go Intercardinal with Sword");

}
