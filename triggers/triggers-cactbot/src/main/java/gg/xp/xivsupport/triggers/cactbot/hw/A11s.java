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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.models.XivCombatant;

import java.util.HashMap;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/raid/a11s.ts をもとに移植
@CalloutRepo(name = "Alexander The Heart Of The Creator Savage", duty = KnownDuty.AlexanderTheHeartOfTheCreatorSavage)
public class A11s extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long LIMIT_CUT_FIRST_MARKER = 0x4F;
	private static final long LIMIT_CUT_LAST_MARKER = 0x56;
	// マーカーが付いてから各番号の処理が来るまでの時間（ミリ秒）
	private static final long[] LIMIT_CUT_DELAY_MS = {8_800, 9_300, 11_000, 11_500, 13_200, 13_700, 15_500, 16_000};
	private static final long LIMIT_CUT_WARNING_MS = 5_000;

	private XivState state;

	public A11s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AlexanderTheHeartOfTheCreatorSavage);
	}

	private final Map<Integer, XivCombatant> limitCutMap = new HashMap<>();

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		limitCutMap.clear();
	}

	// タンクに合わせて逆を言うと混乱しやすいので、常に安置側をコールする
	@NpcCastCallout(0x1A7A)
	private final ModifiableCallout<AbilityCastStart> leftLaserSword = ModifiableCallout.durationBasedCall("Left Laser Sword", "Right");

	@NpcCastCallout(0x1A79)
	private final ModifiableCallout<AbilityCastStart> rightLaserSword = ModifiableCallout.durationBasedCall("Right Laser Sword", "Left");

	@NpcCastCallout(0x1A6C)
	private final ModifiableCallout<AbilityCastStart> opticalSightClock = ModifiableCallout.durationBasedCall("Optical Sight (Clock)", "Clock");

	@NpcCastCallout(0x1A6D)
	private final ModifiableCallout<AbilityCastStart> opticalSightOut = ModifiableCallout.durationBasedCall("Optical Sight (Out)", "Out");

	@NpcCastCallout(0x1A6E)
	private final ModifiableCallout<AbilityCastStart> opticalSightBait = ModifiableCallout.durationBasedCall("Optical Sight (Bait)", "Bait Optical Sight");

	@PlayerHeadmarker(0x5A)
	private final ModifiableCallout<HeadMarkerEvent> superHawkBlaster = new ModifiableCallout<>("Super Hawk Blaster", "Spread");

	@NpcCastCallout(0x1A84)
	private final ModifiableCallout<AbilityCastStart> whirlwind = ModifiableCallout.durationBasedCall("Whirlwind", "Raidwide");

	@NpcCastCallout(0x1A85)
	private final ModifiableCallout<AbilityCastStart> spinCrusher = ModifiableCallout.durationBasedCall("Spin Crusher", "Away from Front");

	private final ModifiableCallout<AbilityCastStart> ga100OnYou = ModifiableCallout.durationBasedCall("GA-100: On You", "GA-100 on You");
	private final ModifiableCallout<AbilityCastStart> ga100Other = ModifiableCallout.durationBasedCall("GA-100: On Someone Else", "GA-100 on {event.target}");

	private final ModifiableCallout<AbilityCastStart> laserXSwordOnYou = ModifiableCallout.durationBasedCall("Laser X Sword: On You", "Shared Tankbuster on You");
	private final ModifiableCallout<AbilityCastStart> laserXSwordOther = ModifiableCallout.durationBasedCall("Laser X Sword: On Someone Else", "Shared Tankbuster on {event.target}");

	@NpcCastCallout(0x1A7F)
	private final ModifiableCallout<AbilityCastStart> propellerWind = ModifiableCallout.durationBasedCall("Propeller Wind", "Hide Behind Tower");

	private final ModifiableCallout<AbilityCastStart> blasstyChargeOnYou = ModifiableCallout.durationBasedCall("Blassty Charge: On You", "Charge on You");
	private final ModifiableCallout<AbilityCastStart> blasstyChargeOther = ModifiableCallout.durationBasedCall("Blassty Charge: On Someone Else", "Charge on {event.target}");

	@HandleEvents
	public void targetedCasts(EventContext context, AbilityCastStart event) {
		boolean onYou = event.getTarget().isThePlayer();
		if (event.abilityIdMatches(0x1A77)) {
			// 頭上マーカーより詠唱開始のほうが3秒ほど早い
			context.accept((onYou ? ga100OnYou : ga100Other).getModified(event));
		}
		else if (event.abilityIdMatches(0x1A7F)) {
			if (onYou) {
				context.accept(laserXSwordOnYou.getModified(event));
			}
			else if (state.playerJobMatches(job -> job.isSupport() || job == Job.BLU)) {
				context.accept(laserXSwordOther.getModified(event));
			}
		}
		else if (event.abilityIdMatches(0x1A83)) {
			// リミットカット中のスーパーブラスティチャージではなく、シールド後の単発のほう
			context.accept((onYou ? blasstyChargeOnYou : blasstyChargeOther).getModified(event));
		}
	}

	private static boolean isLimitCutMarker(HeadMarkerEvent event) {
		long id = event.getMarkerId();
		return id >= LIMIT_CUT_FIRST_MARKER && id <= LIMIT_CUT_LAST_MARKER;
	}

	private static int limitCutIndex(HeadMarkerEvent event) {
		return (int) (event.getMarkerId() - LIMIT_CUT_FIRST_MARKER) + 1;
	}

	@HandleEvents
	public void limitCutCollect(EventContext context, HeadMarkerEvent event) {
		if (isLimitCutMarker(event)) {
			limitCutMap.put(limitCutIndex(event), event.getTarget());
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> limitCutNumber = new ModifiableCallout<>("Limit Cut: Number", "{num}");
	private final ModifiableCallout<HeadMarkerEvent> limitCutOdd = new ModifiableCallout<>("Limit Cut: Odd", "Knockback Cleave, Face Outside", 5_000);
	private final ModifiableCallout<HeadMarkerEvent> limitCutEven = new ModifiableCallout<>("Limit Cut: Even", "Face {player}", 5_000);
	private final ModifiableCallout<HeadMarkerEvent> limitCutEvenUnknown = new ModifiableCallout<>("Limit Cut: Even (Partner Unknown)", "Knockback Charge", 5_000);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> limitCutSq = SqtTemplates.sq(30_000,
			HeadMarkerEvent.class, hm -> isLimitCutMarker(hm) && hm.getTarget().isThePlayer(),
			(e1, s) -> {
				int num = limitCutIndex(e1);
				long delay = LIMIT_CUT_DELAY_MS[num - 1];
				s.setParam("num", num);
				s.updateCall(limitCutNumber, e1);
				s.waitMs(delay - LIMIT_CUT_WARNING_MS);
				if (num % 2 == 1) {
					s.updateCall(limitCutOdd, e1);
				}
				else {
					// 偶数番は直前の奇数番の相手のほうを向いて突進を受ける
					XivCombatant partner = limitCutMap.get(num - 1);
					if (partner == null) {
						s.updateCall(limitCutEvenUnknown, e1);
					}
					else {
						s.setParam("player", partner);
						s.updateCall(limitCutEven, e1);
					}
				}
			});

	@AutoFeed
	private final SequentialTrigger<BaseEvent> limitCutCleanupSq = SqtTemplates.sq(60_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x1A80),
			(e1, s) -> {
				s.waitMs(30_000);
				limitCutMap.clear();
			});

}
