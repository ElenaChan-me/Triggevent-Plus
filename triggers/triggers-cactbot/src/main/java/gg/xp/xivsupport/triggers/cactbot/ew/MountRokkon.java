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
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.MapEffectEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.models.ArenaPos;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/dungeon/mount_rokkon.ts をもとに移植
@CalloutRepo(name = "Mount Rokkon", duty = KnownDuty.MountRokkon)
public class MountRokkon extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long SEAL_FIRE = 0x837A;
	private static final long SEAL_WIND = 0x837B;
	private static final long SEAL_THUNDER = 0x837C;
	private static final long SEAL_RAIN = 0x837D;

	private static final long TATAMI_SHAKE_FLAGS = 0x00020001;
	private static final long TATAMI_OUTSIDE_NORTH = 0x37;
	private static final long TATAMI_INSIDE_NORTH = 0x38;
	private static final long TATAMI_INSIDE_SOUTH = 0x39;
	private static final long TATAMI_OUTSIDE_SOUTH = 0x3A;

	private static final long KASUMI_GIRI_VFX = 0xB9A;
	private static final long KASUMI_BACK = 0x248;
	private static final long KASUMI_LEFT = 0x249;
	private static final long KASUMI_FRONT = 0x24A;
	private static final long KASUMI_RIGHT = 0x24B;

	private static final long FLICKERING_FLAME = 0x84D5;
	private static final long SULPHURIC_STONE = 0x84D6;
	private static final long FLAME_AND_SULPHUR = 0x84D7;

	// 白丸の詠唱位置で東西どちらにいるかを判定する
	private static final double YOZAKURA_CENTER_X = 737;

	private static final ArenaPos shishioArena = new ArenaPos(-40, -300, 5, 5);

	private XivState state;

	public MountRokkon(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.MountRokkon);
	}

	// 発動前に花のギミックを2つだけ表示するので、出現したものから発動済みのものを除いて残りを判定する
	private final List<Long> yozakuraSeals = new ArrayList<>();
	private final Set<Long> shakenTatami = new HashSet<>();
	private boolean doubleKasumiGiri;
	private String firstKasumiGiri;
	private long goraiSummon;
	private boolean pipeCleanerOnMe;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		yozakuraSeals.clear();
		shakenTatami.clear();
		doubleKasumiGiri = false;
		firstKasumiGiri = null;
		goraiSummon = 0;
		pipeCleanerOnMe = false;
	}

	// 夜桜

	@NpcCastCallout(0x83A9)
	private final ModifiableCallout<AbilityCastStart> gloryNeverlasting = ModifiableCallout.durationBasedCall("Glory Neverlasting", "Buster on {event.target}");

	@NpcCastCallout(0x8369)
	private final ModifiableCallout<AbilityCastStart> artOfTheWindblossom = ModifiableCallout.durationBasedCall("Art of the Windblossom", "In");

	@NpcCastCallout(0x8368)
	private final ModifiableCallout<AbilityCastStart> artOfTheFireblossom = ModifiableCallout.durationBasedCall("Art of the Fireblossom", "Out");

	@NpcCastCallout(0x8380)
	private final ModifiableCallout<AbilityCastStart> shadowflight = ModifiableCallout.durationBasedCall("Shadowflight", "Move Away");

	@NpcCastCallout(0x83AA)
	private final ModifiableCallout<AbilityCastStart> kugeRantsui = ModifiableCallout.durationBasedCall("Kuge Rantsui", "Raidwide");

	@NpcCastCallout(0x8393)
	private final ModifiableCallout<AbilityCastStart> driftingPetals = ModifiableCallout.durationBasedCall("Drifting Petals", "Unavoidable Knockback");

	private final ModifiableCallout<AbilityCastStart> riotousBloomInCards = ModifiableCallout.durationBasedCall("Seal of Riotous Bloom (In + Cardinals)", "In + Cardinals");
	private final ModifiableCallout<AbilityCastStart> riotousBloomInIntercards = ModifiableCallout.durationBasedCall("Seal of Riotous Bloom (In + Intercards)", "In + Intercards");
	private final ModifiableCallout<AbilityCastStart> riotousBloomOutCards = ModifiableCallout.durationBasedCall("Seal of Riotous Bloom (Out + Cardinals)", "Out + Cardinals");
	private final ModifiableCallout<AbilityCastStart> riotousBloomOutIntercards = ModifiableCallout.durationBasedCall("Seal of Riotous Bloom (Out + Intercards)", "Out + Intercards");

	private final ModifiableCallout<AbilityCastStart> artOfTheFluffWest = ModifiableCallout.durationBasedCall("Art of the Fluff (West)", "Look West");
	private final ModifiableCallout<AbilityCastStart> artOfTheFluffEast = ModifiableCallout.durationBasedCall("Art of the Fluff (East)", "Look East");

	private final ModifiableCallout<AbilityCastStart> tatamiInsideNorth = ModifiableCallout.durationBasedCall("Tatami-gaeshi (Inside North)", "Inside North");
	private final ModifiableCallout<AbilityCastStart> tatamiInsideSouth = ModifiableCallout.durationBasedCall("Tatami-gaeshi (Inside South)", "Inside South");
	private final ModifiableCallout<AbilityCastStart> tatamiOutsideNorth = ModifiableCallout.durationBasedCall("Tatami-gaeshi (Outside North)", "Outside North");
	private final ModifiableCallout<AbilityCastStart> tatamiOutsideSouth = ModifiableCallout.durationBasedCall("Tatami-gaeshi (Outside South)", "Outside South");

	@PlayerHeadmarker(0xC5)
	private final ModifiableCallout<HeadMarkerEvent> rootArrangement = new ModifiableCallout<>("Root Arrangement", "4x Chasing AoE on You");

	@HandleEvents
	public void sealCollect(EventContext context, AbilityUsedEvent event) {
		if (event.isFirstTarget() && event.abilityIdMatches(SEAL_FIRE, SEAL_WIND, SEAL_THUNDER, SEAL_RAIN)) {
			yozakuraSeals.add(event.getAbility().getId());
		}
	}

	@HandleEvents
	public void tatamiCollect(EventContext context, MapEffectEvent event) {
		if (event.flagsMatches(TATAMI_SHAKE_FLAGS)) {
			shakenTatami.add(event.getLocation());
		}
	}

	@HandleEvents
	public void yozakuraCasts(EventContext context, AbilityCastStart event) {
		// 発動済みの花を取り除いて、残りの組み合わせを後で判定できるようにする
		if (event.abilityIdMatches(0x8375)) {
			yozakuraSeals.remove((Long) SEAL_FIRE);
		}
		else if (event.abilityIdMatches(0x8376)) {
			yozakuraSeals.remove((Long) SEAL_WIND);
		}
		else if (event.abilityIdMatches(0x8377)) {
			yozakuraSeals.remove((Long) SEAL_RAIN);
		}
		else if (event.abilityIdMatches(0x8378)) {
			yozakuraSeals.remove((Long) SEAL_THUNDER);
		}
		else if (event.abilityIdMatches(0x8374)) {
			// どの花が有効かはログから分からないので、2つに絞れたときだけ知らせる
			if (yozakuraSeals.size() != 2) {
				return;
			}
			boolean in = yozakuraSeals.contains(SEAL_WIND);
			boolean cards = yozakuraSeals.contains(SEAL_RAIN);
			ModifiableCallout<AbilityCastStart> call;
			if (in) {
				call = cards ? riotousBloomInCards : riotousBloomInIntercards;
			}
			else {
				call = cards ? riotousBloomOutCards : riotousBloomOutIntercards;
			}
			context.accept(call.getModified(event));
		}
		else if (event.abilityIdMatches(0x839E)) {
			Position pos = event.getSource().getPos();
			if (pos == null) {
				return;
			}
			context.accept((pos.getX() > YOZAKURA_CENTER_X ? artOfTheFluffWest : artOfTheFluffEast).getModified(event));
		}
		else if (event.abilityIdMatches(0x8395)) {
			// 揺れていない畳が安全。内側を優先して知らせる
			ModifiableCallout<AbilityCastStart> call = null;
			if (!shakenTatami.contains(TATAMI_INSIDE_NORTH)) {
				call = tatamiInsideNorth;
			}
			else if (!shakenTatami.contains(TATAMI_INSIDE_SOUTH)) {
				call = tatamiInsideSouth;
			}
			else if (!shakenTatami.contains(TATAMI_OUTSIDE_NORTH)) {
				call = tatamiOutsideNorth;
			}
			else if (!shakenTatami.contains(TATAMI_OUTSIDE_SOUTH)) {
				call = tatamiOutsideSouth;
			}
			shakenTatami.clear();
			if (call != null) {
				context.accept(call.getModified(event));
			}
		}
	}

	// 木曾谷のモウコ

	@NpcCastCallout(0x85AD)
	private final ModifiableCallout<AbilityCastStart> kenkiRelease = ModifiableCallout.durationBasedCall("Kenki Release", "Raidwide");

	@NpcCastCallout(0x8598)
	private final ModifiableCallout<AbilityCastStart> scarletAuspice = ModifiableCallout.durationBasedCall("Scarlet Auspice", "Out");

	@NpcCastCallout(0x859C)
	private final ModifiableCallout<AbilityCastStart> azureAuspice = ModifiableCallout.durationBasedCall("Azure Auspice", "In");

	private final ModifiableCallout<BuffApplied> kasumiGiriBack = new ModifiableCallout<>("Iai-kasumi-giri (Back)", "Back");
	private final ModifiableCallout<BuffApplied> kasumiGiriLeft = new ModifiableCallout<>("Iai-kasumi-giri (Left)", "Left");
	private final ModifiableCallout<BuffApplied> kasumiGiriFront = new ModifiableCallout<>("Iai-kasumi-giri (Front)", "Front");
	private final ModifiableCallout<BuffApplied> kasumiGiriRight = new ModifiableCallout<>("Iai-kasumi-giri (Right)", "Right");
	private final ModifiableCallout<BuffApplied> doubleKasumiGiriFirst = new ModifiableCallout<>("Double Kasumi-giri (First)", "({dir} First)");
	private final ModifiableCallout<BuffApplied> doubleKasumiGiriSecond = new ModifiableCallout<>("Double Kasumi-giri (Second)", "{dir1} => {dir2}", 6_000);

	private static String kasumiGiriDir(long vfx) {
		if (vfx == KASUMI_BACK) {
			return "Back";
		}
		if (vfx == KASUMI_LEFT) {
			return "Left";
		}
		if (vfx == KASUMI_FRONT) {
			return "Front";
		}
		if (vfx == KASUMI_RIGHT) {
			return "Right";
		}
		return null;
	}

	// 2回目の斬りは1回目の位置からの動き方で知らせる
	private static String kasumiGiriFollowUp(long vfx) {
		if (vfx == KASUMI_FRONT) {
			return "Run Through";
		}
		if (vfx == KASUMI_BACK) {
			return "Stay";
		}
		if (vfx == KASUMI_LEFT) {
			return "Rotate Left";
		}
		if (vfx == KASUMI_RIGHT) {
			return "Rotate Right";
		}
		return null;
	}

	@HandleEvents
	public void doubleKasumiGiriCheck(EventContext context, AbilityCastStart event) {
		// この詠唱がVFXより先に来るので、ここで二連続かどうかを判別する
		if (event.abilityIdMatches(0x858B, 0x858C, 0x858D, 0x858E)) {
			doubleKasumiGiri = true;
		}
	}

	@HandleEvents
	public void kasumiGiri(EventContext context, BuffApplied event) {
		if (!event.buffIdMatches(KASUMI_GIRI_VFX)) {
			return;
		}
		long vfx = event.getRawStacks();
		String dir = kasumiGiriDir(vfx);
		if (dir == null) {
			return;
		}
		if (!doubleKasumiGiri) {
			ModifiableCallout<BuffApplied> call;
			if (vfx == KASUMI_BACK) {
				call = kasumiGiriBack;
			}
			else if (vfx == KASUMI_LEFT) {
				call = kasumiGiriLeft;
			}
			else if (vfx == KASUMI_FRONT) {
				call = kasumiGiriFront;
			}
			else {
				call = kasumiGiriRight;
			}
			context.accept(call.getModified(event));
		}
		else if (firstKasumiGiri == null) {
			firstKasumiGiri = dir;
			context.accept(doubleKasumiGiriFirst.getModified(event, Map.of("dir", dir)));
		}
		else {
			context.accept(doubleKasumiGiriSecond.getModified(event, Map.of("dir1", firstKasumiGiri, "dir2", kasumiGiriFollowUp(vfx))));
			firstKasumiGiri = null;
			doubleKasumiGiri = false;
		}
	}

	// 怒りの剛雷

	@NpcCastCallout(0x8500)
	private final ModifiableCallout<AbilityCastStart> unenlightenment = ModifiableCallout.durationBasedCall("Unenlightenment", "Raidwide");

	@NpcCastCallout(0x84F8)
	private final ModifiableCallout<AbilityCastStart> fightingSpirits = ModifiableCallout.durationBasedCall("Fighting Spirits", "Raidwide");

	@NpcCastCallout(0x84FD)
	private final ModifiableCallout<AbilityCastStart> biwaBreaker = ModifiableCallout.durationBasedCall("Biwa Breaker", "Raidwide");

	@NpcCastCallout(0x84FE)
	private final ModifiableCallout<AbilityCastStart> torchingTorment = ModifiableCallout.durationBasedCall("Torching Torment", "Buster on {event.target}");

	@NpcCastCallout(0x84E1)
	private final ModifiableCallout<AbilityCastStart> malformedPrayer = ModifiableCallout.durationBasedCall("Malformed Prayer", "Stand in All Towers");

	private final ModifiableCallout<AbilityCastStart> plectrumLines = ModifiableCallout.durationBasedCall("Plectrum of Power (Lines)", "Avoid Expanding Lines");
	private final ModifiableCallout<AbilityCastStart> plectrumRocks = ModifiableCallout.durationBasedCall("Plectrum of Power (Rocks)", "Avoid Expanding Rocks");
	private final ModifiableCallout<AbilityCastStart> plectrumBoth = ModifiableCallout.durationBasedCall("Plectrum of Power (Both)", "Avoid Expanding Rocks/Lines");
	private final ModifiableCallout<AbilityCastStart> morphicLines = ModifiableCallout.durationBasedCall("Morphic Melody (Lines)", "Go Into Middle");
	private final ModifiableCallout<AbilityCastStart> morphicRocks = ModifiableCallout.durationBasedCall("Morphic Melody (Rocks)", "Stand on Rock");
	private final ModifiableCallout<AbilityCastStart> morphicBoth = ModifiableCallout.durationBasedCall("Morphic Melody (Both)", "Stand on Rock + Line");

	@HandleEvents
	public void goraiCasts(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(FLICKERING_FLAME, SULPHURIC_STONE, FLAME_AND_SULPHUR)) {
			goraiSummon = event.getAbility().getId();
			return;
		}
		ModifiableCallout<AbilityCastStart> lines;
		ModifiableCallout<AbilityCastStart> rocks;
		ModifiableCallout<AbilityCastStart> both;
		if (event.abilityIdMatches(0x84D8)) {
			lines = plectrumLines;
			rocks = plectrumRocks;
			both = plectrumBoth;
		}
		else if (event.abilityIdMatches(0x84D9)) {
			lines = morphicLines;
			rocks = morphicRocks;
			both = morphicBoth;
		}
		else {
			return;
		}
		ModifiableCallout<AbilityCastStart> call = null;
		if (goraiSummon == FLICKERING_FLAME) {
			call = lines;
		}
		else if (goraiSummon == SULPHURIC_STONE) {
			call = rocks;
		}
		else if (goraiSummon == FLAME_AND_SULPHUR) {
			call = both;
		}
		goraiSummon = 0;
		if (call != null) {
			context.accept(call.getModified(event));
		}
	}

	// 獅子王

	@NpcCastCallout(0x83F5)
	private final ModifiableCallout<AbilityCastStart> enkyo = ModifiableCallout.durationBasedCall("Enkyo", "Raidwide");

	@NpcCastCallout(0x83F6)
	private final ModifiableCallout<AbilityCastStart> splittingCry = ModifiableCallout.durationBasedCall("Splitting Cry", "Buster on {event.target}");

	@NpcCastCallout(0x83E6)
	private final ModifiableCallout<AbilityCastStart> noblePursuit = ModifiableCallout.durationBasedCall("Noble Pursuit", "Between Rings + Outside Line");

	@NpcCastCallout(0x83F4)
	private final ModifiableCallout<AbilityCastStart> thunderVortex = ModifiableCallout.durationBasedCall("Thunder Vortex", "Get Under");

	private final ModifiableCallout<AbilityCastStart> devilishThrallSafe = ModifiableCallout.durationBasedCall("Devilish Thrall Safe Spot", "{safe}");

	// 4体の怨霊のうち2体は内向き、2体は外向きなので、外向きの2体の間が安全
	@AutoFeed
	private final SequentialTrigger<BaseEvent> devilishThrallSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x83F0, 0x83F1),
			(e1, s) -> {
				List<AbilityCastStart> casts = new ArrayList<>();
				casts.add(e1);
				casts.addAll(s.waitEventsQuickSuccession(3, AbilityCastStart.class, acs -> acs.abilityIdMatches(0x83F0, 0x83F1), Duration.ofMillis(500)));
				if (casts.size() != 4) {
					return;
				}
				s.refreshCombatants(100);
				List<ArenaSector> outward = new ArrayList<>();
				for (AbilityCastStart cast : casts) {
					XivCombatant thrall = state.getLatestCombatantData(cast.getSource());
					ArenaSector pos = shishioArena.forCombatant(thrall);
					ArenaSector facing = ArenaPos.combatantFacing(thrall);
					if (facing != pos.opposite()) {
						outward.add(pos);
					}
				}
				if (outward.size() != 2) {
					return;
				}
				ArenaSector safe;
				if (outward.stream().allMatch(ArenaSector::isCardinal)) {
					safe = ArenaSector.tryCombineTwoCardinals(outward);
				}
				else {
					safe = ArenaSector.tryCombineTwoQuadrants(outward);
				}
				if (safe != null) {
					s.setParam("safe", safe);
					s.updateCall(devilishThrallSafe, e1);
				}
			});

	// 煙煙羅

	@NpcCastCallout(value = 0x8042, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> flagrantCombustion = ModifiableCallout.durationBasedCall("Flagrant Combustion", "Raidwide");

	@NpcCastCallout(0x8053)
	private final ModifiableCallout<AbilityCastStart> smokeRings = ModifiableCallout.durationBasedCall("Smoke Rings", "Out");

	@NpcCastCallout(0x8052)
	private final ModifiableCallout<AbilityCastStart> clearingSmoke = ModifiableCallout.durationBasedCall("Clearing Smoke", "Knockback");

	@NpcCastCallout(0x8056)
	private final ModifiableCallout<AbilityCastStart> snuff = ModifiableCallout.durationBasedCall("Snuff", "Tank Cleave");

	private final ModifiableCallout<AbilityCastStart> pipeCleaner = ModifiableCallout.durationBasedCall("Pipe Cleaner", "Earthshaker on You");
	private final ModifiableCallout<AbilityUsedEvent> uplift = new ModifiableCallout<>("Uplift", "Move Away");

	@HandleEvents
	public void pipeCleanerTether(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x11) && event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			pipeCleanerOnMe = true;
		}
	}

	@HandleEvents
	public void pipeCleanerCast(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8054)) {
			if (pipeCleanerOnMe) {
				context.accept(pipeCleaner.getModified(event));
			}
			pipeCleanerOnMe = false;
		}
	}

	// スナッフを受けた人は直後のアップリフトから離れる
	@HandleEvents
	public void snuffHit(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x8056) && event.getTarget().isThePlayer()) {
			context.accept(uplift.getModified(event));
		}
	}

}
