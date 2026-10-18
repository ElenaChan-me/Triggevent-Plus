package gg.xp.xivsupport.triggers.dtun;

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

import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/trial/byakko-un.ts をもとに移植
@CalloutRepo(name = "Byakko Unreal", duty = KnownDuty.ByakkoUn)
public class ByakkoUn extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;
	private volatile boolean flareOnMe;

	public ByakkoUn(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.ByakkoUn);
	}

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		flareOnMe = false;
		firstStake = null;
		stakeCount = 0;
	}

	// 青魔道士がタンク役で入ることがあるため、タンク向けの通知に含める
	private boolean isTankOrBlu() {
		Job job = state.getPlayerJob();
		return job != null && (job.isTank() || job == Job.BLU);
	}

	@NpcCastCallout(0x9BFB)
	private final ModifiableCallout<AbilityCastStart> heavenlyStrike = ModifiableCallout.durationBasedCall("Heavenly Strike", "Tank Cleave on {event.target}");

	// 外周に安置がないドーナツ範囲
	@NpcCastCallout(0x9C15)
	private final ModifiableCallout<AbilityCastStart> sweepTheLegIntermission = ModifiableCallout.durationBasedCall("Sweep the Leg (Intermission)", "In");

	// 270度の前方範囲
	@NpcCastCallout(0x9BFC)
	private final ModifiableCallout<AbilityCastStart> sweepTheLeg = ModifiableCallout.durationBasedCall("Sweep the Leg", "Behind");

	@NpcCastCallout(0x9BFD)
	private final ModifiableCallout<AbilityCastStart> stormPulse = ModifiableCallout.durationBasedCall("Storm Pulse", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> distantClap = ModifiableCallout.durationBasedCall("Distant Clap", "Under");

	@HandleEvents
	public void distantClapCast(EventContext context, AbilityCastStart event) {
		// フレア持ちは下に入ると巻き込むので通知しない
		if (event.abilityIdMatches(0x9BFE) && !event.getSource().isPc() && !flareOnMe) {
			context.accept(distantClap.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> stateOfShockOther = new ModifiableCallout<>("State of Shock (Not on You)", "Provoke Boss");
	private final ModifiableCallout<AbilityCastStart> stateOfShockSelf = new ModifiableCallout<>("State of Shock (on You, Delayed)", "Provoke Boss");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> stateOfShockSq = SqtTemplates.sq(20_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x9C01),
			(e1, s) -> {
				Job job = state.getPlayerJob();
				if (job == null || !job.isTank()) {
					return;
				}
				if (e1.getTarget().isThePlayer()) {
					// 自分が掴まれた場合は解放後に取り返す
					s.waitMs(12_000);
					s.updateCall(stateOfShockSelf, e1);
				}
				else {
					s.updateCall(stateOfShockOther, e1);
				}
			});

	private final ModifiableCallout<AbilityCastStart> roarOfThunder = new ModifiableCallout<>("The Roar of Thunder: Tank LB", "Tank LB Now");

	// 2回目の雷轟だけがタンクLB必須
	@AutoFeed
	private final SequentialTrigger<BaseEvent> roarOfThunderSq = SqtTemplates.multiInvocation(20_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x9C19),
			(e1, s) -> {
			},
			(e1, s) -> {
				s.waitMs(14_000);
				if (isTankOrBlu()) {
					s.updateCall(roarOfThunder, e1);
				}
			});

	@PlayerHeadmarker(0x65)
	private final ModifiableCallout<HeadMarkerEvent> galeForce = new ModifiableCallout<>("Gale Force", "Drop Bubble Outside");

	@PlayerStatusCallout(0x5C9)
	private final ModifiableCallout<BuffApplied> ominousWind = ModifiableCallout.<BuffApplied>durationBasedCall("Ominous Wind", "Pink Bubble").autoIcon();

	@PlayerHeadmarker(0x04)
	private final ModifiableCallout<HeadMarkerEvent> aratama = new ModifiableCallout<>("Aratama", "Puddles on You");

	private final ModifiableCallout<HeadMarkerEvent> whiteHerald = new ModifiableCallout<>("White Herald", "Away from Group");

	@HandleEvents
	public void whiteHeraldMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x57) && event.getTarget().isThePlayer()) {
			flareOnMe = true;
			context.accept(whiteHerald.getModified(event));
		}
	}

	@HandleEvents
	public void whiteHeraldCleanup(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x9C1A)) {
			flareOnMe = false;
		}
	}

	private final ModifiableCallout<BattleTalkEvent> tigerAdd = new ModifiableCallout<>("Tiger Add", "Tiger Add");

	@HandleEvents
	public void tigerAddTalk(EventContext context, BattleTalkEvent event) {
		if (event.getInstanceContentTextId() == 0x48AE && isTankOrBlu()) {
			context.accept(tigerAdd.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> highestStakes = ModifiableCallout.durationBasedCall("Highest Stakes", "Stack #{num}");

	// 一連の乾坤一擲は20秒以内に収まるので、その間だけ回数を数える
	private static final long STAKES_WINDOW_MS = 20_000;
	private AbilityCastStart firstStake;
	private int stakeCount;

	@HandleEvents
	public void highestStakesCast(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x9C03) || event.getSource().isPc()) {
			return;
		}
		if (firstStake == null || firstStake.getEffectiveTimeSince().toMillis() > STAKES_WINDOW_MS) {
			firstStake = event;
			stakeCount = 0;
		}
		stakeCount++;
		context.accept(highestStakes.getModified(event, Map.of("num", stakeCount)));
	}

}
