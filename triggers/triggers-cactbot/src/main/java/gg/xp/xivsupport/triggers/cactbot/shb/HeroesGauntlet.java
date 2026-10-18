package gg.xp.xivsupport.triggers.cactbot.shb;

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
import java.util.ArrayList;
import java.util.List;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/dungeon/heroes_gauntlet.ts をもとに移植
@CalloutRepo(name = "The Heroes Gauntlet", duty = KnownDuty.TheHeroesGauntlet)
public class HeroesGauntlet extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long STACK_MARKER = 0x5D;

	private XivState state;

	public HeroesGauntlet(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheHeroesGauntlet);
	}

	// スペクトル・シーフ

	@NpcCastCallout(0x4FCB)
	private final ModifiableCallout<AbilityCastStart> spectralDream = ModifiableCallout.durationBasedCall("Spectral Dream", "Buster on {event.target}");

	@PlayerHeadmarker(0xA9)
	private final ModifiableCallout<HeadMarkerEvent> spectralGust = new ModifiableCallout<>("Spectral Gust", "Spread");

	@NpcCastCallout(0x4FCC)
	private final ModifiableCallout<AbilityCastStart> spectralWhirlwind = ModifiableCallout.durationBasedCall("Spectral Whirlwind", "Raidwide");

	// 攻撃予定地点に線が付く
	private final ModifiableCallout<TetherEvent> spectralTether = new ModifiableCallout<>("Spectral Tether", "Away from Tether Marker");
	private final RepeatSuppressor spectralTetherSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void spectralTether(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x0C) && spectralTetherSupp.check(event)) {
			context.accept(spectralTether.getModified(event));
		}
	}

	// スペクトル・ネクロマンサー

	private final ModifiableCallout<AbilityCastStart> absoluteProtect = ModifiableCallout.durationBasedCall("Absolute Protect", "Interrupt {event.source}");

	@HandleEvents
	public void absoluteProtect(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x524D) && state.playerJobMatches(job -> job.caresAboutInterrupt())) {
			context.accept(absoluteProtect.getModified(event));
		}
	}

	private final ModifiableCallout<TetherEvent> zombieTether = new ModifiableCallout<>("Large Zombie Tether", "Spread");

	@HandleEvents
	public void zombieTether(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x4F) && event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			context.accept(zombieTether.getModified(event));
		}
	}

	@NpcCastCallout(0x4F5E)
	private final ModifiableCallout<AbilityCastStart> twistedTouch = ModifiableCallout.durationBasedCall("Twisted Touch", "Buster on {event.target}");

	@NpcCastCallout(0x4F60)
	private final ModifiableCallout<AbilityCastStart> chaosStorm = ModifiableCallout.durationBasedCall("Chaos Storm", "Raidwide");

	// スペクトル・バーサーカー

	@NpcCastCallout(0x520C)
	private final ModifiableCallout<AbilityCastStart> beastlyFury = ModifiableCallout.durationBasedCall("Beastly Fury", "Raidwide");

	@NpcCastCallout({0x520A, 0x520B})
	private final ModifiableCallout<AbilityCastStart> ragingSlice = ModifiableCallout.durationBasedCall("Raging Slice", "Away from Front");

	@NpcCastCallout(0x5206)
	private final ModifiableCallout<AbilityCastStart> wildRampage = ModifiableCallout.durationBasedCall("Wild Rampage", "Get in a Crater");

	@NpcCastCallout(value = {0x5202, 0x5203, 0x5204}, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> wildRage = ModifiableCallout.durationBasedCall("Wild Rage", "Knockback");

	// プレイヤーへの頭割りと岩への頭割りが同じマーカーなので、付いた人数で区別する
	private final List<XivCombatant> anguishTargets = new ArrayList<>();

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		anguishTargets.clear();
	}

	@HandleEvents
	public void anguishCollect(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(STACK_MARKER)) {
			anguishTargets.add(event.getTarget());
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> anguishRock = new ModifiableCallout<>("Wild Anguish (Rocks)", "Stack on Your Rock");
	private final ModifiableCallout<HeadMarkerEvent> anguishOnYou = new ModifiableCallout<>("Wild Anguish (On You)", "Stack on You");
	private final ModifiableCallout<HeadMarkerEvent> anguish = new ModifiableCallout<>("Wild Anguish", "Stack on {event.target}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> anguishSq = SqtTemplates.sq(10_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(STACK_MARKER),
			(e1, s) -> {
				s.waitMs(1_000);
				int count = anguishTargets.size();
				anguishTargets.clear();
				if (count > 1) {
					s.updateCall(anguishRock, e1);
				}
				else if (e1.getTarget().isThePlayer()) {
					s.updateCall(anguishOnYou, e1);
				}
				else {
					s.updateCall(anguish, e1);
				}
				// 同じマーカーが続けて来ても再度呼ばない
				s.waitMs(4_000);
				anguishTargets.clear();
			});

	@PlayerHeadmarker(0x5E)
	private final ModifiableCallout<HeadMarkerEvent> anguishSpread = new ModifiableCallout<>("Wild Anguish Spread", "Spread");

}
