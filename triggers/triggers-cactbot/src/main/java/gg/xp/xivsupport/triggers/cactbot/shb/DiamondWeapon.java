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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/trial/diamond_weapon.ts をもとに移植
@CalloutRepo(name = "The Cloud Deck", duty = KnownDuty.TheCloudDeck)
public class DiamondWeapon extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public DiamondWeapon(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheCloudDeck);
	}

	@NpcCastCallout(0x5FA7)
	private final ModifiableCallout<AbilityCastStart> diamondRain = ModifiableCallout.durationBasedCall("Diamond Rain", "Raidwide");

	@NpcCastCallout(0x5F9A)
	private final ModifiableCallout<AbilityCastStart> clawSwipeEast = ModifiableCallout.durationBasedCall("Claw Swipe (East)", "Teleport to West Platform");

	@NpcCastCallout(0x5F9B)
	private final ModifiableCallout<AbilityCastStart> clawSwipeWest = ModifiableCallout.durationBasedCall("Claw Swipe (West)", "Teleport to East Platform");

	@PlayerHeadmarker(0x57)
	private final ModifiableCallout<HeadMarkerEvent> photonBurst = new ModifiableCallout<>("Photon Burst", "Flare on You");

	private final ModifiableCallout<AbilityUsedEvent> diamondFlash = new ModifiableCallout<>("Diamond Flash", "Stack on {event.target}", 5_000);

	@HandleEvents
	public void diamondFlash(EventContext context, AbilityUsedEvent event) {
		// 頭上マーカーが無く、この技で頭割りの対象が示される
		if (event.abilityIdMatches(0x5779)) {
			context.accept(diamondFlash.getModified(event));
		}
	}

	@NpcCastCallout(0x5FE6)
	private final ModifiableCallout<AbilityCastStart> auriCyclone = ModifiableCallout.durationBasedCall("Auri Cyclone", "Knockback");

	@NpcCastCallout(0x5FD7)
	private final ModifiableCallout<AbilityCastStart> outrage = ModifiableCallout.durationBasedCall("Outrage", "Raidwide");

	@NpcCastCallout(0x5FD8)
	private final ModifiableCallout<AbilityCastStart> auriDoomstead = ModifiableCallout.durationBasedCall("Auri Doomstead", "Buster on {event.target}");

	@NpcCastCallout(0x5FE5)
	private final ModifiableCallout<AbilityCastStart> verticalCleave = ModifiableCallout.durationBasedCall("Vertical Cleave", "Knockback");

	@PlayerHeadmarker(0xC5)
	private final ModifiableCallout<HeadMarkerEvent> diamondShrapnel = new ModifiableCallout<>("Diamond Shrapnel", "Diamond Shrapnel on You", 7_000);

	private final ModifiableCallout<AbilityUsedEvent> articulatedBits = new ModifiableCallout<>("Articulated Bits", "Avoid Bits");

	@HandleEvents
	public void articulatedBits(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x5FA9) && event.isFirstTarget()) {
			context.accept(articulatedBits.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> adamantSphere = new ModifiableCallout<>("Adamant Sphere", "Get Towers", 8_000);

	@HandleEvents
	public void adamantSphere(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x6144) && event.isFirstTarget()) {
			context.accept(adamantSphere.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> homingLaser = new ModifiableCallout<>("Homing Laser", "Spread", 7_000);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> homingLaserSq = SqtTemplates.sq(10_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x5F9C) && aue.isFirstTarget(),
			(e1, s) -> {
				// 頭上マーカーより10秒ほど早く使われるので、少し待ってから呼ぶ
				s.waitMs(3_000);
				s.updateCall(homingLaser, e1);
			});

}
