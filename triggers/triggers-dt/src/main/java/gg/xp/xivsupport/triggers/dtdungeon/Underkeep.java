package gg.xp.xivsupport.triggers.dtdungeon;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.*;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.ActorControlExtraEvent;
import gg.xp.xivsupport.events.actlines.events.MapEffectEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.ArenaPos;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.XivCombatant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/dungeon/the_underkeep.ts をもとに移植
@CalloutRepo(name = "The Underkeep", duty = KnownDuty.Underkeep)
public class Underkeep extends AutoChildEventHandler implements FilteredEventHandler {

	private static final Logger log = LoggerFactory.getLogger(Underkeep.class);

	private static final long mapEffectOnFlags = 0x00020001;

	private static final long laserWest = 0x1F;
	private static final long laserEast = 0x21;
	private static final long laserNorth = 0x23;
	private static final long laserSouth = 0x25;
	private static final Set<Long> laserLocations = Set.of(laserWest, laserEast, laserNorth, laserSouth);

	private static final Set<Long> coordinateBitLocations = Set.of(
			0x0DL, 0x0EL, 0x10L, 0x11L, 0x12L, 0x13L, 0x14L,
			0x15L, 0x16L, 0x17L, 0x18L, 0x19L, 0x1AL, 0x1CL);

	private enum MarchMove {
		AFTER_LASER,
		AFTER_ORB
	}

	private record MarchPattern(ArenaSector first, ArenaSector second, MarchMove move) {
	}

	// 内側4マスだけで2手で避けられる配置を、ビットの初期位置ごとに持っておく
	private static final Map<List<Long>, MarchPattern> marchPatterns = Map.of(
			List.of(0x16L, 0x1AL), new MarchPattern(ArenaSector.SOUTHEAST, ArenaSector.NORTHEAST, null),
			List.of(0x0EL, 0x17L), new MarchPattern(ArenaSector.NORTHWEST, ArenaSector.NORTHEAST, null),
			List.of(0x15L, 0x16L), new MarchPattern(ArenaSector.NORTHWEST, ArenaSector.NORTHEAST, null),
			List.of(0x13L, 0x15L), new MarchPattern(ArenaSector.SOUTHWEST, ArenaSector.NORTHWEST, null),
			List.of(0x0EL, 0x10L, 0x19L), new MarchPattern(ArenaSector.SOUTHEAST, ArenaSector.NORTHEAST, MarchMove.AFTER_LASER),
			List.of(0x11L, 0x16L, 0x18L), new MarchPattern(ArenaSector.NORTHEAST, ArenaSector.SOUTHEAST, MarchMove.AFTER_ORB),
			List.of(0x12L, 0x14L, 0x15L), new MarchPattern(ArenaSector.SOUTHEAST, ArenaSector.NORTHEAST, MarchMove.AFTER_ORB),
			List.of(0x0DL, 0x1AL, 0x1CL), new MarchPattern(ArenaSector.NORTHEAST, ArenaSector.SOUTHEAST, MarchMove.AFTER_LASER));

	private XivState state;

