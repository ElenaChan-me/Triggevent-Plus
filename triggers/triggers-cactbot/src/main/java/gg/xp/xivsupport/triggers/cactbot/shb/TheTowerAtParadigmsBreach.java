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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SequentialTriggerController;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/alliance/the_tower_at_paradigms_breach.ts をもとに移植
@CalloutRepo(name = "The Tower At Paradigms Breach", duty = KnownDuty.TheTowerAtParadigmsBreach)
public class TheTowerAtParadigmsBreach extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long NO_ID = -1;
	private static final long CLONE_COLOSSAL_DELAY_MS = 2_100;
	private static final long LUNGE_OFFSET_MS = 6_000;
	private static final long LUNGE_FOLLOWUP_OFFSET_MS = 6_500;
	// トランスファランスで入れ替わる場合は詠唱が7.7秒から12.7秒に延びる
	private static final long SWEEP_SWAP_THRESHOLD_MS = 10_000;
	private static final long MARKER_COLLECT_MS = 500;
	private static final long DEPLOY_COLLECT_MS = 250;
	private static final int SIGNAL_TETHERS_PER_SET = 5;

	private XivState state;

	public TheTowerAtParadigmsBreach(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheTowerAtParadigmsBreach);
	}

	private record Armament(boolean sides, boolean vertical, AbilityCastStart cast) {
	}

	// 本体と分身は同じIDの技を使うので、本体しか使わない技から本体を特定しておく
	private long knaveId = NO_ID;
	private boolean cloneLunge;
	private boolean seenSphere;
	private int signalCount;
	private final List<XivCombatant> busterTargets = new ArrayList<>();
	private final List<XivCombatant> seedTargets = new ArrayList<>();
	private final List<Armament> armaments = new ArrayList<>();

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		knaveId = NO_ID;
		cloneLunge = false;
		seenSphere = false;
		signalCount = 0;
		busterTargets.clear();
		seedTargets.clear();
		armaments.clear();
	}

	private boolean isClone(AbilityCastStart event) {
		return knaveId != NO_ID && event.getSource().getId() != knaveId;
	}

	private static void callWithOffset(SequentialTriggerController<BaseEvent> s, AbilityCastStart event, long offsetMs, ModifiableCallout<AbilityCastStart> call) {
		long delay = event.getInitialDuration().toMillis() - offsetMs;
		if (delay > 0) {
			s.waitMs(delay);
		}
		s.updateCall(call, event);
	}

	// ナイト・オブ・ハート

	@NpcCastCallout(0x5EB5)
	private final ModifiableCallout<AbilityCastStart> roar = ModifiableCallout.durationBasedCall("Roar", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> colossalSides = ModifiableCallout.durationBasedCall("Colossal Impact (Sides)", "Middle");
	private final ModifiableCallout<AbilityCastStart> colossalMiddle = ModifiableCallout.durationBasedCall("Colossal Impact (Middle)", "East/West");
	private final ModifiableCallout<AbilityCastStart> copiedColossalSides = ModifiableCallout.durationBasedCall("Copied Knave Colossal Impact (Sides)", "Middle");
	private final ModifiableCallout<AbilityCastStart> copiedColossalMiddle = ModifiableCallout.durationBasedCall("Copied Knave Colossal Impact (Middle)", "North/South");

	@HandleEvents
	public void knaveCasts(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x5EB5, 0x5EA9, 0x5EB2, 0x60C7, 0x60C8)) {
			knaveId = event.getSource().getId();
		}
		else if (event.abilityIdMatches(0x5EA4) && !isClone(event)) {
			context.accept(colossalSides.getModified(event));
		}
		else if (event.abilityIdMatches(0x5EA7) && !isClone(event)) {
			context.accept(colossalMiddle.getModified(event));
		}
	}

	// 分身は本体の詠唱開始から約6秒遅れて詠唱するので、少し待ってから知らせる
	@AutoFeed
	private final SequentialTrigger<BaseEvent> copiedColossalSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x5EA4, 0x5EA7) && isClone(acs),
			(e1, s) -> {
				s.waitMs(CLONE_COLOSSAL_DELAY_MS);
				s.updateCall(e1.abilityIdMatches(0x5EA4) ? copiedColossalSides : copiedColossalMiddle, e1);
			});

	@PlayerHeadmarker(0xDA)
	private final ModifiableCallout<HeadMarkerEvent> artilleryBetaOnYou = new ModifiableCallout<>("Magic Artillery Beta (on You)", "Buster on You");
	private final ModifiableCallout<HeadMarkerEvent> artilleryBetaHealer = new ModifiableCallout<>("Magic Artillery Beta (Healer)", "Tankbuster");
	private final ModifiableCallout<HeadMarkerEvent> artilleryBetaAvoid = new ModifiableCallout<>("Magic Artillery Beta (Avoid)", "Avoid Tankbuster");

	@PlayerHeadmarker(0xA9)
	private final ModifiableCallout<HeadMarkerEvent> artilleryAlpha = new ModifiableCallout<>("Magic Artillery Alpha", "Spread");

	private final ModifiableCallout<AbilityCastStart> lunge = ModifiableCallout.durationBasedCall("Lunge", "Knockback from Boss");
	private final ModifiableCallout<AbilityCastStart> copiedLunge = ModifiableCallout.durationBasedCall("Copied Knave Lunge", "Knockback from Clone");
	private final ModifiableCallout<AbilityCastStart> lungeGetMiddle = ModifiableCallout.durationBasedCall("Lunge (Get Middle)", "Knockback, Middle");
	private final ModifiableCallout<AbilityCastStart> lungeOutOfMiddle = ModifiableCallout.durationBasedCall("Lunge (Out of Middle)", "Knockback, Out of Middle");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> lungeSq = SqtTemplates.sq(30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x5EB1) && !isClone(acs),
			(e1, s) -> callWithOffset(s, e1, LUNGE_OFFSET_MS, lunge));

	// 2回目以降の分身の突進は後続の専用詠唱で知らせる
	@AutoFeed
	private final SequentialTrigger<BaseEvent> copiedLungeSq = SqtTemplates.sq(30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x5EB1) && isClone(acs) && !cloneLunge,
			(e1, s) -> {
				cloneLunge = true;
				callWithOffset(s, e1, LUNGE_OFFSET_MS, copiedLunge);
			});

	@AutoFeed
	private final SequentialTrigger<BaseEvent> lungeFollowupSq = SqtTemplates.sq(30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x60C7, 0x60C8),
			(e1, s) -> callWithOffset(s, e1, LUNGE_FOLLOWUP_OFFSET_MS, e1.abilityIdMatches(0x60C7) ? lungeGetMiddle : lungeOutOfMiddle));

	// ヘンゼル＆グレーテル

	@NpcCastCallout(0x5C69)
	private final ModifiableCallout<AbilityCastStart> gretelShield = ModifiableCallout.durationBasedCall("Upgraded Shield (Gretel)", "Attack Hansel");

	@NpcCastCallout(0x5C6B)
	private final ModifiableCallout<AbilityCastStart> hanselShield = ModifiableCallout.durationBasedCall("Upgraded Shield (Hansel)", "Attack Gretel");

	// 2体が同時に詠唱するので重複を抑える
	@NpcCastCallout(value = {0x5C76, 0x5C77}, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> wail = ModifiableCallout.durationBasedCall("Wail", "Raidwide");

	@NpcCastCallout(value = {0x5C78, 0x5C79}, onYou = true)
	private final ModifiableCallout<AbilityCastStart> cripplingBlow = ModifiableCallout.durationBasedCall("Crippling Blow", "Buster on You");

	private final ModifiableCallout<HeadMarkerEvent> seedOfMagic = new ModifiableCallout<>("Seed of Magic Alpha", "Spread");
	private final ModifiableCallout<HeadMarkerEvent> riotOnYou = new ModifiableCallout<>("Riot of Magic (on You)", "Stack on You");
	private final ModifiableCallout<HeadMarkerEvent> riotOn = new ModifiableCallout<>("Riot of Magic", "Stack on {event.target}");

	@NpcCastCallout(value = {0x5C73, 0x5C74}, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> lamentation = ModifiableCallout.durationBasedCall("Lamentation", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> bloodySweepBetween = ModifiableCallout.durationBasedCall("Bloody Sweep (Between)", "Between Bosses");
	private final ModifiableCallout<AbilityCastStart> bloodySweepOpposite = ModifiableCallout.durationBasedCall("Bloody Sweep (Opposite)", "Opposite Bosses");
	private final RepeatSuppressor bloodySweepSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void bloodySweep(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x5C54, 0x5C55, 0x5C56, 0x5C57) || !bloodySweepSupp.check(event)) {
			return;
		}
		// ヘンゼルは左利き、グレーテルは右利きなので技IDで2体の左右がわかる
		boolean hanselOnRight = event.abilityIdMatches(0x5C54, 0x5C55);
		boolean swap = event.getInitialDuration().toMillis() > SWEEP_SWAP_THRESHOLD_MS;
		context.accept((hanselOnRight == swap ? bloodySweepBetween : bloodySweepOpposite).getModified(event));
	}

	// レッドガール

	@NpcCastCallout(value = {0x6012, 0x6013}, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> cruelty = ModifiableCallout.durationBasedCall("Cruelty", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> waveWhite = ModifiableCallout.durationBasedCall("Wave: White", "Switch to White");
	private final ModifiableCallout<AbilityCastStart> waveBlack = ModifiableCallout.durationBasedCall("Wave: Black", "Switch to Black");

	@HandleEvents
	public void sphereWave(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x618D, 0x618E)) {
			return;
		}
		// 最初の1回は転送中なので知らせない
		if (seenSphere) {
			context.accept((event.abilityIdMatches(0x618D) ? waveWhite : waveBlack).getModified(event));
		}
		seenSphere = true;
	}

	// 孟子と荀子

	@NpcCastCallout(value = 0x5C06, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> universalAssault = ModifiableCallout.durationBasedCall("Universal Assault", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> deployCenter = ModifiableCallout.durationBasedCall("Deploy Armaments (Center)", "Middle");
	private final ModifiableCallout<AbilityCastStart> deployCorner = ModifiableCallout.durationBasedCall("Deploy Armaments (Corner)", "Corner");
	private final ModifiableCallout<AbilityCastStart> deployNorthBoss = ModifiableCallout.durationBasedCall("Deploy Armaments (North Boss)", "North Boss");
	private final ModifiableCallout<AbilityCastStart> deployNorth = ModifiableCallout.durationBasedCall("Deploy Armaments (North)", "North");
	private final ModifiableCallout<AbilityCastStart> deployWestBoss = ModifiableCallout.durationBasedCall("Deploy Armaments (West Boss)", "West Boss");
	private final ModifiableCallout<AbilityCastStart> deployWest = ModifiableCallout.durationBasedCall("Deploy Armaments (West)", "West");
	private final ModifiableCallout<AbilityCastStart> deployUnknown = ModifiableCallout.durationBasedCall("Deploy Armaments (Unknown)", "Avoid Lines");
	private final RepeatSuppressor deploySupp = new RepeatSuppressor(Duration.ofSeconds(1));

	private static boolean isDeployArmaments(AbilityCastStart event) {
		return event.abilityIdMatches(0x5C00, 0x5C01, 0x5C03, 0x5C04);
	}

	// 中央1本と両脇2本の2種類があり、それぞれ縦横どちらにも来るうえ2体分が重なるので、詠唱中のものをまとめて判定する
	@HandleEvents
	public void deployArmamentsCollect(EventContext context, AbilityCastStart event) {
		if (!isDeployArmaments(event)) {
			return;
		}
		Position pos = event.getSource().getPos();
		if (pos == null) {
			return;
		}
		// 向きを 0=北, 1=東, 2=南, 3=西 に変換
		long direction = Math.round(2 - 2 * pos.getHeading() / Math.PI) % 4;
		armaments.add(new Armament(event.abilityIdMatches(0x5C03, 0x5C04), direction == 0 || direction == 2, event));
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> deployArmamentsSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, acs -> isDeployArmaments(acs) && deploySupp.check(acs),
			(e1, s) -> {
				s.waitMs(DEPLOY_COLLECT_MS);
				armaments.removeIf(a -> a.cast().wouldBeExpired());
				boolean vMiddle = armaments.stream().anyMatch(a -> a.vertical() && !a.sides());
				boolean hMiddle = armaments.stream().anyMatch(a -> !a.vertical() && !a.sides());
				boolean vSides = armaments.stream().anyMatch(a -> a.vertical() && a.sides());
				boolean hSides = armaments.stream().anyMatch(a -> !a.vertical() && a.sides());
				final ModifiableCallout<AbilityCastStart> call;
				if (vMiddle && hMiddle) {
					call = deployCorner;
				}
				else if (vSides && hSides) {
					call = deployCenter;
				}
				else if (vMiddle && hSides) {
					call = deployWestBoss;
				}
				else if (vSides && hMiddle) {
					call = deployNorthBoss;
				}
				else if (vSides || hSides) {
					call = deployCenter;
				}
				else if (vMiddle) {
					call = deployWest;
				}
				else if (hMiddle) {
					call = deployNorth;
				}
				else {
					call = deployUnknown;
				}
				s.updateCall(call, e1);
			});

	// 偽造天使

	@NpcCastCallout(0x5BDD)
	private final ModifiableCallout<AbilityCastStart> falseIdolScreamingScore = ModifiableCallout.durationBasedCall("Screaming Score (False Idol)", "Raidwide");

	@NpcCastCallout(0x5BD6)
	private final ModifiableCallout<AbilityCastStart> madeMagicLeft = ModifiableCallout.durationBasedCall("Made Magic (Left)", "Go Right");

	@NpcCastCallout(0x5BD7)
	private final ModifiableCallout<AbilityCastStart> madeMagicRight = ModifiableCallout.durationBasedCall("Made Magic (Right)", "Go Left");

	@PlayerHeadmarker(0x01)
	private final ModifiableCallout<HeadMarkerEvent> lighterNote = new ModifiableCallout<>("Lighter Note", "Lighter Note on You");

	@PlayerHeadmarker(0x8B)
	private final ModifiableCallout<HeadMarkerEvent> darkerNoteOnYou = new ModifiableCallout<>("Darker Note (on You)", "Buster on You");
	private final ModifiableCallout<HeadMarkerEvent> darkerNoteHealer = new ModifiableCallout<>("Darker Note (Healer)", "Tankbuster");
	private final ModifiableCallout<HeadMarkerEvent> darkerNoteAvoid = new ModifiableCallout<>("Darker Note (Avoid)", "Avoid Tankbuster");

	private final RepeatSuppressor busterSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void collectMarkers(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0xDA, 0x8B)) {
			busterTargets.add(event.getTarget());
		}
		else if (event.markerIdMatches(0x60)) {
			seedTargets.add(event.getTarget());
			if (event.getTarget().isThePlayer()) {
				context.accept(seedOfMagic.getModified(event));
			}
		}
	}

	// 対象者以外にはヒーラーなら警告、それ以外なら回避を促す
	@AutoFeed
	private final SequentialTrigger<BaseEvent> busterOthersSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(0xDA, 0x8B) && busterSupp.check(hm),
			(e1, s) -> {
				s.waitMs(MARKER_COLLECT_MS);
				boolean onMe = busterTargets.stream().anyMatch(XivCombatant::isThePlayer);
				busterTargets.clear();
				if (onMe) {
					return;
				}
				boolean artillery = e1.markerIdMatches(0xDA);
				if (state.playerJobMatches(Job::isHealer)) {
					s.updateCall(artillery ? artilleryBetaHealer : darkerNoteHealer, e1);
				}
				else {
					s.updateCall(artillery ? artilleryBetaAvoid : darkerNoteAvoid, e1);
				}
			});

	// 散開対象者は頭割りに参加しないので知らせない
	@AutoFeed
	private final SequentialTrigger<BaseEvent> riotSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(0x3E),
			(e1, s) -> {
				s.waitMs(MARKER_COLLECT_MS);
				boolean seedOnMe = seedTargets.stream().anyMatch(XivCombatant::isThePlayer);
				seedTargets.clear();
				if (seedOnMe) {
					return;
				}
				s.updateCall(e1.getTarget().isThePlayer() ? riotOnYou : riotOn, e1);
			});

	// ハー・インフローレセンス

	@NpcCastCallout(0x5BF5)
	private final ModifiableCallout<AbilityCastStart> inflorescenceScreamingScore = ModifiableCallout.durationBasedCall("Screaming Score (Her Inflorescence)", "Raidwide");

	// 詠唱開始時点では早すぎるので詠唱完了時に知らせる
	private final ModifiableCallout<AbilityUsedEvent> recreateStructure = new ModifiableCallout<>("Recreate Structure", "Dodge Building Below", 5_000);

	@HandleEvents
	public void recreateStructureUsed(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x5BE1) && event.isFirstTarget()) {
			context.accept(recreateStructure.getModified(event));
		}
	}

	private final ModifiableCallout<TetherEvent> recreateSignal = new ModifiableCallout<>("Recreate Signal", "Go to Red Light", 5_000);

	@HandleEvents
	public void recreateSignalStart(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x5BE3)) {
			signalCount = 0;
		}
	}

	@HandleEvents
	public void recreateSignalTethers(EventContext context, TetherEvent event) {
		if (!event.tetherIdMatches(0x36)) {
			return;
		}
		signalCount++;
		if (signalCount % SIGNAL_TETHERS_PER_SET == 0) {
			context.accept(recreateSignal.getModified(event));
		}
	}

	@NpcCastCallout(0x5BED)
	private final ModifiableCallout<AbilityCastStart> heavyArmsMiddle = ModifiableCallout.durationBasedCall("Heavy Arms (Middle)", "Sides");

	@NpcCastCallout(value = 0x5BEF, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> heavyArmsSides = ModifiableCallout.durationBasedCall("Heavy Arms (Sides)", "Front/Back");

}
