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
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivPlayerCharacter;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/trial/sephirot-ex.ts をもとに移植
@CalloutRepo(name = "Containment Bay S1 T7 Extreme", duty = KnownDuty.ContainmentBayS1T7Extreme)
public class SephirotEx extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long FORCE_AGAINST_MIGHT = 0x3ED;
	private static final long FORCE_AGAINST_MAGIC = 0x3EE;

	private XivState state;

	public SephirotEx(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.ContainmentBayS1T7Extreme);
	}

	// オートアタックの対象をメインタンクとみなす
	private @Nullable Long mainTankId;
	private @Nullable Long force;

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		mainTankId = null;
		force = null;
	}

	@HandleEvents
	public void autoAttack(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x368) && !event.getSource().isPc()) {
			mainTankId = event.getTarget().getId();
		}
	}

	@HandleEvents
	public void forceGained(EventContext context, BuffApplied event) {
		if (event.getTarget().isThePlayer() && event.buffIdMatches(FORCE_AGAINST_MIGHT, FORCE_AGAINST_MAGIC)) {
			force = event.getBuff().getId();
		}
	}

	@HandleEvents
	public void forceLost(EventContext context, BuffRemoved event) {
		if (event.getTarget().isThePlayer() && event.buffIdMatches(FORCE_AGAINST_MIGHT, FORCE_AGAINST_MAGIC)) {
			force = null;
		}
	}

	private boolean isMainTank() {
		XivPlayerCharacter player = state.getPlayer();
		return mainTankId != null && player != null && mainTankId == player.getId();
	}

	@NpcCastCallout(0x1567)
	private final ModifiableCallout<AbilityCastStart> chesed = ModifiableCallout.durationBasedCall("Chesed", "Buster on {event.target}");

	@NpcCastCallout(0x1569)
	private final ModifiableCallout<AbilityCastStart> ain = ModifiableCallout.durationBasedCall("Ain", "Get Behind");

	@PlayerHeadmarker({0x0046, 0x0047})
	private final ModifiableCallout<HeadMarkerEvent> ratzon = new ModifiableCallout<>("Ratzon", "Spread");

	private final ModifiableCallout<HeadMarkerEvent> fiendishRageMainTank = new ModifiableCallout<>("Fiendish Rage (Main Tank)", "Don't Stack");
	private final ModifiableCallout<HeadMarkerEvent> fiendishRage = new ModifiableCallout<>("Fiendish Rage", "Group Stacks");
	private final RepeatSuppressor fiendishRageSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	@HandleEvents
	public void fiendishRageMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x0048) && fiendishRageSupp.check(event)) {
			context.accept((isMainTank() ? fiendishRageMainTank : fiendishRage).getModified(event));
		}
	}

	@NpcCastCallout(0x1572)
	private final ModifiableCallout<AbilityCastStart> daat = ModifiableCallout.durationBasedCall("Da'at", "Spread");

	@NpcCastCallout(0x1582)
	private final ModifiableCallout<AbilityCastStart> malkuth = ModifiableCallout.durationBasedCall("Malkuth", "Knockback");

	// タンク以外が2回以上受けると即死するため、確実に離れさせる
	@NpcCastCallout(value = 0x157E, suppressMs = 2_000)
	private final ModifiableCallout<AbilityCastStart> yesod = ModifiableCallout.durationBasedCall("Yesod", "Move Away");

	@PlayerStatusCallout(FORCE_AGAINST_MIGHT)
	private final ModifiableCallout<BuffApplied> forceAgainstMight = new ModifiableCallout<BuffApplied>("Force Against Might", "Orange")
			.statusIcon(FORCE_AGAINST_MIGHT);

	@PlayerStatusCallout(FORCE_AGAINST_MAGIC)
	private final ModifiableCallout<BuffApplied> forceAgainstMagic = new ModifiableCallout<BuffApplied>("Force Against Magic", "Green")
			.statusIcon(FORCE_AGAINST_MAGIC);

	private final ModifiableCallout<AbilityCastStart> gevurahLeft = ModifiableCallout.durationBasedCall("Gevurah/Chesed: Left", "Left");
	private final ModifiableCallout<AbilityCastStart> gevurahRight = ModifiableCallout.durationBasedCall("Gevurah/Chesed: Right", "Right");
	private final ModifiableCallout<AbilityCastStart> gevurahNoDebuff = ModifiableCallout.durationBasedCall("Gevurah/Chesed: No Debuff", "Raidwide");

	// 0x1578 はオレンジが左、0x1579 は緑が左
	@HandleEvents
	public void gevurahChesed(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x1578, 0x1579)) {
			return;
		}
		Long myForce = force;
		ModifiableCallout<AbilityCastStart> call;
		if (myForce == null) {
			// デバフが無ければ中央でも軽い被弾で済む
			call = gevurahNoDebuff;
		}
		else {
			long leftForce = event.abilityIdMatches(0x1578) ? FORCE_AGAINST_MIGHT : FORCE_AGAINST_MAGIC;
			call = myForce == leftForce ? gevurahLeft : gevurahRight;
		}
		context.accept(call.getModified(event));
	}

	private final ModifiableCallout<AbilityUsedEvent> fiendishWailTower = new ModifiableCallout<>("Fiendish Wail: Get Tower", "Get Tower");
	private final ModifiableCallout<AbilityUsedEvent> fiendishWailAvoid = new ModifiableCallout<>("Fiendish Wail: Avoid Towers", "Avoid Towers");

	@HandleEvents
	public void fiendishWail(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x1575) && event.isFirstTarget()) {
			Long myForce = force;
			boolean getTower = (myForce != null && myForce == FORCE_AGAINST_MIGHT)
			                   || (myForce == null && state.playerJobMatches(Job::isTank));
			context.accept((getTower ? fiendishWailTower : fiendishWailAvoid).getModified(event));
		}
	}

	private final ModifiableCallout<TetherEvent> daatTetherMight = new ModifiableCallout<>("Da'at Tethers (Orange)", "Get Away, Avoid Puddles and Tethers");
	private final ModifiableCallout<TetherEvent> daatTetherMagic = new ModifiableCallout<>("Da'at Tethers (Green)", "Go Front, Get Tether");
	// 線が何度も付け替わるので長めに抑制する
	private final RepeatSuppressor daatTetherSupp = new RepeatSuppressor(Duration.ofSeconds(30));

	@HandleEvents
	public void daatTethers(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x0030) && daatTetherSupp.check(event)) {
			Long myForce = force;
			boolean magic = myForce != null && myForce == FORCE_AGAINST_MAGIC;
			context.accept((magic ? daatTetherMagic : daatTetherMight).getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> earthShakerOnYou = new ModifiableCallout<>("Earth Shaker on You", "Earth Shaker, Max Melee");
	private final ModifiableCallout<HeadMarkerEvent> earthShakerAvoid = new ModifiableCallout<>("Earth Shaker not on You", "Avoid Earth Shakers");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> earthShakerSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(0x0028),
			(e1, s) -> {
				// マーカーは複数人にほぼ同時に付くので、少し待ってからまとめて判定する
				List<HeadMarkerEvent> others = s.waitEventsUntil(8,
						HeadMarkerEvent.class, hm -> hm.markerIdMatches(0x0028),
						BaseEvent.class, e -> e1.getEffectiveTimeSince().toMillis() >= 500);
				boolean onMe = e1.getTarget().isThePlayer()
				               || others.stream().anyMatch(hm -> hm.getTarget().isThePlayer());
				s.updateCall(onMe ? earthShakerOnYou : earthShakerAvoid, e1);
			});

	@NpcCastCallout(0x1583)
	private final ModifiableCallout<AbilityCastStart> revelation = ModifiableCallout.durationBasedCall("Storm of Words: Revelation", "Kill Storm of Words");

	private final ModifiableCallout<HeadMarkerEvent> ascension = new ModifiableCallout<>("Ascension", "Stack");

	@HandleEvents
	public void ascensionMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x003E)) {
			context.accept(ascension.getModified(event));
		}
	}
}
