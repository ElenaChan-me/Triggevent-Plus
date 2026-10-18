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
import gg.xp.xivsupport.events.actlines.events.ActorControlExtraEvent;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.MapEffectEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/raid/r10n.ts をもとに移植
@CalloutRepo(name = "Aac Heavyweight M2", duty = KnownDuty.M10N)
public class R10n extends AutoChildEventHandler implements FilteredEventHandler {

	private static final double CENTER_X = 100;
	private static final double CENTER_Y = 100;
	// 中央付近とみなす幅
	private static final double MIDDLE_TOLERANCE = 5;

	private static final long SHARED_BUSTER = 0x103;
	private static final long TANKBUSTER = 0x158;
	private static final long WATER_SNAKING_SECOND = 0x27B;
	private static final long FIRE_SNAKING_SECOND = 0x27C;
	private static final long PARTY_STACK_FIRE = 0x293;
	private static final long WATER_SNAKING_FIRST = 0x295;
	private static final long FIRE_SNAKING_FIRST = 0x296;
	private static final long CUTBACK_BLAZE_CONE = 0x298;

	private static final long WAVE_TETHER = 0x174;
	private static final long FIRE_TETHER = 0x17D;
	private static final long WATER_TETHER = 0x17C;

	private static final int PLAY_ACTION_TIMELINE = 0x197;
	private static final long STEAM_BURST_TIMELINE = 0x11D4;

	private static final long SNAKE_FIRE_MID_LOCATION = 0x06;
	private static final long SNAKE_WATER_MID_LOCATION = 0x08;
	private static final long SNAKE_PATTERN_FLAGS = 0x00020001;
	// 4回目の誘導だけは中央になることがある
	private static final int SNAKE_LAST_COUNT = 3;
	// 水は北から、火は南から時計回りに進む
	private static final int FIRE_DIR_OFFSET = 4;

	private XivState state;

