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
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/dungeon/the_grand_cosmos.ts をもとに移植
@CalloutRepo(name = "The Grand Cosmos", duty = KnownDuty.TheGrandCosmos)
public class TheGrandCosmos extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long BLACK_FLAME = 0x19;
	private static final long MORTAL_FLAME = 0xC3;

	private XivState state;

	public TheGrandCosmos(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheGrandCosmos);
	}

	private int firesDomainCount;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		firesDomainCount = 0;
	}

	// 寂寥の求道者

	private final ModifiableCallout<AbilityCastStart> shadowboltOnYou = ModifiableCallout.durationBasedCall("Shadowbolt (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> shadowbolt = ModifiableCallout.durationBasedCall("Shadowbolt", "Buster on {event.target}");

	@HandleEvents
	public void shadowbolt(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x4769)) {
			context.accept((event.getTarget().isThePlayer() ? shadowboltOnYou : shadowbolt).getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> darkPulse = new ModifiableCallout<>("Dark Pulse", "Stack on {event.target}");

	@HandleEvents
	public void darkPulse(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E)) {
			context.accept(darkPulse.getModified(event));
		}
	}

	@PlayerHeadmarker(0x60)
	private final ModifiableCallout<HeadMarkerEvent> darkWellFarWinds = new ModifiableCallout<>("Dark Well / Far Winds", "Spread");

	@NpcCastCallout(0x49A3)
	private final ModifiableCallout<AbilityCastStart> immortalAnathema = ModifiableCallout.durationBasedCall("Immortal Anathema", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> tribulation = new ModifiableCallout<>("Tribulation", "Avoid Brooms");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> tribulationSq = SqtTemplates.sq(15_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x476B),
			(e1, s) -> {
				// 箒が動き出す頃に知らせる
				s.waitMs(8_000);
				s.updateCall(tribulation, e1);
			});

	// リャナンシー

	private final ModifiableCallout<AbilityCastStart> stormOfColorOnYou = ModifiableCallout.durationBasedCall("Storm of Color (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> stormOfColor = ModifiableCallout.durationBasedCall("Storm of Color", "Buster on {event.target}");

	@HandleEvents
	public void stormOfColor(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x471B)) {
			context.accept((event.getTarget().isThePlayer() ? stormOfColorOnYou : stormOfColor).getModified(event));
		}
	}

	@NpcCastCallout(0x471C)
	private final ModifiableCallout<AbilityCastStart> odeToLostLove = ModifiableCallout.durationBasedCall("Ode to Lost Love", "Raidwide");

	@NpcCastCallout(0x471E)
	private final ModifiableCallout<AbilityCastStart> gardenersHymn = ModifiableCallout.durationBasedCall("Gardener's Hymn", "Put Seeds on Dirt");

	// 道中

	private final ModifiableCallout<AbilityCastStart> ronkanCure = ModifiableCallout.durationBasedCall("Ronkan Cure II", "Stun {event.source}");

	@HandleEvents
	public void ronkanCure(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x4931) && state.playerJobMatches(job -> job.isTank() || job.isMeleeDps())) {
			context.accept(ronkanCure.getModified(event));
		}
	}

	// ルゴス

	private final ModifiableCallout<AbilityCastStart> captiveBoltOnYou = ModifiableCallout.durationBasedCall("Captive Bolt (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> captiveBolt = ModifiableCallout.durationBasedCall("Captive Bolt", "Buster on {event.target}");

	@HandleEvents
	public void captiveBolt(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x4764)) {
			context.accept((event.getTarget().isThePlayer() ? captiveBoltOnYou : captiveBolt).getModified(event));
		}
	}

	@NpcCastCallout(0x4765)
	private final ModifiableCallout<AbilityCastStart> cullingBlade = ModifiableCallout.durationBasedCall("Culling Blade", "Raidwide");

	private final ModifiableCallout<HeadMarkerEvent> blackFlameSpread = new ModifiableCallout<>("Black Flame", "Spread");
	private final ModifiableCallout<HeadMarkerEvent> blackFlameDodge = new ModifiableCallout<>("Black Flame: Dodge", "Dodge Crosses");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> blackFlameSq = SqtTemplates.sq(10_000,
			HeadMarkerEvent.class, hme -> hme.markerIdMatches(BLACK_FLAME) && hme.getTarget().isThePlayer(),
			(e1, s) -> {
				s.updateCall(blackFlameSpread, e1);
				s.waitMs(4_000);
				s.updateCall(blackFlameDodge, e1);
			});

	private final ModifiableCallout<HeadMarkerEvent> mortalFlameSpread = new ModifiableCallout<>("Mortal Flame", "Spread");
	private final ModifiableCallout<HeadMarkerEvent> mortalFlameTouch = new ModifiableCallout<>("Mortal Flame: Touch Furniture", "Touch Furniture");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> mortalFlameSq = SqtTemplates.sq(10_000,
			HeadMarkerEvent.class, hme -> hme.markerIdMatches(MORTAL_FLAME) && hme.getTarget().isThePlayer(),
			(e1, s) -> {
				s.updateCall(mortalFlameSpread, e1);
				// 家具に燃え移らせて消すので、散開の後に知らせる
				s.waitMs(5_500);
				s.updateCall(mortalFlameTouch, e1);
			});

	@NpcCastCallout(0x4763)
	private final ModifiableCallout<AbilityCastStart> scorchingLeft = ModifiableCallout.durationBasedCall("Scorching Left", "Right");

	@NpcCastCallout(0x4762)
	private final ModifiableCallout<AbilityCastStart> scorchingRight = ModifiableCallout.durationBasedCall("Scorching Right", "Left");

	private final ModifiableCallout<HeadMarkerEvent> firesDomainFirst = new ModifiableCallout<>("Fire's Domain (First)", "Point Tether Away from Furniture");
	private final ModifiableCallout<HeadMarkerEvent> firesDomain = new ModifiableCallout<>("Fire's Domain", "Tether on You");

	@HandleEvents
	public void firesDomain(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(0x32, 0x33, 0x34, 0x35) || !event.getTarget().isThePlayer()) {
			return;
		}
		firesDomainCount++;
		context.accept((firesDomainCount == 1 ? firesDomainFirst : firesDomain).getModified(event));
	}

}
