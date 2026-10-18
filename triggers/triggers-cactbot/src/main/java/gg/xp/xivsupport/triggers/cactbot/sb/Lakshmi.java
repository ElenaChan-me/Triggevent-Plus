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
import gg.xp.xivsupport.events.actlines.events.BuffRemoved;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.models.XivCombatant;

import java.util.ArrayList;
import java.util.List;

// cactbot（Apache License 2.0）の ui/raidboss/data/04-sb/trial/lakshmi.ts をもとに移植
@CalloutRepo(name = "Emanation", duty = KnownDuty.Emanation)
public class Lakshmi extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long chanchalaId = 0x582;
	private static final long stackMarkerId = 0x3E;

	private XivState state;

	public Lakshmi(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.Emanation);
	}

	private boolean chanchala;
	// 頭割り以外のマーカーが付いた人は頭割りに入ってはいけない
	private final List<XivCombatant> avoidStack = new ArrayList<>();

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		chanchala = false;
		avoidStack.clear();
	}

	@HandleEvents
	public void chanchalaGain(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(chanchalaId)) {
			chanchala = true;
		}
	}

	@HandleEvents
	public void chanchalaLose(EventContext context, BuffRemoved event) {
		if (event.buffIdMatches(chanchalaId)) {
			chanchala = false;
		}
	}

	// 2493 はチャンチャラ中
	@NpcCastCallout({0x2492, 0x2493})
	private final ModifiableCallout<AbilityCastStart> pullOfLight = ModifiableCallout.durationBasedCall("Pull of Light", "Buster on {event.target}");

	@NpcCastCallout(0x249E)
	private final ModifiableCallout<AbilityCastStart> stotram = ModifiableCallout.durationBasedCall("Stotram", "Raidwide");

	// 中間フェーズ。ヴリトラを使わないと即死する
	@NpcAbilityUsedCallout(0x2342)
	private final ModifiableCallout<AbilityUsedEvent> jagadishwari = new ModifiableCallout<>("Jagadishwari", "Use Vril or Die");

	@NpcCastCallout(0x2485)
	private final ModifiableCallout<AbilityCastStart> divineDenial = ModifiableCallout.durationBasedCall("Divine Denial", "Vril, Knockback");

	@HandleEvents
	public void headmarkerCollect(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(stackMarkerId)) {
			avoidStack.add(event.getTarget());
		}
	}

	// マーカーはキャスト完了時に付き、マーカーの組は必ずこれらの詠唱から始まる
	@HandleEvents
	public void headmarkerCleanup(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x2486, 0x2487, 0x2488)) {
			avoidStack.clear();
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> pallOfLightAvoid = new ModifiableCallout<>("Pall of Light (Other Marker)", "Don't Stack");
	private final ModifiableCallout<HeadMarkerEvent> pallOfLightOnYou = new ModifiableCallout<>("Pall of Light (On You)", "Stack on You");
	private final ModifiableCallout<HeadMarkerEvent> pallOfLight = new ModifiableCallout<>("Pall of Light", "Stack on {event.target}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> pallOfLightSq = SqtTemplates.sq(5_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(stackMarkerId),
			(e1, s) -> {
				// 他のマーカーが出揃うのを待つ
				s.waitMs(500);
				if (avoidStack.stream().anyMatch(XivCombatant::isThePlayer)) {
					s.updateCall(pallOfLightAvoid, e1);
				}
				else if (e1.getTarget().isThePlayer()) {
					s.updateCall(pallOfLightOnYou, e1);
				}
				else {
					s.updateCall(pallOfLight, e1);
				}
			});

	private final ModifiableCallout<HeadMarkerEvent> pathOfLightOnYou = new ModifiableCallout<>("Path of Light (On You)", "Tank Cleave on You");
	private final ModifiableCallout<HeadMarkerEvent> pathOfLight = new ModifiableCallout<>("Path of Light", "Avoid Tank Cleave");

	@HandleEvents
	public void pathOfLightMarker(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(0x0E)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(pathOfLightOnYou.getModified(event));
		}
		else {
			context.accept(pathOfLight.getModified(event));
		}
	}

	@PlayerHeadmarker(0x6B)
	private final ModifiableCallout<HeadMarkerEvent> handOfGrace = new ModifiableCallout<>("Hand of Grace", "Cross Marker");

	private final ModifiableCallout<HeadMarkerEvent> handOfBeautyExpanding = new ModifiableCallout<>("Hand of Beauty (Chanchala)", "Expanding Flower Marker");
	private final ModifiableCallout<HeadMarkerEvent> handOfBeauty = new ModifiableCallout<>("Hand of Beauty", "Flower Marker");

	@HandleEvents
	public void handOfBeautyMarker(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(0x6D) || !event.getTarget().isThePlayer()) {
			return;
		}
		context.accept((chanchala ? handOfBeautyExpanding : handOfBeauty).getModified(event));
	}

}
