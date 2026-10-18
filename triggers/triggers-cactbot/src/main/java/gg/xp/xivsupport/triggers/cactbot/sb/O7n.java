package gg.xp.xivsupport.triggers.cactbot.sb;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.RawAddCombatantEvent;
import gg.xp.xivsupport.events.state.RawXivCombatantInfo;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/raid/o7n.ts をもとに移植
@CalloutRepo(name = "Sigmascape V30", duty = KnownDuty.SigmascapeV30)
public class O7n extends AutoChildEventHandler implements FilteredEventHandler {

	// エアフォース、ダダルマー、オルトロス、ビブリオタフ
	private static final Set<Long> killPhaseAddNameIds = Set.of(7018L, 7110L, 7111L, 7113L);

	private XivState state;

	public O7n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.SigmascapeV30);
	}

	@NpcCastCallout(0x276E)
	private final ModifiableCallout<AbilityCastStart> diffractivePlasma = ModifiableCallout.durationBasedCall("Diffractive Plasma", "Raidwide");

	@NpcCastCallout(0x276B)
	private final ModifiableCallout<AbilityCastStart> magitekRay = ModifiableCallout.durationBasedCall("Magitek Ray", "Away From Front");

	@NpcCastCallout(0x276C)
	private final ModifiableCallout<AbilityCastStart> armAndHammer = ModifiableCallout.durationBasedCall("Arm and Hammer", "Buster on {event.target}");

	@NpcCastCallout(0x2766)
	private final ModifiableCallout<AbilityCastStart> shockwave = ModifiableCallout.durationBasedCall("Shockwave", "Knockback");

	private final ModifiableCallout<BuffApplied> diffractiveLaser = new ModifiableCallout<>("Diffractive Laser", "Out");
	private final RepeatSuppressor diffractiveLaserSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	// エアフォース・シミュレーションのバフが詠唱開始の約3秒前に付くので、それを合図にする。
	// 隠れている雑魚全員に付くため重複は抑制する
	@HandleEvents
	public void diffractiveLaser(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x5D2) && diffractiveLaserSupp.check(event)) {
			context.accept(diffractiveLaser.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> preyOnYou = new ModifiableCallout<>("Prey (On You)", "Prey on You");
	private final ModifiableCallout<HeadMarkerEvent> prey = new ModifiableCallout<>("Prey", "Prey on {event.target}");

	@HandleEvents
	public void prey(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x1E)) {
			context.accept((event.getTarget().isThePlayer() ? preyOnYou : prey).getModified(event));
		}
	}

	@NpcCastCallout(0x2762)
	private final ModifiableCallout<AbilityCastStart> bombDeployment = ModifiableCallout.durationBasedCall("Bomb Deployment", "Stand in Glowing Bomb Circle");

	@NpcCastCallout(0x2752)
	private final ModifiableCallout<AbilityCastStart> demonSimulation = ModifiableCallout.durationBasedCall("Demon Simulation", "Activate 3 Person Towers");

	private final ModifiableCallout<RawAddCombatantEvent> killPhaseAdd = new ModifiableCallout<>("Kill Phase Add", "Kill {name}");

	@HandleEvents
	public void addedCombatant(EventContext context, RawAddCombatantEvent event) {
		RawXivCombatantInfo info = event.getFullInfo();
		long nameId = info != null ? info.getBnpcNameId() : event.getEntity().getbNpcNameId();
		if (killPhaseAddNameIds.contains(nameId)) {
			String name = info != null ? info.getName() : event.getEntity().getName();
			context.accept(killPhaseAdd.getModified(event, Map.of("name", name)));
		}
	}

}
