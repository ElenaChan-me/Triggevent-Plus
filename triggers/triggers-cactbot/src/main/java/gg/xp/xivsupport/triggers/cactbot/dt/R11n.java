package gg.xp.xivsupport.triggers.cactbot.dt;

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
import gg.xp.xivsupport.events.actlines.events.ActorControlExtraEvent;
import gg.xp.xivsupport.events.actlines.events.DescribesCastLocation;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SequentialTriggerConcurrencyMode;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.ArenaPos;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/raid/r11n.ts をもとに移植
@CalloutRepo(name = "Aac Heavyweight M3", duty = KnownDuty.M11N)
public class R11n extends AutoChildEventHandler implements FilteredEventHandler {

	private static final int PLAY_ACTION_TIMELINE = 0x197;
	private static final long ASSAULT_TETHER = 0xF9;
	private static final long FOREGONE_TETHER = 0x164;
	private static final long RAW_STEEL_BUSTER = 0x258;
	private static final long MASSIVE_METEOR = 0x13E;
	private static final long GREAT_WALL_OF_FIRE = 0x256;
	private static final int ASSAULT_WEAPON_COUNT = 3;

	// 中心から見た象限だけ分かればよい
	private static final ArenaPos quadrants = new ArenaPos(100, 100, 0, 0);

	private XivState state;

