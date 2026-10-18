package gg.xp.xivsupport.triggers.cactbot.arr;

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
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;

import java.util.ArrayList;
import java.util.List;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/trial/ultima-ex.ts をもとに移植
@CalloutRepo(name = "The Minstrels Ballad Ultimas Bane", duty = KnownDuty.TheMinstrelsBalladUltimasBane)
public class UltimaEx extends AutoChildEventHandler implements FilteredEventHandler {

	// 5スタックで大ダメージになるので、4スタックの時点でスイッチを促す
	private static final long VISCOUS_SWAP_STACKS = 4;
	private static final int PLASM_COLLECT_MS = 500;
	private static final int PLASM_SUPPRESS_MS = 5_000;
	private static final int PLASM_MAX_TARGETS = 8;

	private XivState state;

	public UltimaEx(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheMinstrelsBalladUltimasBane);
	}

	@NpcCastCallout(0x5EA)
	private final ModifiableCallout<AbilityCastStart> tankPurge = ModifiableCallout.durationBasedCall("Tank Purge", "Big Raidwide");

	private final ModifiableCallout<BuffApplied> viscousTankSwap = new ModifiableCallout<BuffApplied>("Viscous Aetheroplasm (4 Stacks)", "Tank Swap")
			.statusIcon(0x171, VISCOUS_SWAP_STACKS);

	@HandleEvents
	public void viscousAetheroplasm(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x171)
		    && event.getStacks() == VISCOUS_SWAP_STACKS
		    && state.playerJobMatches(job -> job.isTank() || job == Job.BLU)) {
			context.accept(viscousTankSwap.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> homingPlasmOnYou = new ModifiableCallout<>("Homing Aetheroplasm on You", "Homing Aetheroplasm on You");
	private final ModifiableCallout<AbilityUsedEvent> homingPlasmAvoid = new ModifiableCallout<>("Homing Aetheroplasm on Others", "Avoid Homing Aetheroplasm");

	// ヘッドマーカーの代わりにこのアビリティで対象が分かるので、少しの間まとめて集める
	@AutoFeed
	private final SequentialTrigger<BaseEvent> homingAetheroplasmSq = SqtTemplates.sq(PLASM_COLLECT_MS + PLASM_SUPPRESS_MS + 5_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x672),
			(e1, s) -> {
				List<AbilityUsedEvent> hits = new ArrayList<>();
				hits.add(e1);
				hits.addAll(s.waitEventsUntil(PLASM_MAX_TARGETS - 1,
						AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x672),
						BaseEvent.class, be -> e1.getEffectiveTimeSince().toMillis() >= PLASM_COLLECT_MS));
				boolean onYou = hits.stream().anyMatch(hit -> hit.getTarget().isThePlayer());
				s.updateCall(onYou ? homingPlasmOnYou : homingPlasmAvoid, e1);
				// 連続通知を抑えるため、しばらくこのトリガーを占有しておく
				s.waitMs(PLASM_SUPPRESS_MS);
			});

	private final ModifiableCallout<AbilityCastStart> boomCardinals = ModifiableCallout.durationBasedCall("Aetheric Boom 1: Orbs", "Orbs: Cardinals");
	private final ModifiableCallout<AbilityCastStart> boomCardinalsNsFirst = ModifiableCallout.durationBasedCall("Aetheric Boom 2: Orbs", "Orbs: Cardinals (N/S first)");
	private final ModifiableCallout<AbilityCastStart> boomIntercards = ModifiableCallout.durationBasedCall("Aetheric Boom 3: Orbs", "Orbs: Intercardinals");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> aethericBoomSq = SqtTemplates.multiInvocation(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x5E7),
			(e1, s) -> s.updateCall(boomCardinals, e1),
			(e1, s) -> s.updateCall(boomCardinalsNsFirst, e1),
			(e1, s) -> s.updateCall(boomIntercards, e1));

	@NpcCastCallout(0x5E7)
	private final ModifiableCallout<AbilityCastStart> aethericBoomKnockback = ModifiableCallout.durationBasedCall("Aetheric Boom: Knockback", "Knockback");

}
