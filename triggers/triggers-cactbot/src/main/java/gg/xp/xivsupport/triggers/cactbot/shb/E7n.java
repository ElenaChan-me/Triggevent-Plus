package gg.xp.xivsupport.triggers.cactbot.shb;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/raid/e7n.ts をもとに移植
@CalloutRepo(name = "Edens Verse Iconoclasm", duty = KnownDuty.EdensVerseIconoclasm)
public class E7n extends AutoChildEventHandler implements FilteredEventHandler {

	// 3回目の色はデバフが切れた後に来るので呼ばない
	private static final int LAST_COLOR_COUNT = 3;

	private XivState state;

	public E7n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.EdensVerseIconoclasm);
	}

	private int colorCount;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		colorCount = 0;
	}

	@NpcCastCallout(0x4C52)
	private final ModifiableCallout<AbilityCastStart> emptyWave = ModifiableCallout.durationBasedCall("Empty Wave", "Raidwide");

	private final ModifiableCallout<TetherEvent> unshadowedStakeOnYou = new ModifiableCallout<>("Unshadowed Stake (on You)", "Buster on You");
	private final ModifiableCallout<TetherEvent> unshadowedStakeOther = new ModifiableCallout<>("Unshadowed Stake (on Other)", "Buster on {target}");

	@HandleEvents
	public void unshadowedStake(EventContext context, TetherEvent event) {
		if (!event.tetherIdMatches(0x25)) {
			return;
		}
		XivCombatant target = event.getTargetMatching(XivCombatant::isPc);
		if (target == null) {
			return;
		}
		if (target.isThePlayer()) {
			context.accept(unshadowedStakeOnYou.getModified(event));
		}
		else {
			context.accept(unshadowedStakeOther.getModified(event, Map.of("target", target)));
		}
	}

	@PlayerStatusCallout(0x8C2)
	private final ModifiableCallout<BuffApplied> leftWithThee = new ModifiableCallout<BuffApplied>("Left With Thee", "Teleporting Left").autoIcon();

	@PlayerStatusCallout(0x8C3)
	private final ModifiableCallout<BuffApplied> rightWithThee = new ModifiableCallout<BuffApplied>("Right With Thee", "Teleporting Right").autoIcon();

	@PlayerStatusCallout(0x8C0)
	private final ModifiableCallout<BuffApplied> forwardWithThee = new ModifiableCallout<BuffApplied>("Forward With Thee", "Teleporting Forward").autoIcon();

	@PlayerStatusCallout(0x8C1)
	private final ModifiableCallout<BuffApplied> backWithThee = new ModifiableCallout<BuffApplied>("Back With Thee", "Teleporting Back").autoIcon();

	@NpcCastCallout(value = 0x4C4C, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> strengthInNumbersDonut = ModifiableCallout.durationBasedCall("Strength in Numbers (Donut)", "Teleport into Donut");

	// 直前にプレイヤーが置く床と紛らわしいので、円範囲も呼ぶ
	@NpcCastCallout(value = 0x4C4D, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> strengthInNumbersCircle = ModifiableCallout.durationBasedCall("Strength in Numbers (Circle)", "Out");

	private final ModifiableCallout<BuffApplied> astralEffect = new ModifiableCallout<BuffApplied>("Astral Effect", "Get Hit by Dark").autoIcon();
	private final ModifiableCallout<BuffApplied> umbralEffect = new ModifiableCallout<BuffApplied>("Umbral Effect", "Get Hit by Light").autoIcon();
	private final RepeatSuppressor astralSupp = new RepeatSuppressor(Duration.ofSeconds(3));
	private final RepeatSuppressor umbralSupp = new RepeatSuppressor(Duration.ofSeconds(3));

	@HandleEvents
	public void colorEffect(EventContext context, BuffApplied event) {
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		ModifiableCallout<BuffApplied> call;
		if (event.buffIdMatches(0x8BE)) {
			if (!astralSupp.check(event)) {
				return;
			}
			call = astralEffect;
		}
		else if (event.buffIdMatches(0x8BF)) {
			if (!umbralSupp.check(event)) {
				return;
			}
			call = umbralEffect;
		}
		else {
			return;
		}
		colorCount++;
		if (colorCount == LAST_COLOR_COUNT) {
			colorCount = 0;
			return;
		}
		context.accept(call.getModified(event));
	}

	// 途中で倒れた場合に数がずれないよう、毎回リセットする
	@HandleEvents
	public void colorCleanup(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x4C39)) {
			colorCount = 0;
		}
	}

}
