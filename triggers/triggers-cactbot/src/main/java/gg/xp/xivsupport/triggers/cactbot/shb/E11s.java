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
import gg.xp.xivsupport.callouts.RawModifiedCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/raid/e11s.ts をもとに移植
@CalloutRepo(name = "Edens Promise Anamorphosis Savage", duty = KnownDuty.EdensPromiseAnamorphosisSavage)
public class E11s extends AutoChildEventHandler implements FilteredEventHandler {

	// 次の組の線と混ざらないよう、これより古い線は捨てる
	private static final Duration TETHER_TIMEOUT = Duration.ofSeconds(20);
	private static final Duration CYCLE_DURATION = Duration.ofSeconds(12);
	private static final long CYCLE_TETHER_DELAY_MS = 16_500;

	private XivState state;

	public E11s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.EdensPromiseAnamorphosisSavage);
	}

	// 線の対象プレイヤー → 線元のID（付いた順）
	private final Map<XivCombatant, Long> tethers = new LinkedHashMap<>();
	private TetherEvent lastTether;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		tethers.clear();
		lastTether = null;
	}

	private List<XivCombatant> tetherTargets() {
		if (lastTether == null || lastTether.getEffectiveTimeSince().compareTo(TETHER_TIMEOUT) > 0) {
			return List.of();
		}
		return new ArrayList<>(tethers.keySet());
	}

	@HandleEvents
	public void tetherCollector(EventContext context, TetherEvent event) {
		if (!event.tetherIdMatches(0x11)) {
			return;
		}
		XivCombatant target = event.getTargetMatching(XivCombatant::isPc);
		XivCombatant source = event.getTargetMatching(cbt -> !cbt.isPc());
		if (target == null || source == null) {
			return;
		}
		if (lastTether != null && lastTether.getEffectiveTimeSince().compareTo(TETHER_TIMEOUT) > 0) {
			tethers.clear();
		}
		lastTether = event;
		tethers.put(target, source.getId());
	}

	@NpcCastCallout(0x5663)
	private final ModifiableCallout<AbilityCastStart> elementalBreakFire = ModifiableCallout.durationBasedCall("Elemental Break (Fire)", "Protean -> Partner Stacks");

	@NpcCastCallout(0x5666)
	private final ModifiableCallout<AbilityCastStart> elementalBreakLightning = ModifiableCallout.durationBasedCall("Elemental Break (Lightning)", "Protean -> Spread");

	@NpcCastCallout(0x5668)
	private final ModifiableCallout<AbilityCastStart> elementalBreakHoly = ModifiableCallout.durationBasedCall("Elemental Break (Holy)", "Protean -> Holy Groups");

	@NpcCastCallout(0x5652)
	private final ModifiableCallout<AbilityCastStart> burntStrikeFire = ModifiableCallout.durationBasedCall("Burnt Strike (Fire)", "Line Cleave -> Knockback");

	@NpcCastCallout(0x5654)
	private final ModifiableCallout<AbilityCastStart> burntStrikeLightning = ModifiableCallout.durationBasedCall("Burnt Strike (Lightning)", "Line Cleave -> Out");

	@NpcCastCallout(0x5656)
	private final ModifiableCallout<AbilityCastStart> burntStrikeHoly = ModifiableCallout.durationBasedCall("Burnt Strike (Holy)", "Line Cleave + Bait");

	// 炎・雷・光の線の処理は、本体の詠唱とサイクル・オブ・フェイスの後で共通

	private final ModifiableCallout<AbilityCastStart> fireTetherOnYou = ModifiableCallout.durationBasedCall("Bound Of Faith (Fire, on You)", "Stack on You");
	private final ModifiableCallout<AbilityCastStart> fireTetherOnOther = ModifiableCallout.durationBasedCall("Bound Of Faith (Fire)", "Stack on {target}");
	private final ModifiableCallout<AbilityCastStart> fireTetherUnknown = ModifiableCallout.durationBasedCall("Bound Of Faith (Fire, Unknown)", "Stack");

	private final ModifiableCallout<AbilityCastStart> lightningTetherOnYou = ModifiableCallout.durationBasedCall("Bound Of Faith (Lightning, on You)", "Take Lightning to Tanks");
	private final ModifiableCallout<AbilityCastStart> lightningTetherOnOther = ModifiableCallout.durationBasedCall("Bound Of Faith (Lightning)", "Lightning on {target}");
	private final ModifiableCallout<AbilityCastStart> lightningTetherUnknown = ModifiableCallout.durationBasedCall("Bound Of Faith (Lightning, Unknown)", "Lightning Tether");

	private final ModifiableCallout<AbilityCastStart> holyTetherOnYou = ModifiableCallout.durationBasedCall("Bound Of Faith (Holy, on You)", "Away From Group");
	private final ModifiableCallout<AbilityCastStart> holyTetherOnOther = ModifiableCallout.durationBasedCall("Bound Of Faith (Holy)", "Away From {target}");
	private final ModifiableCallout<AbilityCastStart> holyTetherUnknown = ModifiableCallout.durationBasedCall("Bound Of Faith (Holy, Unknown)", "Away From Holy Tether");

	// サイクル・オブ・フェイス後のものは詠唱終了後に鳴るので、詠唱時間ではなく固定時間で消す
	private final ModifiableCallout<AbilityCastStart> cycleFireTetherOnYou = new ModifiableCallout<>("Cycle of Faith Tether (Fire, on You)", "Stack on You");
	private final ModifiableCallout<AbilityCastStart> cycleFireTetherOnOther = new ModifiableCallout<>("Cycle of Faith Tether (Fire)", "Stack on {target}");
	private final ModifiableCallout<AbilityCastStart> cycleFireTetherUnknown = new ModifiableCallout<>("Cycle of Faith Tether (Fire, Unknown)", "Stack");

	private final ModifiableCallout<AbilityCastStart> cycleLightningTetherOnYou = new ModifiableCallout<>("Cycle of Faith Tether (Lightning, on You)", "Take Lightning to Tanks");
	private final ModifiableCallout<AbilityCastStart> cycleLightningTetherOnOther = new ModifiableCallout<>("Cycle of Faith Tether (Lightning)", "Lightning on {target}");
	private final ModifiableCallout<AbilityCastStart> cycleLightningTetherUnknown = new ModifiableCallout<>("Cycle of Faith Tether (Lightning, Unknown)", "Lightning Tether");

	private final ModifiableCallout<AbilityCastStart> cycleHolyTetherOnYou = new ModifiableCallout<>("Cycle of Faith Tether (Holy, on You)", "Away From Group");
	private final ModifiableCallout<AbilityCastStart> cycleHolyTetherOnOther = new ModifiableCallout<>("Cycle of Faith Tether (Holy)", "Away From {target}");
	private final ModifiableCallout<AbilityCastStart> cycleHolyTetherUnknown = new ModifiableCallout<>("Cycle of Faith Tether (Holy, Unknown)", "Away From Holy Tether");

	// 炎と光は最初の線の対象を、雷は対象が1人に決まるときだけ名前を出す
	private RawModifiedCallout<AbilityCastStart> tetherCall(ModifiableCallout<AbilityCastStart> onYou, ModifiableCallout<AbilityCastStart> onOther, ModifiableCallout<AbilityCastStart> unknown, boolean requireSingleTarget, AbilityCastStart event) {
		List<XivCombatant> targets = tetherTargets();
		if (targets.stream().anyMatch(XivCombatant::isThePlayer)) {
			return onYou.getModified(event);
		}
		if (targets.isEmpty() || (requireSingleTarget && targets.size() != 1)) {
			return unknown.getModified(event);
		}
		return onOther.getModified(event, Map.of("target", targets.get(0)));
	}

	@HandleEvents
	public void boundOfFaith(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x5658)) {
			context.accept(tetherCall(fireTetherOnYou, fireTetherOnOther, fireTetherUnknown, false, event));
		}
		else if (event.abilityIdMatches(0x565B)) {
			context.accept(tetherCall(lightningTetherOnYou, lightningTetherOnOther, lightningTetherUnknown, true, event));
		}
		else if (event.abilityIdMatches(0x565F)) {
			context.accept(tetherCall(holyTetherOnYou, holyTetherOnOther, holyTetherUnknown, false, event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> shiftingSkyInfo = ModifiableCallout.durationBasedCall("Bound Of Faith (Shifting Sky)", "Lightning on {lightning}, Fire on {fire}");
	private final ModifiableCallout<AbilityCastStart> shiftingSkyLightningOnYou = ModifiableCallout.durationBasedCall("Bound Of Faith (Shifting Sky, Lightning on You)", "Take Lightning to Tanks");
	private final ModifiableCallout<AbilityCastStart> shiftingSkyFireOnYou = ModifiableCallout.durationBasedCall("Bound Of Faith (Shifting Sky, Fire on You)", "Stack With Fire Tether");
	private final ModifiableCallout<AbilityCastStart> sunderedSkyInfo = ModifiableCallout.durationBasedCall("Bound Of Faith (Sundered Sky)", "Holy on {holy}, Fire on {fire}");
	private final ModifiableCallout<AbilityCastStart> sunderedSkyHolyOnYou = ModifiableCallout.durationBasedCall("Bound Of Faith (Sundered Sky, Holy on You)", "Away From Group");
	private final ModifiableCallout<AbilityCastStart> sunderedSkyFireOnYou = ModifiableCallout.durationBasedCall("Bound Of Faith (Sundered Sky, Fire on You)", "Stack With Fire Tether");

	// シフティングスカイとサンダードスカイの後だけ、分身が炎と雷/光の2本の線を同時に出す
	@HandleEvents
	public void imageBoundOfFaith(EventContext context, AbilityCastStart event) {
		boolean shifting = event.abilityIdMatches(0x5682);
		if (!shifting && !event.abilityIdMatches(0x5BC5)) {
			return;
		}
		List<XivCombatant> targets = tetherTargets();
		if (targets.size() != 2) {
			return;
		}
		XivCombatant first = targets.get(0);
		XivCombatant second = targets.get(1);
		long sourceId = event.getSource().getId();
		XivCombatant otherTarget;
		XivCombatant fireTarget;
		// 詠唱した分身から伸びている線が雷/光、もう片方が炎
		if (tethers.get(first) == sourceId) {
			otherTarget = first;
			fireTarget = second;
		}
		else if (tethers.get(second) == sourceId) {
			otherTarget = second;
			fireTarget = first;
		}
		else {
			return;
		}
		if (shifting) {
			context.accept(shiftingSkyInfo.getModified(event, Map.of("lightning", otherTarget, "fire", fireTarget)));
			if (otherTarget.isThePlayer()) {
				context.accept(shiftingSkyLightningOnYou.getModified(event));
			}
		}
		else {
			context.accept(sunderedSkyInfo.getModified(event, Map.of("holy", otherTarget, "fire", fireTarget)));
			if (otherTarget.isThePlayer()) {
				context.accept(sunderedSkyHolyOnYou.getModified(event));
			}
		}
		if (fireTarget.isThePlayer()) {
			context.accept((shifting ? shiftingSkyFireOnYou : sunderedSkyFireOnYou).getModified(event));
		}
	}

	@NpcCastCallout(0x56A4)
	private final ModifiableCallout<AbilityCastStart> burnishedGlory = ModifiableCallout.durationBasedCall("Burnished Glory", "Raidwide + Bleed");

	private final ModifiableCallout<AbilityCastStart> powderMarkOnYou = ModifiableCallout.durationBasedCall("Powder Mark (on You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> powderMarkSwap = ModifiableCallout.durationBasedCall("Powder Mark (Tank Swap)", "Tank Swap");
	private final ModifiableCallout<AbilityCastStart> powderMarkOther = ModifiableCallout.durationBasedCall("Powder Mark (on Other)", "Buster on {event.target}");

	@HandleEvents
	public void powderMark(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x56A2)) {
			return;
		}
		ModifiableCallout<AbilityCastStart> call;
		if (event.getTarget().isThePlayer()) {
			call = powderMarkOnYou;
		}
		else if (state.playerJobMatches(Job::isTank)) {
			call = powderMarkSwap;
		}
		else {
			call = powderMarkOther;
		}
		context.accept(call.getModified(event));
	}

	private final ModifiableCallout<BuffApplied> powderMarkExplosion = ModifiableCallout.<BuffApplied>durationBasedCall("Powder Mark Explosion", "Away From Group").autoIcon();

	@AutoFeed
	private final SequentialTrigger<BaseEvent> powderMarkExplosionSq = SqtTemplates.callWhenDurationIs(
			BuffApplied.class, ba -> ba.buffIdMatches(0x993) && ba.getTarget().isThePlayer(),
			powderMarkExplosion, Duration.ofSeconds(4));

	@NpcCastCallout(0x566A)
	private final ModifiableCallout<AbilityCastStart> turnOfTheHeavensFire = new ModifiableCallout<>("Turn of the Heavens (Fire)", "Fire: Go to Blue", 10_000);

	@NpcCastCallout(0x566B)
	private final ModifiableCallout<AbilityCastStart> turnOfTheHeavensLightning = new ModifiableCallout<>("Turn of the Heavens (Lightning)", "Lightning: Go to Red", 10_000);

	@NpcCastCallout(0x5675)
	private final ModifiableCallout<AbilityCastStart> shiftingSkyFire = new ModifiableCallout<>("Shifting Sky (Fire)", "Fire: Go to Blue", 17_000);

	@NpcCastCallout(0x5676)
	private final ModifiableCallout<AbilityCastStart> shiftingSkyLightning = new ModifiableCallout<>("Shifting Sky (Lightning)", "Lightning: Go to Red", 17_000);

	@NpcCastCallout(0x566E)
	private final ModifiableCallout<AbilityCastStart> rightOfTheHeavensFire = new ModifiableCallout<>("Right Of The Heavens (Fire)", "Fire: Go to Blue", 10_000);

	@NpcCastCallout(0x566F)
	private final ModifiableCallout<AbilityCastStart> rightOfTheHeavensLightning = new ModifiableCallout<>("Right Of The Heavens (Lightning)", "Lightning: Go to Red", 10_000);

	@NpcCastCallout(0x5677)
	private final ModifiableCallout<AbilityCastStart> sunderedSkyFire = new ModifiableCallout<>("Sundered Sky (Fire)", "Fire: Knockback to Red -> Go Blue", 16_000);

	@NpcCastCallout(0x5678)
	private final ModifiableCallout<AbilityCastStart> sunderedSkyLightning = new ModifiableCallout<>("Sundered Sky (Lightning)", "Lightning: Knockback to Blue -> Go Red", 16_000);

	private final ModifiableCallout<AbilityCastStart> cycleOfFaithFire = new ModifiableCallout<>("Cycle of Faith (Fire)", "Fire Cycle",
			"Protean -> Partner Stacks -> Line Cleave -> Knockback -> Stack", ModifiableCallout.expiresIn(CYCLE_DURATION));
	private final ModifiableCallout<AbilityCastStart> cycleOfFaithLightning = new ModifiableCallout<>("Cycle of Faith (Lightning)", "Lightning Cycle",
			"Protean -> Spread -> Line Cleave -> Out -> Tank Cleaves", ModifiableCallout.expiresIn(CYCLE_DURATION));
	private final ModifiableCallout<AbilityCastStart> cycleOfFaithHoly = new ModifiableCallout<>("Cycle of Faith (Holy)", "Holy Cycle",
			"Protean -> Holy Groups -> Line Cleave -> Bait -> Away", ModifiableCallout.expiresIn(CYCLE_DURATION));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> cycleOfFaithSq = SqtTemplates.sq(30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x568A, 0x5692, 0x569A),
			(e1, s) -> {
				if (e1.abilityIdMatches(0x568A)) {
					s.call(cycleOfFaithFire, e1);
				}
				else if (e1.abilityIdMatches(0x5692)) {
					s.call(cycleOfFaithLightning, e1);
				}
				else {
					s.call(cycleOfFaithHoly, e1);
				}
				// 最後の線の処理は詠唱開始からかなり後なので、線が出揃ってから改めてコールする
				s.waitMs(CYCLE_TETHER_DELAY_MS);
				if (e1.abilityIdMatches(0x568A)) {
					s.accept(tetherCall(cycleFireTetherOnYou, cycleFireTetherOnOther, cycleFireTetherUnknown, false, e1));
				}
				else if (e1.abilityIdMatches(0x5692)) {
					s.accept(tetherCall(cycleLightningTetherOnYou, cycleLightningTetherOnOther, cycleLightningTetherUnknown, true, e1));
				}
				else {
					s.accept(tetherCall(cycleHolyTetherOnYou, cycleHolyTetherOnOther, cycleHolyTetherUnknown, false, e1));
				}
			});

}
