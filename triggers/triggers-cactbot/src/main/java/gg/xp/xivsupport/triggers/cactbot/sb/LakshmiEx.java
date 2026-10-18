package gg.xp.xivsupport.triggers.cactbot.sb;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.BuffRemoved;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/trial/lakshmi-ex.ts をもとに移植
@CalloutRepo(name = "Emanation Extreme", duty = KnownDuty.EmanationExtreme)
public class LakshmiEx extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public LakshmiEx(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.EmanationExtreme);
	}

	// チャンチャラ中はヴリトラ化してから処理する必要があるので、コールを切り替える
	private boolean chanchala;

	@HandleEvents
	public void reset(PullStartedEvent unused) {
		chanchala = false;
	}

	@HandleEvents
	public void chanchalaGain(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x2148)) {
			chanchala = true;
		}
	}

	@HandleEvents
	public void chanchalaLose(EventContext context, BuffRemoved event) {
		if (event.buffIdMatches(0x582) && !event.getTarget().isPc()) {
			chanchala = false;
		}
	}

	@NpcCastCallout(0x215E)
	private final ModifiableCallout<AbilityCastStart> pullOfLight = ModifiableCallout.durationBasedCall("Pull of Light", "Buster on {event.target}");

	@NpcCastCallout(0x2149)
	private final ModifiableCallout<AbilityCastStart> divineDenial = ModifiableCallout.durationBasedCall("Divine Denial", "Vrill + Knockback");

	@NpcCastCallout(0x214B)
	private final ModifiableCallout<AbilityCastStart> divineDesire = ModifiableCallout.durationBasedCall("Divine Desire", "Vrill + Be Outside");

	@NpcCastCallout(0x214A)
	private final ModifiableCallout<AbilityCastStart> divineDoubt = ModifiableCallout.durationBasedCall("Divine Doubt", "Vrill + Pair Up");

	private final ModifiableCallout<HeadMarkerEvent> pallOfLightOnYou = new ModifiableCallout<>("Pall of Light: On You", "Stack on You");
	private final ModifiableCallout<HeadMarkerEvent> pallOfLight = new ModifiableCallout<>("Pall of Light: On Other", "Stack");
	private final ModifiableCallout<HeadMarkerEvent> pallOfLightVrillOnYou = new ModifiableCallout<>("Pall of Light: On You (Chanchala)", "Vrill + Stack on You");
	private final ModifiableCallout<HeadMarkerEvent> pallOfLightVrill = new ModifiableCallout<>("Pall of Light: On Other (Chanchala)", "Vrill + Stack");

	@HandleEvents
	public void pallOfLight(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(0x3E)) {
			return;
		}
		boolean onYou = event.getTarget().isThePlayer();
		ModifiableCallout<HeadMarkerEvent> call;
		if (chanchala) {
			call = onYou ? pallOfLightVrillOnYou : pallOfLightVrill;
		}
		else {
			call = onYou ? pallOfLightOnYou : pallOfLight;
		}
		context.accept(call.getModified(event));
	}

	private final ModifiableCallout<AbilityCastStart> stotram = ModifiableCallout.durationBasedCall("Stotram (Chanchala)", "Vrill for AOE");

	@HandleEvents
	public void stotram(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x2147) && chanchala) {
			context.accept(stotram.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> pathOfLight = new ModifiableCallout<>("Path of Light Marker", "Cleave on You");
	private final ModifiableCallout<HeadMarkerEvent> pathOfLightVrill = new ModifiableCallout<>("Path of Light Marker (Chanchala)", "Vrill + Cleave on You");
	private final ModifiableCallout<HeadMarkerEvent> handOfGrace = new ModifiableCallout<>("Hand of Grace", "Cross Marker");
	private final ModifiableCallout<HeadMarkerEvent> handOfGraceVrill = new ModifiableCallout<>("Hand of Grace (Chanchala)", "Vrill + Cross Marker");
	private final ModifiableCallout<HeadMarkerEvent> handOfBeauty = new ModifiableCallout<>("Hand of Beauty", "Flower Marker");
	private final ModifiableCallout<HeadMarkerEvent> handOfBeautyVrill = new ModifiableCallout<>("Hand of Beauty (Chanchala)", "Vrill + Flower Marker");

	@HandleEvents
	public void playerMarkers(EventContext context, HeadMarkerEvent event) {
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		if (event.markerIdMatches(0xE)) {
			context.accept((chanchala ? pathOfLightVrill : pathOfLight).getModified(event));
		}
		else if (event.markerIdMatches(0x6B)) {
			context.accept((chanchala ? handOfGraceVrill : handOfGrace).getModified(event));
		}
		else if (event.markerIdMatches(0x6D)) {
			context.accept((chanchala ? handOfBeautyVrill : handOfBeauty).getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> waterIII = new ModifiableCallout<>("Water III", "Move Away");
	// ソロだと2つ付くことがある
	private final RepeatSuppressor waterIIISupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void waterIII(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x17) && event.getTarget().isThePlayer() && waterIIISupp.check(event)) {
			context.accept(waterIII.getModified(event));
		}
	}

}
