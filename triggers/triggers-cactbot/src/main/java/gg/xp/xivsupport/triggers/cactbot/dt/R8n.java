package gg.xp.xivsupport.triggers.cactbot.dt;

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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.models.ArenaPos;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.XivCombatant;

import java.util.ArrayList;
import java.util.List;

// cactbot（Apache License 2.0）の ui/raidboss/data/07-dt/raid/r8n.ts をもとに移植
@CalloutRepo(name = "Aac Cruiserweight M4", duty = KnownDuty.M8N)
public class R8n extends AutoChildEventHandler implements FilteredEventHandler {

	private static final ArenaPos arenaPos = new ArenaPos(100, 100, 5, 5);
	// 詠唱完了後に向き直るので、少し待ってから向きを読む
	private static final long REIGN_FACING_DELAY_MS = 1_200;
	private static final long COMBATANT_REFRESH_DELAY_MS = 100;
	private static final int MOONLIGHT_CLONES = 4;

	private XivState state;

	public R8n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.M8N);
	}

	@NpcCastCallout(0xA38E)
	private final ModifiableCallout<AbilityCastStart> extraplanarPursuit = ModifiableCallout.durationBasedCall("Extraplanar Pursuit", "Raidwide");

	private final ModifiableCallout<HeadMarkerEvent> greatDivide = new ModifiableCallout<>("Great Divide", "Shared Buster on {event.target}");

	@HandleEvents
	public void greatDivide(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x256)) {
			context.accept(greatDivide.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> wolvesReign = ModifiableCallout.durationBasedCall("Wolves' Reign", "In Later");
	private final ModifiableCallout<AbilityCastStart> wolvesReignDirection = new ModifiableCallout<>("Wolves' Reign (Direction)", "In {dir}");

	// ノーマルは常に内側。着地後のボスの向きの反対側が安置
	@AutoFeed
	private final SequentialTrigger<BaseEvent> wolvesReignSq = SqtTemplates.sq(30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xA96D, 0xA96E, 0xA90F, 0xA910),
			(e1, s) -> {
				s.updateCall(wolvesReign, e1);
				s.waitDuration(e1.getEstimatedRemainingDuration());
				s.waitMs(REIGN_FACING_DELAY_MS);
				s.refreshCombatants(COMBATANT_REFRESH_DELAY_MS);
				XivCombatant boss = state.getLatestCombatantData(e1.getSource());
				s.setParam("dir", ArenaPos.combatantFacing(boss).opposite());
				s.updateCall(wolvesReignDirection, e1);
			});

	private final ModifiableCallout<HeadMarkerEvent> heavensearth = new ModifiableCallout<>("Heavensearth", "Stack on {event.target}");

	@HandleEvents
	public void heavensearth(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E, 0x5D)) {
			context.accept(heavensearth.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> moonlightFirst = new ModifiableCallout<>("Beckon Moonlight (First Safe Spot)", "{quad1}", 10_000);
	private final ModifiableCallout<AbilityUsedEvent> moonlightBoth = new ModifiableCallout<>("Beckon Moonlight (Both Safe Spots)", "{quad1} then {quad2}", 10_000);
	private final ModifiableCallout<AbilityCastStart> moonlightSecond = new ModifiableCallout<>("Beckon Moonlight (Move)", "{quad2}");

	private volatile ArenaSector moonlightQuad2;

	private ArenaSector cleavedSide(AbilityUsedEvent event) {
		XivCombatant clone = state.getLatestCombatantData(event.getSource());
		ArenaSector pos = arenaPos.forCombatant(clone);
		// 右半面切りなら分身から見て反時計回り、左なら時計回りが被弾側
		if (event.abilityIdMatches(0xA97E, 0xA3DE)) {
			return pos.plusEighths(-2);
		}
		return pos.plusEighths(2);
	}

	private static ArenaSector safeQuadrant(ArenaSector cleave1, ArenaSector cleave2) {
		if (!cleave1.isOutside() || !cleave2.isOutside()) {
			return ArenaSector.UNKNOWN;
		}
		return ArenaSector.quadrants.stream()
				.filter(quad -> !quad.isStrictlyAdjacentTo(cleave1))
				.filter(quad -> !quad.isStrictlyAdjacentTo(cleave2))
				.findFirst()
				.orElse(ArenaSector.UNKNOWN);
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> moonlightSq = SqtTemplates.sq(30_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0xA97E, 0xA3DE, 0xA97F, 0xA3DF),
			(e1, s) -> {
				List<ArenaSector> bites = new ArrayList<>();
				s.waitThenRefreshCombatants(COMBATANT_REFRESH_DELAY_MS);
				bites.add(cleavedSide(e1));
				AbilityUsedEvent last = e1;
				while (bites.size() < MOONLIGHT_CLONES) {
					last = s.waitEvent(AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0xA97E, 0xA3DE, 0xA97F, 0xA3DF));
					s.waitThenRefreshCombatants(COMBATANT_REFRESH_DELAY_MS);
					bites.add(cleavedSide(last));
					if (bites.size() == 2) {
						s.setParam("quad1", safeQuadrant(bites.get(0), bites.get(1)));
						s.updateCall(moonlightFirst, last);
					}
				}
				ArenaSector quad2 = safeQuadrant(bites.get(2), bites.get(3));
				moonlightQuad2 = quad2;
				s.setParam("quad2", quad2);
				s.updateCall(moonlightBoth, last);
			});

	// 2回目の突進が終わったら2つ目の安置へ移動
	@AutoFeed
	private final SequentialTrigger<BaseEvent> moonlightMoveSq = SqtTemplates.sq(30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xA97C, 0xA376, 0xA97D, 0xA377),
			(e1, s) -> {
				AbilityCastStart second = s.waitEvent(AbilityCastStart.class, acs -> acs.abilityIdMatches(0xA97C, 0xA376, 0xA97D, 0xA377));
				s.waitDuration(second.getEstimatedRemainingDuration());
				ArenaSector quad2 = moonlightQuad2;
				if (quad2 != null) {
					s.setParam("quad2", quad2);
					s.updateCall(moonlightSecond, second);
				}
				moonlightQuad2 = null;
				// 残りの突進を消費して次のセットに備える
				s.waitEvents(MOONLIGHT_CLONES - 2, AbilityCastStart.class, acs -> acs.abilityIdMatches(0xA97C, 0xA376, 0xA97D, 0xA377));
			});

	@NpcCastCallout(value = {0xA981, 0xA374}, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> shadowchase = ModifiableCallout.durationBasedCall("Shadowchase", "Behind Clones");

	@PlayerHeadmarker(0x178)
	private final ModifiableCallout<HeadMarkerEvent> gust = new ModifiableCallout<>("Gust", "Spread");

	@NpcCastCallout(0xA743)
	private final ModifiableCallout<AbilityCastStart> ravenousSaber = ModifiableCallout.durationBasedCall("Ravenous Saber", "Big Raidwide");

	@NpcCastCallout(value = 0xA989, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> wealOfStone = ModifiableCallout.durationBasedCall("Weal of Stone", "Avoid Lines");

	private final ModifiableCallout<AbilityCastStart> shadowchaseRotate = new ModifiableCallout<>("Shadowchase (Rotate)", "Rotate");

	// 分身の突進後にドラゴンヘッドの裏へ回り込む
	@AutoFeed
	private final SequentialTrigger<BaseEvent> shadowchaseRotateSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xA374),
			(e1, s) -> {
				s.waitDuration(e1.getEstimatedRemainingDuration());
				s.updateCall(shadowchaseRotate, e1);
			});

	@NpcCastCallout(0xA4E2)
	private final ModifiableCallout<AbilityCastStart> trackingTremors = ModifiableCallout.durationBasedCall("Tracking Tremors", "Stack x5");

	@NpcCastCallout(value = 0xA98D, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> wealOfStoneCardinals = ModifiableCallout.durationBasedCall("Weal of Stone (Cardinals)", "Cardinals");

}
