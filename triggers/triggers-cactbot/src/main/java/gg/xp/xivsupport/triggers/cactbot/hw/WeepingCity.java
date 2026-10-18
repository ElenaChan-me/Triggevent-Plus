package gg.xp.xivsupport.triggers.cactbot.hw;

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
import gg.xp.xivsupport.events.actlines.events.RawAddCombatantEvent;
import gg.xp.xivsupport.events.actlines.events.SystemLogMessageEvent;
import gg.xp.xivsupport.events.state.RawXivCombatantInfo;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/alliance/weeping_city.ts をもとに移植
@CalloutRepo(name = "The Weeping City Of Mhach", duty = KnownDuty.TheWeepingCityOfMhach)
public class WeepingCity extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long SEALED_OFF_LOG_ID = 0x7DC;
	private static final long OZMASPHERE_NPC_ID = 4889;
	private static final long BULB_HAIR_NAME_ID = 4899;
	private static final long AXE_HAIR_NAME_ID = 4900;
	private static final long SCYTHE_HAIR_NAME_ID = 4898;
	private static final long ENTANGLEMENT_NAME_ID = 4904;

	private XivState state;

	public WeepingCity(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheWeepingCityOfMhach);
	}

	// 0017 と 003E のマーカーは複数のボスで使い回されるので、封鎖メッセージで戦闘中のボスを判別する
	private boolean arachneStarted;
	private boolean ozmaStarted;
	private boolean calStarted;

	@HandleEvents
	public void bossTracker(EventContext context, SystemLogMessageEvent event) {
		if (event.getId() != SEALED_OFF_LOG_ID) {
			return;
		}
		switch ((int) event.getParam1()) {
			case 0x6E0 -> arachneStarted = true;
			case 0x6E5 -> {
				arachneStarted = false;
				ozmaStarted = true;
			}
			case 0x6E6 -> {
				ozmaStarted = false;
				calStarted = true;
			}
			default -> {
			}
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> stickyWicket = new ModifiableCallout<>("Sticky Wicket", "Spread");
	private final RepeatSuppressor stickyWicketSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	private final ModifiableCallout<HeadMarkerEvent> shadowBurst = new ModifiableCallout<>("Shadow Burst", "Stack on {event.target}");

	private final ModifiableCallout<HeadMarkerEvent> meteorStack = new ModifiableCallout<>("Meteor (Ozma)", "Stack");
	private final RepeatSuppressor meteorStackSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	private final ModifiableCallout<HeadMarkerEvent> arachneWeb = new ModifiableCallout<>("Arachne Web", "Drop Web Outside");

	private final ModifiableCallout<HeadMarkerEvent> particleBeamOnYou = new ModifiableCallout<>("Particle Beam: On You", "16x Sky Laser on You");
	private final ModifiableCallout<HeadMarkerEvent> particleBeamAvoid = new ModifiableCallout<>("Particle Beam: Not on You", "Avoid Sky Lasers");

	@HandleEvents
	public void sharedMarkers(EventContext context, HeadMarkerEvent event) {
		boolean onYou = event.getTarget().isThePlayer();
		if (event.markerIdMatches(0x3C)) {
			if (stickyWicketSupp.check(event)) {
				context.accept(stickyWicket.getModified(event));
			}
		}
		else if (event.markerIdMatches(0x3E)) {
			if (arachneStarted) {
				context.accept(shadowBurst.getModified(event));
			}
			else if (ozmaStarted && meteorStackSupp.check(event)) {
				// 各パーティに1つずつ付くので、誰に付いたかは読み上げない
				context.accept(meteorStack.getModified(event));
			}
		}
		else if (event.markerIdMatches(0x17)) {
			if (arachneStarted) {
				if (onYou) {
					context.accept(arachneWeb.getModified(event));
				}
			}
			else if (calStarted) {
				context.accept((onYou ? particleBeamOnYou : particleBeamAvoid).getModified(event));
			}
		}
	}

	@NpcCastCallout(0x183A)
	private final ModifiableCallout<AbilityCastStart> frondAffeared = ModifiableCallout.durationBasedCall("Frond Affeared", "Look Away");

	@PlayerHeadmarker(0x37)
	private final ModifiableCallout<HeadMarkerEvent> brandOfTheFallen = new ModifiableCallout<>("Brand of the Fallen", "Dorito Stack");

	@PlayerHeadmarker(0x19)
	private final ModifiableCallout<HeadMarkerEvent> darkEruption = new ModifiableCallout<>("Dark Eruption", "Puddles on You");

	private final ModifiableCallout<AbilityCastStart> beguilingMist = ModifiableCallout.durationBasedCall("Beguiling Mist", "Interrupt {event.source}");

	@HandleEvents
	public void beguilingMistCast(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x17CE) && state.playerJobMatches(Job::caresAboutInterrupt)) {
			context.accept(beguilingMist.getModified(event));
		}
	}

	@NpcCastCallout(0x17D4)
	private final ModifiableCallout<AbilityCastStart> mortalRay = ModifiableCallout.durationBasedCall("Mortal Ray", "Look Away from {event.source}");

	@NpcCastCallout(0x17CB)
	private final ModifiableCallout<AbilityCastStart> hellWind = ModifiableCallout.durationBasedCall("Hell Wind", "HP to 1");

	@NpcCastCallout(0x17CA)
	private final ModifiableCallout<AbilityCastStart> megaDeath = ModifiableCallout.durationBasedCall("Mega Death", "Stand in One Puddle");

	@PlayerHeadmarker(0x39)
	private final ModifiableCallout<HeadMarkerEvent> meteorImpact = new ModifiableCallout<>("Meteor Impact", "Drop Meteor Back or Left");

	// ピラミッド形態への移行。直後にエクセクレイションが来る
	@NpcAbilityUsedCallout(value = 0x1826, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> execration = new ModifiableCallout<>("Execration", "Get Off Rectangle Platform");

	// キューブ形態への移行。直後にフレアスターとタンクレーザーが来る
	@NpcAbilityUsedCallout(value = 0x1803, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> flareStarRing = new ModifiableCallout<>("Flare Star", "In");

	private final ModifiableCallout<AbilityUsedEvent> tankLasers = new ModifiableCallout<>("Tank Lasers (Tank)", "Tank Lasers, Avoid Party");
	private final ModifiableCallout<AbilityUsedEvent> avoidTanks = new ModifiableCallout<>("Tank Lasers (Non-Tank)", "Avoid Tanks");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> tankLasersSq = SqtTemplates.sq(10_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x1803),
			(e1, s) -> {
				// フレアスターのコールと重ならないようにずらす
				s.waitMs(4_000);
				s.updateCall(state.playerJobMatches(Job::isTank) ? tankLasers : avoidTanks, e1);
			});

	private final ModifiableCallout<RawAddCombatantEvent> flareStarOrbs = new ModifiableCallout<>("Flare Star Orbs", "Get Orbs");
	private final ModifiableCallout<RawAddCombatantEvent> livingLockAxes = new ModifiableCallout<>("Living Lock (Axes and Bulbs)", "Close to Axes, Avoid Bulbs");
	private final RepeatSuppressor livingLockAxesSupp = new RepeatSuppressor(Duration.ofSeconds(5));
	private final ModifiableCallout<RawAddCombatantEvent> livingLockScythes = new ModifiableCallout<>("Living Lock (Scythes)", "Avoid Scythe Lines");
	private final RepeatSuppressor livingLockScythesSupp = new RepeatSuppressor(Duration.ofSeconds(5));
	private final ModifiableCallout<RawAddCombatantEvent> entanglement = new ModifiableCallout<>("Entanglement", "Avoid Purple Circles");
	private final RepeatSuppressor entanglementSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void addedCombatant(EventContext context, RawAddCombatantEvent event) {
		RawXivCombatantInfo info = event.getFullInfo();
		long npcId = info != null ? info.getBnpcId() : event.getEntity().getbNpcId();
		long nameId = info != null ? info.getBnpcNameId() : event.getEntity().getbNpcNameId();
		if (npcId == OZMASPHERE_NPC_ID) {
			// 割らないと爆発するので、受けに行くタンク・ヒーラー（と青魔）にだけ知らせる
			if (state.playerJobMatches(job -> job.isSupport() || job == Job.BLU)) {
				context.accept(flareStarOrbs.getModified(event));
			}
		}
		else if (nameId == BULB_HAIR_NAME_ID || nameId == AXE_HAIR_NAME_ID) {
			if (livingLockAxesSupp.check(event)) {
				context.accept(livingLockAxes.getModified(event));
			}
		}
		else if (nameId == SCYTHE_HAIR_NAME_ID) {
			if (livingLockScythesSupp.check(event)) {
				context.accept(livingLockScythes.getModified(event));
			}
		}
		else if (nameId == ENTANGLEMENT_NAME_ID) {
			if (entanglementSupp.check(event)) {
				context.accept(entanglement.getModified(event));
			}
		}
	}

	private final ModifiableCallout<BuffApplied> accelerationBomb = ModifiableCallout.<BuffApplied>durationBasedCall("Acceleration Bomb", "Stop Everything").statusIcon(0x430);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> accelerationBombSq = SqtTemplates.callWhenDurationIs(
			BuffApplied.class, ba -> ba.buffIdMatches(0x430) && ba.getTarget().isThePlayer(), accelerationBomb, Duration.ofSeconds(3));

	@NpcCastCallout(0x1802)
	private final ModifiableCallout<AbilityCastStart> assimilation = ModifiableCallout.durationBasedCall("Assimilation", "Look Away");

	// 予兆が鎌の見た目だけなので、ヘアカットの前にコイフチェンジで左右を知らせる
	@NpcAbilityUsedCallout(value = 0x180A, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> coifChangeLeft = new ModifiableCallout<>("Coif Change (Left)", "Right");

	@NpcAbilityUsedCallout(value = 0x180E, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> coifChangeRight = new ModifiableCallout<>("Coif Change (Right)", "Left");

	@NpcAbilityUsedCallout(value = 0x181D, suppressMs = 5_000)
	private final ModifiableCallout<AbilityUsedEvent> garrotte = new ModifiableCallout<>("Garrotte", "Kill Extra Add");

	// マナドレインで中間フェーズが終わり、すぐにダンシングマッドが来る
	@NpcAbilityUsedCallout(value = 0x1819, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> dancingMad = new ModifiableCallout<>("Dancing Mad", "Raidwide");

	@NpcCastCallout(0x1822)
	private final ModifiableCallout<AbilityCastStart> penetration = ModifiableCallout.durationBasedCall("Penetration", "Look Away");

	@NpcCastCallout(0x1820)
	private final ModifiableCallout<AbilityCastStart> depthCharge = ModifiableCallout.durationBasedCall("Depth Charge", "Away from Front");

}
