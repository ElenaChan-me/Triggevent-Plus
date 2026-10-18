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
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/trial/hydaelyn.ts をもとに移植
@CalloutRepo(name = "The Mothercrystal", duty = KnownDuty.TheMothercrystal)
public class Hydaelyn extends AutoChildEventHandler implements FilteredEventHandler {

	// アンセリオン・ハイエストホーリーの予兆に使われるステータスとそのカウント値
	private static final long MARKER_STATUS = 0x8E1;
	private static final long ANTHELION_COUNT = 0x1B5;
	private static final long HIGHEST_HOLY_COUNT = 0x1B4;
	// ドーンマントルの詠唱 4.9 秒にマーカー表示までの 2.5 秒を足した分
	private static final int DAWN_MANTLE_DELAY_MS = 7_400;
	private static final int MARKER_DELAY_MS = 2_500;

	private enum Crystallize {
		SPREAD("Spread"),
		STACK("Party Stack");

		private final String label;

		Crystallize(String label) {
			this.label = label;
		}
	}

	private XivState state;

	public Hydaelyn(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheMothercrystal);
	}

	private @Nullable Crystallize crystallize;
	private boolean isEquinox;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		crystallize = null;
		isEquinox = false;
	}

	@NpcCastCallout(0x65D7)
	private final ModifiableCallout<AbilityCastStart> herosRadiance = ModifiableCallout.durationBasedCall("Heros's Radiance", "Raidwide");

	@NpcCastCallout(0x65D8)
	private final ModifiableCallout<AbilityCastStart> magossRadiance = ModifiableCallout.durationBasedCall("Magos's Radiance", "Raidwide");

	private final ModifiableCallout<AbilityUsedEvent> crystallizeIce = new ModifiableCallout<>("Crystallize Ice", "Crystallize: Spread");
	private final ModifiableCallout<AbilityUsedEvent> crystallizeStone = new ModifiableCallout<>("Crystallize Stone", "Crystallize: Party Stack");

	@HandleEvents
	public void crystallizeUsed(EventContext context, AbilityUsedEvent event) {
		if (!event.isFirstTarget()) {
			return;
		}
		if (event.abilityIdMatches(0x659C)) {
			crystallize = Crystallize.SPREAD;
			context.accept(crystallizeIce.getModified(event));
		}
		else if (event.abilityIdMatches(0x659E)) {
			crystallize = Crystallize.STACK;
			context.accept(crystallizeStone.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> equinox = new ModifiableCallout<>("Equinox", "Intercards", 3_500);
	private final ModifiableCallout<AbilityCastStart> equinoxCombo = new ModifiableCallout<>("Equinox (with Crystallize)", "Intercards, then {crystal}", 6_500);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> equinoxSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x6C0C),
			(e1, s) -> {
				// 零式と違いエクイノックスには予兆ステータスが無いので、他のマーカーが来なければエクイノックスとみなす
				isEquinox = true;
				s.waitMs(DAWN_MANTLE_DELAY_MS);
				if (!isEquinox) {
					return;
				}
				Crystallize stored = crystallize;
				crystallize = null;
				if (stored == null) {
					s.updateCall(equinox, e1);
				}
				else {
					s.setParam("crystal", stored.label);
					s.updateCall(equinoxCombo, e1);
				}
			});

	private final ModifiableCallout<BuffApplied> anthelion = new ModifiableCallout<>("Anthelion", "In", 3_500);
	private final ModifiableCallout<BuffApplied> anthelionCombo = new ModifiableCallout<>("Anthelion (with Crystallize)", "In, then {crystal}", 6_500);
	private final ModifiableCallout<BuffApplied> highestHoly = new ModifiableCallout<>("Highest Holy", "Out", 3_500);
	private final ModifiableCallout<BuffApplied> highestHolyCombo = new ModifiableCallout<>("Highest Holy (with Crystallize)", "Out, then {crystal}", 6_500);
	private final RepeatSuppressor markerSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> markerSq = SqtTemplates.sq(10_000,
			BuffApplied.class, ba -> ba.buffIdMatches(MARKER_STATUS)
					&& (ba.getRawStacks() == ANTHELION_COUNT || ba.getRawStacks() == HIGHEST_HOLY_COUNT)
					&& markerSupp.check(ba),
			(e1, s) -> {
				isEquinox = false;
				// すぐに呼ぶとクリスタライズのコールと近すぎるので、マーカーが見える頃まで待つ
				s.waitMs(MARKER_DELAY_MS);
				boolean in = e1.getRawStacks() == ANTHELION_COUNT;
				Crystallize stored = crystallize;
				crystallize = null;
				if (stored == null) {
					s.updateCall(in ? anthelion : highestHoly, e1);
				}
				else {
					s.setParam("crystal", stored.label);
					s.updateCall(in ? anthelionCombo : highestHolyCombo, e1);
				}
			});

	@NpcCastCallout(0x65D6)
	private final ModifiableCallout<AbilityCastStart> mousasScorn = ModifiableCallout.durationBasedCall("Mousa's Scorn", "Shared Buster on {event.target}");

	private final ModifiableCallout<AbilityUsedEvent> exodus = new ModifiableCallout<>("Exodus", "Raidwide");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> exodusSq = SqtTemplates.sq(10_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x65BB) && aue.isFirstTarget(),
			(e1, s) -> {
				// 詠唱なしで約15秒後に全体攻撃が来るので、早すぎない程度に待つ
				s.waitMs(5_000);
				s.updateCall(exodus, e1);
			});

	@NpcCastCallout(0x65D0)
	private final ModifiableCallout<AbilityCastStart> radiantHalo = ModifiableCallout.durationBasedCall("Radiant Halo", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> herosSunderingOnYou = ModifiableCallout.durationBasedCall("Heros's Sundering (On You)", "Tank Cleave on You");
	private final ModifiableCallout<AbilityCastStart> herosSunderingAvoid = ModifiableCallout.durationBasedCall("Heros's Sundering (Not on You)", "Avoid Tank Cleave");

	@HandleEvents
	public void herosSundering(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x65D5)) {
			context.accept((event.getTarget().isThePlayer() ? herosSunderingOnYou : herosSunderingAvoid).getModified(event));
		}
	}

	@NpcCastCallout(0x6C59)
	private final ModifiableCallout<AbilityCastStart> echoStone = ModifiableCallout.durationBasedCall("Crystalline Stone III (Echo)", "Party Stack");

	@NpcCastCallout(value = 0x6C5A, onYou = true)
	private final ModifiableCallout<AbilityCastStart> echoBlizzard = ModifiableCallout.durationBasedCall("Crystalline Blizzard III (Echo)", "Spread");

	private final ModifiableCallout<AbilityCastStart> parhelicCircle = new ModifiableCallout<>("Parhelic Circle", "Avoid Line Ends", 9_000);

	@HandleEvents
	public void parhelicCircle(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x65AC)) {
			crystallize = null;
			context.accept(parhelicCircle.getModified(event));
		}
	}

	@NpcCastCallout({0x65B5, 0x65B6, 0x65B7})
	private final ModifiableCallout<AbilityCastStart> echoes = ModifiableCallout.durationBasedCall("Echoes", "Stack 5x");

}
