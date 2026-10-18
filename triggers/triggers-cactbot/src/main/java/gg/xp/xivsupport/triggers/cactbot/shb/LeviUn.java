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
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.TargetabilityUpdate;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;

import java.util.List;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/trial/levi-un.ts をもとに移植
@CalloutRepo(name = "The Whorleater Unreal", duty = KnownDuty.TheWhorleaterUnreal)
public class LeviUn extends AutoChildEventHandler implements FilteredEventHandler {

	// HPを持っている本体のリヴァイアサン
	private static final long LEVIATHAN_NPC_ID = 12669;
	// 潜ってから本体が移動し終わるまで 4.6～4.7 秒かかる
	private static final int SLAM_POSITION_DELAY_MS = 5_000;
	private static final int COMBATANT_REFRESH_MS = 100;
	private static final double SLAM_X = 14.6;
	private static final double SLAM_Y = 13;
	private static final double SLAM_TOLERANCE = 1;
	private static final int DIVES_PER_CYCLE = 3;

	private XivState state;

	public LeviUn(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheWhorleaterUnreal);
	}

	// 通常はボディスラム → ダイブ → ダイブ → ボディスラムの順になる
	private int diveCounter;
	private boolean veilCalled;
	private boolean mantleCalled;

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		diveCounter = 0;
		veilCalled = false;
		mantleCalled = false;
	}

	private final ModifiableCallout<AbilityUsedEvent> veilOfTheWhorl = new ModifiableCallout<>("Veil of the Whorl", "Attack Head Only");
	private final ModifiableCallout<AbilityUsedEvent> mantleOfTheWhorl = new ModifiableCallout<>("Mantle of the Whorl", "Attack Tail Only");

	@HandleEvents
	public void abilities(EventContext context, AbilityUsedEvent event) {
		if (event.getSource().isPc()) {
			return;
		}
		switch ((int) event.getAbility().getId()) {
			// タイダルウェーブとボディスラムの後は必ずボディスラムから数え直す
			case 0x5CDE, 0x5CD2 -> diveCounter = 1;
			case 0x5CE5 -> {
				if (!veilCalled && state.playerJobMatches(job -> job.isCaster() || job.isHealer())) {
					veilCalled = true;
					context.accept(veilOfTheWhorl.getModified(event));
				}
			}
			case 0x5CE4 -> {
				if (!mantleCalled && state.playerJobMatches(Job::isPranged)) {
					mantleCalled = true;
					context.accept(mantleOfTheWhorl.getModified(event));
				}
			}
		}
	}

	private final ModifiableCallout<TargetabilityUpdate> slamNorth = new ModifiableCallout<>("Body Slam Location: North", "North");
	private final ModifiableCallout<TargetabilityUpdate> slamSouth = new ModifiableCallout<>("Body Slam Location: South", "South");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> slamLocationSq = SqtTemplates.sq(SLAM_POSITION_DELAY_MS + 5_000,
			TargetabilityUpdate.class, tu -> tu.getTarget().npcIdMatches(LEVIATHAN_NPC_ID) && !tu.isTargetable(),
			(e1, s) -> {
				diveCounter++;
				if (diveCounter % DIVES_PER_CYCLE != 1) {
					return;
				}
				s.waitMs(SLAM_POSITION_DELAY_MS);
				s.refreshCombatants(COMBATANT_REFRESH_MS);
				List<Position> slamPositions = state.npcsById(LEVIATHAN_NPC_ID).stream()
						.map(XivCombatant::getPos)
						.filter(pos -> pos != null
						               && Math.abs(Math.abs(pos.getX()) - SLAM_X) < SLAM_TOLERANCE
						               && Math.abs(Math.abs(pos.getY()) - SLAM_Y) < SLAM_TOLERANCE)
						.toList();
				if (slamPositions.size() != 1) {
					return;
				}
				s.updateCall(slamPositions.get(0).getY() > 0 ? slamNorth : slamSouth, e1);
			});

}
