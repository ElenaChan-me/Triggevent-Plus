package gg.xp.xivsupport.triggers.cactbot.arr;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.Job;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.RawAddCombatantEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/02-arr/dungeon/haukke_manor.ts をもとに移植
@CalloutRepo(name = "Haukke Manor", duty = KnownDuty.HaukkeManor)
public class HaukkeManor extends AutoChildEventHandler implements FilteredEventHandler {

	// 入場時に読み込まれる外のセントリーで誤爆しないよう、侍女の出現で判定する
	private static final long LADYS_HANDMAIDEN = 424;

	private XivState state;

	public HaukkeManor(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.HaukkeManor);
	}

	private final ModifiableCallout<AbilityCastStart> darkMistStun = ModifiableCallout.durationBasedCall("Dark Mist (Stun)", "Stun {event.source}");
	private final ModifiableCallout<AbilityCastStart> darkMistDodge = ModifiableCallout.durationBasedCall("Dark Mist (Dodge)", "Out of Melee");
	private final RepeatSuppressor darkMistSupp = new RepeatSuppressor(Duration.ofSeconds(2));

	@HandleEvents
	public void darkMist(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x2C1) && darkMistSupp.check(event)) {
			if (canStun()) {
				context.accept(darkMistStun.getModified(event));
			}
			else {
				context.accept(darkMistDodge.getModified(event));
			}
		}
	}

	private final ModifiableCallout<AbilityCastStart> soulDrain = ModifiableCallout.durationBasedCall("Soul Drain", "Stun {event.source}");
	private final ModifiableCallout<AbilityCastStart> voidFire = ModifiableCallout.durationBasedCall("Void Fire III", "Interrupt {event.source}");

	@HandleEvents
	public void interrupts(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x35C) && canStun()) {
			context.accept(soulDrain.getModified(event));
		}
		else if (event.abilityIdMatches(0x356) && state.playerJobMatches(Job::caresAboutInterrupt)) {
			context.accept(voidFire.getModified(event));
		}
	}

	@NpcCastCallout(value = 0x358, onYou = true)
	private final ModifiableCallout<AbilityCastStart> voidThunder = ModifiableCallout.durationBasedCall("Void Thunder III", "Get Behind");

	private final ModifiableCallout<RawAddCombatantEvent> killSentry = new ModifiableCallout<>("Lady's Handmaiden Spawn", "Kill Sentry");
	private final RepeatSuppressor handmaidenSupp = new RepeatSuppressor(Duration.ofSeconds(2));

	@HandleEvents
	public void addedCombatant(EventContext context, RawAddCombatantEvent event) {
		if (event.getEntity().getbNpcNameId() == LADYS_HANDMAIDEN && handmaidenSupp.check(event)) {
			context.accept(killSentry.getModified(event));
		}
	}

	private boolean canStun() {
		return state.playerJobMatches(job -> job.isTank() || job.isMeleeDps());
	}
}
