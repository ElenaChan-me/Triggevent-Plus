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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SequentialTriggerController;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.models.ArenaPos;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/raid/r9n.ts をもとに移植
@CalloutRepo(name = "Aac Heavyweight M1", duty = KnownDuty.M9N)
public class R9n extends AutoChildEventHandler implements FilteredEventHandler {

	private static final double CENTER_X = 100;
	private static final double CENTER_Y = 100;
	private static final ArenaPos arenaPos = new ArenaPos(CENTER_X, CENTER_Y, 0, 0);

	private static final int BAT_ACE_CATEGORY = 0x197;
	private static final long BAT_ACE_PARAM = 0x11D1;
	private static final int MAX_BATS = 16;
	// 半径で内周・中周・外周を判定する
	private static final double BAT_INNER_RADIUS = 8;
	private static final double BAT_MIDDLE_RADIUS = 16;
	// 各周の蝙蝠が着弾までに回る角度（ラジアン）
	private static final double BAT_INNER_MOVE = 1.5128;
	private static final double BAT_MIDDLE_MOVE = 1.5513;
	private static final long BAT_INNER_DELAY_MS = 4_100;
	private static final long BAT_MIDDLE_DELAY_MS = 9_700;
	private static final long BAT_OUTER_DELAY_MS = 13_200;
	private static final double FLAIL_NEAR_DISTANCE = 10;

	private XivState state;

