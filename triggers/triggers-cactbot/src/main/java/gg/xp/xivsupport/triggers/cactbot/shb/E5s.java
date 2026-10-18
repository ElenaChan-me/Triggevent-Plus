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

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/raid/e5s.ts をもとに移植
@CalloutRepo(name = "Edens Verse Fulmination Savage", duty = KnownDuty.EdensVerseFulminationSavage)
public class E5s extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long SURGE_PROTECTION = 0x8B4;
	private static final long FURYS_BOLT = 0x8B7;
	// 3回目以降の十四雷は玉を取らない
	private static final int FURYS_FOURTEEN_ORB_COUNT = 2;

	private XivState state;

	public E5s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.EdensVerseFulminationSavage);
	}

	private boolean surgeProtection;
	private boolean seenFirstSpear;
	private boolean furysBoltActive;
	private boolean seenFirstAdd;
	private int furysFourteenCount;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		surgeProtection = false;
		seenFirstSpear = false;
		furysBoltActive = false;
		seenFirstAdd = false;
		furysFourteenCount = 0;
	}

	@HandleEvents
	public void buffGain(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(SURGE_PROTECTION) && event.getTarget().isThePlayer()) {
			surgeProtection = true;
		}
		else if (event.buffIdMatches(FURYS_BOLT)) {
			furysBoltActive = true;
		}
	}

	@HandleEvents
	public void buffLose(EventContext context, BuffRemoved event) {
		if (event.buffIdMatches(SURGE_PROTECTION) && event.getTarget().isThePlayer()) {
			surgeProtection = false;
		}
		else if (event.buffIdMatches(FURYS_BOLT)) {
			furysBoltActive = false;
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> stratospear = new ModifiableCallout<>("Stratospear Summons", "Look for Small Spear");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> stratospearSq = SqtTemplates.sq(10_000,
			AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x4BA5) && !seenFirstSpear,
			(e1, s) -> {
				seenFirstSpear = true;
				s.waitMs(5_000);
				s.updateCall(stratospear, e1);
			});

	private final ModifiableCallout<AbilityCastStart> tribunalAdds = ModifiableCallout.durationBasedCall("Tribunal Summons (Adds)", "Look for Adds");
	private final ModifiableCallout<AbilityCastStart> tribunalBigKnockback = ModifiableCallout.durationBasedCall("Tribunal Summons (Fury's Bolt)", "Big Knockback");
	private final ModifiableCallout<AbilityCastStart> tribunalShortKnockback = ModifiableCallout.durationBasedCall("Tribunal Summons", "Short Knockback");

	@HandleEvents
	public void tribunalSummons(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x4BAC)) {
			return;
		}
		ModifiableCallout<AbilityCastStart> call;
		if (seenFirstAdd) {
			call = tribunalAdds;
		}
		else if (furysBoltActive) {
			call = tribunalBigKnockback;
		}
		else {
			call = tribunalShortKnockback;
		}
		seenFirstAdd = true;
		context.accept(call.getModified(event));
	}

	private final ModifiableCallout<AbilityCastStart> furysBoltOrb = ModifiableCallout.durationBasedCall("Fury's Bolt", "Grab an Orb");

	@HandleEvents
	public void furysBolt(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x4BAA) && !surgeProtection) {
			context.accept(furysBoltOrb.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> furysFourteenOrb = ModifiableCallout.durationBasedCall("Fury's Fourteen", "Grab an Orb");

	@HandleEvents
	public void furysFourteen(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x4BAB) || furysFourteenCount >= FURYS_FOURTEEN_ORB_COUNT) {
			return;
		}
		furysFourteenCount++;
		if (!surgeProtection) {
			context.accept(furysFourteenOrb.getModified(event));
		}
	}

	@NpcCastCallout(0x4BB5)
	private final ModifiableCallout<AbilityCastStart> judgmentVolts = ModifiableCallout.durationBasedCall("Judgment Volts", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> steppedLeaderSpread = ModifiableCallout.durationBasedCall("Stepped Leader", "Ready Spread");
	private final ModifiableCallout<AbilityCastStart> steppedLeaderDonut = ModifiableCallout.durationBasedCall("Stepped Leader (Fury's Bolt)", "Donut AoE");
	private final ModifiableCallout<AbilityCastStart> steppedLeaderMove = new ModifiableCallout<>("Stepped Leader: Move", "Move Away");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> steppedLeaderSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x4BC6),
			(e1, s) -> {
				// 雷撃付きの時はドーナツになるので散開不要
				if (furysBoltActive) {
					s.updateCall(steppedLeaderDonut, e1);
					return;
				}
				s.updateCall(steppedLeaderSpread, e1);
				s.waitMs(3_000);
				s.updateCall(steppedLeaderMove, e1);
			});

	private final ModifiableCallout<AbilityCastStart> cripplingBlowOnYou = ModifiableCallout.durationBasedCall("Crippling Blow (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> cripplingBlow = ModifiableCallout.durationBasedCall("Crippling Blow", "Buster on {event.target}");

	@HandleEvents
	public void cripplingBlow(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x4BCA)) {
			context.accept((event.getTarget().isThePlayer() ? cripplingBlowOnYou : cripplingBlow).getModified(event));
		}
	}

	@NpcCastCallout(0x4BB8)
	private final ModifiableCallout<AbilityCastStart> stormcloudSummons = ModifiableCallout.durationBasedCall("Stormcloud Summons", "Position for Stormcloud");

	@PlayerHeadmarker(0xD2)
	private final ModifiableCallout<HeadMarkerEvent> stormcloudCleanse = new ModifiableCallout<>("Hated of Levin", "Cleanse in Cloud");

	@PlayerHeadmarker(0x6E)
	private final ModifiableCallout<HeadMarkerEvent> stormcloudDrop = new ModifiableCallout<>("Stormcloud Drop", "Drop Cloud Away");

	@NpcCastCallout(0x4BAD)
	private final ModifiableCallout<AbilityCastStart> centaursCharge = ModifiableCallout.durationBasedCall("Centaur's Charge", "Be in Your Position");

	@NpcCastCallout(0x4BC4)
	private final ModifiableCallout<AbilityCastStart> chainLightning = ModifiableCallout.durationBasedCall("Chain Lightning", "Ready for Chain");

	@NpcCastCallout(0x4BCC)
	private final ModifiableCallout<AbilityCastStart> levinforce = ModifiableCallout.durationBasedCall("Levinforce", "Knockback");

}
