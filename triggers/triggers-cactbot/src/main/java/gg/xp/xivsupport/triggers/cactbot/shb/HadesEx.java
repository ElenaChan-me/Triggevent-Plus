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
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/trial/hades-ex.ts をもとに移植
@CalloutRepo(name = "The Minstrels Ballad Hadess Elegy", duty = KnownDuty.TheMinstrelsBalladHadessElegy)
public class HadesEx extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public HadesEx(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheMinstrelsBalladHadessElegy);
	}

	private boolean waterDarkMarker;
	private boolean freeze;
	private boolean flame;
	private int sphereCount;
	private boolean netherBlast;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		waterDarkMarker = false;
		freeze = false;
		flame = false;
		sphereCount = 0;
		netherBlast = false;
	}

	private boolean isTankOrBlu() {
		return state.playerJobMatches(job -> job.isTank() || job == Job.BLU);
	}

	// ハーデス 前半

	private final ModifiableCallout<AbilityCastStart> shadowSpread = ModifiableCallout.durationBasedCall("Shadow Spread", "Protean");
	private final ModifiableCallout<AbilityCastStart> shadowSpreadMove = new ModifiableCallout<>("Shadow Spread: Move", "Move Away");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> shadowSpreadSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x47A8),
			(e1, s) -> {
				s.updateCall(shadowSpread, e1);
				// 1回目の着弾後に2回目の扇を避ける
				s.waitMs(5_500);
				s.updateCall(shadowSpreadMove, e1);
			});

	private final ModifiableCallout<AbilityCastStart> ravenousAssaultOnYou = ModifiableCallout.durationBasedCall("Ravenous Assault (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> ravenousAssault = ModifiableCallout.durationBasedCall("Ravenous Assault", "Buster on {event.target}");

	@HandleEvents
	public void ravenousAssault(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x47A6)) {
			context.accept((event.getTarget().isThePlayer() ? ravenousAssaultOnYou : ravenousAssault).getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> badFaithLeft = ModifiableCallout.durationBasedCall("Bad Faith (Left First)", "Left");
	private final ModifiableCallout<AbilityCastStart> badFaithThenRight = new ModifiableCallout<>("Bad Faith (Left First): Second", "Then Right");
	private final ModifiableCallout<AbilityCastStart> badFaithRight = ModifiableCallout.durationBasedCall("Bad Faith (Right First)", "Right");
	private final ModifiableCallout<AbilityCastStart> badFaithThenLeft = new ModifiableCallout<>("Bad Faith (Right First): Second", "Then Left");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> badFaithSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x47AB, 0x47AC),
			(e1, s) -> {
				boolean leftFirst = e1.abilityIdMatches(0x47AB);
				s.updateCall(leftFirst ? badFaithLeft : badFaithRight, e1);
				s.waitMs(5_000);
				s.updateCall(leftFirst ? badFaithThenRight : badFaithThenLeft, e1);
			});

	// 古の影たち

	private final ModifiableCallout<AbilityCastStart> quakeIII = new ModifiableCallout<>("Quake III", "Raidwide");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> quakeIIISq = SqtTemplates.sq(30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x47B8),
			(e1, s) -> {
				// 詠唱がとても長いので、着弾直前に知らせる
				s.waitMs(25_000);
				s.updateCall(quakeIII, e1);
			});

	private final ModifiableCallout<TetherEvent> darkIITether = new ModifiableCallout<>("Dark II Tether", "Point Tether Out");

	@HandleEvents
	public void darkIITether(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x11) && event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			context.accept(darkIITether.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> ancientWaterOnYou = new ModifiableCallout<>("Ancient Water III (On You)", "Healer Stacks");
	private final ModifiableCallout<HeadMarkerEvent> ancientDarkness = new ModifiableCallout<>("Ancient Darkness", "Tank Spread");

	@HandleEvents
	public void ancientMarkers(EventContext context, HeadMarkerEvent event) {
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		if (event.markerIdMatches(0x3E)) {
			waterDarkMarker = true;
			context.accept(ancientWaterOnYou.getModified(event));
		}
		else if (event.markerIdMatches(0x60)) {
			waterDarkMarker = true;
			context.accept(ancientDarkness.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> ancientWaterUnmarked = new ModifiableCallout<>("Ancient Water III (Unmarked)", "Healer Stacks");
	private final RepeatSuppressor ancientWaterSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> ancientWaterUnmarkedSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(0x30, 0x60) && ancientWaterSupp.check(hm),
			(e1, s) -> {
				// 自分にマーカーが付いたかどうかは全員分が届いてから判断する
				s.waitMs(500);
				if (!waterDarkMarker) {
					s.updateCall(ancientWaterUnmarked, e1);
				}
			});

	private final ModifiableCallout<TetherEvent> shadesTooClose = new ModifiableCallout<>("Shades Too Close", "Move Shades Apart");
	private final RepeatSuppressor shadesTooCloseSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	@HandleEvents
	public void shadesTooClose(EventContext context, TetherEvent event) {
		// ボス同士を結ぶ線だけを対象にする
		if (event.tetherIdMatches(0x0E)
				&& !event.eitherTargetMatches(XivCombatant::isPc)
				&& isTankOrBlu()
				&& shadesTooCloseSupp.check(event)) {
			context.accept(shadesTooClose.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> spheres = new ModifiableCallout<>("Spheres", "Tank Swap Soon");

	@HandleEvents
	public void spheres(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x47BD) || !isTankOrBlu()) {
			return;
		}
		// 1回目はまだスイッチ不要
		if (sphereCount > 0) {
			context.accept(spheres.getModified(event));
		}
		sphereCount++;
	}

	@NpcCastCallout(0x47BF)
	private final ModifiableCallout<AbilityCastStart> annihilation = ModifiableCallout.durationBasedCall("Annihilation", "Raidwide");

	@PlayerStatusCallout(0x850)
	private final ModifiableCallout<BuffApplied> burningBrand = new ModifiableCallout<BuffApplied>("Burning Brand", "Attack Igeyorhm").autoIcon();

	@PlayerStatusCallout(0x851)
	private final ModifiableCallout<BuffApplied> freezingBrand = new ModifiableCallout<BuffApplied>("Freezing Brand", "Attack Lahabrea").autoIcon();

	@NpcCastCallout(value = {0x47C2, 0x47C3}, onYou = true)
	private final ModifiableCallout<AbilityCastStart> fireBlizzardIVOnYou = ModifiableCallout.durationBasedCall("Fire IV / Blizzard IV (On You)", "Buster on You");

	@NpcCastCallout(value = {0x47C2, 0x47C3}, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> fireBlizzardIV = ModifiableCallout.durationBasedCall("Fire IV / Blizzard IV", "Tankbusters");

	private final ModifiableCallout<BuffApplied> doom = new ModifiableCallout<BuffApplied>("Doom (Healer)", "Heal T/H to Full").autoIcon();
	private final RepeatSuppressor doomSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void doom(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x6E9)
				&& state.playerJobMatches(job -> job.isHealer() || job == Job.BLU)
				&& doomSupp.check(event)) {
			context.accept(doom.getModified(event));
		}
	}

	private final ModifiableCallout<BuffApplied> shriek = new ModifiableCallout<BuffApplied>("Shriek", "Look Away").autoIcon();
	private final RepeatSuppressor shriekSupp = new RepeatSuppressor(Duration.ofSeconds(2));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> shriekSq = SqtTemplates.callWhenDurationIs(
			BuffApplied.class, ba -> ba.buffIdMatches(0x1C4) && shriekSupp.check(ba),
			shriek, Duration.ofSeconds(2));

	@PlayerStatusCallout(0x566)
	private final ModifiableCallout<BuffApplied> beyondDeath = new ModifiableCallout<BuffApplied>("Beyond Death", "Get Killed", 8_000).autoIcon();

	private final ModifiableCallout<BuffApplied> ancientCircle = new ModifiableCallout<BuffApplied>("Ancient Circle", "Donut on You").autoIcon();

	@AutoFeed
	private final SequentialTrigger<BaseEvent> ancientCircleSq = SqtTemplates.callWhenDurationIs(
			BuffApplied.class, ba -> ba.buffIdMatches(0x83E) && ba.getTarget().isThePlayer(),
			ancientCircle, Duration.ofSeconds(5));

	private final ModifiableCallout<BuffApplied> forkedLightning = new ModifiableCallout<BuffApplied>("Forked Lightning", "Stay Out").autoIcon();

	@AutoFeed
	private final SequentialTrigger<BaseEvent> forkedLightningSq = SqtTemplates.callWhenDurationIs(
			BuffApplied.class, ba -> ba.buffIdMatches(0x24B) && ba.getTarget().isThePlayer(),
			forkedLightning, Duration.ofSeconds(2));

	// アシエン・プライムの影

	private final ModifiableCallout<AbilityCastStart> blight = new ModifiableCallout<>("Blight", "Raidwide with Bleed");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> blightSq = SqtTemplates.sq(20_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x47CC),
			(e1, s) -> {
				s.waitMs(12_000);
				s.updateCall(blight, e1);
			});

	private final ModifiableCallout<AbilityCastStart> heightOfChaosOnYou = ModifiableCallout.durationBasedCall("Height of Chaos (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> heightOfChaosHealer = ModifiableCallout.durationBasedCall("Height of Chaos (Healer)", "Buster on {event.target}");
	private final ModifiableCallout<AbilityCastStart> heightOfChaos = ModifiableCallout.durationBasedCall("Height of Chaos", "Away from {event.target}");

	@HandleEvents
	public void heightOfChaos(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x47D1)) {
			return;
		}
		ModifiableCallout<AbilityCastStart> call;
		if (event.getTarget().isThePlayer()) {
			call = heightOfChaosOnYou;
		}
		else if (state.playerJobMatches(Job::isHealer)) {
			call = heightOfChaosHealer;
		}
		else {
			call = heightOfChaos;
		}
		context.accept(call.getModified(event));
	}

	@NpcCastCallout(value = 0x47CD, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> megiddoFlame = ModifiableCallout.durationBasedCall("Megiddo Flame", "Healer Stacks");

	@NpcCastCallout(0x47D0)
	private final ModifiableCallout<AbilityCastStart> shadowFlare = ModifiableCallout.durationBasedCall("Shadow Flare", "Raidwide");

	@PlayerHeadmarker(0x78)
	private final ModifiableCallout<HeadMarkerEvent> captivity = new ModifiableCallout<>("Captivity", "Out");

	// ハーデス 後半

	private final ModifiableCallout<HeadMarkerEvent> darkFlame = new ModifiableCallout<>("Dark Flame", "Knockback + Stack on You");
	private final ModifiableCallout<HeadMarkerEvent> darkFreeze = new ModifiableCallout<>("Dark Freeze", "Knockback + Ice on You");
	private final ModifiableCallout<HeadMarkerEvent> netherBlastOnYou = new ModifiableCallout<>("Nether Blast", "Puddles on You");

	@HandleEvents
	public void darkMarkers(EventContext context, HeadMarkerEvent event) {
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		if (event.markerIdMatches(0x64)) {
			flame = true;
			context.accept(darkFlame.getModified(event));
		}
		else if (event.markerIdMatches(0xC1)) {
			freeze = true;
			context.accept(darkFreeze.getModified(event));
		}
		else if (event.markerIdMatches(0x8B)) {
			netherBlast = true;
			context.accept(netherBlastOnYou.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> wailOfTheLost = ModifiableCallout.durationBasedCall("Wail of the Lost", "Knockback + Stack With Partner");

	@HandleEvents
	public void wailOfTheLost(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x47E1) && !flame && !freeze) {
			context.accept(wailOfTheLost.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> bident = ModifiableCallout.durationBasedCall("Bident", "Healer Stacks");

	@HandleEvents
	public void bident(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x47E3) && !netherBlast) {
			context.accept(bident.getModified(event));
		}
	}

	@NpcCastCallout(0x47EA)
	private final ModifiableCallout<AbilityCastStart> shadowStream = ModifiableCallout.durationBasedCall("Shadow Stream", "Sides");

	@NpcCastCallout(0x47EB)
	private final ModifiableCallout<AbilityCastStart> polydegmonsPurgation = ModifiableCallout.durationBasedCall("Polydegmon's Purgation", "Front and Center");

	@NpcCastCallout(value = 0x47F1, suppressMs = 10_000)
	private final ModifiableCallout<AbilityCastStart> darkCurrent = new ModifiableCallout<>("Dark Current", "Exoflares", 12_000);

	@NpcCastCallout(0x47F3)
	private final ModifiableCallout<AbilityCastStart> gigantomachy = ModifiableCallout.durationBasedCall("Gigantomachy", "Raidwide");

	@NpcCastCallout(0x47F4)
	private final ModifiableCallout<AbilityCastStart> quadrastrike = ModifiableCallout.durationBasedCall("Quadrastrike", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> quadrastrikeTowers = ModifiableCallout.durationBasedCall("Quadrastrike: Towers (Tank)", "Get Towers");
	private final ModifiableCallout<AbilityCastStart> quadrastrikeHealer = ModifiableCallout.durationBasedCall("Quadrastrike: Towers (Healer)", "Tankbusters");
	private final RepeatSuppressor quadrastrikeSupp = new RepeatSuppressor(Duration.ofSeconds(2));

	@HandleEvents
	public void quadrastrikeTowers(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x47F6)) {
			return;
		}
		if (isTankOrBlu()) {
			if (quadrastrikeSupp.check(event)) {
				context.accept(quadrastrikeTowers.getModified(event));
			}
		}
		else if (state.playerJobMatches(Job::isHealer) && quadrastrikeSupp.check(event)) {
			context.accept(quadrastrikeHealer.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> quadrastrikeBleed = new ModifiableCallout<>("Quadrastrike: After Towers", "Raidwide with Bleed");
	private final RepeatSuppressor quadrastrikeBleedSupp = new RepeatSuppressor(Duration.ofSeconds(2));

	@AutoFeed
	private final SequentialTrigger<BaseEvent> quadrastrikeBleedSq = SqtTemplates.sq(5_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x47F6) && quadrastrikeBleedSupp.check(aue),
			(e1, s) -> {
				// タンクが塔を踏んだ後に全体攻撃が来る
				s.waitMs(2_000);
				s.updateCall(quadrastrikeBleed, e1);
			});

	@NpcCastCallout(0x47F9)
	private final ModifiableCallout<AbilityCastStart> enrage = ModifiableCallout.durationBasedCall("Gigantomachy (Enrage)", "Enrage");

}
