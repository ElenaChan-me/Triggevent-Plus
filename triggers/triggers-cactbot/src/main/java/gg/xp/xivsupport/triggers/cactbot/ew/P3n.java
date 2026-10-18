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
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.models.ArenaPos;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/raid/p3n.ts をもとに移植
@CalloutRepo(name = "Asphodelos The Third Circle", duty = KnownDuty.AsphodelosTheThirdCircle)
public class P3n extends AutoChildEventHandler implements FilteredEventHandler {

	// 両タンクへの詠唱が揃うまで待つ時間
	private static final int COLLECT_MS = 300;
	private static final int MAX_TANKS = 2;
	// アッシュアイは2本目で安置が決まり、4本で1セットとして数え直す
	private static final int ASHEN_EYE_CALL_COUNT = 2;
	private static final int ASHEN_EYE_RESET_COUNT = 3;

	private XivState state;

	public P3n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AsphodelosTheThirdCircle);
	}

	private final List<ArenaSector> ashenEyeDirections = new ArrayList<>();

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		ashenEyeDirections.clear();
	}

	@NpcCastCallout(0x6698)
	private final ModifiableCallout<AbilityCastStart> fireplumeRotating = ModifiableCallout.durationBasedCall("Experimental Fireplume (Rotating)", "Start Middle");

	// 本命のファイアプルームまで約11秒あるので、早めに知らせて少し残す
	@NpcCastCallout(0x6696)
	private final ModifiableCallout<AbilityCastStart> fireplumeOut = new ModifiableCallout<>("Experimental Fireplume (Out)", "Out of Middle Soon", 8_000);

	@NpcCastCallout(0x66B8)
	private final ModifiableCallout<AbilityCastStart> scorchedExaltation = ModifiableCallout.durationBasedCall("Scorched Exaltation", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> heatOfCondemnationOnYou = ModifiableCallout.durationBasedCall("Heat of Condemnation (On You)", "Tank Cleave on You");
	private final ModifiableCallout<AbilityCastStart> heatOfCondemnationAvoid = ModifiableCallout.durationBasedCall("Heat of Condemnation (Not on You)", "Avoid Tank Cleaves");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> heatOfCondemnationSq = SqtTemplates.sq(5_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x66B2),
			(e1, s) -> {
				// 両タンクに同時に詠唱が出るので、自分が含まれるかどうかで分ける
				List<AbilityCastStart> casts = new ArrayList<>();
				casts.add(e1);
				casts.addAll(s.waitEventsUntil(MAX_TANKS - 1,
						AbilityCastStart.class, acs -> acs.abilityIdMatches(0x66B2),
						BaseEvent.class, e -> e1.getEffectiveTimeSince().toMillis() > COLLECT_MS));
				boolean onPlayer = casts.stream().anyMatch(acs -> acs.getTarget().isThePlayer());
				s.updateCall(onPlayer ? heatOfCondemnationOnYou : heatOfCondemnationAvoid, e1);
			});

	@PlayerHeadmarker({0x010C, 0x010D, 0x010E, 0x010F})
	private final ModifiableCallout<HeadMarkerEvent> darkenedFire = new ModifiableCallout<>("Darkened Fire", "Stand on Darkened Fire");

	@NpcCastCallout(0x66B4)
	private final ModifiableCallout<AbilityCastStart> rightCinderwing = ModifiableCallout.durationBasedCall("Right Cinderwing", "Left");

	@NpcCastCallout(0x66B5)
	private final ModifiableCallout<AbilityCastStart> leftCinderwing = ModifiableCallout.durationBasedCall("Left Cinderwing", "Right");

	@NpcCastCallout(0x66AD)
	private final ModifiableCallout<AbilityCastStart> trailOfCondemnation = ModifiableCallout.durationBasedCall("Trail of Condemnation", "Avoid Charge");

	@NpcCastCallout(0x66A9)
	private final ModifiableCallout<AbilityCastStart> deadRebirth = ModifiableCallout.durationBasedCall("Dead Rebirth", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> ashenEye = ModifiableCallout.durationBasedCall("Ashen Eye", "{first} => {second}");

	@HandleEvents
	public void ashenEye(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x66AB)) {
			return;
		}
		Position pos = event.getSource().getPos();
		if (pos == null) {
			return;
		}
		ashenEyeDirections.add(ArenaPos.combatantFacing(pos.getHeading()));
		if (ashenEyeDirections.size() == ASHEN_EYE_CALL_COUNT) {
			// 1本目の向いている方向が後半の安置、2本目の背後が先に安全
			ArenaSector second = ashenEyeDirections.get(0);
			ArenaSector first = ashenEyeDirections.get(1).opposite();
			context.accept(ashenEye.getModified(event, Map.of("first", first, "second", second)));
		}
		else if (ashenEyeDirections.size() > ASHEN_EYE_RESET_COUNT) {
			ashenEyeDirections.clear();
		}
	}

	@NpcCastCallout(0x669E)
	private final ModifiableCallout<AbilityCastStart> devouringBrand = ModifiableCallout.durationBasedCall("Devouring Brand", "Split Intercardinals");

	@PlayerHeadmarker(0x008B)
	private final ModifiableCallout<HeadMarkerEvent> spreadMarker = new ModifiableCallout<>("Spread Marker", "Spread");

}
