package gg.xp.xivsupport.triggers.cactbot.ew;

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
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.models.XivCombatant;

import java.util.ArrayList;
import java.util.List;

// cactbot（Apache License 2.0）の ui/raidboss/data/06-ew/trial/ultima-un.ts をもとに移植
@CalloutRepo(name = "Ultimas Bane Unreal", duty = KnownDuty.UltimasBaneUnreal)
public class UltimaUn extends AutoChildEventHandler implements FilteredEventHandler {

	// 5スタックで被ダメージが跳ね上がるので、4スタックの時点で交代する
	private static final long VISCOUS_SWAP_STACKS = 4;

	private XivState state;

	public UltimaUn(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.UltimasBaneUnreal);
	}

	private final List<XivCombatant> plasmTargets = new ArrayList<>();
	private int boomCounter;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		plasmTargets.clear();
		boomCounter = 0;
	}

	@NpcCastCallout(0x6EF9)
	private final ModifiableCallout<AbilityCastStart> tankPurge = ModifiableCallout.durationBasedCall("Tank Purge", "Big Raidwide");

	private final ModifiableCallout<BuffApplied> viscousAetheroplasm = new ModifiableCallout<>("Viscous Aetheroplasm", "Tank Swap");

	@HandleEvents
	public void viscousAetheroplasmStacks(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x171) && event.getStacks() == VISCOUS_SWAP_STACKS && state.playerJobMatches(Job::isTank)) {
			context.accept(viscousAetheroplasm.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> homingAetheroplasmOnYou = new ModifiableCallout<>("Homing Aetheroplasm (On You)", "Homing Aetheroplasm on You");
	private final ModifiableCallout<AbilityUsedEvent> homingAetheroplasmAvoid = new ModifiableCallout<>("Homing Aetheroplasm (Avoid)", "Avoid Homing Aetheroplasm");

	// 頭上マーカーの代わりにこのアビリティで対象が分かる
	@HandleEvents
	public void homingAetheroplasmCollect(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x6F07)) {
			plasmTargets.add(event.getTarget());
		}
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> homingAetheroplasmSq = SqtTemplates.sq(10_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x6F07),
			(e1, s) -> {
				s.waitMs(500);
				boolean onMe = plasmTargets.stream().anyMatch(XivCombatant::isThePlayer);
				s.updateCall(onMe ? homingAetheroplasmOnYou : homingAetheroplasmAvoid, e1);
				s.waitMs(4_500);
				plasmTargets.clear();
			});

	private final ModifiableCallout<AbilityCastStart> aethericBoomFirst = ModifiableCallout.durationBasedCall("Aetheric Boom (First Orbs)", "Orbs: Cardinals");
	private final ModifiableCallout<AbilityCastStart> aethericBoomSecond = ModifiableCallout.durationBasedCall("Aetheric Boom (Second Orbs)", "Orbs: Cardinals (N/S First)");
	private final ModifiableCallout<AbilityCastStart> aethericBoomThird = ModifiableCallout.durationBasedCall("Aetheric Boom (Third Orbs)", "Orbs: Intercardinals");
	private final ModifiableCallout<AbilityCastStart> aethericBoomKnockback = ModifiableCallout.durationBasedCall("Aetheric Boom (Knockback)", "Knockback");

	// 玉の配置は回数で決まっている
	@HandleEvents
	public void aethericBoom(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x6EF6)) {
			return;
		}
		boomCounter++;
		ModifiableCallout<AbilityCastStart> orbs = switch (boomCounter) {
			case 1 -> aethericBoomFirst;
			case 2 -> aethericBoomSecond;
			case 3 -> aethericBoomThird;
			default -> null;
		};
		if (orbs != null) {
			context.accept(orbs.getModified(event));
		}
		context.accept(aethericBoomKnockback.getModified(event));
	}

}
