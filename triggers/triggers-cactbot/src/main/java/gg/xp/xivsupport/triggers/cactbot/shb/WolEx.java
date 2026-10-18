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
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/trial/wol-ex.ts をもとに移植
@CalloutRepo(name = "The Seat Of Sacrifice Extreme", duty = KnownDuty.TheSeatOfSacrificeExtreme)
public class WolEx extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long DELUGE_MARKER = 0x57;
	private static final long HOLY_MARKER = 0xA1;

	private XivState state;

	public WolEx(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheSeatOfSacrificeExtreme);
	}

	// インビュー・コルスカントとクインタプルキャストで共通の処理内容
	private enum Mech {
		FIRE("Stop"),
		BLIZZARD("Move"),
		HOLY("Stack"),
		STONE("Protean"),
		SWORD_IN("In"),
		SWORD_OUT("Out"),
		FLASH("Look Away");

		private final String label;

		Mech(String label) {
			this.label = label;
		}
	}

	private static final Map<Long, Mech> QUINTUPLE_MARKERS = Map.of(
			0xE2L, Mech.BLIZZARD,
			0xDDL, Mech.HOLY,
			0xDEL, Mech.STONE,
			0xE4L, Mech.FIRE,
			0xDFL, Mech.FLASH);

	private final List<Mech> imbued = new ArrayList<>();
	private final Deque<Mech> quintuplecasts = new ArrayDeque<>();
	private final Map<Mech, RepeatSuppressor> quintupleMarkerSupp = new EnumMap<>(Mech.class);
	private boolean isAddPhase;
	private boolean ultimateSeen;
	private boolean calledSpectral;
	private boolean ninja;
	private boolean deluge;
	private boolean quintuplecasting;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		imbued.clear();
		quintuplecasts.clear();
		quintupleMarkerSupp.clear();
		isAddPhase = false;
		ultimateSeen = false;
		calledSpectral = false;
		ninja = false;
		deluge = false;
		quintuplecasting = false;
	}

	private boolean isTankOrBlu() {
		return state.playerJobMatches(job -> job.isTank() || job == Job.BLU);
	}

	private static String joinMechs(List<Mech> mechs, String delimiter) {
		return mechs.stream().map(mech -> mech.label).collect(Collectors.joining(delimiter));
	}

	private final ModifiableCallout<AbilityUsedEvent> terrorUnleashed = new ModifiableCallout<>("Terror Unleashed (Healer)", "Heal All to Full");
	private final RepeatSuppressor terrorUnleashedSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void terrorUnleashed(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x4F09)
				&& state.playerJobMatches(job -> job.isHealer() || job == Job.BLU)
				&& terrorUnleashedSupp.check(event)) {
			context.accept(terrorUnleashed.getModified(event));
		}
	}

	@NpcCastCallout(0x4F43)
	private final ModifiableCallout<AbilityCastStart> baitConfiteor = ModifiableCallout.durationBasedCall("Confiteor", "Bait Confiteor");

	@NpcCastCallout(0x4F2C)
	private final ModifiableCallout<AbilityCastStart> absoluteStone = ModifiableCallout.durationBasedCall("Absolute Stone III", "Protean");

	@HandleEvents
	public void imbuedCollect(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x4EF3)) {
			imbued.add(Mech.FIRE);
		}
		else if (event.abilityIdMatches(0x4EF4)) {
			imbued.add(Mech.BLIZZARD);
		}
		else if (event.abilityIdMatches(0x4EF5)) {
			imbued.add(Mech.HOLY);
		}
		else if (event.abilityIdMatches(0x4EF6)) {
			imbued.add(Mech.STONE);
		}
	}

	private final ModifiableCallout<AbilityCastStart> imbuedCoruscance = ModifiableCallout.durationBasedCall("Imbued Coruscance", "{mechs}");

	@HandleEvents
	public void imbuedCoruscance(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x4F4A, 0x4F49)) {
			return;
		}
		imbued.add(event.abilityIdMatches(0x4F4A) ? Mech.SWORD_IN : Mech.SWORD_OUT);
		String mechs = joinMechs(imbued, " + ");
		imbued.clear();
		context.accept(imbuedCoruscance.getModified(event, Map.<String, Object>of("mechs", mechs)));
	}

	private final ModifiableCallout<AbilityCastStart> bitterEndOnYou = ModifiableCallout.durationBasedCall("The Bitter End (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> bitterEndSwap = ModifiableCallout.durationBasedCall("The Bitter End (Other Tank)", "Tank Swap");
	private final ModifiableCallout<AbilityCastStart> bitterEnd = ModifiableCallout.durationBasedCall("The Bitter End", "Buster on {event.target}");

	@HandleEvents
	public void bitterEnd(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x4F0A)) {
			return;
		}
		ModifiableCallout<AbilityCastStart> call;
		if (event.getTarget().isThePlayer()) {
			call = bitterEndOnYou;
		}
		else if (state.playerJobMatches(Job::isTank)) {
			call = bitterEndSwap;
		}
		else {
			call = bitterEnd;
		}
		context.accept(call.getModified(event));
	}

	private final ModifiableCallout<AbilityCastStart> summonWyrm = new ModifiableCallout<>("Summon Wyrm", "Avoid Wyrm Dash");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> summonWyrmSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x4F41),
			(e1, s) -> {
				s.waitMs(6_000);
				s.updateCall(summonWyrm, e1);
			});

	private final ModifiableCallout<HeadMarkerEvent> absoluteFlash = new ModifiableCallout<>("Absolute Flash", "Look Away from {event.target}");
	private final RepeatSuppressor absoluteFlashSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void absoluteFlash(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0xB3) && absoluteFlashSupp.check(event) && !event.getTarget().isThePlayer()) {
			context.accept(absoluteFlash.getModified(event));
		}
	}

	@NpcCastCallout(0x4F0B)
	private final ModifiableCallout<AbilityCastStart> elddragonDive = ModifiableCallout.durationBasedCall("Elddragon Dive", "Raidwide");

	// 幻影フェーズ

	@HandleEvents
	public void addPhase(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x5151)) {
			isAddPhase = true;
		}
	}

	@NpcCastCallout(value = {0x5154, 0x5157}, suppressMs = 2_000)
	private final ModifiableCallout<AbilityCastStart> addBusters = ModifiableCallout.durationBasedCall("Fatal Cleave / Blade of Shadow", "Tankbusters");

	private final ModifiableCallout<AbilityCastStart> addInterrupt = ModifiableCallout.durationBasedCall("Berserk / Deep Darkside", "Interrupt {event.source}");
	private final RepeatSuppressor addInterruptSupp = new RepeatSuppressor(Duration.ofSeconds(2));

	@HandleEvents
	public void addInterrupt(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x5156, 0x5158)
				&& state.playerJobMatches(job -> job.caresAboutInterrupt() || job == Job.BLU)
				&& addInterruptSupp.check(event)) {
			context.accept(addInterrupt.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> delugeOfDeath = new ModifiableCallout<>("Deluge of Death", "Flare on You");

	@HandleEvents
	public void delugeOfDeath(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(DELUGE_MARKER) || !event.getTarget().isThePlayer()) {
			return;
		}
		if (ultimateSeen) {
			deluge = true;
		}
		context.accept(delugeOfDeath.getModified(event));
	}

	private final ModifiableCallout<TetherEvent> flareBreath = new ModifiableCallout<>("Flare Breath Tether", "Point Tether Out");
	private final RepeatSuppressor flareBreathSupp = new RepeatSuppressor(Duration.ofSeconds(4));

	@HandleEvents
	public void flareBreath(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x54) && event.eitherTargetMatches(XivCombatant::isThePlayer) && flareBreathSupp.check(event)) {
			context.accept(flareBreath.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> ultimateCrossover = new ModifiableCallout<>("Ultimate Crossover (Tank)", "Tank LB");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> ultimateCrossoverSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x5152),
			(e1, s) -> {
				// 詠唱完了の約1秒前、リミットブレイクが必要になる約7秒前
				s.waitMs(4_000);
				if (isTankOrBlu()) {
					s.updateCall(ultimateCrossover, e1);
				}
				isAddPhase = false;
				ultimateSeen = true;
			});

	// 後半の幻影の予兆

	private final ModifiableCallout<AbilityCastStart> spectralMages = new ModifiableCallout<>("Spectral Black Mage / White Mage", "Black Mage + White Mage");
	private final ModifiableCallout<AbilityCastStart> spectralSummonerWarrior = new ModifiableCallout<>("Spectral Summoner / Warrior", "Summoner + Warrior");
	private final ModifiableCallout<AbilityCastStart> spectralDarkKnightBard = new ModifiableCallout<>("Spectral Dark Knight / Bard", "Dark Knight + Bard");
	private final ModifiableCallout<AbilityCastStart> spectralNinja = new ModifiableCallout<>("Spectral Ninja", "Ninja");

	@HandleEvents
	public void spectralTells(EventContext context, AbilityCastStart event) {
		if (!ultimateSeen || calledSpectral) {
			return;
		}
		ModifiableCallout<AbilityCastStart> call;
		if (event.abilityIdMatches(0x4F37)) {
			call = spectralMages;
		}
		else if (event.abilityIdMatches(0x4EF3, 0x4EF4)) {
			call = spectralSummonerWarrior;
		}
		else if (event.abilityIdMatches(0x4F34, 0x4F35, 0x4F36)) {
			call = spectralDarkKnightBard;
		}
		else if (event.abilityIdMatches(0x4EF5, 0x4EF6)) {
			call = spectralNinja;
			ninja = true;
		}
		else {
			return;
		}
		calledSpectral = true;
		context.accept(call.getModified(event));
	}

	@HandleEvents
	public void spectralCleanup(EventContext context, AbilityUsedEvent event) {
		// 予兆が一通り終わって中央に戻る同期技
		if (event.abilityIdMatches(0x4F45)) {
			calledSpectral = false;
		}
	}

	private final ModifiableCallout<AbilityCastStart> suitonSan = new ModifiableCallout<>("Suiton: San", "Knockback");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> suitonSanSq = SqtTemplates.sq(35_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x4EFD),
			(e1, s) -> {
				s.waitMs(7_000);
				s.updateCall(suitonSan, e1);
				// 火遁とアブソリュートホーリーは同じマーカーなので、忍者の出番が終わるまで区別する
				s.waitMs(23_000);
				ninja = false;
			});

	private final ModifiableCallout<HeadMarkerEvent> katonSan = new ModifiableCallout<>("Katon: San", "Stack");
	private final RepeatSuppressor katonSanSupp = new RepeatSuppressor(Duration.ofSeconds(2));

	@HandleEvents
	public void katonSan(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(HOLY_MARKER) && (ultimateSeen && ninja || isAddPhase) && katonSanSupp.check(event)) {
			context.accept(katonSan.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> absoluteHolyOnYou = new ModifiableCallout<>("Absolute Holy (On You)", "Stack on You");
	private final ModifiableCallout<HeadMarkerEvent> absoluteHoly = new ModifiableCallout<>("Absolute Holy", "Stack on {event.target}");

	@HandleEvents
	public void absoluteHolyOnYou(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(HOLY_MARKER) && !ninja && !isAddPhase && event.getTarget().isThePlayer()) {
			context.accept(absoluteHolyOnYou.getModified(event));
		}
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> absoluteHolySq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(HOLY_MARKER) && !ninja && !isAddPhase && !hm.getTarget().isThePlayer(),
			(e1, s) -> {
				// 頭割りマーカーの直後にデリュージ・オブ・デスのマーカーが来る
				s.waitMs(400);
				if (!deluge) {
					s.updateCall(absoluteHoly, e1);
				}
			});

	@PlayerHeadmarker(0x17)
	private final ModifiableCallout<HeadMarkerEvent> perfectDecimation = new ModifiableCallout<>("Perfect Decimation", "Warrior Cleave on You");

	@PlayerHeadmarker(0x67)
	private final ModifiableCallout<HeadMarkerEvent> brimstoneEarth = new ModifiableCallout<>("Brimstone Earth", "Puddle on You");

	@NpcCastCallout(0x4EF1)
	private final ModifiableCallout<AbilityCastStart> coruscantSaberOut = ModifiableCallout.durationBasedCall("Coruscant Saber (Out)", "Out");

	@NpcCastCallout(0x4EF2)
	private final ModifiableCallout<AbilityCastStart> coruscantSaberIn = ModifiableCallout.durationBasedCall("Coruscant Saber (In)", "In");

	// クインタプルキャスト

	@HandleEvents
	public void quintuplecastStart(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x4EEF)) {
			quintuplecasting = true;
			quintuplecasts.clear();
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> quintupleMarker = new ModifiableCallout<>("Quintuplecast: Marker", "({mech})", 2_000);

	@HandleEvents
	public void quintupleMarker(EventContext context, HeadMarkerEvent event) {
		if (!quintuplecasting) {
			return;
		}
		Mech mech = QUINTUPLE_MARKERS.get(event.getMarkerId());
		if (mech == null) {
			return;
		}
		RepeatSuppressor supp = quintupleMarkerSupp.computeIfAbsent(mech, unused -> new RepeatSuppressor(Duration.ofSeconds(5)));
		if (!supp.check(event)) {
			return;
		}
		quintuplecasts.add(mech);
		context.accept(quintupleMarker.getModified(event, Map.<String, Object>of("mech", mech.label)));
	}

	// 全体の順番は表示のみ、読み上げは個別のコールに任せる
	private final ModifiableCallout<AbilityUsedEvent> quintupleList = new ModifiableCallout<>("Quintuplecast: Order", "", "{order}", ModifiableCallout.expiresIn(Duration.ofMillis(18_500)));
	private final ModifiableCallout<AbilityUsedEvent> quintupleNext = new ModifiableCallout<>("Quintuplecast: Next", "{mech}", 3_000);
	private final RepeatSuppressor quintupleSupp = new RepeatSuppressor(Duration.ofMillis(500));

	@HandleEvents
	public void quintuplecastResolve(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x4EEF, 0x4EF0) || !quintupleSupp.check(event)) {
			return;
		}
		if (event.abilityIdMatches(0x4EEF)) {
			context.accept(quintupleList.getModified(event, Map.<String, Object>of("order", joinMechs(new ArrayList<>(quintuplecasts), " > "))));
		}
		// 最後の 0x4EF0 では次の技が無いので何も言わない
		Mech next = quintuplecasts.poll();
		if (next != null) {
			context.accept(quintupleNext.getModified(event, Map.<String, Object>of("mech", next.label)));
		}
	}

}
