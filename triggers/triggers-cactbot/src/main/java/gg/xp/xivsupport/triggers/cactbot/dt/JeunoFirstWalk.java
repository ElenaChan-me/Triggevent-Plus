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
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.BuffRemoved;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.RawAddCombatantEvent;
import gg.xp.xivsupport.events.actlines.events.SystemLogMessageEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.models.ArenaPos;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.XivCombatant;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/alliance/jeuno-first-walk.ts をもとに移植
@CalloutRepo(name = "Jeuno The First Walk", duty = KnownDuty.JeunoTheFirstWalk)
public class JeunoFirstWalk extends AutoChildEventHandler implements FilteredEventHandler {

	// プリシュの「まだまだ」のエモート回数に合わせて遅らせる
	private static final long PUNCH_SHORT_DELAY_MS = 4_000;
	private static final long PUNCH_LONG_DELAY_MS = 6_000;
	private static final long SPIRAL_FINISH_DELAY_MS = 5_500;
	private static final long LORDLY_IMPLOSION_DELAY_MS = 3_000;
	// 対象者のマーカーが出揃うのを待つ
	private static final long MARKER_COLLECT_DELAY_MS = 500;
	// 炎のギミックの種類を示す詠唱が同時に来るのを待つ
	private static final long BURNING_DELAY_MS = 200;
	private static final long SUPPRESS_MS = 1_000;

	private static final long SHARP_SPIKE_MARKER = 0x156;
	private static final long CLOUDSPLITTER_MARKER = 0x1D0;
	private static final long UNBRIDLED_RAGE_MARKER = 0x1D7;
	private static final long DECISIVE_BATTLE_TETHER = 0x12B;
	private static final long CHASING_TETHER = 0x125;
	private static final long ARK_SHIELD_NPC_ID = 18260;
	private static final long UNINTERRUPTED_STATUS = 0x1140;
	private static final long TERA_SLASH_LOG_ID = 0x29AB;

	// シャドウロードの分身も同じ技IDを使うので、本体だけが使う技から本体を特定する
	private static final long[] SHADOW_LORD_ONLY_ABILITIES = {
			0x9F5B, 0x9F69, 0x9F4A, 0x9F4B, 0x9F4C, 0x9F4D, 0x9F4E, 0x9F4F,
			0x9F50, 0x9F66, 0x9F67, 0xA424, 0xA425, 0xA426, 0xA427,
	};

	private XivState state;

