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
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.models.ArenaPos;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;
import org.jetbrains.annotations.Nullable;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/trial/asura.ts をもとに移植
@CalloutRepo(name = "The Gilded Araya", duty = KnownDuty.TheGildedAraya)
public class Asura extends AutoChildEventHandler implements FilteredEventHandler {

	private static final double CENTER_X = 100;
	private static final double CENTER_Y = 100;
	private static final ArenaPos arena = new ArenaPos(CENTER_X, CENTER_Y, 5, 5);
	// 最初の3回は像が北に固定で、それ以降は像が跳んでから発動する
	private static final int FIXED_IMAGE_EXECUTIONS = 3;
	// 像の跳躍は位置の更新でしか分からないので、一定間隔で位置を見に行く
	private static final int IMAGE_POLL_MS = 200;
	private static final int IMAGE_POLL_LIMIT = 75;
	private static final double IMAGE_MOVED_DISTANCE = 1;

	private enum Iconography {
		OUT,
		IN,
		SIDES
	}

	private XivState state;

	public Asura(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheGildedAraya);
	}

	private int iconicExecutionCount;
	private @Nullable Long asuraImageId;
	private @Nullable Iconography storedIconMech;
	private @Nullable ArenaSector khadgaLc2Side;

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		iconicExecutionCount = 0;
		asuraImageId = null;
		storedIconMech = null;
		khadgaLc2Side = null;
	}

	@NpcCastCallout(0x8CA1)
	private final ModifiableCallout<AbilityCastStart> lowerRealm = ModifiableCallout.durationBasedCall("Lower Realm", "Raidwide");

	@NpcCastCallout(0x8CA0)
	private final ModifiableCallout<AbilityCastStart> cuttingJewel = ModifiableCallout.durationBasedCall("Cutting Jewel", "Tank Cleave");

	@NpcAbilityUsedCallout(value = 0x8C96, suppressMs = 2_000)
	private final ModifiableCallout<AbilityUsedEvent> ephemerality = new ModifiableCallout<>("Ephemerality", "Avoid Clones");

	@HandleEvents
	public void trackers(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x8CB1) && event.isFirstTarget()) {
			iconicExecutionCount++;
		}
		else if (event.abilityIdMatches(0x8C80)) {
			asuraImageId = event.getSource().getId();
		}
	}

	private final ModifiableCallout<AbilityCastStart> pedestalPurge = ModifiableCallout.durationBasedCall("Pedestal Purge", "Out, Away from Image");
	private final ModifiableCallout<AbilityCastStart> pedestalPurgeJump = ModifiableCallout.durationBasedCall("Pedestal Purge (Jump)", "Out, Away from Image After Jump");
	private final ModifiableCallout<AbilityCastStart> wheelOfDeincarnation = ModifiableCallout.durationBasedCall("Wheel of Deincarnation", "In, Under Image");
	private final ModifiableCallout<AbilityCastStart> wheelOfDeincarnationJump = ModifiableCallout.durationBasedCall("Wheel of Deincarnation (Jump)", "In, Under Image After Jump");
	private final ModifiableCallout<AbilityCastStart> bladewise = ModifiableCallout.durationBasedCall("Bladewise", "Avoid Cleave, Sides of Image");
	private final ModifiableCallout<AbilityCastStart> bladewiseJump = ModifiableCallout.durationBasedCall("Bladewise (Jump)", "Avoid Cleave, Sides of Image After Jump");

	private static @Nullable Iconography iconographyOf(long id) {
		if (id == 0x8C81) {
			return Iconography.OUT;
		}
		if (id == 0x8C83) {
			return Iconography.IN;
		}
		if (id == 0x8C85) {
			return Iconography.SIDES;
		}
		return null;
	}

	@HandleEvents
	public void iconographyCast(EventContext context, AbilityCastStart event) {
		Iconography icon = iconographyOf(event.getAbility().getId());
		if (icon == null) {
			return;
		}
		storedIconMech = icon;
		boolean jump = iconicExecutionCount >= FIXED_IMAGE_EXECUTIONS;
		ModifiableCallout<AbilityCastStart> call = switch (icon) {
			case OUT -> jump ? pedestalPurgeJump : pedestalPurge;
			case IN -> jump ? wheelOfDeincarnationJump : wheelOfDeincarnation;
			case SIDES -> jump ? bladewiseJump : bladewise;
		};
		context.accept(call.getModified(event));
	}

	private final ModifiableCallout<AbilityUsedEvent> iconicInNoJump = new ModifiableCallout<>("Iconic Execution: In (No Jump)", "North, Under Image");
	private final ModifiableCallout<AbilityUsedEvent> iconicOutNoJump = new ModifiableCallout<>("Iconic Execution: Out (No Jump)", "South, Away from Image");
	private final ModifiableCallout<AbilityUsedEvent> iconicSidesNoJump = new ModifiableCallout<>("Iconic Execution: Sides (No Jump)", "East/West, Sides of Image");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> iconicNoJumpSq = SqtTemplates.sq(5_000,
			AbilityUsedEvent.class, aue -> iconographyOf(aue.getAbility().getId()) != null
					&& aue.isFirstTarget()
					&& iconicExecutionCount < FIXED_IMAGE_EXECUTIONS,
			(e1, s) -> {
				// 本体の発動が終わり、像の発動に合うよう少し待つ
				s.waitMs(2_000);
				Iconography icon = storedIconMech;
				storedIconMech = null;
				if (icon == null) {
					return;
				}
				s.updateCall(switch (icon) {
					case IN -> iconicInNoJump;
					case OUT -> iconicOutNoJump;
					case SIDES -> iconicSidesNoJump;
				}, e1);
			});

	private static ArenaSector cardinalOf(Position pos) {
		double dx = pos.getX() - CENTER_X;
		double dy = pos.getY() - CENTER_Y;
		if (Math.abs(dx) > Math.abs(dy)) {
			return dx > 0 ? ArenaSector.EAST : ArenaSector.WEST;
		}
		return dy > 0 ? ArenaSector.SOUTH : ArenaSector.NORTH;
	}

	private @Nullable Position imagePos() {
		Long id = asuraImageId;
		if (id == null) {
			return null;
		}
		XivCombatant image = state.getCombatant(id);
		return image == null ? null : image.getPos();
	}

	private final ModifiableCallout<AbilityCastStart> iconicInJump = new ModifiableCallout<>("Iconic Execution: In (Jump)", "{dir}, Under Image");
	private final ModifiableCallout<AbilityCastStart> iconicOutJump = new ModifiableCallout<>("Iconic Execution: Out (Jump)", "{dir}, Away from Image");
	private final ModifiableCallout<AbilityCastStart> iconicSidesJump = new ModifiableCallout<>("Iconic Execution: Sides (Jump)", "{dir1}/{dir2}, Sides of Image");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> iconicJumpSq = SqtTemplates.sq(20_000,
			AbilityCastStart.class, acs -> iconographyOf(acs.getAbility().getId()) != null
					&& iconicExecutionCount >= FIXED_IMAGE_EXECUTIONS,
			(e1, s) -> {
				Position before = imagePos();
				if (before == null) {
					return;
				}
				Position after = null;
				for (int i = 0; i < IMAGE_POLL_LIMIT; i++) {
					s.waitMs(IMAGE_POLL_MS);
					Position now = imagePos();
					if (now != null && now.distanceFrom2D(before) > IMAGE_MOVED_DISTANCE) {
						after = now;
						break;
					}
				}
				Iconography icon = storedIconMech;
				storedIconMech = null;
				if (after == null || icon == null) {
					return;
				}
				ArenaSector imageDir = cardinalOf(after);
				switch (icon) {
					case IN -> {
						s.setParam("dir", imageDir);
						s.updateCall(iconicInJump, e1);
					}
					case OUT -> {
						s.setParam("dir", imageDir.opposite());
						s.updateCall(iconicOutJump, e1);
					}
					case SIDES -> {
						boolean northSouth = imageDir == ArenaSector.NORTH || imageDir == ArenaSector.SOUTH;
						s.setParam("dir1", northSouth ? ArenaSector.EAST : ArenaSector.NORTH);
						s.setParam("dir2", northSouth ? ArenaSector.WEST : ArenaSector.SOUTH);
						s.updateCall(iconicSidesJump, e1);
					}
				}
			});

	@NpcCastCallout(value = {0x8C90, 0x8C92})
	private final ModifiableCallout<AbilityCastStart> faceOfWrath = ModifiableCallout.durationBasedCall("Face of Wrath", "Stand in Blue Half");

	@NpcCastCallout(value = {0x8C93, 0x8C95})
	private final ModifiableCallout<AbilityCastStart> faceOfDelight = ModifiableCallout.durationBasedCall("Face of Delight", "Stand in Red Half");

	@HandleEvents
	public void khadgaLc2(EventContext context, HeadMarkerEvent event) {
		// 2番目の数字マーカーが付いた人の東西で、六刀の順番が分かる
		if (!event.markerIdMatches(0x1C7)) {
			return;
		}
		XivCombatant target = state.getLatestCombatantData(event.getTarget());
		Position pos = target.getPos();
		if (pos == null) {
			return;
		}
		ArenaSector side = arena.forPosition(pos);
		khadgaLc2Side = (side == ArenaSector.EAST || side == ArenaSector.WEST) ? side : null;
	}

	private final ModifiableCallout<AbilityCastStart> khadgaWest = new ModifiableCallout<>("Six-bladed Khadga (West Pattern)", "SE x2, SW x2, East, West", 19_500);
	private final ModifiableCallout<AbilityCastStart> khadgaEast = new ModifiableCallout<>("Six-bladed Khadga (East Pattern)", "SW x2, SE x2, West, East", 19_500);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> khadgaSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x8C88),
			(e1, s) -> {
				// マーカーが出るまで待つ
				s.waitMs(4_500);
				ArenaSector side = khadgaLc2Side;
				khadgaLc2Side = null;
				if (side == ArenaSector.WEST) {
					s.updateCall(khadgaWest, e1);
				}
				else if (side == ArenaSector.EAST) {
					s.updateCall(khadgaEast, e1);
				}
			});

}
