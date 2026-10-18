package gg.xp.xivsupport.triggers.cactbot.dt;

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
import gg.xp.xivsupport.events.actlines.events.ActorControlExtraEvent;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.models.Position;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/raid/r12n.ts をもとに移植
@CalloutRepo(name = "Aac Heavyweight M4", duty = KnownDuty.M12N)
public class R12n extends AutoChildEventHandler implements FilteredEventHandler {

	private static final double CENTER_X = 100;
	// 床フェーズの頭の位置を外西・内西・内東・外東に分ける境界
	private static final double TILE_OUTER_OFFSET = 10;
	private static final int HEAD_ACE_CATEGORY = 0x197;
	private static final long HEAD_ACE_PARAM = 0x11D3;
	private static final int BLOB_ACE_CATEGORY = 0x19D;
	private static final long BLOB_ACE_PARAM1 = 0x10;
	private static final long BLOB_ACE_PARAM2 = 0x20;
	// 吹き飛ばしのときは2個、それ以外は3個出る
	private static final int MAX_BLOBS = 3;
	private static final long BLOB_OUTPUT_DELAY_MS = 300;
	// 拘束が入るのを待ってから脱出方向を出す
	private static final long CRUEL_COIL_DELAY_MS = 3_000;
	private static final long MINDLESS_FLESH_HUGE_DELAY_MS = 9_000;
	private static final long HEMORRHAGIC_FRONT = 0x408;
	private static final long HEMORRHAGIC_RIGHT = 0x409;
	private static final long HEMORRHAGIC_BACK = 0x40A;
	private static final long HEMORRHAGIC_LEFT = 0x40B;

	private XivState state;

