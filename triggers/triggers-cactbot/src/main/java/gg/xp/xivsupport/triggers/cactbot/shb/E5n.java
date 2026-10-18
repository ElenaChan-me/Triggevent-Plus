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
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.BuffRemoved;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/raid/e5n.ts をもとに移植
@CalloutRepo(name = "Edens Verse Fulmination", duty = KnownDuty.EdensVerseFulmination)
public class E5n extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long SURGE_PROTECTION = 0x8B4;
	private static final long DIVINE_JUDGMENT_VOLTS = 0x4B9A;

	private XivState state;

	public E5n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.EdensVerseFulmination);
	}

	private boolean surgeProtection;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		surgeProtection = false;
	}

	@HandleEvents
	public void surgeProtectionGain(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(SURGE_PROTECTION) && event.getTarget().isThePlayer()) {
			surgeProtection = true;
		}
	}

	@HandleEvents
	public void surgeProtectionLose(EventContext context, BuffRemoved event) {
		if (event.buffIdMatches(SURGE_PROTECTION) && event.getTarget().isThePlayer()) {
			surgeProtection = false;
		}
	}

	private final ModifiableCallout<AbilityCastStart> cripplingBlowOnYou = ModifiableCallout.durationBasedCall("Crippling Blow (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> cripplingBlow = ModifiableCallout.durationBasedCall("Crippling Blow", "Buster on {event.target}");
	private final ModifiableCallout<AbilityCastStart> divineJudgmentVoltsOrb = ModifiableCallout.durationBasedCall("Divine Judgment Volts: Orb", "Grab an Orb");

	@HandleEvents
	public void casts(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x4BA3)) {
			context.accept((event.getTarget().isThePlayer() ? cripplingBlowOnYou : cripplingBlow).getModified(event));
		}
		// 耐性デバフが残っている間はオーブを取れない
		else if (event.abilityIdMatches(DIVINE_JUDGMENT_VOLTS) && !surgeProtection) {
			context.accept(divineJudgmentVoltsOrb.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> stratospearSummons = new ModifiableCallout<>("Stratospear Summons", "Look for Small Spear");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> stratospearSummonsSq = SqtTemplates.sq(10_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x4B8D),
			(e1, s) -> {
				s.waitMs(5_000);
				s.updateCall(stratospearSummons, e1);
			});

	@NpcCastCallout(0x4B91)
	private final ModifiableCallout<AbilityCastStart> tribunalSummons = ModifiableCallout.durationBasedCall("Tribunal Summons", "Look for Adds");

	@NpcCastCallout(0x4B90)
	private final ModifiableCallout<AbilityCastStart> furysBolt = ModifiableCallout.durationBasedCall("Fury's Bolt", "Fury's Bolt");

	@NpcCastCallout({0x4B98, DIVINE_JUDGMENT_VOLTS})
	private final ModifiableCallout<AbilityCastStart> judgmentVolts = ModifiableCallout.durationBasedCall("Judgment Volts", "Raidwide");

	@PlayerHeadmarker(0x6E)
	private final ModifiableCallout<HeadMarkerEvent> stormcloud = new ModifiableCallout<>("Stormcloud", "Drop Cloud Outside");

}
