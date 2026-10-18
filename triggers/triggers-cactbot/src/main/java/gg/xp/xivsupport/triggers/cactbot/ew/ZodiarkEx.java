package gg.xp.xivsupport.triggers.cactbot.ew;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.callouts.RawModifiedCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.MapEffectEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/trial/zodiark-ex.ts をもとに移植
@CalloutRepo(name = "The Minstrels Ballad Zodiarks Fall", duty = KnownDuty.TheMinstrelsBalladZodiarksFall)
public class ZodiarkEx extends AutoChildEventHandler implements FilteredEventHandler {

	private static final double CENTER = 100;
	// 中央線上の魔法陣を判定するための許容幅
	private static final double CENTER_TOLERANCE = 1;
	private static final double FRONT_MIDDLE_HALF_WIDTH = 10;
	private static final int STYX_START = 6;
	private static final int STYX_MAX = 9;

	// マップエフェクトの位置
	private static final long LOC_FIREBAR = 0x05;
	private static final long LOC_ECLIPSE_FIRST = 0x06;
	private static final long LOC_ECLIPSE_LAST = 0x08;
	private static final long LOC_BEHEMOTH_NW = 0x09;
	private static final long LOC_BEHEMOTH_NE = 0x0A;
	private static final long LOC_BEHEMOTH_SW = 0x0B;
	private static final long LOC_BEHEMOTH_SE = 0x0C;
	private static final long LOC_SNAKES_N_OUT_W = 0x0D;
	private static final long LOC_SNAKES_N_OUT_E = 0x0E;
	private static final long LOC_SNAKES_S_OUT_W = 0x0F;
	private static final long LOC_SNAKES_S_OUT_E = 0x10;
	private static final long LOC_SNAKES_W_OUT_N = 0x11;
	private static final long LOC_SNAKES_W_OUT_S = 0x12;
	private static final long LOC_SNAKES_E_OUT_N = 0x13;
	private static final long LOC_SNAKES_E_OUT_S = 0x14;
	private static final long LOC_BIRD_NW = 0x15;
	private static final long LOC_BIRD_NE = 0x16;
	private static final long LOC_BIRD_SW = 0x17;
	private static final long LOC_BIRD_SE = 0x18;

	private static final long ANIMAL_APPEAR_FLAGS = 0x00200010;
	private static final long FIREBAR_SW_TO_NE = 0x00020001;
	private static final long FIREBAR_SE_TO_NW = 0x00400020;

	// アストラルエクリプスの穴のフラグ
	private static final long ECLIPSE_A = 0x00020001;
	private static final long ECLIPSE_B = 0x00200010;
	private static final long ECLIPSE_C = 0x10000800;
	private static final long ECLIPSE_D = 0x00800040;

	private static final int SIGIL_GREEN = 0x67E4;
	private static final int SIGIL_RED = 0x67E5;
	private static final int SIGIL_BLUE = 0x67E6;

	private static final Map<Long, Long> CLOCKWISE = Map.ofEntries(
			Map.entry(LOC_BEHEMOTH_NW, LOC_BEHEMOTH_NE),
			Map.entry(LOC_BEHEMOTH_NE, LOC_BEHEMOTH_SE),
			Map.entry(LOC_BEHEMOTH_SW, LOC_BEHEMOTH_NW),
			Map.entry(LOC_BEHEMOTH_SE, LOC_BEHEMOTH_SW),
			Map.entry(LOC_SNAKES_N_OUT_W, LOC_SNAKES_E_OUT_N),
			Map.entry(LOC_SNAKES_N_OUT_E, LOC_SNAKES_E_OUT_S),
			Map.entry(LOC_SNAKES_S_OUT_W, LOC_SNAKES_W_OUT_N),
			Map.entry(LOC_SNAKES_S_OUT_E, LOC_SNAKES_W_OUT_S),
			Map.entry(LOC_SNAKES_W_OUT_N, LOC_SNAKES_N_OUT_E),
			Map.entry(LOC_SNAKES_W_OUT_S, LOC_SNAKES_N_OUT_W),
			Map.entry(LOC_SNAKES_E_OUT_N, LOC_SNAKES_S_OUT_E),
			Map.entry(LOC_SNAKES_E_OUT_S, LOC_SNAKES_S_OUT_W),
			Map.entry(LOC_BIRD_NW, LOC_BIRD_NE),
			Map.entry(LOC_BIRD_NE, LOC_BIRD_SE),
			Map.entry(LOC_BIRD_SW, LOC_BIRD_NW),
			Map.entry(LOC_BIRD_SE, LOC_BIRD_SW));

