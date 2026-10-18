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
import java.time.Instant;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/dungeon/matoyas_relict.ts をもとに移植
@CalloutRepo(name = "Matoyas Relict", duty = KnownDuty.MatoyasRelict)
public class MatoyasRelict extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long SUCKED_IN = 0x9B6;

	private XivState state;

	public MatoyasRelict(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.MatoyasRelict);
	}

	// マッドマン

	@NpcCastCallout(0x547F)
	private final ModifiableCallout<AbilityCastStart> hardRock = ModifiableCallout.durationBasedCall("Hard Rock", "Buster on {event.target}");

	private final ModifiableCallout<AbilityUsedEvent> peatPelt = new ModifiableCallout<>("Peat Pelt", "Pull Orb to an Empty Hole");
	private final RepeatSuppressor peatPeltSupp = new RepeatSuppressor(Duration.ofSeconds(2));

	@HandleEvents
	public void peatPelt(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x5482) && peatPeltSupp.check(event)) {
			context.accept(peatPelt.getModified(event));
		}
	}

	@NpcCastCallout(0x5491)
	private final ModifiableCallout<AbilityCastStart> stoneAge = ModifiableCallout.durationBasedCall("Stone Age", "Raidwide");

	private final ModifiableCallout<HeadMarkerEvent> fallingRockOnYou = new ModifiableCallout<>("Falling Rock (On You)", "Stack on You");
	private final ModifiableCallout<HeadMarkerEvent> fallingRock = new ModifiableCallout<>("Falling Rock", "Stack on {event.target}");

	@HandleEvents
	public void fallingRock(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E)) {
			context.accept((event.getTarget().isThePlayer() ? fallingRockOnYou : fallingRock).getModified(event));
		}
	}

	@PlayerHeadmarker(0x8B)
	private final ModifiableCallout<HeadMarkerEvent> sputter = new ModifiableCallout<>("Sputter", "Spread");

	// ニクシー

	private final ModifiableCallout<HeadMarkerEvent> crashSmashOnYou = new ModifiableCallout<>("Crash-smash (On You)", "Buster on You");
	private final ModifiableCallout<HeadMarkerEvent> crashSmash = new ModifiableCallout<>("Crash-smash", "Avoid {event.target} and Tethers");

	@HandleEvents
	public void crashSmash(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0xE6)) {
			context.accept((event.getTarget().isThePlayer() ? crashSmashOnYou : crashSmash).getModified(event));
		}
	}

	@NpcCastCallout(0x5991)
	private final ModifiableCallout<AbilityCastStart> showerPower = ModifiableCallout.durationBasedCall("Shower Power", "Avoid Wall Flush");

	private final ModifiableCallout<AbilityUsedEvent> pitterPatter = new ModifiableCallout<>("Pitter-patter", "Step in Puddle near the Cloud", 6_000);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> pitterPatterSq = SqtTemplates.sq(10_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x5988),
			(e1, s) -> {
				s.waitMs(3_000);
				s.updateCall(pitterPatter, e1);
			});

	// マザーポークシー

	@NpcCastCallout(0x5913)
	private final ModifiableCallout<AbilityCastStart> tenderLoin = ModifiableCallout.durationBasedCall("Tender Loin", "Raidwide");

	@NpcCastCallout(0x5919)
	private final ModifiableCallout<AbilityCastStart> huffAndPuff = ModifiableCallout.durationBasedCall("Huff and Puff", "Get Knocked into Safe Spot");

	@NpcCastCallout(0x5916)
	private final ModifiableCallout<AbilityCastStart> meatMallet = ModifiableCallout.durationBasedCall("Meat Mallet", "Go to Opposite Side");

	private final ModifiableCallout<BuffApplied> suckedIn = new ModifiableCallout<BuffApplied>("Sucked In", "Run Away").statusIcon(SUCKED_IN);
	// 効果時間中は何度付与されても繰り返さない
	private Instant suckedInUntil = Instant.MIN;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		suckedInUntil = Instant.MIN;
	}

	@HandleEvents
	public void suckedIn(EventContext context, BuffApplied event) {
		if (!event.buffIdMatches(SUCKED_IN)) {
			return;
		}
		Instant now = event.getEffectiveHappenedAt();
		if (now.isBefore(suckedInUntil)) {
			return;
		}
		suckedInUntil = now.plus(event.getInitialDuration());
		context.accept(suckedIn.getModified(event));
	}

	@NpcCastCallout(0x5911)
	private final ModifiableCallout<AbilityCastStart> mincedMeat = ModifiableCallout.durationBasedCall("Minced Meat", "Buster on {event.target}");

	private final ModifiableCallout<AbilityCastStart> spriteExplosion = new ModifiableCallout<>("Sprite Explosion", "Go to Boss");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> spriteExplosionSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x4E34),
			(e1, s) -> {
				s.waitMs(5_000);
				s.updateCall(spriteExplosion, e1);
			});

	@NpcCastCallout(0x5922)
	private final ModifiableCallout<AbilityCastStart> openFlame = ModifiableCallout.durationBasedCall("Open Flame", "Spread");

}
