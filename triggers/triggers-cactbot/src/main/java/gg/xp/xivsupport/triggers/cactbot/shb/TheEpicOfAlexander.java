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
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.BuffRemoved;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/ultimate/the_epic_of_alexander.ts をもとに移植
@CalloutRepo(name = "The Epic Of Alexander Ultimate", duty = KnownDuty.TEA)
public class TheEpicOfAlexander extends AutoChildEventHandler implements FilteredEventHandler {

	// 頭上マーカーはインスタンスごとにずれるので、戦闘最初のマーカー（リミットカット1）を基準に補正する
	private static final long limitCutFirstMarker = 0x4F;
	private static final long limitCutLastMarker = 0x56;
	private static final long enumerationMarker = 0x41;
	private static final long iceMarker = 0x43;
	private static final long opticalStackMarker = 0x3E;
	private static final long incineratingHeatMarker = 0x5D;
	private static final long judgmentCrystalMarker = 0x60;

	private static final long[] decreeNisi = {0x8AE, 0x8AF, 0x859, 0x85A};
	private static final long[] finalJudgmentNisi = {0x8B0, 0x8B1, 0x85B, 0x85C};
	private static final String[] nisiNames = {"Blue Alpha", "Orange Beta", "Purple Gamma", "Green Delta"};

	// ノックバックの警告はリミットカットの処理の5秒前に出す
	private static final long limitCutKnockbackLeadMs = 5_000;
	private static final long[] limitCutDelayWormholeMs = {9_200, 10_700, 13_400, 15_000, 17_700, 19_200, 22_000, 23_400};
	private static final long[] limitCutDelayMs = {9_500, 11_000, 14_100, 15_500, 18_600, 20_000, 23_200, 24_600};

	// 共有の受け手が3人以上いる場合は、タンクが軽減を使ってジャンプを受け持つ想定
	private static final int inceptionVulnThreshold = 5;
	private static final int alphaMinimumClones = 5;
	private static final double arenaCenter = 100;

	private XivState state;

