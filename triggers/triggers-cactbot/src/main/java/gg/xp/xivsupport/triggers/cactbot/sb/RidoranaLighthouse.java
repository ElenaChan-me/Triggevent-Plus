package gg.xp.xivsupport.triggers.cactbot.sb;

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
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;
import gg.xp.xivsupport.models.HitPoints;
import gg.xp.xivsupport.models.XivPlayerCharacter;

import java.util.List;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/alliance/ridorana_lighthouse.ts をもとに移植
@CalloutRepo(name = "The Ridorana Lighthouse", duty = KnownDuty.TheRidoranaLighthouse)
public class RidoranaLighthouse extends AutoChildEventHandler implements FilteredEventHandler {

	private static final int MATH_MIN = 1;
	private static final int MATH_MAX = 9;

	// 添字はHPの一の位（1～9）、値は入るべき円の数字（0は円に入らない）
	private static final int[] DIVIDE_BY_FIVE = {-1, 4, 3, 2, 1, 0, 4, 3, 2, 1};
	private static final int[] DIVIDE_BY_FOUR = {-1, 3, 2, 1, 0, 3, 2, 1, 0, 3};
	private static final int[] DIVIDE_BY_THREE = {-1, 2, 1, 0, 2, 1, 0, 2, 1, 0};
	private static final int[] INDIVISIBLE = {-1, 1, 0, 0, 1, 0, 1, 0, 3, 2};

	private XivState state;

