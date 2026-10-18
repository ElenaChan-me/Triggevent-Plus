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
import gg.xp.xivsupport.events.actlines.events.BuffRemoved;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/trial/sephirot-un.ts をもとに移植
@CalloutRepo(name = "Containment Bay S1 T7 Unreal", duty = KnownDuty.ContainmentBayS1T7Unreal)
public class SephirotUn extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long FORCE_AGAINST_MIGHT = 0x3ED;
	private static final long FORCE_AGAINST_MAGIC = 0x3EE;
	private static final long AUTO_ATTACK = 0x368;

	private XivState state;

	public SephirotUn(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.ContainmentBayS1T7Unreal);
	}

	// フィエンディッシュレイジで頭割りに入るべきかを決めるため、オートアタックの対象を覚えておく
	private boolean playerIsMainTank;
	// 0 は色デバフなし
	private long force;
	private boolean shakerOnPlayer;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		playerIsMainTank = false;
		force = 0;
		shakerOnPlayer = false;
	}

	@HandleEvents
	public void mainTank(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(AUTO_ATTACK) && !event.getSource().isPc()) {
			playerIsMainTank = event.getTarget().isThePlayer();
		}
	}

	@NpcCastCallout(0x7694)
	private final ModifiableCallout<AbilityCastStart> chesed = ModifiableCallout.durationBasedCall("Chesed", "Buster on {event.target}");

	@NpcCastCallout(0x7696)
	private final ModifiableCallout<AbilityCastStart> ein = ModifiableCallout.durationBasedCall("Ein", "Get Behind");

	@PlayerHeadmarker(0x46)
	private final ModifiableCallout<HeadMarkerEvent> ratzonGreen = new ModifiableCallout<>("Ratzon (Green)", "Green Spread");

	@PlayerHeadmarker(0x47)
	private final ModifiableCallout<HeadMarkerEvent> ratzonPurple = new ModifiableCallout<>("Ratzon (Purple)", "Purple Spread");

	private final ModifiableCallout<HeadMarkerEvent> fiendishRageNoStack = new ModifiableCallout<>("Fiendish Rage (Main Tank)", "Don't Stack");
	private final ModifiableCallout<HeadMarkerEvent> fiendishRageStack = new ModifiableCallout<>("Fiendish Rage", "Group Stacks");
	private final RepeatSuppressor fiendishRageSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	@HandleEvents
	public void fiendishRage(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x48) && fiendishRageSupp.check(event)) {
			context.accept((playerIsMainTank ? fiendishRageNoStack : fiendishRageStack).getModified(event));
		}
	}

	@NpcCastCallout(0x769F)
	private final ModifiableCallout<AbilityCastStart> daatSpread = ModifiableCallout.durationBasedCall("Da'at", "Spread");

	@NpcCastCallout(0x76AF)
	private final ModifiableCallout<AbilityCastStart> malkuth = ModifiableCallout.durationBasedCall("Malkuth", "Knockback");

	// 非タンクが2回踏むと死ぬので強めに呼ぶ
	@NpcCastCallout(value = 0x76AB, suppressMs = 2_000)
	private final ModifiableCallout<AbilityCastStart> yesod = ModifiableCallout.durationBasedCall("Yesod", "Move Away");

	private final ModifiableCallout<BuffApplied> forceAgainstMight = ModifiableCallout.<BuffApplied>durationBasedCall("Force Against Might", "Orange ({event.buff.name})").autoIcon();
	private final ModifiableCallout<BuffApplied> forceAgainstMagic = ModifiableCallout.<BuffApplied>durationBasedCall("Force Against Magic", "Green ({event.buff.name})").autoIcon();

	@HandleEvents
	public void forceApplied(EventContext context, BuffApplied event) {
		if (!event.getTarget().isThePlayer() || !event.buffIdMatches(FORCE_AGAINST_MIGHT, FORCE_AGAINST_MAGIC)) {
			return;
		}
		force = event.getBuff().getId();
		context.accept((force == FORCE_AGAINST_MIGHT ? forceAgainstMight : forceAgainstMagic).getModified(event));
	}

	@HandleEvents
	public void forceRemoved(EventContext context, BuffRemoved event) {
		if (event.getTarget().isThePlayer() && event.buffIdMatches(FORCE_AGAINST_MIGHT, FORCE_AGAINST_MAGIC)) {
			force = 0;
		}
	}

	// 左がオレンジ、右が緑。デバフがなければ中央で軽いダメージを受けるだけ
	private final ModifiableCallout<AbilityCastStart> gevurahChesedLeft = ModifiableCallout.durationBasedCall("Gevurah Chesed (Left)", "Left");
	private final ModifiableCallout<AbilityCastStart> gevurahChesedRight = ModifiableCallout.durationBasedCall("Gevurah Chesed (Right)", "Right");
	private final ModifiableCallout<AbilityCastStart> gevurahChesedAoe = ModifiableCallout.durationBasedCall("Gevurah Chesed (No Debuff)", "Raidwide");
	// こちらは左が緑、右がオレンジ
	private final ModifiableCallout<AbilityCastStart> chesedGevurahLeft = ModifiableCallout.durationBasedCall("Chesed Gevurah (Left)", "Left");
	private final ModifiableCallout<AbilityCastStart> chesedGevurahRight = ModifiableCallout.durationBasedCall("Chesed Gevurah (Right)", "Right");
	private final ModifiableCallout<AbilityCastStart> chesedGevurahAoe = ModifiableCallout.durationBasedCall("Chesed Gevurah (No Debuff)", "Raidwide");

	@HandleEvents
	public void gevurahChesed(EventContext context, AbilityCastStart event) {
		ModifiableCallout<AbilityCastStart> call;
		if (event.abilityIdMatches(0x76A5)) {
			if (force == 0) {
				call = gevurahChesedAoe;
			}
			else {
				call = force == FORCE_AGAINST_MIGHT ? gevurahChesedLeft : gevurahChesedRight;
			}
		}
		else if (event.abilityIdMatches(0x76A6)) {
			if (force == 0) {
				call = chesedGevurahAoe;
			}
			else {
				call = force == FORCE_AGAINST_MAGIC ? chesedGevurahLeft : chesedGevurahRight;
			}
		}
		else {
			return;
		}
		context.accept(call.getModified(event));
	}

	private final ModifiableCallout<AbilityUsedEvent> fiendishWailTower = new ModifiableCallout<>("Fiendish Wail (Tower)", "Get a Tower");
	private final ModifiableCallout<AbilityUsedEvent> fiendishWailAvoid = new ModifiableCallout<>("Fiendish Wail (Avoid)", "Avoid Towers");

	@HandleEvents
	public void fiendishWail(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(0x76A2) || !event.isFirstTarget()) {
			return;
		}
		boolean takeTower = force == FORCE_AGAINST_MIGHT || (force == 0 && state.playerJobMatches(Job::isTank));
		context.accept((takeTower ? fiendishWailTower : fiendishWailAvoid).getModified(event));
	}

	private final ModifiableCallout<TetherEvent> daatTethersMight = new ModifiableCallout<>("Da'at Tethers (Orange)", "Get Away, Avoid Puddles and Tethers");
	private final ModifiableCallout<TetherEvent> daatTethersMagic = new ModifiableCallout<>("Da'at Tethers (Green)", "Go Front, Get Tether");
	// 線が何度も付け替わるので、最初の1回だけ呼ぶ
	private final RepeatSuppressor daatTethersSupp = new RepeatSuppressor(Duration.ofSeconds(30));

	@HandleEvents
	public void daatTethers(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x30) && daatTethersSupp.check(event)) {
			context.accept((force == FORCE_AGAINST_MAGIC ? daatTethersMagic : daatTethersMight).getModified(event));
		}
	}

	@HandleEvents
	public void earthShakerCollect(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x28) && event.getTarget().isThePlayer()) {
			shakerOnPlayer = true;
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> earthShakerOnYou = new ModifiableCallout<>("Earth Shaker (on You)", "Earth Shaker, Max Melee");
	private final ModifiableCallout<HeadMarkerEvent> earthShakerAvoid = new ModifiableCallout<>("Earth Shaker (Avoid)", "Avoid Earth Shakers");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> earthShakerSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hme -> hme.markerIdMatches(0x28),
			(e1, s) -> {
				// マーカーは複数人に同時に付くので、出そろうのを待つ
				s.waitMs(500);
				s.updateCall(shakerOnPlayer ? earthShakerOnYou : earthShakerAvoid, e1);
				shakerOnPlayer = false;
			});

	@NpcCastCallout(0x7680)
	private final ModifiableCallout<AbilityCastStart> revelation = ModifiableCallout.durationBasedCall("Revelation", "Kill Storm of Words");

	private final ModifiableCallout<HeadMarkerEvent> ascension = new ModifiableCallout<>("Ascension", "Stack on {event.target}");

	@HandleEvents
	public void ascension(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E)) {
			context.accept(ascension.getModified(event));
		}
	}

}
