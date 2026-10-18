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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/trial/shinryu.ts をもとに移植
@CalloutRepo(name = "The Royal Menagerie", duty = KnownDuty.TheRoyalMenagerie)
public class Shinryu extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long DIVEBOMB = 0x1FF4;
	private static final int CAST_WAIT_TIMEOUT_MS = 30_000;

	private XivState state;

	public Shinryu(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheRoyalMenagerie);
	}

	// アク・ラーイは中間フェーズ後にしか来ない
	private boolean finalPhase;

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		finalPhase = false;
	}

	// 詠唱時間がそのまま使えないものは、着弾の一定時間前に呼ぶ
	private static SequentialTrigger<BaseEvent> callBeforeCastEnd(long abilityId, Duration lead, ModifiableCallout<AbilityCastStart> call) {
		return SqtTemplates.sq(CAST_WAIT_TIMEOUT_MS,
				AbilityCastStart.class, acs -> acs.abilityIdMatches(abilityId),
				(e1, s) -> {
					Duration wait = e1.getInitialDuration().minus(lead);
					if (wait.isPositive()) {
						s.waitDuration(wait);
					}
					s.updateCall(call, e1);
				});
	}

	private final ModifiableCallout<AbilityCastStart> akhMornOnYou = ModifiableCallout.durationBasedCall("Akh Morn: On You", "Akh Morn on You");
	private final ModifiableCallout<AbilityCastStart> akhMornOnOther = ModifiableCallout.durationBasedCall("Akh Morn: On Other (Tank)", "Akh Morn on {event.target}");
	private final ModifiableCallout<AbilityCastStart> akhRhai = ModifiableCallout.durationBasedCall("Akh Rhai", "Akh Rhai: Spread and Move");

	@HandleEvents
	public void akhMorn(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x1FA4)) {
			return;
		}
		boolean tank = state.playerJobMatches(Job::isTank);
		if (event.getTarget().isThePlayer()) {
			context.accept(akhMornOnYou.getModified(event));
			return;
		}
		if (tank) {
			context.accept(akhMornOnOther.getModified(event));
		}
		// アク・モーンの対象とタンクはアク・ラーイの直接の対象にならない
		else if (finalPhase) {
			context.accept(akhRhai.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> diamondDust = ModifiableCallout.durationBasedCall("Diamond Dust", "Stop Moving");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> diamondDustSq = callBeforeCastEnd(0x1FAD, Duration.ofSeconds(4), diamondDust);

	@NpcCastCallout(0x24EF)
	private final ModifiableCallout<AbilityCastStart> dragonfist = ModifiableCallout.durationBasedCall("Dragonfist", "Out of Middle");

	@NpcCastCallout(0x1FAB)
	private final ModifiableCallout<AbilityCastStart> hellfire = ModifiableCallout.durationBasedCall("Hellfire", "Get in Water");

	@NpcCastCallout(value = {0x1F99, 0x1F9A}, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> hypernova = ModifiableCallout.durationBasedCall("Hypernova", "Stack in Water");

	@NpcCastCallout(0x1FAC)
	private final ModifiableCallout<AbilityCastStart> judgementBolt = ModifiableCallout.durationBasedCall("Judgement Bolt", "Out of Water");

	@NpcCastCallout(0x1F9B)
	private final ModifiableCallout<AbilityCastStart> levinbolt = ModifiableCallout.durationBasedCall("Levinbolt", "Spread, No Water");

	private final ModifiableCallout<AbilityCastStart> tidalWave = ModifiableCallout.durationBasedCall("Tidal Wave", "Knockback, Look for Water");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> tidalWaveSq = callBeforeCastEnd(0x1FAA, Duration.ofSeconds(6), tidalWave);

	private final ModifiableCallout<AbilityCastStart> iceStorm = ModifiableCallout.durationBasedCall("Ice Storm", "Raidwide");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> iceStormSq = callBeforeCastEnd(0x1FA2, Duration.ofSeconds(4), iceStorm);

	private final ModifiableCallout<AbilityCastStart> tailSlap = new ModifiableCallout<>("Tail Slap", "Tail: Switch Targets");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> tailSlapSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x1F93),
			(e1, s) -> {
				s.waitMs(2_000);
				s.updateCall(tailSlap, e1);
			});

	@HandleEvents
	public void finalPhaseStart(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(DIVEBOMB)) {
			finalPhase = true;
		}
	}

	@NpcCastCallout(DIVEBOMB)
	private final ModifiableCallout<AbilityCastStart> divebomb = ModifiableCallout.durationBasedCall("Divebomb", "Avoid Divebomb");

	private final ModifiableCallout<HeadMarkerEvent> burningChains = new ModifiableCallout<>("Burning Chains", "Break Chains");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> burningChainsSq = SqtTemplates.sq(10_000,
			HeadMarkerEvent.class, hme -> hme.markerIdMatches(0x61) && hme.getTarget().isThePlayer(),
			(e1, s) -> {
				s.waitMs(3_800);
				s.updateCall(burningChains, e1);
			});

	// 確かな情報は無いが、マーカー対象側から尻尾が来るように見える
	@PlayerHeadmarker(0x62)
	private final ModifiableCallout<HeadMarkerEvent> slapMarker = new ModifiableCallout<>("Slap Marker", "Bait onto Unbroken Squares");

}
