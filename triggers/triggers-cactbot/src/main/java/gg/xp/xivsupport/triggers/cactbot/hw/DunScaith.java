package gg.xp.xivsupport.triggers.cactbot.hw;

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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.RawAddCombatantEvent;
import gg.xp.xivsupport.events.actlines.events.SystemLogMessageEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.state.combatstate.ActiveCastRepository;
import gg.xp.xivsupport.events.state.combatstate.CastTracker;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/alliance/dun_scaith.ts をもとに移植
@CalloutRepo(name = "Dun Scaith", duty = KnownDuty.DunScaith)
public class DunScaith extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long WAILING_ATOMOS = 5510;
	private static final long VOID_SPRITE = 5508;
	private static final long BIT = 3782;
	private static final long AETHER_COLLECTOR = 3781;
	private static final long SHADOW_LIMB = 5516;
	private static final long DEATHGATE = 5523;

	private XivState state;
	private ActiveCastRepository casts;

	public DunScaith(XivState state, ActiveCastRepository casts) {
		this.state = state;
		this.casts = casts;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.DunScaith);
	}

	// デスゲイズ以外の全ボスで共通の頭割りマーカー
	private final ModifiableCallout<HeadMarkerEvent> genericStack = new ModifiableCallout<>("Stack Marker", "Stack on {event.target}");

	@HandleEvents
	public void genericStack(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E)) {
			context.accept(genericStack.getModified(event));
		}
	}

	// デスゲイズ・ホロー

	@NpcCastCallout(value = {0x1C7F, 0x1C90}, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> voidDeathCircle = ModifiableCallout.durationBasedCall("Void Death (Circle)", "Out of Death Circle");

	private final ModifiableCallout<AbilityCastStart> doomsay = ModifiableCallout.durationBasedCall("Doomsay", "Cleanse Doom Soon");

	@HandleEvents
	public void doomsay(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x1C84, 0x1C85) && !event.getSource().isPc() && playerCanCleanse()) {
			context.accept(doomsay.getModified(event));
		}
	}

	// 実際のノックバックは Void Aero IV だが、猶予が短すぎるので前段の詠唱で知らせる
	@NpcCastCallout(value = 0x1C8B, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> blizzardPillars = ModifiableCallout.durationBasedCall("Void Blizzard IV", "Knockback");

	private final ModifiableCallout<RawAddCombatantEvent> voidSprite = new ModifiableCallout<>("Void Sprite Spawn", "Kill Sprites");

	private final RepeatSuppressor voidSpriteSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	@PlayerHeadmarker(0x46)
	private final ModifiableCallout<HeadMarkerEvent> aero2 = new ModifiableCallout<>("Void Aero II", "Drop Tornado Outside");

	@NpcCastCallout(value = {0x1C7B, 0x1C8D}, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> aero3 = ModifiableCallout.durationBasedCall("Void Aero III", "Knockback");

	@NpcCastCallout(value = {0x1C82, 0x1C83}, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> voidDeathSquares = ModifiableCallout.durationBasedCall("Void Death (Squares)", "Avoid Death Squares");

	// フェルディア・ホロー

	@PlayerHeadmarker(0x17)
	private final ModifiableCallout<HeadMarkerEvent> scytheDrop = new ModifiableCallout<>("Blackwind (Scythe)", "Drop Scythe Outside");

	@NpcCastCallout(0x1C98)
	private final ModifiableCallout<AbilityCastStart> jongleursX = ModifiableCallout.durationBasedCall("Jongleur's X", "Buster on {event.target}");

	private final ModifiableCallout<AbilityCastStart> goToAnyUntethered = new ModifiableCallout<>("Atomos: All Chakrams", "Go to Any Untethered");
	private final ModifiableCallout<AbilityCastStart> avoidAllUntethered = new ModifiableCallout<>("Atomos: All Spheres", "Avoid All Untethered");
	private final ModifiableCallout<AbilityCastStart> goToUntetheredBlue = new ModifiableCallout<>("Atomos: Blue Chakram", "Go to Untethered Blue");
	private final ModifiableCallout<AbilityCastStart> goToUntetheredYellow = new ModifiableCallout<>("Atomos: Yellow Chakram", "Go to Untethered Yellow");
	private final ModifiableCallout<AbilityCastStart> avoidUntetheredBlue = new ModifiableCallout<>("Atomos: Blue Sphere", "Avoid Untethered Blue");
	private final ModifiableCallout<AbilityCastStart> avoidUntetheredYellow = new ModifiableCallout<>("Atomos: Yellow Sphere", "Avoid Untethered Yellow");

	private final RepeatSuppressor atomosSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	// 1C9F がエーテル（円）、1CA0 がエーテリアルチャクラム（ドーナツ）
	@AutoFeed
	private final SequentialTrigger<BaseEvent> atomosSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x1C9F, 0x1CA0) && atomosSupp.check(acs),
			(e1, s) -> {
				// 詠唱がアトモスの出現より先に来ることがあるので、出揃うのを待ってから判定する
				s.waitMs(1_000);
				List<AbilityCastStart> atomosCasts = new ArrayList<>(casts.getActiveCastsById(0x1C9F, 0x1CA0)
						.stream()
						.map(CastTracker::getCast)
						.toList());
				if (!atomosCasts.contains(e1)) {
					atomosCasts.add(e1);
				}
				List<Boolean> sphereIsWailing = new ArrayList<>();
				List<Boolean> donutIsWailing = new ArrayList<>();
				for (AbilityCastStart cast : atomosCasts) {
					boolean wailing = state.getLatestCombatantData(cast.getTarget()).getbNpcNameId() == WAILING_ATOMOS;
					if (cast.abilityIdMatches(0x1C9F)) {
						sphereIsWailing.add(wailing);
					}
					else {
						donutIsWailing.add(wailing);
					}
				}
				if (donutIsWailing.size() == 2) {
					s.updateCall(goToAnyUntethered, e1);
				}
				else if (sphereIsWailing.size() == 2) {
					s.updateCall(avoidAllUntethered, e1);
				}
				else if (donutIsWailing.size() == 1) {
					// チャクラムが1つだけなら、そちらの色は必ず安全なのでもう片方は気にしなくてよい
					s.updateCall(donutIsWailing.get(0) ? goToUntetheredBlue : goToUntetheredYellow, e1);
				}
				else if (!sphereIsWailing.isEmpty()) {
					// 円だけの場合はもう片方の色が安全とは限らないので、避ける方を伝える
					s.updateCall(sphereIsWailing.get(0) ? avoidUntetheredBlue : avoidUntetheredYellow, e1);
				}
			});

	@NpcCastCallout(0x1CAA)
	private final ModifiableCallout<AbilityCastStart> blackfire = ModifiableCallout.durationBasedCall("Blackfire", "Avoid Puddles");

	private final ModifiableCallout<BuffApplied> debilitatorFire = new ModifiableCallout<>("Debilitator (Fire)", "Change Puddles to Water");
	private final ModifiableCallout<BuffApplied> debilitatorWater = new ModifiableCallout<>("Debilitator (Water)", "Change Puddles to Fire");

	private final RepeatSuppressor debilitatorFireSupp = new RepeatSuppressor(Duration.ofSeconds(10));
	private final RepeatSuppressor debilitatorWaterSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	@HandleEvents
	public void debilitator(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x471)) {
			if (debilitatorFireSupp.check(event)) {
				context.accept(debilitatorFire.getModified(event));
			}
		}
		else if (event.buffIdMatches(0x485)) {
			if (debilitatorWaterSupp.check(event)) {
				context.accept(debilitatorWater.getModified(event));
			}
		}
	}

	// プロトアルテマ

	@NpcCastCallout({0x1E52, 0x1D9D})
	private final ModifiableCallout<AbilityCastStart> protoUltimaRaidwide = ModifiableCallout.durationBasedCall("Aetherochemical Flare/Supernova", "Raidwide");

	@PlayerStatusCallout(0x232)
	private final ModifiableCallout<BuffApplied> prey = ModifiableCallout.durationBasedCall("Prey", "Away from Party, Keep Moving");

	private final ModifiableCallout<RawAddCombatantEvent> bitCircles = new ModifiableCallout<>("Bit Spawn", "Avoid Bit AoEs");
	private final ModifiableCallout<RawAddCombatantEvent> aetherCollectors = new ModifiableCallout<>("Aether Collector Spawn", "Kill Collectors");

	private final RepeatSuppressor bitSupp = new RepeatSuppressor(Duration.ofSeconds(5));
	private final RepeatSuppressor collectorSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	// スカアハ

	// 実際の攻撃は 1D20 だが詠唱バーが出るのは前段のほう
	@NpcCastCallout(value = {0x1D1E, 0x1D1F}, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> shadespin = ModifiableCallout.durationBasedCall("Shadespin", "Avoid Arm Slaps");

	@NpcAbilityUsedCallout(value = {0x1D1B, 0x1D2B}, suppressMs = 5_000)
	private final ModifiableCallout<AbilityUsedEvent> thirtyThorns = new ModifiableCallout<>("Thirty Thorns", "Out of Melee");

	@NpcCastCallout(0x1D2F)
	private final ModifiableCallout<AbilityCastStart> thirtyArrows = ModifiableCallout.durationBasedCall("Thirty Arrows", "Avoid Lines");

	@NpcCastCallout(0x1D32)
	private final ModifiableCallout<AbilityCastStart> thirtySouls = ModifiableCallout.durationBasedCall("Thirty Souls", "Raidwide");

	private final ModifiableCallout<SystemLogMessageEvent> shadowLinks = new ModifiableCallout<>("Shadow Links", "Stop Moving");

	private final RepeatSuppressor shadowLinksSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	// 「影が床に集まっていく」のログ以外に前兆がない
	@HandleEvents
	public void shadowLinks(EventContext context, SystemLogMessageEvent event) {
		if (event.getId() == 0xABB && shadowLinksSupp.check(event)) {
			context.accept(shadowLinks.getModified(event));
		}
	}

	private final ModifiableCallout<RawAddCombatantEvent> shadowLimb = new ModifiableCallout<>("Shadow Limb Spawn", "Kill Hands");

	private final RepeatSuppressor shadowLimbSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@NpcCastCallout(0x1CD1)
	private final ModifiableCallout<AbilityCastStart> connlaSpawn = ModifiableCallout.durationBasedCall("Connla Spawn", "Avoid AoE, Kill Connla");

	// スカアハとディアボロスで処理が同じ
	@PlayerHeadmarker(0x5C)
	private final ModifiableCallout<HeadMarkerEvent> noxOrbs = new ModifiableCallout<>("Nox", "Take Orb Outside");

	@NpcCastCallout({0x1D23, 0x1C1A})
	private final ModifiableCallout<AbilityCastStart> shadethrust = ModifiableCallout.durationBasedCall("Shadethrust", "Away from Front");

	// ディアボロス

	@NpcCastCallout(0x1C12)
	private final ModifiableCallout<AbilityCastStart> ultimateTerror = ModifiableCallout.durationBasedCall("Ultimate Terror", "In");

	@NpcCastCallout({0x1C0E, 0x1C20})
	private final ModifiableCallout<AbilityCastStart> nightmare = ModifiableCallout.durationBasedCall("Nightmare", "Look Away");

	private final ModifiableCallout<BuffApplied> noctoshield = new ModifiableCallout<>("Noctoshield", "Big Hits, Mitigate");

	private final RepeatSuppressor noctoshieldSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void noctoshield(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x1AA) && !event.getTarget().isPc() && noctoshieldSupp.check(event)) {
			context.accept(noctoshield.getModified(event));
		}
	}

	@NpcCastCallout(value = {0x1C10, 0x1C11}, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> ruinousOmen = ModifiableCallout.durationBasedCall("Ruinous Omen", "Raidwide");

	private final ModifiableCallout<RawAddCombatantEvent> deathgates = new ModifiableCallout<>("Deathgate Spawn", "Kill Deathgates");

	private final RepeatSuppressor deathgateSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@NpcCastCallout(0x1C19)
	private final ModifiableCallout<AbilityCastStart> camisado = ModifiableCallout.durationBasedCall("Camisado", "Buster on {event.target}");

	private final ModifiableCallout<HeadMarkerEvent> hollowNightOnYou = new ModifiableCallout<>("Hollow Night (on You)", "Gaze Stack on You");
	private final ModifiableCallout<HeadMarkerEvent> hollowNightOther = new ModifiableCallout<>("Hollow Night (on Other)", "Stack on {event.target}, Look Away");

	// 視線付きの頭割りなので、対象者だけ伝え方を変える
	@HandleEvents
	public void hollowNight(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x5B)) {
			if (event.getTarget().isThePlayer()) {
				context.accept(hollowNightOnYou.getModified(event));
			}
			else {
				context.accept(hollowNightOther.getModified(event));
			}
		}
	}

	@NpcCastCallout(value = {0x1C22, 0x1C23}, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> hollowOmen = ModifiableCallout.durationBasedCall("Hollow Omen", "Big Raidwide");

	// タンク用の頭割りマーカー
	private final ModifiableCallout<HeadMarkerEvent> blindside = new ModifiableCallout<>("Blindside", "Stack on {event.target}");

	@HandleEvents
	public void blindside(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x5D)) {
			context.accept(blindside.getModified(event));
		}
	}

	@PlayerHeadmarker(0x28)
	private final ModifiableCallout<HeadMarkerEvent> earthShaker = new ModifiableCallout<>("Earth Shaker", "Earthshaker on You");

	@HandleEvents
	public void addedCombatant(EventContext context, RawAddCombatantEvent event) {
		long nameId = event.getEntity().getbNpcNameId();
		if (nameId == VOID_SPRITE) {
			if (voidSpriteSupp.check(event)) {
				context.accept(voidSprite.getModified(event));
			}
		}
		else if (nameId == BIT) {
			if (bitSupp.check(event)) {
				context.accept(bitCircles.getModified(event));
			}
		}
		else if (nameId == AETHER_COLLECTOR) {
			if (collectorSupp.check(event)) {
				context.accept(aetherCollectors.getModified(event));
			}
		}
		else if (nameId == SHADOW_LIMB) {
			if (shadowLimbSupp.check(event)) {
				context.accept(shadowLimb.getModified(event));
			}
		}
		else if (nameId == DEATHGATE) {
			if (deathgateSupp.check(event)) {
				context.accept(deathgates.getModified(event));
			}
		}
	}

	private boolean playerCanCleanse() {
		return state.playerJobMatches(job -> job.isHealer() || job == Job.BRD);
	}

}