	public RidoranaLighthouse(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheRidoranaLighthouse);
	}

	private boolean accelerateSpreadOnMe;
	private int mathBaseValue;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		accelerateSpreadOnMe = false;
		mathBaseValue = 0;
	}

	// ファムフリート

	@NpcCastCallout(0x2C3E)
	private final ModifiableCallout<AbilityCastStart> tidePode = ModifiableCallout.durationBasedCall("Tide Pode", "Buster on {event.target}");

	private final ModifiableCallout<AbilityCastStart> tsunami = new ModifiableCallout<>("Tsunami", "Look for Tsunami");

	// 津波は詠唱後に3回、約12秒間隔で来る
	@AutoFeed
	private final SequentialTrigger<BaseEvent> tsunamiSq = SqtTemplates.sq(40_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x2C50),
			(e1, s) -> {
				s.waitMs(4_500);
				s.updateCall(tsunami, e1);
				s.waitMs(12_000);
				s.updateCall(tsunami, e1);
				s.waitMs(12_000);
				s.updateCall(tsunami, e1);
			});

	@PlayerHeadmarker(0x37)
	private final ModifiableCallout<HeadMarkerEvent> darkCannonade = new ModifiableCallout<>("Dark Cannonade", "Dorito Stack");

	@PlayerHeadmarker(0x8B)
	private final ModifiableCallout<HeadMarkerEvent> brinyCannonade = new ModifiableCallout<>("Briny Cannonade", "Spread");

	// ベリアス

	@NpcCastCallout(0x2CDB)
	private final ModifiableCallout<AbilityCastStart> fire = ModifiableCallout.durationBasedCall("Fire", "Buster on {event.target}");

	@NpcCastCallout(0x2CDE)
	private final ModifiableCallout<AbilityCastStart> timeEruption = ModifiableCallout.durationBasedCall("Time Eruption", "Stand on Slow Clock");

	@PlayerStatusCallout(0x212)
	private final ModifiableCallout<BuffApplied> handOfTime = new ModifiableCallout<BuffApplied>("Hand of Time (Burns)", "Stretch Tether Outside").statusIcon(0x212);

	@NpcCastCallout(0x2CE6)
	private final ModifiableCallout<AbilityCastStart> timeBomb = ModifiableCallout.durationBasedCall("Time Bomb", "Stop Clocks");

	// 労働七号

	@NpcCastCallout({0x2C5A, 0x2C71})
	private final ModifiableCallout<AbilityCastStart> destroy = ModifiableCallout.durationBasedCall("Destroy", "Buster on {event.target}");

	private final ModifiableCallout<HeadMarkerEvent> accelerateSpread = new ModifiableCallout<>("Accelerate (Spread)", "Spread");
	private final ModifiableCallout<HeadMarkerEvent> accelerateStack = new ModifiableCallout<>("Accelerate (Stack)", "Stack on {event.target}");

	@HandleEvents
	public void accelerateMarkers(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x8A) && event.getTarget().isThePlayer()) {
			accelerateSpreadOnMe = true;
			context.accept(accelerateSpread.getModified(event));
		}
		// 散開対象者は頭割りに入れないので頭割りの通知は出さない
		else if (event.markerIdMatches(0x64) && !accelerateSpreadOnMe) {
			context.accept(accelerateStack.getModified(event));
		}
	}

	@HandleEvents
	public void constructCasts(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x2C65)) {
			accelerateSpreadOnMe = false;
		}
		else if (event.abilityIdMatches(0x2C6C)) {
			mathBaseValue = 0;
		}
	}

	// HPペナルティ付与直後はHPが反映されていない場合があるので、少し待ってもう一度確認する
	@AutoFeed
	private final SequentialTrigger<BaseEvent> mathHpCheck = SqtTemplates.sq(5_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0x615) && ba.getTarget().isThePlayer(),
			(e1, s) -> {
				checkMathHp();
				s.waitMs(500);
				checkMathHp();
			});

	private void checkMathHp() {
		if (mathBaseValue != 0) {
			return;
		}
		XivPlayerCharacter player = state.getPlayer();
		if (player == null) {
			return;
		}
		HitPoints hp = player.getHp();
		if (hp == null) {
			return;
		}
		long current = hp.current();
		if (current >= MATH_MIN && current <= MATH_MAX) {
			mathBaseValue = (int) current;
		}
	}

	private final ModifiableCallout<AbilityCastStart> mathStayOut = ModifiableCallout.durationBasedCall("Math: Stay Out", "Stay Out");
	private final ModifiableCallout<AbilityCastStart> mathStandIn1 = ModifiableCallout.durationBasedCall("Math: Stand in 1", "Stand in 1");
	private final ModifiableCallout<AbilityCastStart> mathStandIn2 = ModifiableCallout.durationBasedCall("Math: Stand in 2", "Stand in 2");
	private final ModifiableCallout<AbilityCastStart> mathStandIn3 = ModifiableCallout.durationBasedCall("Math: Stand in 3", "Stand in 3");
	private final ModifiableCallout<AbilityCastStart> mathStandIn4 = ModifiableCallout.durationBasedCall("Math: Stand in 4", "Stand in 4");

	private final List<ModifiableCallout<AbilityCastStart>> mathCalls = List.of(mathStayOut, mathStandIn1, mathStandIn2, mathStandIn3, mathStandIn4);

	@HandleEvents
	public void mathCasts(EventContext context, AbilityCastStart event) {
		final int[] table;
		if (event.abilityIdMatches(0x2CCD)) {
			table = DIVIDE_BY_FIVE;
		}
		else if (event.abilityIdMatches(0x2CCC)) {
			table = DIVIDE_BY_FOUR;
		}
		else if (event.abilityIdMatches(0x2CCA)) {
			table = DIVIDE_BY_THREE;
		}
		else if (event.abilityIdMatches(0x2CCE)) {
			table = INDIVISIBLE;
		}
		else {
			return;
		}
		if (mathBaseValue < MATH_MIN || mathBaseValue > MATH_MAX) {
			return;
		}
		int correct = table[mathBaseValue];
		if (correct < 0 || correct >= mathCalls.size()) {
			return;
		}
		context.accept(mathCalls.get(correct).getModified(event));
	}

	@NpcCastCallout(0x2C61)
	private final ModifiableCallout<AbilityCastStart> pulverize = ModifiableCallout.durationBasedCall("Pulverize", "Out");

	@NpcCastCallout({0x2C5F, 0x2CE9})
	private final ModifiableCallout<AbilityCastStart> dispose = ModifiableCallout.durationBasedCall("Dispose", "Get Behind");

	private final ModifiableCallout<BuffApplied> accelerationBomb = ModifiableCallout.<BuffApplied>durationBasedCall("Acceleration Bomb", "Stop Everything").statusIcon(0x568);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> accelerationBombSq = SqtTemplates.sq(10_000,
			BuffApplied.class, ba -> ba.buffIdMatches(0x568) && ba.getTarget().isThePlayer(),
			(e1, s) -> {
				s.waitMs(2_000);
				s.updateCall(accelerationBomb, e1);
			});

	// ヤズマット

	@NpcCastCallout(0x2D4E)
	private final ModifiableCallout<AbilityCastStart> rakeBuster = ModifiableCallout.durationBasedCall("Rake (Tankbuster)", "Buster on {event.target}");

	@NpcCastCallout(0x2E32)
	private final ModifiableCallout<AbilityCastStart> rakeCharge = ModifiableCallout.durationBasedCall("Rake (Charge)", "Away from Front");

	@NpcCastCallout(0x2C31)
	private final ModifiableCallout<AbilityCastStart> whiteBreath = ModifiableCallout.durationBasedCall("White Breath", "Get Under");

	@PlayerStatusCallout(0x60F)
	private final ModifiableCallout<BuffApplied> magneticNegative = new ModifiableCallout<BuffApplied>("Magnetic Negative", "Move to Positive").statusIcon(0x60F);

	@PlayerStatusCallout(0x60E)
	private final ModifiableCallout<BuffApplied> magneticPositive = new ModifiableCallout<BuffApplied>("Magnetic Positive", "Move to Negative").statusIcon(0x60E);

}
