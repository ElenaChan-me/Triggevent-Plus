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
import gg.xp.xivsupport.events.actlines.events.BuffRemoved;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/raid/a8s.ts をもとに移植
@CalloutRepo(name = "Alexander The Burden Of The Son Savage", duty = KnownDuty.AlexanderTheBurdenOfTheSonSavage)
public class A8s extends AutoChildEventHandler implements FilteredEventHandler {

	private static final Duration DEBUFF_SOON = Duration.ofSeconds(5);

	private XivState state;

	private boolean seenLinkUp;
	private XivCombatant longNeedleStack;
	private final List<XivCombatant> longNeedlePrey = new ArrayList<>();
	private XivCombatant verdictMin;
	private XivCombatant verdictMax;

	public A8s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AlexanderTheBurdenOfTheSonSavage);
	}

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		seenLinkUp = false;
		longNeedleStack = null;
		longNeedlePrey.clear();
		verdictMin = null;
		verdictMax = null;
	}

	// オンスローター

	@NpcCastCallout(0x162E)
	private final ModifiableCallout<AbilityCastStart> megabeamOnslaughter = ModifiableCallout.durationBasedCall("Megabeam (Onslaughter)", "Megabeam");

	private final ModifiableCallout<AbilityUsedEvent> execution = new ModifiableCallout<>("Execution", "Kill Regulators");

	@HandleEvents
	public void execution(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x1632) && event.isFirstTarget() && !event.getSource().isPc()
		    && state.playerJobMatches(job -> job.isDps() || job == Job.BLU)) {
			context.accept(execution.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> perpetualRayOnYou = ModifiableCallout.durationBasedCall("Perpetual Ray (on You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> perpetualRaySwap = ModifiableCallout.durationBasedCall("Perpetual Ray (Tank Swap)", "Tank Swap");
	private final ModifiableCallout<AbilityCastStart> perpetualRayOther = ModifiableCallout.durationBasedCall("Perpetual Ray (on Other)", "Buster on {event.target}");

	private final ModifiableCallout<HeadMarkerEvent> discoid = new ModifiableCallout<>("Discoid", "Orb on You");

	// ヴァーディクトでも同じマーカーが使われるので、合体前だけに絞る
	@HandleEvents
	public void discoid(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x23) && event.getTarget().isThePlayer() && !seenLinkUp) {
			context.accept(discoid.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> mindBlast = ModifiableCallout.durationBasedCall("Mind Blast", "Interrupt {event.source}");

	@HandleEvents
	public void mindBlast(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x1639) && !event.getSource().isPc() && state.playerJobMatches(Job::caresAboutInterrupt)) {
			context.accept(mindBlast.getModified(event));
		}
	}

	// ロー/ハイアリスマティックはどちらも同じヘッドマーカーなのでデバフで見分ける
	@PlayerStatusCallout(0x3FD)
	private final ModifiableCallout<BuffApplied> lowArithmeticks = ModifiableCallout.durationBasedCall("Low Arithmeticks", "Get High");

	@PlayerStatusCallout(0x3FE)
	private final ModifiableCallout<BuffApplied> highArithmeticks = ModifiableCallout.durationBasedCall("High Arithmeticks", "Get Down");

	@NpcCastCallout(0x164A)
	private final ModifiableCallout<AbilityCastStart> bioArithmeticks = ModifiableCallout.durationBasedCall("Bio-Arithmeticks", "Raidwide");

	@NpcCastCallout(0x1657)
	private final ModifiableCallout<AbilityCastStart> superCyclone = ModifiableCallout.durationBasedCall("Super Cyclone", "Knockback");

	private final ModifiableCallout<BuffApplied> lightningOnYou = ModifiableCallout.<BuffApplied>durationBasedCall("Compressed Lightning (on You)", "Thunder on You").statusIcon(0x400);
	private final ModifiableCallout<BuffApplied> lightningOther = ModifiableCallout.<BuffApplied>durationBasedCall("Compressed Lightning (on Other)", "Thunder on {event.target}").statusIcon(0x400);
	private final ModifiableCallout<BuffApplied> lightningSoon = ModifiableCallout.<BuffApplied>durationBasedCall("Compressed Lightning Soon", "Thunder Soon on {event.target}").statusIcon(0x400);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> lightningSoonSq = SqtTemplates.callWhenDurationIs(
			BuffApplied.class, ba -> ba.buffIdMatches(0x400) && !ba.isRefresh(),
			lightningSoon, DEBUFF_SOON);

	private final ModifiableCallout<BuffApplied> waterOnYou = ModifiableCallout.<BuffApplied>durationBasedCall("Compressed Water (on You)", "Water on You").statusIcon(0x3FF);
	private final ModifiableCallout<BuffApplied> waterOther = ModifiableCallout.<BuffApplied>durationBasedCall("Compressed Water (on Other)", "Water on {event.target}").statusIcon(0x3FF);
	private final ModifiableCallout<BuffApplied> waterSoon = ModifiableCallout.<BuffApplied>durationBasedCall("Compressed Water Soon", "Water Soon on {event.target}").statusIcon(0x3FF);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> waterSoonSq = SqtTemplates.callWhenDurationIs(
			BuffApplied.class, ba -> ba.buffIdMatches(0x3FF) && !ba.isRefresh(),
			waterSoon, DEBUFF_SOON);

	@HandleEvents
	public void compressedDebuffs(EventContext context, BuffApplied event) {
		if (event.isRefresh()) {
			return;
		}
		boolean onYou = event.getTarget().isThePlayer();
		if (event.buffIdMatches(0x400)) {
			context.accept((onYou ? lightningOnYou : lightningOther).getModified(event));
		}
		else if (event.buffIdMatches(0x3FF)) {
			context.accept((onYou ? waterOnYou : waterOther).getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> enumeration = new ModifiableCallout<>("Enumeration", "{event.target}: {count}");

	@HandleEvents
	public void enumeration(EventContext context, HeadMarkerEvent event) {
		// 0040 が2人、0041 が3人、0042 が4人
		if (event.markerIdMatches(0x40, 0x41, 0x42)) {
			long count = 2 + event.getMarkerId() - 0x40;
			context.accept(enumeration.getModified(event, Map.of("count", count)));
		}
	}

	// ブルートジャスティス

	@NpcCastCallout(0x1664)
	private final ModifiableCallout<AbilityCastStart> megabeamBruteJustice = ModifiableCallout.durationBasedCall("Megabeam (Brute Justice)", "Megabeam");

	private final ModifiableCallout<AbilityCastStart> doubleRocketPunchOnYou = ModifiableCallout.durationBasedCall("Double Rocket Punch (on You)", "Shared Buster on You");
	private final ModifiableCallout<AbilityCastStart> doubleRocketPunchOther = ModifiableCallout.durationBasedCall("Double Rocket Punch (on Other)", "Shared Buster on {event.target}");

	@HandleEvents
	public void doubleRocketPunch(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x1663) || event.getSource().isPc()) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(doubleRocketPunchOnYou.getModified(event));
		}
		else if (state.playerJobMatches(Job::isSupport)) {
			context.accept(doubleRocketPunchOther.getModified(event));
		}
	}

	@NpcAbilityUsedCallout(value = 0x1668, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> shortNeedle = new ModifiableCallout<>("Short Needle", "Raidwide");

	@HandleEvents
	public void longNeedleMarkers(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E)) {
			longNeedleStack = event.getTarget();
		}
		else if (event.markerIdMatches(0x1E)) {
			longNeedlePrey.add(event.getTarget());
		}
	}

	private final ModifiableCallout<AbilityCastStart> longNeedlePreyCall = ModifiableCallout.durationBasedCall("Long Needle (Prey)", "Prey: Get Out");
	private final ModifiableCallout<AbilityCastStart> longNeedleStackOnYou = ModifiableCallout.durationBasedCall("Long Needle (Stack on You)", "Stack on You");
	private final ModifiableCallout<AbilityCastStart> longNeedleStackOther = ModifiableCallout.durationBasedCall("Long Needle (Stack on Other)", "Stack on {target}");

	private final RepeatSuppressor longNeedleSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	@HandleEvents
	public void longNeedle(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x166A) || event.getSource().isPc()) {
			return;
		}
		if (longNeedleStack == null || longNeedlePrey.isEmpty() || !longNeedleSupp.check(event)) {
			return;
		}
		if (longNeedlePrey.stream().anyMatch(XivCombatant::isThePlayer)) {
			context.accept(longNeedlePreyCall.getModified(event));
		}
		else if (longNeedleStack.isThePlayer()) {
			context.accept(longNeedleStackOnYou.getModified(event));
		}
		else {
			context.accept(longNeedleStackOther.getModified(event, Map.of("target", longNeedleStack)));
		}
		longNeedleStack = null;
		longNeedlePrey.clear();
	}

	private final ModifiableCallout<AbilityCastStart> superJumpOnYou = ModifiableCallout.durationBasedCall("Super Jump (on You)", "Super Jump on You");
	private final ModifiableCallout<AbilityCastStart> superJumpOther = ModifiableCallout.durationBasedCall("Super Jump (on Other)", "Super Jump on {event.target}");

	@HandleEvents
	public void superJump(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x1665) && !event.getSource().isPc()) {
			context.accept((event.getTarget().isThePlayer() ? superJumpOnYou : superJumpOther).getModified(event));
		}
	}

	@PlayerHeadmarker(0x08)
	private final ModifiableCallout<HeadMarkerEvent> mirageMarker = new ModifiableCallout<>("Mirage Marker", "Mirage on You");

	@PlayerHeadmarker(0x43)
	private final ModifiableCallout<HeadMarkerEvent> iceMissile = new ModifiableCallout<>("Ice Missile", "Ice Missile on You");

	private final ModifiableCallout<AbilityUsedEvent> hiddenMinefield = new ModifiableCallout<>("Hidden Minefield (Intermission)", "Get Mines");

	private final RepeatSuppressor hiddenMinefieldSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	@NpcCastCallout(value = 0x165A, onYou = true)
	private final ModifiableCallout<AbilityCastStart> mirageBlinder = ModifiableCallout.durationBasedCall("Mirage Blinder", "Look Away from Mirage");

	@NpcCastCallout(value = 0x165B, onYou = true)
	private final ModifiableCallout<AbilityCastStart> miragePowerTackle = ModifiableCallout.durationBasedCall("Mirage Power Tackle", "Look Towards Mirage");

	@HandleEvents
	public void abilityUsed(EventContext context, AbilityUsedEvent event) {
		if (event.getSource().isPc()) {
			return;
		}
		if (event.abilityIdMatches(0x1673)) {
			seenLinkUp = true;
		}
		// 165E は最終フェーズでも使われるので、合体前だけに絞る
		else if (event.abilityIdMatches(0x165E) && !seenLinkUp && hiddenMinefieldSupp.check(event)) {
			context.accept(hiddenMinefield.getModified(event));
		}
	}

	// ヴァーディクト

	private final ModifiableCallout<BuffApplied> verdictMinHp = new ModifiableCallout<>("Verdict: Min HP", "Min HP: Provoke Boss, then Late NE Tornado", 8_000);
	private final ModifiableCallout<BuffApplied> verdictMinHpTornado = new ModifiableCallout<>("Verdict: Min HP Tornado", "Get NE Tornado");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> verdictMinHpSq = SqtTemplates.sq(35_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0x408) && ba.getTarget().isThePlayer(),
			(e1, s) -> {
				s.updateCall(verdictMinHp, e1);
				s.waitMs(27_000);
				s.updateCall(verdictMinHpTornado, e1);
			});

	private final ModifiableCallout<BuffApplied> verdictMaxHp = new ModifiableCallout<>("Verdict: Max HP", "Max HP: Provoke Boss Second");
	private final ModifiableCallout<BuffApplied> verdictMaxHpDevour = new ModifiableCallout<>("Verdict: Max HP Devour (BLU)", "Use Devour");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> verdictMaxHpSq = SqtTemplates.sq(35_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0x407) && ba.getTarget().isThePlayer(),
			(e1, s) -> {
				s.updateCall(verdictMaxHp, e1);
				if (state.playerJobMatches(job -> job == Job.BLU)) {
					s.waitMs(27_000);
					s.updateCall(verdictMaxHpDevour, e1);
				}
			});

	@HandleEvents
	public void verdictCollect(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x408)) {
			verdictMin = event.getTarget();
		}
		else if (event.buffIdMatches(0x407)) {
			verdictMax = event.getTarget();
		}
	}

	private final ModifiableCallout<BuffRemoved> verdictMaxHpProvoke = new ModifiableCallout<>("Verdict: Max HP Provoke", "Provoke Boss");

	// オートアタックで最小HP担当のファイナルパニッシュメントが消えたら挑発の番
	@HandleEvents
	public void verdictMaxHpProvoke(EventContext context, BuffRemoved event) {
		if (event.buffIdMatches(0x403)
		    && verdictMin != null && verdictMax != null
		    && event.getTarget().getId() == verdictMin.getId() && verdictMax.isThePlayer()) {
			context.accept(verdictMaxHpProvoke.getModified(event));
		}
	}

	@PlayerStatusCallout(0x409)
	private final ModifiableCallout<BuffApplied> verdictPenalty1 = new ModifiableCallout<>("Verdict: Penalty 1", "Penalty 1: SW Orb, then Share 2x North Orbs", 10_000);

	@PlayerStatusCallout(0x40A)
	private final ModifiableCallout<BuffApplied> verdictPenalty2 = new ModifiableCallout<>("Verdict: Penalty 2", "Penalty 2: NW Tornado");

	private final ModifiableCallout<BuffApplied> verdictPenalty3 = new ModifiableCallout<>("Verdict: Penalty 3", "Penalty 3: South Tornado");
	private final ModifiableCallout<BuffApplied> verdictPenalty3Orb = new ModifiableCallout<>("Verdict: Penalty 3 Orb", "Share Last Orb after Gavel");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> verdictPenalty3Sq = SqtTemplates.sq(35_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0x40B) && ba.getTarget().isThePlayer(),
			(e1, s) -> {
				s.updateCall(verdictPenalty3, e1);
				s.waitMs(28_000);
				s.updateCall(verdictPenalty3Orb, e1);
			});

	@PlayerStatusCallout(0x40C)
	private final ModifiableCallout<BuffApplied> verdictNisiA = new ModifiableCallout<>("Verdict: Nisi A", "Blue Regulator, then Share North Orb");

	@PlayerStatusCallout(0x40D)
	private final ModifiableCallout<BuffApplied> verdictNisiB = new ModifiableCallout<>("Verdict: Nisi B", "Red Regulator, then Share North Orb");

	private final ModifiableCallout<AbilityCastStart> finalPunchOnYou = ModifiableCallout.durationBasedCall("Final Punch (on You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> finalPunchSwap = ModifiableCallout.durationBasedCall("Final Punch (Tank Swap)", "Tank Swap");
	private final ModifiableCallout<AbilityCastStart> finalPunchOther = ModifiableCallout.durationBasedCall("Final Punch (on Other)", "Buster on {event.target}");

	@HandleEvents
	public void tankBusterSwaps(EventContext context, AbilityCastStart event) {
		if (event.getSource().isPc()) {
			return;
		}
		if (event.abilityIdMatches(0x162B)) {
			tankBusterSwap(context, event, perpetualRayOnYou, perpetualRaySwap, perpetualRayOther);
		}
		else if (event.abilityIdMatches(0x170C)) {
			tankBusterSwap(context, event, finalPunchOnYou, finalPunchSwap, finalPunchOther);
		}
	}

	// 対象のタンク以外のタンクにはスイッチを促す
	private void tankBusterSwap(EventContext context, AbilityCastStart event,
	                            ModifiableCallout<AbilityCastStart> onYou,
	                            ModifiableCallout<AbilityCastStart> swap,
	                            ModifiableCallout<AbilityCastStart> other) {
		if (event.getTarget().isThePlayer()) {
			context.accept(onYou.getModified(event));
		}
		else if (state.playerJobMatches(Job::isTank)) {
			context.accept(swap.getModified(event));
		}
		else {
			context.accept(other.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> finalBeam = new ModifiableCallout<>("Final Beam", "Stack for Final Beam");

	// ファイナルアポカリプスの着弾を合図にする
	@HandleEvents
	public void finalBeam(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x1716) && event.isFirstTarget() && !event.getSource().isPc()) {
			context.accept(finalBeam.getModified(event));
		}
	}

}
