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
import gg.xp.xivsupport.events.actlines.events.MapEffectEvent;
import gg.xp.xivsupport.events.actlines.events.SystemLogMessageEvent;
import gg.xp.xivsupport.events.actlines.events.ZoneChangeEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;
import java.util.List;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/dungeon/the_sildihn_subterrane.ts をもとに移植
@CalloutRepo(name = "The Sildihn Subterrane", duty = KnownDuty.TheSildihnSubterrane)
public class TheSildihnSubterrane extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long SEALED_OFF_LOG_ID = 0x7DC;
	private static final long SILT_PUMP_PLACE_ID = 0x1068;
	private static final long SETTLING_BASIN_PLACE_ID = 0x106D;
	// 樽が爆発し終わるまでの時間
	private static final long BARREL_ACTIVE_MS = 17_000;

	// 選んだ通路によってカタパルト後のギミック順が変わる
	private enum CatapultMech {
		STAND_ON_BLUE,
		KNOCKBACK,
		LAUNCH,
		CHARGE,
		GIGANTOMILL,
		LAUNCH_OR_SWING,
		BOULDERS,
		// ループで戻る位置の目印
		LOOP_START
	}

	private static final List<CatapultMech> INITIAL = List.of(
			CatapultMech.STAND_ON_BLUE, CatapultMech.STAND_ON_BLUE);

	private static final List<CatapultMech> LEFT_DOOR_YES_PUMP = List.of(
			CatapultMech.STAND_ON_BLUE, CatapultMech.STAND_ON_BLUE, CatapultMech.KNOCKBACK, CatapultMech.LAUNCH,
			CatapultMech.LOOP_START, CatapultMech.CHARGE, CatapultMech.GIGANTOMILL, CatapultMech.KNOCKBACK, CatapultMech.LAUNCH_OR_SWING);

	private static final List<CatapultMech> LEFT_DOOR_NO_PUMP = List.of(
			CatapultMech.STAND_ON_BLUE, CatapultMech.STAND_ON_BLUE, CatapultMech.LAUNCH,
			CatapultMech.LOOP_START, CatapultMech.CHARGE, CatapultMech.LAUNCH_OR_SWING);

	private static final List<CatapultMech> MIDDLE_DOOR_LEFT_HANDLE = List.of(
			CatapultMech.STAND_ON_BLUE, CatapultMech.STAND_ON_BLUE, CatapultMech.KNOCKBACK, CatapultMech.LAUNCH,
			CatapultMech.LOOP_START, CatapultMech.GIGANTOMILL, CatapultMech.CHARGE, CatapultMech.KNOCKBACK, CatapultMech.LAUNCH_OR_SWING);

	private static final List<CatapultMech> MIDDLE_DOOR_RIGHT_HANDLE = List.of(
			CatapultMech.STAND_ON_BLUE, CatapultMech.STAND_ON_BLUE, CatapultMech.BOULDERS, CatapultMech.LAUNCH,
			CatapultMech.LOOP_START, CatapultMech.CHARGE, CatapultMech.GIGANTOMILL, CatapultMech.BOULDERS, CatapultMech.LAUNCH_OR_SWING);

	private static final List<CatapultMech> RIGHT_DOOR_YES_CERULEUM = List.of(
			CatapultMech.STAND_ON_BLUE, CatapultMech.STAND_ON_BLUE, CatapultMech.LAUNCH,
			CatapultMech.LOOP_START, CatapultMech.GIGANTOMILL, CatapultMech.CHARGE, CatapultMech.LAUNCH_OR_SWING);

	private static final List<CatapultMech> RIGHT_DOOR_NO_CERULEUM = List.of(
			CatapultMech.STAND_ON_BLUE, CatapultMech.STAND_ON_BLUE, CatapultMech.LAUNCH,
			CatapultMech.LOOP_START, CatapultMech.CHARGE, CatapultMech.LAUNCH_OR_SWING);

	private XivState state;

	public TheSildihnSubterrane(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheSildihnSubterrane);
	}

	// 通路の選択はボス戦の前に決まるので、プル開始ではリセットしない
	private List<CatapultMech> catapultMechs = INITIAL;
	private int catapultCount;
	private AbilityCastStart lastCatapult;

	@HandleEvents
	public void zoneChange(EventContext context, ZoneChangeEvent event) {
		catapultMechs = INITIAL;
	}

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		catapultCount = 0;
		lastCatapult = null;
	}

	// ゲリュオン

	@HandleEvents
	public void sealedOff(EventContext context, SystemLogMessageEvent event) {
		if (event.getId() != SEALED_OFF_LOG_ID) {
			return;
		}
		// この後のギミックで上書きされることがある
		if (event.getParam1() == SILT_PUMP_PLACE_ID) {
			catapultMechs = LEFT_DOOR_YES_PUMP;
		}
		else if (event.getParam1() == SETTLING_BASIN_PLACE_ID) {
			catapultMechs = RIGHT_DOOR_NO_CERULEUM;
		}
	}

	@HandleEvents
	public void runawaySludge(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x74D6)) {
			catapultMechs = LEFT_DOOR_NO_PUMP;
		}
	}

	@HandleEvents
	public void suddenlySewage(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x74D8)) {
			catapultMechs = RIGHT_DOOR_YES_CERULEUM;
		}
	}

	@HandleEvents
	public void middleDoorMapEffect(EventContext context, MapEffectEvent event) {
		if (event.flagsMatches(0x00020001) && event.getLocation() == 0x09) {
			catapultMechs = MIDDLE_DOOR_LEFT_HANDLE;
		}
		else if (event.flagsMatches(0x20000004) && event.getLocation() == 0x0A) {
			catapultMechs = MIDDLE_DOOR_RIGHT_HANDLE;
		}
	}

	@NpcCastCallout(0x74CF)
	private final ModifiableCallout<AbilityCastStart> colossalStrike = ModifiableCallout.durationBasedCall("Colossal Strike", "Buster on {event.target}");

	@NpcCastCallout(0x74D2)
	private final ModifiableCallout<AbilityCastStart> subterraneanShudder = ModifiableCallout.durationBasedCall("Subterranean Shudder", "Raidwide");

	@NpcCastCallout(0x74C7)
	private final ModifiableCallout<AbilityCastStart> explodingCatapult = ModifiableCallout.durationBasedCall("Exploding Catapult", "Raidwide");

	private boolean barrelActive() {
		return lastCatapult != null && lastCatapult.getEffectiveTimeSince().toMillis() < BARREL_ACTIVE_MS;
	}

	private CatapultMech nextCatapultMech() {
		List<CatapultMech> mechs = catapultMechs;
		if (catapultCount < 0 || catapultCount >= mechs.size()) {
			return null;
		}
		CatapultMech mech = mechs.get(catapultCount);
		if (mech == CatapultMech.LOOP_START) {
			catapultCount++;
			mech = catapultCount < mechs.size() ? mechs.get(catapultCount) : null;
		}
		catapultCount++;
		// 最後まで行ったらループの先頭に戻る
		if (catapultCount >= mechs.size()) {
			catapultCount = mechs.indexOf(CatapultMech.LOOP_START);
		}
		return mech;
	}

	private final ModifiableCallout<AbilityCastStart> catapultStandOnBlue = ModifiableCallout.durationBasedCall("Exploding Catapult: Barrels", "Stand on Blue");
	private final ModifiableCallout<AbilityCastStart> catapultBoulders = ModifiableCallout.durationBasedCall("Exploding Catapult: Barrels (Boulders)", "Stand on Blue, Avoid Boulders");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> explodingCatapultSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x74C7),
			(e1, s) -> {
				lastCatapult = e1;
				s.waitMs(6_500);
				CatapultMech mech = nextCatapultMech();
				// 他のギミックは個別の詠唱でコールする
				if (mech == CatapultMech.STAND_ON_BLUE || mech == CatapultMech.GIGANTOMILL) {
					s.updateCall(catapultStandOnBlue, e1);
				}
				else if (mech == CatapultMech.BOULDERS) {
					s.updateCall(catapultBoulders, e1);
				}
			});

	@NpcCastCallout(0x74CE)
	private final ModifiableCallout<AbilityCastStart> shockwave = ModifiableCallout.durationBasedCall("Shockwave", "Knockback");

	private final ModifiableCallout<AbilityCastStart> runawayRunoff = ModifiableCallout.durationBasedCall("Runaway Runoff", "Knockback onto Blue");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> runawayRunoffSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x74D7),
			(e1, s) -> {
				// 8秒詠唱なので少し遅らせる
				s.waitMs(3_000);
				s.updateCall(runawayRunoff, e1);
			});

	private final ModifiableCallout<AbilityCastStart> colossalSwing = ModifiableCallout.durationBasedCall("Colossal Swing", "Get Behind");
	private final ModifiableCallout<AbilityCastStart> colossalSwingBarrel = ModifiableCallout.durationBasedCall("Colossal Swing (Barrels)", "Get Behind on Blue");

	@HandleEvents
	public void colossalSwing(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x74D1)) {
			context.accept((barrelActive() ? colossalSwingBarrel : colossalSwing).getModified(event));
		}
	}

	@NpcCastCallout(0x74C8)
	private final ModifiableCallout<AbilityCastStart> colossalLaunch = ModifiableCallout.durationBasedCall("Colossal Launch", "Stand on Red");

	@NpcCastCallout(0x74CD)
	private final ModifiableCallout<AbilityCastStart> colossalChargeLeft = ModifiableCallout.durationBasedCall("Colossal Charge (Left)", "Stand on Right Blue");

	@NpcCastCallout(0x74CC)
	private final ModifiableCallout<AbilityCastStart> colossalChargeRight = ModifiableCallout.durationBasedCall("Colossal Charge (Right)", "Stand on Left Blue");

	@NpcCastCallout(0x74CA)
	private final ModifiableCallout<AbilityCastStart> gigantomillLeft = ModifiableCallout.durationBasedCall("Gigantomill (Left)", "Left");

	@NpcCastCallout(0x74C9)
	private final ModifiableCallout<AbilityCastStart> gigantomillRight = ModifiableCallout.durationBasedCall("Gigantomill (Right)", "Right");

	// シルキー

	@NpcCastCallout(0x772C)
	private final ModifiableCallout<AbilityCastStart> totalWash = ModifiableCallout.durationBasedCall("Total Wash", "Raidwide");

	@NpcCastCallout(0x772D)
	private final ModifiableCallout<AbilityCastStart> squeakyRight = ModifiableCallout.durationBasedCall("Squeaky Clean (Right)", "Back Left");

	@NpcCastCallout(0x772E)
	private final ModifiableCallout<AbilityCastStart> squeakyLeft = ModifiableCallout.durationBasedCall("Squeaky Clean (Left)", "Back Right");

	@NpcCastCallout(0x772B)
	private final ModifiableCallout<AbilityCastStart> carpetBuster = ModifiableCallout.durationBasedCall("Carpet Beater", "Buster on {event.target}");

	@NpcCastCallout(0x7744)
	private final ModifiableCallout<AbilityCastStart> dustBluster = ModifiableCallout.durationBasedCall("Dust Bluster", "Knockback");

	private final ModifiableCallout<AbilityCastStart> washOut = ModifiableCallout.durationBasedCall("Wash Out", "Knockback");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> washOutSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x7745),
			(e1, s) -> {
				// 8秒詠唱なので少し遅らせる
				s.waitMs(3_000);
				s.updateCall(washOut, e1);
			});

	@NpcCastCallout(0x7738)
	private final ModifiableCallout<AbilityCastStart> chillingDuster = ModifiableCallout.durationBasedCall("Chilling Duster", "Intercards");

	@NpcCastCallout(0x773B)
	private final ModifiableCallout<AbilityCastStart> chillingDusterSlippery = ModifiableCallout.durationBasedCall("Chilling Duster (Slippery Soap)", "Follow, then Intercards");

	@NpcCastCallout(0x773F)
	private final ModifiableCallout<AbilityCastStart> chillingDusterPuffs = ModifiableCallout.durationBasedCall("Chilling Duster (Puffs)", "Avoid Crosses from Silkie and Puffs");

	@NpcCastCallout(0x7739)
	private final ModifiableCallout<AbilityCastStart> bracingDuster = ModifiableCallout.durationBasedCall("Bracing Duster", "Under");

	@NpcCastCallout(0x773C)
	private final ModifiableCallout<AbilityCastStart> bracingDusterSlippery = ModifiableCallout.durationBasedCall("Bracing Duster (Slippery Soap)", "Follow, then Under");

	// シラディハ・グラディアトル

	@NpcCastCallout(0x7656)
	private final ModifiableCallout<AbilityCastStart> flashOfSteel = ModifiableCallout.durationBasedCall("Flash of Steel", "Raidwide");

	@NpcCastCallout(0x764A)
	private final ModifiableCallout<AbilityCastStart> sculptorsPassion = ModifiableCallout.durationBasedCall("Sculptor's Passion", "Get Behind");

	@NpcCastCallout(0x7657)
	private final ModifiableCallout<AbilityCastStart> mightySmite = ModifiableCallout.durationBasedCall("Mighty Smite", "Buster on {event.target}");

	private final ModifiableCallout<AbilityCastStart> shatteringSteel = ModifiableCallout.durationBasedCall("Shattering Steel", "Get in Big Wind Circle");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> shatteringSteelSq = SqtTemplates.sq(20_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x764B),
			(e1, s) -> {
				// 詠唱12秒に対して浮遊のデバフが5秒なので、残り5秒で知らせる
				s.waitMs(7_000);
				s.updateCall(shatteringSteel, e1);
			});

	@NpcCastCallout(0x763F)
	private final ModifiableCallout<AbilityCastStart> ringOfMight1 = ModifiableCallout.durationBasedCall("Ring of Might (1)", "Outside Inner Ring");

	@NpcCastCallout(0x7640)
	private final ModifiableCallout<AbilityCastStart> ringOfMight2 = ModifiableCallout.durationBasedCall("Ring of Might (2)", "Outside Middle Ring");

	@NpcCastCallout(0x7641)
	private final ModifiableCallout<AbilityCastStart> ringOfMight3 = ModifiableCallout.durationBasedCall("Ring of Might (3)", "Outside Outer Ring");

	private final ModifiableCallout<AbilityUsedEvent> ringOfMightFollowup = new ModifiableCallout<>("Ring of Might: Followup", "In");
	private final RepeatSuppressor ringOfMightSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void ringOfMightFollowup(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x763F, 0x7640, 0x7641) && ringOfMightSupp.check(event)) {
			context.accept(ringOfMightFollowup.getModified(event));
		}
	}

	@NpcCastCallout(0x763A)
	private final ModifiableCallout<AbilityCastStart> rushOfMight1 = ModifiableCallout.durationBasedCall("Rush of Might (1)", "Behind Close Mark");

	@NpcCastCallout(0x763B)
	private final ModifiableCallout<AbilityCastStart> rushOfMight2 = ModifiableCallout.durationBasedCall("Rush of Might (2)", "Behind Middle Mark");

	@NpcCastCallout(0x763C)
	private final ModifiableCallout<AbilityCastStart> rushOfMight3 = ModifiableCallout.durationBasedCall("Rush of Might (3)", "Behind Far Mark");

	private final ModifiableCallout<AbilityUsedEvent> rushOfMightFollowup = new ModifiableCallout<>("Rush of Might: Followup", "Move Through");
	private final RepeatSuppressor rushOfMightSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void rushOfMightFollowup(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x763D) && rushOfMightSupp.check(event)) {
			context.accept(rushOfMightFollowup.getModified(event));
		}
	}

	// 影火のゼレズ・ガー

	@NpcCastCallout(0x74AE)
	private final ModifiableCallout<AbilityCastStart> showOfStrength = ModifiableCallout.durationBasedCall("Show of Strength", "Raidwide");

	@NpcCastCallout(0x74AC)
	private final ModifiableCallout<AbilityCastStart> firesteelFracture = ModifiableCallout.durationBasedCall("Firesteel Fracture", "Buster on {event.target}");

	private final ModifiableCallout<AbilityUsedEvent> infernGale = new ModifiableCallout<>("Infern Gale", "Knockback");
	private final RepeatSuppressor infernGaleSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> infernGaleSq = SqtTemplates.sq(10_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x74A2) && infernGaleSupp.check(aue),
			(e1, s) -> {
				// 詠唱なしで約6.4秒後にノックバックが来る
				s.waitMs(1_400);
				s.updateCall(infernGale, e1);
			});

	private final ModifiableCallout<AbilityUsedEvent> infernWell = new ModifiableCallout<>("Infern Well", "Draw In");
	private final RepeatSuppressor infernWellSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> infernWellSq = SqtTemplates.sq(15_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x74A7) && infernWellSupp.check(aue),
			(e1, s) -> {
				// 詠唱なしで約10秒後に引き寄せが来る
				s.waitMs(5_000);
				s.updateCall(infernWell, e1);
			});

	// ソーン・ナイト

	@NpcCastCallout(0x70EB)
	private final ModifiableCallout<AbilityCastStart> cogwheel = ModifiableCallout.durationBasedCall("Cogwheel", "Raidwide");

	@NpcCastCallout(0x70EA)
	private final ModifiableCallout<AbilityCastStart> blisteringBlow = ModifiableCallout.durationBasedCall("Blistering Blow", "Buster on {event.target}");

	@NpcCastCallout(0x70EC)
	private final ModifiableCallout<AbilityCastStart> foreHonor = ModifiableCallout.durationBasedCall("Fore Honor", "Get Behind");

	private final ModifiableCallout<HeadMarkerEvent> slashburnReversed = new ModifiableCallout<>("Slashburn (Reversed)", "Reversed Slashburn");

	@HandleEvents
	public void slashburnReversed(EventContext context, HeadMarkerEvent event) {
		// ボス自身に付くマーカー
		if (event.markerIdMatches(0x16B) && !event.getTarget().isPc()) {
			context.accept(slashburnReversed.getModified(event));
		}
	}

}