	public R12n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.M12N);
	}

	private final ModifiableCallout<HeadMarkerEvent> feralFissionStack = new ModifiableCallout<>("Feral Fission (Stack)", "Stack on {event.target}", 5_100);

	@HandleEvents
	public void feralFissionStack(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x5D)) {
			context.accept(feralFissionStack.getModified(event));
		}
	}

	@PlayerHeadmarker(0x158)
	private final ModifiableCallout<HeadMarkerEvent> tankbuster = new ModifiableCallout<>("Tankbuster", "Tankbuster", 5_100);

	@PlayerHeadmarker(0x178)
	private final ModifiableCallout<HeadMarkerEvent> feralFissionSpread = new ModifiableCallout<>("Feral Fission (Spread)", "Spread", 5_000);

	@PlayerHeadmarker(0x8B)
	private final ModifiableCallout<HeadMarkerEvent> dramaticLysis = new ModifiableCallout<>("Dramatic Lysis", "Spread");

	@NpcCastCallout(0xB494)
	private final ModifiableCallout<AbilityCastStart> theFixer = ModifiableCallout.durationBasedCall("The Fixer", "Raidwide");

	@NpcCastCallout(0xB465)
	private final ModifiableCallout<AbilityCastStart> bloodshedWest = ModifiableCallout.durationBasedCall("Bloodshed (Cleaving West)", "East");

	@NpcCastCallout(0xB466)
	private final ModifiableCallout<AbilityCastStart> bloodshedEast = ModifiableCallout.durationBasedCall("Bloodshed (Cleaving East)", "West");

	private final ModifiableCallout<AbilityCastStart> ravenousReachWest = ModifiableCallout.durationBasedCall("Ravenous Reach (West)", "Left/West");
	private final ModifiableCallout<AbilityCastStart> ravenousReachEast = ModifiableCallout.durationBasedCall("Ravenous Reach (East)", "Right/East");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> ravenousReachSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xB46D),
			(e1, s) -> {
				Position pos = s.waitForCastLocation(e1).getPos();
				if (pos == null) {
					return;
				}
				s.updateCall(pos.x() < CENTER_X ? ravenousReachWest : ravenousReachEast, e1);
			});

	private final ModifiableCallout<ActorControlExtraEvent> burstBlobLaunch = new ModifiableCallout<>("Burst Blob (Launch)", "Launch away from Blobs", 6_600);
	private final ModifiableCallout<ActorControlExtraEvent> burstBlobAway = new ModifiableCallout<>("Burst Blob", "Away from Blobs", 6_600);

	private static boolean isBlobEvent(ActorControlExtraEvent ace) {
		return ace.getCategory() == BLOB_ACE_CATEGORY && ace.getData0() == BLOB_ACE_PARAM1 && ace.getData1() == BLOB_ACE_PARAM2;
	}

	// 個数で吹き飛ばしかどうかが変わるので、まとめて数えてから出す
	@AutoFeed
	private final SequentialTrigger<BaseEvent> burstBlobSq = SqtTemplates.sq(10_000,
			ActorControlExtraEvent.class, R12n::isBlobEvent,
			(e1, s) -> {
				List<ActorControlExtraEvent> blobs = s.waitEventsQuickSuccession(MAX_BLOBS - 1, ActorControlExtraEvent.class, R12n::isBlobEvent, Duration.ofMillis(BLOB_OUTPUT_DELAY_MS));
				int count = blobs.size() + 1;
				s.updateCall(count < MAX_BLOBS ? burstBlobLaunch : burstBlobAway, e1);
			});

	private final ModifiableCallout<BuffApplied> fleshLaunchForward = new ModifiableCallout<BuffApplied>("Flesh Launch (Forward)", "Launching Forward", 7_000).autoIcon();
	private final ModifiableCallout<BuffApplied> fleshLaunchBackward = new ModifiableCallout<BuffApplied>("Flesh Launch (Backward)", "Launching Backward", 7_000).autoIcon();

	@HandleEvents
	public void fleshLaunch(EventContext context, BuffApplied event) {
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		if (event.buffIdMatches(0x128B)) {
			context.accept(fleshLaunchForward.getModified(event));
		}
		else if (event.buffIdMatches(0x128C)) {
			context.accept(fleshLaunchBackward.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> cruelCoilNw = new ModifiableCallout<>("Cruel Coil (Northwest)", "Escape Northwest Clockwise", 6_100);
	private final ModifiableCallout<AbilityUsedEvent> cruelCoilSe = new ModifiableCallout<>("Cruel Coil (Southeast)", "Escape Southeast Clockwise", 6_100);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> cruelCoilSq = SqtTemplates.sq(10_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0xB11B, 0xB11C) && aue.isFirstTarget(),
			(e1, s) -> {
				s.waitMs(CRUEL_COIL_DELAY_MS);
				s.updateCall(e1.abilityIdMatches(0xB11B) ? cruelCoilNw : cruelCoilSe, e1);
			});

	private final ModifiableCallout<BuffApplied> hemorrhagicProjection = new ModifiableCallout<BuffApplied>("Hemorrhagic Projection", "Cleaving {dir}, Point Out", 7_000).autoIcon();

	@HandleEvents
	public void hemorrhagicProjection(EventContext context, BuffApplied event) {
		if (!event.buffIdMatches(0x808) || !event.getTarget().isThePlayer()) {
			return;
		}
		long stacks = event.getRawStacks();
		String dir;
		if (stacks == HEMORRHAGIC_FRONT) {
			dir = "Front";
		}
		else if (stacks == HEMORRHAGIC_RIGHT) {
			dir = "Right";
		}
		else if (stacks == HEMORRHAGIC_BACK) {
			dir = "Back";
		}
		else if (stacks == HEMORRHAGIC_LEFT) {
			dir = "Left";
		}
		else {
			return;
		}
		context.accept(hemorrhagicProjection.getModified(event, Map.of("dir", dir)));
	}

	@NpcCastCallout({0xB9BD, 0xB9C0, 0xBBE0})
	private final ModifiableCallout<AbilityCastStart> splattershed = ModifiableCallout.durationBasedCall("Splattershed", "Raidwide");

	@NpcAbilityUsedCallout(value = 0xB478, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> feralFission = new ModifiableCallout<>("Feral Fission", "Away from Snakes, Platform Breaking", 6_000);

	private final ModifiableCallout<ActorControlExtraEvent> tileHeadsMiddle = new ModifiableCallout<>("Wyrm Head Cleaves (Middle)", "Middle", 7_000);
	private final ModifiableCallout<ActorControlExtraEvent> tileHeadsWest = new ModifiableCallout<>("Wyrm Head Cleaves (West)", "Left/West", 7_000);
	private final ModifiableCallout<ActorControlExtraEvent> tileHeadsEast = new ModifiableCallout<>("Wyrm Head Cleaves (East)", "Right/East", 7_000);
	private final ModifiableCallout<ActorControlExtraEvent> tileHeadsSplit = new ModifiableCallout<>("Wyrm Head Cleaves (Split)", "{dir1}/{dir2}", 7_000);

	private enum TileHeadPos {
		OUT_WEST("Out West", true, true),
		IN_WEST("In West", false, true),
		IN_EAST("In East", false, false),
		OUT_EAST("Out East", true, false);

		private final String label;
		private final boolean outer;
		private final boolean west;

		TileHeadPos(String label, boolean outer, boolean west) {
			this.label = label;
			this.outer = outer;
			this.west = west;
		}

		static TileHeadPos of(Position pos) {
			double xOffset = pos.x() - CENTER_X;
			if (xOffset < -TILE_OUTER_OFFSET) {
				return OUT_WEST;
			}
			if (xOffset < 0) {
				return IN_WEST;
			}
			if (xOffset < TILE_OUTER_OFFSET) {
				return IN_EAST;
			}
			return OUT_EAST;
		}
	}

	private static boolean isHeadEvent(ActorControlExtraEvent ace) {
		return ace.getCategory() == HEAD_ACE_CATEGORY && ace.getData0() == HEAD_ACE_PARAM;
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> tileHeadsSq = SqtTemplates.sq(10_000,
			ActorControlExtraEvent.class, R12n::isHeadEvent,
			(e1, s) -> {
				ActorControlExtraEvent e2 = s.waitEvent(ActorControlExtraEvent.class, R12n::isHeadEvent);
				Position pos1 = state.getLatestCombatantData(e1.getTarget()).getPos();
				Position pos2 = state.getLatestCombatantData(e2.getTarget()).getPos();
				if (pos1 == null || pos2 == null) {
					return;
				}
				TileHeadPos head1 = TileHeadPos.of(pos1);
				TileHeadPos head2 = TileHeadPos.of(pos2);
				if (head1.outer && head2.outer) {
					s.updateCall(tileHeadsMiddle, e2);
				}
				else if (!head1.west && !head2.west) {
					s.updateCall(tileHeadsWest, e2);
				}
				else if (head1.west && head2.west) {
					s.updateCall(tileHeadsEast, e2);
				}
				else {
					List<TileHeadPos> safe = Arrays.stream(TileHeadPos.values())
							.filter(pos -> pos != head1 && pos != head2)
							.toList();
					if (safe.size() < 2) {
						return;
					}
					s.setParam("dir1", safe.get(0).label);
					s.setParam("dir2", safe.get(1).label);
					s.updateCall(tileHeadsSplit, e2);
				}
			});

	@NpcCastCallout(value = {0xBBD8, 0xBBD9, 0xBBDA, 0xBBDB, 0xBBDC, 0xBBDD, 0xBBDE}, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> mindlessFlesh = ModifiableCallout.durationBasedCall("Mindless Flesh", "Dodge Lines");

	private final ModifiableCallout<AbilityCastStart> mindlessFleshHugeWest = new ModifiableCallout<>("Mindless Flesh Huge (West Safe)", "Left/West", 6_800);
	private final ModifiableCallout<AbilityCastStart> mindlessFleshHugeEast = new ModifiableCallout<>("Mindless Flesh Huge (East Safe)", "Right/East", 6_800);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> mindlessFleshHugeSq = SqtTemplates.sq(20_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xBBDF),
			(e1, s) -> {
				Position pos = s.waitForCastLocation(e1).getPos();
				long remaining = MINDLESS_FLESH_HUGE_DELAY_MS - s.timeSinceStart().toMillis();
				if (remaining > 0) {
					s.waitMs(remaining);
				}
				if (pos == null) {
					return;
				}
				s.updateCall(pos.x() < CENTER_X ? mindlessFleshHugeEast : mindlessFleshHugeWest, e1);
			});

}