	// 反時計回りは時計回りの逆写像になる
	private static final Map<Long, Long> COUNTERCLOCKWISE = invert(CLOCKWISE);

	private static Map<Long, Long> invert(Map<Long, Long> map) {
		Map<Long, Long> out = new HashMap<>();
		map.forEach((k, v) -> out.put(v, k));
		return Map.copyOf(out);
	}

	private XivState state;

	public ZodiarkEx(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheMinstrelsBalladZodiarksFall);
	}

	private int paradeigmaCounter;
	private boolean seenAdikia;
	private int styxCount = STYX_START;
	private final List<Long> eclipseFlags = new ArrayList<>();
	private int eclipseExplosionCount;
	private final List<MapEffectEvent> paradeigmaCollect = new ArrayList<>();
	private final List<AbilityCastStart> frontSigils = new ArrayList<>();
	private @Nullable ArenaSector lastSigilDir;
	private @Nullable Boolean prevGreenWasSides;
	private boolean eclipseInitialCalled;
	private boolean eclipseInstructionsCalled;

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		paradeigmaCounter = 0;
		seenAdikia = false;
		styxCount = STYX_START;
		eclipseFlags.clear();
		eclipseExplosionCount = 0;
		paradeigmaCollect.clear();
		frontSigils.clear();
		lastSigilDir = null;
		prevGreenWasSides = null;
		eclipseInitialCalled = false;
		eclipseInstructionsCalled = false;
	}

	@NpcCastCallout(0x6B63)
	private final ModifiableCallout<AbilityCastStart> ania = ModifiableCallout.durationBasedCall("Ania", "Buster on {event.target}, Swap");

	@NpcCastCallout(0x6C60)
	private final ModifiableCallout<AbilityCastStart> kokytos = ModifiableCallout.durationBasedCall("Kokytos", "Big Raidwide");

	@NpcCastCallout(0x67F0)
	private final ModifiableCallout<AbilityCastStart> phobos = ModifiableCallout.durationBasedCall("Phobos", "Raidwide with Bleed");

	private final ModifiableCallout<AbilityUsedEvent> paradeigma1 = new ModifiableCallout<>("Paradeigma 1", "Under Northwest Bird");

	@HandleEvents
	public void paradeigma(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x67BF) || !event.isFirstTarget()) {
			return;
		}
		paradeigmaCounter++;
		paradeigmaCollect.clear();
		if (paradeigmaCounter == 1) {
			context.accept(paradeigma1.getModified(event));
		}
	}

	@HandleEvents
	public void mapEffects(EventContext context, MapEffectEvent event) {
		long loc = event.getLocation();
		if (loc == LOC_FIREBAR
				|| (event.flagsMatches(ANIMAL_APPEAR_FLAGS) && loc >= LOC_BEHEMOTH_NW && loc <= LOC_BIRD_SE)) {
			paradeigmaCollect.add(event);
		}
		else if (loc >= LOC_ECLIPSE_FIRST && loc <= LOC_ECLIPSE_LAST) {
			astralEclipse(context, event);
		}
	}

	private List<Long> paradeigmaLocs() {
		return paradeigmaCollect.stream().map(MapEffectEvent::getLocation).toList();
	}

	private final ModifiableCallout<AbilityCastStart> outsideNorth = ModifiableCallout.durationBasedCall("Astral Flow: Outside North", "Outside North");
	private final ModifiableCallout<AbilityCastStart> insideNorth = ModifiableCallout.durationBasedCall("Astral Flow: Inside North", "Inside North");
	private final ModifiableCallout<AbilityCastStart> outsideWest = ModifiableCallout.durationBasedCall("Astral Flow: Outside West", "Outside West");
	private final ModifiableCallout<AbilityCastStart> insideWest = ModifiableCallout.durationBasedCall("Astral Flow: Inside West", "Inside West");
	private final ModifiableCallout<AbilityCastStart> outsideEast = ModifiableCallout.durationBasedCall("Astral Flow: Outside East", "Outside East");
	private final ModifiableCallout<AbilityCastStart> insideEast = ModifiableCallout.durationBasedCall("Astral Flow: Inside East", "Inside East");
	private final ModifiableCallout<AbilityCastStart> astralFlowBird = ModifiableCallout.durationBasedCall("Astral Flow: Bird", "{dir} Bird (Lean {lean})");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> astralFlowSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x6662, 0x6663),
			(e1, s) -> {
				// 火の帯のマップエフェクトが詠唱より遅れて届くことがある
				if (paradeigmaCounter != 3) {
					s.waitMs(1_000);
				}
				boolean clockwise = e1.abilityIdMatches(0x6662);
				Map<Long, Long> rotation = clockwise ? CLOCKWISE : COUNTERCLOCKWISE;
				List<Long> locs = paradeigmaLocs().stream().map(loc -> rotation.getOrDefault(loc, loc)).toList();
				Long firebarFlags = paradeigmaCollect.stream()
						.filter(me -> me.getLocation() == LOC_FIREBAR)
						.map(MapEffectEvent::getFlags)
						.findFirst()
						.orElse(null);
				ArenaSector lastSigil = lastSigilDir;
				paradeigmaCollect.clear();
				lastSigilDir = null;

				boolean outsideNorthBad = locs.contains(LOC_SNAKES_W_OUT_N) || locs.contains(LOC_SNAKES_E_OUT_N);
				boolean outsideSouthBad = locs.contains(LOC_SNAKES_W_OUT_S) || locs.contains(LOC_SNAKES_E_OUT_S);
				boolean outsideWestBad = locs.contains(LOC_SNAKES_N_OUT_W) || locs.contains(LOC_SNAKES_S_OUT_W);
				boolean outsideEastBad = locs.contains(LOC_SNAKES_N_OUT_E) || locs.contains(LOC_SNAKES_S_OUT_E);

				if (paradeigmaCounter == 3 || paradeigmaCounter == 7) {
					ModifiableCallout<AbilityCastStart> call = null;
					if (outsideNorthBad) {
						call = insideNorth;
					}
					else if (outsideSouthBad) {
						call = outsideNorth;
					}
					else if (lastSigil == ArenaSector.WEST && outsideWestBad) {
						call = insideWest;
					}
					else if (lastSigil == ArenaSector.WEST && outsideEastBad) {
						call = outsideWest;
					}
					else if (lastSigil == ArenaSector.EAST && outsideWestBad) {
						call = outsideEast;
					}
					else if (lastSigil == ArenaSector.EAST && outsideEastBad) {
						call = insideEast;
					}
					if (call != null) {
						s.updateCall(call, e1);
					}
					return;
				}

				// 火の帯は南西-北東か南東-北西で、回転後は東西か南北のどちらかを扇で焼く
				boolean firebarEastWestSafe = firebarFlags != null
						&& ((firebarFlags == FIREBAR_SW_TO_NE && !clockwise) || (firebarFlags == FIREBAR_SE_TO_NW && clockwise));

				ArenaSector dir = null;
				String lean = null;
				if (paradeigmaCounter == 5 || paradeigmaCounter == 8) {
					ArenaSector sigil = paradeigmaCounter != 5 ? lastSigil : null;
					if (locs.contains(LOC_BIRD_NW) && (sigil == null || sigil == ArenaSector.WEST || sigil == ArenaSector.NORTH)) {
						dir = ArenaSector.NORTHWEST;
						lean = firebarEastWestSafe ? "SW" : "NE";
					}
					else if (locs.contains(LOC_BIRD_NE) && (sigil == null || sigil == ArenaSector.EAST || sigil == ArenaSector.NORTH)) {
						dir = ArenaSector.NORTHEAST;
						lean = firebarEastWestSafe ? "SE" : "NW";
					}
					else if (locs.contains(LOC_BIRD_SW) && (sigil == null || sigil == ArenaSector.SOUTH || sigil == ArenaSector.WEST)) {
						dir = ArenaSector.SOUTHWEST;
						lean = firebarEastWestSafe ? "NW" : "SE";
					}
					else if (locs.contains(LOC_BIRD_SE) && (sigil == null || sigil == ArenaSector.SOUTH || sigil == ArenaSector.EAST)) {
						dir = ArenaSector.SOUTHEAST;
						lean = firebarEastWestSafe ? "NE" : "SW";
					}
				}
				else if (paradeigmaCounter == 6 || paradeigmaCounter == 9) {
					ArenaSector sigil = paradeigmaCounter == 6 ? ArenaSector.WEST : lastSigil;
					if (outsideNorthBad) {
						if (sigil == ArenaSector.WEST) {
							dir = ArenaSector.NORTHWEST;
							lean = firebarEastWestSafe ? "SW" : "ESE";
						}
						else if (sigil == ArenaSector.EAST) {
							dir = ArenaSector.NORTHEAST;
							lean = firebarEastWestSafe ? "SE" : "WSW";
						}
					}
					else if (outsideSouthBad) {
						if (sigil == ArenaSector.WEST) {
							dir = ArenaSector.NORTHWEST;
							lean = firebarEastWestSafe ? "WNW" : "NE";
						}
						else if (sigil == ArenaSector.EAST) {
							dir = ArenaSector.NORTHEAST;
							lean = firebarEastWestSafe ? "ENE" : "NW";
						}
					}
					else if (outsideWestBad) {
						if (sigil == ArenaSector.WEST) {
							dir = ArenaSector.NORTHWEST;
							lean = firebarEastWestSafe ? "SSE" : "NE";
						}
						else {
							dir = ArenaSector.NORTHEAST;
							lean = firebarEastWestSafe ? "SE" : "NNE";
						}
					}
					else if (outsideEastBad) {
						if (sigil == ArenaSector.WEST) {
							dir = ArenaSector.NORTHWEST;
							lean = firebarEastWestSafe ? "SW" : "NNW";
						}
						else {
							dir = ArenaSector.NORTHEAST;
							lean = firebarEastWestSafe ? "SSW" : "NW";
						}
					}
				}
				if (dir != null) {
					s.setParam("dir", dir);
					s.setParam("lean", lean);
					s.updateCall(astralFlowBird, e1);
				}
			});

	private final ModifiableCallout<AbilityCastStart> styx = ModifiableCallout.durationBasedCall("Styx", "Stack x{num}");

	@HandleEvents
	public void styx(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x67F3)) {
			context.accept(styx.getModified(event, Map.<String, Object>of("num", styxCount)));
			styxCount = Math.min(styxCount + 1, STYX_MAX);
		}
	}

	// 別の扇や縦横の判定と紛らわしいので「レーザー用」と明示する
	private final ModifiableCallout<AbilityUsedEvent> greenLaserMiddle = new ModifiableCallout<>("Green Laser (Second): Middle", "Middle for Laser");
	private final ModifiableCallout<AbilityUsedEvent> greenLaserSides = new ModifiableCallout<>("Green Laser (Second): Sides", "Sides for Laser");
	private final RepeatSuppressor greenLaserSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void greenLaserSecond(EventContext context, AbilityUsedEvent event) {
		Boolean prev = prevGreenWasSides;
		if (!event.abilityIdMatches(SIGIL_GREEN) || prev == null || !greenLaserSupp.check(event)) {
			return;
		}
		context.accept((prev ? greenLaserMiddle : greenLaserSides).getModified(event));
		prevGreenWasSides = null;
	}

	private final ModifiableCallout<TetherEvent> blueConeTether = new ModifiableCallout<>("Blue Cone Tether", "{dir} Cone");
	private final ModifiableCallout<TetherEvent> redBoxTether = new ModifiableCallout<>("Red Box Tether", "{safe}");

	@HandleEvents
	public void sigilTethers(EventContext context, TetherEvent event) {
		boolean blue = event.tetherIdMatches(0xA4);
		if (!blue && !event.tetherIdMatches(0xAB)) {
			return;
		}
		Position pos = state.getLatestCombatantData(event.getTarget()).getPos();
		if (pos == null) {
			return;
		}
		ArenaSector sigilSide;
		if (pos.x() < CENTER - CENTER_TOLERANCE) {
			sigilSide = ArenaSector.WEST;
		}
		else if (pos.x() > CENTER + CENTER_TOLERANCE) {
			sigilSide = ArenaSector.EAST;
		}
		else if (pos.y() < CENTER) {
			sigilSide = ArenaSector.NORTH;
		}
		else {
			sigilSide = ArenaSector.SOUTH;
		}
		if (blue) {
			// 青い扇は魔法陣の側が安全
			lastSigilDir = sigilSide;
			context.accept(blueConeTether.getModified(event, Map.<String, Object>of("dir", sigilSide)));
		}
		else {
			// 赤い箱は魔法陣の反対側が安全
			ArenaSector safe = sigilSide.opposite();
			lastSigilDir = safe;
			context.accept(redBoxTether.getModified(event, Map.<String, Object>of("safe", safe)));
		}
	}

	@HandleEvents
	public void sigilCollect(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(SIGIL_GREEN, SIGIL_RED, SIGIL_BLUE)) {
			return;
		}
		Position pos = event.getSource().getPos();
		if (pos != null && pos.y() < CENTER) {
			frontSigils.add(event);
		}
	}

	private final ModifiableCallout<AbilityCastStart> sigilSides = ModifiableCallout.durationBasedCall("Arcane Sigil: Sides", "Sides for Laser");
	private final ModifiableCallout<AbilityCastStart> sigilMiddle = ModifiableCallout.durationBasedCall("Arcane Sigil: Middle", "Middle for Laser");
	private final ModifiableCallout<AbilityCastStart> sigilSouth = ModifiableCallout.durationBasedCall("Arcane Sigil: South", "South");
	private final ModifiableCallout<AbilityCastStart> sigilNorth = ModifiableCallout.durationBasedCall("Arcane Sigil: North", "North");
	private final ModifiableCallout<AbilityCastStart> sigilFrontSides = ModifiableCallout.durationBasedCall("Arcane Sigil: Front Sides", "Front Sides");
	private final ModifiableCallout<AbilityCastStart> sigilBackMiddle = ModifiableCallout.durationBasedCall("Arcane Sigil: Back Middle", "Back Middle");
	private final ModifiableCallout<AbilityCastStart> sigilFrontMiddle = ModifiableCallout.durationBasedCall("Arcane Sigil: Front Middle", "Front Middle");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> arcaneSigilSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(SIGIL_GREEN, SIGIL_RED, SIGIL_BLUE),
			(e1, s) -> {
				// 同時に出る魔法陣の詠唱が出揃うのを待つ
				s.waitMs(200);
				List<AbilityCastStart> sigils = new ArrayList<>(frontSigils);
				frontSigils.clear();
				if (sigils.isEmpty()) {
					return;
				}
				boolean firstGreen = sigils.get(0).abilityIdMatches(SIGIL_GREEN);
				// 縦横の切り替わりでは、1回目が発動する前に2回目を言わないようにする
				if (prevGreenWasSides != null && firstGreen) {
					return;
				}
				ModifiableCallout<AbilityCastStart> call = null;
				if (sigils.size() == 1) {
					if (firstGreen) {
						prevGreenWasSides = true;
						call = sigilSides;
					}
					else if (sigils.get(0).abilityIdMatches(SIGIL_RED)) {
						call = sigilSouth;
					}
					else {
						call = sigilNorth;
					}
				}
				else if (sigils.size() == 2 && firstGreen && sigils.get(1).abilityIdMatches(SIGIL_GREEN)) {
					prevGreenWasSides = false;
					call = sigilMiddle;
				}
				else if (sigils.size() == 3) {
					for (AbilityCastStart sig : sigils) {
						Position pos = sig.getSource().getPos();
						if (pos == null || Math.abs(pos.x() - CENTER) >= FRONT_MIDDLE_HALF_WIDTH) {
							continue;
						}
						if (sig.abilityIdMatches(SIGIL_GREEN)) {
							call = sigilFrontSides;
						}
						else if (sig.abilityIdMatches(SIGIL_RED)) {
							call = sigilBackMiddle;
						}
						else {
							call = sigilFrontMiddle;
						}
						break;
					}
				}
				if (call != null) {
					s.updateCall(call, sigils.get(0));
				}
			});

	private final ModifiableCallout<AbilityCastStart> algedonCombo = ModifiableCallout.durationBasedCall("Algedon", "Go {first}/{second}");
	private final ModifiableCallout<AbilityCastStart> algedonSingle = ModifiableCallout.durationBasedCall("Algedon (Melee Spot Safe)", "Go {dir} (Lean {lean})");
	private final ModifiableCallout<AbilityCastStart> algedonBird = ModifiableCallout.durationBasedCall("Algedon (Paradeigma 2)", "{dir} Bird (Lean {lean})");

	@HandleEvents
	public void algedon(EventContext context, AbilityCastStart event) {
		// 67EC は左寄り、67ED は右寄り
		boolean left = event.abilityIdMatches(0x67EC);
		if (!left && !event.abilityIdMatches(0x67ED)) {
			return;
		}
		if (paradeigmaCounter == 2 && !paradeigmaCollect.isEmpty()) {
			List<Long> locs = paradeigmaLocs();
			paradeigmaCollect.clear();
			boolean nwSafe = locs.contains(LOC_BIRD_NW);
			boolean neSafe = locs.contains(LOC_BIRD_NE);
			// 安全側の近接位置に鳥がいればそこだけを案内する
			if (left && neSafe) {
				context.accept(algedonBird.getModified(event, Map.<String, Object>of("dir", ArenaSector.NORTHEAST, "lean", "NE")));
				return;
			}
			if (!left && nwSafe) {
				context.accept(algedonBird.getModified(event, Map.<String, Object>of("dir", ArenaSector.NORTHWEST, "lean", "NW")));
				return;
			}
			if (left && nwSafe) {
				context.accept(combo(event, ArenaSector.NORTHWEST, ArenaSector.SOUTHWEST));
				return;
			}
			if (!left && neSafe) {
				context.accept(combo(event, ArenaSector.NORTHEAST, ArenaSector.SOUTHEAST));
				return;
			}
			// ベヒーモスが両方北にいる場合は通常の案内にする
		}
		// パラデイグマ5と6の間はエソテリコスと同時なので、近接位置が安全ならそこを優先する
		if (paradeigmaCounter == 5) {
			if (lastSigilDir == ArenaSector.EAST) {
				if (left) {
					context.accept(algedonSingle.getModified(event, Map.<String, Object>of("dir", ArenaSector.NORTHEAST, "lean", "NE")));
				}
				else {
					context.accept(combo(event, ArenaSector.NORTHEAST, ArenaSector.SOUTHEAST));
				}
				return;
			}
			if (lastSigilDir == ArenaSector.WEST) {
				if (!left) {
					context.accept(algedonSingle.getModified(event, Map.<String, Object>of("dir", ArenaSector.NORTHWEST, "lean", "NW")));
				}
				else {
					context.accept(combo(event, ArenaSector.NORTHWEST, ArenaSector.SOUTHWEST));
				}
				return;
			}
		}
		if (left) {
			context.accept(combo(event, ArenaSector.NORTHEAST, ArenaSector.SOUTHWEST));
		}
		else {
			context.accept(combo(event, ArenaSector.NORTHWEST, ArenaSector.SOUTHEAST));
		}
	}

	private RawModifiedCallout<AbilityCastStart> combo(AbilityCastStart event, ArenaSector first, ArenaSector second) {
		return algedonCombo.getModified(event, Map.<String, Object>of("first", first, "second", second));
	}

	private final ModifiableCallout<AbilityCastStart> algedonKnockback = ModifiableCallout.durationBasedCall("Algedon Knockback", "Knockback");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> algedonKnockbackSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x67EE),
			(e1, s) -> {
				// アルゲドンの案内と重ならないように遅らせる
				s.waitMs(3_000);
				s.updateCall(algedonKnockback, e1);
			});

	private final ModifiableCallout<AbilityCastStart> adikiaFirst = ModifiableCallout.durationBasedCall("Adikia (First)", "Double Fists, Look for Snakes");
	private final ModifiableCallout<AbilityCastStart> adikiaOutsideNorth = ModifiableCallout.durationBasedCall("Adikia (Outside North)", "Double Fists, Outside North");
	private final ModifiableCallout<AbilityCastStart> adikiaInsideNorth = ModifiableCallout.durationBasedCall("Adikia (Inside North)", "Double Fists, Inside North");
	private final ModifiableCallout<AbilityCastStart> adikia = ModifiableCallout.durationBasedCall("Adikia", "Double Fists");

	@HandleEvents
	public void adikia(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x63A9)) {
			return;
		}
		ModifiableCallout<AbilityCastStart> call = null;
		if (!paradeigmaCollect.isEmpty()) {
			List<Long> locs = paradeigmaLocs();
			paradeigmaCollect.clear();
			if (locs.contains(LOC_SNAKES_W_OUT_S) || locs.contains(LOC_SNAKES_E_OUT_S)) {
				call = adikiaOutsideNorth;
			}
			else if (locs.contains(LOC_SNAKES_W_OUT_N) || locs.contains(LOC_SNAKES_E_OUT_N)) {
				call = adikiaInsideNorth;
			}
		}
		if (call == null) {
			call = seenAdikia ? adikia : adikiaFirst;
		}
		seenAdikia = true;
		context.accept(call.getModified(event));
	}

	@HandleEvents
	public void astralEclipseCleanup(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x67C3)) {
			eclipseFlags.clear();
			eclipseExplosionCount = 0;
			eclipseInitialCalled = false;
			eclipseInstructionsCalled = false;
		}
	}

	// 模様は6通りしかないので、最初の2つから3つ目が決まる
	private static @Nullable Long thirdEclipse(long first, long second) {
		if (first == ECLIPSE_A && second == ECLIPSE_B) {
			return ECLIPSE_D;
		}
		if (first == ECLIPSE_C && second == ECLIPSE_D) {
			return ECLIPSE_A;
		}
		if (first == ECLIPSE_A && second == ECLIPSE_C) {
			return ECLIPSE_B;
		}
		if (first == ECLIPSE_C && second == ECLIPSE_B) {
			return ECLIPSE_D;
		}
		if (first == ECLIPSE_B && second == ECLIPSE_C) {
			return ECLIPSE_D;
		}
		if (first == ECLIPSE_B && second == ECLIPSE_D) {
			return ECLIPSE_A;
		}
		return null;
	}

	private ArenaSector eclipseSafe(int idx) {
		if (idx >= eclipseFlags.size()) {
			return ArenaSector.UNKNOWN;
		}
		long flags = eclipseFlags.get(idx);
		return switch (idx) {
			case 0 -> flags == ECLIPSE_A ? ArenaSector.WEST : ArenaSector.NORTHWEST;
			case 1 -> flags == ECLIPSE_B ? ArenaSector.CENTER : flags == ECLIPSE_C ? ArenaSector.NORTH : ArenaSector.WEST;
			case 2 -> flags == ECLIPSE_B ? ArenaSector.CENTER : flags == ECLIPSE_A ? ArenaSector.NORTH : ArenaSector.NORTHEAST;
			default -> ArenaSector.UNKNOWN;
		};
	}

	private final ModifiableCallout<MapEffectEvent> eclipseInitial = new ModifiableCallout<>("Astral Eclipse: First Safe Spot", "{dir}");
	private final ModifiableCallout<MapEffectEvent> eclipseInstructions = new ModifiableCallout<>("Astral Eclipse: All Safe Spots", "{dir1}, {dir2}, {dir3}", 19_000);
	private final ModifiableCallout<AbilityUsedEvent> eclipseStep = new ModifiableCallout<>("Astral Eclipse: Next Safe Spot", "{dir}");
	private final RepeatSuppressor eclipseStepSupp = new RepeatSuppressor(Duration.ofSeconds(2));

	private void astralEclipse(EventContext context, MapEffectEvent event) {
		// 爆発中にも同じ位置のエフェクトが来るので、最初の2つだけを使う
		if (eclipseFlags.isEmpty()) {
			eclipseFlags.add(event.getFlags());
		}
		else if (eclipseFlags.size() == 1) {
			long second = event.getFlags();
			eclipseFlags.add(second);
			Long third = thirdEclipse(eclipseFlags.get(0), second);
			if (third != null) {
				eclipseFlags.add(third);
			}
		}
		if (!eclipseInitialCalled) {
			eclipseInitialCalled = true;
			context.accept(eclipseInitial.getModified(event, Map.<String, Object>of("dir", eclipseSafe(0))));
		}
		if (eclipseFlags.size() == 3 && !eclipseInstructionsCalled) {
			eclipseInstructionsCalled = true;
			context.accept(eclipseInstructions.getModified(event, Map.<String, Object>of(
					"dir1", eclipseSafe(0),
					"dir2", eclipseSafe(1),
					"dir3", eclipseSafe(2))));
		}
	}

	@HandleEvents
	public void astralEclipseStep(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x67E7) || !eclipseStepSupp.check(event)) {
			return;
		}
		eclipseExplosionCount++;
		if (eclipseExplosionCount >= 3) {
			return;
		}
		context.accept(eclipseStep.getModified(event, Map.<String, Object>of("dir", eclipseSafe(eclipseExplosionCount))));
	}

}