	public Underkeep(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.Underkeep);
	}

	private final List<Long> turretLocations = new ArrayList<>();
	private final Map<Long, XivCombatant> tetherMap = new HashMap<>();
	private final List<Long> orbLocations = new ArrayList<>();
	private MarchMove marchMove;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		turretLocations.clear();
		tetherMap.clear();
		orbLocations.clear();
		marchMove = null;
	}

	@HandleEvents
	public void mapEffects(EventContext context, MapEffectEvent event) {
		if (!event.flagsMatches(mapEffectOnFlags)) {
			return;
		}
		long location = event.getLocation();
		if (laserLocations.contains(location)) {
			turretLocations.add(location);
		}
		else if (coordinateBitLocations.contains(location)) {
			orbLocations.add(location);
		}
	}

	// 1ボス目（Gargant）

	@NpcCastCallout(0xA633)
	private final ModifiableCallout<AbilityCastStart> chillingChirp = ModifiableCallout.durationBasedCall("Chilling Chirp", "Raidwide");

	@NpcCastCallout(0xA632)
	private final ModifiableCallout<AbilityCastStart> almightyRacket = ModifiableCallout.durationBasedCall("Almighty Racket", "Behind");

	private final ModifiableCallout<ActorControlExtraEvent> gargantUntargetable = new ModifiableCallout<>("Gargant: Untargetable", "Watch Boss for Dash");

	// 姿を消してから突進してくるので、消えた時点で知らせる
	@HandleEvents
	public void gargantUntargetable(EventContext context, ActorControlExtraEvent event) {
		if (event.getCategory() == 0x3F && event.getData0() == 0x1C) {
			context.accept(gargantUntargetable.getModified(event));
		}
	}

	@NpcCastCallout(value = 0xA898, onYou = true)
	private final ModifiableCallout<AbilityCastStart> sedimentaryDebris = ModifiableCallout.durationBasedCall("Sedimentary Debris", "Spread");

	@NpcCastCallout(0xA634)
	private final ModifiableCallout<AbilityCastStart> trapJaws = ModifiableCallout.durationBasedCall("Trap Jaws", "Buster on {event.target}");

	// 2ボス目（Soldier S0）

	@NpcCastCallout(0xA653)
	private final ModifiableCallout<AbilityCastStart> fieldOfScorn = ModifiableCallout.durationBasedCall("Field of Scorn", "Raidwide");

	@NpcCastCallout(0xA880)
	private final ModifiableCallout<AbilityCastStart> thunderousSlash = ModifiableCallout.durationBasedCall("Thunderous Slash", "Buster on {event.target}");

	private final ModifiableCallout<AbilityCastStart> orderedFire = ModifiableCallout.durationBasedCall("Ordered Fire", "Spread in {quad} Quadrant");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> orderedFireSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xA64C),
			(e1, s) -> {
				// 砲台のマップエフェクトが詠唱とほぼ同時に来るので少し待つ
				s.waitMs(500);
				boolean north = turretLocations.contains(laserNorth);
				boolean south = turretLocations.contains(laserSouth);
				boolean east = turretLocations.contains(laserEast);
				boolean west = turretLocations.contains(laserWest);
				ArenaSector quad = ArenaSector.UNKNOWN;
				if (north && east) {
					quad = ArenaSector.SOUTHWEST;
				}
				else if (north && west) {
					quad = ArenaSector.SOUTHEAST;
				}
				else if (south && east) {
					quad = ArenaSector.NORTHWEST;
				}
				else if (south && west) {
					quad = ArenaSector.NORTHEAST;
				}
				turretLocations.clear();
				s.setParam("quad", quad);
				s.updateCall(orderedFire, e1);
			});

	@HandleEvents
	public void sectorBisectorTethers(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x0147)) {
			tetherMap.put(event.getSource().getId(), event.getTarget());
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> sectorBisector = new ModifiableCallout<>("Sector Bisector", "{safe} Half Safe");

	private static final int sectorBisectorSuppressMs = 8_000;

	// 分身が消える時の技で、最初に消えた分身から線をたどった先が最後に薙ぎ払う分身になる
	@AutoFeed
	private final SequentialTrigger<BaseEvent> sectorBisectorSq = SqtTemplates.sq(15_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0xA89B, 0xA89C),
			(e1, s) -> {
				XivCombatant lastClone = tetherMap.get(e1.getSource().getId());
				if (lastClone == null) {
					log.error("Sector Bisector: could not find last clone for {}", e1.getSource());
				}
				else {
					s.waitThenRefreshCombatants(100);
					ArenaSector facing = ArenaPos.combatantFacing(state.getLatestCombatantData(lastClone));
					// A89B は左側、A89C は右側を薙ぎ払う
					boolean cleavingLeft = e1.abilityIdMatches(0xA89B);
					s.setParam("safe", facing.plusEighths(cleavingLeft ? 2 : -2));
					tetherMap.clear();
					s.updateCall(sectorBisector, e1);
				}
				s.waitMs(sectorBisectorSuppressMs);
			});

	// 3ボス目（Valia Pira）

	private final ModifiableCallout<AbilityCastStart> entropicSphere = ModifiableCallout.durationBasedCall("Entropic Sphere", "Raidwide");

	@HandleEvents
	public void entropicSphere(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0xA61D)) {
			orbLocations.clear();
			context.accept(entropicSphere.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> electray = ModifiableCallout.durationBasedCall("Electray", "Dodge Wall Turrets");

	private final RepeatSuppressor electraySupp = new RepeatSuppressor(Duration.ofSeconds(2));

	@HandleEvents
	public void electray(EventContext context, AbilityCastStart event) {
		// 座標行進中は避け方の指示を優先するので出さない
		if (event.abilityIdMatches(0xA87A) && marchMove == null && electraySupp.check(event)) {
			context.accept(electray.getModified(event));
		}
	}

	@NpcCastCallout(0xA617)
	private final ModifiableCallout<AbilityCastStart> electricField = ModifiableCallout.durationBasedCall("Electric Field", "Spread, Cones Expand");

	@NpcCastCallout(0xA6F2)
	private final ModifiableCallout<AbilityCastStart> neutralizeFrontLines = ModifiableCallout.durationBasedCall("Neutralize Front Lines", "Behind, Spread");

	private final ModifiableCallout<AbilityCastStart> deterrentPulse = ModifiableCallout.durationBasedCall("Deterrent Pulse", "Line Stack on {event.target}");

	@HandleEvents
	public void deterrentPulse(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0xA62C) && !event.getTarget().isThePlayer()) {
			context.accept(deterrentPulse.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> coordinateMarch = ModifiableCallout.durationBasedCall("Coordinate March", "Inner {first} then Inner {second}");
	private final ModifiableCallout<AbilityCastStart> coordinateMarchAfterLaser = ModifiableCallout.durationBasedCall("Coordinate March (Move after Wall Laser)", "Inner {first} then Inner {second} after Wall Laser");
	private final ModifiableCallout<AbilityCastStart> coordinateMarchAfterOrb = ModifiableCallout.durationBasedCall("Coordinate March (Move after Orb Explosion)", "Inner {first} then Inner {second} after Orb Explosion");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> coordinateMarchSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xA611),
			(e1, s) -> {
				s.waitMs(500);
				// 動かせるビットもあるが、判定に使うのは出現時の位置だけ
				List<Long> bits = orbLocations.stream().sorted().toList();
				orbLocations.clear();
				if (bits.size() != 2 && bits.size() != 3) {
					log.error("Coordinate March: unexpected amount of bits {}", bits);
					return;
				}
				MarchPattern pattern = marchPatterns.get(bits);
				if (pattern == null) {
					log.error("Coordinate March: unknown pattern {}", bits);
					return;
				}
				marchMove = pattern.move();
				s.setParam("first", pattern.first());
				s.setParam("second", pattern.second());
				if (pattern.move() == MarchMove.AFTER_LASER) {
					s.updateCall(coordinateMarchAfterLaser, e1);
				}
				else if (pattern.move() == MarchMove.AFTER_ORB) {
					s.updateCall(coordinateMarchAfterOrb, e1);
				}
				else {
					s.updateCall(coordinateMarch, e1);
				}
			});

	private final ModifiableCallout<AbilityUsedEvent> electrayMove = new ModifiableCallout<>("Coordinate March: Move after Wall Laser", "Move Away");
	private final ModifiableCallout<AbilityUsedEvent> enforcementRayMove = new ModifiableCallout<>("Coordinate March: Move after Orb Explosion", "Move Away");

	@HandleEvents
	public void marchMoves(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0xA87A) && marchMove == MarchMove.AFTER_LASER) {
			marchMove = null;
			context.accept(electrayMove.getModified(event));
		}
		else if (event.abilityIdMatches(0xA6F1) && marchMove == MarchMove.AFTER_ORB) {
			marchMove = null;
			context.accept(enforcementRayMove.getModified(event));
		}
	}

}
