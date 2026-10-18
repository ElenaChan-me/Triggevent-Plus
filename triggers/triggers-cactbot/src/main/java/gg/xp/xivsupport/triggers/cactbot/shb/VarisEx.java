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
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/trial/varis-ex.ts をもとに移植
@CalloutRepo(name = "Memoria Misera Extreme", duty = KnownDuty.MemoriaMiseraExtreme)
public class VarisEx extends AutoChildEventHandler implements FilteredEventHandler {

	private static final int PHASE_2 = 2;
	private static final int PHASE_5 = 5;

	private XivState state;

	public VarisEx(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.MemoriaMiseraExtreme);
	}

	private int phase;
	private boolean clonesActive;
	private boolean suppressDodgeCloneCall;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		phase = 0;
		clonesActive = false;
		suppressDodgeCloneCall = false;
	}

	// 本体の詠唱から実際の発動までの間隔が一定でないので、本体の詠唱開始で呼ぶ
	@NpcCastCallout(0x4CB5)
	private final ModifiableCallout<AbilityCastStart> ignisEst = new ModifiableCallout<>("Ignis Est", "Out");

	@NpcCastCallout(0x4CC6)
	private final ModifiableCallout<AbilityCastStart> ventusEst = new ModifiableCallout<>("Ventus Est", "In");

	private final ModifiableCallout<HeadMarkerEvent> festinaLenteClones = new ModifiableCallout<>("Festina Lente (With Clones)", "Dodge Clones + Stack");
	private final ModifiableCallout<HeadMarkerEvent> festinaLente = new ModifiableCallout<>("Festina Lente", "Stack on {event.target}");
	private final RepeatSuppressor festinaLenteSupp = new RepeatSuppressor(Duration.ofSeconds(2));

	@HandleEvents
	public void headmarkers(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0xA1) && festinaLenteSupp.check(event)) {
			if (clonesActive) {
				// 分身避けと頭割りが重なるときは、分身側の読み上げを省く
				suppressDodgeCloneCall = true;
				context.accept(festinaLenteClones.getModified(event));
			}
			else {
				context.accept(festinaLente.getModified(event));
			}
		}
	}

	@NpcCastCallout(0x4CCA)
	private final ModifiableCallout<AbilityCastStart> altius = ModifiableCallout.durationBasedCall("Altius", "Bait Slashes");

	private final ModifiableCallout<AbilityCastStart> citiusOnYou = ModifiableCallout.durationBasedCall("Citius (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> citiusAvoid = ModifiableCallout.durationBasedCall("Citius (DPS)", "Avoid Tank Cleave");
	private final ModifiableCallout<AbilityCastStart> citius = ModifiableCallout.durationBasedCall("Citius", "Buster on {event.target}");

	private final ModifiableCallout<AbilityCastStart> dodgeClones = ModifiableCallout.durationBasedCall("Terminus Est (Clones)", "Dodge Clones");

	@HandleEvents
	public void casts(EventContext context, AbilityCastStart event) {
		if (event.getSource().isPc()) {
			return;
		}
		if (event.abilityIdMatches(0x4CF0)) {
			if (event.getTarget().isThePlayer()) {
				context.accept(citiusOnYou.getModified(event));
			}
			else if (state.playerJobMatches(job -> job.isDps() && job != Job.BLU)) {
				context.accept(citiusAvoid.getModified(event));
			}
			else {
				context.accept(citius.getModified(event));
			}
		}
		else if (event.abilityIdMatches(0x4CB4) && clonesActive) {
			if (!suppressDodgeCloneCall) {
				context.accept(dodgeClones.getModified(event));
			}
			suppressDodgeCloneCall = false;
			clonesActive = false;
		}
	}

	@NpcCastCallout(0x4CD2)
	private final ModifiableCallout<AbilityCastStart> aleaIactaEst = ModifiableCallout.durationBasedCall("Alea Iacta Est", "Get Behind");

	private final ModifiableCallout<AbilityUsedEvent> aleaIactaEstFront = new ModifiableCallout<>("Alea Iacta Est (Go Front)", "Go Front");
	private final RepeatSuppressor aleaSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	private final ModifiableCallout<AbilityUsedEvent> gunshieldSides = new ModifiableCallout<>("Reinforced Gunshield (Sides)", "Front/Back");
	private final ModifiableCallout<AbilityUsedEvent> gunshieldFront = new ModifiableCallout<>("Reinforced Gunshield (Front)", "Sides");

	private final ModifiableCallout<AbilityUsedEvent> loadedGunshield = new ModifiableCallout<>("Loaded Gunshield", "Spread");
	private final ModifiableCallout<AbilityUsedEvent> loadedGunshieldFinal = new ModifiableCallout<>("Loaded Gunshield (Final Phase)", "Spread");

	private final ModifiableCallout<AbilityUsedEvent> reinforcementsTank = new ModifiableCallout<>("Reinforcements (Tank)", "Grab Tethers");
	private final ModifiableCallout<AbilityUsedEvent> reinforcements = new ModifiableCallout<>("Reinforcements", "Kill Adds");

	@HandleEvents
	public void abilities(EventContext context, AbilityUsedEvent event) {
		if (event.getSource().isPc()) {
			return;
		}
		if (event.abilityIdMatches(0x4CCC)) {
			phase = PHASE_2;
		}
		else if (event.abilityIdMatches(0x4CE2)) {
			phase = PHASE_5;
		}
		else if (event.abilityIdMatches(0x4CB3)) {
			clonesActive = true;
		}
		else if (event.abilityIdMatches(0x4CD5)) {
			// 4回目の Alea Iacta Est の後は5回目が後方なので前へ
			if (aleaSupp.check(event)) {
				context.accept(aleaIactaEstFront.getModified(event));
			}
		}
		else if (!event.isFirstTarget()) {
			return;
		}
		else if (event.abilityIdMatches(0x4CDC)) {
			context.accept(gunshieldSides.getModified(event));
		}
		else if (event.abilityIdMatches(0x4CDB)) {
			context.accept(gunshieldFront.getModified(event));
		}
		else if (event.abilityIdMatches(0x4CDE)) {
			// 4CDE は散開ダメージの約3秒前に来る。最終フェーズは頭割りと続くので区別する
			context.accept((phase == PHASE_5 ? loadedGunshieldFinal : loadedGunshield).getModified(event));
		}
		else if (event.abilityIdMatches(0x4CEA)) {
			context.accept((state.playerJobMatches(Job::isTank) ? reinforcementsTank : reinforcements).getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> electrifiedGunshield = new ModifiableCallout<>("Electrified Gunshield", "Knockback");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> electrifiedGunshieldSq = SqtTemplates.sq(30_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x4CD7) && aue.isFirstTarget(),
			(e1, s) -> {
				s.waitMs(21_500);
				s.updateCall(electrifiedGunshield, e1);
			});

	private final ModifiableCallout<AbilityUsedEvent> reinforcedGunshield = new ModifiableCallout<>("Reinforced Gunshield", "Stop Attacking");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> reinforcedGunshieldSq = SqtTemplates.sq(30_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x4CD9) && aue.isFirstTarget(),
			(e1, s) -> {
				s.waitMs(phase == PHASE_2 ? 20_000 : 10_000);
				s.updateCall(reinforcedGunshield, e1);
			});

	@AutoFeed
	private final SequentialTrigger<BaseEvent> festinaLenteCleanupSq = SqtTemplates.sq(15_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x4CC9) && aue.isFirstTarget(),
			(e1, s) -> {
				// 頭割りと分身避けが離れている場合は、分身避けを改めて呼べるように戻す
				s.waitMs(10_000);
				suppressDodgeCloneCall = false;
			});

	@NpcCastCallout(0x4E4F)
	private final ModifiableCallout<AbilityCastStart> magitekTorch = ModifiableCallout.durationBasedCall("Magitek Torch", "Stack");

	@NpcCastCallout(0x4E50)
	private final ModifiableCallout<AbilityCastStart> magitekSpark = ModifiableCallout.durationBasedCall("Magitek Spark", "Spread");

	@NpcCastCallout({0x4CE5, 0x4CE6})
	private final ModifiableCallout<AbilityCastStart> fortius = ModifiableCallout.durationBasedCall("Fortius", "Bait Puddles Out");

}
