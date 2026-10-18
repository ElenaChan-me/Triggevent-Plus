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
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/raid/e7s.ts をもとに移植
@CalloutRepo(name = "Edens Verse Iconoclasm Savage", duty = KnownDuty.EdensVerseIconoclasmSavage)
public class E7s extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long STACK_MARKER = 0x64;
	private static final long SPREAD_MARKER = 0x65;
	// 次の組の詠唱と混ざらないよう、これより古い記録は捨てる
	private static final Duration BOUNDLESS_TIMEOUT = Duration.ofSeconds(20);

	private XivState state;

	public E7s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.EdensVerseIconoclasmSavage);
	}

	private enum Phase {
		NONE,
		BETWIXT_WORLDS,
		FALSE_MIDNIGHT,
		ADDS
	}

	private enum Color {
		NONE,
		LIGHT,
		DARK
	}

	private Phase phase = Phase.NONE;
	private Color color = Color.NONE;
	private final List<XivCombatant> betwixtWorldsTethers = new ArrayList<>();
	private final List<XivCombatant> betwixtWorldsStack = new ArrayList<>();
	private final List<XivCombatant> falseMidnightSpread = new ArrayList<>();
	private final List<XivCombatant> insatiableLightStack = new ArrayList<>();
	private XivCombatant boundlessLight;
	private XivCombatant boundlessDark;
	private AbilityCastStart lastBoundless;
	private HeadMarkerEvent silverSledgeMarker;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		phase = Phase.NONE;
		color = Color.NONE;
		betwixtWorldsTethers.clear();
		betwixtWorldsStack.clear();
		falseMidnightSpread.clear();
		insatiableLightStack.clear();
		silverSledgeMarker = null;
		clearBoundless();
	}

	private void clearBoundless() {
		boundlessLight = null;
		boundlessDark = null;
		lastBoundless = null;
	}

	private static String names(List<XivCombatant> players) {
		return players.stream().map(XivCombatant::getName).sorted().collect(Collectors.joining(", "));
	}

	@NpcCastCallout(0x4C8A)
	private final ModifiableCallout<AbilityCastStart> emptyWave = ModifiableCallout.durationBasedCall("Empty Wave", "Raidwide");

	private final ModifiableCallout<TetherEvent> unshadowedStakeOnYou = new ModifiableCallout<>("Unshadowed Stake (on You)", "Buster on You");
	private final ModifiableCallout<TetherEvent> unshadowedStakeOther = new ModifiableCallout<>("Unshadowed Stake (on Other)", "Buster on {target}");

	@HandleEvents
	public void unshadowedStake(EventContext context, TetherEvent event) {
		if (!event.tetherIdMatches(0x25)) {
			return;
		}
		XivCombatant target = event.getTargetMatching(XivCombatant::isPc);
		if (target == null) {
			return;
		}
		if (target.isThePlayer()) {
			context.accept(unshadowedStakeOnYou.getModified(event));
		}
		else {
			context.accept(unshadowedStakeOther.getModified(event, Map.of("target", target)));
		}
	}

	// 詠唱だけ見て、以降の線や頭割りマーカーの意味を切り替える
	@HandleEvents
	public void phaseCasts(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x4CFD)) {
			phase = Phase.BETWIXT_WORLDS;
			betwixtWorldsTethers.clear();
			betwixtWorldsStack.clear();
		}
		else if (event.abilityIdMatches(0x4C99)) {
			phase = Phase.FALSE_MIDNIGHT;
			falseMidnightSpread.clear();
		}
	}

	private final ModifiableCallout<TetherEvent> betwixtWorldsTether = new ModifiableCallout<>("Betwixt Worlds Tether", "Tether on You");

	@HandleEvents
	public void betwixtWorldsTether(EventContext context, TetherEvent event) {
		if (phase != Phase.BETWIXT_WORLDS || !event.tetherIdMatches(0x11)) {
			return;
		}
		XivCombatant target = event.getTargetMatching(XivCombatant::isPc);
		if (target == null) {
			return;
		}
		betwixtWorldsTethers.add(target);
		if (target.isThePlayer()) {
			context.accept(betwixtWorldsTether.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> betwixtWorldsStackOnYou = new ModifiableCallout<>("Betwixt Worlds Stack (on You)", "Stack on You");
	private final ModifiableCallout<HeadMarkerEvent> betwixtWorldsStackPlayers = new ModifiableCallout<>("Betwixt Worlds Stack", "Stack ({players})");

	@HandleEvents
	public void betwixtWorldsStack(EventContext context, HeadMarkerEvent event) {
		if (phase != Phase.BETWIXT_WORLDS || !event.markerIdMatches(STACK_MARKER)) {
			return;
		}
		betwixtWorldsStack.add(event.getTarget());
		// 線を持っている人は頭割りに参加しない
		if (betwixtWorldsTethers.stream().anyMatch(XivCombatant::isThePlayer)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(betwixtWorldsStackOnYou.getModified(event));
		}
		else if (betwixtWorldsStack.size() > 1) {
			context.accept(betwixtWorldsStackPlayers.getModified(event, Map.of("players", names(betwixtWorldsStack))));
		}
	}

	@PlayerStatusCallout(0x8C2)
	private final ModifiableCallout<BuffApplied> leftWithThee = new ModifiableCallout<BuffApplied>("Left With Thee", "Teleporting Left").autoIcon();

	@PlayerStatusCallout(0x8C3)
	private final ModifiableCallout<BuffApplied> rightWithThee = new ModifiableCallout<BuffApplied>("Right With Thee", "Teleporting Right").autoIcon();

	@PlayerStatusCallout(0x8C0)
	private final ModifiableCallout<BuffApplied> forwardWithThee = new ModifiableCallout<BuffApplied>("Forward With Thee", "Teleporting Forward").autoIcon();

	@PlayerStatusCallout(0x8C1)
	private final ModifiableCallout<BuffApplied> backWithThee = new ModifiableCallout<BuffApplied>("Back With Thee", "Teleporting Back").autoIcon();

	private final ModifiableCallout<HeadMarkerEvent> silverShot = new ModifiableCallout<>("Silver Shot", "Spread");

	@HandleEvents
	public void silverShot(EventContext context, HeadMarkerEvent event) {
		if (phase != Phase.FALSE_MIDNIGHT || !event.markerIdMatches(SPREAD_MARKER)) {
			return;
		}
		falseMidnightSpread.add(event.getTarget());
		if (event.getTarget().isThePlayer()) {
			context.accept(silverShot.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> silverSledgeOnYou = new ModifiableCallout<>("Silver Sledge (on You)", "Stack on You");
	private final ModifiableCallout<HeadMarkerEvent> silverSledgeOther = new ModifiableCallout<>("Silver Sledge (on Other)", "Stack on {event.target}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> silverSledgeSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hm -> phase == Phase.FALSE_MIDNIGHT && hm.markerIdMatches(STACK_MARKER),
			(e1, s) -> {
				// フォールスミッドナイトの頭割りは1つだけなので、次の頭割りは雑魚フェーズのもの
				phase = Phase.ADDS;
				silverSledgeMarker = e1;
				insatiableLightStack.clear();
				// 頭割りマーカーは散開マーカーの途中で付くので、散開が揃うのを待つ
				s.waitMs(500);
				if (falseMidnightSpread.stream().anyMatch(XivCombatant::isThePlayer)) {
					return;
				}
				if (e1.getTarget().isThePlayer()) {
					s.updateCall(silverSledgeOnYou, e1);
				}
				else {
					s.updateCall(silverSledgeOther, e1);
				}
			});

	private final ModifiableCallout<AbilityCastStart> adventOfLight = ModifiableCallout.durationBasedCall("Advent Of Light", "Interrupt {event.source}");
	private final RepeatSuppressor adventOfLightSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void adventOfLight(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x4C6E) && state.playerJobMatches(Job::caresAboutInterrupt) && adventOfLightSupp.check(event)) {
			context.accept(adventOfLight.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> insatiableLightOnYou = new ModifiableCallout<>("Insatiable Light Stack (on You)", "Stack on You");
	private final ModifiableCallout<HeadMarkerEvent> insatiableLightPlayers = new ModifiableCallout<>("Insatiable Light Stack", "Stack ({players})");

	@HandleEvents
	public void insatiableLightStack(EventContext context, HeadMarkerEvent event) {
		if (phase != Phase.ADDS || !event.markerIdMatches(STACK_MARKER) || event == silverSledgeMarker) {
			return;
		}
		insatiableLightStack.add(event.getTarget());
		if (event.getTarget().isThePlayer()) {
			context.accept(insatiableLightOnYou.getModified(event));
		}
		else if (insatiableLightStack.size() > 1) {
			context.accept(insatiableLightPlayers.getModified(event, Map.of("players", names(insatiableLightStack))));
		}
	}

	@HandleEvents
	public void insatiableLight(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x4C6D)) {
			insatiableLightStack.clear();
		}
	}

	@NpcCastCallout(value = 0x4C70, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> strengthInNumbers = ModifiableCallout.durationBasedCall("Strength in Numbers", "Get Under Vertical Add");

	@NpcAbilityUsedCallout(value = 0x4C74, suppressMs = 15_000)
	private final ModifiableCallout<AbilityUsedEvent> unearnedEnvy = new ModifiableCallout<>("Unearned Envy", "Raidwide", 7_000);

	@NpcCastCallout(value = {0x4C8B, 0x4C8C, 0x4E55, 0x4E56}, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> emptyFlood = ModifiableCallout.durationBasedCall("Empty Flood", "Raidwide");

	@HandleEvents
	public void colorBuffs(EventContext context, BuffApplied event) {
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		if (event.buffIdMatches(0x8BE)) {
			color = Color.LIGHT;
		}
		else if (event.buffIdMatches(0x8BF)) {
			color = Color.DARK;
		}
	}

	private final ModifiableCallout<AbilityCastStart> boundlessAvoid = ModifiableCallout.durationBasedCall("Boundless Light/Dark (Same Target)", "Avoid {event.target}");
	private final ModifiableCallout<AbilityCastStart> boundlessStackOnYou = ModifiableCallout.durationBasedCall("Boundless Light/Dark (Stack on You)", "Stack on You");
	private final ModifiableCallout<AbilityCastStart> boundlessStackOther = ModifiableCallout.durationBasedCall("Boundless Light/Dark (Stack on Other)", "Stack on {event.target}");

	@HandleEvents
	public void boundless(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x4C5C, 0x4C5D)) {
			return;
		}
		if (lastBoundless != null && lastBoundless.getEffectiveTimeSince().compareTo(BOUNDLESS_TIMEOUT) > 0) {
			clearBoundless();
		}
		lastBoundless = event;
		// 4C5C は闇側、4C5D は光側のプレイヤーが受けるもの
		Color oppositeColor = event.abilityIdMatches(0x4C5C) ? Color.DARK : Color.LIGHT;
		if (oppositeColor == Color.DARK) {
			boundlessDark = event.getTarget();
		}
		else {
			boundlessLight = event.getTarget();
		}
		if (boundlessLight == null || boundlessDark == null || color != oppositeColor) {
			return;
		}
		if (boundlessLight.equals(boundlessDark)) {
			// 1人で両方受けている場合は絶対に重ならない
			if (!event.getTarget().isThePlayer()) {
				context.accept(boundlessAvoid.getModified(event));
			}
			return;
		}
		context.accept((event.getTarget().isThePlayer() ? boundlessStackOnYou : boundlessStackOther).getModified(event));
	}

	private final ModifiableCallout<AbilityCastStart> wordsOfNightHitByDark = ModifiableCallout.durationBasedCall("Words of Night (Light)", "Get Hit by Dark");
	private final ModifiableCallout<AbilityCastStart> wordsOfNightHitByLight = ModifiableCallout.durationBasedCall("Words of Night (Dark)", "Get Hit by Light");

	@HandleEvents
	public void wordsOfNight(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x4C2C, 0x4C65)) {
			return;
		}
		if (color == Color.LIGHT) {
			context.accept(wordsOfNightHitByDark.getModified(event));
		}
		else if (color == Color.DARK) {
			context.accept(wordsOfNightHitByLight.getModified(event));
		}
	}

	@NpcCastCallout(value = 0x4C9A, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> falseDawn = ModifiableCallout.durationBasedCall("False Dawn", "Bait Puddles");

	// 堅守系が効かないので、どこへ飛ばされるかを伝える
	@NpcCastCallout(0x4C76)
	private final ModifiableCallout<AbilityCastStart> crusade = ModifiableCallout.durationBasedCall("Crusade", "Knockback Into Corner");

	private final ModifiableCallout<AbilityUsedEvent> unjoinedAspectSouth = new ModifiableCallout<>("Unjoined Aspect (Tank)", "Go South");
	private final ModifiableCallout<AbilityUsedEvent> unjoinedAspectNorthwest = new ModifiableCallout<>("Unjoined Aspect (Light)", "Go Northwest");
	private final ModifiableCallout<AbilityUsedEvent> unjoinedAspectNortheast = new ModifiableCallout<>("Unjoined Aspect (Dark)", "Go Northeast");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> unjoinedAspectSq = SqtTemplates.sq(5_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x4C7A) && aue.isFirstTarget(),
			(e1, s) -> {
				// 色のデバフは着弾直後に付くので少し待つ
				s.waitMs(100);
				if (state.playerJobMatches(Job::isTank)) {
					s.updateCall(unjoinedAspectSouth, e1);
				}
				else if (color == Color.LIGHT) {
					s.updateCall(unjoinedAspectNorthwest, e1);
				}
				else {
					s.updateCall(unjoinedAspectNortheast, e1);
				}
			});

	private final ModifiableCallout<AbilityCastStart> threefoldGraceDark = ModifiableCallout.durationBasedCall("Threefold Grace (Light)", "Stand in Dark");
	private final ModifiableCallout<AbilityCastStart> threefoldGraceLight = ModifiableCallout.durationBasedCall("Threefold Grace (Dark)", "Stand in Light");

	@HandleEvents
	public void threefoldGrace(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x4C7E)) {
			return;
		}
		if (color == Color.LIGHT) {
			context.accept(threefoldGraceDark.getModified(event));
		}
		else if (color == Color.DARK) {
			context.accept(threefoldGraceLight.getModified(event));
		}
	}

}
