package gg.xp.xivsupport.triggers.cactbot.arr;

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
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.models.XivCombatant;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/raid/t13.ts をもとに移植
@CalloutRepo(name = "The Final Coil Of Bahamut Turn4", duty = KnownDuty.TheFinalCoilOfBahamutTurn4)
public class T13 extends AutoChildEventHandler implements FilteredEventHandler {

	// 2回目と3回目のギガフレアだけがダイブフェーズへの移行
	private static final int FIRST_DIVE_GIGAFLARE = 1;
	private static final int LAST_DIVE_GIGAFLARE = 2;

	private XivState state;

	public T13(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheFinalCoilOfBahamutTurn4);
	}

	private final ModifiableCallout<AbilityCastStart> gigaflareDives = ModifiableCallout.durationBasedCall("Gigaflare (Phase Change)", "Stack Center for Dives");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> gigaflareSq = SqtTemplates.selfManagedMultiInvocation(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0xBB9),
			(e1, s, index) -> {
				if (index >= FIRST_DIVE_GIGAFLARE && index <= LAST_DIVE_GIGAFLARE) {
					s.updateCall(gigaflareDives, e1);
				}
			});

	private final ModifiableCallout<AbilityCastStart> flattenOnYou = ModifiableCallout.durationBasedCall("Flatten on You", "Flatten on You");
	private final ModifiableCallout<AbilityCastStart> flattenOnOther = ModifiableCallout.durationBasedCall("Flatten on Other", "Flatten on {event.target}");
	private final ModifiableCallout<AbilityCastStart> akhMornOnYou = ModifiableCallout.durationBasedCall("Akh Morn on You", "Akh Morn on You");
	private final ModifiableCallout<AbilityCastStart> akhMornOnOther = ModifiableCallout.durationBasedCall("Akh Morn on Other", "Akh Morn on {event.target}");

	@HandleEvents
	public void targetedCasts(EventContext context, AbilityCastStart event) {
		if (event.getSource().isPc()) {
			return;
		}
		boolean onYou = event.getTarget().isThePlayer();
		if (event.abilityIdMatches(0xBAE)) {
			context.accept((onYou ? flattenOnYou : flattenOnOther).getModified(event));
		}
		else if (event.abilityIdMatches(0xBC2)) {
			context.accept((onYou ? akhMornOnYou : akhMornOnOther).getModified(event));
		}
	}

	@PlayerHeadmarker(0x27)
	private final ModifiableCallout<HeadMarkerEvent> megaflareShare = new ModifiableCallout<>("Megaflare Share", "Megaflare Stack");

	@PlayerHeadmarker(0x28)
	private final ModifiableCallout<HeadMarkerEvent> earthshaker = new ModifiableCallout<>("Earthshaker", "Earthshaker on You");

	private final ModifiableCallout<TetherEvent> tempestWing = new ModifiableCallout<>("Tempest Wing", "Tempest Tether on You");

	@HandleEvents
	public void tempestWingTether(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x4)
		    && event.eitherTargetMatches(XivCombatant::isThePlayer)
		    && event.eitherTargetMatches(cbt -> !cbt.isPc())) {
			context.accept(tempestWing.getModified(event));
		}
	}

}
