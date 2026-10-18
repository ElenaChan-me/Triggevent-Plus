package gg.xp.xivsupport.triggers.cactbot.sb;

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
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/dungeon/bardams_mettle.ts をもとに移植
@CalloutRepo(name = "Bardams Mettle", duty = KnownDuty.BardamsMettle)
public class BardamsMettle extends AutoChildEventHandler implements FilteredEventHandler {

	// 着弾の何秒前に岩陰へ隠れるよう呼ぶか
	private static final Duration METEOR_IMPACT_LEAD = Duration.ofSeconds(7);

	private XivState state;

	public BardamsMettle(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.BardamsMettle);
	}

	// バルダムとヨルで同じマーカーを使うので、ヨル戦に入ったかどうかで区別する
	private boolean yolFight;

	// ガルラ

	private final ModifiableCallout<TetherEvent> rush = new ModifiableCallout<>("Rush", "Run Away From Boss");
	// 線のログはギミックが終わるまで繰り返し出る
	private final RepeatSuppressor rushSupp = new RepeatSuppressor(Duration.ofSeconds(15));

	@HandleEvents
	public void rush(EventContext context, TetherEvent event) {
		// 0x39 が伸びていない線、0x01 が伸びた線。最初から伸びている場合もあるので両方見る
		if (event.tetherIdMatches(0x39, 0x1) && event.eitherTargetMatches(XivCombatant::isThePlayer) && rushSupp.check(event)) {
			context.accept(rush.getModified(event));
		}
	}

	@NpcAbilityUsedCallout(value = 0x1EF9, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> warCryEarthquake = new ModifiableCallout<>("War Cry + Earthquake", "Raidwide");

	// バルダム

	@NpcCastCallout(0x1F04)
	private final ModifiableCallout<AbilityCastStart> emptyGaze = ModifiableCallout.durationBasedCall("Empty Gaze", "Look Away");

	@NpcCastCallout(value = 0x1F01, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> sacrifice = ModifiableCallout.durationBasedCall("Sacrifice", "Stand in a Tower");

	// 繰り返し詠唱されるコメットのうち、0x257D は最初の1回だけ
	@NpcCastCallout(value = 0x257D, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> comet = ModifiableCallout.durationBasedCall("Comet", "8x Puddles on You");

	private final ModifiableCallout<AbilityCastStart> meteorImpact = ModifiableCallout.durationBasedCall("Meteor Impact", "Hide Behind Boulder");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> meteorImpactSq = SqtTemplates.sq(30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x2582),
			(e1, s) -> {
				Duration wait = e1.getInitialDuration().minus(METEOR_IMPACT_LEAD);
				if (wait.isPositive()) {
					s.waitDuration(wait);
				}
				s.updateCall(meteorImpact, e1);
			});

	// ヨル

	@HandleEvents
	public void yolCasts(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x1F0A, 0x1F0D)) {
			yolFight = true;
		}
	}

	@NpcCastCallout(0x1F0A)
	private final ModifiableCallout<AbilityCastStart> windUnbound = ModifiableCallout.durationBasedCall("Wind Unbound", "Raidwide");

	private final ModifiableCallout<HeadMarkerEvent> flutterfall = new ModifiableCallout<>("Flutterfall", "Spread");

	@HandleEvents
	public void flutterfall(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x17) && event.getTarget().isThePlayer() && yolFight) {
			context.accept(flutterfall.getModified(event));
		}
	}

	@NpcCastCallout(0x1F0D)
	private final ModifiableCallout<AbilityCastStart> eyeOfTheFierce = ModifiableCallout.durationBasedCall("Eye Of The Fierce", "Look Away");

	@PlayerHeadmarker(0x10)
	private final ModifiableCallout<HeadMarkerEvent> wingbeatOnYou = new ModifiableCallout<>("Wingbeat: On You", "Knockback Laser on You");

	private final ModifiableCallout<HeadMarkerEvent> wingbeatAvoid = new ModifiableCallout<>("Wingbeat: On Other", "Avoid Laser");

	@HandleEvents
	public void wingbeatOthers(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x10) && !event.getTarget().isThePlayer()) {
			context.accept(wingbeatAvoid.getModified(event));
		}
	}

}
