package gg.xp.xivsupport.triggers.dttrial;

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
import gg.xp.xivsupport.events.actlines.events.MapEffectEvent;
import gg.xp.xivsupport.events.actlines.events.RawAddCombatantEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.gui.util.HasFriendlyName;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/trial/valigarmanda.ts をもとに移植
@CalloutRepo(name = "Worqor Lar Dor (Valigarmanda)", duty = KnownDuty.WorqorLarDor)
public class WorqorLarDor extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	private enum Phase {
		START,
		STORM,
		ICE
	}

	private enum ArcaneLane implements HasFriendlyName {
		NORTH_FRONT("North Row, Front Half"),
		NORTH_BACK("North Row, Back Half"),
		MIDDLE_FRONT("Middle Row, Front Half"),
		MIDDLE_BACK("Middle Row, Back Half"),
		SOUTH_FRONT("South Row, Front Half"),
		SOUTH_BACK("South Row, Back Half");

		private final String friendlyName;

		ArcaneLane(String friendlyName) {
			this.friendlyName = friendlyName;
		}

		@Override
		public String getFriendlyName() {
			return friendlyName;
		}

		// 玉は y = 87.5, 92.5, ... 112.5 の6か所のうち5か所に出る
		static ArcaneLane forY(double y) {
			if (y < 88) {
				return NORTH_FRONT;
			}
			else if (y < 93) {
				return NORTH_BACK;
			}
			else if (y < 98) {
				return MIDDLE_FRONT;
			}
			else if (y < 103) {
				return MIDDLE_BACK;
			}
			else if (y < 108) {
				return SOUTH_FRONT;
			}
			else {
				return SOUTH_BACK;
			}
		}
	}

	private volatile Phase phase = Phase.START;
	private volatile boolean iceTalonOnYou;

	public WorqorLarDor(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.WorqorLarDor);
	}

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		phase = Phase.START;
		iceTalonOnYou = false;
	}

	@HandleEvents
	public void phaseTracker(EventContext context, AbilityCastStart event) {
		// 8D41 が氷、95C2 が雷のリビングディザスター
		if (event.abilityIdMatches(0x95C2)) {
			phase = Phase.STORM;
		}
		else if (event.abilityIdMatches(0x8D41)) {
			phase = Phase.ICE;
		}
	}

	// 全体

	@NpcCastCallout(0x8D3F)
	private final ModifiableCallout<AbilityCastStart> stranglingCoil = ModifiableCallout.durationBasedCall("Strangling Coil", "In");

	@NpcCastCallout(0x8D3D)
	private final ModifiableCallout<AbilityCastStart> slitheringStrike = ModifiableCallout.durationBasedCall("Slithering Strike", "Out");

	@NpcCastCallout(0x8D3B)
	private final ModifiableCallout<AbilityCastStart> susurrantBreath = ModifiableCallout.durationBasedCall("Susurrant Breath", "Front Corners");

	@HandleEvents
	public void iceTalonMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x0158) && event.getTarget().isThePlayer()) {
			iceTalonOnYou = true;
		}
	}

	private final ModifiableCallout<AbilityCastStart> iceTalonOnYouCall = ModifiableCallout.durationBasedCall("Ice Talon (on You)", "Tankbuster on You");
	private final ModifiableCallout<AbilityCastStart> iceTalonAvoid = ModifiableCallout.durationBasedCall("Ice Talon (Not on You)", "Tankbusters");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> iceTalonSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x8D58),
			(e1, s) -> {
				// 頭上マーカーが詠唱より少し遅れて付くことがあるので待つ
				s.waitMs(200);
				s.updateCall(iceTalonOnYou ? iceTalonOnYouCall : iceTalonAvoid, e1);
				iceTalonOnYou = false;
			});

	private final ModifiableCallout<AbilityCastStart> skyruinIce = ModifiableCallout.durationBasedCall("Skyruin (Ice)", "Raidwide");
	private final ModifiableCallout<AbilityCastStart> skyruinStorm = ModifiableCallout.durationBasedCall("Skyruin (Storm)", "Raidwide");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> skyruinSq = SqtTemplates.sq(20_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x8D41, 0x95C2),
			(e1, s) -> {
				// 表示上の詠唱が約11.5秒と長いので、早すぎないように遅らせる
				s.waitMs(5_000);
				s.updateCall(e1.abilityIdMatches(0x95C2) ? skyruinStorm : skyruinIce, e1);
			});

	@NpcCastCallout({0x8D44, 0x8D46})
	private final ModifiableCallout<AbilityCastStart> disasterZone = ModifiableCallout.durationBasedCall("Disaster Zone", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> ruinfallTowerTank = ModifiableCallout.durationBasedCall("Ruinfall Tower: Tank", "Soak Tower");
	private final ModifiableCallout<AbilityCastStart> ruinfallTowerNonTank = ModifiableCallout.durationBasedCall("Ruinfall Tower: Non-Tank", "Avoid Tower");

	@HandleEvents
	public void ruinfallTower(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x8D5B)) {
			context.accept((state.playerJobMatches(Job::isTank) ? ruinfallTowerTank : ruinfallTowerNonTank).getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> ruinfallKnockback = ModifiableCallout.durationBasedCall("Ruinfall Knockback", "Knockback");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> ruinfallKnockbackSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x8D5D),
			(e1, s) -> {
				// 詠唱開始からノックバックまで8秒ほどあるので遅らせる
				s.waitMs(3_000);
				s.updateCall(ruinfallKnockback, e1);
			});

	// 雷フェーズ

	@NpcCastCallout(0x8D4F)
	private final ModifiableCallout<AbilityCastStart> thunderousBreath = ModifiableCallout.durationBasedCall("Thunderous Breath", "Go Up");

	@NpcCastCallout(0x8D4C)
	private final ModifiableCallout<AbilityCastStart> blightedBolt = ModifiableCallout.durationBasedCall("Blighted Bolt", "Go Down, Avoid Feathers");

	private static boolean isArcaneSphere(XivCombatant cbt) {
		// 名前はクライアントの言語で届くので英語と日本語の両方を見る
		String name = cbt.getName();
		return "Arcane Sphere".equals(name) || "立体魔法陣".equals(name);
	}

	private final ModifiableCallout<RawAddCombatantEvent> arcaneSphereSafe = new ModifiableCallout<>("Arcane Sphere: Safe Lane", "{safe}");
	private final ModifiableCallout<RawAddCombatantEvent> arcaneSphereUnknown = new ModifiableCallout<>("Arcane Sphere: Unknown Safe Lane", "Dodge Spheres");
	private final ModifiableCallout<RawAddCombatantEvent> chillingCataclysm = new ModifiableCallout<>("Chilling Cataclysm", "Avoid Star Lines");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> arcaneSphereSq = SqtTemplates.sq(10_000,
			RawAddCombatantEvent.class, e -> isArcaneSphere(e.getEntity()),
			(e1, s) -> {
				if (phase == Phase.ICE) {
					// 詠唱が短すぎるので、玉が出た時点で呼ぶ
					s.updateCall(chillingCataclysm, e1);
					s.waitMs(1_000);
					return;
				}
				if (phase != Phase.STORM) {
					return;
				}
				List<RawAddCombatantEvent> spheres = new ArrayList<>();
				spheres.add(e1);
				spheres.addAll(s.waitEventsUntil(4, RawAddCombatantEvent.class, e -> isArcaneSphere(e.getEntity()),
						BaseEvent.class, e -> e1.getEffectiveTimeSince().toMillis() > 1_000));
				Set<ArcaneLane> safe = EnumSet.allOf(ArcaneLane.class);
				for (RawAddCombatantEvent sphere : spheres) {
					Position pos = sphere.getEntity().getPos();
					if (pos != null) {
						safe.remove(ArcaneLane.forY(pos.y()));
					}
				}
				if (safe.size() == 1) {
					s.setParam("safe", safe.iterator().next());
					s.updateCall(arcaneSphereSafe, e1);
				}
				else {
					s.updateCall(arcaneSphereUnknown, e1);
				}
			});

	// 氷フェーズ

	private final ModifiableCallout<MapEffectEvent> northernCrossFrontRight = new ModifiableCallout<>("Northern Cross (Front/Right Safe)", "Front/Right", 8_000);
	private final ModifiableCallout<MapEffectEvent> northernCrossBackLeft = new ModifiableCallout<>("Northern Cross (Back/Left Safe)", "Back/Left", 8_000);

	@HandleEvents
	public void northernCross(EventContext context, MapEffectEvent event) {
		if (event.getLocation() != 0x02) {
			return;
		}
		// 00020001 は南西半分、00200010 は北東半分を薙ぎ払う
		if (event.getFlags() == 0x0002_0001L) {
			context.accept(northernCrossFrontRight.getModified(event));
		}
		else if (event.getFlags() == 0x0020_0010L) {
			context.accept(northernCrossBackLeft.getModified(event));
		}
	}

	@PlayerHeadmarker(0x00E1)
	private final ModifiableCallout<HeadMarkerEvent> freezingDust = new ModifiableCallout<>("Freezing Dust", "Move", 8_000);

	// 雑魚フェーズ

	@NpcCastCallout(0x9691)
	private final ModifiableCallout<AbilityCastStart> ruinForetold = ModifiableCallout.durationBasedCall("Ruin Foretold", "Raidwide");

	private final ModifiableCallout<AbilityUsedEvent> calamitousCry = new ModifiableCallout<>("Calamitous Cry", "Stack on {event.target}");

	@HandleEvents
	public void calamitousCry(EventContext context, AbilityUsedEvent event) {
		// 87A2 が最初の頭割り、6854 がそれ以降
		if (event.abilityIdMatches(0x87A2, 0x6854) && event.isFirstTarget()) {
			context.accept(calamitousCry.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> tulidisaster1 = new ModifiableCallout<>("Tulidisaster 1", "Raidwide");
	private final ModifiableCallout<AbilityCastStart> tulidisaster2 = new ModifiableCallout<>("Tulidisaster 2", "Raidwide");
	private final ModifiableCallout<AbilityCastStart> tulidisaster3 = new ModifiableCallout<>("Tulidisaster 3", "Raidwide");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> tulidisasterSq = SqtTemplates.sq(30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x8D65),
			(e1, s) -> {
				// 3回の全体攻撃それぞれの約5秒前に呼ぶ
				s.waitMs(5_000);
				s.updateCall(tulidisaster1, e1);
				s.waitMs(8_500);
				s.updateCall(tulidisaster2, e1);
				s.waitMs(8_000);
				s.updateCall(tulidisaster3, e1);
			});

	@NpcCastCallout(0x8D5E)
	private final ModifiableCallout<AbilityCastStart> eruption = new ModifiableCallout<>("Eruption", "Bait Puddles x3", 12_000);

}
