package gg.xp.xivsupport.triggers.cactbot.ew;

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
import gg.xp.xivsupport.events.actlines.events.TargetabilityUpdate;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/trial/endsinger.ts をもとに移植
@CalloutRepo(name = "The Final Day", duty = KnownDuty.TheFinalDay)
public class Endsinger extends AutoChildEventHandler implements FilteredEventHandler {

	private static final double CENTER = 100;
	// 座標は浮動小数なので、中心線上かどうかは少し余裕を持って判定する
	private static final double AXIS_TOLERANCE = 0.5;

	private XivState state;

	public Endsinger(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheFinalDay);
	}

	private final Map<Long, Position> storedStars = new HashMap<>();
	private final List<XivCombatant> pendingStars = new ArrayList<>();
	private int phase = 1;
	private long bossId;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		storedStars.clear();
		pendingStars.clear();
		phase = 1;
		bossId = 0;
	}

	private static boolean onAxis(double value) {
		return Math.abs(value - CENTER) < AXIS_TOLERANCE;
	}

	// 星の反対側の斜めが安置
	private static ArenaSector starSafeDir(Position star) {
		if (star.getX() < CENTER) {
			return star.getY() < CENTER ? ArenaSector.SOUTHEAST : ArenaSector.NORTHEAST;
		}
		return star.getY() < CENTER ? ArenaSector.SOUTHWEST : ArenaSector.NORTHWEST;
	}

	private static boolean isStarCast(AbilityCastStart acs) {
		return acs.abilityIdMatches(0x662E, 0x6634);
	}

	@HandleEvents
	public void starCast(EventContext context, AbilityCastStart event) {
		if (isStarCast(event)) {
			pendingStars.add(event.getSource());
		}
	}

	private final ModifiableCallout<AbilityCastStart> doomedStars = ModifiableCallout.durationBasedCall("Doomed Stars", "{safe}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> doomedStarsSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, Endsinger::isStarCast,
			(e1, s) -> {
				// 出現直後は座標が更新されていないことがあるので、少し待ってから取り直す
				s.waitThenRefreshCombatants(250);
				for (XivCombatant star : pendingStars) {
					Position pos = state.getLatestCombatantData(star).getPos();
					if (pos != null) {
						storedStars.put(star.getId(), pos);
					}
				}
				pendingStars.clear();
				Position pos = storedStars.get(e1.getSource().getId());
				if (pos != null) {
					s.setParam("safe", starSafeDir(pos));
					s.updateCall(doomedStars, e1);
				}
			});

	private final ModifiableCallout<TetherEvent> fatalism = new ModifiableCallout<>("Doomed Stars (Fatalism Tether)", "{safe}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> fatalismSq = SqtTemplates.sq(15_000,
			TetherEvent.class, te -> te.tetherIdMatches(0xA6),
			(e1, s) -> {
				s.waitMs(10_000);
				for (XivCombatant cbt : e1.getTargets()) {
					Position pos = storedStars.get(cbt.getId());
					if (pos != null) {
						s.setParam("safe", starSafeDir(pos));
						s.updateCall(fatalism, e1);
						return;
					}
				}
			});

	// 星以外の詠唱はボスなので、ここでボスを覚えておく
	private final ModifiableCallout<AbilityCastStart> elegeia = ModifiableCallout.durationBasedCall("Elegeia", "Raidwide");

	@HandleEvents
	public void elegeia(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x662C, 0x6682)) {
			bossId = event.getSource().getId();
			context.accept(elegeia.getModified(event));
		}
	}

	@NpcCastCallout(0x6C69)
	private final ModifiableCallout<AbilityCastStart> galaxias = ModifiableCallout.durationBasedCall("Galaxias", "Knockback");

	@NpcCastCallout(0x6644)
	private final ModifiableCallout<AbilityCastStart> elenchosMiddle = ModifiableCallout.durationBasedCall("Elenchos (Middle)", "Sides");

	@NpcCastCallout(0x6642)
	private final ModifiableCallout<AbilityCastStart> elenchosOutsides = ModifiableCallout.durationBasedCall("Elenchos (Outsides)", "Middle");

	@NpcCastCallout(0x6649)
	private final ModifiableCallout<AbilityCastStart> deathsEmbrace = ModifiableCallout.durationBasedCall("Death's Embrace", "Spread");

	private final ModifiableCallout<AbilityUsedEvent> deathsEmbraceFeathers = new ModifiableCallout<>("Death's Embrace (Feathers)", "Move Away");

	@HandleEvents
	public void deathsEmbraceFeathers(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x6649) && event.isFirstTarget()) {
			context.accept(deathsEmbraceFeathers.getModified(event));
		}
	}

	@NpcCastCallout(0x663D)
	private final ModifiableCallout<AbilityCastStart> deathsAporrhoia = ModifiableCallout.durationBasedCall("Death's Aporrhoia", "Avoid Head Lasers");

	private final ModifiableCallout<AbilityCastStart> hubrisOnYou = ModifiableCallout.durationBasedCall("Hubris (On You)", "Tank Cleave on You");
	private final ModifiableCallout<AbilityCastStart> hubrisAvoid = ModifiableCallout.durationBasedCall("Hubris (Not on You)", "Avoid Tank Cleave");

	@HandleEvents
	public void hubris(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x6652)) {
			context.accept((event.getTarget().isThePlayer() ? hubrisOnYou : hubrisAvoid).getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> epigonoiCardinal = ModifiableCallout.durationBasedCall("Epigonoi (Cardinal Safe)", "Cardinal Edge");
	private final ModifiableCallout<AbilityCastStart> epigonoiIntercard = ModifiableCallout.durationBasedCall("Epigonoi (Intercardinal Safe)", "Intercardinal Edge");

	private final RepeatSuppressor epigonoiSupp = new RepeatSuppressor(Duration.ofSeconds(3));

	@HandleEvents
	public void epigonoi(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x6646)) {
			return;
		}
		Position pos = event.getSource().getPos();
		// 中央の頭では判定できないので、外側の頭を1つ使う
		if (pos == null || (onAxis(pos.getX()) && onAxis(pos.getY()))) {
			return;
		}
		if (!epigonoiSupp.check(event)) {
			return;
		}
		// 頭が東西南北にいるなら斜めが安置
		boolean cardinalHead = onAxis(pos.getX()) || onAxis(pos.getY());
		context.accept((cardinalHead ? epigonoiIntercard : epigonoiCardinal).getModified(event));
	}

	private final ModifiableCallout<TargetabilityUpdate> interstellar = new ModifiableCallout<>("Interstellar", "{dir1} / {dir2}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> interstellarSq = SqtTemplates.sq(10_000,
			TargetabilityUpdate.class, tu -> !tu.isTargetable() && phase == 1 && bossId != 0 && tu.getSource().getId() == bossId,
			(e1, s) -> {
				// ボスが移動し終わるまで待つ
				s.waitMs(3_900);
				s.refreshCombatants(100);
				Position pos = state.getLatestCombatantData(e1.getSource()).getPos();
				if (pos == null) {
					return;
				}
				double x = pos.getX();
				double y = pos.getY();
				ArenaSector dir1;
				ArenaSector dir2;
				if (onAxis(x)) {
					dir1 = ArenaSector.EAST;
					dir2 = ArenaSector.WEST;
				}
				else if (onAxis(y)) {
					dir1 = ArenaSector.NORTH;
					dir2 = ArenaSector.SOUTH;
				}
				else if ((x < CENTER) == (y < CENTER)) {
					dir1 = ArenaSector.NORTHEAST;
					dir2 = ArenaSector.SOUTHWEST;
				}
				else {
					dir1 = ArenaSector.NORTHWEST;
					dir2 = ArenaSector.SOUTHEAST;
				}
				s.setParam("dir1", dir1);
				s.setParam("dir2", dir2);
				s.updateCall(interstellar, e1);
			});

	@HandleEvents
	public void planetes(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x6B58)) {
			phase = 2;
		}
	}

	@NpcCastCallout(value = 0x664E, onYou = true)
	private final ModifiableCallout<AbilityCastStart> nemesis = ModifiableCallout.durationBasedCall("Nemesis", "Spread");

	private final ModifiableCallout<AbilityCastStart> ultimateFate = ModifiableCallout.durationBasedCall("Ultimate Fate", "Tank LB Now");

	@HandleEvents
	public void ultimateFate(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x6B59) && state.playerJobMatches(job -> job.isTank() || job == Job.BLU)) {
			context.accept(ultimateFate.getModified(event));
		}
	}

}
