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
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/alliance/the_puppets_bunker.ts をもとに移植
@CalloutRepo(name = "The Puppets Bunker", duty = KnownDuty.ThePuppetsBunker)
public class ThePuppetsBunker extends AutoChildEventHandler implements FilteredEventHandler {

	// 同時に付く対象者マーカーや詠唱が揃うまで待つ時間
	private static final int COLLECT_MS = 500;
	// プライムブレードの詠唱者がこれ以上いれば分身による攻撃とみなす
	private static final int PUPPET_CLONE_MIN_CASTERS = 2;
	private static final int CLONE_COLLECT_MS = 300;

	private enum Phase {
		NONE,
		SUPERIOR,
		COMPOUND
	}

	private XivState state;

	public ThePuppetsBunker(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.ThePuppetsBunker);
	}

	private boolean isHealer() {
		return state.playerJobMatches(Job::isHealer);
	}

	private Phase phase = Phase.NONE;
	private boolean busterOnPlayer;
	private boolean lifesLastSongCalled;
	// 先頭の左右は詠唱時にコール済みなので null にして飛ばす
	private final List<Boolean> swipes = new LinkedList<>();
	private int heavyPodCount;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		phase = Phase.NONE;
		busterOnPlayer = false;
		lifesLastSongCalled = false;
		swipes.clear();
		heavyPodCount = 0;
	}

	// 複数人に同時に来るタンク強攻撃の対象者を集める
	private void collectBusterTarget(XivCombatant target) {
		if (target.isThePlayer()) {
			busterOnPlayer = true;
		}
	}

	// 813P：イージス

	@PlayerHeadmarker(0xC6)
	private final ModifiableCallout<HeadMarkerEvent> antiPersonnelLaserOnYou = new ModifiableCallout<>("Anti-Personnel Laser: On You", "Buster on You");
	private final ModifiableCallout<HeadMarkerEvent> antiPersonnelLaserHealer = new ModifiableCallout<>("Anti-Personnel Laser: Healer", "Tankbusters");
	private final ModifiableCallout<HeadMarkerEvent> antiPersonnelLaserAvoid = new ModifiableCallout<>("Anti-Personnel Laser: Not on You", "Avoid Tankbusters");
	private final RepeatSuppressor antiPersonnelLaserSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void antiPersonnelLaserCollect(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0xC6)) {
			collectBusterTarget(event.getTarget());
		}
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> antiPersonnelLaserSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(0xC6) && antiPersonnelLaserSupp.check(hm),
			(e1, s) -> {
				s.waitMs(COLLECT_MS);
				if (!busterOnPlayer) {
					s.updateCall(isHealer() ? antiPersonnelLaserHealer : antiPersonnelLaserAvoid, e1);
				}
				busterOnPlayer = false;
			});

	@NpcCastCallout(0x5073)
	private final ModifiableCallout<AbilityCastStart> beamCannons = ModifiableCallout.durationBasedCall("Beam Cannons", "Go to Narrow Intersection");

	private final ModifiableCallout<HeadMarkerEvent> surfaceLaser = new ModifiableCallout<>("Aerial Support: Surface Laser", "Chasing Laser, Get Away");
	private final ModifiableCallout<HeadMarkerEvent> incendiaryBombing = new ModifiableCallout<>("Maneuver: Incendiary Bombing", "Fire Puddle on You");

	@HandleEvents
	public void marker17(EventContext context, HeadMarkerEvent event) {
		// 同じマーカーが813Pではレーザー、105Pでは火炎の設置になる
		if (event.markerIdMatches(0x17) && event.getTarget().isThePlayer()) {
			context.accept((phase == Phase.SUPERIOR ? incendiaryBombing : surfaceLaser).getModified(event));
		}
	}

	@NpcCastCallout(0x5080)
	private final ModifiableCallout<AbilityCastStart> refractionCannonsLeft = ModifiableCallout.durationBasedCall("Refraction Cannons (Left)", "Left, Behind Wing");

	@NpcCastCallout(0x507F)
	private final ModifiableCallout<AbilityCastStart> refractionCannonsRight = ModifiableCallout.durationBasedCall("Refraction Cannons (Right)", "Right, Behind Wing");

	@NpcCastCallout(0x508F)
	private final ModifiableCallout<AbilityCastStart> aegisHighPoweredLaser = ModifiableCallout.durationBasedCall("High-Powered Laser (Aegis)", "Stack");

	private final ModifiableCallout<AbilityCastStart> lifesLastSong = ModifiableCallout.durationBasedCall("Life's Last Song", "Dodge into Ring Gap");

	@HandleEvents
	public void lifesLastSong(EventContext context, AbilityCastStart event) {
		// ほぼ絶え間なく繰り返すので、初回だけ注意喚起する
		if (event.abilityIdMatches(0x53B3) && !lifesLastSongCalled) {
			lifesLastSongCalled = true;
			context.accept(lifesLastSong.getModified(event));
		}
	}

	// 軽装甲

	@NpcCastCallout(value = 0x5211, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> voltArray = ModifiableCallout.durationBasedCall("Volt Array", "Interrupt {event.source}");

	@PlayerHeadmarker(0x8B)
	private final ModifiableCallout<HeadMarkerEvent> spreadMarker = new ModifiableCallout<>("Spread Marker", "Spread");

	@NpcCastCallout(value = 0x5213, onYou = true)
	private final ModifiableCallout<AbilityCastStart> martialArmOnYou = ModifiableCallout.durationBasedCall("Maneuver: Martial Arm (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> martialArmHealer = ModifiableCallout.durationBasedCall("Maneuver: Martial Arm (Healer)", "Tankbusters");
	private final RepeatSuppressor martialArmSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void castBusterCollect(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x5213, 0x4FC5)) {
			collectBusterTarget(event.getTarget());
		}
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> martialArmSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x5213) && martialArmSupp.check(acs),
			(e1, s) -> {
				s.waitMs(COLLECT_MS);
				// 範囲が広がらないので、ヒーラー以外には何も言わない
				if (!busterOnPlayer && isHealer()) {
					s.updateCall(martialArmHealer, e1);
				}
				busterOnPlayer = false;
			});

	// 105P：特殊防御

	@HandleEvents
	public void superiorPhase(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x4FA6, 0x4FA7, 0x4FA8)) {
			phase = Phase.SUPERIOR;
		}
	}

	@NpcCastCallout(value = 0x4FBD, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> missileCommand = ModifiableCallout.durationBasedCall("Missile Command", "Raidwide");

	@NpcCastCallout(value = 0x4FB4, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> superiorHighPoweredLaser = ModifiableCallout.durationBasedCall("High-Powered Laser (Superior)", "Line Stack, Point Outside");

	@NpcCastCallout(value = {0x4FA9, 0x5511, 0x5513}, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> sharpTurnInside = ModifiableCallout.durationBasedCall("Sharp Turn (Inside)", "Move Inside");

	@NpcCastCallout(value = {0x4FAA, 0x5512, 0x5514}, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> sharpTurnOutside = ModifiableCallout.durationBasedCall("Sharp Turn (Outside)", "Move Outside");

	@NpcCastCallout(value = 0x4FC5, onYou = true)
	private final ModifiableCallout<AbilityCastStart> precisionMissileOnYou = ModifiableCallout.durationBasedCall("Precision Guided Missile (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> precisionMissileHealer = ModifiableCallout.durationBasedCall("Precision Guided Missile (Healer)", "Tankbusters");
	private final ModifiableCallout<AbilityCastStart> precisionMissileAvoid = ModifiableCallout.durationBasedCall("Precision Guided Missile (Not on You)", "Avoid Tankbusters");
	private final RepeatSuppressor precisionMissileSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> precisionMissileSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x4FC5) && precisionMissileSupp.check(acs),
			(e1, s) -> {
				s.waitMs(COLLECT_MS);
				if (!busterOnPlayer) {
					s.updateCall(isHealer() ? precisionMissileHealer : precisionMissileAvoid, e1);
				}
				busterOnPlayer = false;
			});

	private static boolean isSlidingSwipe(long id) {
		return id == 0x4FAC || id == 0x4FAD || id == 0x550D || id == 0x550E || id == 0x550F || id == 0x5510;
	}

	private static boolean isSwipeRight(long id) {
		return id == 0x4FAC || id == 0x550D || id == 0x550F;
	}

	private final ModifiableCallout<AbilityCastStart> slidingSwipeFirstRight = new ModifiableCallout<>("Sliding Swipe: First (Right)", "Right", 6_000);
	private final ModifiableCallout<AbilityCastStart> slidingSwipeFirstLeft = new ModifiableCallout<>("Sliding Swipe: First (Left)", "Left", 6_000);
	private final ModifiableCallout<AbilityUsedEvent> slidingSwipeNextRight = new ModifiableCallout<>("Sliding Swipe: Next (Right)", "Right");
	private final ModifiableCallout<AbilityUsedEvent> slidingSwipeNextLeft = new ModifiableCallout<>("Sliding Swipe: Next (Left)", "Left");

	@HandleEvents
	public void slidingSwipeCast(EventContext context, AbilityCastStart event) {
		long id = event.getAbility().getId();
		if (!isSlidingSwipe(id)) {
			return;
		}
		boolean right = isSwipeRight(id);
		swipes.add(right);
		if (swipes.size() == 1) {
			swipes.set(0, null);
			context.accept((right ? slidingSwipeFirstRight : slidingSwipeFirstLeft).getModified(event));
		}
	}

	@HandleEvents
	public void slidingSwipeUsed(EventContext context, AbilityUsedEvent event) {
		if (!isSlidingSwipe(event.getAbility().getId()) || swipes.isEmpty()) {
			return;
		}
		// 先頭がコール済みなら、それが発動した時点で次の向きを知らせる
		Boolean next = swipes.remove(0);
		if (next == null && !swipes.isEmpty()) {
			next = swipes.remove(0);
		}
		if (next != null) {
			context.accept((next ? slidingSwipeNextRight : slidingSwipeNextLeft).getModified(event));
		}
	}

	// 905P：重砲撃

	@NpcCastCallout(0x5006)
	private final ModifiableCallout<AbilityCastStart> heavyVoltArray = ModifiableCallout.durationBasedCall("Volt Array (Heavy)", "Raidwide");

	@NpcCastCallout(0x4FED)
	private final ModifiableCallout<AbilityCastStart> activeLaserTurret = ModifiableCallout.durationBasedCall("Active Laser Turret", "Get Outside Upper Laser");

	private final ModifiableCallout<AbilityCastStart> activeLaserTurretMove = new ModifiableCallout<>("Active Laser Turret: Move", "Move Away");
	private final RepeatSuppressor activeLaserTurretMoveSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> activeLaserTurretMoveSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x5086) && activeLaserTurretMoveSupp.check(acs),
			(e1, s) -> {
				s.waitMs(5_300);
				s.updateCall(activeLaserTurretMove, e1);
			});

	@PlayerHeadmarker(0xAC)
	private final ModifiableCallout<HeadMarkerEvent> unconventionalVoltage = new ModifiableCallout<>("Unconventional Voltage", "Voltage Cleave on You");

	@NpcCastCallout(0x5000)
	private final ModifiableCallout<AbilityCastStart> revolvingLaser = ModifiableCallout.durationBasedCall("Revolving Laser", "In");

	private final ModifiableCallout<AbilityCastStart> heavyHighPoweredLaserOnYou = ModifiableCallout.durationBasedCall("High-Powered Laser (Heavy, Tank)", "Tank Laser Cleave on You");
	private final ModifiableCallout<AbilityCastStart> heavyHighPoweredLaserAvoid = ModifiableCallout.durationBasedCall("High-Powered Laser (Heavy, Non-Tank)", "Avoid Tank Lasers");

	@HandleEvents
	public void heavyHighPoweredLaser(EventContext context, AbilityCastStart event) {
		// 詠唱は1回だけで、タンク全員に順番に撃たれる
		if (!event.abilityIdMatches(0x5001)) {
			return;
		}
		boolean tankTarget = state.playerJobMatches(Job::isTank) || event.getTarget().isThePlayer();
		context.accept((tankTarget ? heavyHighPoweredLaserOnYou : heavyHighPoweredLaserAvoid).getModified(event));
	}

	private final ModifiableCallout<AbilityCastStart> supportPodBetweenPods = new ModifiableCallout<>("Support Pod: Lasers/Hammers", "Get Outside Between Pods");
	private final ModifiableCallout<AbilityCastStart> supportPodScreens = new ModifiableCallout<>("Support Pod: Screens", "Get Between Lasers, Watch Tethers");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> supportPodSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x4FE9),
			(e1, s) -> {
				// ポッドが出てくるのがおよそこのタイミング
				s.waitMs(6_000);
				heavyPodCount++;
				// 最初の2回はレーザーとハンマーで、どちらの順でも安置の方向は同じ
				s.updateCall(heavyPodCount <= 2 ? supportPodBetweenPods : supportPodScreens, e1);
			});

	@NpcCastCallout(0x4FEC)
	private final ModifiableCallout<AbilityCastStart> synthesizeCompound = ModifiableCallout.durationBasedCall("Synthesize Compound", "Turn Towers Blue");

	@PlayerHeadmarker(0xA4)
	private final ModifiableCallout<HeadMarkerEvent> hallwayTargetedLaser = new ModifiableCallout<>("Targeted Laser (Hallway)", "Laser on You");

	// 最終防衛機構

	private final ModifiableCallout<AbilityCastStart> mechanicalLaceration = ModifiableCallout.durationBasedCall("Mechanical Laceration / Centrifugal Slice", "Raidwide");

	@HandleEvents
	public void compoundRaidwide(EventContext context, AbilityCastStart event) {
		// 第一形態と第二形態で同じIDを使う
		if (event.abilityIdMatches(0x51B8)) {
			phase = Phase.COMPOUND;
			context.accept(mechanicalLaceration.getModified(event));
		}
	}

	@NpcCastCallout(0x51B3)
	private final ModifiableCallout<AbilityCastStart> mechanicalDissection = ModifiableCallout.durationBasedCall("Mechanical Dissection", "Sides");

	@NpcCastCallout(0x51B4)
	private final ModifiableCallout<AbilityCastStart> mechanicalDecapitation = ModifiableCallout.durationBasedCall("Mechanical Decapitation", "In");

	@NpcCastCallout(0x541F)
	private final ModifiableCallout<AbilityCastStart> primeBladeOutJump = ModifiableCallout.durationBasedCall("Prime Blade (Out, Jump)", "Out");

	@NpcCastCallout(value = {0x5420, 0x5199})
	private final ModifiableCallout<AbilityCastStart> primeBladeBehind = ModifiableCallout.durationBasedCall("Prime Blade (Behind)", "Get Behind");

	private final ModifiableCallout<AbilityCastStart> primeBladeOut = ModifiableCallout.durationBasedCall("Prime Blade (Out)", "Out");
	private final ModifiableCallout<AbilityCastStart> primeBladeIn = ModifiableCallout.durationBasedCall("Prime Blade (In)", "In");
	private final ModifiableCallout<AbilityCastStart> puppetGuaranteedIn = ModifiableCallout.durationBasedCall("Prime Blade (Clones, Corner In)", "Get Under Clone Corner");
	private final ModifiableCallout<AbilityCastStart> puppetIn = ModifiableCallout.durationBasedCall("Prime Blade (Clones, Cardinal In)", "Get Under Cardinal Clone");
	private final ModifiableCallout<AbilityCastStart> puppetOut = ModifiableCallout.durationBasedCall("Prime Blade (Clones, Out)", "Away from Clones");

	private static boolean isSharedPrimeBlade(AbilityCastStart acs) {
		return acs.abilityIdMatches(0x5421, 0x519A, 0x5198);
	}

	// 本体と分身が同じIDを使うので、同時に詠唱した数で分身かどうかを見分ける
	@AutoFeed
	private final SequentialTrigger<BaseEvent> primeBladeSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, ThePuppetsBunker::isSharedPrimeBlade,
			(e1, s) -> {
				long id = e1.getAbility().getId();
				Set<XivCombatant> casters = new HashSet<>();
				casters.add(e1.getSource());
				List<AbilityCastStart> others = new ArrayList<>(s.waitEventsUntil(8,
						AbilityCastStart.class, acs -> acs.abilityIdMatches(id),
						BaseEvent.class, e -> e1.getEffectiveTimeSince().toMillis() > CLONE_COLLECT_MS));
				others.forEach(acs -> casters.add(acs.getSource()));
				boolean clones = casters.size() >= PUPPET_CLONE_MIN_CASTERS;
				ModifiableCallout<AbilityCastStart> call;
				if (id == 0x5421) {
					call = clones ? puppetGuaranteedIn : primeBladeIn;
				}
				else if (id == 0x519A) {
					call = clones ? puppetIn : primeBladeIn;
				}
				else {
					call = clones ? puppetOut : primeBladeOut;
				}
				s.updateCall(call, e1);
			});

	@PlayerHeadmarker(0xDA)
	private final ModifiableCallout<HeadMarkerEvent> r012LaserOnYou = new ModifiableCallout<>("R012: Laser (On You)", "Buster on You");
	private final ModifiableCallout<HeadMarkerEvent> r012LaserHealer = new ModifiableCallout<>("R012: Laser (Healer)", "Tankbusters");
	private final RepeatSuppressor r012LaserSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void r012LaserCollect(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0xDA)) {
			collectBusterTarget(event.getTarget());
		}
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> r012LaserSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(0xDA) && r012LaserSupp.check(hm),
			(e1, s) -> {
				s.waitMs(COLLECT_MS);
				// タンク以外にも散開マーカーが大量に付くので、避けろとは言わない
				if (!busterOnPlayer && isHealer()) {
					s.updateCall(r012LaserHealer, e1);
				}
				busterOnPlayer = false;
			});

	private final ModifiableCallout<HeadMarkerEvent> threePartsDisdain = new ModifiableCallout<>("Three Parts Disdain", "Stack on {event.target}");

	@HandleEvents
	public void threePartsDisdain(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E) && phase == Phase.COMPOUND) {
			context.accept(threePartsDisdain.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> threePartsDisdainKnockback = new ModifiableCallout<>("Three Parts Disdain: Knockback", "Knockback");
	private final RepeatSuppressor threePartsDisdainSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> threePartsDisdainKnockbackSq = SqtTemplates.sq(10_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(0x3E) && phase == Phase.COMPOUND && threePartsDisdainSupp.check(hm),
			(e1, s) -> {
				// 堅守系の効果時間は6秒で、マーカーから最後の一撃まで約9.6秒ある
				s.waitMs(3_600);
				s.updateCall(threePartsDisdainKnockback, e1);
			});

	private final ModifiableCallout<HeadMarkerEvent> fourPartsJump = new ModifiableCallout<>("Four Parts Resolve: Jump", "Jump {num} on You");
	private final ModifiableCallout<HeadMarkerEvent> fourPartsCleave = new ModifiableCallout<>("Four Parts Resolve: Cleave", "Cleave {num} on You");

	@HandleEvents
	public void fourPartsResolve(EventContext context, HeadMarkerEvent event) {
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		if (event.markerIdMatches(0x4F, 0x51)) {
			context.accept(fourPartsJump.getModified(event, Map.<String, Object>of("num", event.markerIdMatches(0x4F) ? 1 : 2)));
		}
		else if (event.markerIdMatches(0x50, 0x52)) {
			context.accept(fourPartsCleave.getModified(event, Map.<String, Object>of("num", event.markerIdMatches(0x50) ? 1 : 2)));
		}
	}

	private final ModifiableCallout<AbilityCastStart> energyCompression = new ModifiableCallout<>("Energy Compression", "Get Towers");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> energyCompressionSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x51A6),
			(e1, s) -> {
				s.waitMs(4_000);
				s.updateCall(energyCompression, e1);
			});

	@NpcCastCallout(value = 0x541B, suppressMs = 2_000)
	private final ModifiableCallout<AbilityCastStart> r011Laser = ModifiableCallout.durationBasedCall("R011: Laser", "Avoid Lasers");

}
