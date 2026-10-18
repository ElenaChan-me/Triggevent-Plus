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
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/raid/o12s.ts をもとに移植
@CalloutRepo(name = "Alphascape V40 Savage", duty = KnownDuty.O12S)
public class O12s extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long ARCHIVE_STACK_MARKER = 0x3E;
	private static final long ARCHIVE_SPREAD_MARKER = 0x60;
	// 頭割り1つと散開4つ
	private static final int ARCHIVE_MARKER_COUNT = 5;
	// これ以下の持続時間なら先に爆発する頭割り
	private static final Duration SHORT_STACK_MAX = Duration.ofSeconds(8);

	private XivState state;

	public O12s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.O12S);
	}

	private enum WeaponPhase {
		NONE,
		BLADES,
		SHIELD
	}

	private boolean isFinalOmega;
	private boolean dpsShortStack;
	private boolean calledHelloNoMarker;
	private boolean playerHasHelloDebuff;
	private boolean beyondDefenseHitPlayer;
	private boolean seenSolarRay;
	private boolean solarRayOnPlayer;
	private WeaponPhase weaponPhase = WeaponPhase.NONE;
	private final Map<XivCombatant, Long> archiveMarkers = new HashMap<>();

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		isFinalOmega = false;
		dpsShortStack = false;
		calledHelloNoMarker = false;
		playerHasHelloDebuff = false;
		beyondDefenseHitPlayer = false;
		seenSolarRay = false;
		solarRayOnPlayer = false;
		weaponPhase = WeaponPhase.NONE;
		archiveMarkers.clear();
	}

	@HandleEvents
	public void ionEfflux(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x3357)) {
			isFinalOmega = true;
			dpsShortStack = true;
			calledHelloNoMarker = false;
			playerHasHelloDebuff = false;
			archiveMarkers.clear();
		}
	}

	// 前半

	private final ModifiableCallout<AbilityCastStart> beyondDefenseSpread = ModifiableCallout.durationBasedCall("Beyond Defense", "Spread");

	@HandleEvents
	public void beyondDefenseCast(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x332C)) {
			beyondDefenseHitPlayer = false;
			context.accept(beyondDefenseSpread.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> beyondDefenseVuln = new ModifiableCallout<>("Beyond Defense: Hit on You", "Don't Stack");

	@HandleEvents
	public void beyondDefenseHit(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x332C) && event.getTarget().isThePlayer()) {
			beyondDefenseHitPlayer = true;
			context.accept(beyondDefenseVuln.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> beyondDefenseStack = new ModifiableCallout<>("Beyond Defense: Stack", "Stack");
	private final RepeatSuppressor beyondDefenseSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> beyondDefenseStackSq = SqtTemplates.sq(5_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x332C) && beyondDefenseSupp.check(aue),
			(e1, s) -> {
				// 複数人に当たることがあるので、全員分が届くのを待つ
				s.waitMs(500);
				if (!beyondDefenseHitPlayer) {
					s.updateCall(beyondDefenseStack, e1);
				}
			});

	private final ModifiableCallout<AbilityCastStart> superliminalMotion = ModifiableCallout.durationBasedCall("Superliminal Motion", "Behind, then Spread");
	private final ModifiableCallout<AbilityCastStart> bladesSuperliminalMotion = ModifiableCallout.durationBasedCall("Superliminal Motion (Blades)", "Behind, then Out and Spread");
	private final RepeatSuppressor superliminalMotionSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void superliminalMotion(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x3334) && superliminalMotionSupp.check(event)) {
			context.accept((weaponPhase == WeaponPhase.BLADES ? bladesSuperliminalMotion : superliminalMotion).getModified(event));
		}
	}

	@NpcCastCallout(0x3352)
	private final ModifiableCallout<AbilityCastStart> laserShower = ModifiableCallout.durationBasedCall("Laser Shower", "Raidwide");

	@NpcCastCallout(0x3342)
	private final ModifiableCallout<AbilityCastStart> cosmoMemory = ModifiableCallout.durationBasedCall("Cosmo Memory", "Big Raidwide");

	private final ModifiableCallout<BuffApplied> localResonance = new ModifiableCallout<>("Local Resonance", "Move Bosses Apart");
	private final ModifiableCallout<BuffApplied> remoteResonance = new ModifiableCallout<>("Remote Resonance", "Move Bosses Together");
	private final RepeatSuppressor resonanceSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void resonance(EventContext context, BuffApplied event) {
		if (!event.buffIdMatches(0x67E, 0x67F) || event.getTarget().isPc() || !resonanceSupp.check(event)) {
			return;
		}
		context.accept((event.buffIdMatches(0x67E) ? localResonance : remoteResonance).getModified(event));
	}

	@NpcCastCallout(value = {0x3350, 0x3351}, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> solarRay = ModifiableCallout.durationBasedCall("Solar Ray", "Tankbuster");

	@HandleEvents
	public void solarRayCollect(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x3350, 0x3351)) {
			seenSolarRay = true;
			if (event.getTarget().isThePlayer()) {
				solarRayOnPlayer = true;
			}
		}
	}

	private final ModifiableCallout<AbilityCastStart> solarRayAvoid = ModifiableCallout.durationBasedCall("Solar Ray: Not Targeted", "Avoid Tank Cleave");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> solarRayAvoidSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x3350, 0x3351),
			(e1, s) -> {
				// 2体分の詠唱が揃ってから判定する
				s.waitMs(500);
				if (!solarRayOnPlayer) {
					s.updateCall(solarRayAvoid, e1);
				}
				solarRayOnPlayer = false;
			});

	private final ModifiableCallout<AbilityUsedEvent> shieldBladesSetup = new ModifiableCallout<>("Solar Ray: Weapon Setup", "Bring Bosses Middle, Face Eye");
	private final RepeatSuppressor shieldBladesSetupSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void shieldBladesSetup(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x3350, 0x3351) || !shieldBladesSetupSupp.check(event)) {
			return;
		}
		weaponPhase = WeaponPhase.NONE;
		if (state.playerJobMatches(job -> job.isTank() || job == Job.BLU)) {
			context.accept(shieldBladesSetup.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> syntheticBlades = ModifiableCallout.durationBasedCall("Synthetic Blades", "Get Middle (Blades)");

	@HandleEvents
	public void syntheticBlades(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x3301) && seenSolarRay) {
			weaponPhase = WeaponPhase.BLADES;
			context.accept(syntheticBlades.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> bladesSuperliminalSteel = new ModifiableCallout<>("Superliminal Steel (Blades)", "Intercards, Stack");
	private final RepeatSuppressor superliminalSteelSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void bladesSuperliminalSteel(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x332F) && weaponPhase == WeaponPhase.BLADES && superliminalSteelSupp.check(event)) {
			context.accept(bladesSuperliminalSteel.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> syntheticShield = ModifiableCallout.durationBasedCall("Synthetic Shield", "Intercards, Spread (Shield)");

	@HandleEvents
	public void syntheticShield(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x32FD) && seenSolarRay) {
			weaponPhase = WeaponPhase.SHIELD;
			context.accept(syntheticShield.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> shieldBeyondStrength = new ModifiableCallout<>("Beyond Strength (Shield)", "Spread, then Follow M");
	private final ModifiableCallout<AbilityUsedEvent> shieldBeyondDefense = new ModifiableCallout<>("Efficient Bladework (Shield)", "Away from M");
	private final RepeatSuppressor shieldSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void shieldMechanics(EventContext context, AbilityUsedEvent event) {
		if (weaponPhase != WeaponPhase.SHIELD) {
			return;
		}
		// どちらも詠唱バーが無いので、直前の攻撃の着弾で予告する
		if (event.abilityIdMatches(0x332E) && shieldSupp.check(event)) {
			context.accept(shieldBeyondStrength.getModified(event));
		}
		else if (event.abilityIdMatches(0x3328) && shieldSupp.check(event)) {
			context.accept(shieldBeyondDefense.getModified(event));
		}
	}

	@NpcCastCallout(value = {0x334B, 0x334C}, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> optimizedBladeDance = ModifiableCallout.durationBasedCall("Optimized Blade Dance", "Tankbuster");

	private final ModifiableCallout<HeadMarkerEvent> electricSlideSquare = new ModifiableCallout<>("Electric Slide: Square", "Square {num}");
	private final ModifiableCallout<HeadMarkerEvent> electricSlideTriangle = new ModifiableCallout<>("Electric Slide: Triangle", "Triangle {num}");

	@HandleEvents
	public void electricSlide(EventContext context, HeadMarkerEvent event) {
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		long id = event.getMarkerId();
		if (id >= 0x91 && id <= 0x94) {
			context.accept(electricSlideSquare.getModified(event, Map.<String, Object>of("num", id - 0x90)));
		}
		else if (id >= 0x95 && id <= 0x98) {
			context.accept(electricSlideTriangle.getModified(event, Map.<String, Object>of("num", id - 0x94)));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> mfStackMarker = new ModifiableCallout<>("Stack Groups", "Stack Groups");
	private final RepeatSuppressor mfStackSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void mfStackMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(ARCHIVE_STACK_MARKER) && !isFinalOmega && mfStackSupp.check(event)) {
			context.accept(mfStackMarker.getModified(event));
		}
	}

	@PlayerHeadmarker(0x57)
	private final ModifiableCallout<HeadMarkerEvent> optimizedMeteor = new ModifiableCallout<>("Optimized Meteor", "Meteor on You");

	@PlayerHeadmarker(0x17)
	private final ModifiableCallout<HeadMarkerEvent> optimizedSagittariusArrow = new ModifiableCallout<>("Optimized Sagittarius Arrow", "Arrow on You");

	@PlayerStatusCallout(0x67D)
	private final ModifiableCallout<BuffApplied> packetFilterF = new ModifiableCallout<BuffApplied>("Packet Filter F", "Attack Omega-M").autoIcon();

	@PlayerStatusCallout(0x67C)
	private final ModifiableCallout<BuffApplied> packetFilterM = new ModifiableCallout<BuffApplied>("Packet Filter M", "Attack Omega-F").autoIcon();

	// 後半

	@NpcCastCallout(0x3367)
	private final ModifiableCallout<AbilityCastStart> diffuseWaveCannonSides = ModifiableCallout.durationBasedCall("Diffuse Wave Cannon (Sides)", "Sides");

	@NpcCastCallout(0x3368)
	private final ModifiableCallout<AbilityCastStart> diffuseWaveCannonFrontBack = ModifiableCallout.durationBasedCall("Diffuse Wave Cannon (Front/Back)", "Front/Back");

	private final ModifiableCallout<AbilityCastStart> monitorsLeft = ModifiableCallout.durationBasedCall("Oversampled Wave Cannon Right (Tank)", "Monitors Left");
	private final ModifiableCallout<AbilityCastStart> dodgeLeft = ModifiableCallout.durationBasedCall("Oversampled Wave Cannon Right", "Dodge Left");
	private final ModifiableCallout<AbilityCastStart> monitorsRight = ModifiableCallout.durationBasedCall("Oversampled Wave Cannon Left (Tank)", "Monitors Right");
	private final ModifiableCallout<AbilityCastStart> dodgeRight = ModifiableCallout.durationBasedCall("Oversampled Wave Cannon Left", "Dodge Right");

	@HandleEvents
	public void oversampledWaveCannon(EventContext context, AbilityCastStart event) {
		boolean right = event.abilityIdMatches(0x3364);
		if (!right && !event.abilityIdMatches(0x3365)) {
			return;
		}
		// タンクはモニターを受けに行く側、それ以外は避ける側をコールする
		boolean tank = state.playerJobMatches(job -> job.isTank() || job == Job.BLU);
		ModifiableCallout<AbilityCastStart> call;
		if (right) {
			call = tank ? monitorsLeft : dodgeLeft;
		}
		else {
			call = tank ? monitorsRight : dodgeRight;
		}
		context.accept(call.getModified(event));
	}

	private final ModifiableCallout<HeadMarkerEvent> targetAnalysisOnYou = new ModifiableCallout<>("Target Analysis: On You", "Vuln on You");
	private final ModifiableCallout<HeadMarkerEvent> targetAnalysisOther = new ModifiableCallout<>("Target Analysis: On Other (Tank)", "Vuln on {event.target}");

	@HandleEvents
	public void targetAnalysis(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(0xE)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(targetAnalysisOnYou.getModified(event));
		}
		else if (state.playerJobMatches(job -> job.isTank() || job == Job.BLU)) {
			context.accept(targetAnalysisOther.getModified(event));
		}
	}

	@PlayerStatusCallout(0x688)
	private final ModifiableCallout<BuffApplied> localRegression = new ModifiableCallout<BuffApplied>("Local Regression", "Close Tethers").autoIcon();

	@PlayerStatusCallout(0x689)
	private final ModifiableCallout<BuffApplied> remoteRegression = new ModifiableCallout<BuffApplied>("Remote Regression", "Far Tethers").autoIcon();

	@PlayerStatusCallout(0x681)
	private final ModifiableCallout<BuffApplied> criticalOverflowBug = new ModifiableCallout<BuffApplied>("Critical Overflow Bug", "Defamation on You").autoIcon();

	@PlayerStatusCallout(0x686)
	private final ModifiableCallout<BuffApplied> latentDefect = new ModifiableCallout<BuffApplied>("Latent Defect", "Blue Marker").autoIcon();

	@PlayerStatusCallout(0x682)
	private final ModifiableCallout<BuffApplied> criticalUnderflowBug = new ModifiableCallout<BuffApplied>("Critical Underflow Bug", "Rot").autoIcon();

	@PlayerStatusCallout(0x687)
	private final ModifiableCallout<BuffApplied> cascadingLatentDefect = new ModifiableCallout<BuffApplied>("Cascading Latent Defect", "Move Out for Defamation").autoIcon();

	private final ModifiableCallout<BuffApplied> shortStackOnYou = new ModifiableCallout<BuffApplied>("Critical Synchronization Bug: Short on You", "Short Stack on You").autoIcon();
	private final ModifiableCallout<BuffApplied> longStackOnYou = new ModifiableCallout<BuffApplied>("Critical Synchronization Bug: Long on You", "Long Stack on You").autoIcon();
	private final ModifiableCallout<BuffApplied> shortStackOnOther = new ModifiableCallout<BuffApplied>("Critical Synchronization Bug: Short on Other", "Short Stack on {event.target}").autoIcon();

	@HandleEvents
	public void criticalSynchronizationBug(EventContext context, BuffApplied event) {
		if (!event.buffIdMatches(0x680)) {
			return;
		}
		Duration duration = event.getInitialDuration();
		if (duration.isZero() || duration.isNegative()) {
			return;
		}
		boolean isShort = duration.compareTo(SHORT_STACK_MAX) <= 0;
		if (event.getTarget().isThePlayer()) {
			context.accept((isShort ? shortStackOnYou : longStackOnYou).getModified(event));
		}
		else if (dpsShortStack && isShort) {
			dpsShortStack = false;
			// 青魔道士の定番の処理ではダイアモンドバックで受けるので不要
			if (!state.playerJobMatches(job -> job == Job.BLU)) {
				context.accept(shortStackOnOther.getModified(event));
			}
		}
	}

	@HandleEvents
	public void helloWorldDebuffCollect(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x680, 0x681, 0x682, 0x686) && !calledHelloNoMarker && event.getTarget().isThePlayer()) {
			playerHasHelloDebuff = true;
		}
	}

	private final ModifiableCallout<BuffApplied> helloWorldNoMarker = new ModifiableCallout<>("Hello World: No Debuff", "No Marker");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> helloWorldNoMarkerSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0x680, 0x681, 0x682, 0x686) && !calledHelloNoMarker,
			(e1, s) -> {
				s.waitMs(300);
				if (!playerHasHelloDebuff) {
					s.updateCall(helloWorldNoMarker, e1);
				}
				calledHelloNoMarker = true;
			});

	private final ModifiableCallout<HeadMarkerEvent> archiveAllStackOnOther = new ModifiableCallout<>("Archive All: No Marker", "Stack on {stackTarget}");
	private final ModifiableCallout<HeadMarkerEvent> archiveAllStackOnYou = new ModifiableCallout<>("Archive All: Stack on You", "Stack on You");
	private final ModifiableCallout<HeadMarkerEvent> archiveAllSpread = new ModifiableCallout<>("Archive All: Spread", "Spread");

	@HandleEvents
	public void archiveAllMarkers(EventContext context, HeadMarkerEvent event) {
		if (!isFinalOmega || !event.markerIdMatches(ARCHIVE_STACK_MARKER, ARCHIVE_SPREAD_MARKER)) {
			return;
		}
		XivCombatant target = event.getTarget();
		archiveMarkers.put(target, event.getMarkerId());
		if (target.isThePlayer()) {
			context.accept((event.markerIdMatches(ARCHIVE_STACK_MARKER) ? archiveAllStackOnYou : archiveAllSpread).getModified(event));
		}
		if (archiveMarkers.size() != ARCHIVE_MARKER_COUNT) {
			return;
		}
		boolean playerMarked = archiveMarkers.keySet().stream().anyMatch(XivCombatant::isThePlayer);
		if (playerMarked) {
			return;
		}
		archiveMarkers.entrySet().stream()
				.filter(entry -> entry.getValue() == ARCHIVE_STACK_MARKER)
				.map(Map.Entry::getKey)
				.findFirst()
				.ifPresent(stackTarget -> context.accept(archiveAllStackOnOther.getModified(event, Map.<String, Object>of("stackTarget", stackTarget))));
	}

}
