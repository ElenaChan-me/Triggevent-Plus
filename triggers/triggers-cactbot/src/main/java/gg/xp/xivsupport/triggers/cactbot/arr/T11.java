package gg.xp.xivsupport.triggers.cactbot.arr;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.HitPoints;
import gg.xp.xivsupport.models.XivCombatant;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/raid/t11.ts をもとに移植
@CalloutRepo(name = "The Final Coil Of Bahamut Turn2", duty = KnownDuty.TheFinalCoilOfBahamutTurn2)
public class T11 extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long RED_TETHER = 0x1C;
	private static final long BLUE_TETHER = 0x1D;
	// 60%を切るとフェーズ2のギミックが始まる
	private static final double PHASE_2_HP = 0.60;
	private static final int HP_POLL_MS = 500;

	private XivState state;
	private @Nullable String firstSeed;
	private boolean beganMonitoringHp;
	private final List<XivCombatant> tetherA = new ArrayList<>();
	private final List<XivCombatant> tetherB = new ArrayList<>();

	public T11(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheFinalCoilOfBahamutTurn2);
	}

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		firstSeed = null;
		beganMonitoringHp = false;
		tetherA.clear();
		tetherB.clear();
	}

	@NpcAbilityUsedCallout(0xB73)
	private final ModifiableCallout<AbilityUsedEvent> secondaryHead = new ModifiableCallout<>("Secondary Head", "Stun on {event.target}");

	private final ModifiableCallout<AbilityUsedEvent> seedRiverFirst = new ModifiableCallout<>("Seed of the Rivers (First)", "Spread, then Stack");
	private final ModifiableCallout<AbilityUsedEvent> seedSeaFirst = new ModifiableCallout<>("Seed of the Sea (First)", "Stack, then Spread");
	private final ModifiableCallout<AbilityUsedEvent> seedRiverSecond = new ModifiableCallout<>("Seed of the Rivers (Second)", "Stack");
	private final ModifiableCallout<AbilityUsedEvent> seedSeaSecond = new ModifiableCallout<>("Seed of the Sea (Second)", "Spread");
	// 対象ごとにイベントが来るので、同じ技は1回だけ処理する
	private final RepeatSuppressor seedSupp = new RepeatSuppressor(Duration.ofSeconds(2));

	@HandleEvents
	public void seeds(EventContext context, AbilityUsedEvent event) {
		if (event.getSource().isPc() || !event.abilityIdMatches(0xB74, 0xB75, 0xB76, 0xB77) || !seedSupp.check(event)) {
			return;
		}
		switch ((int) event.getAbility().getId()) {
			case 0xB74 -> {
				if (firstSeed == null) {
					context.accept(seedRiverFirst.getModified(event));
					firstSeed = "river";
				}
			}
			case 0xB75 -> {
				if (firstSeed == null) {
					context.accept(seedSeaFirst.getModified(event));
					firstSeed = "sea";
				}
			}
			case 0xB76 -> {
				if (firstSeed == null) {
					context.accept(seedRiverSecond.getModified(event));
				}
				firstSeed = null;
			}
			case 0xB77 -> {
				if (firstSeed == null) {
					context.accept(seedSeaSecond.getModified(event));
				}
				firstSeed = null;
			}
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> phase2 = new ModifiableCallout<>("Phase 2", "Out of Middle");

	// バロフィールドを合図にボスのHPを監視し始める
	@AutoFeed
	private final SequentialTrigger<BaseEvent> phase2Sq = SqtTemplates.sq(600_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0xB6F) && !aue.getSource().isPc() && !beganMonitoringHp,
			(e1, s) -> {
				beganMonitoringHp = true;
				while (true) {
					HitPoints hp = state.getLatestCombatantData(e1.getSource()).getHp();
					if (hp != null && hp.max() > 0 && hp.getPercent() <= PHASE_2_HP) {
						s.updateCall(phase2, e1);
						return;
					}
					s.waitMs(HP_POLL_MS);
				}
			});

	private final ModifiableCallout<AbilityUsedEvent> forkedLightning = new ModifiableCallout<>("Forked Lightning", "Lightning on You");

	@HandleEvents
	public void forkedLightning(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0xB85) && !event.getSource().isPc() && event.getTarget().isThePlayer()) {
			context.accept(forkedLightning.getModified(event));
		}
	}

	@NpcAbilityUsedCallout(value = 0xB78, suppressMs = 2_000)
	private final ModifiableCallout<AbilityUsedEvent> phase3 = new ModifiableCallout<>("Phase 3", "Final Phase");

	private final ModifiableCallout<TetherEvent> redTethers = new ModifiableCallout<>("Red Tethers", "Red Tethers With {player}");
	private final ModifiableCallout<TetherEvent> blueTethers = new ModifiableCallout<>("Blue Tethers", "Blue Tethers With {player}");

	@HandleEvents
	public void tethers(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(RED_TETHER)) {
			handleTether(context, event, tetherA, redTethers);
		}
		else if (event.tetherIdMatches(BLUE_TETHER)) {
			handleTether(context, event, tetherB, blueTethers);
		}
	}

	private void handleTether(EventContext context, TetherEvent event, List<XivCombatant> tethered, ModifiableCallout<TetherEvent> call) {
		tethered.add(event.getSource());
		if (tethered.size() != 2) {
			return;
		}
		XivCombatant player0 = tethered.get(0);
		XivCombatant player1 = tethered.get(1);
		XivCombatant partner = null;
		if (player0.isThePlayer()) {
			partner = player1;
		}
		if (player1.isThePlayer()) {
			partner = player0;
		}
		if (partner != null) {
			context.accept(call.getModified(event, Map.of("player", partner)));
		}
	}

	@HandleEvents
	public void tetherCleanup(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0xB7B)) {
			tetherA.clear();
			tetherB.clear();
		}
	}
}
