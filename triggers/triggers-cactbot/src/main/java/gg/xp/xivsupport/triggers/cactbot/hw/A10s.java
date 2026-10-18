package gg.xp.xivsupport.triggers.cactbot.hw;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.Job;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/raid/a10s.ts をもとに移植
@CalloutRepo(name = "Alexander The Breath Of The Creator Savage", duty = KnownDuty.AlexanderTheBreathOfTheCreatorSavage)
public class A10s extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long brighteyesId = 0x1AA9;
	private static final long preyMarkerId = 0x29;
	private static final Duration brighteyesWindow = Duration.ofSeconds(20);

	private XivState state;

	public A10s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AlexanderTheBreathOfTheCreatorSavage);
	}

	private final Deque<Long> charges = new ArrayDeque<>();
	private Instant lastBrighteyes;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		charges.clear();
		lastBrighteyes = null;
	}

	@NpcAbilityUsedCallout(value = 0x1AB2, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> floorSpikeTrap = new ModifiableCallout<>("Floor Spike Trap", "Floor Spikes");

	@NpcAbilityUsedCallout(value = 0x1AB1, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> frostLaserTrap = new ModifiableCallout<>("Frost Laser Trap", "Frost Lasers");

	@NpcAbilityUsedCallout(value = 0x1AB0, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> ceilingWeightTrap = new ModifiableCallout<>("Ceiling Weight Trap", "Ceiling Weight");

	private final ModifiableCallout<AbilityUsedEvent> chargeMarkerIn = new ModifiableCallout<>("Charge Marker: In", "In");
	private final ModifiableCallout<AbilityUsedEvent> chargeMarkerOut = new ModifiableCallout<>("Charge Marker: Out", "Out");
	private final ModifiableCallout<AbilityUsedEvent> chargeMarkerSpread = new ModifiableCallout<>("Charge Marker: Spread", "Spread");
	private final ModifiableCallout<AbilityUsedEvent> chargeMarkerStack = new ModifiableCallout<>("Charge Marker: Stack", "Stack");

	private final ModifiableCallout<AbilityUsedEvent> chargeNextIn = new ModifiableCallout<>("Charge Followup: In", "In");
	private final ModifiableCallout<AbilityUsedEvent> chargeNextOut = new ModifiableCallout<>("Charge Followup: Out", "Out");
	private final ModifiableCallout<AbilityUsedEvent> chargeNextSpread = new ModifiableCallout<>("Charge Followup: Spread", "Spread");
	private final ModifiableCallout<AbilityUsedEvent> chargeNextStack = new ModifiableCallout<>("Charge Followup: Stack", "Stack");

	private final RepeatSuppressor chargeFollowupSupp = new RepeatSuppressor(Duration.ofMillis(500));

	// 1AB8～1ABB はそれぞれ In / Out / 散開 / 頭割り の予告
	private static ModifiableCallout<AbilityUsedEvent> pick(long markerId,
	                                                       ModifiableCallout<AbilityUsedEvent> in,
	                                                       ModifiableCallout<AbilityUsedEvent> out,
	                                                       ModifiableCallout<AbilityUsedEvent> spread,
	                                                       ModifiableCallout<AbilityUsedEvent> stack) {
		return switch ((int) markerId) {
			case 0x1AB8 -> in;
			case 0x1AB9 -> out;
			case 0x1ABA -> spread;
			case 0x1ABB -> stack;
			default -> null;
		};
	}

	@HandleEvents
	public void chargeAbilities(EventContext context, AbilityUsedEvent event) {
		if (event.getSource().isPc() || !event.isFirstTarget()) {
			return;
		}
		if (event.abilityIdMatches(0x1AB8, 0x1AB9, 0x1ABA, 0x1ABB)) {
			long id = event.getAbility().getId();
			charges.add(id);
			ModifiableCallout<AbilityUsedEvent> call = pick(id, chargeMarkerIn, chargeMarkerOut, chargeMarkerSpread, chargeMarkerStack);
			if (call != null) {
				context.accept(call.getModified(event));
			}
		}
		else if (event.abilityIdMatches(0x1A97, 0x1A98, 0x1A99)) {
			// 1回目の突進。予告時に読み上げ済みなので消化するだけ
			charges.poll();
		}
		else if (event.abilityIdMatches(0x1A9A, 0x1A9B, 0x1A9C, 0x1A9E) && chargeFollowupSupp.check(event)) {
			Long next = charges.poll();
			if (next == null) {
				return;
			}
			ModifiableCallout<AbilityUsedEvent> call = pick(next, chargeNextIn, chargeNextOut, chargeNextSpread, chargeNextStack);
			if (call != null) {
				context.accept(call.getModified(event));
			}
		}
	}

	@NpcCastCallout(0x1A9F)
	private final ModifiableCallout<AbilityCastStart> gobrushRushgob = ModifiableCallout.durationBasedCall("Gobrush Rushgob", "Buster on {event.target}");

	private final ModifiableCallout<TetherEvent> slicetopsOnYou = new ModifiableCallout<>("Slicetops (On You)", "Tank Swap, Get Away");
	private final ModifiableCallout<TetherEvent> slicetopsTank = new ModifiableCallout<>("Slicetops (Other Tank)", "Tank Swap");
	private final ModifiableCallout<TetherEvent> slicetopsHealer = new ModifiableCallout<>("Slicetops (Healer)", "Shield {target}");

	@HandleEvents
	public void slicetopsTether(EventContext context, TetherEvent event) {
		if (!event.tetherIdMatches(0x39)) {
			return;
		}
		XivCombatant target = event.getTargetMatching(XivCombatant::isPc);
		if (target == null) {
			return;
		}
		if (target.isThePlayer()) {
			context.accept(slicetopsOnYou.getModified(event));
		}
		else if (state.playerJobMatches(Job::isTank)) {
			context.accept(slicetopsTank.getModified(event));
		}
		else if (state.playerJobMatches(job -> job.isHealer() || job == Job.BLU)) {
			context.accept(slicetopsHealer.getModified(event, Map.of("target", target)));
		}
	}

	@NpcCastCallout(0x1AA4)
	private final ModifiableCallout<AbilityCastStart> gobsnickLeghops = ModifiableCallout.durationBasedCall("Gobsnick Leghops", "Stop Everything");

	@HandleEvents
	public void brighteyesTracker(EventContext context, AbilityUsedEvent event) {
		if (!event.abilityIdMatches(brighteyesId)) {
			return;
		}
		// 最初の Brighteyes から20秒間をひとまとまりとして扱う
		Instant now = event.getHappenedAt();
		if (lastBrighteyes == null || Duration.between(lastBrighteyes, now).compareTo(brighteyesWindow) >= 0) {
			lastBrighteyes = now;
		}
	}

	@PlayerHeadmarker(preyMarkerId)
	private final ModifiableCallout<HeadMarkerEvent> preyMarker = new ModifiableCallout<>("Brighteyes Prey", "Prey on You");

	private final ModifiableCallout<HeadMarkerEvent> passPrey = new ModifiableCallout<>("Brighteyes Prey (Pass)", "Pass Prey");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> passPreySq = SqtTemplates.sq(10_000,
			HeadMarkerEvent.class, hm -> hm.markerIdMatches(preyMarkerId) && hm.getTarget().isThePlayer(),
			(e1, s) -> {
				Instant brighteyes = lastBrighteyes;
				boolean passedToMe = brighteyes != null
				                     && Duration.between(brighteyes, e1.getHappenedAt()).compareTo(brighteyesWindow) < 0;
				// 受け渡しが必要なのは最初のマーカーだけ
				if (passedToMe) {
					return;
				}
				s.waitMs(5_000);
				s.updateCall(passPrey, e1);
			});

	@NpcCastCallout(0x1A92)
	private final ModifiableCallout<AbilityCastStart> gobsliceMooncrops = ModifiableCallout.durationBasedCall("Gobslice Mooncrops", "Hit Floor Trap");

	@NpcCastCallout(0x1A8F)
	private final ModifiableCallout<AbilityCastStart> gobspinZoomdrops = ModifiableCallout.durationBasedCall("Gobspin Zoomdrops", "Out");

	@NpcAbilityUsedCallout(value = 0x1A8F, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> gobspinZoomdropsAfter = new ModifiableCallout<>("Gobspin Zoomdrops (After)", "Hit Boss With Ice");

}
