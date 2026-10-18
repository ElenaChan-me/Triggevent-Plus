package gg.xp.xivsupport.triggers.cactbot.ew;

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
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/trial/hydaelyn-ex.ts をもとに移植
@CalloutRepo(name = "The Minstrels Ballad Hydaelyns Call", duty = KnownDuty.TheMinstrelsBalladHydaelynsCall)
public class HydaelynEx extends AutoChildEventHandler implements FilteredEventHandler {

	private static final double CENTER_Y = 100;
	// アンセリオン・ハイエストホーリーの予兆に使われるステータスとそのカウント値
	private static final long MARKER_STATUS = 0x8E1;
	private static final long ANTHELION_COUNT = 0x1B5;
	private static final long HIGHEST_HOLY_COUNT = 0x1B4;

	private enum Crystallize {
		SPREAD("Spread"),
		GROUPS("Healer Groups"),
		STACK("Party Stack");

		private final String label;

		Crystallize(String label) {
			this.label = label;
		}
	}

	private XivState state;

	public HydaelynEx(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheMinstrelsBalladHydaelynsCall);
	}

	private @Nullable Crystallize crystallize;
	private boolean parhelion;
	private final Set<Long> brightSpectrumTargets = new HashSet<>();

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		crystallize = null;
		parhelion = false;
		brightSpectrumTargets.clear();
	}

	// 記憶しているクリスタライズがあれば後に続けて呼び、使ったら忘れる
	private <X> void comboCall(EventContext context, X event, ModifiableCallout<X> plain, ModifiableCallout<X> combo) {
		Crystallize stored = crystallize;
		crystallize = null;
		if (stored == null) {
			context.accept(plain.getModified(event));
		}
		else {
			context.accept(combo.getModified(event, Map.of("crystal", stored.label)));
		}
	}

	@NpcCastCallout(0x65C1)
	private final ModifiableCallout<AbilityCastStart> herosRadiance = ModifiableCallout.durationBasedCall("Heros's Radiance", "Raidwide");

	@NpcCastCallout(0x65C2)
	private final ModifiableCallout<AbilityCastStart> magossRadiance = ModifiableCallout.durationBasedCall("Magos's Radiance", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> shiningSaber = ModifiableCallout.durationBasedCall("Shining Saber", "Stack");
	private final ModifiableCallout<AbilityCastStart> shiningSaberCombo = new ModifiableCallout<>("Shining Saber (with Crystallize)", "Stack, then {crystal}", 7_000);

	private final ModifiableCallout<AbilityCastStart> aureole = ModifiableCallout.durationBasedCall("Aureole", "Sides");
	private final ModifiableCallout<AbilityCastStart> aureoleCombo = ModifiableCallout.durationBasedCall("Aureole (with Crystallize)", "Sides, then {crystal}");

	private final ModifiableCallout<AbilityCastStart> lateralAureole = ModifiableCallout.durationBasedCall("Lateral Aureole", "Front/Back");
	private final ModifiableCallout<AbilityCastStart> lateralAureoleCombo = ModifiableCallout.durationBasedCall("Lateral Aureole (with Crystallize)", "Front/Back, then {crystal}");

	private final ModifiableCallout<AbilityCastStart> parhelicCircle = new ModifiableCallout<>("Parhelic Circle", "Avoid Line Ends, then Spread", 9_000);

	@HandleEvents
	public void casts(EventContext context, AbilityCastStart event) {
		switch ((int) event.getAbility().getId()) {
			case 0x65B0 -> parhelion = true;
			case 0x68C8 -> comboCall(context, event, shiningSaber, shiningSaberCombo);
			case 0x6C91, 0x6F11 -> comboCall(context, event, aureole, aureoleCombo);
			case 0x65C5, 0x6F13 -> comboCall(context, event, lateralAureole, lateralAureoleCombo);
			case 0x65AC -> {
				// ここは常に散開になる
				crystallize = null;
				context.accept(parhelicCircle.getModified(event));
			}
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> crystallizeWater = new ModifiableCallout<>("Crystallize Water", "Crystallize: Healer Groups");
	private final ModifiableCallout<AbilityUsedEvent> crystallizeIce = new ModifiableCallout<>("Crystallize Ice", "Crystallize: Spread");
	private final ModifiableCallout<AbilityUsedEvent> crystallizeStone = new ModifiableCallout<>("Crystallize Stone", "Crystallize: Party Stack");

	@HandleEvents
	public void crystallizeUsed(EventContext context, AbilityUsedEvent event) {
		if (!event.isFirstTarget()) {
			return;
		}
		switch ((int) event.getAbility().getId()) {
			case 0x659A, 0x6ED5 -> {
				crystallize = Crystallize.GROUPS;
				context.accept(crystallizeWater.getModified(event));
			}
			case 0x659C, 0x659D -> {
				crystallize = Crystallize.SPREAD;
				context.accept(crystallizeIce.getModified(event));
			}
			case 0x659B, 0x659E -> {
				crystallize = Crystallize.STACK;
				context.accept(crystallizeStone.getModified(event));
			}
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> crystallizeWaterParhelion = new ModifiableCallout<>("Crystallize Water (Parhelion)", "Healer Groups");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> crystallizeWaterParhelionSq = SqtTemplates.sq(10_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x659A, 0x6ED5) && aue.isFirstTarget() && parhelion,
			(e1, s) -> {
				// パーヘリオン中は間に別のギミックを挟まず、発動まで10秒ほどある
				s.waitMs(5_000);
				crystallize = null;
				parhelion = false;
				s.updateCall(crystallizeWaterParhelion, e1);
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
				// すぐに呼ぶとクリスタライズのコールと近すぎるので、マーカーが見える頃まで待つ
				s.waitMs(2_500);
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

	@NpcCastCallout(0x65C0)
	private final ModifiableCallout<AbilityCastStart> mousasScorn = ModifiableCallout.durationBasedCall("Mousa's Scorn", "Shared Buster on {event.target}");

	private final ModifiableCallout<AbilityUsedEvent> crystalsFirst = new ModifiableCallout<>("Crystal of Light", "{dir} Crystals First");
	private final ModifiableCallout<AbilityUsedEvent> echoesFirst = new ModifiableCallout<>("Crystal of Light (Tank)", "Move Echoes {dir} First");
	private final RepeatSuppressor crystalOfLightSupp = new RepeatSuppressor(Duration.ofSeconds(60));

	@HandleEvents
	public void crystalOfLight(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x65BE) || !crystalOfLightSupp.check(event)) {
			return;
		}
		XivCombatant source = event.getSource();
		Position pos = source.getPos();
		if (pos == null) {
			pos = state.getLatestCombatantData(source).getPos();
		}
		if (pos == null) {
			return;
		}
		// 最初に攻撃してきたクリスタルの側から壊す。タンクは分身を反対側へ動かす
		boolean southFirst = pos.getY() > CENTER_Y;
		if (state.playerJobMatches(Job::isTank)) {
			context.accept(echoesFirst.getModified(event, Map.of("dir", southFirst ? ArenaSector.NORTH : ArenaSector.SOUTH)));
		}
		else {
			context.accept(crystalsFirst.getModified(event, Map.of("dir", southFirst ? ArenaSector.SOUTH : ArenaSector.NORTH)));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> exodus = new ModifiableCallout<>("Exodus", "Raidwide");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> exodusSq = SqtTemplates.sq(10_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x6B55) && aue.isFirstTarget(),
			(e1, s) -> {
				// 詠唱なしで、ここから全体攻撃まで約15秒ある
				s.waitMs(5_000);
				s.updateCall(exodus, e1);
			});

	@NpcCastCallout(0x65A5)
	private final ModifiableCallout<AbilityCastStart> halo = ModifiableCallout.durationBasedCall("Halo", "Raidwide");

	@NpcCastCallout(0x6B54)
	private final ModifiableCallout<AbilityCastStart> radiantHalo = ModifiableCallout.durationBasedCall("Radiant Halo", "Raidwide");

	@NpcCastCallout(0x65BF)
	private final ModifiableCallout<AbilityCastStart> herosSundering = ModifiableCallout.durationBasedCall("Heros's Sundering", "Tank Cleave");

	@NpcCastCallout(0x6669)
	private final ModifiableCallout<AbilityCastStart> infralateralArc = ModifiableCallout.durationBasedCall("Infralateral Arc", "Role Positions");

	@NpcCastCallout(0x65A8)
	private final ModifiableCallout<AbilityCastStart> herosGlory = ModifiableCallout.durationBasedCall("Heros's Glory", "Get Behind");

	@NpcCastCallout(0x65B5)
	private final ModifiableCallout<AbilityCastStart> echoes = ModifiableCallout.durationBasedCall("Echoes", "Stack 5x");

	private final ModifiableCallout<AbilityCastStart> brightSpectrum = ModifiableCallout.durationBasedCall("Bright Spectrum", "Spread");
	private final ModifiableCallout<AbilityCastStart> dichroicSpectrum = ModifiableCallout.durationBasedCall("Dichroic Spectrum", "Shared Buster on {event.target}");

	@HandleEvents
	public void spectrum(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x65B9)) {
			brightSpectrumTargets.add(event.getTarget().getId());
			if (event.getTarget().isThePlayer()) {
				context.accept(brightSpectrum.getModified(event));
			}
		}
		else if (event.abilityIdMatches(0x65B8)) {
			// 散開の対象者は頭割りに入れないので呼ばない
			XivCombatant player = state.getPlayer();
			boolean spreading = player != null && brightSpectrumTargets.contains(player.getId());
			brightSpectrumTargets.clear();
			if (!spreading && (event.getTarget().isThePlayer() || state.playerJobMatches(Job::isTank))) {
				context.accept(dichroicSpectrum.getModified(event));
			}
		}
	}

}
