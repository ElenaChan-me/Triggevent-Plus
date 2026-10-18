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
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/alliance/royal_city_of_rabanastre.ts をもとに移植
@CalloutRepo(name = "The Royal City Of Rabanastre", duty = KnownDuty.TheRoyalCityOfRabanastre)
public class RoyalCityOfRabanastre extends AutoChildEventHandler implements FilteredEventHandler {

	private static final int BREATHLESS_BUBBLE_STACKS = 6;

	private XivState state;

	public RoyalCityOfRabanastre(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheRoyalCityOfRabanastre);
	}

	private boolean maskOfTruth;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		maskOfTruth = false;
	}

	// マテウス

	private final ModifiableCallout<AbilityCastStart> aquaSphere = new ModifiableCallout<>("Aqua Sphere", "Kill Aqua Spheres");

	// 詠唱開始時点では玉がまだ出ていないので、出現する頃まで待つ
	@AutoFeed
	private final SequentialTrigger<BaseEvent> aquaSphereSq = SqtTemplates.sq(20_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x2633),
			(e1, s) -> {
				s.waitMs(11_000);
				s.updateCall(aquaSphere, e1);
			});

	private final ModifiableCallout<BuffApplied> breathlessBubble = new ModifiableCallout<BuffApplied>("Breathless (6 Stacks)", "Get in Bubble").statusIcon(0x595);
	private final ModifiableCallout<BuffApplied> breathless = new ModifiableCallout<BuffApplied>("Breathless (7+ Stacks)", "Breathless: {num}").statusIcon(0x595);

	@HandleEvents
	public void breathlessBuff(EventContext context, BuffApplied event) {
		if (!event.buffIdMatches(0x595) || !event.getTarget().isThePlayer()) {
			return;
		}
		long stacks = event.getStacks();
		if (stacks == BREATHLESS_BUBBLE_STACKS) {
			context.accept(breathlessBubble.getModified(event));
		}
		else if (stacks > BREATHLESS_BUBBLE_STACKS) {
			context.accept(breathless.getModified(event, Map.of("num", stacks)));
		}
	}

	@NpcCastCallout(0x263D)
	private final ModifiableCallout<AbilityCastStart> blizzard4 = ModifiableCallout.durationBasedCall("Blizzard IV", "Move to Safe Spot");

	// ハシュマリム

	@NpcCastCallout(0x25D8)
	private final ModifiableCallout<AbilityCastStart> quake4 = ModifiableCallout.durationBasedCall("Quake IV", "Raidwide");

	@NpcCastCallout(0x25D0)
	private final ModifiableCallout<AbilityCastStart> extremeEdgeLeft = ModifiableCallout.durationBasedCall("Extreme Edge (Left)", "Find Boss, Dodge Left");

	@NpcCastCallout(0x25CE)
	private final ModifiableCallout<AbilityCastStart> extremeEdgeRight = ModifiableCallout.durationBasedCall("Extreme Edge (Right)", "Find Boss, Dodge Right");

	@NpcCastCallout(0x25D7)
	private final ModifiableCallout<AbilityCastStart> rockCutter = ModifiableCallout.durationBasedCall("Rock Cutter", "Tank Cleave");

	// 3つ同時に詠唱されるのでまとめて頭割りとだけ伝える
	@NpcCastCallout(value = 0x25D2, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> fallingBoulder = ModifiableCallout.durationBasedCall("Falling Boulder", "Stack");

	@NpcCastCallout(value = 0x25D3, onYou = true)
	private final ModifiableCallout<AbilityCastStart> fallingRock = ModifiableCallout.durationBasedCall("Falling Rock", "Spread");

	@NpcCastCallout(0x25CB)
	private final ModifiableCallout<AbilityCastStart> earthHammer = ModifiableCallout.durationBasedCall("Earth Hammer", "Move Away");

	private final ModifiableCallout<AbilityCastStart> golems = new ModifiableCallout<>("Golems", "Kill Golems");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> golemsSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x25D4),
			(e1, s) -> {
				s.waitMs(5_000);
				s.updateCall(golems, e1);
			});

	// 道中

	@NpcCastCallout(0xD10)
	private final ModifiableCallout<AbilityCastStart> dragonVoice = ModifiableCallout.durationBasedCall("Dragon Voice (Archaeolion)", "Get Under");

	@NpcCastCallout({0xD0F, 0x273B})
	private final ModifiableCallout<AbilityCastStart> ramVoice = ModifiableCallout.durationBasedCall("Ram Voice (Archaeolion)", "Out");

	// ロフォカレ

	private final ModifiableCallout<AbilityUsedEvent> crushHelm = new ModifiableCallout<>("Crush Helm", "Buster on {event.target}");
	// 詠唱なしの連続ヒットなので最初の1発だけ読み上げる
	private final RepeatSuppressor crushHelmSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	@HandleEvents
	public void crushHelmHit(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x2681) && crushHelmSupp.check(event)) {
			context.accept(crushHelm.getModified(event));
		}
	}

	@PlayerHeadmarker(0x17)
	private final ModifiableCallout<HeadMarkerEvent> chariot = new ModifiableCallout<>("Chariot", "In");

	@NpcCastCallout(0x2676)
	private final ModifiableCallout<AbilityCastStart> trample = ModifiableCallout.durationBasedCall("Trample", "Trample");

	// アルガス

	@NpcCastCallout(0x262E)
	private final ModifiableCallout<AbilityCastStart> fire4 = ModifiableCallout.durationBasedCall("Fire IV", "Raidwide");

	@NpcCastCallout(0x262D)
	private final ModifiableCallout<AbilityCastStart> cripplingBlow = ModifiableCallout.durationBasedCall("Crippling Blow", "Buster on {event.target}");

	private final ModifiableCallout<AbilityUsedEvent> trepidation = new ModifiableCallout<>("Trepidation", "Get Towers");

	@HandleEvents
	public void argathAbilities(EventContext context, AbilityUsedEvent event) {
		if (!event.isFirstTarget()) {
			return;
		}
		if (event.abilityIdMatches(0x2622)) {
			context.accept(trepidation.getModified(event));
		}
		else if (event.abilityIdMatches(0x261A)) {
			maskOfTruth = true;
		}
		else if (event.abilityIdMatches(0x2619)) {
			maskOfTruth = false;
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> commandScatterMove = new ModifiableCallout<>("Command Scatter (Mask of Truth)", "Keep Moving");
	private final ModifiableCallout<HeadMarkerEvent> commandScatterStop = new ModifiableCallout<>("Command Scatter (Mask of Lies)", "Stop Everything");
	private final ModifiableCallout<HeadMarkerEvent> commandTurnAway = new ModifiableCallout<>("Command Turn (Mask of Truth)", "Look Away");
	private final ModifiableCallout<HeadMarkerEvent> commandTurnTowards = new ModifiableCallout<>("Command Turn (Mask of Lies)", "Look Towards Boss");

	@HandleEvents
	public void commandMarkers(EventContext context, HeadMarkerEvent event) {
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		if (event.markerIdMatches(0x7B)) {
			context.accept((maskOfTruth ? commandScatterMove : commandScatterStop).getModified(event));
		}
		else if (event.markerIdMatches(0x7C)) {
			context.accept((maskOfTruth ? commandTurnAway : commandTurnTowards).getModified(event));
		}
	}

}
