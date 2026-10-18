package gg.xp.xivsupport.triggers.cactbot.ew;

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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/dungeon/ktisis_hyperboreia.ts をもとに移植
@CalloutRepo(name = "Ktisis Hyperboreia", duty = KnownDuty.KtisisHyperboreia)
public class KtisisHyperboreia extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long BREATH_CENTER = 0xAFC;
	private static final long BREATH_RIGHT = 0xAFD;
	private static final long BREATH_LEFT = 0xAFE;

	private XivState state;

	public KtisisHyperboreia(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.KtisisHyperboreia);
	}

	private final List<Long> ladonBreaths = new ArrayList<>();
	private boolean isHermes;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		ladonBreaths.clear();
		isHermes = false;
	}

	// リッサ

	@NpcCastCallout(0x625E)
	private final ModifiableCallout<AbilityCastStart> skullDasher = ModifiableCallout.durationBasedCall("Skull Dasher", "Buster on {event.target}");

	@NpcCastCallout(0x625D)
	private final ModifiableCallout<AbilityCastStart> frigidStomp = ModifiableCallout.durationBasedCall("Frigid Stomp", "Raidwide");

	@NpcCastCallout(0x625C)
	private final ModifiableCallout<AbilityCastStart> heavySmash = ModifiableCallout.durationBasedCall("Heavy Smash", "Stack on {event.target}");

	// ラドンロード

	@NpcCastCallout(0x648F)
	private final ModifiableCallout<AbilityCastStart> scratch = ModifiableCallout.durationBasedCall("Scratch", "Buster on {event.target}");

	@NpcCastCallout(0x648D)
	private final ModifiableCallout<AbilityCastStart> intimidation = ModifiableCallout.durationBasedCall("Intimidation", "Raidwide");

	@NpcCastCallout(0x648E)
	private final ModifiableCallout<AbilityCastStart> pyricBlast = ModifiableCallout.durationBasedCall("Pyric Blast", "Stack on {event.target}");

	private final ModifiableCallout<AbilityUsedEvent> pyricBreathFirst = new ModifiableCallout<>("Pyric Breath (First)", "Away from Front");
	private final ModifiableCallout<AbilityUsedEvent> pyricBreathFront = new ModifiableCallout<>("Pyric Breath (Front Safe)", "Go Front");
	private final ModifiableCallout<AbilityUsedEvent> pyricBreathBackLeft = new ModifiableCallout<>("Pyric Breath (Back Left Safe)", "Get Behind and Left");
	private final ModifiableCallout<AbilityUsedEvent> pyricBreathBackRight = new ModifiableCallout<>("Pyric Breath (Back Right Safe)", "Get Behind and Right");

	@HandleEvents
	public void pyricBreathCollect(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(BREATH_CENTER, BREATH_RIGHT, BREATH_LEFT)) {
			ladonBreaths.add(event.getBuff().getId());
		}
	}

	// ボスが中央に戻った時点で回転が終わっていないことがあるので、少し待つ
	@AutoFeed
	private final SequentialTrigger<BaseEvent> pyricBreathSq = SqtTemplates.sq(10_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x6485) && aue.isFirstTarget(),
			(e1, s) -> {
				s.waitMs(2_000);
				List<Long> breaths = new ArrayList<>(ladonBreaths);
				ladonBreaths.clear();
				if (breaths.isEmpty()) {
					return;
				}
				// 最初のブレスは必ず中央の首
				if (breaths.size() == 1) {
					s.updateCall(pyricBreathFirst, e1);
					return;
				}
				if (!breaths.contains(BREATH_CENTER)) {
					s.updateCall(pyricBreathFront, e1);
				}
				else if (!breaths.contains(BREATH_RIGHT)) {
					s.updateCall(pyricBreathBackLeft, e1);
				}
				else if (!breaths.contains(BREATH_LEFT)) {
					s.updateCall(pyricBreathBackRight, e1);
				}
			});

	// ヘルメス

	private final ModifiableCallout<AbilityCastStart> trismegistos = ModifiableCallout.durationBasedCall("Trismegistos", "Raidwide");

	@HandleEvents
	public void trismegistosCast(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x651E)) {
			isHermes = true;
			context.accept(trismegistos.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> trueTornado = new ModifiableCallout<>("True Tornado", "Tank Cleave on {event.target}");

	// 詠唱は自分対象なので頭上マーカーで判定する。同じマーカーが前の2ボスでも使われるが、範囲が広がるのはヘルメスだけ
	@HandleEvents
	public void trueTornadoMarker(EventContext context, HeadMarkerEvent event) {
		if (isHermes && event.markerIdMatches(0xDA)) {
			context.accept(trueTornado.getModified(event));
		}
	}

	@NpcCastCallout(0x652B)
	private final ModifiableCallout<AbilityCastStart> trueAero = ModifiableCallout.durationBasedCall("True Aero", "Spread");

	private final ModifiableCallout<AbilityCastStart> trueBravery = ModifiableCallout.durationBasedCall("True Bravery", "Interrupt {event.source}");

	@HandleEvents
	public void trueBraveryCast(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x6533) && state.playerJobMatches(job -> job.caresAboutInterrupt())) {
			context.accept(trueBravery.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> cosmicKiss = new ModifiableCallout<>("Cosmic Kiss", "Hide Behind Unbroken Meteor");
	private final RepeatSuppressor cosmicKissSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void cosmicKissUsed(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x6523) && cosmicKissSupp.check(event)) {
			context.accept(cosmicKiss.getModified(event));
		}
	}

}
