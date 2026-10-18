package gg.xp.xivsupport.triggers.cactbot.sb;

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
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.BuffRemoved;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/raid/o8s.ts をもとに移植
@CalloutRepo(name = "Sigmascape V40 Savage", duty = KnownDuty.O4S)
public class O8s extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long spreadMarkerId = 0x7F;
	private static final long stackMarkerId = 0x80;
	private static final int thunderSuppressMs = 40_000;

	private XivState state;

	public O8s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.O4S);
	}

	private boolean antics;
	private boolean truths;
	private Boolean lastFireSpread;
	private Boolean lastThunderTrue;
	private Boolean lastIceOut;
	private Boolean fireMarkerSpread;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		antics = false;
		truths = false;
		clearMana();
		fireMarkerSpread = null;
	}

	private void clearMana() {
		lastFireSpread = null;
		lastThunderTrue = null;
		lastIceOut = null;
	}

	// グレイブンイメージ

	private final ModifiableCallout<AbilityCastStart> shockwave = new ModifiableCallout<>("Shockwave", "Knockback");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> shockwaveSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x28DB),
			(e1, s) -> {
				// 詠唱開始直後に言うと早すぎるため、少し遅らせる
				s.waitMs(5_000);
				s.updateCall(shockwave, e1);
			});

	@NpcCastCallout(0x28E4)
	private final ModifiableCallout<AbilityCastStart> indolentWill = ModifiableCallout.durationBasedCall("Indolent Will", "Look Away From Statue");

	@NpcCastCallout(0x28DF)
	private final ModifiableCallout<AbilityCastStart> intemperateWill = ModifiableCallout.durationBasedCall("Intemperate Will", "West");

	@NpcCastCallout(0x28DE)
	private final ModifiableCallout<AbilityCastStart> gravitationalWave = ModifiableCallout.durationBasedCall("Gravitational Wave", "East");

	@NpcCastCallout(0x28E3)
	private final ModifiableCallout<AbilityCastStart> aveMaria = ModifiableCallout.durationBasedCall("Ave Maria", "Look At Statue");

	@NpcCastCallout(value = 0x28DD, onYou = true)
	private final ModifiableCallout<AbilityCastStart> pulseWave = ModifiableCallout.durationBasedCall("Pulse Wave", "Knockback on You");

	@NpcCastCallout(value = 0x28E5, onYou = true)
	private final ModifiableCallout<AbilityCastStart> indulgentWill = ModifiableCallout.durationBasedCall("Indulgent Will", "Confusion: Go Outside");

	@NpcCastCallout(value = 0x28E6, onYou = true)
	private final ModifiableCallout<AbilityCastStart> idyllicWill = ModifiableCallout.durationBasedCall("Idyllic Will", "Sleep: Go Inside");

	// ケフカ

	@NpcCastCallout(0x28F1)
	private final ModifiableCallout<AbilityCastStart> pastsForgotten = ModifiableCallout.durationBasedCall("Past's Forgotten", "Past: Stack and Stay");

	@NpcCastCallout(0x28EE)
	private final ModifiableCallout<AbilityCastStart> futuresNumbered = ModifiableCallout.durationBasedCall("Future's Numbered", "Future: Stack and Through");

	private final ModifiableCallout<AbilityCastStart> pastsEnd = ModifiableCallout.durationBasedCall("Past's End (Tank/Healer)", "Past: Bait, then Through");
	private final ModifiableCallout<AbilityCastStart> futuresEnd = ModifiableCallout.durationBasedCall("Future's End (Tank/Healer)", "Future: Bait, then Stay");

	@HandleEvents
	public void endCasts(EventContext context, AbilityCastStart event) {
		if (!state.playerJobMatches(job -> job.isTank() || job.isHealer())) {
			return;
		}
		if (event.abilityIdMatches(0x28F7, 0x28F8)) {
			context.accept(pastsEnd.getModified(event));
		}
		else if (event.abilityIdMatches(0x28F4, 0x28F5)) {
			context.accept(futuresEnd.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> wingsTank = ModifiableCallout.durationBasedCall("Wings of Destruction (Tank)", "Wings: Be Near/Far");
	private final ModifiableCallout<AbilityCastStart> wings = ModifiableCallout.durationBasedCall("Wings of Destruction", "Max Melee: Avoid Tanks");

	@HandleEvents
	public void wingsOfDestruction(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x2900)) {
			context.accept((state.playerJobMatches(Job::isTank) ? wingsTank : wings).getModified(event));
		}
	}

	@NpcCastCallout({0x28FE, 0x28FF})
	private final ModifiableCallout<AbilityCastStart> singleWing = ModifiableCallout.durationBasedCall("Single Wing of Destruction", "Single Wing");

	private final ModifiableCallout<AbilityCastStart> embraceOnYou = ModifiableCallout.durationBasedCall("Ultimate Embrace (On You)", "Embrace on You");
	private final ModifiableCallout<AbilityCastStart> embraceOther = ModifiableCallout.durationBasedCall("Ultimate Embrace", "Embrace on {event.target}");

	@HandleEvents
	public void ultimateEmbrace(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x2910)) {
			context.accept((event.getTarget().isThePlayer() ? embraceOnYou : embraceOther).getModified(event));
		}
	}

	@NpcCastCallout({0x28E8, 0x2912})
	private final ModifiableCallout<AbilityCastStart> hyperdrive = ModifiableCallout.durationBasedCall("Hyperdrive", "Buster on {event.target}");

	@NpcCastCallout(0x28E7)
	private final ModifiableCallout<AbilityCastStart> ultimaUpsurge = ModifiableCallout.durationBasedCall("Ultima Upsurge", "Raidwide");

	@NpcCastCallout(0x2911)
	private final ModifiableCallout<AbilityCastStart> ultima = ModifiableCallout.durationBasedCall("Ultima", "Raidwide");

	// 道化の嘘とまことの見極め

	@HandleEvents
	public void jesterGain(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x5CE)) {
			antics = true;
		}
		else if (event.buffIdMatches(0x5CF)) {
			truths = true;
		}
	}

	@HandleEvents
	public void jesterLoss(EventContext context, BuffRemoved event) {
		if (event.buffIdMatches(0x5CE, 0x5CF)) {
			antics = false;
			truths = false;
		}
	}

	// 魔力解放で直前の魔力充填と同じ技が来るので、その内容を覚えておく
	@HandleEvents
	public void manaCharge(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x28D1)) {
			clearMana();
		}
	}

	private final RepeatSuppressor fireMarkerSupp = new RepeatSuppressor(Duration.ofSeconds(2));

	@HandleEvents
	public void fireMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(spreadMarkerId, stackMarkerId) && fireMarkerSupp.check(event)) {
			fireMarkerSpread = event.markerIdMatches(spreadMarkerId);
		}
	}

	private final ModifiableCallout<AbilityCastStart> fireSpread = ModifiableCallout.durationBasedCall("Fire (Spread)", "Spread");
	private final ModifiableCallout<AbilityCastStart> fireStack = ModifiableCallout.durationBasedCall("Fire (Stack)", "Stack");

	@HandleEvents
	public void fire(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x28CE) || fireMarkerSpread == null) {
			return;
		}
		// 道化の嘘なら頭上マーカーと逆の処理になる
		boolean spread;
		if (truths) {
			spread = fireMarkerSpread;
		}
		else if (antics) {
			spread = !fireMarkerSpread;
		}
		else {
			return;
		}
		context.accept((spread ? fireSpread : fireStack).getModified(event));
		lastFireSpread = spread;
		fireMarkerSpread = null;
	}

	private final ModifiableCallout<AbilityCastStart> trueThunder = ModifiableCallout.durationBasedCall("Thrumming Thunder (True)", "True Thunder");
	private final ModifiableCallout<AbilityCastStart> fakeThunder = ModifiableCallout.durationBasedCall("Thrumming Thunder (Fake)", "Fake Thunder");
	private final RepeatSuppressor trueThunderSupp = new RepeatSuppressor(Duration.ofMillis(thunderSuppressMs));
	private final RepeatSuppressor fakeThunderSupp = new RepeatSuppressor(Duration.ofMillis(thunderSuppressMs));

	@HandleEvents
	public void thunder(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x28CD, 0x2B31)) {
			lastThunderTrue = true;
			if (trueThunderSupp.check(event)) {
				context.accept(trueThunder.getModified(event));
			}
		}
		else if (event.abilityIdMatches(0x28CC, 0x2B30)) {
			lastThunderTrue = false;
			if (fakeThunderSupp.check(event)) {
				context.accept(fakeThunder.getModified(event));
			}
		}
	}

	private final ModifiableCallout<AbilityCastStart> fakeIceDonut = ModifiableCallout.durationBasedCall("Blizzard (Fake Donut)", "Fake Ice: Get Out");
	private final ModifiableCallout<AbilityCastStart> trueIceDonut = ModifiableCallout.durationBasedCall("Blizzard (True Donut)", "True Ice: Get In");
	private final ModifiableCallout<AbilityCastStart> fakeIceNear = ModifiableCallout.durationBasedCall("Blizzard (Fake Near)", "Fake Ice: Get In");
	private final ModifiableCallout<AbilityCastStart> trueIceNear = ModifiableCallout.durationBasedCall("Blizzard (True Near)", "True Ice: Get Out");
	private final RepeatSuppressor fakeIceDonutSupp = new RepeatSuppressor(Duration.ofMillis(thunderSuppressMs));
	private final RepeatSuppressor trueIceDonutSupp = new RepeatSuppressor(Duration.ofMillis(thunderSuppressMs));
	private final RepeatSuppressor fakeIceNearSupp = new RepeatSuppressor(Duration.ofMillis(thunderSuppressMs));
	private final RepeatSuppressor trueIceNearSupp = new RepeatSuppressor(Duration.ofMillis(thunderSuppressMs));

	@HandleEvents
	public void blizzard(EventContext context, AbilityCastStart event) {
		ModifiableCallout<AbilityCastStart> call;
		RepeatSuppressor supp;
		if (event.abilityIdMatches(0x28C5, 0x2B2B)) {
			lastIceOut = true;
			call = fakeIceDonut;
			supp = fakeIceDonutSupp;
		}
		else if (event.abilityIdMatches(0x28C9, 0x2B2E)) {
			lastIceOut = false;
			call = trueIceDonut;
			supp = trueIceDonutSupp;
		}
		else if (event.abilityIdMatches(0x28C4, 0x2B2A)) {
			lastIceOut = false;
			call = fakeIceNear;
			supp = fakeIceNearSupp;
		}
		else if (event.abilityIdMatches(0x28C8, 0x2B2D)) {
			lastIceOut = true;
			call = trueIceNear;
			supp = trueIceNearSupp;
		}
		else {
			return;
		}
		if (supp.check(event)) {
			context.accept(call.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> releaseSpread = ModifiableCallout.durationBasedCall("Mana Release (Fire Spread)", "Spread");
	private final ModifiableCallout<AbilityCastStart> releaseStack = ModifiableCallout.durationBasedCall("Mana Release (Fire Stack)", "Stack");
	private final ModifiableCallout<AbilityCastStart> releaseTrueThunderOut = ModifiableCallout.durationBasedCall("Mana Release (True Thunder, Out)", "True Thunder, Get Out");
	private final ModifiableCallout<AbilityCastStart> releaseTrueThunderIn = ModifiableCallout.durationBasedCall("Mana Release (True Thunder, In)", "True Thunder, Get In");
	private final ModifiableCallout<AbilityCastStart> releaseFakeThunderOut = ModifiableCallout.durationBasedCall("Mana Release (Fake Thunder, Out)", "Fake Thunder, Get Out");
	private final ModifiableCallout<AbilityCastStart> releaseFakeThunderIn = ModifiableCallout.durationBasedCall("Mana Release (Fake Thunder, In)", "Fake Thunder, Get In");

	@HandleEvents
	public void manaRelease(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x28D2)) {
			return;
		}
		if (lastFireSpread != null) {
			context.accept((lastFireSpread ? releaseSpread : releaseStack).getModified(event));
			return;
		}
		if (lastIceOut == null || lastThunderTrue == null) {
			return;
		}
		ModifiableCallout<AbilityCastStart> call;
		if (lastThunderTrue) {
			call = lastIceOut ? releaseTrueThunderOut : releaseTrueThunderIn;
		}
		else {
			call = lastIceOut ? releaseFakeThunderOut : releaseFakeThunderIn;
		}
		context.accept(call.getModified(event));
	}

}
