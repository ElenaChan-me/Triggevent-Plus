package gg.xp.xivsupport.triggers.cactbot.hw;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.Job;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;
import gg.xp.xivsupport.models.XivCombatant;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/raid/a7s.ts をもとに移植
@CalloutRepo(name = "Alexander The Arm Of The Son Savage", duty = KnownDuty.AlexanderTheArmOfTheSonSavage)
public class A7s extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public A7s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AlexanderTheArmOfTheSonSavage);
	}

	private final ModifiableCallout<HeadMarkerEvent> sizzlebeamOnYou = new ModifiableCallout<>("Sizzlebeam (on You)", "Sizzlebeam on You");
	private final ModifiableCallout<HeadMarkerEvent> sizzlebeamOther = new ModifiableCallout<>("Sizzlebeam (not on You)", "Sizzlebeam on {event.target}");

	@HandleEvents
	public void sizzlebeam(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(0x18)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(sizzlebeamOnYou.getModified(event));
		}
		else {
			context.accept(sizzlebeamOther.getModified(event));
		}
	}

	@NpcCastCallout(0x16F8)
	private final ModifiableCallout<AbilityCastStart> sizzlespark = ModifiableCallout.durationBasedCall("Sizzlespark", "Raidwide");

	private final ModifiableCallout<TetherEvent> bombTether = new ModifiableCallout<>("Bomb Tether", "Bomb Spread");

	@HandleEvents
	public void bombTether(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x1F) && event.eitherTargetMatches(XivCombatant::isThePlayer)) {
			context.accept(bombTether.getModified(event));
		}
	}

	@PlayerHeadmarker(0x29)
	private final ModifiableCallout<HeadMarkerEvent> jailPrey = new ModifiableCallout<>("Jail Prey", "Jail Prey");

	private final ModifiableCallout<TetherEvent> jailTether = new ModifiableCallout<>("Jail Tether", "Jail Tether");

	private final RepeatSuppressor jailTetherSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	@HandleEvents
	public void jailTether(EventContext context, TetherEvent event) {
		if (event.tetherIdMatches(0x11) && event.eitherTargetMatches(XivCombatant::isThePlayer) && jailTetherSupp.check(event)) {
			context.accept(jailTether.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> kugelblitz = ModifiableCallout.durationBasedCall("Kugelblitz", "Stun {event.source}");

	@HandleEvents
	public void kugelblitz(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x16FE) && !event.getSource().isPc()
		    && state.playerJobMatches(job -> job.isTank() || job.isMeleeDps() || job == Job.BLU)) {
			context.accept(kugelblitz.getModified(event));
		}
	}

	// 対象人数分のアクションが出るので1回にまとめる
	@NpcAbilityUsedCallout(value = 0x15EC, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> trueHeart = new ModifiableCallout<>("True Heart", "Kill Heart");

	@PlayerStatusCallout(0x178)
	private final ModifiableCallout<BuffApplied> searingWind = ModifiableCallout.<BuffApplied>durationBasedCall("Searing Wind", "Searing Wind on You").autoIcon();

}
