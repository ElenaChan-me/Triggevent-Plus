package gg.xp.xivsupport.triggers.cactbot.shb;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.RawAddCombatantEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.RawXivCombatantInfo;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.Position;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/raid/e12n.ts をもとに移植
@CalloutRepo(name = "Edens Promise Eternity", duty = KnownDuty.EdensPromiseEternity)
public class E12n extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long TITANIC_BOMB_BOULDER = 9816;
	// 爆弾の湧き位置は南北が y=-70 を境に分かれる
	private static final double BOMB_CENTER_Y = -70;
	private static final long TETHER_LEVIATHAN = 0x8E;
	private static final long TETHER_IFRIT = 0x8F;
	private static final long TETHER_RAMUH = 0x90;
	private static final long TETHER_GARUDA = 0x91;

	private XivState state;

	public E12n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.EdensPromiseEternity);
	}

	private record Bomb(boolean north, boolean east) {
	}

	private boolean seenIntermission;
	private final List<Bomb> bombs = new ArrayList<>();
	private final List<Long> tethers = new ArrayList<>();

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		seenIntermission = false;
		bombs.clear();
		tethers.clear();
	}

	@HandleEvents
	public void intermissionComplete(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x4B48)) {
			seenIntermission = true;
		}
	}

	@NpcCastCallout(0x5872)
	private final ModifiableCallout<AbilityCastStart> maleficium = ModifiableCallout.durationBasedCall("Maleficium", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> formlessJudgmentTank = ModifiableCallout.durationBasedCall("Formless Judgment (On You)", "Tank Cleave on You");
	private final ModifiableCallout<AbilityCastStart> formlessJudgment = ModifiableCallout.durationBasedCall("Formless Judgment", "Avoid Tank Cleave");

	@HandleEvents
	public void formlessJudgment(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x5873)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(formlessJudgmentTank.getModified(event));
		}
		else {
			context.accept(formlessJudgment.getModified(event));
		}
	}

	@HandleEvents
	public void bombCollect(EventContext context, RawAddCombatantEvent event) {
		RawXivCombatantInfo info = event.getFullInfo();
		long nameId = info != null ? info.getBnpcNameId() : event.getEntity().getbNpcNameId();
		if (nameId != TITANIC_BOMB_BOULDER) {
			return;
		}
		Position pos = info != null ? info.getPos() : event.getEntity().getPos();
		if (pos != null) {
			bombs.add(new Bomb(pos.getY() < BOMB_CENTER_Y, pos.getX() > 0));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> bouldersNorth = new ModifiableCallout<>("Boulders Impact: North Safe", "Between North Bombs");
	private final ModifiableCallout<AbilityUsedEvent> bouldersSouth = new ModifiableCallout<>("Boulders Impact: South Safe", "Between South Bombs");
	private final ModifiableCallout<AbilityUsedEvent> bouldersEast = new ModifiableCallout<>("Boulders Impact: East Safe", "Between East Bombs");
	private final ModifiableCallout<AbilityUsedEvent> bouldersWest = new ModifiableCallout<>("Boulders Impact: West Safe", "Between West Bombs");
	private final RepeatSuppressor bouldersImpactSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void bouldersImpact(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x586E) || !bouldersImpactSupp.check(event)) {
			return;
		}
		if (bombs.size() >= 2) {
			Bomb first = bombs.get(0);
			Bomb second = bombs.get(1);
			// 爆弾が2つ並んだ側の反対が安置
			ModifiableCallout<AbilityUsedEvent> call;
			if (first.north() == second.north()) {
				call = first.north() ? bouldersSouth : bouldersNorth;
			}
			else {
				call = first.east() ? bouldersWest : bouldersEast;
			}
			context.accept(call.getModified(event));
		}
		bombs.clear();
	}

	private final ModifiableCallout<AbilityUsedEvent> bouldersExplosion = new ModifiableCallout<>("Boulders Explosion", "Move to Last Explosions");
	private final RepeatSuppressor bouldersExplosionSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void bouldersExplosion(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x586F) && bouldersExplosionSupp.check(event)) {
			context.accept(bouldersExplosion.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> rapturousStackOnYou = new ModifiableCallout<>("Rapturous Reach: Stack on You", "Stack on You");
	private final ModifiableCallout<HeadMarkerEvent> rapturousStacks = new ModifiableCallout<>("Rapturous Reach: Two Stacks", "Stack ({first}, {second})");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> rapturousReachDoubleSq = SqtTemplates.sq(10_000,
			HeadMarkerEvent.class, hme -> hme.markerIdMatches(0x3E) && !seenIntermission,
			(e1, s) -> {
				if (e1.getTarget().isThePlayer()) {
					s.call(rapturousStackOnYou, e1);
				}
				HeadMarkerEvent e2 = s.waitEvent(HeadMarkerEvent.class, hme -> hme.markerIdMatches(0x3E));
				if (e2.getTarget().isThePlayer()) {
					s.call(rapturousStackOnYou, e2);
				}
				String a = e1.getTarget().getName();
				String b = e2.getTarget().getName();
				boolean ordered = a.compareTo(b) <= 0;
				s.setParam("first", ordered ? a : b);
				s.setParam("second", ordered ? b : a);
				s.updateCall(rapturousStacks, e2);
			});

	private final ModifiableCallout<HeadMarkerEvent> rapturousSingle = new ModifiableCallout<>("Rapturous Reach (Single)", "Stack on {event.target}");

	@HandleEvents
	public void rapturousReachSingle(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E) && seenIntermission) {
			context.accept(rapturousSingle.getModified(event));
		}
	}

	@NpcCastCallout(0x5864)
	private final ModifiableCallout<AbilityCastStart> diamondDust = ModifiableCallout.durationBasedCall("Diamond Dust", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> diamondDustStop = new ModifiableCallout<>("Diamond Dust: Stop", "Stop Moving");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> diamondDustStopSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x5864),
			(e1, s) -> {
				// 全体攻撃のコールと重ならないように少し遅らせる
				s.waitMs(1_000);
				s.updateCall(diamondDustStop, e1);
			});

	private final ModifiableCallout<HeadMarkerEvent> frigidStone = new ModifiableCallout<>("Frigid Stone", "Spread");

	@HandleEvents
	public void frigidStone(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x60) && event.getTarget().isThePlayer()) {
			context.accept(frigidStone.getModified(event));
		}
	}

	@HandleEvents
	public void tetherCollect(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(TETHER_LEVIATHAN, TETHER_IFRIT, TETHER_RAMUH, TETHER_GARUDA)) {
			tethers.add(event.getId());
		}
	}

	private final ModifiableCallout<AbilityCastStart> releaseSingle = new ModifiableCallout<>("Release: One Primal", "{safe}");
	private final ModifiableCallout<AbilityCastStart> releaseCombined = new ModifiableCallout<>("Release: Two Primals", "{safe1} + {safe2}");
	private final ModifiableCallout<AbilityCastStart> releaseLeviIfrit = new ModifiableCallout<>("Release: Leviathan + Ifrit", "Under + Sides");
	private final ModifiableCallout<AbilityCastStart> releaseLeviRamuh = new ModifiableCallout<>("Release: Leviathan + Ramuh", "North/South + Out");
	private final ModifiableCallout<AbilityCastStart> releaseLeviGaruda = new ModifiableCallout<>("Release: Leviathan + Garuda", "Under + Intercards");

	private static String primalSafeSpot(long tetherId) {
		if (tetherId == TETHER_LEVIATHAN) {
			return "Middle";
		}
		else if (tetherId == TETHER_IFRIT) {
			return "Sides";
		}
		else if (tetherId == TETHER_RAMUH) {
			return "Out";
		}
		else {
			return "Intercards";
		}
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> releaseSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x4E2C, 0x585B, 0x5861),
			(e1, s) -> {
				// 線はログ上で先に来るはずだが念のため待つ
				s.waitMs(500);
				List<Long> sorted = tethers.stream().sorted().toList();
				if (sorted.size() == 1) {
					s.setParam("safe", primalSafeSpot(sorted.get(0)));
					s.updateCall(releaseSingle, e1);
				}
				else if (sorted.size() >= 2) {
					long first = sorted.get(0);
					long second = sorted.get(1);
					// リヴァイアサンは一言で表せないので組み合わせごとに分ける
					if (first == TETHER_LEVIATHAN && second == TETHER_IFRIT) {
						s.updateCall(releaseLeviIfrit, e1);
					}
					else if (first == TETHER_LEVIATHAN && second == TETHER_RAMUH) {
						s.updateCall(releaseLeviRamuh, e1);
					}
					else if (first == TETHER_LEVIATHAN && second == TETHER_GARUDA) {
						s.updateCall(releaseLeviGaruda, e1);
					}
					else {
						s.setParam("safe1", primalSafeSpot(first));
						s.setParam("safe2", primalSafeSpot(second));
						s.updateCall(releaseCombined, e1);
					}
				}
				s.waitMs(4_500);
				tethers.clear();
			});

}
