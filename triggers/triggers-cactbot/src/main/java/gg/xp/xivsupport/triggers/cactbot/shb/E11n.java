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
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/raid/e11n.ts をもとに移植
@CalloutRepo(name = "Edens Promise Anamorphosis", duty = KnownDuty.EdensPromiseAnamorphosis)
public class E11n extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long POWDER_MARK = 0x993;

	private XivState state;

	public E11n(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.EdensPromiseAnamorphosis);
	}

	private final List<XivCombatant> tetherTargets = new ArrayList<>();

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		tetherTargets.clear();
	}

	@NpcCastCallout(0x5650)
	private final ModifiableCallout<AbilityCastStart> burnishedGlory = ModifiableCallout.durationBasedCall("Burnished Glory", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> powderMarkOnYou = ModifiableCallout.durationBasedCall("Powder Mark (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> powderMark = ModifiableCallout.durationBasedCall("Powder Mark", "Buster on {event.target}");

	@HandleEvents
	public void powderMark(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x564E)) {
			context.accept((event.getTarget().isThePlayer() ? powderMarkOnYou : powderMark).getModified(event));
		}
	}

	private final ModifiableCallout<BuffApplied> powderMarkExplosion = ModifiableCallout.<BuffApplied>durationBasedCall("Powder Mark Explosion", "Away from Group").statusIcon(POWDER_MARK);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> powderMarkExplosionSq = SqtTemplates.callWhenDurationIs(
			BuffApplied.class, ba -> ba.buffIdMatches(POWDER_MARK) && ba.getTarget().isThePlayer(), powderMarkExplosion, Duration.ofSeconds(4));

	@NpcCastCallout(0x562C)
	private final ModifiableCallout<AbilityCastStart> burntStrikeFire = ModifiableCallout.durationBasedCall("Burnt Strike (Fire)", "Line Cleave, then Knockback");

	@NpcCastCallout(0x562E)
	private final ModifiableCallout<AbilityCastStart> burntStrikeLightning = ModifiableCallout.durationBasedCall("Burnt Strike (Lightning)", "Line Cleave, then Out");

	@NpcCastCallout(0x5630)
	private final ModifiableCallout<AbilityCastStart> burntStrikeHoly = ModifiableCallout.durationBasedCall("Burnt Strike (Holy)", "Line Cleave and Bait");

	@NpcCastCallout(0x5645)
	private final ModifiableCallout<AbilityCastStart> burntStrikeLightningClone = ModifiableCallout.durationBasedCall("Burnt Strike (Lightning Clone)", "Dodge Lightning First, then Rotate for Fire");

	@NpcCastCallout(0x5643)
	private final ModifiableCallout<AbilityCastStart> burntStrikeFireClone = ModifiableCallout.durationBasedCall("Burnt Strike (Fire Clone)", "Fire Knockback after Lightning");

	@HandleEvents
	public void tetherCollect(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x2, 0x5, 0x6) && !tetherTargets.contains(event.getTarget())) {
			tetherTargets.add(event.getTarget());
		}
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> tetherCleanupSq = SqtTemplates.sq(25_000,
			TetherEvent.class, te -> te.tetherIdMatches(0x2, 0x5, 0x6),
			(e1, s) -> {
				s.waitMs(20_000);
				tetherTargets.clear();
			});

	private boolean playerTethered() {
		return tetherTargets.stream().anyMatch(XivCombatant::isThePlayer);
	}

	private final ModifiableCallout<AbilityCastStart> fireOnYou = ModifiableCallout.durationBasedCall("Bound of Faith (Fire): On You", "Stack on You");
	private final ModifiableCallout<AbilityCastStart> fireOnOther = ModifiableCallout.durationBasedCall("Bound of Faith (Fire): On Other", "Stack on {player}");
	private final ModifiableCallout<AbilityCastStart> fireUnknown = ModifiableCallout.durationBasedCall("Bound of Faith (Fire): Unknown", "Stack");

	@HandleEvents
	public void boundOfFaithFire(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x4B18)) {
			return;
		}
		if (playerTethered()) {
			context.accept(fireOnYou.getModified(event));
		}
		else if (tetherTargets.isEmpty()) {
			context.accept(fireUnknown.getModified(event));
		}
		else {
			context.accept(fireOnOther.getModified(event, Map.<String, Object>of("player", tetherTargets.get(0))));
		}
	}

	private final ModifiableCallout<AbilityCastStart> lightningOnYou = ModifiableCallout.durationBasedCall("Bound of Faith (Lightning): On You", "Lightning on You");
	private final ModifiableCallout<AbilityCastStart> lightningOnOther = ModifiableCallout.durationBasedCall("Bound of Faith (Lightning): On Other", "Lightning on {player}");
	private final ModifiableCallout<AbilityCastStart> lightningUnknown = ModifiableCallout.durationBasedCall("Bound of Faith (Lightning): Unknown", "Lightning");

	@HandleEvents
	public void boundOfFaithLightning(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x4B19)) {
			return;
		}
		if (playerTethered()) {
			context.accept(lightningOnYou.getModified(event));
		}
		else if (tetherTargets.size() == 1) {
			context.accept(lightningOnOther.getModified(event, Map.<String, Object>of("player", tetherTargets.get(0))));
		}
		else {
			context.accept(lightningUnknown.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> holyOnYou = ModifiableCallout.durationBasedCall("Bound of Faith (Holy): On You", "Away from Group");
	private final ModifiableCallout<AbilityCastStart> holyOnOther = ModifiableCallout.durationBasedCall("Bound of Faith (Holy): On Other", "Away from {player}");
	private final ModifiableCallout<AbilityCastStart> holyUnknown = ModifiableCallout.durationBasedCall("Bound of Faith (Holy): Unknown", "Away from Tether");

	@HandleEvents
	public void boundOfFaithHoly(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x4B1B)) {
			return;
		}
		if (playerTethered()) {
			context.accept(holyOnYou.getModified(event));
		}
		else if (tetherTargets.isEmpty()) {
			context.accept(holyUnknown.getModified(event));
		}
		else {
			context.accept(holyOnOther.getModified(event, Map.<String, Object>of("player", tetherTargets.get(0))));
		}
	}

	@NpcCastCallout(0x5639)
	private final ModifiableCallout<AbilityCastStart> turnOfTheHeavensFire = ModifiableCallout.durationBasedCall("Turn of the Heavens (Fire)", "Fire: Go to Blue");

	@NpcCastCallout(0x563A)
	private final ModifiableCallout<AbilityCastStart> turnOfTheHeavensLightning = ModifiableCallout.durationBasedCall("Turn of the Heavens (Lightning)", "Lightning: Go to Red");

}
