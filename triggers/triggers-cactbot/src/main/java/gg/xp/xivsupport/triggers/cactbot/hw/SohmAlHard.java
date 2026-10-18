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
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcAbilityUsedCallout;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/dungeon/sohm_al_hard.ts をもとに移植
@CalloutRepo(name = "Sohm Al Hard", duty = KnownDuty.SohmAlHard)
public class SohmAlHard extends AutoChildEventHandler implements FilteredEventHandler {

	private XivState state;

	public SohmAlHard(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.SohmAlHard);
	}

	// 1ボス目（The Leightonward）

	// 実際のダメージは 1C31 だが、1C30 から 1C31 までの間に溜めが入るのでこちらを起点にする
	@NpcAbilityUsedCallout(value = 0x1C30, suppressMs = 1_000)
	private final ModifiableCallout<AbilityUsedEvent> inflammableFumes = new ModifiableCallout<>("Inflammable Fumes", "Raidwide");

	// 大小のスポアサックで同名の技だが別IDになっている
	@NpcCastCallout(value = 0x1C32, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> gloriousBlaze = ModifiableCallout.durationBasedCall("Glorious Blaze", "Away from Large Pod");

	private final ModifiableCallout<BuffApplied> excretion = new ModifiableCallout<BuffApplied>("Excretion (Heavy)", "Cleanse {event.target}").autoIcon();

	@HandleEvents
	public void excretion(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(0x0E) && !event.isRefresh() && event.getTarget().isPc()
		    && state.playerJobMatches(job -> job.isHealer() || job == Job.BRD || job == Job.BLU)) {
			context.accept(excretion.getModified(event));
		}
	}

	// 2ボス目（Gowrow）

	@NpcCastCallout(0x1C37)
	private final ModifiableCallout<AbilityCastStart> ripperClaw = ModifiableCallout.durationBasedCall("Ripper Claw", "Away from Front");

	@NpcCastCallout(0x1C35)
	private final ModifiableCallout<AbilityCastStart> tailSmash = ModifiableCallout.durationBasedCall("Tail Smash", "Front or Sides");

	@NpcCastCallout(0x1C36)
	private final ModifiableCallout<AbilityCastStart> tailSwing = ModifiableCallout.durationBasedCall("Tail Swing", "Out");

	@NpcCastCallout(0x1C39)
	private final ModifiableCallout<AbilityCastStart> wildCharge = ModifiableCallout.durationBasedCall("Wild Charge", "Away from Front");

	@NpcCastCallout(0x1C3A)
	private final ModifiableCallout<AbilityCastStart> hotCharge = ModifiableCallout.durationBasedCall("Hot Charge", "Away from Front");

	@NpcCastCallout(0x1C3B)
	private final ModifiableCallout<AbilityCastStart> fireball = ModifiableCallout.durationBasedCall("Fireball", "Away from Front");

	@NpcCastCallout(0x1C3C)
	private final ModifiableCallout<AbilityCastStart> lavaFlow = ModifiableCallout.durationBasedCall("Lava Flow", "Away from Front");

	// 3ボス目（Lava Scorpion）

	// 頭上マーカーも出るが、詠唱と同じタイミングなので詠唱側で拾う
	@NpcCastCallout(value = 0x1C3E, onYou = true)
	private final ModifiableCallout<AbilityCastStart> flyingPress = ModifiableCallout.durationBasedCall("Flying Press", "Drop Puddle Outside");

	@NpcCastCallout({0x1C40, 0x1C48})
	private final ModifiableCallout<AbilityCastStart> deadlyThrust = ModifiableCallout.durationBasedCall("Deadly Thrust", "Buster on {event.target}");

	@NpcCastCallout(0x1C45)
	private final ModifiableCallout<AbilityCastStart> hiss = ModifiableCallout.durationBasedCall("Hiss", "Kill Adds");

}