	public R11n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.M11N);
	}

	private enum Weapon {
		SWORD(0x11D1, "Intercards"),
		AXE(0x11D2, "Out"),
		SCYTHE(0x11D3, "In"),
		UNKNOWN(-1, "?");

		private final long timeline;
		private final String label;

		Weapon(long timeline, String label) {
			this.timeline = timeline;
			this.label = label;
		}

		static Weapon of(long timeline) {
			for (Weapon w : values()) {
				if (w.timeline == timeline) {
					return w;
				}
			}
			return UNKNOWN;
		}
	}

	private boolean trophyActive;
	private final Map<Long, Weapon> weaponModels = new HashMap<>();
	private final Map<Long, Long> weaponTethers = new HashMap<>();
	private final List<AbilityCastStart> dominationCasts = new ArrayList<>();

	@HandleEvents(order = -10)
	public void reset(EventContext context, PullStartedEvent event) {
		trophyActive = false;
		weaponModels.clear();
		weaponTethers.clear();
		dominationCasts.clear();
	}

	private static boolean isWeaponTimeline(ActorControlExtraEvent event) {
		return event.getCategory() == PLAY_ACTION_TIMELINE && Weapon.of(event.getData0()) != Weapon.UNKNOWN;
	}

	// 詠唱が終わる少し前に出す
	private static SequentialTrigger<BaseEvent> callBeforeCastEnd(Duration before, ModifiableCallout<AbilityCastStart> call, long... ids) {
		return SqtTemplates.sq(
				60_000,
				AbilityCastStart.class, acs -> acs.abilityIdMatches(ids),
				(e1, s) -> {
					Duration wait = e1.getInitialDuration().minus(before);
					if (wait.isPositive()) {
						s.waitDuration(wait);
					}
					s.updateCall(call, e1);
				});
	}

	@NpcCastCallout(0xB3B6)
	private final ModifiableCallout<AbilityCastStart> crownOfArcadia = ModifiableCallout.durationBasedCall("Crown of Arcadia", "Raidwide");

	@NpcCastCallout(0xB3BA)
	private final ModifiableCallout<AbilityCastStart> smashdownAxe = ModifiableCallout.durationBasedCall("Smashdown (Axe)", "Out of Melee");

	@NpcCastCallout(0xB3BC)
	private final ModifiableCallout<AbilityCastStart> smashdownScythe = ModifiableCallout.durationBasedCall("Smashdown (Scythe)", "In");

	@NpcCastCallout(0xB3BE)
	private final ModifiableCallout<AbilityCastStart> smashdownSword = ModifiableCallout.durationBasedCall("Smashdown (Sword)", "Intercards");

	@PlayerHeadmarker(0x276)
	private final ModifiableCallout<HeadMarkerEvent> voidStardust = new ModifiableCallout<>("Void Stardust", "Spread, then Bait 3 Puddles");

	@HandleEvents
	public void trophyWeapons(EventContext context, AbilityCastStart event) {
		// 複数武器のギミックごとに前回の情報を捨てる
		if (event.abilityIdMatches(0xB3CC, 0xB7EB)) {
			weaponModels.clear();
			weaponTethers.clear();
		}
		if (event.abilityIdMatches(0xB7EB)) {
			trophyActive = true;
		}
	}

	@HandleEvents
	public void weaponModel(EventContext context, ActorControlExtraEvent event) {
		if (!trophyActive && isWeaponTimeline(event)) {
			weaponModels.put(event.getTarget().getId(), Weapon.of(event.getData0()));
		}
	}

	@HandleEvents
	public void weaponTether(EventContext context, TetherEvent event) {
		if (!trophyActive && event.tetherIdMatches(ASSAULT_TETHER) && !event.getSource().isPc() && !event.getTarget().isPc()) {
			weaponTethers.put(event.getSource().getId(), event.getTarget().getId());
		}
	}

	private final ModifiableCallout<AbilityCastStart> assaultEvolved = new ModifiableCallout<>("Assault Evolved", "{first}, then {second}, then {third}", 15_000);

	@HandleEvents
	public void assaultEvolvedCall(EventContext context, AbilityCastStart event) {
		if (trophyActive || !event.abilityIdMatches(0xB3CD)) {
			return;
		}
		// 線のつながりをボスから辿って実行順を決める
		List<String> labels = new ArrayList<>();
		Long current = event.getSource().getId();
		for (int i = 0; i < ASSAULT_WEAPON_COUNT; i++) {
			current = current == null ? null : weaponTethers.get(current);
			Weapon weapon = current == null ? Weapon.UNKNOWN : weaponModels.getOrDefault(current, Weapon.UNKNOWN);
			labels.add(weapon.label);
		}
		context.accept(assaultEvolved.getModified(event, Map.of("first", labels.get(0), "second", labels.get(1), "third", labels.get(2))));
	}

	@NpcCastCallout(0xB3D1)
	private final ModifiableCallout<AbilityCastStart> danceOfDomination = ModifiableCallout.durationBasedCall("Dance of Domination", "Raidwide");

	@HandleEvents
	public void dominationCollect(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0xB7B9)) {
			dominationCasts.add(event);
		}
	}

	private final ModifiableCallout<AbilityCastStart> dominationNorthSouth = ModifiableCallout.durationBasedCall("Dance of Domination Explosion (N/S Safe)", "North/South Middle");
	private final ModifiableCallout<AbilityCastStart> dominationEastWest = ModifiableCallout.durationBasedCall("Dance of Domination Explosion (E/W Safe)", "East/West Middle");
	private final ModifiableCallout<AbilityCastStart> dominationUnknown = ModifiableCallout.durationBasedCall("Dance of Domination Explosion (Unknown)", "Avoid Exploding Lines");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> dominationSq = SqtTemplates.sq(
			30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xB7B9),
			(e1, s) -> {
				s.waitMs(500);
				// 斜めの線は無視し、1本だけの縦横線から安置を出す
				int vert = 0;
				int horiz = 0;
				for (AbilityCastStart cast : dominationCasts) {
					DescribesCastLocation<AbilityCastStart> loc = cast.getLocationInfo();
					if (loc == null) {
						continue;
					}
					ArenaSector facing = ArenaPos.combatantFacing(loc.getBestHeading());
					if (facing == ArenaSector.NORTH || facing == ArenaSector.SOUTH) {
						vert++;
					}
					else if (facing == ArenaSector.EAST || facing == ArenaSector.WEST) {
						horiz++;
					}
				}
				if (vert == 1) {
					s.updateCall(dominationNorthSouth, e1);
				}
				else if (horiz == 1) {
					s.updateCall(dominationEastWest, e1);
				}
				else {
					s.updateCall(dominationUnknown, e1);
				}
				s.waitMs(1_000);
				dominationCasts.clear();
			});

	private final ModifiableCallout<HeadMarkerEvent> rawSteelBuster = new ModifiableCallout<>("Raw Steel (Buster)", "Shared Buster on {event.target}");

	@PlayerHeadmarker(0x137)
	private final ModifiableCallout<HeadMarkerEvent> rawSteelSpread = new ModifiableCallout<>("Raw Steel (Spread)", "Spread");

	private final ModifiableCallout<HeadMarkerEvent> massiveMeteorOnYou = new ModifiableCallout<>("Massive Meteor (On You)", "Stack on You");
	private final ModifiableCallout<HeadMarkerEvent> massiveMeteor = new ModifiableCallout<>("Massive Meteor", "Stack on {event.target}");
	private final ModifiableCallout<HeadMarkerEvent> greatWallOfFire = new ModifiableCallout<>("Great Wall of Fire", "Shared Buster on {event.target}");

	@HandleEvents
	public void headMarkers(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(RAW_STEEL_BUSTER)) {
			if (state.playerJobMatches(Job::isTank) || event.getTarget().isThePlayer()) {
				context.accept(rawSteelBuster.getModified(event));
			}
		}
		else if (event.markerIdMatches(MASSIVE_METEOR)) {
			context.accept((event.getTarget().isThePlayer() ? massiveMeteorOnYou : massiveMeteor).getModified(event));
		}
		else if (event.markerIdMatches(GREAT_WALL_OF_FIRE)) {
			context.accept(greatWallOfFire.getModified(event));
		}
	}

	@NpcCastCallout(0xB3D7)
	private final ModifiableCallout<AbilityCastStart> charybdistopia = ModifiableCallout.durationBasedCall("Charybdistopia", "HP to 1");

	private final ModifiableCallout<ActorControlExtraEvent> trophyNextAxe = new ModifiableCallout<>("Ultimate Trophy Weapons (Axe)", "Out Next");
	private final ModifiableCallout<ActorControlExtraEvent> trophyNextScythe = new ModifiableCallout<>("Ultimate Trophy Weapons (Scythe)", "In Next");
	private final ModifiableCallout<ActorControlExtraEvent> trophyNextSword = new ModifiableCallout<>("Ultimate Trophy Weapons (Sword)", "Intercards Next");

	// 直前の武器の処理が終わるまで待ってから次を伝える
	@AutoFeed
	private final SequentialTrigger<BaseEvent> ultimateTrophySq = SqtTemplates.sq(
			10_000,
			ActorControlExtraEvent.class, acee -> trophyActive && isWeaponTimeline(acee),
			(e1, s) -> {
				s.waitMs(2_400);
				switch (Weapon.of(e1.getData0())) {
					case AXE -> s.updateCall(trophyNextAxe, e1);
					case SCYTHE -> s.updateCall(trophyNextScythe, e1);
					case SWORD -> s.updateCall(trophyNextSword, e1);
					default -> {
					}
				}
			}).setConcurrency(SequentialTriggerConcurrencyMode.CONCURRENT);

	private final ModifiableCallout<AbilityCastStart> oneAndOnly = ModifiableCallout.durationBasedCall("One and Only", "Raidwide");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> oneAndOnlySq = callBeforeCastEnd(Duration.ofSeconds(5), oneAndOnly, 0xB3DC);

	@NpcCastCallout(value = 0xB3DE, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> cosmicKiss = ModifiableCallout.durationBasedCall("Cosmic Kiss", "Towers");

	private final ModifiableCallout<TetherEvent> foregoneFatality = new ModifiableCallout<>("Foregone Fatality", "Buster Tethers");
	// 線が受け渡されるたびに呼ばないようにする
	private final RepeatSuppressor foregoneSupp = new RepeatSuppressor(Duration.ofSeconds(9));

	@HandleEvents
	public void foregoneFatality(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(FOREGONE_TETHER) && foregoneSupp.check(event)) {
			context.accept(foregoneFatality.getModified(event));
		}
	}

	@NpcCastCallout(0xB3E5)
	private final ModifiableCallout<AbilityCastStart> doubleTyrannhilation = ModifiableCallout.durationBasedCall("Double Tyrannhilation", "Hide Behind Meteors");

	@NpcCastCallout(0xB3E8)
	private final ModifiableCallout<AbilityCastStart> flatliner = ModifiableCallout.durationBasedCall("Flatliner", "Short Knockback to Sides");

	@NpcCastCallout(0xB3E9)
	private final ModifiableCallout<AbilityCastStart> majesticMeteor = ModifiableCallout.durationBasedCall("Majestic Meteor", "Bait 3 Puddles");

	private final ModifiableCallout<AbilityCastStart> mammothMeteor = ModifiableCallout.durationBasedCall("Mammoth Meteor", "Proximity, {safe1} or {safe2}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> mammothMeteorSq = SqtTemplates.sq(
			30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xB3EC),
			(e1, s) -> {
				// 隕石は必ず対角の斜めに2つ落ちるので、1つ目で安置が決まる
				Position pos = s.waitForCastLocation(e1).getPos();
				ArenaSector meteor = quadrants.forPosition(pos);
				boolean neSw = meteor == ArenaSector.NORTHEAST || meteor == ArenaSector.SOUTHWEST;
				s.setParam("safe1", neSw ? ArenaSector.NORTHWEST : ArenaSector.NORTHEAST);
				s.setParam("safe2", neSw ? ArenaSector.SOUTHEAST : ArenaSector.SOUTHWEST);
				s.updateCall(mammothMeteor, e1);
				s.waitMs(1_000);
			});

	@NpcCastCallout(value = 0xB3ED, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> explosionTowers = ModifiableCallout.durationBasedCall("Explosion", "Knockback Towers");

	@NpcCastCallout({0xB3F3, 0xB3F5})
	private final ModifiableCallout<AbilityCastStart> avalancheWest = ModifiableCallout.durationBasedCall("Arcadion Avalanche (West Safe)", "Tower Knockback to West");

	@NpcCastCallout({0xB3EF, 0xB3F1})
	private final ModifiableCallout<AbilityCastStart> avalancheEast = ModifiableCallout.durationBasedCall("Arcadion Avalanche (East Safe)", "Tower Knockback to East");

	private final ModifiableCallout<AbilityCastStart> avalancheNorth = ModifiableCallout.durationBasedCall("Arcadion Avalanche Follow-up (North Safe)", "North");
	private final ModifiableCallout<AbilityCastStart> avalancheSouth = ModifiableCallout.durationBasedCall("Arcadion Avalanche Follow-up (South Safe)", "South");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> avalancheNorthSq = callBeforeCastEnd(Duration.ofSeconds(6), avalancheNorth, 0xB3F0, 0xB3F6);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> avalancheSouthSq = callBeforeCastEnd(Duration.ofSeconds(6), avalancheSouth, 0xB3F2, 0xB3F4);

	@NpcCastCallout(0xB3FF)
	private final ModifiableCallout<AbilityCastStart> heartbreakKick = ModifiableCallout.durationBasedCall("Heartbreak Kick", "Stack in Tower");
}
