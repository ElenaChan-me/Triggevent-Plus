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
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/raid/e9s.ts をもとに移植
@CalloutRepo(name = "Edens Promise Umbra Savage", duty = KnownDuty.EdensPromiseUmbraSavage)
public class E9s extends AutoChildEventHandler implements FilteredEventHandler {

	// 偽物のボスは別の BNpcID なので、本物だけをこれで探す
	private static final long CLOUD_OF_DARKNESS_NPC_ID = 12379;
	private static final double ARENA_CENTER = 100;
	private static final double CLONE_DISTANCE = 20;
	private static final int DIRECTION_COUNT = 8;
	// 1回のなぎ払いで潰れる 1/8 区画の数
	private static final int SWIPE_WIDTH = 4;
	private static final String[] SUMMON_DIRECTIONS = {"NNE", "ENE", "ESE", "SSE", "SSW", "WSW", "WNW", "NNW"};

	private XivState state;

	public E9s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.EdensPromiseUmbraSavage);
	}

	private enum ArtKey {
		RIGHT("Right"),
		LEFT("Left"),
		PARTNERS("Partners"),
		PROTEAN("Protean");

		private final String text;

		ArtKey(String text) {
			this.text = text;
		}
	}

	private Map<Long, ArtKey> artOfDarknessIdMap;
	private ArtKey artOfDarknessExpected;
	private List<ArtKey> artOfDarkness;
	private ArtKey finalArtOfDarkness;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		artOfDarknessIdMap = null;
		artOfDarknessExpected = null;
		artOfDarkness = null;
		finalArtOfDarkness = null;
	}

	@NpcCastCallout(0x5625)
	private final ModifiableCallout<AbilityCastStart> groundRazingParticleBeam = ModifiableCallout.durationBasedCall("Ground-Razing Particle Beam", "Raidwide");

	@NpcCastCallout({0x5B45, 0x55FB})
	private final ModifiableCallout<AbilityCastStart> artOfDarknessProtean = ModifiableCallout.durationBasedCall("The Art Of Darkness (Protean)", "Protean");

	@NpcCastCallout({0x5B46, 0x55FE})
	private final ModifiableCallout<AbilityCastStart> artOfDarknessPartners = ModifiableCallout.durationBasedCall("The Art Of Darkness (Partner Stacks)", "Partners");

	private final ModifiableCallout<AbilityCastStart> devouringDarkOnYou = ModifiableCallout.durationBasedCall("Zero-Form Devouring Dark (on You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> devouringDarkTankSwap = ModifiableCallout.durationBasedCall("Zero-Form Devouring Dark (Tank Swap)", "Tank Swap");
	private final ModifiableCallout<AbilityCastStart> devouringDarkHealer = ModifiableCallout.durationBasedCall("Zero-Form Devouring Dark (Healer)", "Buster on {event.target}");
	private final ModifiableCallout<AbilityCastStart> devouringDarkAvoid = ModifiableCallout.durationBasedCall("Zero-Form Devouring Dark (Avoid)", "Avoid Laser");

	@HandleEvents
	public void devouringDark(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x5623)) {
			return;
		}
		ModifiableCallout<AbilityCastStart> call;
		if (event.getTarget().isThePlayer()) {
			call = devouringDarkOnYou;
		}
		else if (state.playerJobMatches(Job::isTank)) {
			call = devouringDarkTankSwap;
		}
		else if (state.playerJobMatches(Job::isHealer)) {
			call = devouringDarkHealer;
		}
		else {
			call = devouringDarkAvoid;
		}
		context.accept(call.getModified(event));
	}

	@NpcCastCallout(0x55EE)
	private final ModifiableCallout<AbilityCastStart> obscureWoods = ModifiableCallout.durationBasedCall("Obscure Woods", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> floodOfObscurity = new ModifiableCallout<>("Flood Of Obscurity", "Place Bramble");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> floodOfObscuritySq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x5907),
			(e1, s) -> {
				s.waitMs(3_000);
				s.updateCall(floodOfObscurity, e1);
			});

	@NpcCastCallout(0x5617)
	private final ModifiableCallout<AbilityCastStart> wasteAway = ModifiableCallout.durationBasedCall("Waste Away", "Knockback");

	@NpcCastCallout(0x5618)
	private final ModifiableCallout<AbilityCastStart> rejuvenatingBalm = ModifiableCallout.durationBasedCall("Rejuvenating Balm", "Away From Tethered Walls");

	private final ModifiableCallout<TetherEvent> stygianBreak = new ModifiableCallout<>("Stygian Break Tether", "Break Chains");
	private final RepeatSuppressor stygianBreakSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void stygianBreak(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x12) && event.eitherTargetMatches(XivCombatant::isThePlayer) && stygianBreakSupp.check(event)) {
			context.accept(stygianBreak.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> antiAirListTank = new ModifiableCallout<>("Anti-Air Phaser Unlimited (Tank)", "Out -> Tank Spread -> Sides", 15_000);
	private final ModifiableCallout<AbilityCastStart> antiAirList = new ModifiableCallout<>("Anti-Air Phaser Unlimited", "Out -> Healer Stacks -> Sides", 15_000);
	private final ModifiableCallout<AbilityCastStart> antiAirTankSpread = new ModifiableCallout<>("Anti-Air Phaser Unlimited: Tank Spread", "Tank Spread");
	private final ModifiableCallout<AbilityCastStart> antiAirHealerStacks = new ModifiableCallout<>("Anti-Air Phaser Unlimited: Healer Stacks", "Healer Stacks");
	private final ModifiableCallout<AbilityCastStart> antiAirSides = new ModifiableCallout<>("Anti-Air Phaser Unlimited: Sides", "Sides");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> antiAirPhaserSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x5612, 0x5613),
			(e1, s) -> {
				boolean tank = state.playerJobMatches(Job::isTank);
				s.call(tank ? antiAirListTank : antiAirList, e1);
				s.waitMs(7_000);
				s.call(tank ? antiAirTankSpread : antiAirHealerStacks, e1);
				s.waitMs(5_000);
				s.call(antiAirSides, e1);
			});

	private final ModifiableCallout<AbilityCastStart> wideAngleListTank = new ModifiableCallout<>("Wide-Angle Phaser Unlimited (Tank)", "Sides -> Laser on You -> Out", 15_000);
	private final ModifiableCallout<AbilityCastStart> wideAngleList = new ModifiableCallout<>("Wide-Angle Phaser Unlimited", "Sides -> Healer Stacks -> Out", 15_000);
	private final ModifiableCallout<AbilityCastStart> wideAngleTankLaser = new ModifiableCallout<>("Wide-Angle Phaser Unlimited: Tank Laser", "Laser on You");
	private final ModifiableCallout<AbilityCastStart> wideAngleHealerStacks = new ModifiableCallout<>("Wide-Angle Phaser Unlimited: Healer Stacks", "Healer Stacks");
	private final ModifiableCallout<AbilityCastStart> wideAngleOut = new ModifiableCallout<>("Wide-Angle Phaser Unlimited: Out", "Out");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> wideAnglePhaserSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x560D, 0x560E),
			(e1, s) -> {
				boolean tank = state.playerJobMatches(Job::isTank);
				s.call(tank ? wideAngleListTank : wideAngleList, e1);
				s.waitMs(8_000);
				s.call(tank ? wideAngleTankLaser : wideAngleHealerStacks, e1);
				s.waitMs(4_000);
				s.call(wideAngleOut, e1);
			});

	private final ModifiableCallout<AbilityCastStart> secondArtStartLeft = ModifiableCallout.durationBasedCall("The Second Art Of Darkness (Right)", "Start Left");
	private final ModifiableCallout<AbilityCastStart> secondArtStartRight = ModifiableCallout.durationBasedCall("The Second Art Of Darkness (Left)", "Start Right");
	private final ModifiableCallout<AbilityCastStart> thirdArtStartLeft = ModifiableCallout.durationBasedCall("The Third Art Of Darkness (Right)", "Start Left");
	private final ModifiableCallout<AbilityCastStart> thirdArtStartRight = ModifiableCallout.durationBasedCall("The Third Art Of Darkness (Left)", "Start Right");

	@HandleEvents
	public void artOfDarknessCasts(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x5601, 0x5602)) {
			// セカンド → サード → セカンドの順なので、2回目のセカンドの前に消しておく
			finalArtOfDarkness = null;
			artOfDarkness = new ArrayList<>();
			boolean right = event.abilityIdMatches(0x5601);
			if (artOfDarknessIdMap == null) {
				artOfDarknessExpected = right ? ArtKey.RIGHT : ArtKey.LEFT;
			}
			context.accept((right ? secondArtStartLeft : secondArtStartRight).getModified(event));
		}
		else if (event.abilityIdMatches(0x5603, 0x5604)) {
			artOfDarkness = new ArrayList<>();
			boolean right = event.abilityIdMatches(0x5603);
			// 最後の1回は詠唱で分かるので、2回目の突進が見えた時点でまとめてコールする
			finalArtOfDarkness = right ? ArtKey.RIGHT : ArtKey.LEFT;
			context.accept((right ? thirdArtStartLeft : thirdArtStartRight).getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> artOfDarknessSequence = new ModifiableCallout<>("The Second Art Of Darkness Sequence", "{sequence}", 9_000);
	private final ModifiableCallout<HeadMarkerEvent> thirdArtOfDarknessSequence = new ModifiableCallout<>("The Third Art Of Darkness Sequence", "{sequence}", 16_000);

	@HandleEvents
	public void artOfDarknessMarkers(EventContext context, HeadMarkerEvent event) {
		if (event.getTarget().isPc()) {
			return;
		}
		if (artOfDarknessIdMap == null) {
			if (artOfDarknessExpected == null) {
				return;
			}
			// マーカーIDはランダムにずれるが連番なので、最初の突進が左右どちらかから基準を求める
			long idPivot = artOfDarknessExpected == ArtKey.LEFT ? event.getMarkerId() : event.getMarkerId() - 1;
			artOfDarknessExpected = null;
			artOfDarknessIdMap = new HashMap<>();
			ArtKey[] keys = ArtKey.values();
			for (int i = 0; i < keys.length; i++) {
				artOfDarknessIdMap.put(idPivot + i, keys[i]);
			}
		}
		if (artOfDarkness == null) {
			return;
		}
		ArtKey key = artOfDarknessIdMap.get(event.getMarkerId());
		if (key == null) {
			return;
		}
		artOfDarkness.add(key);
		if (key == ArtKey.RIGHT || key == ArtKey.LEFT) {
			return;
		}
		// 頭割りか散開が来た時点で、できるだけ早くまとめてコールする
		List<ArtKey> sequence = new ArrayList<>(artOfDarkness);
		if (finalArtOfDarkness != null) {
			sequence.add(finalArtOfDarkness);
		}
		String text = sequence.stream().map(k -> k.text).collect(Collectors.joining(" -> "));
		ModifiableCallout<HeadMarkerEvent> call = finalArtOfDarkness != null ? thirdArtOfDarknessSequence : artOfDarknessSequence;
		context.accept(call.getModified(event, Map.of("sequence", text)));
		artOfDarkness = null;
	}

	@NpcCastCallout(0x55EF)
	private final ModifiableCallout<AbilityCastStart> emptyPlane = ModifiableCallout.durationBasedCall("Empty Plane", "Raidwide");

	@NpcCastCallout(0x55F0)
	private final ModifiableCallout<AbilityCastStart> floodOfEmptiness = ModifiableCallout.durationBasedCall("Flood Of Emptiness", "Tile Positions");

	private final ModifiableCallout<BuffApplied> curseOfDarkness = ModifiableCallout.<BuffApplied>durationBasedCall("Curse Of Darkness", "Face Laser Out").autoIcon();

	@AutoFeed
	private final SequentialTrigger<BaseEvent> curseOfDarknessSq = SqtTemplates.callWhenDurationIs(
			BuffApplied.class, ba -> ba.buffIdMatches(0x953) && ba.getTarget().isThePlayer(),
			curseOfDarkness, Duration.ofSeconds(3));

	private final ModifiableCallout<AbilityCastStart> hyperchargedCondensation = new ModifiableCallout<>("Hypercharged Condensation", "Kill Adds", 5_000);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> hyperchargedCondensationSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x560C),
			(e1, s) -> {
				s.waitMs(2_000);
				s.updateCall(hyperchargedCondensation, e1);
			});

	@NpcCastCallout(0x5A95)
	private final ModifiableCallout<AbilityCastStart> artOfDarknessRight = ModifiableCallout.durationBasedCall("The Art Of Darkness (Right)", "Left");

	@NpcCastCallout(0x5A96)
	private final ModifiableCallout<AbilityCastStart> artOfDarknessLeft = ModifiableCallout.durationBasedCall("The Art Of Darkness (Left)", "Right");

	private final ModifiableCallout<AbilityCastStart> fullPerimeterParticleBeam = new ModifiableCallout<>("Full-Perimeter Particle Beam", "In", 5_000);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> fullPerimeterSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x5629),
			(e1, s) -> {
				// カースオブダークネスのコールを先に済ませる
				s.waitMs(2_000);
				s.updateCall(fullPerimeterParticleBeam, e1);
			});

	@NpcCastCallout(0x55F1)
	private final ModifiableCallout<AbilityCastStart> delugeOfDarkness = ModifiableCallout.durationBasedCall("Deluge Of Darkness", "Big Raidwide");

	private final ModifiableCallout<AbilityCastStart> particleConcentration = new ModifiableCallout<>("Particle Concentration", "Get Towers", 6_000);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> particleConcentrationSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x5620),
			(e1, s) -> {
				s.waitMs(6_000);
				s.updateCall(particleConcentration, e1);
			});

	private final ModifiableCallout<AbilityCastStart> cloneArtOfDarkness = ModifiableCallout.durationBasedCall("Clone The Art Of Darkness", "{safe}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> cloneArtOfDarknessSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x561E, 0x561F),
			(e1, s) -> {
				// 分身2体の詠唱が揃うのを待ち、詠唱元の位置を分身の位置として使う
				AbilityCastStart other = s.waitEvent(AbilityCastStart.class,
						acs -> acs.abilityIdMatches(0x561E, 0x561F) && !acs.getSource().equals(e1.getSource()));
				s.waitThenRefreshCombatants(100);
				XivCombatant boss = state.npcById(CLOUD_OF_DARKNESS_NPC_ID);
				if (boss == null) {
					return;
				}
				Position bossPos = state.getLatestCombatantData(boss).getPos();
				Position clone1 = state.getLatestCombatantData(e1.getSource()).getPos();
				Position clone2 = state.getLatestCombatantData(other.getSource()).getPos();
				if (bossPos == null || clone1 == null || clone2 == null) {
					return;
				}
				String safe = calculateSummonSafeZone(bossHeadingToClonePosition(bossPos), clone1, clone2, e1.abilityIdMatches(0x561E));
				if (safe != null) {
					s.setParam("safe", safe);
					s.updateCall(cloneArtOfDarkness, e1);
				}
			});

	// 本体は向いている方向の反対側にいる分身と同じ扱いにする
	private static Position bossHeadingToClonePosition(Position boss) {
		double closestRad = Math.round(boss.getHeading() * 4 / Math.PI) / 4.0 * Math.PI;
		return Position.of2d(
				ARENA_CENTER - CLONE_DISTANCE * Math.round(Math.sin(closestRad)),
				ARENA_CENTER - CLONE_DISTANCE * Math.round(Math.cos(closestRad)));
	}

	private static long toOctant(Position pos) {
		return Math.round(4 - 4 * Math.atan2(pos.getX() - ARENA_CENTER, pos.getY() - ARENA_CENTER) / Math.PI);
	}

	private static String calculateSummonSafeZone(Position boss, Position clone1, Position clone2, boolean swipeRight) {
		Set<Long> badZones = new HashSet<>();
		for (Position pos : List.of(boss, clone1, clone2)) {
			long position = toOctant(pos);
			for (int i = 0; i < SWIPE_WIDTH; i++) {
				// 分身は北が 0 だが安置は北北東が 0 なので、右なぎ払いは1つずれる
				long newPosition = swipeRight
						? (position - i + DIRECTION_COUNT - 1) % DIRECTION_COUNT
						: (position + i) % DIRECTION_COUNT;
				badZones.add(newPosition);
			}
		}
		List<Integer> safeZones = new ArrayList<>();
		for (int i = 0; i < DIRECTION_COUNT; i++) {
			if (!badZones.contains((long) i)) {
				safeZones.add(i);
			}
		}
		if (safeZones.size() != 1) {
			return null;
		}
		return SUMMON_DIRECTIONS[safeZones.get(0)];
	}

}
