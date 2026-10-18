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
import gg.xp.xivsupport.events.actlines.events.BuffRemoved;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/dungeon/the_lost_city_of_amdapor_hard.ts をもとに移植
@CalloutRepo(name = "The Lost City Of Amdapor Hard", duty = KnownDuty.TheLostCityOfAmdaporHard)
public class TheLostCityOfAmdaporHard extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long WIND_RESIST_DOWN = 0x41C;
	private static final long EARTH_RESIST_DOWN = 0x41D;

	private XivState state;

	public TheLostCityOfAmdaporHard(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TheLostCityOfAmdaporHard);
	}

	private final ModifiableCallout<AbilityCastStart> badMouth = ModifiableCallout.durationBasedCall("Gremlin: Bad-Mouth", "Comfort {event.target}");

	@HandleEvents
	public void badMouth(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x775) && !event.getSource().isPc() && !event.getTarget().isThePlayer()) {
			context.accept(badMouth.getModified(event));
		}
	}

	@NpcCastCallout(0x15C5)
	private final ModifiableCallout<AbilityCastStart> neuroSquama = ModifiableCallout.durationBasedCall("Neuro Squama", "Go to Edge, Look Outside");

	private final ModifiableCallout<HeadMarkerEvent> toxicSquama = new ModifiableCallout<>("Toxic Squama", "Avoid Chasing Orb");

	@HandleEvents
	public void toxicSquama(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x0001)) {
			context.accept(toxicSquama.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> waterInterrupt = ModifiableCallout.durationBasedCall("Void Monk: Water III (Interrupt)", "Interrupt {event.source}");
	private final ModifiableCallout<AbilityCastStart> waterStun = ModifiableCallout.durationBasedCall("Void Monk: Water III (Stun)", "Stun {event.source}");
	private final ModifiableCallout<AbilityCastStart> banishInterrupt = ModifiableCallout.durationBasedCall("Light Sprite: Banish III (Interrupt)", "Interrupt {event.source}");
	private final ModifiableCallout<AbilityCastStart> banishStun = ModifiableCallout.durationBasedCall("Light Sprite: Banish III (Stun)", "Stun {event.source}");

	// 沈黙できるジョブには沈黙を、できなければスタンを促す
	@HandleEvents
	public void stunOrInterrupt(EventContext context, AbilityCastStart event) {
		ModifiableCallout<AbilityCastStart> interrupt;
		ModifiableCallout<AbilityCastStart> stun;
		if (event.abilityIdMatches(0x16C7)) {
			interrupt = waterInterrupt;
			stun = waterStun;
		}
		else if (event.abilityIdMatches(0x680)) {
			interrupt = banishInterrupt;
			stun = banishStun;
		}
		else {
			return;
		}
		if (event.getSource().isPc()) {
			return;
		}
		if (state.playerJobMatches(job -> job.isTank() || job.isPranged())) {
			context.accept(interrupt.getModified(event));
		}
		else if (state.playerJobMatches(Job::isMeleeDps)) {
			context.accept(stun.getModified(event));
		}
	}

	@NpcCastCallout(0x16C5)
	private final ModifiableCallout<AbilityCastStart> sucker = ModifiableCallout.durationBasedCall("Void Monk: Sucker", "Draw In");

	private final ModifiableCallout<AbilityUsedEvent> flood = new ModifiableCallout<>("Void Monk: Flood", "Out");

	// Sucker の吸い込み直後に即発動の Flood が来る
	@HandleEvents
	public void flood(EventContext context, AbilityUsedEvent event) {
		if (event.abilityIdMatches(0x16C5) && event.isFirstTarget() && !event.getSource().isPc()) {
			context.accept(flood.getModified(event));
		}
	}

	// 耐性低下が付いている属性の玉は割らずに避ける
	private boolean windResistDown;
	private boolean earthResistDown;

	@PlayerStatusCallout(WIND_RESIST_DOWN)
	private final ModifiableCallout<BuffApplied> windResistDownCall = new ModifiableCallout<BuffApplied>("Wind Resistance Down II", "Avoid Aero").statusIcon(WIND_RESIST_DOWN);

	@PlayerStatusCallout(EARTH_RESIST_DOWN)
	private final ModifiableCallout<BuffApplied> earthResistDownCall = new ModifiableCallout<BuffApplied>("Earth Resistance Down II", "Avoid Stone").statusIcon(EARTH_RESIST_DOWN);

	@HandleEvents
	public void resistDownGained(EventContext context, BuffApplied event) {
		if (event.getTarget().isThePlayer()) {
			if (event.buffIdMatches(WIND_RESIST_DOWN)) {
				windResistDown = true;
			}
			else if (event.buffIdMatches(EARTH_RESIST_DOWN)) {
				earthResistDown = true;
			}
		}
	}

	@HandleEvents
	public void resistDownLost(EventContext context, BuffRemoved event) {
		if (event.getTarget().isThePlayer()) {
			if (event.buffIdMatches(WIND_RESIST_DOWN)) {
				windResistDown = false;
			}
			else if (event.buffIdMatches(EARTH_RESIST_DOWN)) {
				earthResistDown = false;
			}
		}
	}

	private final ModifiableCallout<AbilityUsedEvent> stonePop = new ModifiableCallout<>("Ancient Stone: Pop Orb", "Pop Stone Orb");
	private final ModifiableCallout<AbilityUsedEvent> stoneAvoid = new ModifiableCallout<>("Ancient Stone: Avoid Orb", "Avoid Stone Orb");
	private final ModifiableCallout<AbilityUsedEvent> aeroPop = new ModifiableCallout<>("Ancient Aero: Pop Orb", "Pop Aero Orb");
	private final ModifiableCallout<AbilityUsedEvent> aeroAvoid = new ModifiableCallout<>("Ancient Aero: Avoid Orb", "Avoid Aero Orb");
	private final ModifiableCallout<AbilityUsedEvent> holyPop = new ModifiableCallout<>("Ancient Holy", "Pop Holy Orb");
	private final RepeatSuppressor holySupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void ancientOrbs(EventContext context, AbilityUsedEvent event) {
		if (!event.isFirstTarget() || event.getSource().isPc()) {
			return;
		}
		if (event.abilityIdMatches(0x15D2)) {
			context.accept((earthResistDown ? stoneAvoid : stonePop).getModified(event));
		}
		else if (event.abilityIdMatches(0x15CE)) {
			context.accept((windResistDown ? aeroAvoid : aeroPop).getModified(event));
		}
		else if (event.abilityIdMatches(0x15CA) && holySupp.check(event)) {
			context.accept(holyPop.getModified(event));
		}
	}

	// 複数体がほぼ同時に詠唱するのでまとめる
	@NpcCastCallout(value = 0x16C8, suppressMs = 5_000)
	private final ModifiableCallout<AbilityCastStart> mysteriousLight = ModifiableCallout.durationBasedCall("Mana Pot: Mysterious Light", "Look Away");

	private final ModifiableCallout<AbilityCastStart> kuribuRegen = ModifiableCallout.durationBasedCall("Kuribu: Regen", "Move Boss out of Puddle");

	@HandleEvents
	public void kuribuRegen(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x15DC) && !event.getSource().isPc() && state.playerJobMatches(Job::isTank)) {
			context.accept(kuribuRegen.getModified(event));
		}
	}

	@NpcCastCallout(0x15DF)
	private final ModifiableCallout<AbilityCastStart> cureIV = ModifiableCallout.durationBasedCall("Kuribu: Cure IV", "Stand in Circle");

	@NpcCastCallout(0x15E0)
	private final ModifiableCallout<AbilityCastStart> cureIVReverse = ModifiableCallout.durationBasedCall("Kuribu: Reversed Cure IV", "Raidwide");

	@PlayerHeadmarker(0x004A)
	private final ModifiableCallout<HeadMarkerEvent> cureIII = new ModifiableCallout<>("Kuribu: Cure III", "Spread");
}
