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
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.RawAddCombatantEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.RawXivCombatantInfo;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/raid/e10s.ts をもとに移植
@CalloutRepo(name = "Edens Promise Litany Savage", duty = KnownDuty.EdensPromiseLitanySavage)
public class E10s extends AutoChildEventHandler implements FilteredEventHandler {

	// 影の番号マーカーは3種類の連番
	private static final int SHADOW_MARKER_COUNT = 3;

	private XivState state;

	public E10s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.EdensPromiseLitanySavage);
	}

	private XivCombatant umbraTarget;
	private long gigaSlashCleaveDebuffId;
	private boolean clones;
	private boolean seenShadowkeeper;
	private boolean seenPitchBog;
	private final Set<Long> shadowIds = new HashSet<>();
	private final List<Long> myClone = new ArrayList<>();
	private Map<Long, Integer> shadowMarkerMap;
	private boolean headMarkerTriggered;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		umbraTarget = null;
		gigaSlashCleaveDebuffId = 0;
		clones = false;
		seenShadowkeeper = false;
		seenPitchBog = false;
		shadowIds.clear();
		myClone.clear();
		shadowMarkerMap = null;
		headMarkerTriggered = false;
	}

	@NpcCastCallout(0x573E)
	private final ModifiableCallout<AbilityCastStart> deepshadowNova = ModifiableCallout.durationBasedCall("Deepshadow Nova", "Raidwide");

	@NpcCastCallout(0x56F0)
	private final ModifiableCallout<AbilityCastStart> implosionHowl = ModifiableCallout.durationBasedCall("Implosion (Howl)", "Shadow Side");

	@NpcCastCallout(0x56F3)
	private final ModifiableCallout<AbilityCastStart> implosionTail = ModifiableCallout.durationBasedCall("Implosion (Tail)", "Opposite Shadow");

	@NpcCastCallout(0x5717)
	private final ModifiableCallout<AbilityCastStart> throneOfShadow = ModifiableCallout.durationBasedCall("Throne Of Shadow", "Out");

	@NpcCastCallout(0x56EA)
	private final ModifiableCallout<AbilityCastStart> gigaSlashSingleLeft = ModifiableCallout.durationBasedCall("Giga Slash (Single, Left)", "Go Left of Shadow");

	@NpcCastCallout(0x56ED)
	private final ModifiableCallout<AbilityCastStart> gigaSlashSingleRight = ModifiableCallout.durationBasedCall("Giga Slash (Single, Right)", "Go Right of Shadow");

	@NpcCastCallout(0x56F4)
	private final ModifiableCallout<AbilityCastStart> gigaSlashQuadrupleLeft = ModifiableCallout.durationBasedCall("Giga Slash (Quadruple, Left)", "Go Left of Shadows");

	@NpcCastCallout(0x56F8)
	private final ModifiableCallout<AbilityCastStart> gigaSlashQuadrupleRight = ModifiableCallout.durationBasedCall("Giga Slash (Quadruple, Right)", "Go Right of Shadows");

	private final ModifiableCallout<AbilityCastStart> umbraSmashOnYou = ModifiableCallout.durationBasedCall("Umbra Smash (on You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> umbraSmashOther = ModifiableCallout.durationBasedCall("Umbra Smash (on Other)", "Buster on {event.target}");

	@HandleEvents
	public void umbraSmash(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x5BAA)) {
			return;
		}
		umbraTarget = event.getTarget();
		context.accept((event.getTarget().isThePlayer() ? umbraSmashOnYou : umbraSmashOther).getModified(event));
	}

	private final ModifiableCallout<AbilityCastStart> umbraSmashSwap = new ModifiableCallout<>("Umbra Smash (Tank Swap)", "Tank Swap");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> umbraSmashSwapSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x5BAA),
			(e1, s) -> {
				if (!state.playerJobMatches(Job::isTank) || e1.getTarget().isThePlayer()) {
					return;
				}
				// 4回攻撃の途中で挑発すると最後の1発を受けてしまうので、4発目が確定するまで待つ
				s.waitMs(8_500);
				s.updateCall(umbraSmashSwap, e1);
			});

	private final ModifiableCallout<AbilityCastStart> darknessUnleashedAvoid = ModifiableCallout.durationBasedCall("Darkness Unleashed (Umbra Smash Target)", "Avoid Stack");
	private final ModifiableCallout<AbilityCastStart> darknessUnleashedStack = ModifiableCallout.durationBasedCall("Darkness Unleashed", "Stack");

	@HandleEvents
	public void darknessUnleashed(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x5B0E)) {
			return;
		}
		boolean avoid = umbraTarget != null && umbraTarget.isThePlayer();
		context.accept((avoid ? darknessUnleashedAvoid : darknessUnleashedStack).getModified(event));
	}

	@NpcCastCallout(0x5B0C)
	private final ModifiableCallout<AbilityCastStart> shadowsEdge = ModifiableCallout.durationBasedCall("Shadow's Edge", "Tank Cleave");

	@HandleEvents
	public void cleaveDropDebuff(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x973, 0x974, 0x975, 0x976) && event.getTarget().isThePlayer()) {
			gigaSlashCleaveDebuffId = event.getBuff().getId();
		}
	}

	private final ModifiableCallout<AbilityCastStart> gigaSlashDropLeftCleave = ModifiableCallout.durationBasedCall("Giga Slash Shadow Drop (Left Cleave)", "Left Cleave");
	private final ModifiableCallout<AbilityCastStart> gigaSlashDropRightCleave = ModifiableCallout.durationBasedCall("Giga Slash Shadow Drop (Right Cleave)", "Right Cleave");
	private final ModifiableCallout<AbilityCastStart> gigaSlashDropShadow = ModifiableCallout.durationBasedCall("Giga Slash Shadow Drop (Direction)", "Drop Shadow {dir}");

	@HandleEvents
	public void gigaSlashDrop(EventContext context, AbilityCastStart event) {
		boolean leftCleave = event.abilityIdMatches(0x5B2D);
		if (!leftCleave && !event.abilityIdMatches(0x5B2C)) {
			return;
		}
		context.accept((leftCleave ? gigaSlashDropLeftCleave : gigaSlashDropRightCleave).getModified(event));
		// 影の向きはデバフで決まり、左右どちらの斬撃かで落とす方角が反転する
		String dir;
		if (gigaSlashCleaveDebuffId == 0x973) {
			dir = leftCleave ? "West" : "East";
		}
		else if (gigaSlashCleaveDebuffId == 0x974) {
			dir = leftCleave ? "East" : "West";
		}
		else if (gigaSlashCleaveDebuffId == 0x975) {
			dir = leftCleave ? "North" : "South";
		}
		else if (gigaSlashCleaveDebuffId == 0x976) {
			dir = leftCleave ? "South" : "North";
		}
		else {
			dir = null;
		}
		gigaSlashCleaveDebuffId = 0;
		if (dir != null) {
			context.accept(gigaSlashDropShadow.getModified(event, Map.of("dir", dir)));
		}
	}

	private final ModifiableCallout<BuffApplied> shadowServantGetIn = new ModifiableCallout<>("Shadow Servant", "In");
	private final RepeatSuppressor shadowServantSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> shadowServantSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0x9D6) && shadowServantSupp.check(ba),
			(e1, s) -> {
				// 効果は2秒なので、すぐではなく少し遅らせて入るよう促す
				s.waitMs(1_000);
				s.updateCall(shadowServantGetIn, e1);
			});

	private final ModifiableCallout<AbilityCastStart> shadowCleave = ModifiableCallout.durationBasedCall("Shadow Cleave", "Drop Shadow Out");

	@HandleEvents
	public void shadowCleave(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x5718)) {
			clones = true;
			context.accept(shadowCleave.getModified(event));
		}
	}

	// 影はプレイヤーと同じジョブを持つので、ジョブが一致する影を自分の影とみなす
	@HandleEvents
	public void shadowOfAHero(EventContext context, RawAddCombatantEvent event) {
		if (!clones || event.getEntity().isPc()) {
			return;
		}
		RawXivCombatantInfo info = event.getFullInfo();
		if (info == null || info.getJobId() == 0) {
			return;
		}
		shadowIds.add(event.getEntity().getId());
		Job playerJob = state.getPlayerJob();
		if (playerJob != null && info.getJobId() == playerJob.getId()) {
			myClone.add(event.getEntity().getId());
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> shadowMarker = new ModifiableCallout<>("Shadow Of A Hero Head Marker", "{num}", 7_000);
	private final ModifiableCallout<HeadMarkerEvent> shadowMarkerUnknown = new ModifiableCallout<>("Shadow Of A Hero Head Marker (Unknown)", "?", 7_000);
	private final RepeatSuppressor shadowMarkerMapSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void shadowMarkers(EventContext context, HeadMarkerEvent event) {
		long targetId = event.getTarget().getId();
		if (!shadowIds.contains(targetId)) {
			return;
		}
		// マーカーIDは毎回ずれるが連番なので、最初に見えたものを1番とする
		if (shadowMarkerMap == null && shadowMarkerMapSupp.check(event)) {
			shadowMarkerMap = new HashMap<>();
			for (int i = 0; i < SHADOW_MARKER_COUNT; i++) {
				shadowMarkerMap.put(event.getMarkerId() + i, i + 1);
			}
		}
		if (headMarkerTriggered) {
			return;
		}
		// 同じジョブが複数いたり、死亡して影のジョブが 0 になると判別できない
		if (myClone.size() != 1) {
			headMarkerTriggered = true;
			context.accept(shadowMarkerUnknown.getModified(event));
			return;
		}
		if (targetId != myClone.get(0)) {
			return;
		}
		headMarkerTriggered = true;
		Integer num = shadowMarkerMap == null ? null : shadowMarkerMap.get(event.getMarkerId());
		if (num == null) {
			context.accept(shadowMarkerUnknown.getModified(event));
		}
		else {
			context.accept(shadowMarker.getModified(event, Map.of("num", num)));
		}
	}

	@NpcCastCallout(0x573A)
	private final ModifiableCallout<AbilityCastStart> dualspell1 = ModifiableCallout.durationBasedCall("Dualspell 1", "1 Out, 2+3 In");

	private final ModifiableCallout<AbilityUsedEvent> dualspell2 = new ModifiableCallout<>("Dualspell 2", "2 Out, 1+3 In");
	private final ModifiableCallout<AbilityUsedEvent> dualspell3 = new ModifiableCallout<>("Dualspell 3", "3 Out, 1+2 In");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> dualspellSq = SqtTemplates.sq(10_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x573A) && aue.isFirstTarget(),
			(e1, s) -> {
				s.updateCall(dualspell2, e1);
				s.waitMs(3_000);
				s.updateCall(dualspell3, e1);
			});

	private final ModifiableCallout<AbilityCastStart> shadowkeeper = ModifiableCallout.durationBasedCall("Shadowkeeper", "Drop Shadow Out");

	@HandleEvents
	public void shadowkeeper(EventContext context, AbilityCastStart event) {
		// 最初の1回だけ影の設置が必要
		if (event.abilityIdMatches(0x5720) && !seenShadowkeeper) {
			seenShadowkeeper = true;
			clones = false;
			context.accept(shadowkeeper.getModified(event));
		}
	}

	@NpcCastCallout(value = 0x5BBF, suppressMs = 3_000)
	private final ModifiableCallout<AbilityCastStart> swathOfSilence = ModifiableCallout.durationBasedCall("Swath of Silence", "Move Away");

	@NpcCastCallout(0x5716)
	private final ModifiableCallout<AbilityCastStart> distantScream = ModifiableCallout.durationBasedCall("Distant Scream", "Knockback");

	private final ModifiableCallout<AbilityCastStart> umbralOrbs = new ModifiableCallout<>("Umbral Orbs", "Orbs");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> umbralOrbsSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x5731),
			(e1, s) -> {
				s.waitMs(3_500);
				s.updateCall(umbralOrbs, e1);
			});

	@NpcCastCallout(0x5739)
	private final ModifiableCallout<AbilityCastStart> shadowWarrior = ModifiableCallout.durationBasedCall("Shadow Warrior", "Watch Tethered Dog");

	private final ModifiableCallout<AbilityCastStart> fadeToShadow = new ModifiableCallout<>("Fade To Shadow", "Be On Squiggles");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> fadeToShadowSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x572B),
			(e1, s) -> {
				// 詠唱開始はかなり早いので、クロークオブシャドウに合わせて遅らせる
				s.waitMs(4_000);
				s.updateCall(fadeToShadow, e1);
			});

	// 5B13 と 5B14 は同時に詠唱されるが着弾がずれるので、先に来る 5B13 で離れるタイミングを計る
	@NpcAbilityUsedCallout(value = 0x5B13, suppressMs = 5_000)
	private final ModifiableCallout<AbilityUsedEvent> cloakOfShadows = new ModifiableCallout<>("Cloak Of Shadows", "Away From Squiggles");

	@NpcCastCallout(0x56FC)
	private final ModifiableCallout<AbilityCastStart> quadrupleImplosionHowl = ModifiableCallout.durationBasedCall("Quadruple Implosion (Howl)", "Shadow Side");

	@NpcCastCallout(0x5700)
	private final ModifiableCallout<AbilityCastStart> quadrupleImplosionTail = ModifiableCallout.durationBasedCall("Quadruple Implosion (Tail)", "Opposite Shadow");

	private final ModifiableCallout<AbilityCastStart> voidgate = ModifiableCallout.durationBasedCall("Voidgate", "Cleaves With Towers");
	private final ModifiableCallout<AbilityCastStart> voidgateSecondTower = new ModifiableCallout<>("Voidgate Second Tower", "Towers First, Then Cleaves");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> voidgateSq = SqtTemplates.sq(30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x5734),
			(e1, s) -> {
				s.call(voidgate, e1);
				s.waitMs(23_300);
				s.call(voidgateSecondTower, e1);
			});

	private final ModifiableCallout<AbilityCastStart> pitchBogFirst = ModifiableCallout.durationBasedCall("Pitch Bog (First)", "Puddles Outside");
	private final ModifiableCallout<AbilityCastStart> pitchBogSecond = ModifiableCallout.durationBasedCall("Pitch Bog (Second)", "Final Puddle Positions");

	@HandleEvents
	public void pitchBog(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x5721)) {
			context.accept((seenPitchBog ? pitchBogSecond : pitchBogFirst).getModified(event));
			seenPitchBog = true;
		}
	}

	private final ModifiableCallout<TetherEvent> shackledApart = new ModifiableCallout<>("Shackled Apart", "Far Tethers with {partner}");
	private final ModifiableCallout<TetherEvent> shackledTogether = new ModifiableCallout<>("Shackled Together", "Close Tethers with {partner}");

	@HandleEvents
	public void shackles(EventContext context, TetherEvent event) {
		boolean apart = event.tetherIdMatches(0x82);
		if (!apart && !event.tetherIdMatches(0x81)) {
			return;
		}
		if (!event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			return;
		}
		XivCombatant partner = event.getSource().isThePlayer() ? event.getTarget() : event.getSource();
		context.accept((apart ? shackledApart : shackledTogether).getModified(event, Map.of("partner", partner)));
	}

	@NpcCastCallout(0x5BCF)
	private final ModifiableCallout<AbilityCastStart> voidgateAmplifier = ModifiableCallout.durationBasedCall("Voidgate Amplifier", "Pick Up Puddles");

}
