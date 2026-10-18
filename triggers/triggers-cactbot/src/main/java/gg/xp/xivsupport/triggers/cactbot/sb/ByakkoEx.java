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
import gg.xp.xivsupport.events.misc.BattleTalkEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/trial/byakko-ex.ts をもとに移植
@CalloutRepo(name = "The Jade Stoa Extreme", duty = KnownDuty.TheJadeStoaExtreme)
public class ByakkoEx extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long WHITE_HERALD_MARKER = 0x57;
	private static final long HIGHEST_STAKES = 0x27E2;
	// 2回目の雷轟で白帝の詠唱が終わる直前にタンクLBを入れる
	private static final int TANK_LB_ROAR = 2;
	private static final int TANK_LB_DELAY_MS = 14_000;
	private static final int STATE_OF_SHOCK_SWAP_DELAY_MS = 12_000;
	// 以降の連続詠唱を同じ組として数える期間
	private static final int HIGHEST_STAKES_WINDOW_MS = 20_000;

	private XivState state;

	public ByakkoEx(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheJadeStoaExtreme);
	}

	private int roarCount;
	private boolean playerHasFlare;

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		roarCount = 0;
		playerHasFlare = false;
	}

	private boolean isTank() {
		return state.playerJobMatches(job -> job.isTank() || job == Job.BLU);
	}

	private final ModifiableCallout<AbilityCastStart> heavenlyStrikeOnYou = ModifiableCallout.durationBasedCall("Heavenly Strike: On You", "Tank Cleave on You");
	private final ModifiableCallout<AbilityCastStart> heavenlyStrikeAvoid = ModifiableCallout.durationBasedCall("Heavenly Strike: Not Targeted", "Avoid Tank Cleave");

	@HandleEvents
	public void heavenlyStrike(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x27DA)) {
			context.accept((event.getTarget().isThePlayer() ? heavenlyStrikeOnYou : heavenlyStrikeAvoid).getModified(event));
		}
	}

	// 中間のドーナツ範囲は外側に安置が無い
	@NpcCastCallout(0x27F4)
	private final ModifiableCallout<AbilityCastStart> sweepTheLegIntermission = ModifiableCallout.durationBasedCall("Sweep The Leg (Intermission)", "In");

	@NpcCastCallout(0x27DB)
	private final ModifiableCallout<AbilityCastStart> sweepTheLeg = ModifiableCallout.durationBasedCall("Sweep The Leg", "Get Behind");

	@NpcCastCallout(0x27DC)
	private final ModifiableCallout<AbilityCastStart> stormPulse = ModifiableCallout.durationBasedCall("Storm Pulse", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> distantClap = ModifiableCallout.durationBasedCall("Distant Clap", "Get Under");

	// フレアマーカーと同時に来るので、マーカーの判定を待ってから呼ぶ
	@AutoFeed
	private final SequentialTrigger<BaseEvent> distantClapSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x27DD) && !acs.getTarget().isPc(),
			(e1, s) -> {
				s.waitMs(100);
				if (!playerHasFlare) {
					s.updateCall(distantClap, e1);
				}
			});

	private final ModifiableCallout<AbilityCastStart> stateOfShockProvoke = new ModifiableCallout<>("State Of Shock: Not Targeted", "Provoke Boss");
	private final ModifiableCallout<AbilityCastStart> stateOfShockProvokeBack = new ModifiableCallout<>("State Of Shock: Targeted", "Provoke Boss");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> stateOfShockSq = SqtTemplates.sq(20_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x27E0),
			(e1, s) -> {
				if (!state.playerJobMatches(Job::isTank)) {
					return;
				}
				if (!e1.getTarget().isThePlayer()) {
					s.updateCall(stateOfShockProvoke, e1);
				}
				else {
					// 掴まれている間はもう一人のタンクが持つので、解放後に取り返す
					s.waitMs(STATE_OF_SHOCK_SWAP_DELAY_MS);
					s.updateCall(stateOfShockProvokeBack, e1);
				}
			});

	private final ModifiableCallout<AbilityCastStart> roarOfThunderLb = new ModifiableCallout<>("Roar Of Thunder: Tank LB", "Tank LB Now");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> roarOfThunderSq = SqtTemplates.sq(TANK_LB_DELAY_MS + 5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x27F9),
			(e1, s) -> {
				roarCount++;
				if (roarCount != TANK_LB_ROAR || !isTank()) {
					return;
				}
				s.waitMs(TANK_LB_DELAY_MS);
				s.updateCall(roarOfThunderLb, e1);
			});

	@PlayerHeadmarker(0x65)
	private final ModifiableCallout<HeadMarkerEvent> galeForce = new ModifiableCallout<>("Gale Force", "Drop Bubble Outside");

	@PlayerStatusCallout(0x5C9)
	private final ModifiableCallout<BuffApplied> ominousWind = new ModifiableCallout<BuffApplied>("Ominous Wind", "Pink Bubble").autoIcon();

	@PlayerHeadmarker(0x4)
	private final ModifiableCallout<HeadMarkerEvent> aratama = new ModifiableCallout<>("Aratama", "Puddles on You");

	@PlayerHeadmarker(WHITE_HERALD_MARKER)
	private final ModifiableCallout<HeadMarkerEvent> whiteHerald = new ModifiableCallout<>("White Herald", "Away from Group");

	@HandleEvents
	public void whiteHeraldCollect(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(WHITE_HERALD_MARKER) && event.getTarget().isThePlayer()) {
			playerHasFlare = true;
		}
	}

	@HandleEvents
	public void whiteHeraldCleanup(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x9C1A)) {
			playerHasFlare = false;
		}
	}

	private final ModifiableCallout<BattleTalkEvent> tigerAdd = new ModifiableCallout<>("Tiger Add", "Tiger Add");

	@HandleEvents
	public void tigerAdd(EventContext context, BattleTalkEvent event) {
		// 「二重の怒りを受けよ」のセリフで虎の雑魚が出る
		if (event.getInstanceContentTextId() == 0x48AE && isTank()) {
			context.accept(tigerAdd.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> highestStakes = ModifiableCallout.durationBasedCall("Highest Stakes", "Stack #{num}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> highestStakesSq = SqtTemplates.sq(HIGHEST_STAKES_WINDOW_MS,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(HIGHEST_STAKES),
			(e1, s) -> {
				int num = 1;
				s.setParam("num", num);
				s.updateCall(highestStakes, e1);
				while (true) {
					AbilityCastStart next = s.waitEvent(AbilityCastStart.class, acs -> acs.abilityIdMatches(HIGHEST_STAKES));
					num++;
					s.setParam("num", num);
					s.updateCall(highestStakes, next);
				}
			});

}