	public TheEpicOfAlexander(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TEA);
	}

	private String phase;
	private int handOfPainCount;
	private XivCombatant cruiseTank;
	private boolean seenGavel;
	private Integer limitCutNumber;
	private long limitCutDisplayMs = limitCutDelayMs[0];
	private final List<XivCombatant> enumerations = new ArrayList<>();
	private final Set<Long> buffMap = new HashSet<>();
	private final Set<Long> vuln = new HashSet<>();
	private final Map<Long, XivCombatant> tetherBois = new HashMap<>();
	private final Map<Long, Integer> nisiMap = new HashMap<>();
	private final Map<Long, Integer> finalNisiMap = new HashMap<>();
	private final Map<Long, XivCombatant> nisiPlayers = new HashMap<>();
	private final List<XivCombatant> opticalStack = new ArrayList<>();
	private XivCombatant alphaSolidarity;
	private XivCombatant alphaDefamation;
	private final List<XivCombatant> alphaSeverity = new ArrayList<>();
	private final List<XivCombatant> alphaNoDebuff = new ArrayList<>();
	private String firstAlphaOrdained;
	private boolean secondAlphaMotion;
	private final List<XivCombatant> betaBait = new ArrayList<>();
	private ArenaSector radiantDir;
	private boolean betaIsOpticalStack;
	private final List<Character> trine = new ArrayList<>();
	private ArenaSector secondTrineDir;
	private final Map<Long, BaseEvent> lastCalls = new HashMap<>();

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		phase = null;
		handOfPainCount = 0;
		cruiseTank = null;
		seenGavel = false;
		nisiMap.clear();
		finalNisiMap.clear();
		nisiPlayers.clear();
		opticalStack.clear();
		alphaSolidarity = null;
		alphaDefamation = null;
		alphaSeverity.clear();
		alphaNoDebuff.clear();
		firstAlphaOrdained = null;
		secondAlphaMotion = false;
		betaBait.clear();
		radiantDir = null;
		betaIsOpticalStack = false;
		trine.clear();
		secondTrineDir = null;
		lastCalls.clear();
		resetState();
	}

	private void resetState() {
		enumerations.clear();
		buffMap.clear();
		tetherBois.clear();
		vuln.clear();
		limitCutNumber = null;
	}

	// 同じ技が複数の対象に出るものは、一定時間内の2回目以降を無視する
	private boolean suppressed(long key, BaseEvent event, long ms) {
		BaseEvent last = lastCalls.get(key);
		if (last != null && Duration.between(last.getEffectiveHappenedAt(), event.getEffectiveHappenedAt()).toMillis() < ms) {
			return true;
		}
		lastCalls.put(key, event);
		return false;
	}

	private static long correctedMarker(HeadMarkerEvent event) {
		return limitCutFirstMarker + event.getMarkerOffset();
	}

	private static boolean isLimitCutMarker(HeadMarkerEvent event) {
		long id = correctedMarker(event);
		return id >= limitCutFirstMarker && id <= limitCutLastMarker;
	}

	private static boolean isMe(@Nullable XivCombatant cbt) {
		return cbt != null && cbt.isThePlayer();
	}

	private static String joinNames(List<XivCombatant> players) {
		return players.stream()
				.map(XivCombatant::getName)
				.sorted()
				.collect(Collectors.joining(", "));
	}

	private static int indexOf(long[] ids, long id) {
		for (int i = 0; i < ids.length; i++) {
			if (ids[i] == id) {
				return i;
			}
		}
		return -1;
	}

	@HandleEvents
	public void phaseTracker(EventContext context, AbilityCastStart event) {
		String newPhase;
		if (event.abilityIdMatches(0x483E)) {
			newPhase = "brute";
		}
		else if (event.abilityIdMatches(0x486F)) {
			newPhase = "inception";
		}
		else if (event.abilityIdMatches(0x486E)) {
			newPhase = "wormhole";
		}
		else if (event.abilityIdMatches(0x487B)) {
			newPhase = "alpha";
		}
		else if (event.abilityIdMatches(0x4B13)) {
			newPhase = "beta";
		}
		else {
			return;
		}
		phase = newPhase;
		resetState();
	}

	@HandleEvents
	public void tankTracker(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x497A)) {
			cruiseTank = event.getTarget();
		}
	}

	// リビングリキッド

	@NpcCastCallout(0x4826)
	private final ModifiableCallout<AbilityCastStart> cascade = ModifiableCallout.durationBasedCall("Cascade", "Raidwide");

	@NpcCastCallout(0x4822)
	private final ModifiableCallout<AbilityCastStart> proteanWave = ModifiableCallout.durationBasedCall("Protean Wave", "Protean Wave");

	private final ModifiableCallout<TetherEvent> drainageTether = new ModifiableCallout<>("Drainage Tether", "Drainage Tether on You");

	@HandleEvents
	public void drainage(EventContext context, TetherEvent event) {
		// 線を正しく取れていても何度も付け直されるので間引く
		if (event.tetherIdMatches(0x03) && event.eitherTargetMatches(XivCombatant::isThePlayer) && !suppressed(0x03, event, 5_000)) {
			context.accept(drainageTether.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> handOfPain5 = ModifiableCallout.durationBasedCall("Hand of Pain (5th)", "Focus Living Liquid");

	@HandleEvents
	public void handOfPain(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x482D)) {
			handOfPainCount++;
			if (handOfPainCount == 5) {
				context.accept(handOfPain5.getModified(event));
			}
		}
	}

	private final ModifiableCallout<BuffApplied> throttle = new ModifiableCallout<BuffApplied>("Throttle", "Cleanse Throttle").statusIcon(0x2BC);

	@HandleEvents
	public void throttleGained(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x2BC)
				&& state.playerJobMatches(job -> job.isHealer() || job == Job.BRD)
				&& !suppressed(0x2BC, event, 1_000)) {
			context.accept(throttle.getModified(event));
		}
	}

	// リミットカット

	private final ModifiableCallout<HeadMarkerEvent> limitCutNumberCall = new ModifiableCallout<HeadMarkerEvent>("Limit Cut Number", "{num}", "#{num}",
			hme -> hme.getEffectiveTimeSince().toMillis() > limitCutDisplayMs);

	private long limitCutDelay(int num) {
		long[] delays = "wormhole".equals(phase) ? limitCutDelayWormholeMs : limitCutDelayMs;
		return delays[num - 1];
	}

	@HandleEvents
	public void limitCutNumbers(EventContext context, HeadMarkerEvent event) {
		if (!event.getTarget().isThePlayer() || !isLimitCutMarker(event)) {
			return;
		}
		int num = (int) (correctedMarker(event) - limitCutFirstMarker) + 1;
		limitCutNumber = num;
		// 忘れやすいので、自分の番が終わるまで番号を表示し続ける
		limitCutDisplayMs = limitCutDelay(num);
		context.accept(limitCutNumberCall.getModified(event, Map.of("num", num)));
	}

	private final ModifiableCallout<HeadMarkerEvent> limitCutCleaveWormhole = new ModifiableCallout<>("Limit Cut: Odd (Wormhole)", "Knockback Cleave, Face Outside");
	private final ModifiableCallout<HeadMarkerEvent> limitCutChargeWormhole = new ModifiableCallout<>("Limit Cut: Even (Wormhole)", "Knockback Charge, Face Middle");
	private final ModifiableCallout<HeadMarkerEvent> limitCutCleave = new ModifiableCallout<>("Limit Cut: Odd", "Knockback Cleave on You");
	private final ModifiableCallout<HeadMarkerEvent> limitCutKnockback = new ModifiableCallout<>("Limit Cut: Even", "Knockback");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> limitCutKnockbackSq = SqtTemplates.sq(30_000,
			HeadMarkerEvent.class, hme -> hme.getTarget().isThePlayer() && isLimitCutMarker(hme),
			(e1, s) -> {
				int num = (int) (correctedMarker(e1) - limitCutFirstMarker) + 1;
				boolean wormhole = "wormhole".equals(phase);
				// アームズレングスを間に合わせるため、処理の5秒前に知らせる
				long waitMs = limitCutDelay(num) - limitCutKnockbackLeadMs;
				if (waitMs > 0) {
					s.waitMs(waitMs);
				}
				boolean odd = (correctedMarker(e1) & 1) == 1;
				if (wormhole) {
					s.updateCall(odd ? limitCutCleaveWormhole : limitCutChargeWormhole, e1);
				}
				else {
					s.updateCall(odd ? limitCutCleave : limitCutKnockback, e1);
				}
			});

	// クルーズチェイサー・ブルートジャスティス

	private final ModifiableCallout<AbilityUsedEvent> chakramsOut = new ModifiableCallout<>("Link-up (Chakrams)", "Out, Dodge Chakrams");

	@HandleEvents
	public void linkUp(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x483F) && event.isFirstTarget() && "brute".equals(phase)) {
			context.accept(chakramsOut.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> chakramsIn = new ModifiableCallout<>("Optical Sight (Chakrams)", "Run In");

	@HandleEvents
	public void opticalSight(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x482F) && !suppressed(0x482F, event, 1_000)) {
			context.accept(chakramsIn.getModified(event));
		}
	}

	@NpcCastCallout(0x49C2)
	private final ModifiableCallout<AbilityCastStart> whirlwind = ModifiableCallout.durationBasedCall("Whirlwind", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> spinCrusher = ModifiableCallout.durationBasedCall("Spin Crusher", "Dodge Spin Crusher");

	@HandleEvents
	public void spinCrusherCast(EventContext context, AbilityCastStart event) {
		// チェイサーの正面にはタンクしかいないはずなので、タンクにだけ知らせる
		if (event.abilityIdMatches(0x4A72) && isMe(cruiseTank)) {
			context.accept(spinCrusher.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> freezeTornado = new ModifiableCallout<>("Ice Marker", "Freeze Tornado");

	@HandleEvents
	public void iceMarkerOnMe(EventContext context, HeadMarkerEvent event) {
		if (event.getTarget().isThePlayer() && correctedMarker(event) == iceMarker) {
			context.accept(freezeTornado.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> mines = new ModifiableCallout<>("Hidden Minefield", "Mines");

	@HandleEvents
	public void hiddenMinefield(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x4851) && state.playerJobMatches(Job::isTank) && !suppressed(0x4851, event, 1_000)) {
			context.accept(mines.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> enumerationOnYou = new ModifiableCallout<>("Enumeration (On You)", "Enumeration on You");
	private final ModifiableCallout<HeadMarkerEvent> enumerationPlayers = new ModifiableCallout<>("Enumeration (Targets)", "Enumeration: {players}");

	@HandleEvents
	public void enumeration(EventContext context, HeadMarkerEvent event) {
		if (correctedMarker(event) != enumerationMarker) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(enumerationOnYou.getModified(event));
		}
		enumerations.add(event.getTarget());
		if (enumerations.size() == 2) {
			context.accept(enumerationPlayers.getModified(event, Map.of("players", joinNames(enumerations))));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> limitCutShield = new ModifiableCallout<>("Limit Cut Shield", "Break Shield From Front");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> limitCutShieldSq = SqtTemplates.sq(5_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x4833) && aue.isFirstTarget(),
			(e1, s) -> {
				s.waitMs(2_000);
				s.updateCall(limitCutShield, e1);
			});

	private final ModifiableCallout<BuffApplied> waterOnYou = new ModifiableCallout<BuffApplied>("Compressed Water", "Water on You").statusIcon(0x85E);
	private final ModifiableCallout<BuffApplied> dropWater = ModifiableCallout.<BuffApplied>durationBasedCall("Compressed Water (Drop)", "Drop Water Soon").statusIcon(0x85E);
	private final ModifiableCallout<BuffApplied> lightningOnYou = new ModifiableCallout<BuffApplied>("Compressed Lightning", "Lightning on You").statusIcon(0x85F);
	private final ModifiableCallout<BuffApplied> dropLightning = ModifiableCallout.<BuffApplied>durationBasedCall("Compressed Lightning (Drop)", "Drop Lightning Soon").statusIcon(0x85F);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> compressedWaterSq = SqtTemplates.sq(60_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0x85E) && ba.getTarget().isThePlayer(),
			(e1, s) -> {
				s.updateCall(waterOnYou, e1);
				long waitMs = e1.getEstimatedRemainingDuration().minus(Duration.ofSeconds(5)).toMillis();
				if (waitMs > 0) {
					s.waitMs(waitMs);
				}
				// 審判の後は落とす場所が決まっているので不要
				if (!seenGavel) {
					s.updateCall(dropWater, e1);
				}
			});

	@AutoFeed
	private final SequentialTrigger<BaseEvent> compressedLightningSq = SqtTemplates.sq(60_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0x85F) && ba.getTarget().isThePlayer(),
			(e1, s) -> {
				s.updateCall(lightningOnYou, e1);
				long waitMs = e1.getEstimatedRemainingDuration().minus(Duration.ofSeconds(5)).toMillis();
				if (waitMs > 0) {
					s.waitMs(waitMs);
				}
				if (!seenGavel) {
					s.updateCall(dropLightning, e1);
				}
			});

	// 確定判決

	private final ModifiableCallout<AbilityCastStart> passNisi1 = new ModifiableCallout<>("Pass Nisi (1st)", "Pass Nisi");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> passNisi1Sq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x4836),
			(e1, s) -> {
				if (suppressed(0x4836, e1, 10_000_000)) {
					return;
				}
				s.waitMs(4_000);
				s.updateCall(passNisi1, e1);
			});

	private final ModifiableCallout<AbilityUsedEvent> passNisi2 = new ModifiableCallout<>("Pass Nisi (2nd)", "Pass Nisi");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> passNisi2Sq = SqtTemplates.sq(5_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x4850) && "brute".equals(phase),
			(e1, s) -> {
				if (suppressed(0x4850, e1, 1_000)) {
					return;
				}
				s.waitMs(1_000);
				s.updateCall(passNisi2, e1);
			});

	private final ModifiableCallout<AbilityCastStart> nisiUnknown = new ModifiableCallout<>("Nisi Pass: Unknown", "Get Final Nisi (?)", 9_000);
	private final ModifiableCallout<AbilityCastStart> nisiPass = new ModifiableCallout<>("Nisi Pass: Pass", "Pass {type} Nisi", 9_000);
	private final ModifiableCallout<AbilityCastStart> nisiPassTo = new ModifiableCallout<>("Nisi Pass: Pass To", "Pass {type} to {players}", 9_000);
	private final ModifiableCallout<AbilityCastStart> nisiGet = new ModifiableCallout<>("Nisi Pass: Get", "Get {type}", 9_000);
	private final ModifiableCallout<AbilityCastStart> nisiGetFrom = new ModifiableCallout<>("Nisi Pass: Get From", "Get {type} from {player}", 9_000);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> passNisi3Sq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x4845),
			(e1, s) -> {
				s.waitMs(8_000);
				long me = state.getPlayer().getId();
				Integer myFinal = finalNisiMap.get(me);
				if (myFinal == null) {
					s.updateCall(nisiUnknown, e1);
					return;
				}
				Integer myNisi = nisiMap.get(me);
				if (myNisi != null) {
					// 自分のナイサイは、同じ確定判決を持っていてナイサイの無い人に渡す
					List<Long> sameFinal = finalNisiMap.entrySet().stream()
							.filter(e -> e.getValue().equals(myNisi) && e.getKey() != me)
							.map(Map.Entry::getKey)
							.toList();
					List<Long> withoutNisi = sameFinal.stream().filter(id -> !nisiMap.containsKey(id)).toList();
					// 渡しが遅れて全員ナイサイ持ちになっていても、確定判決の持ち主だけは出す
					if (withoutNisi.isEmpty()) {
						withoutNisi = sameFinal;
					}
					s.setParam("type", nisiNames[myNisi]);
					if (withoutNisi.isEmpty()) {
						s.updateCall(nisiPass, e1);
						return;
					}
					List<XivCombatant> players = withoutNisi.stream().map(nisiPlayers::get).filter(p -> p != null).toList();
					s.setParam("players", joinNames(players));
					s.updateCall(nisiPassTo, e1);
					return;
				}
				s.setParam("type", nisiNames[myFinal]);
				XivCombatant holder = nisiMap.entrySet().stream()
						.filter(e -> e.getValue().equals(myFinal))
						.map(e -> nisiPlayers.get(e.getKey()))
						.filter(p -> p != null)
						.findFirst()
						.orElse(null);
				if (holder == null) {
					s.updateCall(nisiGet, e1);
				}
				else {
					s.setParam("player", holder);
					s.updateCall(nisiGetFrom, e1);
				}
			});

	private final ModifiableCallout<BuffApplied> verdict = new ModifiableCallout<>("Final Judgment: Verdict", "Verdict: {type} Nisi");

	@HandleEvents
	public void nisiGained(EventContext context, BuffApplied event) {
		long id = event.getBuff().getId();
		int decree = indexOf(decreeNisi, id);
		if (decree >= 0) {
			nisiMap.put(event.getTarget().getId(), decree);
			nisiPlayers.put(event.getTarget().getId(), event.getTarget());
			return;
		}
		int judgment = indexOf(finalJudgmentNisi, id);
		if (judgment >= 0) {
			finalNisiMap.put(event.getTarget().getId(), judgment);
			nisiPlayers.put(event.getTarget().getId(), event.getTarget());
			// 確定判決は延々と更新され続けるので、一度だけ知らせる
			if (event.getTarget().isThePlayer() && !suppressed(0x8B0, event, 10_000_000)) {
				context.accept(verdict.getModified(event, Map.of("type", nisiNames[judgment])));
			}
		}
	}

	@HandleEvents
	public void nisiLost(EventContext context, BuffRemoved event) {
		if (indexOf(decreeNisi, event.getBuff().getId()) >= 0) {
			nisiMap.remove(event.getTarget().getId());
		}
	}

	@HandleEvents
	public void gavel(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x483C)) {
			seenGavel = true;
		}
	}

	private final ModifiableCallout<AbilityCastStart> doubleRocketPunchOnYou = ModifiableCallout.durationBasedCall("Double Rocket Punch (On You)", "Shared Tankbuster on You");
	private final ModifiableCallout<AbilityCastStart> doubleRocketPunchOn = ModifiableCallout.durationBasedCall("Double Rocket Punch (Tank/Healer)", "Shared Tankbuster on {event.target}");
	private final ModifiableCallout<AbilityCastStart> baitSuperJump = ModifiableCallout.durationBasedCall("Double Rocket Punch (DPS)", "Bait Super Jump?");

	@HandleEvents
	public void doubleRocketPunch(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x4847)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(doubleRocketPunchOnYou.getModified(event));
		}
		else if (state.playerJobMatches(job -> job.isTank() || job.isHealer())) {
			context.accept(doubleRocketPunchOn.getModified(event));
		}
		else {
			context.accept(baitSuperJump.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> bruteRay = new ModifiableCallout<>("Apocalyptic Ray", "Avoid Ray");
	private final ModifiableCallout<AbilityUsedEvent> wormholeSuperJump = new ModifiableCallout<AbilityUsedEvent>("Wormhole Strat: Super Jump (#2/#3)", "Move Behind Brute Justice?").disabledByDefault();

	@HandleEvents
	public void bruteRayUsed(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x484A) || !event.isFirstTarget()) {
			return;
		}
		if ("brute".equals(phase)) {
			context.accept(bruteRay.getModified(event));
		}
		else if ("wormhole".equals(phase) && limitCutNumber != null && (limitCutNumber == 2 || limitCutNumber == 3)) {
			context.accept(wormholeSuperJump.getModified(event));
		}
	}

	// 時間停止

	private final ModifiableCallout<BuffApplied> noDebuff = new ModifiableCallout<>("Temporal Stasis: No Debuff", "No Debuff", 10_000);

	@HandleEvents
	public void buffCollection(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x461, 0x462, 0x463, 0x464)) {
			buffMap.add(event.getTarget().getId());
		}
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> noDebuffSq = SqtTemplates.sq(5_000,
			// 時間停止フェーズはデバフが付いた後に始まるので、直前のフェーズで判定する
			BuffApplied.class, ba -> ba.buffIdMatches(0x464) && ("brute".equals(phase) || "inception".equals(phase)),
			(e1, s) -> {
				if (suppressed(0x464, e1, 1_000)) {
					return;
				}
				s.waitMs(500);
				if (!buffMap.contains(state.getPlayer().getId())) {
					s.updateCall(noDebuff, e1);
				}
			});

	@PlayerStatusCallout(0x464)
	private final ModifiableCallout<BuffApplied> restrainingOrder = new ModifiableCallout<BuffApplied>("Restraining Order", "Far Tethers", 10_000).statusIcon(0x464);

	@PlayerStatusCallout(0x463)
	private final ModifiableCallout<BuffApplied> houseArrest = new ModifiableCallout<BuffApplied>("House Arrest", "Close Tethers", 10_000).statusIcon(0x463);

	@PlayerStatusCallout(0x462)
	private final ModifiableCallout<BuffApplied> sharedSentence = new ModifiableCallout<BuffApplied>("Shared Sentence", "Shared Sentence", 10_000).statusIcon(0x462);

	@PlayerStatusCallout(0x461)
	private final ModifiableCallout<BuffApplied> aggravatedAssault = new ModifiableCallout<BuffApplied>("Aggravated Assault", "Thunder", 10_000).statusIcon(0x461);

	private final ModifiableCallout<BuffApplied> sharedSentenceInception = new ModifiableCallout<BuffApplied>("Shared Sentence (Inception)", "Shared Sentence on {event.target}").statusIcon(0x462);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> sharedSentenceInceptionSq = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0x462) && "inception".equals(phase),
			(e1, s) -> {
				s.waitMs(3_000);
				s.updateCall(sharedSentenceInception, e1);
			});

	// アレキサンダー・プライム

	private final ModifiableCallout<AbilityCastStart> chasteningHeatOnYou = ModifiableCallout.durationBasedCall("Chastening Heat (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> chasteningHeatOn = ModifiableCallout.durationBasedCall("Chastening Heat (Tank/Healer)", "Buster on {event.target}");

	@HandleEvents
	public void chasteningHeat(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x4A80)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(chasteningHeatOnYou.getModified(event));
		}
		else if (state.playerJobMatches(job -> job.isTank() || job.isHealer())) {
			context.accept(chasteningHeatOn.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> crystalOnYou = new ModifiableCallout<>("Judgment Crystal", "Crystal on You");

	@HandleEvents
	public void judgmentCrystal(EventContext context, HeadMarkerEvent event) {
		if (event.getTarget().isThePlayer() && correctedMarker(event) == judgmentCrystalMarker) {
			context.accept(crystalOnYou.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> crystalPlacement = new ModifiableCallout<>("Judgment Crystal (Placed)", "Get Away From Crystals");

	@HandleEvents
	public void crystalPlaced(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x485C) && !suppressed(0x485C, event, 100_000)) {
			context.accept(crystalPlacement.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> terashatter = new ModifiableCallout<>("Terashatter", "Bait Brute's Flarethrower");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> terashatterSq = SqtTemplates.sq(5_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x4A88),
			(e1, s) -> {
				if (suppressed(0x4A88, e1, 100_000)) {
					return;
				}
				s.waitMs(1_000);
				s.updateCall(terashatter, e1);
			});

	@HandleEvents
	public void inceptionVuln(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x2B7) && "inception".equals(phase)) {
			vuln.add(event.getTarget().getId());
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> baitJumpWithCooldowns = new ModifiableCallout<>("Inception: Bait Jump (Tank)", "Bait Jump With Cooldowns");
	private final ModifiableCallout<AbilityUsedEvent> baitSword = new ModifiableCallout<>("Inception: Bait Sword", "Bait Sword");
	private final ModifiableCallout<AbilityUsedEvent> baitSwordOrJump = new ModifiableCallout<>("Inception: Bait Sword or Jump", "Bait Sword or Jump?");
	private final ModifiableCallout<AbilityUsedEvent> vulnAvoid = new ModifiableCallout<>("Inception: Vulnerable", "Vuln: Avoid Cleaves and Jump");

	@HandleEvents
	public void inceptionAlphaSword(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x485F) || !event.isFirstTarget() || !"inception".equals(phase)) {
			return;
		}
		boolean myVuln = vuln.contains(state.getPlayer().getId());
		boolean manyVulns = vuln.size() >= inceptionVulnThreshold;
		if (myVuln) {
			if (manyVulns && state.playerJobMatches(Job::isTank)) {
				context.accept(baitJumpWithCooldowns.getModified(event));
			}
			else {
				context.accept(vulnAvoid.getModified(event));
			}
		}
		else {
			context.accept((manyVulns ? baitSword : baitSwordOrJump).getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> wormhole = new ModifiableCallout<>("Wormhole Formation", "Bait Chakrams");

	@HandleEvents
	public void wormholeUsed(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x486E) && event.isFirstTarget()) {
			context.accept(wormhole.getModified(event));
		}
	}

	// 次元断絶のマーチの cactbot 式の処理法。使う人だけ有効にする想定
	private final ModifiableCallout<HeadMarkerEvent> wormholeStrat1 = new ModifiableCallout<HeadMarkerEvent>("Wormhole Strat: #1", "Left To Robot, Look Outside, 3rd Puddle", 10_000).disabledByDefault();
	private final ModifiableCallout<HeadMarkerEvent> wormholeStrat2 = new ModifiableCallout<HeadMarkerEvent>("Wormhole Strat: #2", "Back Right Opposite Robot, Look Middle, 3rd Puddle", 10_000).disabledByDefault();
	private final ModifiableCallout<HeadMarkerEvent> wormholeStrat3 = new ModifiableCallout<HeadMarkerEvent>("Wormhole Strat: #3", "Back Left Opposite Robot, No Puddle", 10_000).disabledByDefault();
	private final ModifiableCallout<HeadMarkerEvent> wormholeStrat4 = new ModifiableCallout<HeadMarkerEvent>("Wormhole Strat: #4", "Right To Robot, No Puddle", 10_000).disabledByDefault();
	private final ModifiableCallout<HeadMarkerEvent> wormholeStrat5 = new ModifiableCallout<HeadMarkerEvent>("Wormhole Strat: #5", "Left Robot Side, then 1st Puddle", 10_000).disabledByDefault();
	private final ModifiableCallout<HeadMarkerEvent> wormholeStrat6 = new ModifiableCallout<HeadMarkerEvent>("Wormhole Strat: #6", "Right Robot Side, then 1st Puddle", 10_000).disabledByDefault();
	private final ModifiableCallout<HeadMarkerEvent> wormholeStrat7 = new ModifiableCallout<HeadMarkerEvent>("Wormhole Strat: #7", "Left Robot Side, then Cardinal, 2nd Puddle", 10_000).disabledByDefault();
	private final ModifiableCallout<HeadMarkerEvent> wormholeStrat8 = new ModifiableCallout<HeadMarkerEvent>("Wormhole Strat: #8", "Right Robot Side, then Cardinal, 2nd Puddle", 10_000).disabledByDefault();

	@HandleEvents
	public void wormholeStrat(EventContext context, HeadMarkerEvent event) {
		if (!"wormhole".equals(phase) || !event.getTarget().isThePlayer() || !isLimitCutMarker(event)) {
			return;
		}
		int num = (int) (correctedMarker(event) - limitCutFirstMarker) + 1;
		ModifiableCallout<HeadMarkerEvent> call = switch (num) {
			case 1 -> wormholeStrat1;
			case 2 -> wormholeStrat2;
			case 3 -> wormholeStrat3;
			case 4 -> wormholeStrat4;
			case 5 -> wormholeStrat5;
			case 6 -> wormholeStrat6;
			case 7 -> wormholeStrat7;
			default -> wormholeStrat8;
		};
		context.accept(call.getModified(event));
	}

	private final ModifiableCallout<HeadMarkerEvent> incineratingHeat = new ModifiableCallout<>("Incinerating Heat", "Stack Middle");

	@HandleEvents
	public void incineratingHeatMarker(EventContext context, HeadMarkerEvent event) {
		if (correctedMarker(event) == incineratingHeatMarker) {
			context.accept(incineratingHeat.getModified(event));
		}
	}

	@NpcCastCallout(0x4A83)
	private final ModifiableCallout<AbilityCastStart> megaHoly = ModifiableCallout.durationBasedCall("Mega Holy", "Big Raidwide");

	private final ModifiableCallout<AbilityCastStart> summonAlexander = new ModifiableCallout<>("Summon Alexander", "Kill Cruise Chaser First");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> summonAlexanderSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x4A55),
			(e1, s) -> {
				s.waitMs(10_400);
				s.updateCall(summonAlexander, e1);
			});

	private final ModifiableCallout<AbilityUsedEvent> divineJudgment = new ModifiableCallout<>("Divine Judgment", "Tank LB");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> divineJudgmentSq = SqtTemplates.sq(10_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x4879) && aue.isFirstTarget() && state.playerJobMatches(Job::isTank),
			(e1, s) -> {
				s.waitMs(6_000);
				s.updateCall(divineJudgment, e1);
			});

	// パーフェクト・アレキサンダー

	@NpcCastCallout(0x488A)
	private final ModifiableCallout<AbilityCastStart> perfectOpticalSight = ModifiableCallout.durationBasedCall("Optical Sight (Spread)", "Spread");

	private final ModifiableCallout<HeadMarkerEvent> opticalStackOnYou = new ModifiableCallout<>("Optical Sight: Stack (On You)", "Stack on You");
	private final ModifiableCallout<HeadMarkerEvent> opticalStackPlayers = new ModifiableCallout<>("Optical Sight: Stack (Targets)", "Optical Stack ({players})");

	@HandleEvents
	public void opticalStackMarker(EventContext context, HeadMarkerEvent event) {
		if (correctedMarker(event) != opticalStackMarker) {
			return;
		}
		opticalStack.add(event.getTarget());
		if (event.getTarget().isThePlayer()) {
			context.accept(opticalStackOnYou.getModified(event));
		}
		else if (opticalStack.size() > 1) {
			context.accept(opticalStackPlayers.getModified(event, Map.of("players", joinNames(opticalStack))));
		}
	}

	@NpcCastCallout(0x487E)
	private final ModifiableCallout<AbilityCastStart> ordainedMotion = ModifiableCallout.durationBasedCall("Ordained Motion", "Keep Moving");

	@NpcCastCallout(0x487F)
	private final ModifiableCallout<AbilityCastStart> ordainedStillness = ModifiableCallout.durationBasedCall("Ordained Stillness", "Stop Everything");

	@PlayerStatusCallout(0x868)
	private final ModifiableCallout<BuffApplied> contactProhibition = new ModifiableCallout<BuffApplied>("Contact Prohibition", "Orange", "Orange (Attract)").statusIcon(0x868);

	@PlayerStatusCallout(0x869)
	private final ModifiableCallout<BuffApplied> contactRegulation = new ModifiableCallout<BuffApplied>("Contact Regulation", "Orange Bait: Get Away").statusIcon(0x869);

	@PlayerStatusCallout(0x86A)
	private final ModifiableCallout<BuffApplied> escapeProhibition = new ModifiableCallout<BuffApplied>("Escape Prohibition", "Purple", "Purple (Repel)").statusIcon(0x86A);

	@PlayerStatusCallout(0x86B)
	private final ModifiableCallout<BuffApplied> escapeDetection = new ModifiableCallout<BuffApplied>("Escape Detection", "Purple Bait: Be in Back of Group").statusIcon(0x86B);

	@HandleEvents
	public void fateTethers(EventContext context, TetherEvent event) {
		if (!event.tetherIdMatches(0x62)) {
			return;
		}
		XivCombatant player = event.getTargetMatching(XivCombatant::isPc);
		XivCombatant clone = event.getTargetMatching(cbt -> !cbt.isPc());
		if (player != null && clone != null) {
			tetherBois.put(clone.getId(), player);
		}
	}

	// 未来の分身はアクターIDの降順に並べると役割が決まる。死んでいる人がいると若い番号から詰まる
	private List<XivCombatant> sortedTetherBois() {
		return tetherBois.entrySet().stream()
				.sorted(Map.Entry.<Long, XivCombatant>comparingByKey(Comparator.reverseOrder()))
				.map(Map.Entry::getValue)
				.toList();
	}

	private static @Nullable XivCombatant getOrNull(List<XivCombatant> list, int index) {
		return index < list.size() ? list.get(index) : null;
	}

	private static boolean containsMe(List<XivCombatant> list) {
		return list.stream().anyMatch(TheEpicOfAlexander::isMe);
	}

	private final ModifiableCallout<TetherEvent> alphaDefamationCall = new ModifiableCallout<>("Fate Alpha: Defamation", "Defamation on You", 28_000);
	private final ModifiableCallout<TetherEvent> alphaSolidarityCall = new ModifiableCallout<>("Fate Alpha: Shared Sentence", "Shared Sentence: Stack", 28_000);
	private final ModifiableCallout<TetherEvent> alphaSeverityCall = new ModifiableCallout<>("Fate Alpha: Severity", "Severity: Avoid Shared Stack", 28_000);
	private final ModifiableCallout<TetherEvent> alphaNoDebuffCall = new ModifiableCallout<>("Fate Alpha: No Debuff", "No Debuff: Shared Stack", 28_000);
	private final ModifiableCallout<TetherEvent> alphaMaybeStack = new ModifiableCallout<>("Fate Alpha: No Clone (Probably Stack)", "No Clone: Probably Stack?", 28_000);
	private final ModifiableCallout<TetherEvent> alphaUnknown = new ModifiableCallout<>("Fate Alpha: No Clone", "No Clone: ???", 28_000);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> alphaInstructionsSq = SqtTemplates.sq(10_000,
			TetherEvent.class, te -> te.tetherIdMatches(0x62) && "alpha".equals(phase),
			(e1, s) -> {
				s.waitMs(2_000);
				List<XivCombatant> sorted = sortedTetherBois();
				alphaSolidarity = getOrNull(sorted, 0);
				alphaDefamation = getOrNull(sorted, 1);
				alphaSeverity.clear();
				alphaNoDebuff.clear();
				for (int i = 2; i < Math.min(sorted.size(), 8); i++) {
					(i < 5 ? alphaSeverity : alphaNoDebuff).add(sorted.get(i));
				}
				if (isMe(alphaDefamation)) {
					s.updateCall(alphaDefamationCall, e1);
				}
				else if (isMe(alphaSolidarity)) {
					s.updateCall(alphaSolidarityCall, e1);
				}
				else if (containsMe(alphaSeverity)) {
					s.updateCall(alphaSeverityCall, e1);
				}
				else if (containsMe(alphaNoDebuff)) {
					s.updateCall(alphaNoDebuffCall, e1);
				}
				else if (tetherBois.size() >= alphaMinimumClones) {
					s.updateCall(alphaMaybeStack, e1);
				}
				else {
					s.updateCall(alphaUnknown, e1);
				}
			});

	private final ModifiableCallout<AbilityUsedEvent> alphaMotionFirst = new ModifiableCallout<>("Fate Alpha: Motion First", "Motion First", 8_000);
	private final ModifiableCallout<AbilityUsedEvent> alphaStillnessFirst = new ModifiableCallout<>("Fate Alpha: Stillness First", "Stillness First", 8_000);
	private final ModifiableCallout<AbilityUsedEvent> alphaMotionSecond = new ModifiableCallout<AbilityUsedEvent>("Fate Alpha: Motion Second", "Motion Second", "{first}, Motion Second", ModifiableCallout.expiresIn(15));
	private final ModifiableCallout<AbilityUsedEvent> alphaStillnessSecond = new ModifiableCallout<AbilityUsedEvent>("Fate Alpha: Stillness Second", "Stillness Second", "{first}, Stillness Second", ModifiableCallout.expiresIn(15));

	@HandleEvents
	public void alphaOrdained(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x4B0D) && !suppressed(0x4B0D, event, 20_000)) {
			firstAlphaOrdained = "Motion First";
			context.accept(alphaMotionFirst.getModified(event));
		}
		else if (event.abilityIdMatches(0x4B0E) && !suppressed(0x4B0E, event, 20_000)) {
			firstAlphaOrdained = "Stillness First";
			context.accept(alphaStillnessFirst.getModified(event));
		}
		else if (event.abilityIdMatches(0x4899) && !suppressed(0x4899, event, 20_000)) {
			secondAlphaMotion = true;
			context.accept(alphaMotionSecond.getModified(event, Map.of("first", firstAlphaOrdained == null ? "Unknown" : firstAlphaOrdained)));
		}
		else if (event.abilityIdMatches(0x489A) && !suppressed(0x489A, event, 20_000)) {
			secondAlphaMotion = false;
			context.accept(alphaStillnessSecond.getModified(event, Map.of("first", firstAlphaOrdained == null ? "Unknown" : firstAlphaOrdained)));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> alphaDefamationFrontLeft = new ModifiableCallout<>("Fate Alpha Safe Spot: Defamation (Front Left)", "Defamation: Front Left", 10_000);
	private final ModifiableCallout<AbilityUsedEvent> alphaDefamationFrontRight = new ModifiableCallout<>("Fate Alpha Safe Spot: Defamation (Front Right)", "Defamation: Front Right", 10_000);
	private final ModifiableCallout<AbilityUsedEvent> alphaPartyBackRight = new ModifiableCallout<>("Fate Alpha Safe Spot: Party (Back Right)", "Party: Back Right", 10_000);
	private final ModifiableCallout<AbilityUsedEvent> alphaPartyBackLeft = new ModifiableCallout<>("Fate Alpha Safe Spot: Party (Back Left)", "Party: Back Left", 10_000);

	@HandleEvents
	public void alphaSafeSpot(EventContext context, AbilityUsedEvent event) {
		// 安置でない分身は 489F を使う
		if (!event.abilityIdMatches(0x49AA) || !event.isFirstTarget()) {
			return;
		}
		Position pos = event.getSource().getPos();
		if (pos == null) {
			return;
		}
		// 分身は左から北西寄り・北北西・北北東・北東寄りの4か所に並び、45度刻みになっている
		double rot0 = Math.atan2(78.28883 - arenaCenter, arenaCenter - 91.00694);
		double rot1 = Math.atan2(91.00694 - arenaCenter, arenaCenter - 78.28883);
		double scale = rot1 - rot0;
		double x = pos.x() - arenaCenter;
		double y = arenaCenter - pos.y();
		long idx = Math.round((Math.atan2(x, y) - rot0) / scale);
		if (idx != 1 && idx != 2) {
			return;
		}
		if (isMe(alphaDefamation)) {
			context.accept((idx == 1 ? alphaDefamationFrontLeft : alphaDefamationFrontRight).getModified(event));
		}
		else {
			context.accept((idx == 1 ? alphaPartyBackRight : alphaPartyBackLeft).getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> alphaResolveMoveFirst = new ModifiableCallout<>("Fate Alpha Resolve: Move First", "Move First");
	private final ModifiableCallout<AbilityUsedEvent> alphaResolveStillnessFirst = new ModifiableCallout<>("Fate Alpha Resolve: Stillness First", "Stillness First");
	private final ModifiableCallout<AbilityUsedEvent> alphaResolveKeepMoving = new ModifiableCallout<>("Fate Alpha Resolve: Keep Moving", "Keep Moving");
	private final ModifiableCallout<AbilityUsedEvent> alphaResolveStop = new ModifiableCallout<>("Fate Alpha Resolve: Stop Everything", "Stop Everything");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> alphaResolveSq = SqtTemplates.sq(15_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x487C) && aue.isFirstTarget(),
			(e1, s) -> {
				// 1回目の処理まで約5秒の時点で知らせる
				s.waitMs(2_200);
				s.updateCall("Motion First".equals(firstAlphaOrdained) ? alphaResolveMoveFirst : alphaResolveStillnessFirst, e1);
				s.waitMs(5_000);
				s.updateCall(secondAlphaMotion ? alphaResolveKeepMoving : alphaResolveStop, e1);
			});

	private final ModifiableCallout<TetherEvent> betaUnknown = new ModifiableCallout<>("Fate Beta: No Clone", "No Clone: Maybe Purple E to S?", 35_000);
	private final ModifiableCallout<TetherEvent> betaPurpleBait = new ModifiableCallout<>("Fate Beta: Purple Bait", "Purple Bait: Bait East", 35_000);
	private final ModifiableCallout<TetherEvent> betaOrangeBait = new ModifiableCallout<>("Fate Beta: Orange Bait", "Orange Bait: Bait North", 35_000);
	private final ModifiableCallout<TetherEvent> betaPurpleNoTether = new ModifiableCallout<>("Fate Beta: Purple, No Tether", "Purple, No Tether: East to West", 35_000);
	private final ModifiableCallout<TetherEvent> betaOrangeNoTether = new ModifiableCallout<>("Fate Beta: Orange, No Tether", "Orange, No Tether: East to North", 35_000);
	private final ModifiableCallout<TetherEvent> betaPurpleCloseTether = new ModifiableCallout<>("Fate Beta: Purple, Close Tether", "Purple, Close Tether: East to North", 35_000);
	private final ModifiableCallout<TetherEvent> betaOrangeCloseTether = new ModifiableCallout<>("Fate Beta: Orange, Close Tether", "Orange, Close Tether: East to North", 35_000);
	private final ModifiableCallout<TetherEvent> betaPurpleFarTether = new ModifiableCallout<>("Fate Beta: Purple, Far Tether", "Purple, Far Tether: East to South", 35_000);
	private final ModifiableCallout<TetherEvent> betaOrangeFarTether = new ModifiableCallout<>("Fate Beta: Orange, Far Tether", "Orange, Far Tether: East to North", 35_000);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> betaInstructionsSq = SqtTemplates.sq(10_000,
			TetherEvent.class, te -> te.tetherIdMatches(0x62) && "beta".equals(phase),
			(e1, s) -> {
				s.waitMs(2_000);
				List<XivCombatant> sorted = sortedTetherBois();
				betaBait.clear();
				for (int i = 0; i < Math.min(sorted.size(), 2); i++) {
					betaBait.add(sorted.get(i));
				}
				int idx = -1;
				for (int i = 0; i < sorted.size(); i++) {
					if (isMe(sorted.get(i))) {
						idx = i;
						break;
					}
				}
				ModifiableCallout<TetherEvent> call = switch (idx) {
					case 0 -> betaPurpleBait;
					case 1 -> betaOrangeBait;
					case 2 -> betaPurpleNoTether;
					case 3 -> betaOrangeNoTether;
					case 4 -> betaPurpleCloseTether;
					case 5 -> betaOrangeCloseTether;
					case 6 -> betaPurpleFarTether;
					case 7 -> betaOrangeFarTether;
					// 分からない場合は東から南へ走っておけば北の頭割りを巻き込まない
					default -> betaUnknown;
				};
				s.updateCall(call, e1);
			});

	private final ModifiableCallout<AbilityUsedEvent> radiantSacrament = new ModifiableCallout<>("Radiant Sacrament", "Sacrament {dir}");

	private static ArenaSector cardinalOf(Position pos) {
		double x = pos.x() - arenaCenter;
		double y = arenaCenter - pos.y();
		int idx = (int) (Math.round(Math.atan2(x, y) / Math.PI * 2 + 4) % 4);
		return ArenaSector.cardinals.get(idx);
	}

	@HandleEvents
	public void betaRadiant(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x489E) || !event.isFirstTarget()) {
			return;
		}
		Position pos = event.getSource().getPos();
		if (pos == null) {
			return;
		}
		radiantDir = cardinalOf(pos);
		context.accept(radiantSacrament.getModified(event, Map.of("dir", radiantDir)));
	}

	private final ModifiableCallout<AbilityUsedEvent> betaOpticalSpread = new ModifiableCallout<>("Fate Beta: Optical Spread", "Optical Spread");
	private final ModifiableCallout<AbilityUsedEvent> betaOpticalStack = new ModifiableCallout<>("Fate Beta: Optical Stack", "Optical Stack");

	@HandleEvents
	public void betaOptical(EventContext context, AbilityUsedEvent event) {
		if (!event.isFirstTarget()) {
			return;
		}
		if (event.abilityIdMatches(0x48A0)) {
			betaIsOpticalStack = false;
			context.accept(betaOpticalSpread.getModified(event));
		}
		else if (event.abilityIdMatches(0x48A1)) {
			betaIsOpticalStack = true;
			context.accept(betaOpticalStack.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> betaFinalSpread = new ModifiableCallout<>("Fate Beta Resolve: Optical Spread", "Optical Spread");
	private final ModifiableCallout<AbilityUsedEvent> betaFinalStackOnYou = new ModifiableCallout<>("Fate Beta Resolve: Optical Stack (On You)", "Optical Stack on You");
	private final ModifiableCallout<AbilityUsedEvent> betaFinalStack = new ModifiableCallout<>("Fate Beta Resolve: Optical Stack", "Optical Stack");
	private final ModifiableCallout<AbilityUsedEvent> betaFinalStackPlayers = new ModifiableCallout<>("Fate Beta Resolve: Optical Stack (Targets)", "Optical Stack ({players})");
	private final ModifiableCallout<AbilityUsedEvent> betaFinalRadiant = new ModifiableCallout<>("Fate Beta Resolve: Radiant Sacrament", "Sacrament {dir}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> betaFinalSq = SqtTemplates.sq(20_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x4B14) && aue.isFirstTarget(),
			(e1, s) -> {
				s.waitMs(12_200);
				if (!betaIsOpticalStack) {
					s.updateCall(betaFinalSpread, e1);
				}
				else if (containsMe(betaBait)) {
					s.updateCall(betaFinalStackOnYou, e1);
				}
				else if (betaBait.isEmpty()) {
					s.updateCall(betaFinalStack, e1);
				}
				else {
					s.setParam("players", joinNames(betaBait));
					s.updateCall(betaFinalStackPlayers, e1);
				}
				s.waitMs(3_800);
				if (radiantDir != null) {
					s.setParam("dir", radiantDir);
					s.updateCall(betaFinalRadiant, e1);
				}
			});

	private final ModifiableCallout<AbilityCastStart> ordainedPunishmentOnYou = ModifiableCallout.durationBasedCall("Ordained Punishment (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> ordainedPunishmentSwap = ModifiableCallout.durationBasedCall("Ordained Punishment (Other Tank)", "Tank Swap");
	private final ModifiableCallout<AbilityCastStart> ordainedPunishmentOn = ModifiableCallout.durationBasedCall("Ordained Punishment (Healer)", "Buster on {event.target}");

	@HandleEvents
	public void ordainedPunishment(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x4891)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(ordainedPunishmentOnYou.getModified(event));
		}
		else if (state.playerJobMatches(Job::isTank)) {
			context.accept(ordainedPunishmentSwap.getModified(event));
		}
		else if (state.playerJobMatches(Job::isHealer)) {
			context.accept(ordainedPunishmentOn.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> trineGetMiddle = new ModifiableCallout<>("Trine: Get Middle", "Stack Middle for Trine");

	@HandleEvents
	public void trineMiddle(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x488E) && event.isFirstTarget()) {
			context.accept(trineGetMiddle.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> trineWaitMiddleDodgeNorth = new ModifiableCallout<>("Trine: Wait Middle, Dodge North", "Wait Middle, Dodge North");
	private final ModifiableCallout<AbilityUsedEvent> trineGoNorthDodgeSouth = new ModifiableCallout<>("Trine: Go North, Dodge South", "Go 1 North, Dodge South");
	private final ModifiableCallout<AbilityUsedEvent> trineGoNorthDodgeWest = new ModifiableCallout<>("Trine: Go North, Dodge West", "Go 1 North, Dodge West");
	private final ModifiableCallout<AbilityUsedEvent> trineGoSouthDodgeEast = new ModifiableCallout<>("Trine: Go South, Dodge East", "Go 1 South, Dodge East");
	private final ModifiableCallout<AbilityUsedEvent> trineWaitMiddleDodgeSouth = new ModifiableCallout<>("Trine: Wait Middle, Dodge South", "Wait Middle, Dodge South");
	private final ModifiableCallout<AbilityUsedEvent> trineGoSouthDodgeNorth = new ModifiableCallout<>("Trine: Go South, Dodge North", "Go 1 South, Dodge North");

	@HandleEvents
	public void trineInitial(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x488F) || !event.isFirstTarget()) {
			return;
		}
		Position pos = event.getSource().getPos();
		if (pos == null || Math.round(pos.x()) != Math.round(arenaCenter)) {
			return;
		}
		// 中央の縦列 (100,92) (100,100) (100,108) を北から赤・緑・黄と呼ぶ
		char thisTrine;
		switch ((int) Math.round(pos.y())) {
			case 92 -> thisTrine = 'r';
			case 100 -> thisTrine = 'g';
			case 108 -> thisTrine = 'y';
			default -> {
				return;
			}
		}
		trine.add(thisTrine);
		// 2つ見えた時点で全体が確定する
		if (trine.size() != 2) {
			return;
		}
		char one = trine.get(0);
		char three = 'r';
		for (char c : new char[]{'r', 'g', 'y'}) {
			if (!trine.contains(c)) {
				three = c;
			}
		}
		// 中央から Z 字の5マスだけを使い、3番目のトライン → 1番目のトラインの順に移動する
		String threeOne = "" + three + one;
		ModifiableCallout<AbilityUsedEvent> call;
		switch (threeOne) {
			case "gr" -> {
				secondTrineDir = ArenaSector.NORTH;
				call = trineWaitMiddleDodgeNorth;
			}
			case "rg" -> {
				secondTrineDir = ArenaSector.SOUTH;
				call = trineGoNorthDodgeSouth;
			}
			case "ry" -> {
				secondTrineDir = ArenaSector.WEST;
				call = trineGoNorthDodgeWest;
			}
			case "yr" -> {
				secondTrineDir = ArenaSector.EAST;
				call = trineGoSouthDodgeEast;
			}
			case "gy" -> {
				secondTrineDir = ArenaSector.SOUTH;
				call = trineWaitMiddleDodgeSouth;
			}
			case "yg" -> {
				secondTrineDir = ArenaSector.NORTH;
				call = trineGoSouthDodgeNorth;
			}
			default -> {
				return;
			}
		}
		context.accept(call.getModified(event));
	}

	private final ModifiableCallout<AbilityUsedEvent> trineSecond = new ModifiableCallout<>("Trine: Second Dodge", "{dir}");

	@HandleEvents
	public void trineSecondUsed(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x4890) && secondTrineDir != null && !suppressed(0x4890, event, 15_000)) {
			context.accept(trineSecond.getModified(event, Map.of("dir", secondTrineDir)));
		}
	}

	private final ModifiableCallout<AbilityCastStart> irresistibleGrace = ModifiableCallout.durationBasedCall("Irresistible Grace", "Stack on {event.target}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> irresistibleGraceSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x4894),
			(e1, s) -> {
				trine.clear();
				secondTrineDir = null;
				// トラインの指示と重ならないよう少し遅らせる
				s.waitMs(2_000);
				s.updateCall(irresistibleGrace, e1);
			});

}
