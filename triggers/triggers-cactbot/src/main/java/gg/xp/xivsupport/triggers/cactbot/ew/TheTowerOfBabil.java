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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import org.jetbrains.annotations.Nullable;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/dungeon/the_tower_of_babil.ts をもとに移植
@CalloutRepo(name = "The Tower Of Babil", duty = KnownDuty.TheTowerOfBabil)
public class TheTowerOfBabil extends AutoChildEventHandler implements FilteredEventHandler {

	// 頭上マーカーやもう一方の詠唱が揃うまで待つ時間
	private static final int COLLECT_MS = 500;

	private XivState state;

	public TheTowerOfBabil(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheTowerOfBabil);
	}

	private @Nullable Boolean barnabasNegative;
	private @Nullable Boolean playerNegative;

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		barnabasNegative = null;
		playerNegative = null;
	}

	// バルナバス

	@NpcCastCallout(value = {0x6247, 0x62EA})
	private final ModifiableCallout<AbilityCastStart> groundAndPound = ModifiableCallout.durationBasedCall("Ground and Pound", "Away from Front");

	@HandleEvents
	public void dynamicMarkers(EventContext context, HeadMarkerEvent event) {
		// プレイヤーの極性マーカーはスクラップラインとパウンドの両方で使われるので毎回記録する
		if (event.markerIdMatches(0xA2, 0xA3) && event.getTarget().isThePlayer()) {
			playerNegative = event.markerIdMatches(0xA3);
		}
		else if (event.markerIdMatches(0x122, 0x123) && !event.getTarget().isPc()) {
			barnabasNegative = event.markerIdMatches(0x122);
		}
	}

	@HandleEvents
	public void dynamicPoundCollect(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x6245, 0x62EE)) {
			barnabasNegative = event.abilityIdMatches(0x6245);
		}
	}

	// 極性が同じなら引き寄せられ、違えば弾かれる
	private @Nullable Boolean consumeSamePolarity() {
		Boolean boss = barnabasNegative;
		Boolean player = playerNegative;
		barnabasNegative = null;
		playerNegative = null;
		if (boss == null || player == null) {
			return null;
		}
		return boss.equals(player);
	}

	private final ModifiableCallout<AbilityCastStart> dynamicScraplineClose = ModifiableCallout.durationBasedCall("Dynamic Scrapline (Same Polarity)", "Close to Boss");
	private final ModifiableCallout<AbilityCastStart> dynamicScraplineFar = ModifiableCallout.durationBasedCall("Dynamic Scrapline (Opposite Polarity)", "Away from Boss");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> dynamicScraplineSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x6246, 0x62F0),
			(e1, s) -> {
				s.waitMs(COLLECT_MS);
				Boolean same = consumeSamePolarity();
				if (same != null) {
					s.updateCall(same ? dynamicScraplineClose : dynamicScraplineFar, e1);
				}
			});

	private final ModifiableCallout<AbilityCastStart> dynamicPoundClose = ModifiableCallout.durationBasedCall("Dynamic Pound (Same Polarity)", "Go Center Next to Scrapline");
	private final ModifiableCallout<AbilityCastStart> dynamicPoundFar = ModifiableCallout.durationBasedCall("Dynamic Pound (Opposite Polarity)", "Go Sides Away from Scrapline");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> dynamicPoundSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x6245, 0x62EE),
			(e1, s) -> {
				s.waitMs(COLLECT_MS);
				Boolean same = consumeSamePolarity();
				if (same != null) {
					s.updateCall(same ? dynamicPoundClose : dynamicPoundFar, e1);
				}
			});

	@NpcCastCallout(0x62EB)
	private final ModifiableCallout<AbilityCastStart> rollingScrapline = ModifiableCallout.durationBasedCall("Rolling Scrapline", "Out of Melee");

	private final ModifiableCallout<HeadMarkerEvent> shockingForce = new ModifiableCallout<>("Shocking Force", "Stack on {event.target}");

	@HandleEvents
	public void shockingForce(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E)) {
			context.accept(shockingForce.getModified(event));
		}
	}

	// ルゲイエ

	@NpcAbilityUsedCallout(value = 0x62F3, suppressMs = 10_000)
	private final ModifiableCallout<AbilityUsedEvent> magitekChakram = new ModifiableCallout<>("Magitek Chakram", "Purple Pad to Shrink");

	@NpcAbilityUsedCallout(value = 0x62F5, suppressMs = 2_000)
	private final ModifiableCallout<AbilityUsedEvent> downpour = new ModifiableCallout<>("Downpour", "Green Pad for Frog");

	@NpcCastCallout(0x62FA)
	private final ModifiableCallout<AbilityCastStart> thermalSuppression = ModifiableCallout.durationBasedCall("Thermal Suppression", "Raidwide");

	@NpcAbilityUsedCallout(value = 0x62F8, suppressMs = 2_000)
	private final ModifiableCallout<AbilityUsedEvent> magitekExplosive = new ModifiableCallout<>("Magitek Explosive", "Avoid Bomb Lines");

	// アニマ

	@NpcAbilityUsedCallout(value = 0x62FE, suppressMs = 2_000)
	private final ModifiableCallout<AbilityUsedEvent> lunarNail = new ModifiableCallout<>("Lunar Nail", "Go to Safe Quadrant");

	@NpcCastCallout(0x6300)
	private final ModifiableCallout<AbilityCastStart> megaGraviton = ModifiableCallout.durationBasedCall("Mega Graviton", "Raidwide");

	@NpcCastCallout(value = 0x6301, onYou = true)
	private final ModifiableCallout<AbilityCastStart> aetherialPull = ModifiableCallout.durationBasedCall("Aetherial Pull", "Away from Your Tether Add");

	@NpcAbilityUsedCallout(value = 0x6303, suppressMs = 2_000)
	private final ModifiableCallout<AbilityUsedEvent> boundlessPain = new ModifiableCallout<>("Boundless Pain", "Get to a Corner");

	@PlayerHeadmarker(0xC5)
	private final ModifiableCallout<HeadMarkerEvent> coffinScratch = new ModifiableCallout<>("Coffin Scratch", "5x Chasing Puddles on You");

}
