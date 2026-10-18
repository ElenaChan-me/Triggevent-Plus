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
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/trial/zeromus.ts をもとに移植
@CalloutRepo(name = "The Abyssal Fracture", duty = KnownDuty.TheAbyssalFracture)
public class Zeromus extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long ACCELERATION_BOMB = 0xA61;

	private XivState state;

	public Zeromus(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheAbyssalFracture);
	}

	private final ModifiableCallout<AbilityUsedEvent> abyssalNox = new ModifiableCallout<>("Abyssal Nox", "Heal to Full");
	private final ModifiableCallout<AbilityUsedEvent> abyssalEchoes = new ModifiableCallout<>("Abyssal Echoes", "Away from Glowing Circles");
	private final ModifiableCallout<AbilityUsedEvent> sableThread = new ModifiableCallout<>("Sable Thread", "5x Line Stack on {event.target}");
	private final ModifiableCallout<AbilityUsedEvent> flareHit = new ModifiableCallout<>("Flare (Hit)", "Out");
	private final RepeatSuppressor noxSupp = new RepeatSuppressor(Duration.ofSeconds(5));
	private final RepeatSuppressor echoesSupp = new RepeatSuppressor(Duration.ofSeconds(5));
	private final RepeatSuppressor flareSupp = new RepeatSuppressor(Duration.ofSeconds(5));

	@HandleEvents
	public void abilityUsed(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x8AF7)) {
			if (noxSupp.check(event)) {
				context.accept(abyssalNox.getModified(event));
			}
		}
		else if (event.abilityIdMatches(0x8AF9)) {
			if (echoesSupp.check(event)) {
				context.accept(abyssalEchoes.getModified(event));
			}
		}
		else if (event.abilityIdMatches(0x8AEF)) {
			if (event.isFirstTarget()) {
				context.accept(sableThread.getModified(event));
			}
		}
		else if (event.abilityIdMatches(0x8B12)) {
			// 塔を踏んだ直後に中央から離れる
			if (flareSupp.check(event)) {
				context.accept(flareHit.getModified(event));
			}
		}
	}

	@NpcCastCallout(0x8AFB)
	private final ModifiableCallout<AbilityCastStart> visceralWhirlNe = ModifiableCallout.durationBasedCall("Visceral Whirl (NE/SW Safe)", "Northeast / Southwest");

	@NpcCastCallout(0x8AFE)
	private final ModifiableCallout<AbilityCastStart> visceralWhirlNw = ModifiableCallout.durationBasedCall("Visceral Whirl (NW/SE Safe)", "Northwest / Southeast");

	private final ModifiableCallout<HeadMarkerEvent> darkMatterOnYou = new ModifiableCallout<>("Dark Matter (On You)", "Buster on You");
	private final ModifiableCallout<HeadMarkerEvent> darkMatter = new ModifiableCallout<>("Dark Matter", "Tankbusters");
	private final ModifiableCallout<HeadMarkerEvent> theDarkBeckons = new ModifiableCallout<>("The Dark Beckons", "Stack on {event.target}");
	private final RepeatSuppressor darkMatterSupp = new RepeatSuppressor(Duration.ofSeconds(2));

	@HandleEvents
	public void headMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x016C)) {
			// 複数のタンクに付くので、自分以外ならまとめて1回だけ呼ぶ
			if (event.getTarget().isThePlayer()) {
				context.accept(darkMatterOnYou.getModified(event));
			}
			else if (darkMatterSupp.check(event)) {
				context.accept(darkMatter.getModified(event));
			}
		}
		else if (event.markerIdMatches(0x0064)) {
			context.accept(theDarkBeckons.getModified(event));
		}
	}

	@PlayerHeadmarker(0x00C5)
	private final ModifiableCallout<HeadMarkerEvent> nox = new ModifiableCallout<>("Nox", "Spread");

	@NpcCastCallout(0x8AF3)
	private final ModifiableCallout<AbilityCastStart> fracturedEventideNe = ModifiableCallout.durationBasedCall("Fractured Eventide (NE Safe)", "Northeast");

	@NpcCastCallout(0x8AF4)
	private final ModifiableCallout<AbilityCastStart> fracturedEventideNw = ModifiableCallout.durationBasedCall("Fractured Eventide (NW Safe)", "Northwest");

	@NpcCastCallout(0x8B12)
	private final ModifiableCallout<AbilityCastStart> flare = ModifiableCallout.durationBasedCall("Flare", "Stand in Tower");

	@NpcCastCallout(0x8B03)
	private final ModifiableCallout<AbilityCastStart> bigBang = ModifiableCallout.durationBasedCall("Big Bang", "Raidwide");

	private final ModifiableCallout<BuffApplied> accelerationBomb = new ModifiableCallout<>("Acceleration Bomb", "Stop Everything");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> accelerationBombSq = SqtTemplates.callWhenDurationIs(BuffApplied.class,
			ba -> ba.buffIdMatches(ACCELERATION_BOMB) && ba.getTarget().isThePlayer(),
			accelerationBomb, Duration.ofSeconds(3));

	@PlayerHeadmarker(0x0178)
	private final ModifiableCallout<HeadMarkerEvent> theDarkDivides = new ModifiableCallout<>("The Dark Divides", "Spread");

	@NpcCastCallout(0x8B04)
	private final ModifiableCallout<AbilityCastStart> bigCrunch = ModifiableCallout.durationBasedCall("Big Crunch", "Raidwide");

	@NpcCastCallout(0x8B1A)
	private final ModifiableCallout<AbilityCastStart> nostalgia = ModifiableCallout.durationBasedCall("Nostalgia", "Big Raidwide");

	@PlayerHeadmarker(0x0017)
	private final ModifiableCallout<HeadMarkerEvent> akhRhai = new ModifiableCallout<>("Akh Rhai", "Spread + Stay Out");

}