	public R10n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.M10N);
	}

	private @Nullable XivCombatant waveActor;
	private int snakingCount = -1;
	private @Nullable Boolean fireEndsMid;

	@HandleEvents(order = -10)
	public void reset(EventContext context, PullStartedEvent event) {
		waveActor = null;
		snakingCount = -1;
		fireEndsMid = null;
	}

	private static ArenaSector cardinalOf(Position pos) {
		double dx = pos.getX() - CENTER_X;
		double dy = pos.getY() - CENTER_Y;
		if (Math.abs(dx) > Math.abs(dy)) {
			return dx > 0 ? ArenaSector.EAST : ArenaSector.WEST;
		}
		return dy > 0 ? ArenaSector.SOUTH : ArenaSector.NORTH;
	}

	private final ModifiableCallout<HeadMarkerEvent> hotImpact = new ModifiableCallout<>("Hot Impact", "Shared Buster on {event.target}");
	private final ModifiableCallout<HeadMarkerEvent> deepImpact = new ModifiableCallout<>("Deep Impact", "Buster on {event.target}");
	private final ModifiableCallout<HeadMarkerEvent> pyrotation = new ModifiableCallout<>("Pyrotation", "Stack on {event.target}");
	private final ModifiableCallout<HeadMarkerEvent> cutbackBlaze = new ModifiableCallout<>("Cutback Blaze", "Spread Fire Cone");

	@HandleEvents
	public void headMarkers(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(SHARED_BUSTER)) {
			context.accept(hotImpact.getModified(event));
		}
		else if (event.markerIdMatches(TANKBUSTER)) {
			context.accept(deepImpact.getModified(event));
		}
		else if (event.markerIdMatches(PARTY_STACK_FIRE)) {
			context.accept(pyrotation.getModified(event));
		}
		// 対象プレイヤーは副ターゲット側に入ってくる
		else if (event.markerIdMatches(CUTBACK_BLAZE_CONE) && event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			context.accept(cutbackBlaze.getModified(event));
		}
	}

	@PlayerHeadmarker(0x294)
	private final ModifiableCallout<HeadMarkerEvent> alleyOopInferno = new ModifiableCallout<>("Alley-oop Inferno", "Spread");

	@NpcCastCallout(0xB582)
	private final ModifiableCallout<AbilityCastStart> diversDare = ModifiableCallout.durationBasedCall("Divers' Dare", "Raidwide");

	@HandleEvents
	public void waveTether(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(WAVE_TETHER)) {
			waveActor = event.getTarget();
		}
	}

	private final ModifiableCallout<AbilityCastStart> sickSwell = ModifiableCallout.durationBasedCall("Sick Swell", "Knockback from {kb}, Away from {away}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> sickSwellSq = SqtTemplates.sq(
			30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xB593),
			(e1, s) -> {
				Position castPos = s.waitForCastLocation(e1).getPos();
				ArenaSector kb = ArenaSector.UNKNOWN;
				if (waveActor != null) {
					Position wavePos = state.getLatestCombatantData(waveActor).getPos();
					if (wavePos != null) {
						kb = cardinalOf(wavePos);
					}
				}
				ArenaSector away;
				if (kb == ArenaSector.EAST || kb == ArenaSector.WEST) {
					if (castPos.getY() < CENTER_Y - MIDDLE_TOLERANCE) {
						away = ArenaSector.NORTH;
					}
					else if (castPos.getY() > CENTER_Y + MIDDLE_TOLERANCE) {
						away = ArenaSector.SOUTH;
					}
					else {
						away = ArenaSector.CENTER;
					}
				}
				else {
					if (castPos.getX() < CENTER_X - MIDDLE_TOLERANCE) {
						away = ArenaSector.WEST;
					}
					else if (castPos.getX() > CENTER_X + MIDDLE_TOLERANCE) {
						away = ArenaSector.EAST;
					}
					else {
						away = ArenaSector.CENTER;
					}
				}
				s.setParam("kb", kb);
				s.setParam("away", away);
				s.updateCall(sickSwell, e1);
			});

	private final ModifiableCallout<AbilityCastStart> deepVarial = ModifiableCallout.durationBasedCall("Deep Varial", "Go {where} Edge");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> deepVarialSq = SqtTemplates.sq(
			30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xB598),
			(e1, s) -> {
				Position pos = s.waitForCastLocation(e1).getPos();
				s.setParam("where", cardinalOf(pos));
				s.updateCall(deepVarial, e1);
			});

	// 詠唱完了後の大ダメージに合わせて出す
	private final ModifiableCallout<AbilityCastStart> xtremeSpectacular = new ModifiableCallout<>("Xtreme Spectacular", "North or South, Big Raidwide");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> xtremeSpectacularSq = SqtTemplates.sq(
			30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xB5A1),
			(e1, s) -> {
				s.waitDuration(e1.getInitialDuration());
				s.updateCall(xtremeSpectacular, e1);
			});

	@NpcCastCallout(0xB58A)
	private final ModifiableCallout<AbilityCastStart> hotAerial = ModifiableCallout.durationBasedCall("Hot Aerial", "Towers");

	private final ModifiableCallout<ActorControlExtraEvent> steamBurst = new ModifiableCallout<>("Steam Burst", "Away from Exploding Orbs");
	private final RepeatSuppressor steamBurstSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void steamBurst(EventContext context, ActorControlExtraEvent event) {
		if (event.getCategory() == PLAY_ACTION_TIMELINE
				&& event.getData0() == STEAM_BURST_TIMELINE
				&& !event.getTarget().isPc()
				&& steamBurstSupp.check(event)) {
			context.accept(steamBurst.getModified(event));
		}
	}

	@NpcCastCallout(value = 0xB59E, suppressMs = 10_000)
	private final ModifiableCallout<AbilityCastStart> alleyOopMaelstrom = ModifiableCallout.durationBasedCall("Alley-oop Maelstrom", "Dodge Cones Twice");

	@NpcCastCallout(0xB57E)
	private final ModifiableCallout<AbilityCastStart> snakingAoe = ModifiableCallout.durationBasedCall("Firesnaking/Watersnaking", "Raidwide");

	private final ModifiableCallout<TetherEvent> fireTether = new ModifiableCallout<>("Firesnaking Tether", "Bait Fire Cones");
	private final ModifiableCallout<TetherEvent> waterTether = new ModifiableCallout<>("Watersnaking Tether", "Bait Water Cones");

	@HandleEvents
	public void snakingTethers(EventContext context, TetherEvent event) {
		if (!event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			return;
		}
		if (event.tetherIdMatches(FIRE_TETHER)) {
			context.accept(fireTether.getModified(event));
		}
		else if (event.tetherIdMatches(WATER_TETHER)) {
			context.accept(waterTether.getModified(event));
		}
	}

	@HandleEvents
	public void snakingPattern(EventContext context, MapEffectEvent event) {
		if (fireEndsMid != null || !event.flagsMatches(SNAKE_PATTERN_FLAGS)) {
			return;
		}
		if (event.getLocation() == SNAKE_FIRE_MID_LOCATION) {
			fireEndsMid = true;
		}
		else if (event.getLocation() == SNAKE_WATER_MID_LOCATION) {
			fireEndsMid = false;
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> snakingBaitFire = new ModifiableCallout<>("Snaking Bait (Fire)", "Bait Fire Cone from {dir}");
	private final ModifiableCallout<HeadMarkerEvent> snakingBaitWater = new ModifiableCallout<>("Snaking Bait (Water)", "Bait Water Cone from {dir}");
	private final RepeatSuppressor snakingCountSupp = new RepeatSuppressor(Duration.ofSeconds(3));

	@HandleEvents
	public void snakingBait(EventContext context, HeadMarkerEvent event) {
		boolean fire = event.markerIdMatches(FIRE_SNAKING_FIRST, FIRE_SNAKING_SECOND);
		boolean water = event.markerIdMatches(WATER_SNAKING_FIRST, WATER_SNAKING_SECOND);
		if (!fire && !water) {
			return;
		}
		// 同時に付く複数のマーカーで1回と数える
		if (snakingCountSupp.check(event)) {
			snakingCount++;
		}
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		int dirNum = snakingCount + (fire ? FIRE_DIR_OFFSET : 0);
		ArenaSector dir = dirNum >= 0 && dirNum < ArenaSector.all.size() ? ArenaSector.all.get(dirNum) : ArenaSector.UNKNOWN;
		if (fireEndsMid != null && fireEndsMid == fire && snakingCount == SNAKE_LAST_COUNT) {
			dir = ArenaSector.CENTER;
		}
		context.accept((fire ? snakingBaitFire : snakingBaitWater).getModified(event, Map.of("dir", dir)));
	}
}
