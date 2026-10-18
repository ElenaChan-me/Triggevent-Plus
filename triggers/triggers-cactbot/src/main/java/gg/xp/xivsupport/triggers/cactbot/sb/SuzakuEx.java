package gg.xp.xivsupport.triggers.cactbot.sb;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/trial/suzaku-ex.ts をもとに移植
@CalloutRepo(name = "Hells Kier Extreme", duty = KnownDuty.HellsKierExtreme)
public class SuzakuEx extends AutoChildEventHandler implements FilteredEventHandler {

	// ファントムフレアは連続ヒットするので、最初の1回だけ拾う
	private static final long PHANTOM_FLURRY_SUPPRESS_MS = 5_000;

	private XivState state;

	public SuzakuEx(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.HellsKierExtreme);
	}

	@NpcCastCallout(0x32D2)
	private final ModifiableCallout<AbilityCastStart> screamsOfTheDamned = ModifiableCallout.durationBasedCall("Screams of the Damned", "Raidwide");

	@PlayerHeadmarker(0x8B)
	private final ModifiableCallout<HeadMarkerEvent> rekindle = new ModifiableCallout<>("Rekindle", "Spread");

	@NpcCastCallout(0x32D1)
	private final ModifiableCallout<AbilityCastStart> cremate = ModifiableCallout.durationBasedCall("Cremate", "Buster on {event.target}");

	@NpcCastCallout(0x32DF)
	private final ModifiableCallout<AbilityCastStart> southronStar = ModifiableCallout.durationBasedCall("Southron Star", "Raidwide");

	// 詠唱は自分対象なので、対象者は出さない
	@NpcCastCallout(0x32DC)
	private final ModifiableCallout<AbilityCastStart> phantomFlurry = ModifiableCallout.durationBasedCall("Phantom Flurry", "Tankbuster");

	@NpcAbilityUsedCallout(value = 0x32DC, suppressMs = PHANTOM_FLURRY_SUPPRESS_MS)
	private final ModifiableCallout<AbilityUsedEvent> phantomFlurryBehind = new ModifiableCallout<>("Phantom Flurry (Follow-up)", "Get Behind");

	private final ModifiableCallout<HeadMarkerEvent> scathingNet = new ModifiableCallout<>("Scathing Net", "Stack on {event.target}");

	@HandleEvents
	public void scathingNet(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0xA1)) {
			context.accept(scathingNet.getModified(event));
		}
	}

	@NpcCastCallout(0x32DA)
	private final ModifiableCallout<AbilityCastStart> mesmerizingMelody = ModifiableCallout.durationBasedCall("Mesmerizing Melody", "Out");

	@NpcCastCallout(0x32DB)
	private final ModifiableCallout<AbilityCastStart> ruthlessRefrain = ModifiableCallout.durationBasedCall("Ruthless Refrain", "In");

}
