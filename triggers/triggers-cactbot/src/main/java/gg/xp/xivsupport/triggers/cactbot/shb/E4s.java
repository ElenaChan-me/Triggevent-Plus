package gg.xp.xivsupport.triggers.cactbot.shb;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.Job;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/raid/e4s.ts をもとに移植
@CalloutRepo(name = "Edens Gate Sepulture Savage", duty = KnownDuty.EdensGateSepultureSavage)
public class E4s extends AutoChildEventHandler implements FilteredEventHandler {

	// 岩の位置は x, y ともに 86 / 100 / 114 のいずれか
	private static final double ARENA_CENTER = 100;
	private static final double MIDDLE_TOLERANCE = 5;
	private static final int GAOL_COUNT = 2;
	// これだけ記録が溜まっていれば3回目なので、残りは1か所に決まる
	private static final int PLATE_FRACTURE_THIRD = 2;
	private static final int PLATE_FRACTURE_SECOND = 1;

	private enum Phase {
		NONE,
		LANDSLIDE,
		ARMOR
	}

	private enum Plate {
		FRONT_LEFT,
		FRONT_RIGHT,
		BACK_LEFT,
		BACK_RIGHT
	}

	private XivState state;

	public E4s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.EdensGateSepultureSavage);
	}

	private Phase phase = Phase.NONE;
	private boolean printedBury;
	private final List<Plate> plateFractures = new ArrayList<>();
	private final List<XivCombatant> gaolPlayers = new ArrayList<>();

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		phase = Phase.NONE;
		printedBury = false;
		plateFractures.clear();
		gaolPlayers.clear();
	}

	private final ModifiableCallout<AbilityUsedEvent> massiveLandslideFront = new ModifiableCallout<>("Massive Landslide: Front", "Landslide: In Front");

	@HandleEvents
	public void phaseChange(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x40E6)) {
			phase = Phase.LANDSLIDE;
			printedBury = false;
			if (event.isFirstTarget()) {
				context.accept(massiveLandslideFront.getModified(event));
			}
		}
		else if (event.abilityIdMatches(0x40E7, 0x40E9)) {
			phase = Phase.ARMOR;
			printedBury = false;
		}
	}

	@NpcCastCallout(0x4116)
	private final ModifiableCallout<AbilityCastStart> stonecrusher = ModifiableCallout.durationBasedCall("Stonecrusher", "Buster on {event.target}");

	@PlayerHeadmarker(0xB9)
	private final ModifiableCallout<HeadMarkerEvent> pulseOfTheLand = new ModifiableCallout<>("Pulse of the Land", "Yellow Spread");

	@NpcCastCallout(value = 0x410C, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> evilEarth = ModifiableCallout.durationBasedCall("Evil Earth", "Look for Evil Earth Marker");

	@PlayerHeadmarker(0xBA)
	private final ModifiableCallout<HeadMarkerEvent> forceOfTheLand = new ModifiableCallout<>("Force of the Land", "Orange Stack");

	@NpcCastCallout(0x4114)
	private final ModifiableCallout<AbilityCastStart> voiceOfTheLand = ModifiableCallout.durationBasedCall("Voice of the Land", "Raidwide");

	@NpcCastCallout(0x4113)
	private final ModifiableCallout<AbilityCastStart> geocrush = ModifiableCallout.durationBasedCall("Geocrush", "Knockback");

	private final ModifiableCallout<AbilityUsedEvent> massiveLandslideSides = new ModifiableCallout<>("Massive Landslide: Sides", "Sides");
	private final ModifiableCallout<AbilityUsedEvent> faultLineSides = new ModifiableCallout<>("Fault Line: Sides", "Wheels: On Sides");
	private final ModifiableCallout<AbilityUsedEvent> faultLineFront = new ModifiableCallout<>("Fault Line: Front", "Tank Charge");

	@HandleEvents
	public void titanAbilities(EventContext context, AbilityUsedEvent event) {
		if (!event.isFirstTarget()) {
			return;
		}
		if (event.abilityIdMatches(0x4117)) {
			context.accept(massiveLandslideSides.getModified(event));
		}
		else if (event.abilityIdMatches(0x40E8)) {
			context.accept(faultLineSides.getModified(event));
		}
		else if (event.abilityIdMatches(0x411F)) {
			context.accept(faultLineFront.getModified(event));
		}
	}

	@NpcCastCallout(0x411A)
	private final ModifiableCallout<AbilityCastStart> landslide = ModifiableCallout.durationBasedCall("Landslide", "Back Corners");

	@PlayerHeadmarker(0x17)
	private final ModifiableCallout<HeadMarkerEvent> crumblingDown = new ModifiableCallout<>("Crumbling Down", "Bomb on You");

	private final ModifiableCallout<AbilityUsedEvent> buryHideEast = new ModifiableCallout<>("Bury: Hide Behind East", "Hide Behind East", 7_000);
	private final ModifiableCallout<AbilityUsedEvent> buryHideWest = new ModifiableCallout<>("Bury: Hide Behind West", "Hide Behind West", 7_000);
	private final ModifiableCallout<AbilityUsedEvent> buryCardinals = new ModifiableCallout<>("Bury: Cardinals First", "Go Cardinals First", 7_000);
	private final ModifiableCallout<AbilityUsedEvent> buryCorners = new ModifiableCallout<>("Bury: Corners First", "Go Corners First", 7_000);

	@HandleEvents
	public void buryDirections(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x4142) || printedBury) {
			return;
		}
		XivCombatant source = event.getSource();
		Position pos = source.getPos();
		if (pos == null) {
			pos = state.getLatestCombatantData(source).getPos();
		}
		if (pos == null) {
			return;
		}
		double x = pos.x();
		double y = pos.y();
		if (phase == Phase.ARMOR) {
			// 縦一列の岩が3本落ちるので、端の列の反対側に隠れる
			if (x < ARENA_CENTER - MIDDLE_TOLERANCE) {
				printedBury = true;
				context.accept(buryHideEast.getModified(event));
			}
			else if (x > ARENA_CENTER + MIDDLE_TOLERANCE) {
				printedBury = true;
				context.accept(buryHideWest.getModified(event));
			}
		}
		else if (phase == Phase.LANDSLIDE) {
			boolean xMiddle = Math.abs(x - ARENA_CENTER) < MIDDLE_TOLERANCE;
			boolean yMiddle = Math.abs(y - ARENA_CENTER) < MIDDLE_TOLERANCE;
			// 中央の岩が先に来ることがあるので、それでは判定しない
			if (xMiddle && yMiddle) {
				return;
			}
			printedBury = true;
			// 先に落ちた側と逆の位置が安置になる
			context.accept((!xMiddle && !yMiddle ? buryCardinals : buryCorners).getModified(event));
		}
	}

	@NpcCastCallout(0x4121)
	private final ModifiableCallout<AbilityCastStart> magnitude = ModifiableCallout.durationBasedCall("Magnitude 5.0", "Get Under");

	@NpcCastCallout(0x4124)
	private final ModifiableCallout<AbilityCastStart> earthenFury = ModifiableCallout.durationBasedCall("Earthen Fury", "Big Raidwide");

	@HandleEvents
	public void earthenFuryReset(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x4124)) {
			plateFractures.clear();
		}
	}

	@NpcCastCallout(0x413A)
	private final ModifiableCallout<AbilityCastStart> earthenFuryBleed = ModifiableCallout.durationBasedCall("Earthen Fury (Bleed)", "Raidwide with Bleed");

	@NpcCastCallout(0x412F)
	private final ModifiableCallout<AbilityCastStart> earthenFistLeftRight = ModifiableCallout.durationBasedCall("Earthen Fist (Left/Right)", "Left, then Right");

	@NpcCastCallout(0x4130)
	private final ModifiableCallout<AbilityCastStart> earthenFistRightLeft = ModifiableCallout.durationBasedCall("Earthen Fist (Right/Left)", "Right, then Left");

	@NpcCastCallout(0x4131)
	private final ModifiableCallout<AbilityCastStart> earthenFistLeftLeft = ModifiableCallout.durationBasedCall("Earthen Fist (2x Left)", "Left, Stay Left");

	@NpcCastCallout(0x4132)
	private final ModifiableCallout<AbilityCastStart> earthenFistRightRight = ModifiableCallout.durationBasedCall("Earthen Fist (2x Right)", "Right, Stay Right");

	@NpcCastCallout(0x4135)
	private final ModifiableCallout<AbilityCastStart> dualEarthenFists = ModifiableCallout.durationBasedCall("Dual Earthen Fists", "Knockback");

	@PlayerHeadmarker(0xBB)
	private final ModifiableCallout<HeadMarkerEvent> weightOfTheWorld = new ModifiableCallout<>("Weight of the World", "Blue Weight");

	private final ModifiableCallout<HeadMarkerEvent> megalithAvoid = new ModifiableCallout<>("Megalith: Non-Tank", "Avoid Tank Cleave");
	private final ModifiableCallout<HeadMarkerEvent> megalithOnYou = new ModifiableCallout<>("Megalith: On You", "Shared Buster on You");
	private final ModifiableCallout<HeadMarkerEvent> megalithOther = new ModifiableCallout<>("Megalith: On Other Tank", "Shared Buster on {event.target}");

	@HandleEvents
	public void megalith(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(0x5D)) {
			return;
		}
		if (!state.playerJobMatches(Job::isTank)) {
			context.accept(megalithAvoid.getModified(event));
		}
		else if (event.getTarget().isThePlayer()) {
			context.accept(megalithOnYou.getModified(event));
		}
		else {
			context.accept(megalithOther.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> graniteGaol = new ModifiableCallout<>("Granite Gaol", "Gaol on You with {partner}");

	@HandleEvents
	public void graniteGaol(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(0xBF)) {
			return;
		}
		gaolPlayers.add(event.getTarget());
		if (gaolPlayers.size() != GAOL_COUNT) {
			return;
		}
		gaolPlayers.stream()
				.filter(cbt -> !cbt.isThePlayer())
				.findFirst()
				.filter(partner -> gaolPlayers.stream().anyMatch(XivCombatant::isThePlayer))
				.ifPresent(partner -> context.accept(graniteGaol.getModified(event, Map.<String, Object>of("partner", partner))));
	}

	private final ModifiableCallout<AbilityCastStart> plateLeft = new ModifiableCallout<>("Plate Fracture: Left", "Left", 6_000);
	private final ModifiableCallout<AbilityCastStart> plateRight = new ModifiableCallout<>("Plate Fracture: Right", "Right", 6_000);
	private final ModifiableCallout<AbilityCastStart> plateFront = new ModifiableCallout<>("Plate Fracture: Front", "Front", 6_000);
	private final ModifiableCallout<AbilityCastStart> plateBack = new ModifiableCallout<>("Plate Fracture: Back", "Back", 6_000);
	private final ModifiableCallout<AbilityCastStart> plateLeftOrBack = new ModifiableCallout<>("Plate Fracture: Left or Back", "Left (or Back)", 6_000);
	private final ModifiableCallout<AbilityCastStart> plateLeftOrFront = new ModifiableCallout<>("Plate Fracture: Left or Front", "Left (or Front)", 6_000);
	private final ModifiableCallout<AbilityCastStart> plateRightOrFront = new ModifiableCallout<>("Plate Fracture: Right or Front", "Right (or Front)", 6_000);
	private final ModifiableCallout<AbilityCastStart> plateRightOrBack = new ModifiableCallout<>("Plate Fracture: Right or Back", "Right (or Back)", 6_000);

	@HandleEvents
	public void plateFracture(EventContext context, AbilityCastStart event) {
		Plate plate;
		if (event.abilityIdMatches(0x4125)) {
			plate = Plate.FRONT_RIGHT;
		}
		else if (event.abilityIdMatches(0x4126)) {
			plate = Plate.BACK_RIGHT;
		}
		else if (event.abilityIdMatches(0x4127)) {
			plate = Plate.BACK_LEFT;
		}
		else if (event.abilityIdMatches(0x4128)) {
			plate = Plate.FRONT_LEFT;
		}
		else {
			return;
		}
		int count = plateFractures.size();
		Plate last = count == 0 ? null : plateFractures.get(count - 1);
		// 崩れた床の並びから残る足場を絞り込む
		ModifiableCallout<AbilityCastStart> call = switch (plate) {
			case FRONT_RIGHT -> {
				if (count == PLATE_FRACTURE_THIRD || last == Plate.BACK_RIGHT) {
					yield plateLeft;
				}
				if (count == PLATE_FRACTURE_SECOND || last == Plate.FRONT_LEFT) {
					yield plateBack;
				}
				yield plateLeftOrBack;
			}
			case BACK_RIGHT -> {
				if (count == PLATE_FRACTURE_THIRD || last == Plate.FRONT_RIGHT) {
					yield plateLeft;
				}
				if (count == PLATE_FRACTURE_SECOND || last == Plate.BACK_LEFT) {
					yield plateFront;
				}
				yield plateLeftOrFront;
			}
			case BACK_LEFT -> {
				if (count == PLATE_FRACTURE_THIRD || last == Plate.FRONT_LEFT) {
					yield plateRight;
				}
				if (count == PLATE_FRACTURE_SECOND || last == Plate.BACK_RIGHT) {
					yield plateFront;
				}
				yield plateRightOrFront;
			}
			case FRONT_LEFT -> {
				if (count == PLATE_FRACTURE_THIRD || last == Plate.BACK_LEFT) {
					yield plateRight;
				}
				if (count == PLATE_FRACTURE_SECOND || last == Plate.FRONT_RIGHT) {
					yield plateBack;
				}
				yield plateRightOrBack;
			}
		};
		plateFractures.add(plate);
		context.accept(call.getModified(event));
	}

	@NpcCastCallout(0x412A)
	private final ModifiableCallout<AbilityCastStart> tumult = ModifiableCallout.durationBasedCall("Tumult", "Raidwide");

}