	public JeunoFirstWalk(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.JeunoTheFirstWalk);
	}

	private @Nullable ArenaSector dragonBreathFacing;
	private final List<XivCombatant> spikeTargets = new ArrayList<>();
	private final List<XivCombatant> splitterTargets = new ArrayList<>();
	private final List<XivCombatant> rageTargets = new ArrayList<>();
	private boolean burningBattlementsActive;
	private boolean burningKeepActive;
	private boolean seenNebula;
	private @Nullable Long shadowLordId;

	@HandleEvents(order = -10)
	public void reset(EventContext context, PullStartedEvent event) {
		dragonBreathFacing = null;
		spikeTargets.clear();
		splitterTargets.clear();
		rageTargets.clear();
		burningBattlementsActive = false;
		burningKeepActive = false;
		seenNebula = false;
		shadowLordId = null;
	}

	private boolean isLordlyShadow(XivCombatant source) {
		return shadowLordId != null && shadowLordId != source.getId();
	}

	private static boolean containsPlayer(List<XivCombatant> targets) {
		return targets.stream().anyMatch(XivCombatant::isThePlayer);
	}

	private boolean canInterrupt() {
		return state.playerJobMatches(Job::caresAboutInterrupt);
	}

	// ---- プリシュ ----

	@NpcCastCallout(0x9FE7)
	private final ModifiableCallout<AbilityCastStart> banishga = ModifiableCallout.durationBasedCall("Banishga", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> knuckleSandwichSmall = ModifiableCallout.durationBasedCall("Knuckle Sandwich (Small)", "Outside Small Circle, then In");
	private final ModifiableCallout<AbilityCastStart> knuckleSandwichMid = ModifiableCallout.durationBasedCall("Knuckle Sandwich (Mid)", "Outside Middle Circle, then In");
	private final ModifiableCallout<AbilityCastStart> knuckleSandwichBig = ModifiableCallout.durationBasedCall("Knuckle Sandwich (Big)", "Outside Big Circle, then In");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> knuckleSandwichSq = SqtTemplates.sq(
			30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x9FE8, 0x9FE9, 0x9FEA),
			(e1, s) -> {
				s.waitMs(e1.abilityIdMatches(0x9FE8) ? PUNCH_SHORT_DELAY_MS : PUNCH_LONG_DELAY_MS);
				switch ((int) e1.getAbility().getId()) {
					case 0x9FE8 -> s.updateCall(knuckleSandwichSmall, e1);
					case 0x9FE9 -> s.updateCall(knuckleSandwichMid, e1);
					default -> s.updateCall(knuckleSandwichBig, e1);
				}
			});

	private final ModifiableCallout<HeadMarkerEvent> nullifyingDropkick = new ModifiableCallout<>("Nullifying Dropkick", "Shared Buster on {event.target}");

	@NpcAbilityUsedCallout(0x9FF2)
	private final ModifiableCallout<AbilityUsedEvent> banishStorm = new ModifiableCallout<>("Banish Storm", "Avoid Radiating Circles");

	@PlayerHeadmarker(0xD7)
	private final ModifiableCallout<HeadMarkerEvent> holy = new ModifiableCallout<>("Holy", "Spread");

	private final ModifiableCallout<AbilityCastStart> auroralUppercutShort = ModifiableCallout.durationBasedCall("Auroral Uppercut (Short)", "Knockback (Short)");
	private final ModifiableCallout<AbilityCastStart> auroralUppercutMid = ModifiableCallout.durationBasedCall("Auroral Uppercut (Mid)", "Knockback (Mid)");
	private final ModifiableCallout<AbilityCastStart> auroralUppercutBig = ModifiableCallout.durationBasedCall("Auroral Uppercut (Big)", "Knockback (Big)");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> auroralUppercutSq = SqtTemplates.sq(
			30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x9FF6, 0x9FF7, 0x9FF8),
			(e1, s) -> {
				s.waitMs(e1.abilityIdMatches(0x9FF6) ? PUNCH_SHORT_DELAY_MS : PUNCH_LONG_DELAY_MS);
				switch ((int) e1.getAbility().getId()) {
					case 0x9FF6 -> s.updateCall(auroralUppercutShort, e1);
					case 0x9FF7 -> s.updateCall(auroralUppercutMid, e1);
					default -> s.updateCall(auroralUppercutBig, e1);
				}
			});

	@NpcCastCallout(0x9FFA)
	private final ModifiableCallout<AbilityCastStart> banishgaIV = ModifiableCallout.durationBasedCall("Banishga IV", "Raidwide");

	@NpcAbilityUsedCallout(value = 0x9FFA, suppressMs = SUPPRESS_MS)
	private final ModifiableCallout<AbilityUsedEvent> banishgaIVOrbs = new ModifiableCallout<>("Banishga IV (Orbs)", "Avoid Exploding Orbs", 6_000);

	// 自身対象の詠唱で、中央の塔が頭割りの位置になる
	@NpcCastCallout(0x9FFC)
	private final ModifiableCallout<AbilityCastStart> asuranFists = ModifiableCallout.durationBasedCall("Asuran Fists", "Stack");

	private final ModifiableCallout<AbilityCastStart> hundredFists = ModifiableCallout.durationBasedCall("Hundred Fists", "Interrupt {event.source}");
	private final ModifiableCallout<AbilityCastStart> panzerfaust = ModifiableCallout.durationBasedCall("Panzerfaust", "Interrupt {event.source}");
	private final ModifiableCallout<AbilityCastStart> meteor = ModifiableCallout.durationBasedCall("Meteor", "Interrupt {event.source}");
	private final ModifiableCallout<AbilityCastStart> criticalReaver = ModifiableCallout.durationBasedCall("Critical Reaver", "Interrupt {event.source}");

	@HandleEvents
	public void interrupts(EventContext context, AbilityCastStart event) {
		if (!canInterrupt()) {
			return;
		}
		if (event.abilityIdMatches(0x9EC8)) {
			context.accept(hundredFists.getModified(event));
		}
		else if (event.abilityIdMatches(0xA2E2)) {
			context.accept(panzerfaust.getModified(event));
		}
		else if (event.abilityIdMatches(0xA08A)) {
			context.accept(meteor.getModified(event));
		}
		else if (event.abilityIdMatches(0xA13B)) {
			context.accept(criticalReaver.getModified(event));
		}
	}

	// ---- ファフニル ----

	@NpcCastCallout(0x9F96)
	private final ModifiableCallout<AbilityCastStart> darkMatterBlast = ModifiableCallout.durationBasedCall("Dark Matter Blast", "Raidwide");

	@NpcCastCallout(0xA09A)
	private final ModifiableCallout<AbilityCastStart> spikeFlail = ModifiableCallout.durationBasedCall("Spike Flail", "Front");

	@NpcCastCallout(0x9F6E)
	private final ModifiableCallout<AbilityCastStart> dragonBreath = ModifiableCallout.durationBasedCall("Dragon Breath", "Get Under");

	// 詠唱開始時の向きは古いことがあるので、実行時の向きを使う
	@HandleEvents
	public void dragonBreathStore(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x9F6E)) {
			ArenaSector facing = ArenaPos.combatantFacing(event.getSource());
			if (facing != ArenaSector.UNKNOWN) {
				dragonBreathFacing = facing;
			}
		}
	}

	private final ModifiableCallout<AbilityCastStart> touchdown = ModifiableCallout.durationBasedCall("Touchdown", "Out");
	private final ModifiableCallout<AbilityCastStart> touchdownDir = ModifiableCallout.durationBasedCall("Touchdown (After Dragon Breath)", "Out toward {safeDir}");

	@HandleEvents
	public void touchdown(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0xA09C)) {
			return;
		}
		if (dragonBreathFacing != null) {
			context.accept(touchdownDir.getModified(event, Map.of("safeDir", dragonBreathFacing)));
		}
		else {
			context.accept(touchdown.getModified(event));
		}
	}

	@NpcCastCallout(0x9BF2)
	private final ModifiableCallout<AbilityCastStart> balefulBreath = ModifiableCallout.durationBasedCall("Baleful Breath", "Stack");

	@HandleEvents
	public void cleaveMarkers(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(SHARP_SPIKE_MARKER)) {
			spikeTargets.add(event.getTarget());
		}
		else if (event.markerIdMatches(CLOUDSPLITTER_MARKER)) {
			splitterTargets.add(event.getTarget());
		}
		else if (event.markerIdMatches(UNBRIDLED_RAGE_MARKER)) {
			rageTargets.add(event.getTarget());
		}
	}

	private SequentialTrigger<BaseEvent> tankCleaveSq(long castId, List<XivCombatant> targets, ModifiableCallout<AbilityCastStart> onYou, ModifiableCallout<AbilityCastStart> avoid) {
		return SqtTemplates.sq(
				30_000,
				AbilityCastStart.class, acs -> acs.abilityIdMatches(castId),
				(e1, s) -> {
					s.waitMs(MARKER_COLLECT_DELAY_MS);
					s.updateCall(containsPlayer(targets) ? onYou : avoid, e1);
					targets.clear();
					s.waitMs(SUPPRESS_MS);
				});
	}

	private final ModifiableCallout<AbilityCastStart> sharpSpikeOnYou = ModifiableCallout.durationBasedCall("Sharp Spike (On You)", "Tank Cleave on You");
	private final ModifiableCallout<AbilityCastStart> sharpSpikeAvoid = ModifiableCallout.durationBasedCall("Sharp Spike (Not On You)", "Avoid Tank Cleave");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> sharpSpikeSq = tankCleaveSq(0x9F97, spikeTargets, sharpSpikeOnYou, sharpSpikeAvoid);

	// 竜の息吹の後には必ずシャープスパイクが来るが、タッチダウンはそうではない
	@HandleEvents
	public void sharpSpikeClear(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x9F97)) {
			dragonBreathFacing = null;
		}
	}

	@PlayerHeadmarker(0x1F3)
	private final ModifiableCallout<HeadMarkerEvent> horridRoar = new ModifiableCallout<>("Horrid Roar", "Spread");

	@NpcCastCallout(0x9F8D)
	private final ModifiableCallout<AbilityCastStart> absoluteTerror = ModifiableCallout.durationBasedCall("Absolute Terror", "Sides");

	@NpcCastCallout(0x9F8F)
	private final ModifiableCallout<AbilityCastStart> wingedTerror = ModifiableCallout.durationBasedCall("Winged Terror", "Middle");

	@NpcCastCallout(0x9F71)
	private final ModifiableCallout<AbilityCastStart> hurricaneWing = ModifiableCallout.durationBasedCall("Hurricane Wing", "Raidwide x10");

	@NpcCastCallout(0x9F78)
	private final ModifiableCallout<AbilityCastStart> hurricaneWingOuter = ModifiableCallout.durationBasedCall("Hurricane Wing (Outer Ring First)", "Rings Out to In");

	@NpcCastCallout(0x9F7D)
	private final ModifiableCallout<AbilityCastStart> hurricaneWingInner = ModifiableCallout.durationBasedCall("Hurricane Wing (Inner Ring First)", "Rings In to Out");

	// ---- 道中 ----

	@NpcCastCallout(value = 0xA2C3, suppressMs = SUPPRESS_MS)
	private final ModifiableCallout<AbilityCastStart> mysteriousLight = ModifiableCallout.durationBasedCall("Mysterious Light", "Look Away from {event.source}");

	@NpcCastCallout(0x9ECA)
	private final ModifiableCallout<AbilityCastStart> scraplineStorm = ModifiableCallout.durationBasedCall("Scrapline Storm", "Out, then In");

	// ---- アークエンジェル ----

	private final ModifiableCallout<TetherEvent> decisiveBattle = new ModifiableCallout<>("Decisive Battle", "Attack {angel}");
	private final ModifiableCallout<TetherEvent> decisiveBattleUnknown = new ModifiableCallout<>("Decisive Battle (Unknown)", "Attack Angel with Matching Buff");
	private final ModifiableCallout<TetherEvent> chasingTether = new ModifiableCallout<>("Chasing Tether", "Chasing Tether, Run Away", 10_000);

	@HandleEvents
	public void tethers(EventContext context, TetherEvent event) {
		// 線の始点と終点が一定しないので両方を見る
		if (!event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			return;
		}
		if (event.tetherIdMatches(DECISIVE_BATTLE_TETHER)) {
			XivCombatant angel = event.getTargetMatching(cbt -> !cbt.isPc());
			if (angel != null) {
				context.accept(decisiveBattle.getModified(event, Map.of("angel", angel)));
			}
			else {
				context.accept(decisiveBattleUnknown.getModified(event));
			}
		}
		else if (event.tetherIdMatches(CHASING_TETHER)) {
			context.accept(chasingTether.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> cloudsplitterOnYou = ModifiableCallout.durationBasedCall("Cloudsplitter (On You)", "Tank Cleave on You");
	private final ModifiableCallout<AbilityCastStart> cloudsplitterAvoid = ModifiableCallout.durationBasedCall("Cloudsplitter (Not On You)", "Avoid Tank Cleave");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> cloudsplitterSq = tankCleaveSq(0xA076, splitterTargets, cloudsplitterOnYou, cloudsplitterAvoid);

	@NpcCastCallout(0xA07A)
	private final ModifiableCallout<AbilityCastStart> gekko = ModifiableCallout.durationBasedCall("Tachi: Gekko", "Look Away from {event.source}");

	private final ModifiableCallout<AbilityCastStart> spiralFinish = ModifiableCallout.durationBasedCall("Spiral Finish", "Knockback");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> spiralFinishSq = SqtTemplates.sq(
			30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xA06C),
			(e1, s) -> {
				s.waitMs(SPIRAL_FINISH_DELAY_MS);
				s.updateCall(spiralFinish, e1);
			});

	private final ModifiableCallout<HeadMarkerEvent> havocSpiralCw = new ModifiableCallout<>("Havoc Spiral (Clockwise)", "Clockwise", 5_000);
	private final ModifiableCallout<HeadMarkerEvent> havocSpiralCcw = new ModifiableCallout<>("Havoc Spiral (Counterclockwise)", "Counterclockwise", 5_000);
	private final ModifiableCallout<HeadMarkerEvent> arroganceIncarnate = new ModifiableCallout<>("Arrogance Incarnate", "Stack on {event.target}");
	private final ModifiableCallout<HeadMarkerEvent> echoesOfAgony = new ModifiableCallout<>("Echoes of Agony", "Stack on {event.target}");

	@HandleEvents
	public void headMarkers(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x23A)) {
			context.accept(nullifyingDropkick.getModified(event));
		}
		else if (event.markerIdMatches(0xA7)) {
			context.accept(havocSpiralCw.getModified(event));
		}
		else if (event.markerIdMatches(0xA8)) {
			context.accept(havocSpiralCcw.getModified(event));
		}
		else if (event.markerIdMatches(0x131)) {
			context.accept(arroganceIncarnate.getModified(event));
		}
		else if (event.markerIdMatches(0x221)) {
			context.accept(echoesOfAgony.getModified(event));
		}
	}

	@NpcCastCallout(0xA07E)
	private final ModifiableCallout<AbilityCastStart> dragonfall = ModifiableCallout.durationBasedCall("Dragonfall", "Stacks");

	@NpcCastCallout(0xA067)
	private final ModifiableCallout<AbilityCastStart> guillotine = ModifiableCallout.durationBasedCall("Guillotine", "Get Behind");

	@NpcCastCallout(0xA085)
	private final ModifiableCallout<AbilityCastStart> dominionSlash = ModifiableCallout.durationBasedCall("Dominion Slash", "Raidwide");

	@NpcCastCallout(0xA089)
	private final ModifiableCallout<AbilityCastStart> angelHoly = ModifiableCallout.durationBasedCall("Holy (Ark Angel EV)", "Raidwide");

	private final ModifiableCallout<RawAddCombatantEvent> proudPalisade = new ModifiableCallout<>("Proud Palisade", "Kill Ark Shield");

	@HandleEvents
	public void arkShield(EventContext context, RawAddCombatantEvent event) {
		if (event.getEntity().npcIdMatches(ARK_SHIELD_NPC_ID)) {
			context.accept(proudPalisade.getModified(event));
		}
	}

	private final ModifiableCallout<BuffRemoved> mijinGakure = new ModifiableCallout<>("Mijin Gakure", "Interrupt {event.target}");

	@HandleEvents
	public void mijinGakure(EventContext context, BuffRemoved event) {
		if (event.buffIdMatches(UNINTERRUPTED_STATUS) && canInterrupt()) {
			context.accept(mijinGakure.getModified(event));
		}
	}

	// ---- シャドウロード ----

	@HandleEvents(order = -5)
	public void shadowLordTracker(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(SHADOW_LORD_ONLY_ABILITIES)) {
			shadowLordId = event.getSource().getId();
		}
	}

	private final ModifiableCallout<AbilityCastStart> gigaSlashRightLeft = ModifiableCallout.durationBasedCall("Giga Slash (Right First)", "Right, then Left");
	private final ModifiableCallout<AbilityCastStart> gigaSlashLeftRight = ModifiableCallout.durationBasedCall("Giga Slash (Left First)", "Left, then Right");
	private final ModifiableCallout<AbilityCastStart> gigaSlashShadowRightLeft = ModifiableCallout.durationBasedCall("Giga Slash, Lordly Shadow (Right First)", "Right, then Left of Shadow");
	private final ModifiableCallout<AbilityCastStart> gigaSlashShadowLeftRight = ModifiableCallout.durationBasedCall("Giga Slash, Lordly Shadow (Left First)", "Left, then Right of Shadow");
	private final ModifiableCallout<AbilityCastStart> implosionRight = ModifiableCallout.durationBasedCall("Implosion (Right)", "Right and Out");
	private final ModifiableCallout<AbilityCastStart> implosionLeft = ModifiableCallout.durationBasedCall("Implosion (Left)", "Left and Out");

	@HandleEvents
	public void shadowLordCasts(EventContext context, AbilityCastStart event) {
		boolean shadow = isLordlyShadow(event.getSource());
		// 9F3E は左から、9F3F は右から斬る
		if (event.abilityIdMatches(0x9F3E)) {
			context.accept((shadow ? gigaSlashShadowRightLeft : gigaSlashRightLeft).getModified(event));
		}
		else if (event.abilityIdMatches(0x9F3F)) {
			context.accept((shadow ? gigaSlashShadowLeftRight : gigaSlashLeftRight).getModified(event));
		}
		else if (!shadow && event.abilityIdMatches(0x9F44)) {
			context.accept(implosionRight.getModified(event));
		}
		else if (!shadow && event.abilityIdMatches(0x9F45)) {
			context.accept(implosionLeft.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> implosionShadowRight = ModifiableCallout.durationBasedCall("Implosion, Lordly Shadow (Right)", "Right of Shadow and Out");
	private final ModifiableCallout<AbilityCastStart> implosionShadowLeft = ModifiableCallout.durationBasedCall("Implosion, Lordly Shadow (Left)", "Left of Shadow and Out");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> implosionShadowSq = SqtTemplates.sq(
			30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x9F44, 0x9F45) && isLordlyShadow(acs.getSource()),
			(e1, s) -> {
				s.waitMs(LORDLY_IMPLOSION_DELAY_MS);
				s.updateCall(e1.abilityIdMatches(0x9F44) ? implosionShadowRight : implosionShadowLeft, e1);
			});

	@NpcCastCallout(0x9F5B)
	private final ModifiableCallout<AbilityCastStart> umbraSmash = ModifiableCallout.durationBasedCall("Umbra Smash", "Avoid Radiating Lines");

	@NpcCastCallout(0x9F69)
	private final ModifiableCallout<AbilityCastStart> flamesOfHatred = ModifiableCallout.durationBasedCall("Flames of Hatred", "Raidwide");

	@NpcCastCallout({0x9F4A, 0x9F4B})
	private final ModifiableCallout<AbilityCastStart> cthonicFury = ModifiableCallout.durationBasedCall("Cthonic Fury", "Raidwide");

	@HandleEvents
	public void burningFlags(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x9F4F)) {
			burningBattlementsActive = true;
		}
		else if (event.abilityIdMatches(0x9F4E)) {
			burningKeepActive = true;
		}
	}

	private final ModifiableCallout<AbilityCastStart> burningMoat = ModifiableCallout.durationBasedCall("Burning Moat", "In Circles");
	private final ModifiableCallout<AbilityCastStart> burningMoatBattlements = ModifiableCallout.durationBasedCall("Burning Moat + Battlements", "In Circles, Close to Boss");
	private final ModifiableCallout<AbilityCastStart> burningMoatKeep = ModifiableCallout.durationBasedCall("Burning Moat + Keep", "In Circles, Away from Boss");
	private final ModifiableCallout<AbilityCastStart> burningCourt = ModifiableCallout.durationBasedCall("Burning Court", "Out of Circles");
	private final ModifiableCallout<AbilityCastStart> burningCourtBattlements = ModifiableCallout.durationBasedCall("Burning Court + Battlements", "Out of Circles, Close to Boss");
	private final ModifiableCallout<AbilityCastStart> burningCourtKeep = ModifiableCallout.durationBasedCall("Burning Court + Keep", "Out of Circles, Away from Boss");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> burningSq = SqtTemplates.sq(
			30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x9F4D, 0x9F4C),
			(e1, s) -> {
				s.waitMs(BURNING_DELAY_MS);
				boolean moat = e1.abilityIdMatches(0x9F4D);
				if (burningBattlementsActive) {
					s.updateCall(moat ? burningMoatBattlements : burningCourtBattlements, e1);
				}
				else if (burningKeepActive) {
					s.updateCall(moat ? burningMoatKeep : burningCourtKeep, e1);
				}
				else {
					s.updateCall(moat ? burningMoat : burningCourt, e1);
				}
				burningBattlementsActive = false;
				burningKeepActive = false;
				s.waitMs(SUPPRESS_MS);
			});

	private final ModifiableCallout<AbilityCastStart> darkNebulaFirst = ModifiableCallout.durationBasedCall("Dark Nebula (First)", "Knockback from Line");
	private final ModifiableCallout<AbilityCastStart> darkNebula = ModifiableCallout.durationBasedCall("Dark Nebula", "4x Knockback from Lines");

	@HandleEvents
	public void darkNebula(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x9F50)) {
			context.accept((seenNebula ? darkNebula : darkNebulaFirst).getModified(event));
			seenNebula = true;
		}
	}

	// テラスラッシュには詠唱バーがないので、10秒前のシステムメッセージを使う
	private final ModifiableCallout<SystemLogMessageEvent> teraSlash = new ModifiableCallout<>("Tera Slash", "Big Raidwide", 10_000);

	@HandleEvents
	public void teraSlash(EventContext context, SystemLogMessageEvent event) {
		if (event.getId() == TERA_SLASH_LOG_ID) {
			context.accept(teraSlash.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> unbridledRageOnYou = ModifiableCallout.durationBasedCall("Unbridled Rage (On You)", "Tank Cleave on You");
	private final ModifiableCallout<AbilityCastStart> unbridledRageAvoid = ModifiableCallout.durationBasedCall("Unbridled Rage (Not On You)", "Avoid Tank Cleave");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> unbridledRageSq = tankCleaveSq(0x9F67, rageTargets, unbridledRageOnYou, unbridledRageAvoid);

	@PlayerHeadmarker(0x137)
	private final ModifiableCallout<HeadMarkerEvent> darkNova = new ModifiableCallout<>("Dark Nova", "Spread");

	@NpcCastCallout(0x9F55)
	private final ModifiableCallout<AbilityCastStart> bindingSigil = ModifiableCallout.durationBasedCall("Binding Sigil", "Dodge Puddles 3 to 1");

	@NpcCastCallout(0x9F57)
	private final ModifiableCallout<AbilityCastStart> damningStrikes = ModifiableCallout.durationBasedCall("Damning Strikes", "Towers");

	@NpcCastCallout(0xA424)
	private final ModifiableCallout<AbilityCastStart> nightfallRightLeftBack = ModifiableCallout.durationBasedCall("Nightfall Slash (Right, Left, Back)", "Right, then Left, then Back");

	@NpcCastCallout(0xA425)
	private final ModifiableCallout<AbilityCastStart> nightfallRightLeftFront = ModifiableCallout.durationBasedCall("Nightfall Slash (Right, Left, Front)", "Right, then Left, then Front");

	@NpcCastCallout(0xA426)
	private final ModifiableCallout<AbilityCastStart> nightfallLeftRightBack = ModifiableCallout.durationBasedCall("Nightfall Slash (Left, Right, Back)", "Left, then Right, then Back");

	@NpcCastCallout(0xA427)
	private final ModifiableCallout<AbilityCastStart> nightfallLeftRightFront = ModifiableCallout.durationBasedCall("Nightfall Slash (Left, Right, Front)", "Left, then Right, then Front");

	@NpcCastCallout(value = 0x9F53, suppressMs = SUPPRESS_MS)
	private final ModifiableCallout<AbilityCastStart> lordlyUmbraSmash = ModifiableCallout.durationBasedCall("Umbra Smash (Lordly Shadow)", "Dodge Lines, Out then In");

	@NpcCastCallout(0x9F66)
	private final ModifiableCallout<AbilityCastStart> doomArc = ModifiableCallout.durationBasedCall("Doom Arc", "Raidwide with Bleed");
}