	public R9n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.M9N);
	}

	private final ModifiableCallout<HeadMarkerEvent> multiStack = new ModifiableCallout<>("Party Multi Stack", "Stack on {event.target}");

	@HandleEvents
	public void multiStack(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x131)) {
			context.accept(multiStack.getModified(event));
		}
	}

	@PlayerHeadmarker(0x289)
	private final ModifiableCallout<HeadMarkerEvent> hugeTankbuster = new ModifiableCallout<>("Huge Tankbuster", "Tankbuster");

	@NpcCastCallout(value = 0xB348, onYou = true)
	private final ModifiableCallout<AbilityCastStart> aetherlettingSpread = ModifiableCallout.durationBasedCall("Aetherletting (Spread)", "Spread");

	@NpcAbilityUsedCallout(value = 0xB348, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> aetherlettingFollowup = new ModifiableCallout<>("Aetherletting (Followup)", "Dodge Lines");

	@NpcCastCallout(0xB361)
	private final ModifiableCallout<AbilityCastStart> killerVoice = ModifiableCallout.durationBasedCall("Killer Voice", "Raidwide");

	@NpcCastCallout(0xBEB7)
	private final ModifiableCallout<AbilityCastStart> halfMoonSmallRight = ModifiableCallout.durationBasedCall("Half Moon (Small, Right First)", "Right, then Left");

	@NpcCastCallout(0xBEB9)
	private final ModifiableCallout<AbilityCastStart> halfMoonSmallLeft = ModifiableCallout.durationBasedCall("Half Moon (Small, Left First)", "Left, then Right");

	@NpcCastCallout(0xBEB8)
	private final ModifiableCallout<AbilityCastStart> halfMoonLargeRight = ModifiableCallout.durationBasedCall("Half Moon (Large, Right First)", "Right Max Melee, then Left Max Melee");

	@NpcCastCallout(0xBEBA)
	private final ModifiableCallout<AbilityCastStart> halfMoonLargeLeft = ModifiableCallout.durationBasedCall("Half Moon (Large, Left First)", "Left Max Melee, then Right Max Melee");

	@NpcCastCallout(0xB34A)
	private final ModifiableCallout<AbilityCastStart> vampStomp = ModifiableCallout.durationBasedCall("Vamp Stomp", "Out");

	private final ModifiableCallout<ActorControlExtraEvent> blastBeatInner = new ModifiableCallout<>("Blast Beat (Inner)", "Away from Bats {dirs}", 5_500);
	private final ModifiableCallout<ActorControlExtraEvent> blastBeatMiddle = new ModifiableCallout<>("Blast Beat (Middle)", "Away from Bats {dirs}", 3_400);
	private final ModifiableCallout<ActorControlExtraEvent> blastBeatOuter = new ModifiableCallout<>("Blast Beat (Outer)", "Middle", 3_400);

	private static boolean isBatEvent(ActorControlExtraEvent ace) {
		return ace.getCategory() == BAT_ACE_CATEGORY && ace.getData0() == BAT_ACE_PARAM;
	}

	// 蝙蝠の現在位置と向きから回転方向を割り出し、着弾位置を求める
	private static ArenaSector batEndSector(Position pos, double moveRads) {
		double angle = Math.atan2(pos.x() - CENTER_X, pos.y() - CENTER_Y);
		double angleCw = angle - (Math.PI / 2);
		if (angleCw < -Math.PI) {
			angleCw += Math.PI * 2;
		}
		double angleDiff = Math.abs(angleCw - pos.heading());
		if (angleDiff > Math.PI * 1.75) {
			angleDiff = Math.abs(angleDiff - (Math.PI * 2));
		}
		boolean cw = angleDiff < (Math.PI / 2);
		double endAngle = angle + (moveRads * (cw ? -1 : 1));
		if (endAngle < -Math.PI) {
			endAngle += Math.PI * 2;
		}
		else if (endAngle > Math.PI) {
			endAngle -= Math.PI * 2;
		}
		return ArenaPos.combatantFacing(endAngle);
	}

	private static String joinSectors(List<ArenaSector> sectors) {
		if (sectors.isEmpty()) {
			return ArenaSector.UNKNOWN.getFriendlyName();
		}
		return String.join("/", sectors.stream().map(ArenaSector::getFriendlyName).toList());
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> blastBeatSq = SqtTemplates.sq(20_000,
			ActorControlExtraEvent.class, R9n::isBatEvent,
			(e1, s) -> {
				List<ActorControlExtraEvent> events = new ArrayList<>();
				events.add(e1);
				events.addAll(s.waitEventsQuickSuccession(MAX_BATS - 1, ActorControlExtraEvent.class, R9n::isBatEvent, Duration.ofSeconds(1)));
				List<ArenaSector> inner = new ArrayList<>();
				List<ArenaSector> middle = new ArrayList<>();
				for (ActorControlExtraEvent ace : events) {
					Position pos = state.getLatestCombatantData(ace.getTarget()).getPos();
					if (pos == null) {
						continue;
					}
					double dist = Math.hypot(pos.x() - CENTER_X, pos.y() - CENTER_Y);
					if (dist < BAT_INNER_RADIUS) {
						inner.add(batEndSector(pos, BAT_INNER_MOVE));
					}
					// 外周は中央が安置になるので着弾位置は不要
					else if (dist < BAT_MIDDLE_RADIUS) {
						middle.add(batEndSector(pos, BAT_MIDDLE_MOVE));
					}
				}
				waitUntil(s.timeSinceStart(), BAT_INNER_DELAY_MS, s);
				s.setParam("dirs", joinSectors(inner));
				s.updateCall(blastBeatInner, e1);
				waitUntil(s.timeSinceStart(), BAT_MIDDLE_DELAY_MS, s);
				s.setParam("dirs", joinSectors(middle));
				s.updateCall(blastBeatMiddle, e1);
				waitUntil(s.timeSinceStart(), BAT_OUTER_DELAY_MS, s);
				s.updateCall(blastBeatOuter, e1);
			});

	private static void waitUntil(Duration elapsed, long targetMs, SequentialTriggerController<BaseEvent> s) {
		long remaining = targetMs - elapsed.toMillis();
		if (remaining > 0) {
			s.waitMs(remaining);
		}
	}

	@NpcCastCallout(value = {0xB362, 0xB363}, onYou = true)
	private final ModifiableCallout<AbilityCastStart> flayingFry = ModifiableCallout.durationBasedCall("Flaying Fry", "Spread");

	@NpcCastCallout(0xB364)
	private final ModifiableCallout<AbilityCastStart> penetratingPitch = ModifiableCallout.durationBasedCall("Penetrating Pitch", "Healer Groups");

	@NpcCastCallout(0xB33E)
	private final ModifiableCallout<AbilityCastStart> crowdKill = ModifiableCallout.durationBasedCall("Crowd Kill", "Raidwide");

	@NpcCastCallout({0xB340, 0xB341})
	private final ModifiableCallout<AbilityCastStart> finaleFatale = ModifiableCallout.durationBasedCall("Finale Fatale", "Raidwide");

	@NpcCastCallout(0xB344)
	private final ModifiableCallout<AbilityCastStart> insatiableThirst = ModifiableCallout.durationBasedCall("Insatiable Thirst", "Raidwide");

	@NpcCastCallout(0xB333)
	private final ModifiableCallout<AbilityCastStart> sadisticScreech = ModifiableCallout.durationBasedCall("Sadistic Screech", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> plummet = ModifiableCallout.durationBasedCall("Plummet", "Flails {dist1} {dir1}/{dist2} {dir2}");

	private static String flailDistance(Position pos) {
		return Math.abs(pos.y() - CENTER_Y) < FLAIL_NEAR_DISTANCE ? "Near" : "Far";
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> plummetSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xB33B),
			(e1, s) -> {
				AbilityCastStart e2 = s.waitEvent(AbilityCastStart.class, acs -> acs.abilityIdMatches(0xB33B));
				Position pos1 = s.waitForCastLocation(e1).getPos();
				Position pos2 = s.waitForCastLocation(e2).getPos();
				if (pos1 == null || pos2 == null) {
					return;
				}
				s.setParam("dir1", arenaPos.forPosition(pos1));
				s.setParam("dir2", arenaPos.forPosition(pos2));
				s.setParam("dist1", flailDistance(pos1));
				s.setParam("dist2", flailDistance(pos2));
				s.updateCall(plummet, e1);
			});

}
