package gg.xp.xivsupport.triggers.cactbot.ew;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/raid/p1n.ts をもとに移植
@CalloutRepo(name = "Asphodelos The First Circle", duty = KnownDuty.AsphodelosTheFirstCircle)
public class P1n extends AutoChildEventHandler implements FilteredEventHandler {

	// パワフルライトのスタック値で、光と炎のどちらが安全かが分かる
	private static final long POWERFUL_LIGHT_LIGHT_STACKS = 0x14C;

	private XivState state;

	public P1n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AsphodelosTheFirstCircle);
	}

	// エーテルフレイルの時にも使われる
	@NpcCastCallout(0x6DA2)
	private final ModifiableCallout<AbilityCastStart> gaolersFlailRight = ModifiableCallout.durationBasedCall("Gaoler's Flail (Right)", "Left");

	@NpcCastCallout(0x6DA3)
	private final ModifiableCallout<AbilityCastStart> gaolersFlailLeft = ModifiableCallout.durationBasedCall("Gaoler's Flail (Left)", "Right");

	@NpcCastCallout(0x65F4)
	private final ModifiableCallout<AbilityCastStart> wardersWrath = ModifiableCallout.durationBasedCall("Warder's Wrath", "Raidwide");

	@NpcCastCallout(0x65E9)
	private final ModifiableCallout<AbilityCastStart> shiningCells = ModifiableCallout.durationBasedCall("Shining Cells", "Raidwide");

	@NpcCastCallout(0x65EA)
	private final ModifiableCallout<AbilityCastStart> slamShut = ModifiableCallout.durationBasedCall("Slam Shut", "Raidwide");

	@PlayerHeadmarker(0x01)
	private final ModifiableCallout<HeadMarkerEvent> pitilessFlailKnockback = new ModifiableCallout<>("Pitiless Flail (Knockback)", "Knockback");

	private final ModifiableCallout<HeadMarkerEvent> pitilessFlailStack = new ModifiableCallout<>("Pitiless Flail (Stack)", "Stack on {event.target}");

	@HandleEvents
	public void pitilessFlailStackMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x3E)) {
			context.accept(pitilessFlailStack.getModified(event));
		}
	}

	private final ModifiableCallout<BuffApplied> intemperanceRed = new ModifiableCallout<>("Intemperance (Red)", "Get Hit by Red");
	private final ModifiableCallout<BuffApplied> intemperanceBlue = new ModifiableCallout<>("Intemperance (Blue)", "Get Hit by Blue");

	@HandleEvents
	public void intemperance(EventContext context, BuffApplied event) {
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		if (event.buffIdMatches(0xAB3)) {
			context.accept(intemperanceRed.getModified(event));
		}
		else if (event.buffIdMatches(0xAB4)) {
			context.accept(intemperanceBlue.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> heavyHand = ModifiableCallout.durationBasedCall("Heavy Hand", "Buster on {event.target}");

	// 物理の軽減を持たないキャスターには知らせない
	@HandleEvents
	public void heavyHandCast(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x65F3) && !state.playerJobMatches(job -> job.isCaster())) {
			context.accept(heavyHand.getModified(event));
		}
	}

	private final ModifiableCallout<BuffApplied> powerfulLightFire = new ModifiableCallout<>("Powerful Light (Fire)", "Stand on Fire");
	private final ModifiableCallout<BuffApplied> powerfulLightLight = new ModifiableCallout<>("Powerful Light (Light)", "Stand on Light");

	@HandleEvents
	public void powerfulLight(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x893)) {
			context.accept((event.getRawStacks() == POWERFUL_LIGHT_LIGHT_STACKS ? powerfulLightLight : powerfulLightFire).getModified(event));
		}
	}

}
